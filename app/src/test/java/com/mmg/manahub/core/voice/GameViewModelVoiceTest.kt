package com.mmg.manahub.core.voice

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.nearby.domain.repository.NearbySessionRepository
import com.mmg.manahub.core.online.domain.usecase.AdvancePhaseUseCase
import com.mmg.manahub.core.online.domain.usecase.ConfirmDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.LeaveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.NextTurnUseCase
import com.mmg.manahub.core.online.domain.usecase.ObserveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.RevokeDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.ToggleLandPlayedUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateCommanderDamageUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateCounterUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateLifeUseCase
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.voice.domain.VoiceCommand
import com.mmg.manahub.core.voice.domain.VoiceCommandRecognizer
import com.mmg.manahub.core.voice.domain.VoiceLanguage
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.usecase.EvaluatePlayerEliminationUseCase
import com.mmg.manahub.feature.game.presentation.GameSettings
import com.mmg.manahub.feature.game.presentation.GameViewModel
import com.mmg.manahub.feature.game.presentation.PlayerConfig
import com.mmg.manahub.feature.tournament.domain.usecase.RecordMatchResultUseCase
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [GameViewModel]'s voice command integration.
 *
 * GROUP 1 — PlayLand command toggles land state when voice is enabled
 * GROUP 2 — PlayLand is idempotent when land already played
 * GROUP 3 — voiceCommandEvents emits exactly once per land toggle
 * GROUP 4 — startVoiceListening is a no-op when voice disabled
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelVoiceTest {

    // ── Dispatcher ─────────────────────────────────────────────────────────────

    // UnconfinedTestDispatcher eagerly executes coroutines so the SharedFlow collector
    // inside GameViewModel.init is subscribed before any emissions in the test body.
    private val testDispatcher = UnconfinedTestDispatcher()

    // ── Fake voice recognizer ──────────────────────────────────────────────────

    private inner class FakeVoiceCommandRecognizer : VoiceCommandRecognizer {
        val commandFlow = MutableSharedFlow<VoiceCommand>(extraBufferCapacity = 8)
        private val _isListening = MutableStateFlow(false)

        override val commands: Flow<VoiceCommand> = commandFlow.asSharedFlow()
        override val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

        var startCalled = false

        override suspend fun start(
            enabledCommands: Set<VoiceCommand>,
            language: VoiceLanguage,
        ) {
            startCalled = true
            _isListening.value = true
        }

        override fun stop() { _isListening.value = false }
        override fun release() { _isListening.value = false }
    }

    private lateinit var fakeRecognizer: FakeVoiceCommandRecognizer

    // ── Mocks ──────────────────────────────────────────────────────────────────

    private val gameSessionRepo            = mockk<GameSessionRepository>(relaxed = true)
    private val tournamentRepo             = mockk<TournamentRepository>(relaxed = true)
    private val analyticsHelper            = mockk<AnalyticsHelper>(relaxed = true)
    private val observeSessionUseCase      = mockk<ObserveSessionUseCase>(relaxed = true)
    private val updateLifeUseCase          = mockk<UpdateLifeUseCase>(relaxed = true)
    private val advancePhaseUseCase        = mockk<AdvancePhaseUseCase>(relaxed = true)
    private val nextTurnUseCase            = mockk<NextTurnUseCase>(relaxed = true)
    private val updateCounterUseCase       = mockk<UpdateCounterUseCase>(relaxed = true)
    private val updateCommanderDamageUseCase = mockk<UpdateCommanderDamageUseCase>(relaxed = true)
    private val confirmDefeatUseCase       = mockk<ConfirmDefeatUseCase>(relaxed = true)
    private val revokeDefeatUseCase        = mockk<RevokeDefeatUseCase>(relaxed = true)
    private val leaveSessionUseCase        = mockk<LeaveSessionUseCase>(relaxed = true)
    private val nearbyRepo                 = mockk<NearbySessionRepository>(relaxed = true)
    private val toggleLandPlayedUseCase    = mockk<ToggleLandPlayedUseCase>(relaxed = true)
    private val recordMatchResultUseCase   = mockk<RecordMatchResultUseCase>(relaxed = true)
    private val appContext                 = mockk<Context>(relaxed = true)

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun buildViewModel(voiceEnabled: Boolean = true): GameViewModel {
        fakeRecognizer = FakeVoiceCommandRecognizer()
        val handle = SavedStateHandle(mapOf("mode" to GameMode.STANDARD.name, "playerCount" to 2))
        val vm = GameViewModel(
            savedStateHandle             = handle,
            gameSessionRepo              = gameSessionRepo,
            tournamentRepo               = tournamentRepo,
            recordMatchResultUseCase     = recordMatchResultUseCase,
            analyticsHelper              = analyticsHelper,
            observeSessionUseCase        = observeSessionUseCase,
            updateLifeUseCase            = updateLifeUseCase,
            advancePhaseUseCase          = advancePhaseUseCase,
            nextTurnUseCase              = nextTurnUseCase,
            updateCounterUseCase         = updateCounterUseCase,
            updateCommanderDamageUseCase = updateCommanderDamageUseCase,
            confirmDefeatUseCase         = confirmDefeatUseCase,
            revokeDefeatUseCase          = revokeDefeatUseCase,
            leaveSessionUseCase          = leaveSessionUseCase,
            nearbyRepo                   = nearbyRepo,
            toggleLandPlayedUseCase           = toggleLandPlayedUseCase,
            voiceCommandRecognizer            = fakeRecognizer,
            evaluatePlayerEliminationUseCase  = EvaluatePlayerEliminationUseCase(),
            appContext                        = appContext,
        )
        val configs = listOf(
            PlayerConfig(0, "Mage", PlayerTheme.ALL[0], isAppUser = true),
            PlayerConfig(1, "Rival", PlayerTheme.ALL[1], isAppUser = false),
        )
        vm.initFromConfigs(
            configs  = configs,
            mode     = GameMode.STANDARD,
            settings = GameSettings(voiceLandReminderEnabled = voiceEnabled),
        )
        return vm
    }

    // ── Setup / Teardown ───────────────────────────────────────────────────────

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        coEvery { gameSessionRepo.saveGameSession(any()) } returns 1L
        every { nearbyRepo.observeMessages() } returns MutableSharedFlow()
        every { nearbyRepo.observeConnectionEvents() } returns MutableSharedFlow()
        every { appContext.getString(any()) } returns "Player"
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic(FirebaseCrashlytics::class)
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 1 — PlayLand command toggles land state when voice is enabled
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given voice enabled when PlayLand emitted then active player is added to hasPlayedLand`() = runTest {
        val vm = buildViewModel(voiceEnabled = true)
        val activeId = vm.uiState.value.activePlayerId

        assertFalse(
            "No land should be played before voice command",
            activeId in vm.uiState.value.hasPlayedLand,
        )

        fakeRecognizer.commandFlow.emit(VoiceCommand.PlayLand)
        advanceUntilIdle()

        assertTrue(
            "Active player must be in hasPlayedLand after PlayLand voice command",
            activeId in vm.uiState.value.hasPlayedLand,
        )
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 2 — PlayLand is idempotent when land already played
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given land already played when PlayLand emitted again then hasPlayedLand unchanged`() = runTest {
        val vm = buildViewModel(voiceEnabled = true)
        val activeId = vm.uiState.value.activePlayerId

        // First command: toggles land ON
        fakeRecognizer.commandFlow.emit(VoiceCommand.PlayLand)
        advanceUntilIdle()
        assertTrue(activeId in vm.uiState.value.hasPlayedLand)

        // Second command: must be a no-op (land already played)
        fakeRecognizer.commandFlow.emit(VoiceCommand.PlayLand)
        advanceUntilIdle()

        assertTrue(
            "hasPlayedLand must still contain activeId — duplicate emission must be rejected",
            activeId in vm.uiState.value.hasPlayedLand,
        )
    }

    @Test
    fun `given land already played when PlayLand emitted again then voiceCommandEvents does not emit`() = runTest {
        val vm = buildViewModel(voiceEnabled = true)

        // First command fires the event
        fakeRecognizer.commandFlow.emit(VoiceCommand.PlayLand)
        advanceUntilIdle()

        // Collect events AFTER the first emission has been consumed
        vm.voiceCommandEvents.test {
            // Second emission: land already played → no new event
            fakeRecognizer.commandFlow.emit(VoiceCommand.PlayLand)
            advanceUntilIdle()
            expectNoEvents()
            cancel()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 3 — voiceCommandEvents emits exactly once on successful land toggle
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given voice enabled when PlayLand emitted then voiceCommandEvents emits PlayLand once`() = runTest {
        val vm = buildViewModel(voiceEnabled = true)

        vm.voiceCommandEvents.test {
            fakeRecognizer.commandFlow.emit(VoiceCommand.PlayLand)
            advanceUntilIdle()

            val event = awaitItem()
            assertTrue("Event must be PlayLand", event is VoiceCommand.PlayLand)
            expectNoEvents()
            cancel()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GROUP 4 — startVoiceListening is a no-op when voice is disabled
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `given voice disabled when startVoiceListening called then recognizer start is not called`() = runTest {
        val vm = buildViewModel(voiceEnabled = false)

        vm.startVoiceListening()
        advanceUntilIdle()

        assertFalse(
            "recognizer.start() must not be called when voiceLandReminderEnabled = false",
            fakeRecognizer.startCalled,
        )
    }

    @Test
    fun `given voice disabled when PlayLand emitted then hasPlayedLand remains empty`() = runTest {
        val vm = buildViewModel(voiceEnabled = false)
        val activeId = vm.uiState.value.activePlayerId

        fakeRecognizer.commandFlow.emit(VoiceCommand.PlayLand)
        advanceUntilIdle()

        assertFalse(
            "Voice-disabled game must ignore PlayLand command",
            activeId in vm.uiState.value.hasPlayedLand,
        )
    }

    @Test
    fun `given voice enabled when startVoiceListening called then recognizer start is called`() = runTest {
        val vm = buildViewModel(voiceEnabled = true)

        vm.startVoiceListening()
        advanceUntilIdle()

        assertTrue(
            "recognizer.start() must be called when voiceLandReminderEnabled = true",
            fakeRecognizer.startCalled,
        )
    }
}
