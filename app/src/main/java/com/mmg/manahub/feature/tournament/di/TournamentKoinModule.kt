package com.mmg.manahub.feature.tournament.di

import com.mmg.manahub.core.data.local.dao.TournamentDao
import com.mmg.manahub.feature.tournament.domain.usecase.CalculateStandingsUseCase
import com.mmg.manahub.feature.tournament.domain.usecase.GenerateNextRoundUseCase
import com.mmg.manahub.feature.tournament.domain.usecase.RecordMatchResultUseCase
import com.mmg.manahub.feature.tournament.presentation.TournamentListViewModel
import com.mmg.manahub.feature.tournament.presentation.TournamentSetupViewModel
import com.mmg.manahub.feature.tournament.presentation.TournamentViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Tournament feature is the fifteenth "Koin island":
 * all three tournament ViewModels ([TournamentListViewModel], [TournamentSetupViewModel],
 * [TournamentViewModel]) are resolved by Koin (`koinViewModel()`) while every other unmigrated feature
 * stays on Hilt. This continues the incremental, per-feature cutover proven by Spike D.
 *
 * ## Deterministic batch-1 cutover (2026-07)
 * The feature-private Hilt `TournamentModule` was CONVERTED and DELETED: `GameViewModel` (the last
 * suspected still-Hilt consumer of `TournamentRepository` / `RecordMatchResultUseCase`) had ALREADY been
 * migrated to a plain Koin-resolved class in an earlier batch (`gameKoinModule`), so nothing in the Hilt
 * graph consumes any Tournament-owned type anymore. `TournamentRepository`, `CalculateStandingsUseCase`,
 * `RecordMatchResultUseCase` and `GenerateNextRoundUseCase` are now built directly by Koin.
 *
 * ## What is NOT registered here
 * - [com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository] is SHARED with the Home
 *   and Game islands, so it is natively constructed exactly once in `coreBridgeKoinModule` (registering
 *   the same type in two loaded modules would throw `DefinitionOverrideException`) and resolved here via
 *   `get()`. Its own dependencies — [TournamentDao] and [GenerateNextRoundUseCase] — are registered
 *   below (Tournament-only); Koin resolves `get()` across modules regardless of declaration site.
 *
 * ## What IS registered here (Tournament-only, natively Koin-built)
 * - [TournamentDao] — bridged from the Room/`DatabaseModule`-owned Hilt singleton (`ManaHubApp` `@Inject`
 *   field); Room stays `androidMain`/Hilt-provided per the KMP migration plan, only the DAO instance
 *   itself is re-exposed to Koin.
 * - [GenerateNextRoundUseCase] — stateless, no-arg; used only by [TournamentRepositoryImpl].
 * - [CalculateStandingsUseCase] — used only by [TournamentViewModel].
 * - [RecordMatchResultUseCase] — the SINGLE finish-and-advance entry point, ALSO resolved via `get()` by
 *   `gameKoinModule` (the game-played result flow) — one shared instance across both consumers, same as
 *   before, just Koin-native instead of Hilt-bridged.
 *
 * The [TournamentViewModel] factory resolves a Koin-injected `SavedStateHandle` (`savedStateHandle =
 * get()`), which carries the `tournamentId` nav arg from the NavBackStackEntry's `CreationExtras` exactly
 * as Hilt did — so the `> 0L` construction guard and nav behaviour are byte-for-byte unchanged.
 *
 * @param tournamentDao the Room/`DatabaseModule`-owned Hilt singleton, bridged for [TournamentRepository].
 * @return a Koin [Module] providing the Tournament-only singletons and the three ViewModel factories.
 */
fun tournamentKoinModule(
    tournamentDao: TournamentDao,
): Module = module {
    // ── Tournament-only singletons, natively Koin-built (Hilt TournamentModule deleted). ──
    single { tournamentDao }
    single { GenerateNextRoundUseCase() }
    single { CalculateStandingsUseCase(repository = get()) }
    single { RecordMatchResultUseCase(repository = get()) }

    // ── The Koin island: the three tournament ViewModels are now resolved by Koin, not Hilt. ──
    viewModel {
        TournamentListViewModel(
            repository = get(),
        )
    }
    viewModel {
        TournamentSetupViewModel(
            repository = get(),
        )
    }
    viewModel {
        TournamentViewModel(
            repository = get(),
            calculateStandings = get(),
            recordMatchResultUseCase = get(),
            savedStateHandle = get(),
        )
    }
}
