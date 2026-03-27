package com.mmg.magicfolder.feature.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.data.local.dao.DeckStatsRow
import com.mmg.magicfolder.core.data.local.entity.GameSessionWithPlayers
import com.mmg.magicfolder.core.data.local.LanguagePreference
import com.mmg.magicfolder.core.domain.model.CollectionStats
import com.mmg.magicfolder.core.domain.repository.GameSessionRepository
import com.mmg.magicfolder.core.domain.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── DataStore extension ───────────────────────────────────────────────────────

private val Context.themeDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "magic_prefs")

private val KEY_THEME = stringPreferencesKey("selected_theme")

// ── UiState ───────────────────────────────────────────────────────────────────

enum class AppTheme(val displayName: String, val isUnlocked: Boolean) {
    NEON_VOID("Neon Void",   isUnlocked = true),
    DAWN_REALM("Dawn Realm", isUnlocked = false),
    ARCANE_GRAY("Arcane",    isUnlocked = false),
}

data class ProfileUiState(
    // Collection
    val stats:            CollectionStats? = null,
    val selectedTheme:    AppTheme         = AppTheme.NEON_VOID,
    val selectedLanguage: String           = "en",
    val playerName:       String           = "Player 1",
    val isLoading:        Boolean          = true,
    // Game stats
    val totalGames:       Int              = 0,
    val totalWins:        Int              = 0,
    val avgLifeOnWin:     Double           = 0.0,
    val avgLifeOnLoss:    Double           = 0.0,
    val recentSessions:   List<GameSessionWithPlayers> = emptyList(),
    val deckStats:        List<DeckStatsRow>           = emptyList(),
) {
    val winRate: Float get() = if (totalGames > 0) totalWins.toFloat() / totalGames else 0f
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val statsRepo:       StatsRepository,
    private val gameSessionRepo: GameSessionRepository,
    private val langPref:        LanguagePreference,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        // Collection stats
        statsRepo.observeCollectionStats()
            .onEach { stats -> _state.update { it.copy(stats = stats, isLoading = false) } }
            .catch   { _state.update { it.copy(isLoading = false) } }
            .launchIn(viewModelScope)

        // Restore persisted theme
        context.themeDataStore.data
            .map { prefs -> prefs[KEY_THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } }
            .onEach { theme -> if (theme != null) _state.update { it.copy(selectedTheme = theme) } }
            .catch { /* ignore DataStore read errors */ }
            .launchIn(viewModelScope)

        // Observe persisted language
        langPref.languageFlow
            .onEach { lang -> _state.update { it.copy(selectedLanguage = lang) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        // Observe persisted player name
        langPref.playerNameFlow
            .onEach { name -> _state.update { it.copy(playerName = name) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        // Game stats
        gameSessionRepo.observeTotalGames()
            .onEach { total -> _state.update { it.copy(totalGames = total) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        // Wins — re-subscribe automatically when player name changes
        langPref.playerNameFlow
            .flatMapLatest { name -> gameSessionRepo.observeWins(name) }
            .onEach { wins -> _state.update { it.copy(totalWins = wins) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        gameSessionRepo.observeAvgLifeOnWin()
            .onEach { avg -> _state.update { it.copy(avgLifeOnWin = avg ?: 0.0) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        gameSessionRepo.observeAvgLifeOnLoss()
            .onEach { avg -> _state.update { it.copy(avgLifeOnLoss = avg ?: 0.0) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        gameSessionRepo.observeRecentSessions(5)
            .onEach { sessions -> _state.update { it.copy(recentSessions = sessions) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        gameSessionRepo.observeDeckStats()
            .onEach { ds -> _state.update { it.copy(deckStats = ds) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)
    }

    fun selectLanguage(lang: String) {
        viewModelScope.launch { langPref.set(lang) }
    }

    fun savePlayerName(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { langPref.savePlayerName(name.trim()) }
    }

    fun selectTheme(theme: AppTheme) {
        if (!theme.isUnlocked) return
        _state.update { it.copy(selectedTheme = theme) }
        viewModelScope.launch {
            context.themeDataStore.edit { it[KEY_THEME] = theme.name }
        }
    }
}
