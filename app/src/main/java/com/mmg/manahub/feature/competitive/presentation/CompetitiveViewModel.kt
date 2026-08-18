package com.mmg.manahub.feature.competitive.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.feature.news.domain.usecase.GetProTourContentUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Competitive screen: a format selector filtering the static deep-link catalog
 * ([CompetitiveResourceCatalog]), a persisted external event-locator postal code, and a Pro Tour
 * news filter.
 *
 * ## 2026-08 pivot — zero live third-party API calls
 * This ViewModel previously called `CompetitiveRepository.getWeeklyMeta`/`getLimitedRatings`
 * (the `manahub-competitive` Cloudflare Worker's weekly meta snapshot + LIVE 17lands Limited
 * ratings fetch) and drove an "Import to Deck Studio" CTA off the resulting representative deck.
 * Per Miguel's explicit decision (after reviewing 17lands/TopDeck/Spicerack's policies), the app
 * now makes NO live/REST calls to third-party MTG data services at all — the whole
 * `CompetitiveRepository`/Worker data layer stays in the codebase, completely untouched and
 * dormant, for a possible future revisit, but this ViewModel no longer references it. The former
 * live sections are replaced by [CompetitiveResourceCatalog], a compiled-in, 100% static table of
 * precise deep links (see `feature/competitive/CLAUDE.md` for the full rationale).
 *
 * Telemetry note: `screen_viewed: competitive` and `competitive_event_locator_no_browser` (the
 * `openUrl()` Custom Tabs helper's failure path) live in `CompetitiveScreen.kt`, not here — see
 * the `crashlytics-ux-auditor` audit of 2026-08-12
 * (`.claude/agent-memory/crashlytics-ux-auditor/audit_competitive_feature.md`) for the pre-pivot
 * baseline; the meta/limited/import-specific events documented there were removed along with the
 * code paths that fired them.
 */
class CompetitiveViewModel(
    private val getProTourContent: GetProTourContentUseCase,
    private val userPrefsDataStore: UserPreferencesDataStore,
) : ViewModel() {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    private val _uiState = MutableStateFlow(CompetitiveUiState())
    val uiState: StateFlow<CompetitiveUiState> = _uiState.asStateFlow()

    init {
        crashlytics.setCustomKey("competitive_selected_format", _uiState.value.selectedFormat.id)
        crashlytics.setCustomKey("competitive_selected_tab", _uiState.value.selectedTab.id)
        viewModelScope.launch {
            userPrefsDataStore.competitivePostalCodeFlow.collect { postalCode ->
                _uiState.update { it.copy(postalCode = postalCode) }
            }
        }
        viewModelScope.launch {
            getProTourContent()
                .catch {
                    crashlytics.log("competitive_pro_tour_feed_error")
                    recordSafeNonFatal("competitive_pro_tour_feed_error", it)
                    emit(emptyList())
                }
                .collect { items -> _uiState.update { it.copy(proTourContent = items) } }
        }
    }

    /** Switches the format used to filter [CompetitiveResourceCatalog]'s format-scoped entries.
     * No-op if already selected. */
    fun onFormatSelected(format: CompetitiveFormat) {
        if (format == _uiState.value.selectedFormat) return
        _uiState.update { it.copy(selectedFormat = format) }
        crashlytics.setCustomKey("competitive_selected_format", format.id)
    }

    /** Updates and persists the event-locator postal code. */
    fun onPostalCodeChanged(postalCode: String) {
        _uiState.update { it.copy(postalCode = postalCode) }
        viewModelScope.launch { userPrefsDataStore.setCompetitivePostalCode(postalCode) }
    }

    /** Switches the active tab (Tournaments/Metagame/Hub) grouping [ResourceCategory]. No-op if
     * already selected. Cheap, useful engagement signal — worth a breadcrumb even though this is
     * "just" a UI grouping change (per CLAUDE.md's mandatory telemetry review). */
    fun onTabSelected(tab: CompetitiveTab) {
        if (tab == _uiState.value.selectedTab) return
        _uiState.update { it.copy(selectedTab = tab) }
        crashlytics.log("competitive_tab_selected")
        crashlytics.setCustomKey("competitive_selected_tab", tab.id)
    }

    /** Toggles a category's accordion expand/collapse state. In-memory only — nothing persisted. */
    fun onCategoryToggled(categoryId: String) {
        _uiState.update { state ->
            val expanded = state.expandedCategories
            val next = if (categoryId in expanded) expanded - categoryId else expanded + categoryId
            state.copy(expandedCategories = next)
        }
        crashlytics.log("competitive_category_toggled")
        crashlytics.setCustomKey("competitive_last_toggled_category", categoryId)
    }
}
