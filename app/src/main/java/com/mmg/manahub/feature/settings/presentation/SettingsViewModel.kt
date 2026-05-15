package com.mmg.manahub.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.AppLanguage
import com.mmg.manahub.core.domain.model.CardLanguage
import com.mmg.manahub.core.domain.model.NewsLanguage
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPrefsDataStore: UserPreferencesDataStore,
    private val userPreferencesRepo: UserPreferencesRepository,
    private val analyticsHelper: AnalyticsHelper,
    private val authRepository: AuthRepository,
    private val userProfileDataSource: UserProfileDataSource,
//    private val langPref:              LanguagePreference
) : ViewModel() {

    private val _appLanguageChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val appLanguageChanged: SharedFlow<Unit> = _appLanguageChanged.asSharedFlow()
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _prefsState = MutableStateFlow(PreferencesState())
    val prefsState: StateFlow<PreferencesState> = _prefsState.asStateFlow()

    init {
        userPrefsDataStore.themeFlow
            .onEach { theme -> _uiState.update { it.copy(currentTheme = theme) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        userPreferencesRepo.preferencesFlow
            .onEach { prefs -> _prefsState.update { it.copy(userPreferences = prefs) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        // ── Privacy flows — keep UI in sync with the locally cached values ───
        userPrefsDataStore.collectionPublicFlow
            .onEach { value -> _uiState.update { it.copy(collectionPublic = value) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        userPrefsDataStore.wishlistPublicFlow
            .onEach { value -> _uiState.update { it.copy(wishlistPublic = value) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)

        userPrefsDataStore.tradeListPublicFlow
            .onEach { value -> _uiState.update { it.copy(tradeListPublic = value) } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)
    }

    fun selectTheme(theme: AppTheme) {
        viewModelScope.launch {
            analyticsHelper.logEvent("theme_selected", mapOf("theme" to theme))
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

    // ── Privacy settings ──────────────────────────────────────────────────────

    /**
     * Optimistically updates [collectionPublic] in the local DataStore, then syncs to Supabase.
     * Reverts the local value and shows an error toast if the server call fails.
     *
     * @param value The new visibility value for the user's card collection.
     * @param errorMsg Localised error string to display on failure (supplied by the caller so the
     *   ViewModel remains Context-free).
     */
    fun setCollectionPublic(value: Boolean, errorMsg: String) {
        viewModelScope.launch {
            // Read the true current value from DataStore — not from _uiState which may already
            // reflect a previous optimistic write from a concurrent rapid-toggle coroutine.
            val previous = userPrefsDataStore.collectionPublicFlow.first()
            userPrefsDataStore.saveCollectionPublic(value)
            val userId = authRepository.getCurrentUser()?.id ?: run {
                userPrefsDataStore.saveCollectionPublic(previous)
                return@launch
            }
            val result = userProfileDataSource.updatePrivacySettings(
                userId = userId,
                collectionPublic = value,
            )
            if (result.isFailure) {
                userPrefsDataStore.saveCollectionPublic(previous)
                _uiState.update { it.copy(privacyToastMessage = errorMsg, privacyToastIsError = true) }
            }
        }
    }

    /**
     * Optimistically updates [wishlistPublic] in the local DataStore, then syncs to Supabase.
     * Reverts the local value and shows an error toast if the server call fails.
     *
     * @param value The new visibility value for the user's wishlist.
     * @param errorMsg Localised error string to display on failure.
     */
    fun setWishlistPublic(value: Boolean, errorMsg: String) {
        viewModelScope.launch {
            val previous = userPrefsDataStore.wishlistPublicFlow.first()
            userPrefsDataStore.saveWishlistPublic(value)
            val userId = authRepository.getCurrentUser()?.id ?: run {
                userPrefsDataStore.saveWishlistPublic(previous)
                return@launch
            }
            val result = userProfileDataSource.updatePrivacySettings(
                userId = userId,
                wishlistPublic = value,
            )
            if (result.isFailure) {
                userPrefsDataStore.saveWishlistPublic(previous)
                _uiState.update { it.copy(privacyToastMessage = errorMsg, privacyToastIsError = true) }
            }
        }
    }

    /**
     * Optimistically updates [tradeListPublic] in the local DataStore, then syncs to Supabase.
     * Reverts the local value and shows an error toast if the server call fails.
     *
     * @param value The new visibility value for the user's trade list.
     * @param errorMsg Localised error string to display on failure.
     */
    fun setTradeListPublic(value: Boolean, errorMsg: String) {
        viewModelScope.launch {
            val previous = userPrefsDataStore.tradeListPublicFlow.first()
            userPrefsDataStore.saveTradeListPublic(value)
            val userId = authRepository.getCurrentUser()?.id ?: run {
                userPrefsDataStore.saveTradeListPublic(previous)
                return@launch
            }
            val result = userProfileDataSource.updatePrivacySettings(
                userId = userId,
                tradeListPublic = value,
            )
            if (result.isFailure) {
                userPrefsDataStore.saveTradeListPublic(previous)
                _uiState.update { it.copy(privacyToastMessage = errorMsg, privacyToastIsError = true) }
            }
        }
    }

    /** Clears the privacy toast message after it has been displayed. */
    fun clearPrivacyToast() {
        _uiState.update { it.copy(privacyToastMessage = null, privacyToastIsError = false) }
    }
}
