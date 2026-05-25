package com.mmg.manahub.feature.tournament.presentation

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.PlayerThemeColors
import com.mmg.manahub.feature.tournament.domain.model.PlayerConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TournamentSetupViewModel @Inject constructor(
    private val repository: TournamentRepository,
) : ViewModel() {

    data class UiState(
        val name:              String         = "Tournament",
        val format:            String         = "COMMANDER",
        val structure:         String         = "ROUND_ROBIN",
        val matchesPerPairing: Int            = 1,
        val isRandomPairings:  Boolean        = true,
        val players:           List<PlayerConfig> = List(4) { i ->
            PlayerConfig(
                id    = i,
                name  = "",
                theme = PlayerTheme.ALL[i % PlayerTheme.ALL.size],
            )
        },
        val isCreating: Boolean  = false,
        val error:      String?  = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _navigationEvent = Channel<Long>(Channel.BUFFERED)
    val navigationEvent = _navigationEvent.receiveAsFlow()

    private companion object {
        const val MAX_TOURNAMENT_NAME_LENGTH = 60
        const val MAX_PLAYER_NAME_LENGTH     = 30
    }

    fun onNameChange(name: String) =
        _uiState.update { it.copy(name = name.take(MAX_TOURNAMENT_NAME_LENGTH), error = null) }

    fun onFormatChange(format: String) =
        _uiState.update { it.copy(format = format) }

    fun onStructureChange(structure: String) =
        _uiState.update { it.copy(structure = structure) }

    fun onMatchesPerPairingChange(n: Int) =
        _uiState.update { it.copy(matchesPerPairing = n.coerceIn(1, 3)) }

    fun onRandomPairingsChange(random: Boolean) =
        _uiState.update { it.copy(isRandomPairings = random) }

    fun addPlayer() {
        val current = _uiState.value.players
        if (current.size >= 10) return
        val usedThemes = current.map { it.theme }
        val nextTheme  = PlayerTheme.ALL.firstOrNull { it !in usedThemes }
            ?: PlayerTheme.ALL[current.size % PlayerTheme.ALL.size]
        _uiState.update { state ->
            state.copy(
                players = current + PlayerConfig(
                    id    = current.size,
                    name  = "",
                    theme = nextTheme,
                )
            )
        }
    }

    fun removePlayer(index: Int) {
        val list = _uiState.value.players.toMutableList()
        if (list.size <= 2) return
        list.removeAt(index)
        _uiState.update { it.copy(players = list.mapIndexed { i, p -> p.copy(id = i) }) }
    }

    fun updatePlayerName(index: Int, name: String) {
        val list = _uiState.value.players.toMutableList()
        list[index] = list[index].copy(name = name.take(MAX_PLAYER_NAME_LENGTH))
        _uiState.update { it.copy(players = list) }
    }

    fun updatePlayerTheme(index: Int, theme: PlayerThemeColors) {
        val list = _uiState.value.players.toMutableList()
        list[index] = list[index].copy(theme = theme)
        _uiState.update { it.copy(players = list) }
    }

    fun createTournament() {
        val state = _uiState.value
        if (state.isCreating) return
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Tournament name cannot be empty") }
            return
        }
        if (state.structure != "ROUND_ROBIN" && state.players.size % 2 != 0) {
            _uiState.update { it.copy(error = "Swiss and Single Elimination require an even number of players") }
            return
        }
        _uiState.update { it.copy(isCreating = true, error = null) }
        viewModelScope.launch {
            val players = state.players.map { config ->
                val pName = config.name.ifEmpty { "Wizard ${config.id + 1}" }
                val hex   = "#${config.theme.accent.toArgb().and(0xFFFFFF).toString(16).padStart(6, '0')}"
                pName to hex
            }
            runCatching {
                repository.createTournament(
                    name              = state.name,
                    format            = state.format,
                    structure         = state.structure,
                    players           = players,
                    matchesPerPairing = state.matchesPerPairing,
                    isRandomPairings  = state.isRandomPairings,
                )
            }.onSuccess { id ->
                _uiState.update { it.copy(isCreating = false) }
                _navigationEvent.send(id)
            }.onFailure { e ->
                _uiState.update { it.copy(isCreating = false, error = e.message ?: "Failed to create tournament") }
            }
        }
    }
}
