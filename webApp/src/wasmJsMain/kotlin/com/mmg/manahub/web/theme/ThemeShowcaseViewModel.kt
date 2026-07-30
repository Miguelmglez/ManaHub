package com.mmg.manahub.web.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.common.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs [ThemeShowcaseScreen]'s persisted preference toggle (web roadmap W1). Exists mainly to
 * prove the whole DI path end-to-end: Koin `startKoin` on wasmJs + the pure-CMP `koinViewModel()`
 * (from `koin-compose-viewmodel`, distinct from the Android-only `koin-androidx-compose`) + a real
 * [KeyValueStore] round-trip through `window.localStorage`.
 */
class ThemeShowcaseViewModel(
    private val keyValueStore: KeyValueStore,
) : ViewModel() {

    private val _prefEnabled = MutableStateFlow(false)
    val prefEnabled: StateFlow<Boolean> = _prefEnabled.asStateFlow()

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

    private companion object {
        const val PREF_KEY = "w1_showcase_pref_toggle"
    }
}
