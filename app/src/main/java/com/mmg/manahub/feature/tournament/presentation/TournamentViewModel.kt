package com.mmg.manahub.feature.tournament.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.feature.game.presentation.PlayerConfig
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.tournament.domain.usecase.CalculateStandingsUseCase
import com.mmg.manahub.feature.tournament.domain.usecase.GenerateNextRoundUseCase
import com.mmg.manahub.feature.tournament.domain.usecase.NextRoundResult
import com.mmg.manahub.feature.tournament.domain.usecase.RecordMatchResultUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TournamentViewModel @Inject constructor(
    private val repository:             TournamentRepository,
    private val generateNextRound:      GenerateNextRoundUseCase,
    private val calculateStandings:     CalculateStandingsUseCase,
    private val recordMatchResultUseCase: RecordMatchResultUseCase,
    savedStateHandle:                   SavedStateHandle,
) : ViewModel() {

    private val tournamentId: Long = checkNotNull(
        savedStateHandle.get<Long>("tournamentId")?.takeIf { it > 0L }
    ) { "TournamentViewModel requires a positive tournamentId" }

    data class UiState(
        val tournament:      TournamentEntity?            = null,
        val standings:       List<TournamentStanding>     = emptyList(),
        val matches:         List<TournamentMatchEntity>   = emptyList(),
        val players:         List<TournamentPlayerEntity>  = emptyList(),
        val nextMatch:       TournamentMatchEntity?        = null,
        val activeMatch:     TournamentMatchEntity?        = null,
        val isFinished:      Boolean                      = false,
        val isPaused:        Boolean                      = false,
        val isLoading:       Boolean                      = true,
        val isStartingMatch: Boolean                      = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadTournament()
    }

    private fun loadTournament() {
        viewModelScope.launch {
            // One-time auto-resume on screen open — MUST NOT be inside the combine transformer.
            val current = repository.observeTournament(tournamentId).first()
            if (current?.status == "SETUP" || current?.status == "PAUSED") {
                runCatching { repository.startTournament(tournamentId) }
            }

            combine(
                repository.observeTournament(tournamentId),
                repository.observeMatches(tournamentId),
                repository.observePlayers(tournamentId),
            ) { tournament, matches, players ->
                val standings   = calculateStandings(tournamentId)
                val nextMatch   = matches.firstOrNull { it.status == "PENDING" }
                val activeMatch = matches.firstOrNull { it.status == "ACTIVE" }
                val isFinished  = matches.isNotEmpty() && matches.all { it.status == "FINISHED" }
                val isPaused    = tournament?.status == "PAUSED"
                _uiState.update { state ->
                    state.copy(
                        tournament  = tournament,
                        matches     = matches,
                        players     = players,
                        standings   = standings,
                        nextMatch   = nextMatch,
                        activeMatch = activeMatch,
                        isFinished  = isFinished,
                        isPaused    = isPaused,
                        isLoading   = false,
                    )
                }
            }.distinctUntilChanged().collect {}
        }
    }

    fun pause() {
        viewModelScope.launch {
            runCatching { repository.pauseTournament(tournamentId) }
                .onSuccess { _uiState.update { it.copy(isPaused = true) } }
        }
    }

    fun startNextMatch(onNavigateToGame: (matchId: Long) -> Unit) {
        if (_uiState.value.isStartingMatch) return
        _uiState.update { it.copy(isStartingMatch = true) }
        viewModelScope.launch {
            val match = _uiState.value.nextMatch ?: run {
                _uiState.update { it.copy(isStartingMatch = false) }
                return@launch
            }
            FirebaseCrashlytics.getInstance().log("tournament_match_started: matchId=${match.id}")
            runCatching { repository.startMatch(match.id) }
                .onSuccess {
                    _uiState.update { it.copy(isStartingMatch = false) }
                    onNavigateToGame(match.id)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isStartingMatch = false) }
                    FirebaseCrashlytics.getInstance().apply {
                        log("tournament_match_start_failed: matchId=${match.id}")
                        setCustomKey("tournament_id", tournamentId)
                        recordException(e)
                    }
                }
        }
    }

    fun startMatch(matchId: Long, onNavigateToGame: (matchId: Long) -> Unit) {
        if (_uiState.value.isStartingMatch) return
        _uiState.update { it.copy(isStartingMatch = true) }
        viewModelScope.launch {
            runCatching { repository.startMatch(matchId) }
                .onSuccess {
                    _uiState.update { it.copy(isStartingMatch = false) }
                    onNavigateToGame(matchId)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isStartingMatch = false) }
                    FirebaseCrashlytics.getInstance().apply {
                        log("tournament_match_start_failed: matchId=$matchId")
                        setCustomKey("tournament_id", tournamentId)
                        recordException(e)
                    }
                }
        }
    }

    /** Resumes an ACTIVE match — navigates directly to the game screen without calling startMatch again. */
    fun resumeMatch(matchId: Long, onNavigateToGame: (matchId: Long) -> Unit) {
        onNavigateToGame(matchId)
    }

    /** Resets an ACTIVE match back to PENDING so it can be replayed or have its result recorded manually. */
    fun resetMatch(matchId: Long) {
        viewModelScope.launch {
            runCatching { repository.resetMatch(matchId) }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("tournament_reset_match_failed: matchId=$matchId")
                        setCustomKey("tournament_id", tournamentId)
                        recordException(e)
                    }
                }
        }
    }

    /**
     * Returns (tournamentPlayerIds, playerConfigs) for a given match, ready to pass to GameViewModel.
     */
    fun buildPlayerConfigsForMatch(matchId: Long): Pair<List<Long>, List<PlayerConfig>> {
        val state = _uiState.value
        val match = state.matches.find { it.id == matchId } ?: return Pair(emptyList(), emptyList())
        val ids = match.playerIds.trim('[', ']').split(",").mapNotNull { it.trim().toLongOrNull() }
        val configs = ids.mapIndexed { index, playerId ->
            val player = state.players.find { it.id == playerId }
            PlayerConfig(
                id        = index,
                name      = player?.playerName ?: "Wizard ${index + 1}",
                theme     = PlayerTheme.ALL[(player?.seed ?: index) % PlayerTheme.ALL.size],
                isAppUser = index == 0,
            )
        }
        return Pair(ids, configs)
    }

    fun getGameMode(): GameMode {
        return when (_uiState.value.tournament?.format?.uppercase()) {
            "COMMANDER" -> GameMode.COMMANDER
            else        -> GameMode.STANDARD
        }
    }

    fun recordMatchResultManual(matchId: Long, winnerId: Long) {
        recordMatchResult(matchId, winnerId, null, emptyMap())
    }

    fun recordDrawManual(matchId: Long) {
        viewModelScope.launch {
            runCatching {
                recordMatchResultUseCase.recordDraw(matchId, null, emptyMap())
                refreshAfterResult()
            }.onFailure { e ->
                FirebaseCrashlytics.getInstance().apply {
                    log("tournament_draw_save_failed: matchId=$matchId")
                    setCustomKey("tournament_id", tournamentId)
                    recordException(e)
                }
            }
        }
    }

    fun recordMatchResult(
        matchId:    Long,
        winnerId:   Long,
        sessionId:  Long?,
        lifeTotals: Map<Long, Int>,
    ) {
        viewModelScope.launch {
            runCatching {
                recordMatchResultUseCase.recordWin(matchId, winnerId, sessionId, lifeTotals)
                refreshAfterResult()
            }.onFailure { e ->
                FirebaseCrashlytics.getInstance().apply {
                    log("tournament_match_result_save_failed: matchId=$matchId winnerId=$winnerId")
                    setCustomKey("tournament_id", tournamentId)
                    recordException(e)
                }
            }
        }
    }

    private suspend fun refreshAfterResult() {
        val standings = calculateStandings(tournamentId)
        _uiState.update { it.copy(standings = standings) }

        when (generateNextRound(tournamentId)) {
            is NextRoundResult.TournamentFinished -> {
                repository.finishTournament(tournamentId)
                _uiState.update { it.copy(isFinished = true) }
            }
            is NextRoundResult.RoundGenerated -> { /* DB flows will emit new matches */ }
            is NextRoundResult.RoundNotComplete -> { /* current round still has pending matches */ }
        }
    }
}
