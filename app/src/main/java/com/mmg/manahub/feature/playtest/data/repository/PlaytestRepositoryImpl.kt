package com.mmg.manahub.feature.playtest.data.repository

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.dao.PlaytestDao
import com.mmg.manahub.core.data.local.entity.PlaytestCardStatEntity
import com.mmg.manahub.core.data.local.entity.PlaytestSessionEntity
import com.mmg.manahub.core.data.local.entity.PlaytestSurveyAnswerEntity
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.playtest.domain.model.PlaytestSurveyAnswers
import com.mmg.manahub.feature.playtest.domain.repository.PlaytestRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implements [PlaytestRepository] by delegating to [PlaytestDao].
 *
 * All suspend functions run on [IoDispatcher] to keep Room off the main thread.
 */
class PlaytestRepositoryImpl @Inject constructor(
    private val playtestDao: PlaytestDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaytestRepository {

    override suspend fun saveTest(
        deckId: String,
        deckFormat: String,
        configuredDrawCount: Int,
        mulligansUsed: Int,
        librarySize: Int,
        onThePlay: Boolean,
        startedAt: Long,
        cardCountsInHand: Map<String, Int>,
        cardCountsBottomed: Map<String, Int>,
    ): Long = withContext(ioDispatcher) {
        val session = PlaytestSessionEntity(
            id           = 0,
            deckId       = deckId,
            format       = deckFormat,
            drawCount    = configuredDrawCount,
            mulligansUsed = mulligansUsed,
            librarySize  = librarySize,
            onThePlay    = onThePlay,
            startedAt    = startedAt,
            savedAt      = System.currentTimeMillis(),
        )

        // Build per-card stat rows for every distinct scryfallId that appeared
        // (in the hand or was bottomed). Session id is 0 — saveTestAtomically stamps it.
        val allScryfallIds = (cardCountsInHand.keys + cardCountsBottomed.keys).toSet()
        val cardStats = allScryfallIds.map { scryfallId ->
            PlaytestCardStatEntity(
                id                    = 0,
                playtestSessionId     = 0,
                scryfallId            = scryfallId,
                copiesInOpeningHand   = cardCountsInHand.getOrDefault(scryfallId, 0),
                copiesBottomedOnMulligan = cardCountsBottomed.getOrDefault(scryfallId, 0),
            )
        }

        runCatching {
            playtestDao.saveTestAtomically(session, cardStats)
        }.onFailure { e ->
            FirebaseCrashlytics.getInstance().apply {
                log("playtest_db_save_test_failed: deckId=$deckId format=$deckFormat mulligansUsed=$mulligansUsed drawCount=$configuredDrawCount")
                setCustomKey("playtest_deck_id", deckId)
                setCustomKey("playtest_format", deckFormat)
                setCustomKey("playtest_draw_count", configuredDrawCount)
                setCustomKey("playtest_mulligans_used", mulligansUsed)
                recordException(RuntimeException("[PlaytestRepository] saveTestAtomically failed", e))
            }
            throw e
        }.getOrThrow()
    }

    override suspend fun saveSurveyAnswers(
        playtestSessionId: Long,
        deckId: String,
        answers: PlaytestSurveyAnswers,
        questionTypes: Map<String, String>,
        cardReferences: Map<String, String?>,
    ) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val entities = answers.map { (questionId, answerValue) ->
            PlaytestSurveyAnswerEntity(
                id                = 0,
                playtestSessionId = playtestSessionId,
                questionId        = questionId,
                questionType      = questionTypes.getOrDefault(questionId, "UNKNOWN"),
                answer            = answerValue,
                cardReference     = cardReferences[questionId],
                deckId            = deckId,
                answeredAt        = now,
                updatedAt         = now,
            )
        }
        try {
            playtestDao.replacePlaytestSurveyAnswers(playtestSessionId, entities)
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().apply {
                log("playtest_db_save_survey_failed: sessionId=$playtestSessionId deckId=$deckId answerCount=${entities.size}")
                setCustomKey("playtest_deck_id", deckId)
                recordException(RuntimeException("[PlaytestRepository] replacePlaytestSurveyAnswers failed", e))
            }
            throw e
        }
    }

    override fun observeTestCountForDeck(deckId: String): Flow<Int> =
        playtestDao.observeTestCountForDeck(deckId)
}
