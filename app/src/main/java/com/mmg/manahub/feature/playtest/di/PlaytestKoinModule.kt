package com.mmg.manahub.feature.playtest.di

import com.mmg.manahub.core.data.local.dao.PlaytestDao
import com.mmg.manahub.feature.playtest.data.repository.PlaytestRepositoryImpl
import com.mmg.manahub.feature.playtest.domain.repository.PlaytestRepository
import com.mmg.manahub.feature.playtest.domain.usecase.BuildLibraryUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.CanPlaytestDeckUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.DrawHandUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.LondonMulliganUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.SavePlaytestSurveyUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.SavePlaytestUseCase
import com.mmg.manahub.feature.playtest.presentation.hand.PlaytestHandViewModel
import com.mmg.manahub.feature.playtest.presentation.setup.PlaytestSetupViewModel
import kotlinx.coroutines.Dispatchers
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Deck Playtest feature is a "Koin island": both of
 * its ViewModels — [PlaytestSetupViewModel] (deck eligibility + draw config) and
 * [PlaytestHandViewModel] (the SAME ViewModel drives both the MULLIGAN and PLAY/battlefield phases as
 * conditional content) — are resolved by Koin (`koinViewModel()`) while every other un-migrated
 * feature stays on Hilt.
 *
 * ## Feature-private Hilt module converted + DELETED
 * The old `PlaytestModule` only `@Binds PlaytestRepository` and is consumed by ZERO Hilt features
 * (grep-verified: the repository, its impl, the rate-free DAO write path and all six use cases live
 * exclusively under `feature/playtest`). Its `@Binds` is therefore ported here as a `single { }`, the
 * Hilt module deleted, and `@Inject`/`@Singleton`/`@IoDispatcher` stripped from
 * [PlaytestRepositoryImpl] + the six use cases (now plain Koin-constructed classes). The
 * `@IoDispatcher CoroutineDispatcher` becomes `Dispatchers.IO` directly — the SAME singleton the Hilt
 * binding returned (Survey/CommunityDecks precedent).
 *
 * ## Bridged singleton (passed in, registered here — this island only)
 * - [PlaytestDao] — the Room/`DatabaseModule`-owned (still Hilt-provided) DAO that is the ONLY
 *   sanctioned write path (`saveTestAtomically`); consumed only by this island, so it is bridged
 *   here, not in `coreBridgeKoinModule`.
 *
 * ## Shared singletons (resolved via `get()`, NOT re-registered)
 * A `single<T>` is resolvable via `get()` from ANY loaded module, so re-registering would throw
 * `DefinitionOverrideException`:
 * - `DeckRepository` — already in `coreBridgeKoinModule`.
 * - `CardDao` — already a `single` in `surveyKoinModule`.
 *
 * ## ViewModel resolution notes
 * - [PlaytestSetupViewModel] takes a `SavedStateHandle` carrying the `"deckId"` nav arg →
 *   `savedStateHandle = get()`; `koinViewModel()` populates it from the NavBackStackEntry's
 *   `CreationExtras`, so the nav-arg behaviour is byte-for-byte identical to Hilt.
 * - [PlaytestHandViewModel] takes NO `SavedStateHandle`: its [PlaytestSetup] arrives via the
 *   in-memory `pendingPlaytestSetup` handoff in `AppNavGraph`, applied through `initWithSetup(setup)`
 *   in the screen's `LaunchedEffect`. That handoff is unchanged by the DI cutover. Its one-shot
 *   events stay on the buffered `Channel` collected via `LaunchedEffect` in the screen.
 *
 * @param playtestDao the Hilt/Room-owned [PlaytestDao] singleton (this island only).
 * @return a Koin [Module] providing the playtest data layer, use cases and both ViewModel factories.
 */
fun playtestKoinModule(
    playtestDao: PlaytestDao,
): Module = module {
    // ── Hilt → Koin bridge: the Room-owned DAO (still Hilt/DatabaseModule-provided). ──
    single { playtestDao }

    // ── Playtest data layer (formerly the Hilt PlaytestModule @Binds). ──
    single<PlaytestRepository> {
        PlaytestRepositoryImpl(playtestDao = get(), ioDispatcher = Dispatchers.IO)
    }

    // ── Playtest use cases (formerly @Inject constructor). ──
    single { BuildLibraryUseCase() }
    single { DrawHandUseCase() }
    single { LondonMulliganUseCase() }
    single { CanPlaytestDeckUseCase() }
    single { SavePlaytestUseCase(repository = get()) }
    single { SavePlaytestSurveyUseCase(repository = get()) }

    // ── The Koin island: both ViewModels are now resolved by Koin, not Hilt. ──
    viewModel {
        PlaytestSetupViewModel(
            savedStateHandle = get(),
            deckRepository = get(),
            cardDao = get(),
            canPlaytestDeckUseCase = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
    viewModel {
        PlaytestHandViewModel(
            deckRepository = get(),
            cardDao = get(),
            buildLibraryUseCase = get(),
            drawHandUseCase = get(),
            londonMulliganUseCase = get(),
            savePlaytestUseCase = get(),
            savePlaytestSurveyUseCase = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }
}
