package com.mmg.manahub.core.domain.usecase.decks

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.dao.SessionSummary
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.domain.model.Card
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Aggregates per-deck game statistics from Room into a single observable [Result].
 *
 * Combines four flows:
 *  - Single-deck win/loss stats keyed by the current player name.
 *  - Top-scoring cards (up to [TOP_CARDS_LIMIT]) from survey answers.
 *  - Weakest cards (up to [WEAK_CARDS_LIMIT]) from survey answers.
 *  - The last [RECENT_SESSIONS_LIMIT] session summaries for the deck.
 *
 * Card references are resolved to [Card] domain objects via [cardDao.getByIds] on [Dispatchers.IO].
 *
 * No Hilt module needed — Hilt auto-provides via @Inject constructor.
 */
class GetDeckGameStatsUseCase @Inject constructor(
    private val gameSessionDao: GameSessionDao,
    private val surveyAnswerDao: SurveyAnswerDao,
    private val cardDao: com.mmg.manahub.core.data.local.dao.CardDao,
    private val userPrefs: UserPreferencesDataStore,
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
        val recentSessions: List<SessionSummary>,
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
     * underlying Room queries change for [deckId].
     */
    operator fun invoke(deckId: String): Flow<Result> =
        userPrefs.playerNameFlow.flatMapLatest { playerName ->
            combine(
                gameSessionDao.observeSingleDeckStats(deckId, playerName),
                surveyAnswerDao.observeTopCardsForDeck(deckId, TOP_CARDS_LIMIT),
                surveyAnswerDao.observeWeakestCardsForDeck(deckId, WEAK_CARDS_LIMIT),
                gameSessionDao.observeSessionsForDeck(deckId).map { it.take(RECENT_SESSIONS_LIMIT) },
            ) { statsRow, topRows, weakRows, sessions ->

                val totalGames = statsRow?.totalGames ?: 0
                val wins = statsRow?.wins ?: 0
                val winrate = if (totalGames > 0) wins.toFloat() / totalGames else 0f
                val avgDurationMs = statsRow?.avgDurationMs?.toLong() ?: 0L

                // Resolve card references to domain Card objects in one batched IO call.
                val allIds = (topRows + weakRows)
                    .mapNotNull { it.cardReference }
                    .distinct()

                val cardMap: Map<String, Card> = if (allIds.isEmpty()) {
                    emptyMap()
                } else {
                    withContext(Dispatchers.IO) {
                        cardDao.getByIds(allIds).associate { it.scryfallId to it.toDomainCard() }
                    }
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
}
