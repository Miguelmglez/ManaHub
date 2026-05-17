package com.mmg.manahub.feature.survey

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
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
import com.mmg.manahub.feature.survey.presentation.CardImpactRating
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

    @Test
    fun `init restores existing answers including CARD_IMPACT and FREE_TEXT`() = runTest(dispatcher) {
        coEvery { surveyAnswerDao.getAnswersForSession(sessionId) } returns listOf(
            SurveyAnswerEntity(sessionId = sessionId, questionId = "decisive_moment", questionType = "DECISIVE_MOMENT", answer = "KEY_TURN"),
            SurveyAnswerEntity(sessionId = sessionId, questionId = "card_impact", questionType = "CARD_IMPACT", answer = "KEY_CARD", cardReference = "card-a"),
            SurveyAnswerEntity(sessionId = sessionId, questionId = "free_notes", questionType = "FREE_TEXT", answer = "felt slow"),
        )
        val vm = buildVm()
        val state = vm.uiState.value
        assertEquals("KEY_TURN", state.answers["decisive_moment"])
        assertEquals(CardImpactRating.KEY_CARD, state.cardImpactSelections["card-a"])
        assertEquals("felt slow", state.freeNotes)
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
    fun `addExtraImpactCard dedupes by scryfallId`() = runTest(dispatcher) {
        val vm = buildVm()
        val card = fakeCard("card-a")
        vm.addExtraImpactCard(card)
        vm.addExtraImpactCard(card)
        assertEquals(1, vm.uiState.value.extraImpactCards.size)
    }

    @Test
    fun `removeImpactCard clears both selection and extra entry`() = runTest(dispatcher) {
        val vm = buildVm()
        val card = fakeCard("card-x")
        vm.addExtraImpactCard(card)
        vm.setCardImpact("card-x", CardImpactRating.WEAK)
        vm.removeImpactCard("card-x")
        assertTrue(vm.uiState.value.extraImpactCards.isEmpty())
        assertTrue(vm.uiState.value.cardImpactSelections.isEmpty())
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun buildVm() = SurveyViewModel(
        surveyAnswerDao = surveyAnswerDao,
        gameSessionDao = gameSessionDao,
        deckRepository = deckRepository,
        cardDao = cardDao,
        userPreferencesRepository = prefsRepository,
        context = context,
        ioDispatcher = dispatcher,
        savedStateHandle = SavedStateHandle(mapOf("sessionId" to sessionId, "mode" to "COMPLETE")),
    )

    private fun fakeSessionWithPlayers(surveyStatus: String = "PENDING"): GameSessionWithPlayers {
        val session = GameSessionEntity(
            id = sessionId,
            playedAt = 1_700_000_000_000L,
            durationMs = 1_320_000L,
            mode = "COMMANDER",
            totalTurns = 14,
            playerCount = 2,
            winnerId = 1,
            winnerName = "Me",
            surveyStatus = surveyStatus,
        )
        val players = listOf(
            PlayerSessionEntity(sessionId = sessionId, playerId = 1, playerName = "Me", finalLife = 30, finalPoison = 0, eliminationReason = null, isWinner = true),
            PlayerSessionEntity(sessionId = sessionId, playerId = 2, playerName = "Rival", finalLife = -3, finalPoison = 0, eliminationReason = "LIFE", isWinner = false),
        )
        return GameSessionWithPlayers(session, players)
    }

    private fun fakeCard(id: String) = com.mmg.manahub.core.domain.model.Card(
        scryfallId = id,
        name = "Stub $id",
        printedName = null,
        manaCost = null,
        cmc = 3.0,
        colors = emptyList(),
        colorIdentity = emptyList(),
        typeLine = "Creature",
        printedTypeLine = null,
        oracleText = null,
        printedText = null,
        keywords = emptyList(),
        power = null,
        toughness = null,
        loyalty = null,
        setCode = "",
        setName = "",
        collectorNumber = "",
        rarity = "",
        releasedAt = "",
        frameEffects = emptyList(),
        promoTypes = emptyList(),
        lang = "",
        imageNormal = null,
        imageArtCrop = null,
        imageBackNormal = null,
        priceUsd = null,
        priceUsdFoil = null,
        priceEur = null,
        priceEurFoil = null,
        legalityStandard = "",
        legalityPioneer = "",
        legalityModern = "",
        legalityCommander = "",
        flavorText = null,
        artist = null,
        scryfallUri = "",
    )
}
