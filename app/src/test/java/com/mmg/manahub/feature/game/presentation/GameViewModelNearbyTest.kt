package com.mmg.manahub.feature.game.presentation

import com.mmg.manahub.core.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.nearby.domain.model.NearbyGameMessage
import com.mmg.manahub.core.nearby.domain.repository.NearbySessionRepository
import com.mmg.manahub.core.online.domain.usecase.AdvancePhaseUseCase
import com.mmg.manahub.core.online.domain.usecase.ConfirmDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.LeaveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.NextTurnUseCase
import com.mmg.manahub.core.online.domain.usecase.ObserveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.RevokeDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateCounterUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateCommanderDamageUseCase
import com.mmg.manahub.core.online.domain.usecase.ToggleLandPlayedUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateLifeUseCase
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.core.ui.theme.PlayerTheme
import androidx.lifecycle.SavedStateHandle
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelNearbyTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var analyticsHelper: AnalyticsHelper
    private lateinit var gameSessionRepo: GameSessionRepository
    private lateinit var tournamentRepo: TournamentRepository
    private lateinit var updateLifeUseCase: UpdateLifeUseCase
    private lateinit var advancePhaseUseCase: AdvancePhaseUseCase
    private lateinit var nextTurnUseCase: NextTurnUseCase
    private lateinit var observeSessionUseCase: ObserveSessionUseCase
    private lateinit var confirmDefeatUseCase: ConfirmDefeatUseCase
    private lateinit var revokeDefeatUseCase: RevokeDefeatUseCase
    private lateinit var leaveSessionUseCase: LeaveSessionUseCase
    private lateinit var nearbyRepo: NearbySessionRepository
    private lateinit var updateCounterUseCase: UpdateCounterUseCase
    private lateinit var updateCommanderDamageUseCase: UpdateCommanderDamageUseCase
    private lateinit var toggleLandPlayedUseCase: ToggleLandPlayedUseCase

    private lateinit var viewModel: GameViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        analyticsHelper = mockk(relaxed = true)
        gameSessionRepo = mockk(relaxed = true)
        tournamentRepo = mockk(relaxed = true)
        updateLifeUseCase = mockk(relaxed = true)
        advancePhaseUseCase = mockk(relaxed = true)
        nextTurnUseCase = mockk(relaxed = true)
        observeSessionUseCase = mockk(relaxed = true)
        confirmDefeatUseCase = mockk(relaxed = true)
        revokeDefeatUseCase = mockk(relaxed = true)
        leaveSessionUseCase = mockk(relaxed = true)
        nearbyRepo = mockk(relaxed = true)
        updateCounterUseCase = mockk(relaxed = true)
        updateCommanderDamageUseCase = mockk(relaxed = true)
        toggleLandPlayedUseCase = mockk(relaxed = true)

        val messagesFlow = MutableSharedFlow<NearbyGameMessage>()
        every { nearbyRepo.observeMessages() } returns messagesFlow
        every { nearbyRepo.observeConnectionEvents() } returns MutableSharedFlow()

        viewModel = GameViewModel(
            savedStateHandle             = SavedStateHandle(mapOf("mode" to GameMode.STANDARD.name, "playerCount" to 2)),
            analyticsHelper              = analyticsHelper,
            gameSessionRepo              = gameSessionRepo,
            tournamentRepo               = tournamentRepo,
            updateLifeUseCase            = updateLifeUseCase,
            advancePhaseUseCase          = advancePhaseUseCase,
            nextTurnUseCase              = nextTurnUseCase,
            observeSessionUseCase        = observeSessionUseCase,
            updateCounterUseCase         = updateCounterUseCase,
            updateCommanderDamageUseCase = updateCommanderDamageUseCase,
            confirmDefeatUseCase         = confirmDefeatUseCase,
            revokeDefeatUseCase          = revokeDefeatUseCase,
            leaveSessionUseCase          = leaveSessionUseCase,
            nearbyRepo                   = nearbyRepo,
            toggleLandPlayedUseCase      = toggleLandPlayedUseCase,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `changeLife broadcasts LifeChanged message over nearbyRepo`() {
        // Given
        val configs = listOf(
            PlayerConfig(0, "Player 1", PlayerTheme.ALL[0], isAppUser = true),
            PlayerConfig(1, "Player 2", PlayerTheme.ALL[1])
        )
        viewModel.initFromNearbySession("TEST12", true, 0, configs, GameMode.STANDARD)

        // When
        viewModel.changeLife(0, -5)

        // Then
        verify { nearbyRepo.sendMessage(NearbyGameMessage.LifeChanged(0, 35)) } // Standard starting life 40 - 5 = 35
    }

    @Test
    fun `advancePhase broadcasts PhaseChanged over nearbyRepo`() {
        val configs = listOf(
            PlayerConfig(0, "P1", PlayerTheme.ALL[0], isAppUser = true),
            PlayerConfig(1, "P2", PlayerTheme.ALL[1])
        )
        viewModel.initFromNearbySession("TEST12", true, 0, configs, GameMode.STANDARD)

        viewModel.advancePhase()
        
        // Initial phase is UNTAP, advances to UPKEEP
        verify { nearbyRepo.sendMessage(NearbyGameMessage.PhaseChanged("UPKEEP")) }
    }
}
