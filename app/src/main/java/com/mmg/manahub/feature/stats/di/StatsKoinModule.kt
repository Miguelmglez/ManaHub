package com.mmg.manahub.feature.stats.di

import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.data.usecase.collection.RefreshCollectionPricesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionSetCodesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionStatsUseCase
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
 * As features migrate, each `single { hiltInstance }` here is replaced by a real Koin provider and the
 * matching Hilt `@Provides`/`@Binds` is deleted — so the bridge shrinks to nothing without ever leaving
 * the app uncompilable between commits.
 *
 * @return a Koin [Module] that provides the Stats-only bridged singletons and the [StatsViewModel] factory.
 */
fun statsKoinModule(
    getCollectionStats: GetCollectionStatsUseCase,
    getCollectionSetCodes: GetCollectionSetCodesUseCase,
    refreshPricesUseCase: RefreshCollectionPricesUseCase,
    gameSessionDao: GameSessionDao,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Stats-only Hilt-owned singletons to Koin. ──
    // (UserPreferencesRepository [Settings], GameSessionRepository [Profile + Home], DeckRepository [Home]
    //  and ScryfallRemoteDataSource [Home] are bridged in coreBridgeKoinModule, not here, and resolved
    //  below via get().)
    single { getCollectionStats }
    single { getCollectionSetCodes }
    single { refreshPricesUseCase }
    single { gameSessionDao }

    // ── The Koin island: StatsViewModel is now resolved by Koin, not Hilt. ──
    viewModel {
        StatsViewModel(
            getStats = get(),
            getSetCodes = get(),
            scryfallDataSource = get(),
            refreshPricesUseCase = get(),
            userPreferencesDataStore = get(),
            gameSessionDao = get(),
            gameSessionRepository = get(),
            deckRepository = get(),
        )
    }
}
