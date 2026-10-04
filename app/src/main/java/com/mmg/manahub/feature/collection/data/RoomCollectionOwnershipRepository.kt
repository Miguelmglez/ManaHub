package com.mmg.manahub.feature.collection.data

import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.model.Card
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Indexed candidate lookups never materialize the owner's collection or claim legacy guest rows. */
class RoomCollectionOwnershipRepository(
    private val database: MtgDatabase,
    private val sessions: TransferSessionGate,
    private val observer: TransferAuthSessionObserver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CollectionOwnershipRepository {
    private data class Request(val session: TransferSession,val candidates: CollectionOwnershipCandidates)

    @OptIn(ExperimentalCoroutinesApi::class,FlowPreview::class)
    override fun observe(candidates: Flow<CollectionOwnershipCandidates>): Flow<Set<String>> = channelFlow {
        combine(sessions.sessions,observer.identities,candidates) { session,_,cards -> Request(session,cards) }
            .onEach { send(emptySet()) }
            .collectLatest request@ { request ->
                val captured=request.session
                val available=(captured as? TransferSession.Available)?.takeIf { observer.matchesObserved(it.owner) } ?: return@request
                if(request.candidates.size==0)return@request
                fun current()=sessions.currentSession==captured && observer.matchesObserved(available.owner)
                database.invalidationTracker.createFlow("cards","user_card_collection","collection_transfer_guest_rows")
                    .debounce(100).collectLatest refresh@ {
                        try {
                            val keys=HashSet<String>()
                            var start=0
                            while(start<request.candidates.size) {
                                currentCoroutineContext().ensureActive()
                                if(!current())return@refresh
                                val end=minOf(start+200,request.candidates.size)
                                val page=(start until end).map(request.candidates::cardAt)
                                val matched=lookup(available.owner,page)
                                if(!current())return@refresh
                                page.forEach { card -> if(card.scryfallId in matched) {
                                    if(card.oracleId.isNotBlank())keys.add(card.oracleId)
                                    keys.add(card.name)
                                } }
                                start=end
                            }
                            if(current())send(keys)
                        } catch(cancelled: CancellationException) { throw cancelled }
                        catch(_: Exception) {
                            if(current())send(emptySet())
                        }
                    }
            }
    }.flowOn(dispatcher)

    internal fun lookup(owner: TransferOwner,cards: List<Card>): Set<String> {
        require(cards.size in 1..200)
        val values=cards.indices.joinToString(",") { index ->
            val first=2+index*3
            "(?$first,?${first+1},?${first+2})"
        }
        val ownership="((substr(?1,1,8)='account:' AND u.user_id=substr(?1,9)) OR (substr(?1,1,6)='guest:' AND u.user_id IS NULL AND EXISTS(SELECT 1 FROM collection_transfer_guest_rows g WHERE g.row_id=u.id AND g.owner_key=?1)))"
        fun exists(index: String,predicate: String)="EXISTS(SELECT 1 FROM cards owned INDEXED BY $index JOIN user_card_collection u INDEXED BY index_user_card_collection_scryfall_id ON u.scryfall_id=owned.scryfall_id WHERE $predicate AND u.is_deleted=0 AND $ownership)"
        val sql="WITH requested(printing,oracle_id,name) AS (VALUES $values) SELECT printing FROM requested r WHERE ${exists("index_cards_oracle_id","r.oracle_id!='' AND owned.oracle_id=r.oracle_id")} OR ${exists("index_cards_name","owned.name=r.name") }"
        val args=ArrayList<Any>(1+cards.size*3)
        args.add(owner.storageKey())
        cards.forEach { card -> args.add(card.scryfallId);args.add(card.oracleId);args.add(card.name) }
        return database.openHelper.readableDatabase.query(sql,args.toTypedArray()).use { cursor ->
            buildSet { while(cursor.moveToNext())add(cursor.getString(0)) }
        }
    }
}
