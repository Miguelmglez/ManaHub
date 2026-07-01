package com.mmg.manahub.core.ui.components.search.di

import com.mmg.manahub.core.ui.components.search.AdvancedSearchViewModel
import com.mmg.manahub.core.ui.components.search.SetPickerViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt->Koin cutover (remediation slice P1.3, the last two non-excluded
 * `@HiltViewModel` orphans besides [com.mmg.manahub.feature.gamification.presentation.GamificationCelebrationViewModel]
 * and [com.mmg.manahub.feature.profile.presentation.ProfileEditViewModel]).
 *
 * ## Why a dedicated module instead of extending an existing feature island
 * [AdvancedSearchViewModel] and [SetPickerViewModel] back reusable `core/ui/components/search/` sheets
 * (`AdvancedSearchSheet`, `SetPickerSheet`) consumed from MULTIPLE already-Koin islands (Stats,
 * Collection, Home, AddCard) — no single feature is their natural owner. Homing them in, say,
 * `CollectionKoinModule` would be arbitrary and imply an ownership relationship that doesn't exist.
 * Koin resolves `single`/`viewModel` definitions from a single shared container regardless of which
 * module registered them, so a small standalone module for these shared widgets is the cleanest fit —
 * consistent with `core-ui`'s existing "shared, not feature-owned" positioning (CLAUDE.md).
 *
 * ## Bridge pattern (same as the earlier islands)
 * Every constructor dependency of both ViewModels is ALREADY a `single` in a loaded module — no new
 * `ManaHubApp` bridging is required:
 * - [com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource] and
 *   [com.mmg.manahub.core.data.local.UserPreferencesDataStore] are bridged in `coreBridgeKoinModule`
 *   (shared across many islands).
 * - [com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase] is already a `single` in
 *   `addCardKoinModule` (the AddCard island); resolved here via `get()`, NOT re-registered (a second
 *   `single<T>` for the same type across two loaded modules throws `DefinitionOverrideException`).
 *
 * [SetPickerViewModel] is resolved at the call site via
 * `koinViewModel(key = availableSets?.hashCode()?.toString())` — the `key` param is the exact Koin
 * equivalent of the previous `hiltViewModel(key = ...)`, minting a fresh instance whenever
 * `availableSets` changes (see `SetPickerSheet.kt`).
 *
 * @return a Koin [Module] providing the [AdvancedSearchViewModel] and [SetPickerViewModel] factories.
 */
fun searchWidgetsKoinModule(): Module = module {
    // ── The Koin island: both search-widget ViewModels are now resolved by Koin, not Hilt. ──
    // (ScryfallRemoteDataSource + UserPreferencesDataStore come from coreBridgeKoinModule;
    //  BuildScryfallQueryUseCase is already a single in addCardKoinModule — none re-registered here.)
    viewModel {
        AdvancedSearchViewModel(
            scryfallDataSource = get(),
            buildQuery = get(),
            userPreferencesDataStore = get(),
        )
    }
    viewModel {
        SetPickerViewModel(
            scryfallDataSource = get(),
        )
    }
}
