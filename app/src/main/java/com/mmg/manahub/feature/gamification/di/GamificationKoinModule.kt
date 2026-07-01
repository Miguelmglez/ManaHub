package com.mmg.manahub.feature.gamification.di

import com.mmg.manahub.feature.gamification.presentation.GamificationCelebrationViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt->Koin cutover (remediation slice P1.3). [GamificationCelebrationViewModel]
 * is the sole ViewModel in `feature/gamification/presentation` (it drives the global celebration
 * overlay mounted once at the app root by
 * [com.mmg.manahub.feature.gamification.presentation.GamificationCelebrationHost] — see that file's
 * KDoc), so this is a standalone single-VM island, following the same per-feature module convention as
 * every prior cutover.
 *
 * ## Bridge pattern (same as the earlier islands)
 * Both constructor dependencies are already bridged Hilt-owned singletons in `coreBridgeKoinModule`
 * (shared with several other islands) — resolved below via `get()`, NOT re-registered here (a second
 * `single<T>` for the same type across two loaded modules throws `DefinitionOverrideException`):
 * - [com.mmg.manahub.core.gamification.domain.repository.GamificationRepository] — shared with Profile
 *   + Home.
 * - [com.mmg.manahub.core.data.local.UserPreferencesDataStore] — shared with Settings + Profile + Home.
 *
 * @return a Koin [Module] providing the [GamificationCelebrationViewModel] factory.
 */
fun gamificationKoinModule(): Module = module {
    // ── The Koin island: GamificationCelebrationViewModel is now resolved by Koin, not Hilt. ──
    viewModel {
        GamificationCelebrationViewModel(
            repository = get(),
            userPreferencesDataStore = get(),
        )
    }
}
