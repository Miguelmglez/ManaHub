package com.mmg.magicfolder.feature.tournament

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.domain.repository.TournamentRepository
import com.mmg.magicfolder.core.ui.theme.PlayerTheme
import com.mmg.magicfolder.core.ui.theme.PlayerThemeColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
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
        val isCreating: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) =
        _uiState.update { it.copy(name = name) }

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
        _uiState.update { it.copy(players = list) }
    }

    fun updatePlayerName(index: Int, name: String) {
        val list = _uiState.value.players.toMutableList()
        list[index] = list[index].copy(name = name)
        _uiState.update { it.copy(players = list) }
    }

    fun updatePlayerTheme(index: Int, theme: PlayerThemeColors) {
        val list = _uiState.value.players.toMutableList()
        list[index] = list[index].copy(theme = theme)
        _uiState.update { it.copy(players = list) }
    }

    suspend fun createTournament(): Long {
        _uiState.update { it.copy(isCreating = true) }
        val state   = _uiState.value
        val players = state.players.map { config ->
            val pName = config.name.ifEmpty { "Player ${config.id + 1}" }
            val hex   = "#${config.theme.accent.toArgb().and(0xFFFFFF).toString(16).padStart(6, '0')}"
            pName to hex
        }
        return repository.createTournament(
            name              = state.name,
            format            = state.format,
            structure         = state.structure,
            players           = players,
            matchesPerPairing = state.matchesPerPairing,
            isRandomPairings  = state.isRandomPairings,
        ).also {
            _uiState.update { s -> s.copy(isCreating = false) }
        }
    }
}
