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
        // The existing-items merge is computed INSIDE the update lambda so a concurrent
        // refreshProposalThread() call can't interleave a read-then-write and silently drop
        // the other call's freshly-fetched items (trades audit §2.8, 2026-07-10).
        cache.update { current ->
            val existingById = current.associateBy { it.id }
            // Preserve items already loaded for any proposal in the cache.
            dtos.map { dto -> dto.toDomain(existingById[dto.id]?.items ?: emptyList()) }
        }
        return Result.success(Unit)
    }

    override suspend fun refreshProposalThread(rootProposalId: String, userId: String): Result<Unit> {
        // Fetch fresh proposal metadata for all of the user's proposals.
        val proposalsResult = remote.fetchProposals(userId)
        if (proposalsResult.isFailure) return Result.failure(proposalsResult.exceptionOrNull()!!)

        val dtos = proposalsResult.getOrThrow().distinctBy { it.id }

        // Fetch items for every proposal in this thread CONCURRENTLY. Network/DB calls stay
        // OUTSIDE the cache.update lambda below (its lambda must be pure/fast —
        // MutableStateFlow.update may re-run it under contention).
        //
        // A sequential `associate { ... }` here would await each proposal's fetchProposalItems
        // round-trip before starting the next one — an N+1 that pays full latency × thread length
        // on long counter chains (trades audit §6.1, 2026-07-10). `coroutineScope` + `async` per
        // proposal fans the fetches out; the `cache.update` merge below is unchanged.
        val threadItems: Map<String, Pair<List<TradeItemDto>, Map<String, CardEntity>>> = coroutineScope {
            dtos.filter { it.rootProposalId == rootProposalId }.map { dto ->
                async {
                    val itemsResult = remote.fetchProposalItems(dto.id)
                    val itemDtos = if (itemsResult.isSuccess) itemsResult.getOrThrow() else emptyList()
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
                    dto.id to (itemDtos to cardMap)
                }
            }.awaitAll().toMap()
        }

        // For proposals in this thread: use the freshly-fetched items. For others: preserve
        // whatever items are in the cache. Computed inside update() — see §2.8 note above.
        cache.update { current ->
            val existingById = current.associateBy { it.id }
            dtos.map { dto ->
                val fetched = threadItems[dto.id]
                if (fetched != null) {
                    dto.toDomain(fetched.first, fetched.second)
                } else {
                    dto.toDomain(existingById[dto.id]?.items ?: emptyList())
                }
            }
        }
        return Result.success(Unit)
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

    override suspend fun acceptProposal(proposalId: String): Result<Unit> =
        remote.acceptProposal(proposalId).also { result ->
            // Emit only after a successful accept (ADR-002 §1). Idempotency key
            // trade:{proposalId} means a second accept (e.g. the counterparty's device,
            // or a retry) grants XP at most once per proposal.
            if (result.isSuccess) {
                progressionEventBus.emit(
                    ProgressionEvent.TradeCompleted(
                        tradeId = proposalId,
                        occurredAt = Clock.System.now(),
                    )
                )
            }
        }

    override suspend fun revokeAcceptance(proposalId: String): Result<Unit> =
        remote.revokeAcceptance(proposalId)

    override suspend fun markCompleted(proposalId: String): Result<Unit> =
        remote.markCompleted(proposalId)

    override fun clearCache() {
        cache.value = emptyList()
    }

    private fun TradeProposalDto.toDomain(existingItems: List<TradeItem>) = TradeProposal(
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
        items = existingItems,
        createdAt = createdAt.parseIso() ?: 0L,
        updatedAt = updatedAt.parseIso() ?: 0L,
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
