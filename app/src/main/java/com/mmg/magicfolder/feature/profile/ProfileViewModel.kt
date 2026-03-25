package com.mmg.magicfolder.feature.profile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.data.local.LanguagePreference
import com.mmg.magicfolder.core.domain.model.CollectionStats
import com.mmg.magicfolder.core.domain.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
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
    val stats:            CollectionStats? = null,
    val selectedTheme:    AppTheme         = AppTheme.NEON_VOID,
    val selectedLanguage: String           = "en",
    val isLoading:        Boolean          = true,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val statsRepo:    StatsRepository,
    private val langPref:     LanguagePreference,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        // Observe collection stats
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
    }

    fun selectLanguage(lang: String) {
        viewModelScope.launch { langPref.set(lang) }
    }

    fun selectTheme(theme: AppTheme) {
        if (!theme.isUnlocked) return
        _state.update { it.copy(selectedTheme = theme) }
        viewModelScope.launch {
            context.themeDataStore.edit { it[KEY_THEME] = theme.name }
        }
    }
}
