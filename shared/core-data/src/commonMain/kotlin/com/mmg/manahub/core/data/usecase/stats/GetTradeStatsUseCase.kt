package com.mmg.manahub.core.data.usecase.stats

import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.TradePartnerSummary
import com.mmg.manahub.core.model.TradeStats
import com.mmg.manahub.core.model.TradeStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * Aggregates trade-history stats over the local user's COMPLETED
 * [com.mmg.manahub.core.model.TradeProposal]s (Stats screen Phase 4, 2026-07 stats expansion).
 *
 * [TradesRepository] has NO local Room-backed Flow — [TradesRepository.observeAllProposals] is an
 * in-memory cache populated only by explicit refresh calls (Phase 0 feasibility finding, see
 * `docs/stats-feasibility_PLAN.md` item 5). This use case therefore performs its OWN one-shot
 * remote refresh (proposal metadata, then every COMPLETED thread's items fanned out concurrently)
 * rather than observing a live Flow. Callers must invoke it explicitly — e.g. on the Stats
 * screen's TRADES tab activation — never from a `combine`/`flatMapLatest` pipeline that expects
 * cheap local reads.
 *
 * Deliberately fetches items for EVERY completed thread, never capped to "newest N": unlike
 * `HomeViewModel.hydrateTradeItemCounts()`'s bounded dashboard warm-up (correct for a summary
 * widget), a stats aggregate that silently capped would undercount any user with more completed
 * trades than the cap.
 *
 * @param tradesRepository owns proposal/item reads and the refresh calls that populate them.
 * @param friendRepository resolves each counterparty's display name (nickname, falling back to
 *   game tag, falling back to "Unknown" — never a raw user id).
 */
class GetTradeStatsUseCase(
    private val tradesRepository: TradesRepository,
    private val friendRepository: FriendRepository,
) {
    suspend operator fun invoke(userId: String, currency: PreferredCurrency): Result<TradeStats> {
        val metadataRefresh = tradesRepository.refreshProposals(userId)
        if (metadataRefresh.isFailure) {
            return Result.failure(
                metadataRefresh.exceptionOrNull() ?: IllegalStateException("Trade metadata refresh failed")
            )
        }

        val completedRootIds = tradesRepository.observeAllProposals().first()
            .filter { it.status == TradeStatus.COMPLETED }
            .map { it.rootProposalId }
            .distinct()

        if (completedRootIds.isEmpty()) {
            return Result.success(
                TradeStats(
                    completedTradesCount = 0,
                    cardsSent = 0,
                    cardsReceived = 0,
                    netValueDelta = 0.0,
                    currency = currency,
                    topPartner = null,
                )
            )
        }

        // Fan out one refreshProposalThread per distinct completed thread, concurrently — the same
        // coroutineScope + async pattern TradesRepositoryImpl.refreshProposalThread itself already
        // uses internally to fetch a single thread's items. A single thread's failure degrades
        // just that thread's items to whatever was already cached (refreshProposalThread's own
        // internal per-item fallback) rather than failing the whole aggregate. This module has no
        // access to the Android-only CrashlyticsHelper (commonMain), so failures are swallowed
        // defensively here — the outer Result already reports a hard (metadata-refresh) failure to
        // Crashlytics at the ViewModel layer, which is the only failure mode expected in practice.
        coroutineScope {
            completedRootIds.map { rootId ->
                async { runCatching { tradesRepository.refreshProposalThread(rootId, userId) } }
            }.awaitAll()
        }

        val completed = tradesRepository.observeAllProposals().first()
            .filter { it.status == TradeStatus.COMPLETED }

        val friendNameByUserId = runCatching { friendRepository.observeFriends().first() }
            .getOrDefault(emptyList())
            .associate { friend -> friend.userId to friend.nickname.ifBlank { friend.gameTag }.ifBlank { "Unknown" } }

        var cardsSent = 0
        var cardsReceived = 0
        var netValueDelta = 0.0
        val completedTradesByPartner = mutableMapOf<String, Int>()

        completed.forEach { proposal ->
            val partnerId = if (proposal.proposerId == userId) proposal.receiverId else proposal.proposerId
            completedTradesByPartner[partnerId] = (completedTradesByPartner[partnerId] ?: 0) + 1

            proposal.items.forEach { item ->
                // Review-collection placeholders never represent a real card transfer.
                if (item.isReviewCollectionPlaceholder) return@forEach
                val quantity = item.quantity ?: 1
                val price = (if (currency == PreferredCurrency.USD) item.priceUsd else item.priceEur) ?: 0.0
                when {
                    item.fromUserId == userId -> {
                        cardsSent += quantity
                        netValueDelta -= price * quantity
                    }
                    item.toUserId == userId -> {
                        cardsReceived += quantity
                        netValueDelta += price * quantity
                    }
                }
            }
        }

        val topPartner = completedTradesByPartner.maxByOrNull { (_, count) -> count }
            ?.let { (partnerId, count) ->
                TradePartnerSummary(
                    userId = partnerId,
                    displayName = friendNameByUserId[partnerId] ?: "Unknown",
                    completedTradesCount = count,
                )
            }

        return Result.success(
            TradeStats(
                completedTradesCount = completed.size,
                cardsSent = cardsSent,
                cardsReceived = cardsReceived,
                netValueDelta = netValueDelta,
                currency = currency,
                topPartner = topPartner,
            )
        )
    }
}
