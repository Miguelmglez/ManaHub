package com.mmg.manahub.feature.stats.di

import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.feature.stats.presentation.StatsViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Stats feature is the second "Koin island" (after
 * Settings): [StatsViewModel] is resolved by Koin (`koinViewModel()`) while every other feature stays
 * on Hilt. This continues the incremental, per-feature cutover proven by Spike D.
 *
 * ## Bridge pattern (same as Settings)
 * [StatsViewModel] depends on eight singletons still owned by the Hilt object graph. Rather than
 * re-providing them in Koin — which would risk duplicate construction / divergent state — `ManaHubApp`
 * is the bridge: it `@Inject`s the already-constructed Hilt instances and passes them into
 * [statsKoinModule], which re-exposes the Stats-only ones to Koin as `single { }`.
 *
 * Several dependencies are SHARED with other islands, so they are NOT registered here — they are bridged
 * once in `coreBridgeKoinModule` (registering the same type in two loaded modules would throw
 * `DefinitionOverrideException`), and this module resolves them via `get()`:
 * - [UserPreferencesRepository] — shared with Settings.
 * - [GameSessionRepository] — shared with Profile + Home.
 * - [DeckRepository] — shared with Home.
 * - [ScryfallRemoteDataSource] — shared with Home.
 *
 * `GetCollectionStatsUseCase`, `GetCollectionSetCodesUseCase` and `RefreshCollectionPricesUseCase`
 * (KMP migration batch 2) are now natively Koin-built in `SharedDomainKoinModule` — this module takes
 * NO constructor params anymore and resolves all three via `get()`, same as the shared repositories.
 *
 * As features migrate, each `single { hiltInstance }` here is replaced by a real Koin provider and the
 * matching Hilt `@Provides`/`@Binds` is deleted — so the bridge shrinks to nothing without ever leaving
 * the app uncompilable between commits.
 *
 * @return a Koin [Module] that provides the [StatsViewModel] factory.
 */
fun statsKoinModule(): Module = module {
    // ── The Koin island: StatsViewModel is now resolved by Koin, not Hilt. ──
    viewModel {
        StatsViewModel(
            getStats = get(),
            getSetCodes = get(),
            scryfallDataSource = get(),
            refreshPricesUseCase = get(),
            userPreferencesDataStore = get(),
            gameSessionRepository = get(),
            deckRepository = get(),
        )
    }
}
