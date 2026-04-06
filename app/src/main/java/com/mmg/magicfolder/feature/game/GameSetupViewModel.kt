package com.mmg.magicfolder.feature.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.data.local.LanguagePreference
import com.mmg.magicfolder.core.ui.theme.PlayerTheme
import com.mmg.magicfolder.core.ui.theme.PlayerThemeColors
import com.mmg.magicfolder.feature.game.model.GameMode
import com.mmg.magicfolder.feature.game.model.LayoutTemplate
import com.mmg.magicfolder.feature.game.model.LayoutTemplates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─────────────────────────────────────────────────────────────────────────────
//  Data model
// ─────────────────────────────────────────────────────────────────────────────

private val DEFAULT_NAMES = setOf("Player 1", "Jugador 1", "Spieler 1")

data class PlayerConfig(
    val id:           Int,
    val name:         String,
    val theme:        PlayerThemeColors,
    val gridPosition: Int     = 0,
    val isAppUser:    Boolean = false,
    val isDefaultName: Boolean = false,
)

data class GameSetupUiState(
    val selectedMode:   GameMode          = GameMode.STANDARD,
    val playerCount:    Int               = 2,
    val playerConfigs:  List<PlayerConfig> = emptyList(),
    val selectedLayout: LayoutTemplate    = LayoutTemplates.getDefaultLayout(2),
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

@HiltViewModel
class GameSetupViewModel @Inject constructor(
    private val languagePreference: LanguagePreference,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildInitialState(GameMode.STANDARD, 2))
    val uiState: StateFlow<GameSetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profileName = languagePreference.playerNameFlow.first()
            val isDefault   = profileName.isBlank() || profileName in DEFAULT_NAMES
            _uiState.update { state ->
                val configs = state.playerConfigs.toMutableList()
                if (configs.isNotEmpty()) {
                    configs[0] = configs[0].copy(
                        name          = profileName,
                        isAppUser     = true,
                        isDefaultName = isDefault,
                    )
                }
                state.copy(playerConfigs = configs)
            }
        }
    }

    fun onModeChange(mode: GameMode) {
        _uiState.update { it.copy(selectedMode = mode) }
    }

    fun onPlayerCountChange(count: Int) {
        val clampedCount = count.coerceIn(2, 6)
        val current = _uiState.value.playerConfigs
        val configs = List(clampedCount) { i ->
            current.getOrNull(i) ?: PlayerConfig(
                id            = i,
                name          = "",
                theme         = PlayerTheme.ALL[i % PlayerTheme.ALL.size],
                gridPosition  = i,
                isAppUser     = false,
                isDefaultName = true,
            )
        }
        _uiState.update { it.copy(
            playerCount    = clampedCount,
            playerConfigs  = configs,
            selectedLayout = LayoutTemplates.getDefaultLayout(clampedCount),
        )}
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
                id            = i,
                name          = "",
                theme         = PlayerTheme.ALL[i % PlayerTheme.ALL.size],
                gridPosition  = i,
                isAppUser     = i == 0,
                isDefaultName = true,
            )
        }
        return GameSetupUiState(
            selectedMode   = mode,
            playerCount    = count,
            playerConfigs  = configs,
            selectedLayout = LayoutTemplates.getDefaultLayout(count),
        )
    }
}
