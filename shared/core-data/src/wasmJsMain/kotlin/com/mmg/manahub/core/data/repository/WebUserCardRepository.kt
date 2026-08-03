package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.data.remote.collection.UserCardCollectionDto
import com.mmg.manahub.core.domain.repository.AddOutcome
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UpdateEntryOutcome
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Web [UserCardRepository] implementation (KMP web roadmap W3d) — the fourth web data-layer
 * slice.
 *
 * ## Why this looks like [WebDeckRepository], not [WebCardRepository]
 * [UserCardRepository]'s own KDoc documents it the same way [com.mmg.manahub.core.domain
 * .repository.DeckRepository] does: "Sync is NOT part of this interface — local CRUD only,
 * `SyncManager` owns push/pull." Web has no Room and no separate sync engine, so every mutation
 * here talks to Supabase via [remote] ([CollectionRemoteDataSource], the SAME `commonMain` class
 * Android's `SyncManager` uses since W3d moved it out of `:app`) directly and immediately — same
 * remote-first shape as [WebDeckRepository] (W3c), not [WebCardRepository]'s online-first-search
 * shape.
 *
 * ## Reactivity: two caches, because [UserCardWithCard] needs joined Card data
 * Unlike decks, every read method here returns [UserCardWithCard] (not the bare [UserCard]) —
 * the collection row joined with its full [Card]. Two in-memory [MutableStateFlow] caches back
 * this:
 *  - [entriesCache] — every [UserCardCollectionDto] row for the current session (LIVE *and*
 *    soft-deleted), keyed by row id. Soft-deleted rows are kept (not dropped) because
 *    [addOrIncrement]/[decrementOrRemove] need to find a previously-soft-deleted row at a target
 *    attribute tuple to *revive* it instead of creating a duplicate — mirroring Android's Room
 *    query, which returns soft-deleted rows too (`getByCompositeKey` has no `is_deleted` filter).
 *  - [cardsCache] — resolved [Card] data per scryfall id, populated lazily via [cardRepository]
 *    (the same [CardRepository] Koin singleton the Card Search screen uses — [WebCardRepository]
 *    on web) whenever [entriesCache] contains a scryfall id not yet resolved. A row whose card
 *    hasn't resolved yet is simply absent from every joined read method's output until it does
 *    (an eventually-consistent gap, same shape as [WebDeckRepository]'s async hydration).
 *
 * Hydration (both [entriesCache] and the initial [cardsCache] warm-up) is driven by
 * [SupabaseClient.auth]'s `sessionStatus` transitioning to [SessionStatus.Authenticated], not a
 * one-shot `init` fetch — see [WebDeckRepository]'s KDoc for why a one-shot fetch races the
 * also-async session restore from `localStorage` on a fresh page load.
 *
 * ## `userId` parameters are accepted but effectively ignored
 * Every read/write RPC in [remote] is scoped server-side by `auth.uid()` (RLS), and [entriesCache]
 * only ever holds the CURRENT session's own rows (there is no local multi-user cache on web, and
 * no web consumer browses a friend's collection through this repository — that goes through a
 * separate RPC entirely). The interface's `userId: String?` parameters exist for Android/Room
 * signature parity; this implementation always resolves against the current session.
 *
 * ## Known Web v1 gap: [updateEntryWithMerge]'s Open-for-Trade re-pointing is NOT implemented
 * Android's Room transaction ALSO re-points a linked `local_open_for_trade` row inside the same
 * transaction as the collection-row merge. The `merge_collection_entry` RPC (added for this slice,
 * see [CollectionRemoteDataSource.mergeEntry]'s KDoc) deliberately does NOT touch `open_for_trade`
 * — there is no web Trades feature yet. A future web Trades slice must extend the RPC (or add a
 * companion one) before this repository can honor that part of the documented contract.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class WebUserCardRepository(
    private val remote: CollectionRemoteDataSource,
    private val cardRepository: CardRepository,
    private val supabaseClient: SupabaseClient,
) : UserCardRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Every collection row (live + soft-deleted) for the current session, keyed by row id. */
    private val entriesCache = MutableStateFlow<Map<String, UserCardCollectionDto>>(emptyMap())

    /** Resolved [Card] data per scryfall id, populated lazily. Never persisted (session-scoped). */
    private val cardsCache = MutableStateFlow<Map<String, Card>>(emptyMap())

    init {
        repositoryScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) refreshAllEntries()
            }
        }
        // Resolve card data for any scryfall id newly present in entriesCache (initial hydration
        // AND every subsequent write) — the join half of the two-cache design above.
        repositoryScope.launch {
            entriesCache.collect { entries -> ensureCardsResolved(entries.values.map { it.scryfallId }) }
        }
    }

    // ── Observables ───────────────────────────────────────────────────────────

    /** Joined, LIVE-only view, newest-added first — the shape every read method below filters/sorts. */
    private fun joinedLiveFlow(): Flow<List<UserCardWithCard>> =
        combine(entriesCache, cardsCache) { entries, cards ->
            entries.values
                .filterNot { it.isDeleted }
                .mapNotNull { dto -> cards[dto.scryfallId]?.let { card -> UserCardWithCard(dto.toUserCard(), card) } }
                .sortedByDescending { it.userCard.createdAt }
        }

    override fun observeCollection(): Flow<List<UserCardWithCard>> = joinedLiveFlow()

    override fun observeByColor(color: String): Flow<List<UserCardWithCard>> =
        joinedLiveFlow().map { list -> list.filter { it.card.colorIdentity.any { c -> c.equals(color, ignoreCase = true) } } }

    override fun observeByRarity(rarity: String): Flow<List<UserCardWithCard>> =
        joinedLiveFlow().map { list -> list.filter { it.card.rarity.equals(rarity, ignoreCase = true) } }

    override fun searchInCollection(query: String): Flow<List<UserCardWithCard>> =
        joinedLiveFlow().map { list -> list.filter { it.card.name.contains(query, ignoreCase = true) } }

    override fun observeByScryfallId(scryfallId: String, userId: String?): Flow<List<UserCard>> =
        entriesCache.map { entries ->
            entries.values
                .filter { !it.isDeleted && it.scryfallId == scryfallId }
                .map { it.toUserCard() }
        }

    override fun observeCount(userId: String?): Flow<Int> =
        entriesCache.map { entries -> entries.values.count { !it.isDeleted } }

    override fun observeRecentlyAdded(limit: Int): Flow<List<UserCardWithCard>> =
        joinedLiveFlow().map { list -> list.take(limit) }

    override fun observeVersionsByOracle(
        oracleId: String,
        name: String,
        userId: String?,
    ): Flow<List<UserCardWithCard>> =
        joinedLiveFlow().map { list ->
            list.filter { entry ->
                if (oracleId.isNotBlank()) entry.card.oracleId == oracleId else entry.card.name == name
            }
        }

    // ── Mutations ─────────────────────────────────────────────────────────────

    override suspend fun addOrIncrement(
        scryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        isForTrade: Boolean,
        userId: String?,
        quantity: Int,
    ): AddOutcome {
        val now = nowMillis()
        val normalizedCondition = condition.uppercase().trim()
        val normalizedLanguage = language.lowercase().trim()
        val existing = findByCompositeKey(scryfallId, isFoil, normalizedCondition, normalizedLanguage)

        return when {
            existing == null -> {
                val dto = UserCardCollectionDto(
                    id = Uuid.random().toString(),
                    userId = requireUserId(),
                    scryfallId = scryfallId,
                    quantity = quantity,
                    isFoil = isFoil,
                    condition = normalizedCondition,
                    language = normalizedLanguage,
                    isForTrade = isForTrade,
                    isDeleted = false,
                    updatedAt = now,
                    createdAt = now,
                )
                remote.batchUpsert(listOf(dto)).getOrThrow()
                entriesCache.update { it + (dto.id to dto) }
                AddOutcome.CREATED_NEW
            }
            existing.isDeleted -> {
                // Restore the same row (same id) so no duplicate is created. createdAt is bumped
                // to `now` too -- matches Android's fix (Home dashboard audit, HIGH): a re-added
                // card must surface at the top of Recently Added, not sort by its stale original
                // acquisition date.
                val revived = existing.copy(
                    quantity = quantity,
                    isDeleted = false,
                    isForTrade = isForTrade,
                    updatedAt = now,
                    createdAt = now,
                )
                remote.batchUpsert(listOf(revived)).getOrThrow()
                entriesCache.update { it + (revived.id to revived) }
                AddOutcome.CREATED_NEW
            }
            else -> {
                val incremented = existing.copy(
                    quantity = existing.quantity + quantity,
                    isForTrade = isForTrade || existing.isForTrade,
                    updatedAt = now,
                )
                remote.batchUpsert(listOf(incremented)).getOrThrow()
                entriesCache.update { it + (incremented.id to incremented) }
                AddOutcome.INCREMENTED_EXISTING
            }
        }
    }

    override suspend fun updateAttributes(id: String, isForTrade: Boolean, quantity: Int) {
        val existing = entriesCache.value[id] ?: return
        val updated = existing.copy(isForTrade = isForTrade, quantity = quantity, updatedAt = nowMillis())
        remote.batchUpsert(listOf(updated)).getOrThrow()
        entriesCache.update { it + (id to updated) }
    }

    override suspend fun deleteCard(id: String) {
        val existing = entriesCache.value[id] ?: return
        val deleted = existing.copy(isDeleted = true, updatedAt = nowMillis())
        remote.batchUpsert(listOf(deleted)).getOrThrow()
        entriesCache.update { it + (id to deleted) }
    }

    override suspend fun getScryfallIds(): List<String> =
        entriesCache.value.values.filterNot { it.isDeleted }.map { it.scryfallId }.distinct()

    override suspend fun decrementOrRemove(
        userId: String,
        scryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        quantityToDeduct: Int,
    ) {
        val normCondition = condition.uppercase().trim()
        val normLanguage = language.lowercase().trim()
        val existing = findByCompositeKey(scryfallId, isFoil, normCondition, normLanguage)
            ?.takeIf { !it.isDeleted } ?: return // Row not found (or already gone) -- skip silently, no crash.

        val newQty = (existing.quantity - quantityToDeduct).coerceAtLeast(0)
        val updated = if (newQty == 0) {
            existing.copy(isDeleted = true, updatedAt = nowMillis())
        } else {
            existing.copy(quantity = newQty, updatedAt = nowMillis())
        }
        remote.batchUpsert(listOf(updated)).getOrThrow()
        entriesCache.update { it + (updated.id to updated) }
    }

    override suspend fun updateEntryWithMerge(
        entryId: String,
        newScryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        quantity: Int,
        userId: String?,
    ): UpdateEntryOutcome {
        val normalizedCondition = condition.uppercase().trim()
        val normalizedLanguage = language.lowercase().trim()
        val found = remote.mergeEntry(
            entryId = entryId,
            newScryfallId = newScryfallId,
            isFoil = isFoil,
            condition = normalizedCondition,
            language = normalizedLanguage,
            quantity = quantity,
        ).getOrThrow()

        if (!found) return UpdateEntryOutcome.ENTRY_NOT_FOUND

        // The RPC performed the atomic 4-branch merge server-side (see CollectionRemoteDataSource
        // .mergeEntry's KDoc) but doesn't report back WHICH branch fired or the resulting row
        // shape(s) -- re-pulling the full set is simpler and strictly correct vs. re-deriving the
        // merge branch client-side a second time (this is a low-frequency user action, not a hot
        // path, so the extra round trip is a fine trade for guaranteed cache correctness).
        refreshAllEntries()
        return UpdateEntryOutcome.UPDATED
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun findByCompositeKey(
        scryfallId: String,
        isFoil: Boolean,
        normalizedCondition: String,
        normalizedLanguage: String,
    ): UserCardCollectionDto? = entriesCache.value.values.firstOrNull {
        it.scryfallId == scryfallId &&
            it.isFoil == isFoil &&
            it.condition == normalizedCondition &&
            it.language == normalizedLanguage
    }

    /** Full pull of every collection row for the current session (0L watermark = full pull). */
    private suspend fun refreshAllEntries() {
        val dtos = remote.getChangesSince(0L).getOrElse { return }
        entriesCache.value = dtos.associateBy { it.id }
    }

    private fun ensureCardsResolved(scryfallIds: Collection<String>) {
        val missing = scryfallIds.distinct().filterNot { cardsCache.value.containsKey(it) }
        if (missing.isEmpty()) return
        repositoryScope.launch {
            cardRepository.warmCacheForIds(missing)
            val resolved = cardRepository.getCardsByIds(missing)
            if (resolved.isNotEmpty()) cardsCache.update { it + resolved.associateBy(Card::scryfallId) }
        }
    }

    private fun requireUserId(): String =
        supabaseClient.auth.currentUserOrNull()?.id
            ?: error("WebUserCardRepository requires a signed-in session (guest sign-in included) to mutate the collection")

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    private fun UserCardCollectionDto.toUserCard(): UserCard = UserCard(
        id = id,
        scryfallId = scryfallId,
        quantity = quantity,
        isFoil = isFoil,
        condition = condition,
        language = language,
        isForTrade = isForTrade,
        updatedAt = updatedAt,
        createdAt = createdAt,
    )
}
