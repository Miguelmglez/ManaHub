package com.mmg.manahub.feature.splash.di

import com.mmg.manahub.feature.splash.presentation.SplashViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Splash feature is the smallest "Koin island" yet:
 * a single [SplashViewModel] resolved by Koin (`koinViewModel()`) while every other un-migrated
 * feature stays on Hilt.
 *
 * ## Bridge / shared singletons (resolved via `get()`)
 * [SplashViewModel] has exactly ONE dependency, `AuthRepository`, which is already shared across the
 * Settings + Profile + Home + CardDetail + Friends islands and bridged once in `coreBridgeKoinModule`.
 * It is therefore resolved here via `get()` and NOT re-registered (a second `single<AuthRepository>`
 * across two loaded modules would throw `DefinitionOverrideException`). This island bridges nothing of
 * its own, so [splashKoinModule] takes no parameters and `ManaHubApp` needs no new `@Inject` field.
 *
 * @return a Koin [Module] providing the [SplashViewModel] factory.
 */
fun splashKoinModule(): Module = module {
    viewModel {
        SplashViewModel(
            authRepository = get(),
        )
    }
}
