package com.mmg.manahub.web.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.KeyValueStore
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.model.CollectionViewMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs [ThemeShowcaseScreen]'s persisted preference toggle (web roadmap W1) and, since W3a, the
 * `collectionViewMode` round-trip through the first web data-layer repository slice
 * ([UserPreferencesRepository]). Exists mainly to prove the whole DI path end-to-end: Koin
 * `startKoin` on wasmJs + the pure-CMP `koinViewModel()` (from `koin-compose-viewmodel`, distinct
 * from the Android-only `koin-androidx-compose`) + real [KeyValueStore]/repository round-trips
 * through `window.localStorage`.
 */
class ThemeShowcaseViewModel(
    private val keyValueStore: KeyValueStore,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _prefEnabled = MutableStateFlow(false)
    val prefEnabled: StateFlow<Boolean> = _prefEnabled.asStateFlow()

    /**
     * W3a smoke check: exercises [UserPreferencesRepository.collectionViewModeFlow] /
     * [UserPreferencesRepository.saveCollectionViewMode] — the simplest field on the new
     * [WebUserPreferencesRepository][com.mmg.manahub.core.data.repository.WebUserPreferencesRepository]
     * to demonstrate the full DI-to-implementation chain resolving and persisting correctly on
     * wasmJs, distinct from the raw [KeyValueStore] toggle above (already proven in W1).
     */
    val collectionViewMode: StateFlow<CollectionViewMode> = userPreferencesRepository.collectionViewModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionViewMode.GRID)

    init {
        viewModelScope.launch {
            _prefEnabled.value = keyValueStore.getBoolean(PREF_KEY, default = false)
        }
    }

    /** Flips the toggle and persists the new value immediately. */
    fun togglePref() {
        viewModelScope.launch {
            val newValue = !_prefEnabled.value
            keyValueStore.putBoolean(PREF_KEY, newValue)
            _prefEnabled.value = newValue
        }
    }

    /** Flips [collectionViewMode] between GRID/LIST and persists it via the repository. */
    fun toggleCollectionViewMode() {
        viewModelScope.launch {
            val next = if (collectionViewMode.value == CollectionViewMode.GRID) {
                CollectionViewMode.LIST
            } else {
                CollectionViewMode.GRID
            }
            userPreferencesRepository.saveCollectionViewMode(next)
        }
    }

    private companion object {
        const val PREF_KEY = "w1_showcase_pref_toggle"
    }
}
