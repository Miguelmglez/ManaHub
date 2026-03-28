package com.mmg.magicfolder.feature.game

import androidx.lifecycle.ViewModel
import com.mmg.magicfolder.core.ui.theme.PlayerTheme
import com.mmg.magicfolder.core.ui.theme.PlayerThemeColors
import com.mmg.magicfolder.feature.game.model.GameMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
//  Data model
// ─────────────────────────────────────────────────────────────────────────────

data class PlayerConfig(
    val id:         Int,
    val name:       String,
    val theme:      PlayerThemeColors,
    val gridPosition: Int = 0,
)

data class GameSetupUiState(
    val selectedMode:  GameMode          = GameMode.STANDARD,
    val playerCount:   Int               = 2,
    val playerConfigs: List<PlayerConfig> = emptyList(),
) {
    init {
        // Ensure configs always match playerCount on construction
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class GameSetupViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(buildInitialState(GameMode.STANDARD, 2))
    val uiState: StateFlow<GameSetupUiState> = _uiState.asStateFlow()

    fun onModeChange(mode: GameMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun onPlayerCountChange(count: Int) {
        val current = _uiState.value.playerConfigs
        val configs = List(count) { i ->
            current.getOrNull(i) ?: PlayerConfig(
                id           = i,
                name         = "",
                theme        = PlayerTheme.ALL[i % PlayerTheme.ALL.size],
                gridPosition = i,
            )
        }
        _uiState.update { it.copy(playerCount = count, playerConfigs = configs) }
    }

    fun updatePlayerName(index: Int, name: String) {
        val configs = _uiState.value.playerConfigs.toMutableList()
        if (index in configs.indices) {
            configs[index] = configs[index].copy(name = name)
            _uiState.update { it.copy(playerConfigs = configs) }
        }
    }

    fun updatePlayerTheme(index: Int, theme: PlayerThemeColors) {
        val configs = _uiState.value.playerConfigs.toMutableList()
        if (index in configs.indices) {
            configs[index] = configs[index].copy(theme = theme)
            _uiState.update { it.copy(playerConfigs = configs) }
        }
    }


    private fun buildInitialState(mode: GameMode, count: Int): GameSetupUiState {
        val configs = List(count) { i ->
            PlayerConfig(
                id           = i,
                name         = "",
                theme        = PlayerTheme.ALL[i % PlayerTheme.ALL.size],
                gridPosition = i,
            )
        }
        return GameSetupUiState(selectedMode = mode, playerCount = count, playerConfigs = configs)
    }
}
