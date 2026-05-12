package com.mmg.manahub.feature.tournament

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.feature.game.PlayerConfig
import com.mmg.manahub.feature.game.model.GameMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TournamentViewModel @Inject constructor(
    private val repository:      TournamentRepository,
    savedStateHandle:            SavedStateHandle,
) : ViewModel() {

    private val tournamentId: Long =
        savedStateHandle.get<Long>("tournamentId") ?: 0L

    data class UiState(
        val tournament: TournamentEntity?          = null,
        val standings:  List<TournamentStanding>   = emptyList(),
        val matches:    List<TournamentMatchEntity> = emptyList(),
        val players:    List<TournamentPlayerEntity> = emptyList(),
        val nextMatch:  TournamentMatchEntity?     = null,
        val isFinished: Boolean                    = false,
        val isLoading:  Boolean                    = true,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadTournament()
    }

    private fun loadTournament() {
        viewModelScope.launch {
            combine(
                repository.observeTournament(tournamentId),
                repository.observeMatches(tournamentId),
                repository.observePlayers(tournamentId),
            ) { tournament, matches, players ->
                val standings  = repository.calculateStandings(tournamentId)
                val nextMatch  = matches.firstOrNull { it.status == "PENDING" }
                val isFinished = matches.isNotEmpty() && matches.all { it.status == "FINISHED" }
                _uiState.update { state ->
                    state.copy(
                        tournament = tournament,
                        matches    = matches,
                        players    = players,
                        standings  = standings,
                        nextMatch  = nextMatch,
                        isFinished = isFinished,
                        isLoading  = false,
                    )
                }
            }.collect()
        }
    }

    fun startNextMatch(onNavigateToGame: (matchId: Long) -> Unit) {
        viewModelScope.launch {
            val match = _uiState.value.nextMatch ?: return@launch
            repository.startMatch(match.id)
            onNavigateToGame(match.id)
        }
    }

    fun startMatch(matchId: Long, onNavigateToGame: (matchId: Long) -> Unit) {
        viewModelScope.launch {
            repository.startMatch(matchId)
            onNavigateToGame(matchId)
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
            repository.finishMatch(matchId, winnerId, sessionId, lifeTotals)
            val standings = repository.calculateStandings(tournamentId)
            _uiState.update { it.copy(standings = standings) }
            if (repository.isFinished(tournamentId)) {
                repository.finishTournament(tournamentId)
                _uiState.update { it.copy(isFinished = true) }
            }
        }
    }
}
