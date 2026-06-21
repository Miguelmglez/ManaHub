package com.mmg.manahub.feature.tournament.di

import com.mmg.manahub.feature.tournament.domain.usecase.CalculateStandingsUseCase
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
 * ## Bridge pattern (same as the earlier islands)
 * `ManaHubApp` is the bridge: it `@Inject`s the still-Hilt-owned singletons and passes the Tournament-only
 * ones into [tournamentKoinModule], which re-exposes them to Koin as `single { }`.
 *
 * ## What is NOT registered here
 * - [com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository] is SHARED with the Home
 *   island (already migrated) and with the still-Hilt `GameViewModel`, so it is bridged exactly once in
 *   `coreBridgeKoinModule` (registering the same type in two loaded modules would throw
 *   `DefinitionOverrideException`) and resolved here via `get()`.
 *
 * ## What IS bridged here (Tournament-only, from the Hilt graph)
 * - [CalculateStandingsUseCase] — used only by [TournamentViewModel] (Koin), but left Hilt-constructed
 *   and bridged so its `TournamentDao` + IO-dispatcher deps stay in the Hilt graph (no DAO bridging).
 * - [RecordMatchResultUseCase] — the SINGLE finish-and-advance entry point. It is STILL consumed by the
 *   Hilt `GameViewModel` (the game-played result flow), so its Hilt `@Inject constructor` binding is KEPT
 *   and the same singleton is bridged here. The DI cutover does not touch the atomic write path
 *   (`RecordMatchResultUseCase` → `TournamentRepository.finishMatch` →
 *   `TournamentDao.finishMatchAndAdvanceAtomically`) — both the game flow and the manual dialog keep
 *   routing through the identical instance.
 *
 * The [TournamentViewModel] factory resolves a Koin-injected `SavedStateHandle` (`savedStateHandle =
 * get()`), which carries the `tournamentId` nav arg from the NavBackStackEntry's `CreationExtras` exactly
 * as Hilt did — so the `> 0L` construction guard and nav behaviour are byte-for-byte unchanged.
 *
 * The feature-private Hilt `TournamentModule` (`@Binds TournamentRepository`) is KEPT (NOT converted /
 * deleted): the still-Hilt `GameViewModel` consumes `TournamentRepository` from the Hilt graph, so the
 * binding must stay alive. The bridge guarantees one shared singleton instance across both DI graphs.
 *
 * @param calculateStandings the Hilt-owned [CalculateStandingsUseCase] singleton (Tournament only).
 * @param recordMatchResult the Hilt-owned [RecordMatchResultUseCase] singleton (Tournament + the Hilt
 *   `GameViewModel`).
 * @return a Koin [Module] providing the Tournament-only bridged singletons and the three ViewModel factories.
 */
fun tournamentKoinModule(
    calculateStandings: CalculateStandingsUseCase,
    recordMatchResult: RecordMatchResultUseCase,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Tournament-only Hilt-owned singletons to Koin. ──
    // (TournamentRepository is shared → bridged in coreBridgeKoinModule, not here, and resolved below
    //  via get().)
    single { calculateStandings }
    single { recordMatchResult }

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
