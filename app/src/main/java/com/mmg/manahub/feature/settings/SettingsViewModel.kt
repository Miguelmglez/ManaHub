package com.mmg.manahub.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.AppLanguage
import com.mmg.manahub.core.domain.model.CardLanguage
import com.mmg.manahub.core.domain.model.NewsLanguage
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPrefsDataStore: UserPreferencesDataStore,
    private val userPreferencesRepo:   UserPreferencesRepository,
//    private val langPref:              LanguagePreference
) : ViewModel() {

    private val _appLanguageChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val appLanguageChanged: SharedFlow<Unit> = _appLanguageChanged.asSharedFlow()
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _prefsState = MutableStateFlow(PreferencesState())
    val prefsState: StateFlow<PreferencesState> = _prefsState.asStateFlow()

    init {
        viewModelScope.launch {
            userPrefsDataStore.autoRefreshPricesFlow.collect { enabled ->
                _uiState.update { it.copy(autoRefreshPrices = enabled) }
            }
        }

        userPrefsDataStore.themeFlow
            .onEach { theme -> _uiState.update { it.copy(currentTheme = theme) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)


        userPreferencesRepo.preferencesFlow
            .onEach { prefs -> _prefsState.update { it.copy(userPreferences = prefs) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)
    }

    fun onAutoRefreshChanged(enabled: Boolean) {
        viewModelScope.launch {
            userPrefsDataStore.saveAutoRefreshPrices(enabled)
            _uiState.update { it.copy(autoRefreshPrices = enabled) }
        }
    }
    fun selectTheme(theme: AppTheme) {
        viewModelScope.launch {
            userPrefsDataStore.saveTheme(theme)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            userPreferencesRepo.setAppLanguage(language)
            _appLanguageChanged.emit(Unit)
        }
    }

    fun setCardLanguage(language: CardLanguage) {
        viewModelScope.launch { userPreferencesRepo.setCardLanguage(language) }
    }

    fun setNewsLanguages(languages: Set<NewsLanguage>) {
        if (languages.isEmpty()) return
        viewModelScope.launch { userPreferencesRepo.setNewsLanguages(languages) }
    }

    fun setPreferredCurrency(currency: PreferredCurrency) {
        viewModelScope.launch { userPreferencesRepo.setPreferredCurrency(currency) }
    }
}
