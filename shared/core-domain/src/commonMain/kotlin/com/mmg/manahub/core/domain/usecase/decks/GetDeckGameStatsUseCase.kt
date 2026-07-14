package com.mmg.manahub.core.domain.usecase.decks

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase.Companion.RECENT_SESSIONS_LIMIT
import com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase.Companion.TOP_CARDS_LIMIT
import com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase.Companion.WEAK_CARDS_LIMIT
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.feature.game.domain.model.DeckSessionSummary
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Aggregates per-deck game statistics into a single observable [Result].
 *
 * Combines four flows from [gameSessionRepository] / [cardRepository]:
 *  - Single-deck win/loss stats keyed by [playerName].
 *  - Top-scoring cards (up to [TOP_CARDS_LIMIT]) from survey answers.
 *  - Weakest cards (up to [WEAK_CARDS_LIMIT]) from survey answers.
 *  - The last [RECENT_SESSIONS_LIMIT] session summaries for the deck.
 *
 * Card references are resolved to [Card] domain objects via [CardRepository.getCardsByIds].
 */
class GetDeckGameStatsUseCase(
    private val gameSessionRepository: GameSessionRepository,
    private val cardRepository: CardRepository,
) {

    /** The final aggregated result exposed to the presentation layer. */
    data class Result(
        val totalGames: Int,
        val wins: Int,
        /** Win rate in [0, 1]. Always 0 when [totalGames] == 0. */
        val winrate: Float,
        val avgDurationMs: Long,
        /** Up to [TOP_CARDS_LIMIT] best-scoring cards. */
        val topCards: List<CardScore>,
        /** Up to [WEAK_CARDS_LIMIT] worst-scoring cards. */
        val weakestCards: List<CardScore>,
        /** Up to [RECENT_SESSIONS_LIMIT] most recent sessions. */
        val recentSessions: List<DeckSessionSummary>,
    )

    /** A card enriched with its survey impact score for this deck. */
    data class CardScore(
        val card: Card,
        val appearances: Int,
        val avgScore: Double,
    )

    private companion object {
        const val TOP_CARDS_LIMIT = 3
        const val WEAK_CARDS_LIMIT = 3
        const val RECENT_SESSIONS_LIMIT = 5
    }

    /**
     * Returns a cold [Flow] that emits a fresh [Result] whenever any of the
     * underlying repository queries change for [deckId] or [playerName].
     */
    operator fun invoke(deckId: String, playerName: String): Flow<Result> =
        combine(
            gameSessionRepository.observeSingleDeckStats(deckId, playerName),
            gameSessionRepository.observeTopCardImpactsForDeck(deckId, TOP_CARDS_LIMIT),
            gameSessionRepository.observeWeakestCardImpactsForDeck(deckId, WEAK_CARDS_LIMIT),
            gameSessionRepository.observeSessionSummariesForDeck(deckId).map { it.take(RECENT_SESSIONS_LIMIT) },
        ) { statsRow, topRows, weakRows, sessions ->

            val totalGames = statsRow?.totalGames ?: 0
            val wins = statsRow?.wins ?: 0
            val winrate = if (totalGames > 0) wins.toFloat() / totalGames else 0f
            val avgDurationMs = statsRow?.avgDurationMs?.toLong() ?: 0L

            // Resolve card references to domain Card objects in one batched call.
            val allIds = (topRows + weakRows)
                .mapNotNull { it.cardReference }
                .distinct()

            val cardMap: Map<String, Card> = if (allIds.isEmpty()) {
                emptyMap()
            } else {
                cardRepository.getCardsByIds(allIds).associateBy { it.scryfallId }
            }

            val topCards = topRows
                .mapNotNull { row ->
                    val card = cardMap[row.cardReference] ?: return@mapNotNull null
                    CardScore(card, row.appearances, row.avgScore)
                }

            val weakestCards = weakRows
                .mapNotNull { row ->
                    val card = cardMap[row.cardReference] ?: return@mapNotNull null
                    CardScore(card, row.appearances, row.avgScore)
                }

            Result(
                totalGames = totalGames,
                wins = wins,
                winrate = winrate,
                avgDurationMs = avgDurationMs,
                topCards = topCards,
                weakestCards = weakestCards,
                recentSessions = sessions,
            )
        }
        .catch { emit(Result(totalGames = 0, wins = 0, winrate = 0f, avgDurationMs = 0L, topCards = emptyList(), weakestCards = emptyList(), recentSessions = emptyList())) }
}
