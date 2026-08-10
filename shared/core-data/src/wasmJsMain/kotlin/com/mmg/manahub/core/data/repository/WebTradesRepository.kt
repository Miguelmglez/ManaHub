package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.remote.dto.TradeItemDto
import com.mmg.manahub.core.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.core.data.remote.dto.TradeProposalDto
import com.mmg.manahub.core.data.remote.trades.TradesRemoteDataSource
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.ReviewFlags
import com.mmg.manahub.core.model.TradeItem
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Web [TradesRepository] implementation (KMP web roadmap — Trades slice). A near-direct port of
 * Android's `TradesRepositoryImpl` (`app/src/main/java/com/mmg/manahub/feature/trades/data/
 * repository/TradesRepositoryImpl.kt`): the Android implementation is ALREADY Room-free for the
 * proposal cache itself (a plain in-memory `MutableStateFlow<List<TradeProposal>>`, identical to
 * what [WebUserCardRepository]/[WebDeckRepository] build by hand for their own caches) — its only
 * two platform-specific dependencies are [com.mmg.manahub.core.data.local.dao.CardDao] (Room, used
 * ONLY to enrich [TradeItem] display fields from a resolved card) and
 * `com.mmg.manahub.core.gamification.domain.ProgressionEventBus` (gamification event emission on
 * accept). Both are swapped out here:
 *  - Card enrichment uses [CardRepository.getCardsByIds] (already used the same way by
 *    [WebUserCardRepository.ensureCardsResolved]) instead of a Room DAO lookup.
 *  - The gamification `TradeCompleted` event emission is DROPPED, not stubbed — the web target has
 *    no gamification module wired at all yet (no engine, no event bus registered in
 *    `WebAppKoinModule`), so there is nothing to emit into. Revisit if/when gamification ever lands
 *    on web.
 *
 * Hydration follows the same [SupabaseClient.auth] `sessionStatus`-driven pattern as
 * [WebUserCardRepository]/[WebDeckRepository] (never a one-shot `init` fetch, which would race the
 * async session restore from `localStorage` on a fresh page load).
 */
@OptIn(ExperimentalTime::class)
class WebTradesRepository(
    private val remote: TradesRemoteDataSource,
    private val cardRepository: CardRepository,
    private val supabaseClient: SupabaseClient,
) : TradesRepository {

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val cache = MutableStateFlow<List<TradeProposal>>(emptyList())

    init {
        repositoryScope.launch {
            supabaseClient.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    val userId = supabaseClient.auth.currentUserOrNull()?.id ?: return@collect
                    refreshProposals(userId)
                }
            }
        }
    }

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
        cache.update { current ->
            val existingById = current.associateBy { it.id }
            dtos.map { dto -> dto.toDomain(existingById[dto.id]?.items ?: emptyList()) }
        }
        return Result.success(Unit)
    }

    override suspend fun refreshProposalThread(rootProposalId: String, userId: String): Result<Unit> {
        val proposalsResult = remote.fetchProposals(userId)
        if (proposalsResult.isFailure) return Result.failure(proposalsResult.exceptionOrNull()!!)

        val dtos = proposalsResult.getOrThrow().distinctBy { it.id }
        val threadItems = fetchItemsForProposals(
            dtos.filter { it.rootProposalId == rootProposalId }.map { it.id }
        )

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

    override suspend fun refreshItemsForThread(rootProposalId: String): Result<Unit> {
        val threadProposalIds = cache.value.filter { it.rootProposalId == rootProposalId }.map { it.id }
        if (threadProposalIds.isEmpty()) return Result.success(Unit)

        val threadItems = fetchItemsForProposals(threadProposalIds)
        cache.update { current ->
            current.map { proposal ->
                val fetched = threadItems[proposal.id] ?: return@map proposal
                proposal.copy(items = fetched.first.map { it.toDomain(fetched.second) })
            }
        }
        return Result.success(Unit)
    }

    /**
     * Fetches [TradeItemDto]s + a resolved [Card] map for each of [proposalIds], CONCURRENTLY —
     * same fan-out shape as Android's `TradesRepositoryImpl.fetchItemsForProposals` (an N+1 avoided
     * via `coroutineScope` + `async` per proposal). Network calls stay OUTSIDE any `cache.update`
     * lambda.
     */
    private suspend fun fetchItemsForProposals(
        proposalIds: List<String>,
    ): Map<String, Pair<List<TradeItemDto>, Map<String, Card>>> = coroutineScope {
        proposalIds.map { proposalId ->
            async {
                val itemsResult = remote.fetchProposalItems(proposalId)
                val itemDtos = if (itemsResult.isSuccess) itemsResult.getOrThrow() else emptyList()
                val cardIds = itemDtos.map { it.cardId }.distinct()
                val cardMap: Map<String, Card> = if (cardIds.isNotEmpty()) {
                    cardRepository.warmCacheForIds(cardIds)
                    cardRepository.getCardsByIds(cardIds).associateBy { it.scryfallId }
                } else {
                    emptyMap()
                }
                proposalId to (itemDtos to cardMap)
            }
        }.awaitAll().toMap()
    }

    override suspend fun createProposal(
        receiverId: String,
        items: List<TradeItemRequestDto>,
        includesReviewFromProposer: Boolean,
        includesReviewFromReceiver: Boolean,
        autoSend: Boolean,
    ): Result<String> =
        remote.createProposal(receiverId, items, includesReviewFromProposer, includesReviewFromReceiver, autoSend)

    override suspend fun editProposal(
        proposalId: String,
        expectedVersion: Int,
        newItems: List<TradeItemRequestDto>,
        newReviewFlags: ReviewFlags,
    ): Result<Unit> = remote.editProposal(proposalId, expectedVersion, newItems, newReviewFlags)

    override suspend fun sendProposal(proposalId: String): Result<Unit> = remote.sendProposal(proposalId)

    override suspend fun cancelProposal(proposalId: String): Result<Unit> = remote.cancelProposal(proposalId)

    override suspend fun declineProposal(proposalId: String): Result<Unit> = remote.declineProposal(proposalId)

    override suspend fun counterProposal(
        parentProposalId: String,
        items: List<TradeItemRequestDto>,
        reviewFlags: ReviewFlags,
    ): Result<String> = remote.counterProposal(parentProposalId, items, reviewFlags)

    override suspend fun acceptProposal(proposalId: String): Result<Unit> = remote.acceptProposal(proposalId)

    override suspend fun revokeAcceptance(proposalId: String): Result<Unit> = remote.revokeAcceptance(proposalId)

    override suspend fun markCompleted(proposalId: String): Result<Unit> = remote.markCompleted(proposalId)

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

    private fun TradeProposalDto.toDomain(items: List<TradeItemDto>, cardMap: Map<String, Card> = emptyMap()) =
        TradeProposal(
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

    private fun TradeItemDto.toDomain(cardMap: Map<String, Card> = emptyMap()) = TradeItem(
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

    private fun String.parseIso(): Long? = runCatching { Instant.parse(this).toEpochMilliseconds() }.getOrNull()

    /**
     * Same terminal-fallback convention as Android's `TradesRepositoryImpl.toTradeStatusOrFallback`
     * (trades audit §2.6, 2026-07-10): an unrecognised server status string maps to
     * [TradeStatus.CANCELLED] (terminal, hides every action button), never [TradeStatus.DRAFT] (the
     * most permissive state). No Crashlytics on web -- silently falls back instead of reporting a
     * non-fatal (there is no web equivalent of `recordSafeNonFatal` wired yet).
     */
    private fun String.toTradeStatusOrFallback(): TradeStatus =
        runCatching { TradeStatus.valueOf(this) }.getOrElse { TradeStatus.CANCELLED }
}
