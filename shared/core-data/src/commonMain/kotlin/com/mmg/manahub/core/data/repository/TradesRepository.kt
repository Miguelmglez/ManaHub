package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.core.model.ReviewFlags
import com.mmg.manahub.core.model.TradeProposal
import kotlinx.coroutines.flow.Flow

/**
 * Contract for trade-proposal operations (create/edit/send/accept/decline/counter/complete).
 *
 * Implementations live in `:app` (`TradesRepositoryImpl`); this interface sits in `:shared:core-data`
 * so Koin islands and future web targets can depend on it without pulling in the full `:app` module.
 */
interface TradesRepository {
    fun observeActiveProposals(): Flow<List<TradeProposal>>
    fun observeProposalHistory(): Flow<List<TradeProposal>>
    fun observeAllProposals(): Flow<List<TradeProposal>>
    fun observeProposalThread(rootProposalId: String): Flow<List<TradeProposal>>
    /** Refreshes all proposal metadata (no items). Fast; used by the history list. */
    suspend fun refreshProposals(userId: String): Result<Unit>
    /** Refreshes full proposals + items for a specific thread. Used by the detail view. */
    suspend fun refreshProposalThread(rootProposalId: String, userId: String): Result<Unit>
    /**
     * Refreshes ONLY the trade items (never proposal metadata) for the proposals already present
     * in the in-memory cache for [rootProposalId]. Backend & Performance Optimization plan, WS4a
     * finding 2 (2026-07-28): [refreshProposalThread] always re-fetches the caller's FULL proposal
     * table before hydrating items, which is redundant when the metadata is already fresh (e.g.
     * immediately after [refreshProposals]). Callers MUST ensure proposal metadata is already
     * current before calling this -- it reads whatever proposals are currently cached for the
     * thread, it does not fetch metadata itself. If no cached proposal belongs to [rootProposalId]
     * yet, this is a no-op success (nothing to hydrate).
     */
    suspend fun refreshItemsForThread(rootProposalId: String): Result<Unit>
    suspend fun createProposal(
        receiverId: String,
        items: List<TradeItemRequestDto>,
        includesReviewFromProposer: Boolean,
        includesReviewFromReceiver: Boolean,
        autoSend: Boolean,
    ): Result<String>
    suspend fun editProposal(
        proposalId: String,
        expectedVersion: Int,
        newItems: List<TradeItemRequestDto>,
        newReviewFlags: ReviewFlags,
    ): Result<Unit>
    suspend fun sendProposal(proposalId: String): Result<Unit>
    suspend fun cancelProposal(proposalId: String): Result<Unit>
    suspend fun declineProposal(proposalId: String): Result<Unit>
    suspend fun counterProposal(
        parentProposalId: String,
        items: List<TradeItemRequestDto>,
        reviewFlags: ReviewFlags,
    ): Result<String>
    suspend fun acceptProposal(proposalId: String): Result<Unit>
    suspend fun revokeAcceptance(proposalId: String): Result<Unit>
    suspend fun markCompleted(proposalId: String): Result<Unit>
    /**
     * Clears the in-memory proposals cache. Call on sign-out (or before a different account signs
     * in) so the previous account's proposals are never briefly visible to the next account
     * (trades audit §2.10, 2026-07-10) — the cache is a process-lifetime singleton with no
     * per-user partitioning.
     */
    fun clearCache()
}
