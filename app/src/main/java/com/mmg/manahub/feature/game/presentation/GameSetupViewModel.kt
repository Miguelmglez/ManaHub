package com.mmg.manahub.feature.game.presentation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.PlayerThemeColors
import com.mmg.manahub.core.voice.domain.VoiceLanguage
import com.mmg.manahub.core.voice.domain.VoiceModelRepository
import com.mmg.manahub.core.voice.domain.VoiceModelState
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.model.LayoutTemplate
import com.mmg.manahub.feature.game.domain.model.LayoutTemplates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  Data model
// ─────────────────────────────────────────────────────────────────────────────

private val DEFAULT_NAMES = setOf("Wizard", "Player 1", "Jugador 1", "Spieler 1")

data class PlayerConfig(
    val id:           Int,
    val name:         String,
    val theme:        PlayerThemeColors,
    val gridPosition: Int     = 0,
    val isAppUser:    Boolean = false,
    val isDefaultName: Boolean = false,
)

enum class LifeControlMode { SCROLL, TAP }

data class GameSettings(
    val landReminderEnabled: Boolean     = true,
    val voiceLandReminderEnabled: Boolean = false,
    val voiceEndTurnEnabled: Boolean = false,
    val voiceLanguage: VoiceLanguage = VoiceLanguage.ENGLISH,
    val lifeControlMode:     LifeControlMode = LifeControlMode.SCROLL,
)

data class GameSetupUiState(
    val selectedMode:   GameMode          = GameMode.STANDARD,
    val playerCount:    Int               = 2,
    val playerConfigs:  List<PlayerConfig> = emptyList(),
    val selectedLayout: LayoutTemplate    = LayoutTemplates.getDefaultLayout(2),
    val gameSettings:   GameSettings      = GameSettings(),
)

// ─────────────────────────────────────────────────────────────────────────────
//  ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class GameSetupViewModel(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val voiceModelRepository: VoiceModelRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(buildInitialState(GameMode.STANDARD, 2))
    val uiState: StateFlow<GameSetupUiState> = _uiState.asStateFlow()

    /** Per-language voice-model state, mirrored from the repository. */
    val voiceModelStates: StateFlow<Map<VoiceLanguage, VoiceModelState>> =
        voiceModelRepository.modelStates

    init {
        viewModelScope.launch {
            val profileName = userPreferencesDataStore.playerNameFlow.first()
            val defaultPlayerName = appContext.getString(R.string.game_setup_default_player_name)
            val defaultNames = setOf(defaultPlayerName, "Wizard", "Player 1", "Jugador 1", "Spieler 1")
            val isDefault   = profileName.isBlank() || profileName in defaultNames
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

    fun toggleLandReminder() {
        _uiState.update { state ->
            val newLandEnabled = !state.gameSettings.landReminderEnabled
            state.copy(
                gameSettings = state.gameSettings.copy(
                    landReminderEnabled = newLandEnabled,
                    // If we disable the land reminder, we must also disable the voice land reminder
                    voiceLandReminderEnabled = if (!newLandEnabled) false else state.gameSettings.voiceLandReminderEnabled
                )
            )
        }
    }

    fun toggleVoiceLandReminder() {
        // Only allow enabling if the selected language's model is ready; turning off always works.
        val enabling = !_uiState.value.gameSettings.voiceLandReminderEnabled
        val selectedLanguage = _uiState.value.gameSettings.voiceLanguage
        if (enabling && voiceModelStates.value[selectedLanguage] !is VoiceModelState.Ready) return
        _uiState.update { state ->
            val newVoiceEnabled = !state.gameSettings.voiceLandReminderEnabled
            state.copy(
                gameSettings = state.gameSettings.copy(
                    voiceLandReminderEnabled = newVoiceEnabled,
                    // If we enable the voice land reminder, we must also enable the visual land reminder
                    landReminderEnabled = if (newVoiceEnabled) true else state.gameSettings.landReminderEnabled,
                )
            )
        }
    }

    fun toggleVoiceEndTurn() {
        // Only allow enabling if the selected language's model is ready; turning off always works.
        val enabling = !_uiState.value.gameSettings.voiceEndTurnEnabled
        val selectedLanguage = _uiState.value.gameSettings.voiceLanguage
        if (enabling && voiceModelStates.value[selectedLanguage] !is VoiceModelState.Ready) return
        _uiState.update { it.copy(
            gameSettings = it.gameSettings.copy(voiceEndTurnEnabled = !it.gameSettings.voiceEndTurnEnabled)
        )}
    }

    /**
     * Selects [language] as the active recognition language. Only succeeds if that language's
     * model is [VoiceModelState.Ready]; otherwise the call is ignored.
     */
    fun setVoiceLanguage(language: VoiceLanguage) {
        if (voiceModelStates.value[language] !is VoiceModelState.Ready) return
        _uiState.update { it.copy(gameSettings = it.gameSettings.copy(voiceLanguage = language)) }
    }

    /** Downloads the voice model for [language]. */
    fun downloadVoiceModel(language: VoiceLanguage) {
        viewModelScope.launch { voiceModelRepository.download(language) }
    }

    /** Deletes the downloaded voice model for [language]. */
    fun deleteVoiceModel(language: VoiceLanguage) {
        viewModelScope.launch { voiceModelRepository.delete(language) }
    }

    fun setLifeControlMode(mode: LifeControlMode) {
        _uiState.update { it.copy(
            gameSettings = it.gameSettings.copy(lifeControlMode = mode)
        )}
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
