package com.mmg.manahub.feature.collection.data

import androidx.room.withTransaction
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.entity.CollectionSelectionQueryEntity
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.data.local.mapper.toTagList
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.domain.search.AdvancedSearchCardMatcher
import com.mmg.manahub.core.model.*
import kotlinx.coroutines.*
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/** Source values and matcher metadata freeze atomically before bounded evaluation begins. */
class RoomCollectionSelectionRepository(
    private val database: MtgDatabase,
    private val sessions: TransferSessionGate,
    private val observer: TransferAuthSessionObserver,
    private val now: ()->Long=System::currentTimeMillis,
    private val onSnapshotFrozen: suspend ()->Unit = {},
    private val onEvaluationPhase: (String)->Unit = {},
) : CollectionSelectionRepository {
    private val dao get()=database.collectionSelectionDao()
    private fun ensure(owner: TransferOwner,captured: TransferSession) {
        currentCheck(owner,captured)
    }
    private fun currentCheck(owner: TransferOwner,captured: TransferSession) {
        if(sessions.currentSession!=captured || (captured as? TransferSession.Available)?.owner!=owner || !observer.matchesObserved(owner))throw TransferReadException(TransferError.OWNER_CHANGED)
    }
    override suspend fun capture(owner: TransferOwner,query: CollectionSelectionQuery): CollectionSelectionSummary {
        val id=UUID.randomUUID().toString()
        try {
            freeze(owner,query,id)
            onSnapshotFrozen()
            return evaluate(owner,id,query)
        } catch(failure: Throwable) {
            withContext(NonCancellable+Dispatchers.IO) { dao.discard(id,owner.storageKey()) }
            throw failure
        }
    }
    suspend fun freeze(owner: TransferOwner,query: CollectionSelectionQuery,id: String,onFrozen: suspend ()->Unit = {}) = withContext(Dispatchers.Default) {
        val captured=sessions.currentSession;ensure(owner,captured)
        val definition=CollectionSelectionQueryEntity(id,owner.storageKey(),query.source.name,query.search,query.advanced?.let { Json.encodeToString(it) } ?: "",query.sort.name,query.ascending,query.grouping.name,"BUILDING",now())
            sessions.withOwner(owner) { _,guard -> database.withTransaction {
                guard();ensure(owner,captured)
                dao.insertQuery(definition)
                val normalized="CASE WHEN substr(?2,1,8)='account:' THEN substr(?2,9) ELSE NULL END"
                val wishlistOwnership="(w.owner_user_id=$normalized OR (substr(?2,1,6)='guest:' AND w.owner_user_id='local_guest' AND EXISTS(SELECT 1 FROM collection_transfer_wishlist_dirty d WHERE d.wishlist_id=w.id AND d.owner_key=?2 AND d.deleted=0)))"
                val source=when(query.source) {
                    CollectionSource.COLLECTION -> "SELECT id,scryfall_id,quantity,is_foil,condition,language,created_at,is_foil AS raw_foil,condition AS raw_condition,language AS raw_language FROM user_card_collection u WHERE is_deleted=0 AND ((substr(?2,1,8)='account:' AND user_id=substr(?2,9)) OR (substr(?2,1,6)='guest:' AND user_id IS NULL AND EXISTS(SELECT 1 FROM collection_transfer_guest_rows g WHERE g.row_id=u.id AND g.owner_key=?2)))"
                    CollectionSource.WISHLIST -> "SELECT w.id,w.scryfall_id,w.quantity,COALESCE(w.is_foil,0) AS is_foil,COALESCE(w.condition,'NM') AS condition,COALESCE(w.language,'en') AS language,w.created_at,w.is_foil AS raw_foil,w.condition AS raw_condition,w.language AS raw_language FROM local_wishlists w WHERE $wishlistOwnership"
                    CollectionSource.FOR_TRADE -> "SELECT id,scryfall_id,quantity,is_foil,condition,language,created_at,is_foil AS raw_foil,condition AS raw_condition,language AS raw_language FROM local_open_for_trade WHERE owner_user_id=$normalized"
                }
                val columns=selectionCardColumns.joinToString(",") { "card_$it" }
                val values=selectionCardValues.mapIndexed { index,value ->
                    val column=selectionCardColumns[index]
                    if(column in setOf("image_normal","image_art_crop")) "CASE WHEN c.lang!='en' AND EXISTS(SELECT 1 FROM cards e WHERE e.set_code=c.set_code AND e.collector_number=c.collector_number AND e.lang='en') THEN (SELECT e.$column FROM cards e WHERE e.set_code=c.set_code AND e.collector_number=c.collector_number AND e.lang='en' ORDER BY e.scryfall_id LIMIT 1) ELSE c.$column END" else value
                }.joinToString(",")
                val identity="COALESCE(NULLIF(c.oracle_id,''),NULLIF(c.name,''),s.scryfall_id)"
                val sql="INSERT INTO collection_selection_rows(query_id,source_id,quantity,foil,condition,language,source_created_at,wishlisted,for_trade,matched,group_key,raw_foil,raw_condition,raw_language,$columns) SELECT ?1,s.id,s.quantity,s.is_foil,s.condition,s.language,s.created_at,EXISTS(SELECT 1 FROM local_wishlists w WHERE w.scryfall_id=s.scryfall_id AND $wishlistOwnership),EXISTS(SELECT 1 FROM local_open_for_trade t WHERE t.scryfall_id=s.scryfall_id AND t.owner_user_id=$normalized),0,COALESCE(c.set_code,'')||'|'||$identity,s.raw_foil,s.raw_condition,s.raw_language,$values FROM ($source) s LEFT JOIN cards c ON c.scryfall_id=s.scryfall_id"
                database.openHelper.writableDatabase.execSQL(sql,arrayOf(id,owner.storageKey()))
                onFrozen()
                guard();ensure(owner,captured)
            } } ?: throw TransferReadException(TransferError.OWNER_CHANGED)
    }
    suspend fun evaluate(owner: TransferOwner,id: String,query: CollectionSelectionQuery,discardOnFailure: Boolean=true,requireMetadata: Boolean=false,includeUiSummary: Boolean=true): CollectionSelectionSummary = withContext(Dispatchers.Default) {
        val captured=sessions.currentSession;ensure(owner,captured)
        var definition=dao.query(id,owner.storageKey()) ?: throw TransferReadException(TransferError.NOT_FOUND)
        try {
            database.withTransaction { dao.resetEvaluation(id) }
            var after=""
            val advanced=query.advanced
            val tags=linkedMapOf<String,CardTag>()
            if(query.search.isBlank() && advanced==null) {
                dao.markUnfiltered(id,requireMetadata || query.source!=CollectionSource.COLLECTION)
                onEvaluationPhase("marked")
                while(includeUiSummary) {
                    currentCoroutineContext().ensureActive();ensure(owner,captured)
                    val rows=dao.scanTags(id,after);if(rows.isEmpty())break
                    rows.forEach { row ->
                        (row.tags.toTagList()+row.userTags.toTagList()).forEach { tag -> tags.putIfAbsent(tag.key,tag) }
                    }
                    after=rows.last().sourceId;yield()
                }
            } else while(true) {
                currentCoroutineContext().ensureActive();ensure(owner,captured)
                val rows=dao.scan(id,after);if(rows.isEmpty())break
                val matched=rows.filter { row ->
                    val card=row.card.toDomainCard()
                    if(includeUiSummary)(card.tags+card.userTags).forEach { tag -> tags.putIfAbsent(tag.key,tag) }
                    (!requireMetadata || card.staleReason!="pending_hydration") && (query.source==CollectionSource.COLLECTION || card.staleReason!="pending_hydration") &&
                        (query.search.isBlank() || card.name.contains(query.search,ignoreCase=true)) &&
                        (advanced==null || AdvancedSearchCardMatcher.matches(card,advanced,isWishlisted=row.wishlisted,isForTrade=row.forTrade))
                }.map { it.sourceId }
                ensure(owner,captured)
                if(matched.isNotEmpty())dao.markMatched(id,matched)
                after=rows.last().sourceId
                yield()
            }
            onEvaluationPhase("matched_tags")
            ensure(owner,captured)
            database.withTransaction {
                val sql="""INSERT INTO collection_selection_groups(query_id,group_key,representative_id,quantity,foil,distinct_copies,latest_added_at,name,price,rarity,section,section_rank,section_sort,ordinal)
                    SELECT r.query_id,r.group_key,r.source_id,g.quantity,g.foil,g.distinct_copies,g.latest_added_at,r.card_name,COALESCE(r.card_price_usd,0),CASE lower(r.card_rarity) WHEN 'mythic' THEN 4 WHEN 'rare' THEN 3 WHEN 'uncommon' THEN 2 ELSE 1 END,'',0,'',0
                    FROM (SELECT query_id,group_key,SUM(quantity) AS quantity,MAX(foil) AS foil,MAX(source_created_at) AS latest_added_at,
                    (SELECT COUNT(*) FROM (SELECT DISTINCT card_scryfall_id,foil,condition,language FROM collection_selection_rows d WHERE d.query_id=a.query_id AND d.group_key=a.group_key AND d.matched=1)) AS distinct_copies,
                    (SELECT source_id FROM collection_selection_rows p WHERE p.query_id=a.query_id AND p.group_key=a.group_key AND p.matched=1 ORDER BY source_created_at,source_id LIMIT 1) AS representative
                    FROM collection_selection_rows a WHERE query_id=? AND matched=1 GROUP BY query_id,group_key) g
                    JOIN collection_selection_rows r ON r.query_id=g.query_id AND r.source_id=g.representative""".trimIndent()
                database.openHelper.writableDatabase.execSQL(sql,arrayOf(id))
            }
            onEvaluationPhase("aggregate")
            after=""
            while(query.grouping!=CollectionGroupingMode.NONE) {
                currentCoroutineContext().ensureActive();ensure(owner,captured)
                val groups=dao.scanGroups(id,after);if(groups.isEmpty())break
                val metadata=dao.rows(id,groups.map { it.representativeId }).associateBy { it.sourceId }
                dao.updateGroups(groups.map { group ->
                    val card=metadata.getValue(group.representativeId).card.toDomainCard()
                    val token=collectionSelectionSection(card,query.grouping)
                    group.copy(section=token,sectionRank=collectionSelectionSectionRank(token,query.grouping),sectionSort=if(query.grouping==CollectionGroupingMode.SET)card.setName.ifBlank { card.setCode }.lowercase() else "")
                })
                after=groups.last().groupKey;yield()
            }
            onEvaluationPhase("sections")
            ensure(owner,captured)
            database.withTransaction {
                val direction=if(query.ascending)"ASC" else "DESC"
                val sort=when(query.sort) { CollectionSelectionSort.NAME->"name $direction";CollectionSelectionSort.PRICE->"price $direction,name ASC";CollectionSelectionSort.RARITY->"rarity $direction,name ASC";CollectionSelectionSort.DATE_ADDED->"latest_added_at $direction,name ASC" }
                val sql=database.openHelper.writableDatabase
                sql.execSQL("DROP TABLE IF EXISTS temp.collection_selection_sort_temp")
                sql.execSQL("DROP TABLE IF EXISTS temp.collection_selection_sections_temp")
                sql.execSQL("DROP TABLE IF EXISTS temp.collection_selection_final_temp")
                sql.execSQL("CREATE TEMP TABLE collection_selection_sort_temp(position INTEGER PRIMARY KEY,group_key TEXT NOT NULL UNIQUE)")
                sql.execSQL("CREATE TEMP TABLE collection_selection_sections_temp(section TEXT NOT NULL PRIMARY KEY,rank INTEGER NOT NULL,first_position INTEGER NOT NULL,sort TEXT NOT NULL)")
                sql.execSQL("CREATE TEMP TABLE collection_selection_final_temp(position INTEGER PRIMARY KEY,group_key TEXT NOT NULL UNIQUE)")
                sql.execSQL("INSERT INTO collection_selection_sort_temp(group_key) SELECT group_key FROM collection_selection_groups WHERE query_id=? ORDER BY $sort,group_key",arrayOf(id))
                val ordinalTable=if(query.grouping==CollectionGroupingMode.NONE) "collection_selection_sort_temp" else {
                    sql.execSQL("INSERT INTO collection_selection_sections_temp(section,rank,first_position,sort) SELECT g.section,MIN(g.section_rank),MIN(t.position),'' FROM collection_selection_groups g JOIN collection_selection_sort_temp t ON t.group_key=g.group_key WHERE g.query_id=? GROUP BY g.section",arrayOf(id))
                    sql.execSQL("UPDATE collection_selection_sections_temp SET sort=(SELECT g.section_sort FROM collection_selection_sort_temp t JOIN collection_selection_groups g ON g.query_id=? AND g.group_key=t.group_key WHERE t.position=collection_selection_sections_temp.first_position)",arrayOf(id))
                    sql.execSQL("INSERT INTO collection_selection_final_temp(group_key) SELECT g.group_key FROM collection_selection_groups g JOIN collection_selection_sort_temp t ON t.group_key=g.group_key JOIN collection_selection_sections_temp s ON s.section=g.section WHERE g.query_id=? ORDER BY s.rank,s.sort,s.first_position,t.position",arrayOf(id))
                    "collection_selection_final_temp"
                }
                sql.execSQL("UPDATE collection_selection_groups SET ordinal=(SELECT t.position FROM $ordinalTable t WHERE t.group_key=collection_selection_groups.group_key) WHERE query_id=?",arrayOf(id))
                sql.execSQL("DROP TABLE collection_selection_sort_temp")
                sql.execSQL("DROP TABLE collection_selection_sections_temp")
                sql.execSQL("DROP TABLE collection_selection_final_temp")
                val missing=database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM collection_selection_rows WHERE query_id=? AND card_stale_reason='pending_hydration'",arrayOf(id)).use { cursor -> cursor.moveToFirst();cursor.getLong(0) }
                definition=definition.copy(phase="READY",missingMetadata=missing)
                dao.updateQuery(definition)
                ensure(owner,captured)
            }
            onEvaluationPhase("order")
            CollectionSelectionSummary(id,dao.groupCount(id),dao.copyCount(id),definition.missingMetadata,if(includeUiSummary)dao.sections(id) else emptyList(),tags.values.toSet()).also { ensure(owner,captured) }
        } catch(failure: Throwable) {
            if(discardOnFailure)withContext(NonCancellable+Dispatchers.IO) { dao.discard(id,owner.storageKey()) }
            throw failure
        }
    }
    override suspend fun page(owner: TransferOwner,id: String,after: Long): CollectionSelectionPage=withContext(Dispatchers.Default) {
        require(after>=0L)
        val captured=sessions.currentSession;ensure(owner,captured)
        val definition=dao.query(id,owner.storageKey())?.takeIf { it.phase=="READY" } ?: throw TransferReadException(TransferError.NOT_FOUND)
        val groups=dao.page(id,after)
        val metadata=dao.rows(id,groups.map { it.representativeId }).associateBy { it.sourceId }
        val result=groups.map { group ->
            val card=metadata.getValue(group.representativeId).card.toDomainCard()
            CollectionSelectionGroup(card,group.groupKey,group.quantity,group.distinctCopies,group.foil,group.latestAddedAt,group.section)
        }
        ensure(owner,captured)
        CollectionSelectionPage(result,groups.lastOrNull()?.takeIf { groups.size==50 }?.ordinal)
    }
    override suspend fun discard(owner: TransferOwner,id: String) { dao.discard(id,owner.storageKey()) }
}
