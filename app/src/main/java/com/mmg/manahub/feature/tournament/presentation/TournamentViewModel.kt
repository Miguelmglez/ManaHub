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
    private val repository:      TournamentRepository,
    savedStateHandle:            SavedStateHandle,
) : ViewModel() {

    private val tournamentId: Long = checkNotNull(
        savedStateHandle.get<Long>("tournamentId")?.takeIf { it > 0L }
    ) { "TournamentViewModel requires a positive tournamentId" }

    data class UiState(
        val tournament:     TournamentEntity?           = null,
        val standings:      List<TournamentStanding>    = emptyList(),
        val matches:        List<TournamentMatchEntity>  = emptyList(),
        val players:        List<TournamentPlayerEntity> = emptyList(),
        val nextMatch:      TournamentMatchEntity?      = null,
        val isFinished:     Boolean                     = false,
        val isPaused:       Boolean                     = false,
        val isLoading:      Boolean                     = true,
        val isStartingMatch: Boolean                    = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadTournament()
    }

    private fun loadTournament() {
        viewModelScope.launch {
            // One-time auto-resume on screen open — MUST NOT be inside the combine transformer.
            // Putting startTournament inside combine creates a feedback loop: the DB write
            // triggers a new emission which calls startTournament again, infinitely.
            val current = repository.observeTournament(tournamentId).first()
            if (current?.status == "SETUP" || current?.status == "PAUSED") {
                runCatching { repository.startTournament(tournamentId) }
            }

            combine(
                repository.observeTournament(tournamentId),
                repository.observeMatches(tournamentId),
                repository.observePlayers(tournamentId),
            ) { tournament, matches, players ->
                val standings  = repository.calculateStandings(tournamentId)
                val nextMatch  = matches.firstOrNull { it.status == "PENDING" }
                val isFinished = matches.isNotEmpty() && matches.all { it.status == "FINISHED" }
                val isPaused   = tournament?.status == "PAUSED"
                _uiState.update { state ->
                    state.copy(
                        tournament = tournament,
                        matches    = matches,
                        players    = players,
                        standings  = standings,
                        nextMatch  = nextMatch,
                        isFinished = isFinished,
                        isPaused   = isPaused,
                        isLoading  = false,
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

    fun recordMatchResult(
        matchId:    Long,
        winnerId:   Long,
        sessionId:  Long?,
        lifeTotals: Map<Long, Int>,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.finishMatch(matchId, winnerId, sessionId, lifeTotals)
                val standings = repository.calculateStandings(tournamentId)
                _uiState.update { it.copy(standings = standings) }
                if (repository.isFinished(tournamentId)) {
                    repository.finishTournament(tournamentId)
                    _uiState.update { it.copy(isFinished = true) }
                }
            }.onFailure { e ->
                FirebaseCrashlytics.getInstance().apply {
                    log("tournament_match_result_save_failed: matchId=$matchId winnerId=$winnerId")
                    setCustomKey("tournament_id", tournamentId)
                    recordException(e)
                }
            }
        }
    }
}
