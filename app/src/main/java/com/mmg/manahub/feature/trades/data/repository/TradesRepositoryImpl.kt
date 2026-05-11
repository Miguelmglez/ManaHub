package com.mmg.manahub.feature.trades.data.repository

import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.feature.trades.data.remote.TradesRemoteDataSource
import com.mmg.manahub.feature.trades.data.remote.dto.TradeItemDto
import com.mmg.manahub.feature.trades.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.feature.trades.data.remote.dto.TradeProposalDto
import com.mmg.manahub.feature.trades.domain.model.TradeItem
import com.mmg.manahub.feature.trades.domain.model.TradeProposal
import com.mmg.manahub.feature.trades.domain.model.TradeStatus
import com.mmg.manahub.feature.trades.domain.repository.ReviewFlags
import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TradesRepositoryImpl @Inject constructor(
    private val remote: TradesRemoteDataSource,
    private val cardDao: CardDao,
) : TradesRepository {

    private val cache = MutableStateFlow<List<TradeProposal>>(emptyList())

    override fun observeActiveProposals(): Flow<List<TradeProposal>> =
        cache.map { list -> list.filter { it.status.isActive } }

    override fun observeProposalHistory(): Flow<List<TradeProposal>> =
        cache.map { list -> list.filter { it.status.isTerminal } }

    override fun observeProposalThread(rootProposalId: String): Flow<List<TradeProposal>> =
        cache.map { list -> list.filter { it.rootProposalId == rootProposalId } }

    override suspend fun refreshProposals(userId: String): Result<Unit> {
        val proposalsResult = remote.fetchProposals(userId)
        if (proposalsResult.isFailure) return Result.failure(proposalsResult.exceptionOrNull()!!)

        val proposals = proposalsResult.getOrThrow()
        val withItems = proposals.map { proposal ->
            val itemsResult = remote.fetchProposalItems(proposal.id)
            val itemDtos = if (itemsResult.isSuccess) itemsResult.getOrThrow() else emptyList()
            // Batch-resolve card names from local Room cache to avoid showing raw UUIDs in the UI.
            val cardIds = itemDtos.map { it.cardId }.distinct()
            val nameMap = if (cardIds.isNotEmpty()) {
                cardDao.getByIds(cardIds).associate { it.scryfallId to it.name }
            } else emptyMap()
            proposal.toDomain(itemDtos, nameMap)
        }
        cache.value = withItems
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
    ): Result<String> = remote.counterProposal(parentProposalId, items)

    override suspend fun acceptProposal(proposalId: String): Result<Unit> =
        remote.acceptProposal(proposalId)

    override suspend fun revokeAcceptance(proposalId: String): Result<Unit> =
        remote.revokeAcceptance(proposalId)

    override suspend fun markCompleted(proposalId: String): Result<Unit> =
        remote.markCompleted(proposalId)

    private fun TradeProposalDto.toDomain(items: List<TradeItemDto>, nameMap: Map<String, String> = emptyMap()) = TradeProposal(
        id = id,
        status = runCatching { TradeStatus.valueOf(status) }.getOrDefault(TradeStatus.DRAFT),
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
        items = items.map { it.toDomain(nameMap) },
        createdAt = createdAt.parseIso() ?: 0L,
        updatedAt = updatedAt.parseIso() ?: 0L,
    )

    private fun TradeItemDto.toDomain(nameMap: Map<String, String> = emptyMap()) = TradeItem(
        id = id,
        tradeProposalId = tradeProposalId,
        fromUserId = fromUserId,
        toUserId = toUserId,
        userCardIdRef = userCardIdRef,
        quantity = quantity,
        isFoil = isFoil,
        condition = condition,
        language = language,
        isAltArt = isAltArt,
        cardId = cardId,
        cardName = nameMap[cardId] ?: "",
        isReviewCollectionPlaceholder = isReviewCollectionPlaceholder,
    )

    private fun String.parseIso(): Long? = runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
}
