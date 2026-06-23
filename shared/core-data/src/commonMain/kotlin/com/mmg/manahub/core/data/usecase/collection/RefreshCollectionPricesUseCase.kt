package com.mmg.manahub.core.data.usecase.collection

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Refreshes the prices of every card in the user's collection by batch-fetching
 * from Scryfall and writing updated prices to the local card repository.
 *
 * Emits [Result.Progress] per chunk, then [Result.Success] or [Result.Error].
 *
 * @param userCardRepository provides the list of Scryfall IDs in the collection.
 * @param cardRepository     the target for price updates.
 * @param scryfallDataSource rate-limited Scryfall API access.
 * @param dispatcherProvider KMP-safe dispatcher abstraction (replaces `Dispatchers.IO`).
 */
@OptIn(ExperimentalTime::class)
class RefreshCollectionPricesUseCase(
    private val userCardRepository: UserCardRepository,
    private val cardRepository: CardRepository,
    private val scryfallDataSource: ScryfallRemoteDataSource,
    private val dispatcherProvider: DispatcherProvider,
) {
    sealed class Result {
        data class Success(
            val updatedCount: Int,
            val notFoundCount: Int,
            val durationMs: Long,
        ) : Result()
        data class Error(val message: String) : Result()
        data class Progress(val current: Int, val total: Int) : Result()
    }

    fun invoke(): Flow<Result> = flow {
        val startTime = Clock.System.now().toEpochMilliseconds()
        try {
            val scryfallIds = userCardRepository.getScryfallIds().distinct()

            if (scryfallIds.isEmpty()) {
                emit(Result.Success(0, 0, 0))
                return@flow
            }

            val chunks = scryfallIds.chunked(CHUNK_SIZE)
            val totalChunks = chunks.size
            var updatedCount = 0
            var notFoundCount = 0

            chunks.forEachIndexed { index, chunk ->
                emit(Result.Progress(current = index + 1, total = totalChunks))

                val response = scryfallDataSource.getCardCollection(chunk)

                response.data.forEach { cardDto ->
                    val prices = cardDto.prices
                    cardRepository.updatePrices(
                        scryfallId = cardDto.id,
                        priceUsd = prices.usd?.toDoubleOrNull(),
                        priceUsdFoil = prices.usdFoil?.toDoubleOrNull(),
                        priceEur = prices.eur?.toDoubleOrNull(),
                        priceEurFoil = prices.eurFoil?.toDoubleOrNull(),
                        updatedAt = Clock.System.now().toEpochMilliseconds(),
                    )
                    updatedCount++
                }

                notFoundCount += response.notFound.size
            }

            emit(Result.Success(
                updatedCount = updatedCount,
                notFoundCount = notFoundCount,
                durationMs = Clock.System.now().toEpochMilliseconds() - startTime,
            ))
        } catch (e: Exception) {
            emit(Result.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(dispatcherProvider.io)

    companion object {
        private const val CHUNK_SIZE = 75
    }
}
