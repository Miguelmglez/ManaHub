package com.mmg.manahub.feature.addcard.di

import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.feature.addcard.presentation.AddCardViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The AddCard feature is the sixth "Koin island" (after
 * Settings, Stats, Profile, Home and TagDictionary): [AddCardViewModel] is resolved by Koin
 * (`koinViewModel()`) while every other feature stays on Hilt. This continues the incremental,
 * per-feature cutover proven by Spike D.
 *
 * ## Bridge pattern (same as the prior islands)
 * [AddCardViewModel] depends on three singletons still owned by the Hilt object graph. Rather than
 * re-providing them in Koin — which would risk duplicate construction / divergent state — `ManaHubApp`
 * is the bridge: it `@Inject`s the already-constructed Hilt instances and passes the AddCard-only ones
 * into [addCardKoinModule], which re-exposes them to Koin as `single { }`.
 *
 * One dependency is SHARED with another island, so it is NOT registered here — it is bridged once in
 * `coreBridgeKoinModule` (registering the same type in two loaded modules would throw
 * `DefinitionOverrideException`), and this module resolves it via `get()`:
 * - [UserPreferencesRepository] — shared with Settings + Stats.
 *
 * `SearchCardsUseCase` and `BuildScryfallQueryUseCase` (KMP migration batch 2) are now natively
 * Koin-built in `SharedDomainKoinModule` — this module takes NO constructor params anymore and
 * resolves both via `get()`.
 *
 * As features migrate, each `single { hiltInstance }` here is replaced by a real Koin provider and the
 * matching Hilt `@Provides`/`@Binds` is deleted — so the bridge shrinks to nothing without ever leaving
 * the app uncompilable between commits.
 *
 * @return a Koin [Module] that provides the [AddCardViewModel] factory.
 */
fun addCardKoinModule(): Module = module {
    // ── The Koin island: AddCardViewModel is now resolved by Koin, not Hilt. ──
    viewModel {
        AddCardViewModel(
            searchCards = get(),
            userPreferences = get(),
            buildScryfallQuery = get(),
            getSpotlightFeed = get(),
        )
    }
}
