package com.mmg.manahub.feature.trades.domain.repository

import com.mmg.manahub.feature.trades.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.feature.trades.domain.model.TradeProposal
import kotlinx.coroutines.flow.Flow

data class ReviewFlags(val fromProposer: Boolean, val fromReceiver: Boolean)

interface TradesRepository {
    fun observeActiveProposals(): Flow<List<TradeProposal>>
    fun observeProposalHistory(): Flow<List<TradeProposal>>
    fun observeAllProposals(): Flow<List<TradeProposal>>
    fun observeProposalThread(rootProposalId: String): Flow<List<TradeProposal>>
    /** Refreshes all proposal metadata (no items). Fast; used by the history list. */
    suspend fun refreshProposals(userId: String): Result<Unit>
    /** Refreshes full proposals + items for a specific thread. Used by the detail view. */
    suspend fun refreshProposalThread(rootProposalId: String, userId: String): Result<Unit>
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
}
