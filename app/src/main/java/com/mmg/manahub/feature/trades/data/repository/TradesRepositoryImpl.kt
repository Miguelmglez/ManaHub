package com.mmg.manahub.feature.trades.data.repository

import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.entity.CardEntity
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.feature.trades.data.remote.TradesRemoteDataSource
import com.mmg.manahub.core.data.remote.dto.TradeItemDto
import com.mmg.manahub.core.data.remote.dto.TradeItemRequestDto
import com.mmg.manahub.core.data.remote.dto.TradeProposalDto
import com.mmg.manahub.core.model.TradeItem
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.model.ReviewFlags
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

        val dtos = proposalsResult.getOrThrow()
        val existingById = cache.value.associateBy { it.id }
        // Preserve items already loaded for any proposal in the cache.
        cache.value = dtos.map { dto -> dto.toDomain(existingById[dto.id]?.items ?: emptyList()) }
        return Result.success(Unit)
    }

    override suspend fun refreshProposalThread(rootProposalId: String, userId: String): Result<Unit> {
        // Fetch fresh proposal metadata for all of the user's proposals.
        val proposalsResult = remote.fetchProposals(userId)
        if (proposalsResult.isFailure) return Result.failure(proposalsResult.exceptionOrNull()!!)

        val dtos = proposalsResult.getOrThrow()
        val existingById = cache.value.associateBy { it.id }

        // For proposals in this thread: fetch items. For others: preserve existing items.
        val updated = dtos.map { dto ->
            if (dto.rootProposalId == rootProposalId) {
                val itemsResult = remote.fetchProposalItems(dto.id)
                val itemDtos = if (itemsResult.isSuccess) itemsResult.getOrThrow() else emptyList()
                val cardIds = itemDtos.map { it.cardId }.distinct()
                val cardMap: Map<String, CardEntity> = if (cardIds.isNotEmpty()) {
                    cardDao.getByIds(cardIds).associateBy { it.scryfallId }
                } else emptyMap()
                dto.toDomain(itemDtos, cardMap)
            } else {
                dto.toDomain(existingById[dto.id]?.items ?: emptyList())
            }
        }
        cache.value = updated
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
                        occurredAt = Instant.now(),
                    )
                )
            }
        }

    override suspend fun revokeAcceptance(proposalId: String): Result<Unit> =
        remote.revokeAcceptance(proposalId)

    override suspend fun markCompleted(proposalId: String): Result<Unit> =
        remote.markCompleted(proposalId)

    private fun TradeProposalDto.toDomain(existingItems: List<TradeItem>) = TradeProposal(
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
        items = existingItems,
        createdAt = createdAt.parseIso() ?: 0L,
        updatedAt = updatedAt.parseIso() ?: 0L,
    )

    private fun TradeProposalDto.toDomain(items: List<TradeItemDto>, cardMap: Map<String, CardEntity> = emptyMap()) = TradeProposal(
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

    private fun String.parseIso(): Long? = runCatching { Instant.parse(this).toEpochMilli() }.getOrNull()
}
