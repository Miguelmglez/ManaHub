package com.mmg.manahub.web.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.model.AppLanguage
import com.mmg.manahub.core.model.CardLanguage
import com.mmg.manahub.core.model.CollectionGroupingMode
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.NewsLanguage
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs [SettingsScreen] -- web scope expansion approved 2026-08-04 (priority order: Settings ->
 * Profile -> Add Card; Trades/Friends/Game/online sessions remain explicitly out of scope). Pure
 * UI/state layer over the ALREADY-COMPLETE web data layer -- [UserPreferencesRepository]
 * ([com.mmg.manahub.core.data.repository.WebUserPreferencesRepository], web roadmap W3a) already
 * implements every flow/setter this screen needs against real `window.localStorage`, so this slice
 * needed zero new repository work.
 *
 * Deliberately exposes only 4 of the interface's 9 setters as real controls:
 *  - [setCardLanguage] / [setPreferredCurrency] / [setCollectionViewMode] / [setCollectionGroupingMode]
 *    ARE exposed.
 *  - `setAppLanguage` is NOT exposed. [AppLanguage] has exactly ONE entry ([AppLanguage.ENGLISH])
 *    and CLAUDE.md's Language rules section states the app is English-only by design (no locale
 *    dirs, no UI-facing strings in any other language) -- a picker with a single selectable option
 *    is dead UI implying an i18n capability that does not and will not exist. Android's own
 *    equivalent row (`feature/settings/presentation/SettingsScreen.kt`'s `PreferencesSection`) is
 *    commented out for the identical reason -- this is not a web-only scope cut, it mirrors a
 *    decision Android already made.
 *  - `setNewsLanguages` is NOT exposed -- News is not built on web yet (CORS blocker, see
 *    `project_kmp_spike_findings` memory), so a language picker for a feature that doesn't exist
 *    would be dead UI too. Same reasoning Android's own commented-out News-language row reflects.
 *  - `saveLastPriceRefresh` / `saveUserDefinedTag` / `deleteUserDefinedTag` are NOT exposed here --
 *    not simple settings-screen toggles (price refresh is CLAUDE.md's "Backend call budget"
 *    automatic-only background path, no manual trigger on ANY platform; user-defined tags are a
 *    tagging-engine feature not yet built on web at all).
 *
 * [collectionGroupingMode] is persisted and round-trips through a reload like every other setting
 * here, but is NOT YET consumed by [com.mmg.manahub.web.collection.CollectionScreen] -- see that
 * screen's own KDoc for why (grouping needs a `CollectionCardGroup`-shaped collapsing step the web
 * collection repository doesn't produce yet, deliberately deferred as non-trivial future work).
 * [CollectionViewMode] IS fully consumed -- see [setCollectionViewMode].
 */
class SettingsViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = userPreferencesRepository.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DEFAULT_PREFERENCES)

    val collectionGroupingMode: StateFlow<CollectionGroupingMode> =
        userPreferencesRepository.collectionGroupingModeFlow
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionGroupingMode.NONE)

    fun setCardLanguage(language: CardLanguage) {
        viewModelScope.launch { userPreferencesRepository.setCardLanguage(language) }
    }

    fun setPreferredCurrency(currency: PreferredCurrency) {
        viewModelScope.launch { userPreferencesRepository.setPreferredCurrency(currency) }
    }

    /**
     * Persists the new [CollectionViewMode]. Shares the exact same repository call
     * [com.mmg.manahub.web.collection.CollectionViewModel.setViewMode] uses, so this screen's
     * picker and the Collection screen's own inline toggle always agree -- both are thin callers
     * over the SAME Koin `single` [UserPreferencesRepository] instance (session-scoped cache, see
     * `WebUserPreferencesRepository`'s own KDoc).
     */
    fun setCollectionViewMode(mode: CollectionViewMode) {
        viewModelScope.launch { userPreferencesRepository.saveCollectionViewMode(mode) }
    }

    fun setCollectionGroupingMode(mode: CollectionGroupingMode) {
        viewModelScope.launch { userPreferencesRepository.saveCollectionGroupingMode(mode) }
    }

    private companion object {
        val DEFAULT_PREFERENCES = UserPreferences(
            appLanguage = AppLanguage.ENGLISH,
            cardLanguage = CardLanguage.ENGLISH,
            newsLanguages = setOf(NewsLanguage.ENGLISH),
            preferredCurrency = PreferredCurrency.EUR,
            collectionViewMode = CollectionViewMode.GRID,
        )
    }
}
