package com.mmg.manahub.feature.trades.domain.usecase

import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.util.recordSafeNonFatal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/** Resolves deferred collection applies for the active account while the app runs. */
class TradePendingApplyCoordinator(
    private val authRepository: AuthRepository,
    private val tradesRepository: TradesRepository,
    private val syncDao: TradeCollectionSyncDao,
    private val updateTradeCollection: UpdateTradeCollectionUseCase,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            authRepository.sessionState
                .map { (it as? SessionState.Authenticated)?.user?.id }
                .distinctUntilChanged()
                .collectLatest { userId ->
                    if (userId == null) return@collectLatest
                    updateTradeCollection.retryPendingOfferCleanup(userId)
                    updateTradeCollection.retryPendingWishlistCleanup(userId)
                    syncDao.observePendingApplyProposalIds(userId).collectLatest { pendingIds ->
                        if (pendingIds.isEmpty()) return@collectLatest
                        while (true) {
                            try {
                                require((authRepository.sessionState.value as? SessionState.Authenticated)?.user?.id == userId)
                                tradesRepository.refreshProposals(userId).getOrThrow()
                                val proposals = tradesRepository.observeAllProposals().first().associateBy { it.id }
                                pendingIds.forEach { proposalId ->
                                    val proposal = proposals[proposalId] ?: return@forEach
                                    when {
                                        proposal.status == TradeStatus.COMPLETED -> {
                                            tradesRepository.refreshProposalThread(proposal.rootProposalId, userId).getOrThrow()
                                            val loaded = tradesRepository.observeProposalThread(proposal.rootProposalId)
                                                .first().firstOrNull { it.id == proposalId && it.itemsLoaded }
                                                ?: return@forEach
                                            updateTradeCollection(
                                                proposalId = proposalId,
                                                userId = userId,
                                                sentItems = loaded.items.filter { it.fromUserId == userId && !it.isReviewCollectionPlaceholder },
                                                receivedItems = loaded.items.filter { it.toUserId == userId && !it.isReviewCollectionPlaceholder },
                                            ).getOrThrow()
                                        }
                                        proposal.status.isTerminal -> syncDao.clearPendingApply(proposalId, userId)
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                recordSafeNonFatal("trade_pending_apply_background_failed", e)
                            }
                            delay(POLL_INTERVAL_MS)
                        }
                    }
                }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5 * 60 * 1000L
    }
}
