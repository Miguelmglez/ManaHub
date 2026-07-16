package com.mmg.manahub.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.dao.LocalOpenForTradeDao
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.dao.UserCardWithCard
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.data.local.paging.CollectionRemoteMediator
import com.mmg.manahub.core.data.local.paging.RemoteKeyDao
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.domain.repository.AddOutcome
import com.mmg.manahub.core.domain.repository.CollectionPagerSource
import com.mmg.manahub.core.domain.repository.UpdateEntryOutcome
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.mmg.manahub.core.model.UserCardWithCard as DomainUserCardWithCard

/**
 * Local-first implementation of [UserCardRepository].
 *
 * All mutations write to Room first. The [com.mmg.manahub.core.sync.SyncManager]
 * detects dirty rows via [UserCardCollectionEntity.updatedAt] and pushes them to
 * Supabase on the next sync cycle.
 *
 * Soft-deletes set [UserCardCollectionEntity.isDeleted] = true rather than
 * removing the row, so that the deletion is propagated to Supabase on the next push.
 */
@Singleton
class UserCardRepositoryImpl @Inject constructor(
    private val userCardCollectionDao: UserCardCollectionDao,
    private val collectionRemoteDataSource: CollectionRemoteDataSource,
    private val remoteKeyDao: RemoteKeyDao,
    private val database: MtgDatabase,
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    // Card Versions & Languages, Phase 1A: needed so updateEntryWithMerge can atomically
    // re-point/merge a linked open-for-trade offer inside the SAME database.withTransaction as
    // the collection-row edit (LocalOpenForTradeDao is a core-owned DAO, not a Trades-feature
    // type — see the class KDoc below for why this repository reaches into it directly).
    private val localOpenForTradeDao: LocalOpenForTradeDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UserCardRepository, CollectionPagerSource {

    // Emits null for unauthenticated/loading, userId for authenticated.
    // Used by all observe methods so they re-subscribe when the user changes.
    @Suppress("OPT_IN_USAGE")
    private val currentUserIdFlow = authRepository.sessionState.map { state ->
        (state as? SessionState.Authenticated)?.user?.id
    }

    // ── Observables ───────────────────────────────────────────────────────────

    @Suppress("OPT_IN_USAGE")
    override fun observeCollection(): Flow<List<DomainUserCardWithCard>> =
        currentUserIdFlow.flatMapLatest { userId ->
            // When logged out (userId == null) show ALL local non-deleted cards so the
            // collection stays visible after logout instead of going blank. Logged-in
            // cards have a real userId and would be invisible to observeAll(null).
            val source = if (userId != null)
                userCardCollectionDao.observeAll(userId)
            else
                userCardCollectionDao.observeAllLocal()
            source.map { list -> list.filter { !it.userCard.isDeleted }.mapNotNull { it.toDomain() } }
        }

    @Suppress("OPT_IN_USAGE")
    override fun observeByColor(color: String): Flow<List<DomainUserCardWithCard>> =
        currentUserIdFlow.flatMapLatest { userId ->
            userCardCollectionDao.observeAll(userId).map { list ->
                list.filter { item ->
                    !item.userCard.isDeleted &&
                        item.card?.colorIdentity?.contains(color, ignoreCase = true) == true
                }.mapNotNull { it.toDomain() }
            }
        }

    @Suppress("OPT_IN_USAGE")
    override fun observeByRarity(rarity: String): Flow<List<DomainUserCardWithCard>> =
        currentUserIdFlow.flatMapLatest { userId ->
            userCardCollectionDao.observeAll(userId).map { list ->
                list.filter { item ->
                    !item.userCard.isDeleted &&
                        item.card?.rarity?.equals(rarity, ignoreCase = true) == true
                }.mapNotNull { it.toDomain() }
            }
        }

    @Suppress("OPT_IN_USAGE")
    override fun searchInCollection(query: String): Flow<List<DomainUserCardWithCard>> =
        currentUserIdFlow.flatMapLatest { userId ->
            userCardCollectionDao.observeAll(userId).map { list ->
                list.filter { item ->
                    !item.userCard.isDeleted &&
                        item.card?.name?.contains(query, ignoreCase = true) == true
                }.mapNotNull { it.toDomain() }
            }
        }

    @Suppress("OPT_IN_USAGE")
    override fun observeByScryfallId(scryfallId: String, userId: String?): Flow<List<UserCard>> {
        // If caller passes an explicit userId, use it directly.
        // If null, follow the current session so logged-in users see their cards.
        return if (userId != null) {
            userCardCollectionDao.observeByScryfall(scryfallId, userId).map { it.toUserCards() }
        } else {
            currentUserIdFlow.flatMapLatest { resolvedId ->
                userCardCollectionDao.observeByScryfall(scryfallId, resolvedId).map { it.toUserCards() }
            }
        }
    }

    private fun List<UserCardCollectionEntity>.toUserCards() =
        filter { !it.isDeleted }.map { entity ->
            UserCard(
                id = entity.id,
                scryfallId = entity.scryfallId,
                quantity = entity.quantity,
                isFoil = entity.isFoil,
                condition = entity.condition,
                language = entity.language,
                isForTrade = entity.isForTrade,
                updatedAt = entity.updatedAt,
                createdAt = entity.createdAt,
            )
        }

    override fun observeCount(userId: String?): Flow<Int> =
        userCardCollectionDao.observeCount(userId)

    @Suppress("OPT_IN_USAGE")
    override fun observeRecentlyAdded(limit: Int): Flow<List<DomainUserCardWithCard>> =
        currentUserIdFlow.flatMapLatest { userId ->
            val source = if (userId != null) {
                userCardCollectionDao.observeRecent(userId, limit)
            } else {
                userCardCollectionDao.observeRecentLocal(limit)
            }
            source.map { list -> list.filter { !it.userCard.isDeleted }.mapNotNull { it.toDomain() } }
        }

    @Suppress("OPT_IN_USAGE")
    override fun observeVersionsByOracle(
        oracleId: String,
        name: String,
        userId: String?,
    ): Flow<List<DomainUserCardWithCard>> =
        if (userId != null) {
            userCardCollectionDao.observeVersionsByOracle(oracleId, name, userId)
                .map { list -> list.mapNotNull { it.toDomain() } }
        } else {
            currentUserIdFlow.flatMapLatest { resolvedId ->
                userCardCollectionDao.observeVersionsByOracle(oracleId, name, resolvedId)
                    .map { list -> list.mapNotNull { it.toDomain() } }
            }
        }

    @OptIn(ExperimentalPagingApi::class)
    override fun getCollectionPager(userId: String?): Flow<PagingData<UserCardWithCard>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            remoteMediator = CollectionRemoteMediator(
                userId = userId,
                supabaseClient = supabaseClient,
                userCardCollectionDao = userCardCollectionDao,
                remoteKeyDao = remoteKeyDao,
                db = database,
            ),
            pagingSourceFactory = { userCardCollectionDao.getCollectionPagingSource(userId) },
        ).flow

    // ── Mutations ─────────────────────────────────────────────────────────────

    override suspend fun addOrIncrement(
        scryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        isForTrade: Boolean,
        userId: String?,
        quantity: Int,
    ): AddOutcome = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val resolvedUserId = userId ?: authRepository.getCurrentUser()?.id
        val normalizedCondition = condition.uppercase().trim()
        val normalizedLanguage = language.lowercase().trim()

        // Look up existing row by composite unique key (synchronous query, not Flow).
        val existing = if (!resolvedUserId.isNullOrBlank()) {
            userCardCollectionDao.getByCompositeKey(
                resolvedUserId, scryfallId, isFoil,
                normalizedCondition, normalizedLanguage,
            )
        } else {
            userCardCollectionDao.getByCompositeKeyGuest(
                scryfallId, isFoil, normalizedCondition,
                normalizedLanguage,
            )
        }

        when {
            existing == null -> {
                userCardCollectionDao.upsert(
                    UserCardCollectionEntity(
                        id               = UUID.randomUUID().toString(),
                        userId           = resolvedUserId,
                        scryfallId       = scryfallId,
                        quantity         = quantity,
                        isFoil           = isFoil,
                        condition        = normalizedCondition,
                        language         = normalizedLanguage,
                        isForTrade       = isForTrade,
                        isDeleted        = false,
                        updatedAt        = now,
                        createdAt        = now,
                    )
                )
                AddOutcome.CREATED_NEW
            }
            existing.isDeleted -> {
                userCardCollectionDao.upsert(
                    // Restore the same row (same UUID) so no duplicate is created.
                    // Quantity resets to the incoming value — the card was gone before.
                    // createdAt is bumped to `now` too: this branch reports AddOutcome.CREATED_NEW
                    // (a "new" add from the caller's perspective), but RECENTLY_ADDED orders strictly
                    // by created_at DESC, so leaving the stale original acquisition date meant a card
                    // the user just finished re-adding never surfaced at the top (Home dashboard audit,
                    // HIGH). No other logic in this codebase keys off createdAt staying stable across
                    // a delete/restore cycle — grep confirms achievements/stats never reference it.
                    existing.copy(
                        quantity   = quantity,
                        isDeleted  = false,
                        isForTrade = isForTrade,
                        updatedAt  = now,
                        createdAt  = now,
                    )
                )
                // A previously soft-deleted card returning counts as a new unique add.
                AddOutcome.CREATED_NEW
            }
            else -> {
                userCardCollectionDao.upsert(
                    existing.copy(
                        quantity   = existing.quantity + quantity,
                        isForTrade = isForTrade || existing.isForTrade,
                        updatedAt  = now,
                    )
                )
                AddOutcome.INCREMENTED_EXISTING
            }
        }
    }

    override suspend fun updateAttributes(
        id: String,
        isForTrade: Boolean,
        quantity: Int,
    ) = withContext(ioDispatcher) {
        val existing = userCardCollectionDao.getById(id) ?: return@withContext
        userCardCollectionDao.upsert(
            existing.copy(
                isForTrade = isForTrade,
                quantity = quantity,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun deleteCard(id: String) = withContext(ioDispatcher) {
        // Soft delete: set isDeleted = true, bump updatedAt. The sync engine will
        // propagate this deletion to Supabase on the next push cycle.
        userCardCollectionDao.softDelete(id, System.currentTimeMillis())
    }

    override suspend fun getScryfallIds(): List<String> = withContext(ioDispatcher) {
        val userId = authRepository.getCurrentUser()?.id ?: ""
        userCardCollectionDao.getAllSince(userId, 0L)
            .filter { !it.isDeleted }
            .map { it.scryfallId }
            .distinct()
    }

    override suspend fun decrementOrRemove(
        userId: String,
        scryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        quantityToDeduct: Int,
    ) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val normCondition = condition.uppercase().trim()
        val normLanguage = language.lowercase().trim()

        val existing = userCardCollectionDao.getByCompositeKey(
            userId, scryfallId, isFoil, normCondition, normLanguage,
        ) ?: return@withContext  // Row not found — skip silently, no crash.

        val newQty = (existing.quantity - quantityToDeduct).coerceAtLeast(0)
        if (newQty == 0) {
            // Soft-delete so the sync engine propagates the removal to Supabase.
            userCardCollectionDao.softDelete(existing.id, now)
        } else {
            userCardCollectionDao.upsert(existing.copy(quantity = newQty, updatedAt = now))
        }
    }

    override suspend fun updateEntryWithMerge(
        entryId: String,
        newScryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        quantity: Int,
        userId: String?,
    ): UpdateEntryOutcome = withContext(ioDispatcher) {
        // Cross-DAO atomicity (user_card_collection + local_open_for_trade) is achieved via
        // database.withTransaction { } — the same pattern CollectionRemoteMediator already uses
        // for its Room writes — rather than a single-DAO @Transaction default method: Room only
        // allows a @Transaction default method to call OTHER methods on the SAME DAO instance, and
        // this edit must also re-point/merge a linked LocalOpenForTradeDao row atomically with the
        // collection-row change (a process death between the two would otherwise leave either a
        // dangling open-for-trade offer pointing at a soft-deleted row, or a merged survivor row
        // with no offer carried over).
        database.withTransaction {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("collection_edit_entry_id", entryId)
                setCustomKey("collection_edit_new_scryfall_id", newScryfallId)
            }
            val edited = userCardCollectionDao.getById(entryId)
            if (edited == null) {
                // A2 (edge-case audit, 2026-07-15): the entry no longer exists (concurrent delete
                // from another device via sync, or a stale UI reference). Previously this branch
                // silently `return@withTransaction`'d Unit and the ViewModel showed "Entry
                // updated" even though nothing changed — now the caller can distinguish this and
                // show an honest error.
                FirebaseCrashlytics.getInstance().setCustomKey("collection_edit_merge_outcome", "entry_not_found")
                recordSafeNonFatal(
                    "collection_update_merge_entry_not_found",
                    IllegalStateException("updateEntryWithMerge: entryId no longer exists"),
                )
                return@withTransaction UpdateEntryOutcome.ENTRY_NOT_FOUND
            }
            val now = System.currentTimeMillis()
            val normalizedCondition = condition.uppercase().trim()
            val normalizedLanguage = language.lowercase().trim()
            val resolvedUserId = userId ?: edited.userId

            val survivorCandidate = if (!resolvedUserId.isNullOrBlank()) {
                userCardCollectionDao.getByCompositeKey(
                    resolvedUserId, newScryfallId, isFoil,
                    normalizedCondition, normalizedLanguage,
                )
            } else {
                userCardCollectionDao.getByCompositeKeyGuest(
                    newScryfallId, isFoil, normalizedCondition, normalizedLanguage,
                )
            }

            val mergeOutcome = when {
                // A LIVE row already occupies the target tuple — merge into it and drop [edited].
                survivorCandidate != null && survivorCandidate.id != entryId && !survivorCandidate.isDeleted -> {
                    val mergedQuantity = survivorCandidate.quantity + quantity
                    userCardCollectionDao.upsert(survivorCandidate.copy(quantity = mergedQuantity, updatedAt = now))
                    userCardCollectionDao.softDelete(entryId, now)
                    repointOpenForTradeOffer(
                        fromCollectionId = entryId,
                        toCollectionId = survivorCandidate.id,
                        toScryfallId = newScryfallId,
                        isFoil = isFoil,
                        condition = normalizedCondition,
                        language = normalizedLanguage,
                        quantityCap = mergedQuantity,
                    )
                    "merged"
                }
                // The target tuple is occupied by a SOFT-DELETED row — reuse/undelete it as the
                // survivor instead of creating a new row that would collide with the unique index
                // (same convention as addOrIncrement's restore branch).
                survivorCandidate != null && survivorCandidate.id != entryId && survivorCandidate.isDeleted -> {
                    userCardCollectionDao.upsert(
                        survivorCandidate.copy(quantity = quantity, isDeleted = false, updatedAt = now)
                    )
                    userCardCollectionDao.softDelete(entryId, now)
                    repointOpenForTradeOffer(
                        fromCollectionId = entryId,
                        toCollectionId = survivorCandidate.id,
                        toScryfallId = newScryfallId,
                        isFoil = isFoil,
                        condition = normalizedCondition,
                        language = normalizedLanguage,
                        quantityCap = quantity,
                    )
                    "revived"
                }
                // No collision (or the target tuple IS [edited]'s own current tuple) — update in place.
                else -> {
                    userCardCollectionDao.upsert(
                        edited.copy(
                            scryfallId = newScryfallId,
                            isFoil = isFoil,
                            condition = normalizedCondition,
                            language = normalizedLanguage,
                            quantity = quantity,
                            updatedAt = now,
                        )
                    )
                    val offer = localOpenForTradeDao.getByCollectionId(entryId)
                    if (offer != null) {
                        localOpenForTradeDao.upsert(
                            offer.copy(
                                scryfallId = newScryfallId,
                                isFoil = isFoil,
                                condition = normalizedCondition,
                                language = normalizedLanguage,
                                quantity = offer.quantity.coerceAtMost(quantity),
                                synced = false,
                            )
                        )
                    }
                    "in_place"
                }
            }
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("collection_edit_merge_outcome", mergeOutcome)
                log("collection_entry_update_merge_outcome")
            }
            UpdateEntryOutcome.UPDATED
        }
    }

    /**
     * Re-points the open-for-trade offer (if any) linked to [fromCollectionId] onto
     * [toCollectionId], merging with an existing offer already on the survivor (summed and capped
     * at [quantityCap]) or moving the offer wholesale when the survivor has none yet. Marks the
     * result `synced = false` so the next sync push corrects the remote `user_card_id` mapping.
     * Must be called from inside the SAME `database.withTransaction` block as the collection-row
     * merge that produced [toCollectionId].
     */
    private suspend fun repointOpenForTradeOffer(
        fromCollectionId: String,
        toCollectionId: String,
        toScryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        quantityCap: Int,
    ) {
        val fromOffer = localOpenForTradeDao.getByCollectionId(fromCollectionId) ?: return
        val toOffer = localOpenForTradeDao.getByCollectionId(toCollectionId)
        if (toOffer != null) {
            val cappedQuantity = (toOffer.quantity + fromOffer.quantity).coerceAtMost(quantityCap)
            localOpenForTradeDao.upsert(
                toOffer.copy(
                    quantity = cappedQuantity,
                    isFoil = isFoil,
                    condition = condition,
                    language = language,
                    synced = false,
                )
            )
            localOpenForTradeDao.deleteByCollectionId(fromCollectionId)
        } else {
            localOpenForTradeDao.upsert(
                fromOffer.copy(
                    localCollectionId = toCollectionId,
                    scryfallId = toScryfallId,
                    quantity = fromOffer.quantity.coerceAtMost(quantityCap),
                    isFoil = isFoil,
                    condition = condition,
                    language = language,
                    synced = false,
                )
            )
        }
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun UserCardWithCard.toDomain(): DomainUserCardWithCard? {
        val cardEntity = card ?: return null
        val userCardDomain = UserCard(
            id = userCard.id,
            scryfallId = userCard.scryfallId,
            quantity = userCard.quantity,
            isFoil = userCard.isFoil,
            condition = userCard.condition,
            language = userCard.language,
            isForTrade = userCard.isForTrade,
            updatedAt = userCard.updatedAt,
            createdAt = userCard.createdAt,
        )
        return DomainUserCardWithCard(userCard = userCardDomain, card = cardEntity.toDomainCard())
    }

    companion object {
        private const val PAGE_SIZE = 50
    }
}
