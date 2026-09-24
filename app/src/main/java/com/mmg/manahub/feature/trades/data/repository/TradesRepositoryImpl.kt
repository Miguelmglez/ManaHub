package com.mmg.manahub.feature.trades.data.repository

import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.data.remote.trades.TradesRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.TradeItemDto
import com.mmg.manahub.core.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.core.data.remote.dto.TradeProposalDto
import com.mmg.manahub.core.model.TradeItem
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.model.ReviewFlags
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.util.recordSafeNonFatal
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * KMP migration — Hilt→Koin cutover batch 3. Plain class (no `@Inject`/`@Singleton`); built as a
 * native Koin `single` in [com.mmg.manahub.app.di.coreBridgeKoinModule] (shared with Friends + the
 * still-Hilt `HomeViewModel`/`FriendDetailViewModel` bridges).
 *
 * @param cardRepository used to pre-warm the local card cache for items whose card the user never
 *   cached (typically the counterparty's side of a trade) before enriching from Room, so the UI
 *   never falls back to showing a raw Scryfall UUID as the card name (trades audit §5.4, 2026-07-10).
 */
class TradesRepositoryImpl(
    private val remote: TradesRemoteDataSource,
    private val cardDao: CardDao,
    private val cardRepository: CardRepository,
    private val progressionEventBus: ProgressionEventBus,
) : TradesRepository {

    private val cache = MutableStateFlow<List<TradeProposal>>(emptyList())

    // Trade keys already put on the bus this process, so every refresh does not re-emit the history.
    private val emittedTradeKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun observeActiveProposals(): Flow<List<TradeProposal>> =
        cache.map { list -> list.filter { it.status.isActive } }

    override fun observeProposalHistory(): Flow<List<TradeProposal>> =
        cache.map { list -> list.filter { it.status.isTerminal } }

    override fun observeAllProposals(): Flow<List<TradeProposal>> = cache

    override fun observeProposalThread(rootProposalId: String): Flow<List<TradeProposal>> =
        cache.map { list -> list.filter { it.rootProposalId == rootProposalId } }

    override suspend fun refreshProposals(userId: String): Result<Unit> {
        val proposalsResult = remote.fetchProposals(userId)
        if (proposalsResult.isFailure) return Result.failure(proposalsResult.exceptionOrNull()!!)

        val dtos = proposalsResult.getOrThrow().distinctBy { it.id }
        emitNewlyCompletedTrades(dtos)
        // The existing-items merge is computed INSIDE the update lambda so a concurrent
        // refreshProposalThread() call can't interleave a read-then-write and silently drop
        // the other call's freshly-fetched items (trades audit §2.8, 2026-07-10).
        cache.update { current ->
            val existingById = current.associateBy { it.id }
            // Preserve items already loaded for any proposal in the cache.
            dtos.map { dto -> dto.toDomain(existingById[dto.id]) }
        }
        return Result.success(Unit)
    }

    override suspend fun refreshProposalThread(rootProposalId: String, userId: String): Result<Unit> {
        // Only this thread's proposals: a whole-account fetch per thread open wastes the call budget.
        val dtos = remote.fetchProposals(userId, rootProposalId)
            .getOrElse { return Result.failure(it) }
            .filter { it.rootProposalId == rootProposalId }
            .distinctBy { it.id }
        emitNewlyCompletedTrades(dtos)

        val threadItems = fetchItemsForProposals(dtos.map { it.id })

        // A thread proposal whose item fetch failed keeps whatever the cache already had; other
        // threads are left untouched. Computed inside update() — see the §2.8 note above.
        cache.update { current ->
            val existingById = current.associateBy { it.id }
            val refreshed = dtos.map { dto ->
                val fetched = threadItems[dto.id]?.getOrNull()
                if (fetched != null) dto.toDomain(fetched.first, fetched.second) else dto.toDomain(existingById[dto.id])
            }
            current.filterNot { it.rootProposalId == rootProposalId } + refreshed
        }
        return threadItems.firstItemFailure()?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    override suspend fun refreshItemsForThread(rootProposalId: String): Result<Unit> {
        // WS4a finding 2 (Backend & Performance Optimization plan, 2026-07-28): item-only variant
        // of refreshProposalThread that skips the metadata re-fetch — for callers (Home's
        // hydrateTradeItemCounts fan-out) that already refreshed metadata for the whole account a
        // moment ago via refreshProposals(). Reads proposal ids straight from the in-memory cache
        // rather than fetching dtos again.
        val threadProposalIds = cache.value.filter { it.rootProposalId == rootProposalId }.map { it.id }
        if (threadProposalIds.isEmpty()) return Result.success(Unit)

        val threadItems = fetchItemsForProposals(threadProposalIds)

        cache.update { current ->
            current.map { proposal ->
                val fetched = threadItems[proposal.id]?.getOrNull() ?: return@map proposal
                proposal.copy(items = fetched.first.map { it.toDomain(fetched.second) }, itemsLoaded = true)
            }
        }
        return threadItems.firstItemFailure()?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    private fun Map<String, Result<*>>.firstItemFailure(): Throwable? =
        values.firstNotNullOfOrNull { it.exceptionOrNull() }

    /**
     * Fetches [TradeItemDto]s + a Room card lookup map for each of [proposalIds], CONCURRENTLY.
     * Network/DB calls stay OUTSIDE any `cache.update` lambda (that lambda must be pure/fast —
     * `MutableStateFlow.update` may re-run it under contention).
     *
     * A sequential `associate { ... }` here would await each proposal's fetchProposalItems
     * round-trip before starting the next one — an N+1 that pays full latency × thread length on
     * long counter chains (trades audit §6.1, 2026-07-10). `coroutineScope` + `async` per proposal
     * fans the fetches out. Shared by [refreshProposalThread] and [refreshItemsForThread].
     */
    private suspend fun fetchItemsForProposals(
        proposalIds: List<String>,
    ): Map<String, Result<Pair<List<TradeItemDto>, Map<String, CardEntity>>>> = coroutineScope {
        proposalIds.map { proposalId ->
            async {
                // A failed fetch must never read as "this proposal has no cards".
                val itemDtos = remote.fetchProposalItems(proposalId).getOrElse { e ->
                    return@async proposalId to Result.failure<Pair<List<TradeItemDto>, Map<String, CardEntity>>>(e)
                }
                val cardIds = itemDtos.map { it.cardId }.distinct()
                if (cardIds.isNotEmpty()) {
                    // Pre-warm Room for any card the user never cached (typically the
                    // counterparty's side of the trade) so it doesn't render as a raw
                    // Scryfall UUID below. Best-effort: warmCacheForIds swallows its own
                    // failures internally (trades audit §5.4, 2026-07-10).
                    cardRepository.warmCacheForIds(cardIds)
                }
                val cardMap: Map<String, CardEntity> = if (cardIds.isNotEmpty()) {
                    cardDao.getByIds(cardIds).associateBy { it.scryfallId }
                } else emptyMap()
                proposalId to Result.success(itemDtos to cardMap)
            }
        }.awaitAll().toMap()
    }

    override suspend fun createProposal(
        receiverId: String,
        items: List<TradeItemRequestDto>,
        includesReviewFromProposer: Boolean,
        includesReviewFromReceiver: Boolean,
        autoSend: Boolean,
    ): Result<String> = remote.createProposal(receiverId, items, includesReviewFromProposer, includesReviewFromReceiver, autoSend)

    override suspend fun editProposal(
        proposalId: String,
        expectedVersion: Int,
        newItems: List<TradeItemRequestDto>,
        newReviewFlags: ReviewFlags,
    ): Result<Unit> = remote.editProposal(proposalId, expectedVersion, newItems, newReviewFlags)

    override suspend fun sendProposal(proposalId: String): Result<Unit> =
        remote.sendProposal(proposalId)

    override suspend fun cancelProposal(proposalId: String): Result<Unit> =
        remote.cancelProposal(proposalId)

    override suspend fun declineProposal(proposalId: String): Result<Unit> =
        remote.declineProposal(proposalId)

    override suspend fun counterProposal(
        parentProposalId: String,
        items: List<TradeItemRequestDto>,
        reviewFlags: ReviewFlags,
    ): Result<String> = remote.counterProposal(parentProposalId, items, reviewFlags)

    // Accepting does not complete a trade (it can still be revoked): no progression here (D7).
    override suspend fun acceptProposal(proposalId: String): Result<Unit> =
        remote.acceptProposal(proposalId)

    override suspend fun revokeAcceptance(proposalId: String): Result<Unit> =
        remote.revokeAcceptance(proposalId)

    // COMPLETED needs both parties; the caller's post-success thread refresh observes it and emits.
    override suspend fun markCompleted(proposalId: String): Result<Unit> =
        remote.markCompleted(proposalId)

    override fun clearCache() {
        cache.value = emptyList()
        emittedTradeKeys.clear()
    }

    /**
     * Emits [ProgressionEvent.TradeCompleted] for every COMPLETED proposal in [dtos] not yet emitted
     * this process (restore plan D7). Runs on every server-status observation, so BOTH parties earn it;
     * the key `trade:{rootProposalId}` is global per user, so the ledger dedupes repeats across
     * processes and devices and a replay never advances the trade counters (D10).
     */
    private suspend fun emitNewlyCompletedTrades(dtos: List<TradeProposalDto>) {
        dtos.asSequence()
            .filter { it.status == TradeStatus.COMPLETED.name }
            .map { it.rootProposalId.ifBlank { it.id } }
            .distinct()
            .filter { emittedTradeKeys.add(it) }
            .toList()
            .forEach { tradeId ->
                progressionEventBus.emit(
                    ProgressionEvent.TradeCompleted(tradeId = tradeId, occurredAt = Clock.System.now())
                )
            }
    }

    /** Metadata-only mapping that keeps [existing]'s items and their loaded state. */
    private fun TradeProposalDto.toDomain(existing: TradeProposal?) = TradeProposal(
        id = id,
        status = status.toTradeStatusOrFallback(),
        proposerId = proposerId,
        receiverId = receiverId,
        parentProposalId = parentProposalId,
        rootProposalId = rootProposalId,
        proposalVersion = proposalVersion,
        includesReviewCollectionFromProposer = includesReviewCollectionFromProposer,
        includesReviewCollectionFromReceiver = includesReviewCollectionFromReceiver,
        proposerMarkedCompletedAt = proposerMarkedCompletedAt?.parseIso(),
        receiverMarkedCompletedAt = receiverMarkedCompletedAt?.parseIso(),
        cancellationReason = cancellationReason,
        items = existing?.items ?: emptyList(),
        createdAt = createdAt.parseIso() ?: 0L,
        updatedAt = updatedAt.parseIso() ?: 0L,
        itemsLoaded = existing?.itemsLoaded ?: false,
    )

    private fun TradeProposalDto.toDomain(items: List<TradeItemDto>, cardMap: Map<String, CardEntity> = emptyMap()) = TradeProposal(
        id = id,
        status = status.toTradeStatusOrFallback(),
        proposerId = proposerId,
        receiverId = receiverId,
        parentProposalId = parentProposalId,
        rootProposalId = rootProposalId,
        proposalVersion = proposalVersion,
        includesReviewCollectionFromProposer = includesReviewCollectionFromProposer,
        includesReviewCollectionFromReceiver = includesReviewCollectionFromReceiver,
        proposerMarkedCompletedAt = proposerMarkedCompletedAt?.parseIso(),
        receiverMarkedCompletedAt = receiverMarkedCompletedAt?.parseIso(),
        cancellationReason = cancellationReason,
        items = items.map { it.toDomain(cardMap) },
        createdAt = createdAt.parseIso() ?: 0L,
        updatedAt = updatedAt.parseIso() ?: 0L,
    )

    private fun TradeItemDto.toDomain(cardMap: Map<String, CardEntity> = emptyMap()) = TradeItem(
        id = id,
        tradeProposalId = tradeProposalId,
        fromUserId = fromUserId,
        toUserId = toUserId,
        userCardIdRef = userCardIdRef,
        quantity = quantity,
        isFoil = isFoil,
        condition = condition,
        language = language,
        cardId = cardId,
        cardName = cardMap[cardId]?.name ?: "",
        imageUrl = cardMap[cardId]?.let { it.imageArtCrop ?: it.imageNormal },
        typeLine = cardMap[cardId]?.typeLine,
        setCode = cardMap[cardId]?.setCode,
        setName = cardMap[cardId]?.setName,
        rarity = cardMap[cardId]?.rarity,
        priceUsd = cardMap[cardId]?.let { c -> if (isFoil == true) c.priceUsdFoil ?: c.priceUsd else c.priceUsd },
        priceEur = cardMap[cardId]?.let { c -> if (isFoil == true) c.priceEurFoil ?: c.priceEur else c.priceEur },
        isReviewCollectionPlaceholder = isReviewCollectionPlaceholder,
    )

    private fun String.parseIso(): Long? = runCatching { kotlinx.datetime.Instant.parse(this).toEpochMilliseconds() }.getOrNull()

    /**
     * Maps a raw server status string to [TradeStatus], falling back to a **terminal/inert**
     * status (never [TradeStatus.DRAFT]) on unknown/mismatched values and reporting the drift.
     *
     * [TradeStatus.DRAFT] is the most PERMISSIVE state — it renders the proposal as an active,
     * editable draft with proposer-only Edit/Cancel actions. Silently defaulting an unrecognised
     * status (server enum drift, casing mismatch) to DRAFT was the bug: it surfaced a foreign/
     * unexpected proposal as something the user can edit. [TradeStatus.CANCELLED] is chosen over
     * adding a dedicated `UNKNOWN` enum entry because [TradeStatus] has several exhaustive
     * (non-`else`) `when` blocks in the presentation layer (`TradesHistoryScreen`,
     * `TradeNegotiationDetailScreen`, `FriendHistoryTab`) outside this repository-layer fix's
     * scope — CANCELLED is terminal, hides all action buttons, and needs no new `when` branch
     * anywhere (trades audit §2.6, 2026-07-10).
     */
    private fun String.toTradeStatusOrFallback(): TradeStatus =
        runCatching { TradeStatus.valueOf(this) }.getOrElse { e ->
            recordSafeNonFatal("trade_status_unknown", e)
            TradeStatus.CANCELLED
        }
}
