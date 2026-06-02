package com.mmg.manahub.feature.survey

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.data.local.dao.SurveyCardImpactDao
import com.mmg.manahub.core.data.local.entity.GameSessionEntity
import com.mmg.manahub.core.data.local.entity.GameSessionWithPlayers
import com.mmg.manahub.core.data.local.entity.PlayerSessionEntity
import com.mmg.manahub.core.data.local.entity.SurveyAnswerEntity
import com.mmg.manahub.core.data.local.entity.SurveyStatus
import com.mmg.manahub.core.domain.model.AppLanguage
import com.mmg.manahub.core.domain.model.CardLanguage
import com.mmg.manahub.core.domain.model.CollectionViewMode
import com.mmg.manahub.core.domain.model.NewsLanguage
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.model.UserPreferences
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.feature.survey.presentation.SurveyMode
import com.mmg.manahub.feature.survey.presentation.SurveyViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Uses [UnconfinedTestDispatcher] so coroutines launched from the VM run eagerly,
 * making state observable synchronously after each call. The DAO mocks use
 * `coEvery { ... } returns ...` which complete immediately, so we never need to
 * coordinate with the real Dispatchers.IO that `persistAnswers` switches to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SurveyViewModelTest {

    private val sessionId = 42L

    private lateinit var surveyAnswerDao: SurveyAnswerDao
    private lateinit var surveyCardImpactDao: SurveyCardImpactDao
    private lateinit var gameSessionDao: GameSessionDao
    private lateinit var deckRepository: DeckRepository
    private lateinit var cardDao: CardDao
    private lateinit var prefsRepository: UserPreferencesRepository
    private lateinit var context: Context

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)

        surveyAnswerDao = mockk(relaxUnitFun = true)
        surveyCardImpactDao = mockk(relaxUnitFun = true)
        gameSessionDao = mockk(relaxUnitFun = true)
        deckRepository = mockk()
        cardDao = mockk()
        prefsRepository = mockk()
        context = mockk(relaxed = true)

        every { context.getString(any()) } returns "stub"
        every { context.getString(any(), *anyVararg()) } returns "stub"
        every { context.createConfigurationContext(any()) } returns context

        every { prefsRepository.preferencesFlow } returns flowOf(
            UserPreferences(
                appLanguage = AppLanguage.ENGLISH,
                cardLanguage = CardLanguage.ENGLISH,
                newsLanguages = setOf(NewsLanguage.ENGLISH),
                preferredCurrency = PreferredCurrency.USD,
                collectionViewMode = CollectionViewMode.GRID,
            )
        )

        coEvery { gameSessionDao.getSessionById(sessionId) } returns fakeSessionWithPlayers()
        coEvery { surveyAnswerDao.getAnswersForSession(sessionId) } returns emptyList()
        every { surveyCardImpactDao.observeForSession(sessionId) } returns flowOf(emptyList())
        every { deckRepository.observeAllDecks() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init hydrates GameRecap from loaded session`() = runTest(dispatcher) {
        val vm = buildVm()
        val state = vm.uiState.value
        assertEquals(sessionId, state.recap?.sessionId)
        assertEquals(true, state.recap?.won)
        assertEquals("COMMANDER", state.recap?.mode)
    }

    // ── ADR-001 win/loss derivation (Bug #1, #4) ────────────────────────────────

    @Test
    fun `given_loseGame_survey_shows_loss_recap`() = runTest(dispatcher) {
        // Local seat is NOT the winner — opponent won. Recap must show a LOSS.
        coEvery { gameSessionDao.getSessionById(sessionId) } returns
            fakeSessionWithPlayers(localIsWinner = false)
        val vm = buildVm()
        val state = vm.uiState.value
        assertEquals(false, state.recap?.won)
        // winnerName reflects the actual winner seat (the rival), not the local player.
        assertEquals("Rival", state.recap?.winnerName)
        // Bug #4: opponentNames must exclude the local seat even after a loss.
        assertEquals(listOf("Rival"), state.recap?.opponentNames)
    }

    @Test
    fun `given_winGame_survey_shows_win_recap`() = runTest(dispatcher) {
        // Local seat IS the winner — recap must show a WIN.
        coEvery { gameSessionDao.getSessionById(sessionId) } returns
            fakeSessionWithPlayers(localIsWinner = true)
        val vm = buildVm()
        val state = vm.uiState.value
        assertEquals(true, state.recap?.won)
        assertEquals("Me", state.recap?.winnerName)
        assertEquals(listOf("Rival"), state.recap?.opponentNames)
    }

    @Test
    fun `given_draw_no_winner_survey_shows_draw`() = runTest(dispatcher) {
        // No seat is flagged as winner (draw). The local seat's isWinner is false, so
        // appUserWon falls back to false (safe default, loss-side recap). winnerName
        // falls back to the session's recorded winnerName.
        val session = GameSessionEntity(
            id = sessionId,
            playedAt = 1_700_000_000_000L,
            durationMs = 600_000L,
            mode = "STANDARD",
            totalTurns = 8,
            playerCount = 2,
            winnerId = -1,
            winnerName = "Draw",
            surveyStatus = "PENDING",
        )
        val players = listOf(
            PlayerSessionEntity(sessionId = sessionId, playerId = 1, playerName = "Me", finalLife = 0, finalPoison = 0, eliminationReason = "LIFE", isWinner = false, isLocal = true),
            PlayerSessionEntity(sessionId = sessionId, playerId = 2, playerName = "Rival", finalLife = 0, finalPoison = 0, eliminationReason = "LIFE", isWinner = false, isLocal = false),
        )
        coEvery { gameSessionDao.getSessionById(sessionId) } returns GameSessionWithPlayers(session, players)

        val vm = buildVm()
        val state = vm.uiState.value
        assertEquals(false, state.recap?.won)
        // No seat is the winner, so winnerName falls back to session.winnerName.
        assertEquals("Draw", state.recap?.winnerName)
        assertEquals(listOf("Rival"), state.recap?.opponentNames)
    }

    @Test
    fun `given_multiPlayer_local_player_loses_opponent_wins_recap_correct`() = runTest(dispatcher) {
        // 4-player game: local seat loses, one of three opponents wins.
        val session = GameSessionEntity(
            id = sessionId,
            playedAt = 1_700_000_000_000L,
            durationMs = 2_400_000L,
            mode = "COMMANDER",
            totalTurns = 20,
            playerCount = 4,
            winnerId = 3,
            winnerName = "Carol",
            surveyStatus = "PENDING",
        )
        val players = listOf(
            PlayerSessionEntity(sessionId = sessionId, playerId = 1, playerName = "Me", finalLife = -2, finalPoison = 0, eliminationReason = "LIFE", isWinner = false, isLocal = true),
            PlayerSessionEntity(sessionId = sessionId, playerId = 2, playerName = "Bob", finalLife = 0, finalPoison = 0, eliminationReason = "LIFE", isWinner = false, isLocal = false),
            PlayerSessionEntity(sessionId = sessionId, playerId = 3, playerName = "Carol", finalLife = 18, finalPoison = 0, eliminationReason = null, isWinner = true, isLocal = false),
            PlayerSessionEntity(sessionId = sessionId, playerId = 4, playerName = "Dave", finalLife = 0, finalPoison = 0, eliminationReason = "LIFE", isWinner = false, isLocal = false),
        )
        coEvery { gameSessionDao.getSessionById(sessionId) } returns GameSessionWithPlayers(session, players)

        val vm = buildVm()
        val state = vm.uiState.value
        assertEquals(false, state.recap?.won)
        assertEquals("Carol", state.recap?.winnerName)
        // All three non-local seats are opponents; the local seat is excluded.
        assertEquals(listOf("Bob", "Carol", "Dave"), state.recap?.opponentNames)
    }

    @Test
    fun `init restores existing generic answers and FREE_TEXT`() = runTest(dispatcher) {
        coEvery { surveyAnswerDao.getAnswersForSession(sessionId) } returns listOf(
            SurveyAnswerEntity(sessionId = sessionId, questionId = "decisive_moment", questionType = "DECISIVE_MOMENT", answer = "KEY_TURN"),
            SurveyAnswerEntity(sessionId = sessionId, questionId = "free_notes", questionType = "FREE_TEXT", answer = "felt slow"),
        )
        val vm = buildVm()
        val state = vm.uiState.value
        assertEquals("KEY_TURN", state.answers["decisive_moment"])
        assertEquals("felt slow", state.freeNotes)
    }

    @Test
    fun `init restores per-seat card impacts keyed by playerSessionId and cardId`() = runTest(dispatcher) {
        every { surveyCardImpactDao.observeForSession(sessionId) } returns flowOf(
            listOf(
                com.mmg.manahub.core.data.local.entity.SurveyCardImpactEntity(
                    sessionId = sessionId, playerSessionId = 7L, cardId = "card-a", impact = "MVP",
                ),
            ),
        )
        val vm = buildVm()
        assertEquals("MVP", vm.uiState.value.cardImpactRatings["7:card-a"])
    }

    @Test
    fun `complete writes COMPLETED status with completedAt`() = runTest(dispatcher) {
        val vm = buildVm()
        vm.setAnswer("decisive_moment", "KEY_TURN")
        vm.complete()

        coVerify(timeout = 2_000L) {
            gameSessionDao.updateSurveyStatus(
                sessionId = sessionId,
                status = SurveyStatus.COMPLETED.name,
                completedAt = match { it != null },
            )
        }
    }

    @Test
    fun `postpone writes PARTIAL status with null completedAt`() = runTest(dispatcher) {
        val vm = buildVm()
        vm.setAnswer("hand_quality", "4")
        vm.postpone()

        coVerify(timeout = 2_000L) {
            gameSessionDao.updateSurveyStatus(
                sessionId = sessionId,
                status = SurveyStatus.PARTIAL.name,
                completedAt = null,
            )
        }
    }

    @Test
    fun `skipAll marks SKIPPED only when current status is PENDING`() = runTest(dispatcher) {
        coEvery { gameSessionDao.getSessionById(sessionId) } returns fakeSessionWithPlayers(surveyStatus = "PENDING")
        val vm = buildVm()
        vm.skipAll()

        coVerify(timeout = 2_000L) {
            gameSessionDao.updateSurveyStatus(
                sessionId = sessionId,
                status = SurveyStatus.SKIPPED.name,
                completedAt = null,
            )
        }
    }

    @Test
    fun `skipAll does not overwrite PARTIAL status`() = runTest(dispatcher) {
        coEvery { gameSessionDao.getSessionById(sessionId) } returns fakeSessionWithPlayers(surveyStatus = "PARTIAL")
        val vm = buildVm()
        vm.skipAll()

        coVerify(exactly = 0, timeout = 2_000L) {
            gameSessionDao.updateSurveyStatus(
                sessionId = sessionId,
                status = SurveyStatus.SKIPPED.name,
                completedAt = null,
            )
        }
    }

    @Test
    fun `REVIEW mode propagates to state`() = runTest(dispatcher) {
        val vm = SurveyViewModel(
            surveyAnswerDao = surveyAnswerDao,
            surveyCardImpactDao = surveyCardImpactDao,
            gameSessionDao = gameSessionDao,
            deckRepository = deckRepository,
            cardDao = cardDao,
            userPreferencesRepository = prefsRepository,
            context = context,
            ioDispatcher = dispatcher,
            savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId, "mode" to "REVIEW")),
        )
        assertEquals(SurveyMode.REVIEW, vm.uiState.value.surveyMode)
    }

    @Test
    fun `setCardImpact stores rating keyed by playerSessionId and cardId`() = runTest(dispatcher) {
        val vm = buildVm()
        vm.setCardImpact(playerSessionId = 7L, cardId = "card-x", impact = "MVP")
        assertEquals("MVP", vm.uiState.value.cardImpactRatings["7:card-x"])
    }

    @Test
    fun `setCardImpact toggling the same impact clears the entry`() = runTest(dispatcher) {
        val vm = buildVm()
        vm.setCardImpact(playerSessionId = 7L, cardId = "card-x", impact = "MVP")
        // Tapping the same impact again returns the card to the neutral (unset) state.
        vm.setCardImpact(playerSessionId = 7L, cardId = "card-x", impact = "MVP")
        assertTrue(vm.uiState.value.cardImpactRatings.isEmpty())
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun buildVm() = SurveyViewModel(
        surveyAnswerDao = surveyAnswerDao,
        surveyCardImpactDao = surveyCardImpactDao,
        gameSessionDao = gameSessionDao,
        deckRepository = deckRepository,
        cardDao = cardDao,
        userPreferencesRepository = prefsRepository,
        context = context,
        ioDispatcher = dispatcher,
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId, "mode" to "COMPLETE")),
    )

    /**
     * Default fake: the local seat ("Me") is the winner — a WIN.
     *
     * The [localIsWinner] flag flips win/loss for the local seat while keeping a
     * non-null winner, exercising the ADR-001 fix (win/loss derived from `isLocal`,
     * not from `isWinner`).
     */
    private fun fakeSessionWithPlayers(
        surveyStatus: String = "PENDING",
        localIsWinner: Boolean = true,
    ): GameSessionWithPlayers {
        val session = GameSessionEntity(
            id = sessionId,
            playedAt = 1_700_000_000_000L,
            durationMs = 1_320_000L,
            mode = "COMMANDER",
            totalTurns = 14,
            playerCount = 2,
            winnerId = if (localIsWinner) 1 else 2,
            winnerName = if (localIsWinner) "Me" else "Rival",
            surveyStatus = surveyStatus,
        )
        val players = listOf(
            PlayerSessionEntity(
                sessionId = sessionId, playerId = 1, playerName = "Me",
                finalLife = if (localIsWinner) 30 else -3, finalPoison = 0,
                eliminationReason = if (localIsWinner) null else "LIFE",
                isWinner = localIsWinner, isLocal = true,
            ),
            PlayerSessionEntity(
                sessionId = sessionId, playerId = 2, playerName = "Rival",
                finalLife = if (localIsWinner) -3 else 30, finalPoison = 0,
                eliminationReason = if (localIsWinner) "LIFE" else null,
                isWinner = !localIsWinner, isLocal = false,
            ),
        )
        return GameSessionWithPlayers(session, players)
    }
}
