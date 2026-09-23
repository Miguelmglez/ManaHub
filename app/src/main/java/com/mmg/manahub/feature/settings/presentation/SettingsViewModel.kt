package com.mmg.manahub.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.dto.UserProfileDto
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.NotificationPrefsRepository
import com.mmg.manahub.core.domain.repository.PushTokenRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.model.AppLanguage
import com.mmg.manahub.core.model.CardLanguage
import com.mmg.manahub.core.model.NewsLanguage
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.util.recordNonFatal
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.core.voice.domain.VoiceLanguage
import com.mmg.manahub.core.voice.domain.VoiceModelRepository
import com.mmg.manahub.core.voice.domain.VoiceModelState
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Settings screen ViewModel.
 *
 * KMP migration — Phase 0 Spike D: this is the first feature migrated off Hilt. It is no longer
 * `@HiltViewModel`; it is constructed by Koin via `settingsKoinModule` and resolved in the Composable
 * with `koinViewModel()`. Its dependencies are bridged from the still-Hilt-owned object graph (see
 * `SettingsKoinModule`). Every other ViewModel remains `@HiltViewModel` — Hilt and Koin coexist.
 */
class SettingsViewModel(
    private val userPrefsDataStore: UserPreferencesDataStore,
    private val userPreferencesRepo: UserPreferencesRepository,
    private val analyticsHelper: AnalyticsHelper,
    private val authRepository: AuthRepository,
    private val userProfileDataSource: UserProfileDataSource,
    private val pushTokenRepository: PushTokenRepository,
    private val notificationPrefsRepository: NotificationPrefsRepository,
    private val voiceModelRepository: VoiceModelRepository,
//    private val langPref:              LanguagePreference
) : ViewModel() {

    private val _appLanguageChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    val appLanguageChanged: SharedFlow<Unit> = _appLanguageChanged.asSharedFlow()
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _prefsState = MutableStateFlow(PreferencesState())
    val prefsState: StateFlow<PreferencesState> = _prefsState.asStateFlow()

    /** The three `user_profiles` privacy columns; each has its own write lock so rapid taps serialise per flag. */
    private enum class PrivacyKey(val column: String) {
        COLLECTION("collection_public"),
        WISHLIST("wishlist_public"),
        TRADE_LIST("trade_list_public"),
    }

    private val privacyLocks = PrivacyKey.entries.associateWith { Mutex() }

    // ── Notification preferences ────────────────────────────────────────────────

    /**
     * The per-event-type notification preference map (`event_type -> boolean`).
     * A missing key means the event is enabled (opt-out model).
     */
    val notificationPrefs: StateFlow<Map<String, Boolean>> =
        notificationPrefsRepository.prefsFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    /** Master push-notifications switch backed by the local DataStore. */
    val pushNotificationsEnabled: StateFlow<Boolean> =
        userPrefsDataStore.pushNotificationsEnabledFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    /** Master gamification switch backed by the local DataStore. Default: enabled. */
    val gamificationEnabled: StateFlow<Boolean> =
        userPrefsDataStore.gamificationEnabledFlow
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = true,
            )

    /** Per-language voice-model state, mirrored from the repository. */
    val voiceModelStates: StateFlow<Map<VoiceLanguage, VoiceModelState>> =
        voiceModelRepository.modelStates
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * True only for a real, non-anonymous authenticated session — the same exclusion condition
     * used by `HomeViewModel.isAuthenticatedFlow`. Anonymous/guest sessions (auto-signed-in for
     * Online Sessions) must never be treated as authenticated for account-gated surfaces like
     * "Manage account" (see [SettingsScreen]).
     */
    val isAuthenticatedFlow: StateFlow<Boolean> =
        authRepository.sessionState
            .map { it is SessionState.Authenticated && !it.user.isAnonymous }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = false)

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

        // The prefs repository is an app singleton: reload (or clear) its cache whenever the
        // signed-in account changes so one user never sees another's overrides.
        authRepository.sessionState
            .map { state -> (state as? SessionState.Authenticated)?.user?.takeIf { !it.isAnonymous }?.id }
            .distinctUntilChanged()
            .onEach { runCatching { notificationPrefsRepository.refresh() } }
            .catch { /* ignore */ }
            .launchIn(viewModelScope)
    }

    fun selectTheme(theme: AppTheme) {
        launchSafely {
            analyticsHelper.logEvent("theme_selected", mapOf("theme" to theme.persistKey))
            userPrefsDataStore.saveTheme(theme)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        launchSafely {
            userPreferencesRepo.setAppLanguage(language)
            _appLanguageChanged.emit(Unit)
        }
        // Keep the backend locale in sync so future push notifications use the new language.
        viewModelScope.launch {
            runCatching { pushTokenRepository.updateLocale(language.code) }
        }
    }

    fun setCardLanguage(language: CardLanguage) {
        launchSafely { userPreferencesRepo.setCardLanguage(language) }
    }

    fun setNewsLanguages(languages: Set<NewsLanguage>) {
        if (languages.isEmpty()) return
        launchSafely { userPreferencesRepo.setNewsLanguages(languages) }
    }

    fun setPreferredCurrency(currency: PreferredCurrency) {
        launchSafely { userPreferencesRepo.setPreferredCurrency(currency) }
    }

    // ── Privacy settings ──────────────────────────────────────────────────────

    /** Optimistically updates `collection_public` locally, then syncs to Supabase (see [setPrivacyFlag]). */
    fun setCollectionPublic(value: Boolean) = setPrivacyFlag(PrivacyKey.COLLECTION, value)

    /** Optimistically updates `wishlist_public` locally, then syncs to Supabase (see [setPrivacyFlag]). */
    fun setWishlistPublic(value: Boolean) = setPrivacyFlag(PrivacyKey.WISHLIST, value)

    /** Optimistically updates `trade_list_public` locally, then syncs to Supabase (see [setPrivacyFlag]). */
    fun setTradeListPublic(value: Boolean) = setPrivacyFlag(PrivacyKey.TRADE_LIST, value)

    /**
     * Writes one privacy flag: requires a real signed-in account (anonymous sessions have no
     * `user_profiles` row, so their PATCH would match nothing and report a fake success), holds
     * the flag's own lock so rapid taps run one at a time, and on failure restores the value the
     * server actually holds (re-read) rather than the pre-tap snapshot.
     */
    private fun setPrivacyFlag(key: PrivacyKey, value: Boolean) {
        launchSafely {
            privacyLocks.getValue(key).withLock {
                val user = authRepository.getCurrentUser()
                if (user == null || user.isAnonymous) {
                    showToast(SettingsToast.SIGN_IN_REQUIRED)
                    return@withLock
                }
                _uiState.update { it.copy(pendingPrivacyKeys = it.pendingPrivacyKeys + key.column) }
                try {
                    val previous = readLocalPrivacyFlag(key)
                    writeLocalPrivacyFlag(key, value)
                    val result = userProfileDataSource.updatePrivacySettings(
                        userId = user.id,
                        collectionPublic = value.takeIf { key == PrivacyKey.COLLECTION },
                        wishlistPublic = value.takeIf { key == PrivacyKey.WISHLIST },
                        tradeListPublic = value.takeIf { key == PrivacyKey.TRADE_LIST },
                    )
                    if (result.isFailure) {
                        val serverValue = userProfileDataSource.fetchUserProfile(user.id)
                            ?.privacyFlag(key) ?: previous
                        writeLocalPrivacyFlag(key, serverValue)
                        showToast(SettingsToast.PRIVACY_SAVE_FAILED)
                    }
                } finally {
                    _uiState.update { it.copy(pendingPrivacyKeys = it.pendingPrivacyKeys - key.column) }
                }
            }
        }
    }

    private suspend fun readLocalPrivacyFlag(key: PrivacyKey): Boolean = when (key) {
        PrivacyKey.COLLECTION -> userPrefsDataStore.collectionPublicFlow.first()
        PrivacyKey.WISHLIST   -> userPrefsDataStore.wishlistPublicFlow.first()
        PrivacyKey.TRADE_LIST -> userPrefsDataStore.tradeListPublicFlow.first()
    }

    private suspend fun writeLocalPrivacyFlag(key: PrivacyKey, value: Boolean) = when (key) {
        PrivacyKey.COLLECTION -> userPrefsDataStore.saveCollectionPublic(value)
        PrivacyKey.WISHLIST   -> userPrefsDataStore.saveWishlistPublic(value)
        PrivacyKey.TRADE_LIST -> userPrefsDataStore.saveTradeListPublic(value)
    }

    private fun UserProfileDto.privacyFlag(key: PrivacyKey): Boolean? = when (key) {
        PrivacyKey.COLLECTION -> collectionPublic
        PrivacyKey.WISHLIST   -> wishlistPublic
        PrivacyKey.TRADE_LIST -> tradeListPublic
    }

    // ── Notification settings ───────────────────────────────────────────────────

    /**
     * Toggles the master push-notifications preference in the local DataStore.
     *
     * @param enabled `true` to allow push notifications, `false` to silence all of them.
     */
    fun setPushNotificationsEnabled(enabled: Boolean) {
        launchSafely {
            userPrefsDataStore.savePushNotificationsEnabled(enabled)
            // ADR-005: gate the BACKEND work, not only the display. Dropping the token stops the
            // outbox from targeting this device; re-registering restores delivery.
            runCatching {
                if (enabled) pushTokenRepository.registerCurrentDevice()
                else pushTokenRepository.unregisterCurrentDevice()
            }.onFailure { e ->
                if (e is CancellationException) throw e
                recordSafeNonFatal("settings_push_token_toggle_failed", e)
            }
        }
    }

    /**
     * Toggles the master gamification preference in the local DataStore.
     *
     * @param enabled `true` to show all gamification UI, `false` to hide it.
     */
    fun setGamificationEnabled(enabled: Boolean) {
        launchSafely { userPrefsDataStore.setGamificationEnabled(enabled) }
    }

    /**
     * Enables or disables a single notification event type and persists it to Supabase.
     *
     * @param eventType The backend event identifier (e.g. `"trade_proposed"`).
     * @param enabled `true` to receive notifications for this event, `false` to silence them.
     */
    fun setNotificationEventEnabled(eventType: String, enabled: Boolean) =
        setNotificationGroupEnabled(listOf(eventType), enabled)

    /**
     * Enables or disables every event type in a logical group with a single tap, as one atomic
     * backend write. A failure rolls the cached map back and surfaces a toast.
     *
     * @param eventTypes The backend event identifiers controlled by the group toggle.
     * @param enabled The new value applied to all [eventTypes].
     */
    fun setNotificationGroupEnabled(eventTypes: List<String>, enabled: Boolean) {
        launchSafely {
            val user: AuthUser? = authRepository.getCurrentUser()
            if (user == null || user.isAnonymous) {
                showToast(SettingsToast.SIGN_IN_REQUIRED)
                return@launchSafely
            }
            notificationPrefsRepository.setEventsEnabled(eventTypes, enabled)
                .onFailure { e ->
                    if (e is CancellationException) throw e
                    recordSafeNonFatal("settings_notification_prefs_write_failed", e)
                    showToast(SettingsToast.NOTIFICATION_SAVE_FAILED)
                }
        }
    }

    // ── Voice recognition models ─────────────────────────────────────────────────

    /** Downloads the offline voice-recognition model for [language]. */
    fun downloadVoiceModel(language: VoiceLanguage) {
        viewModelScope.launch { voiceModelRepository.download(language) }
    }

    /** Deletes the downloaded voice-recognition model for [language]. */
    fun deleteVoiceModel(language: VoiceLanguage) {
        viewModelScope.launch { voiceModelRepository.delete(language) }
    }

    /** Clears the toast after it has been displayed. */
    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null, toastIsError = false) }
    }

    private fun showToast(toast: SettingsToast, isError: Boolean = true) {
        _uiState.update { it.copy(toastMessage = toast, toastIsError = isError) }
    }

    /** Runs a preference write; a DataStore IOException is recorded as a non-fatal instead of crashing the app. */
    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure { e ->
                if (e is CancellationException) throw e
                recordNonFatal("settings_write_failed", e)
            }
        }
    }
}
