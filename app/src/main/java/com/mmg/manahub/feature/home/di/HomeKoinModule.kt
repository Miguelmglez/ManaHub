package com.mmg.manahub.feature.home.di

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.repository.CommunityStatsRepositoryStub
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.CommunityStatsRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.feature.home.domain.usecase.GetAccountNudgeUseCase
import com.mmg.manahub.feature.home.presentation.HomeViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Home feature is the fourth "Koin island" (after
 * Settings, Stats and Profile): [HomeViewModel] is resolved by Koin (`koinViewModel()`) while every
 * other feature stays on Hilt. This continues the incremental, per-feature cutover proven by Spike D.
 *
 * ## Bridge pattern (same as the earlier islands)
 * [HomeViewModel] is the heaviest island so far — it depends on seventeen singletons still owned by the
 * Hilt object graph. Rather than re-providing them in Koin — which would risk duplicate construction /
 * divergent state — `ManaHubApp` is the bridge: it `@Inject`s the already-constructed Hilt instances and
 * passes the Home-only ones into [homeKoinModule], which re-exposes them to Koin as `single { }`.
 *
 * Seven of the seventeen dependencies are SHARED with other islands and are therefore NOT registered
 * here — they are bridged exactly once in `coreBridgeKoinModule` (registering the same type in two loaded
 * modules would throw `DefinitionOverrideException`), and this module resolves them via `get()`:
 * - [UserPreferencesDataStore] — shared with Settings + Profile.
 * - [AuthRepository] — shared with Settings + Profile.
 * - [GameSessionRepository] — shared with Stats + Profile.
 * - [StatsRepository] — shared with Profile (promoted to the bridge for Home).
 * - [DeckRepository] — shared with Stats (promoted to the bridge for Home).
 * - [ScryfallRemoteDataSource] — shared with Stats (promoted to the bridge for Home).
 * - [GamificationRepository] — shared with Profile (promoted to the bridge for Home).
 * - `CardRepository` — shared with CommunityDecks (promoted to the bridge for that island).
 * - DraftRepository / DraftSimRepository — shared with Draft (promoted to the bridge for that island).
 * - TournamentRepository — shared with Tournament (promoted to the bridge for that island).
 * - WishlistRepository — shared with Trades + CardDetail (promoted to the bridge for the Trades island;
 *   was a Home-only `single` here until then).
 *
 * As features migrate, each `single { hiltInstance }` here is replaced by a real Koin provider and the
 * matching Hilt `@Provides`/`@Binds` is deleted — so the bridge shrinks to nothing without ever leaving
 * the app uncompilable between commits.
 *
 * `CommunityStatsRepository` was PROMOTED off this bridge (KMP migration batch — 2026-07): the
 * feature-private Hilt `CommunityModule` had exactly one consumer (this island), so it is now
 * natively Koin-built here as `CommunityStatsRepositoryStub()` instead of bridged from Hilt.
 *
 * `GetNewsFeedUseCase`/`RefreshNewsFeedUseCase`/`ManageSourcesUseCase` (KMP migration batch 2) are now
 * natively Koin-built in `SharedDomainKoinModule` — resolved below via `get()`, not registered here
 * anymore.
 *
 * @return a Koin [Module] that provides the Home-only bridged singleton and the [HomeViewModel] factory.
 */
fun homeKoinModule(
    getAccountNudgeUseCase: GetAccountNudgeUseCase,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Home-only Hilt-owned singleton to Koin. ──
    // (UserPreferencesDataStore, AuthRepository, GameSessionRepository, StatsRepository, DeckRepository,
    //  ScryfallRemoteDataSource, GamificationRepository, CardRepository, DraftRepository, DraftSimRepository,
    //  TournamentRepository and WishlistRepository are shared → bridged in coreBridgeKoinModule; the three
    //  news use cases are singles in SharedDomainKoinModule. All resolved below via get().)
    single { getAccountNudgeUseCase }

    // ── CommunityStatsRepository: natively Koin-built (Hilt CommunityModule deleted). ──
    // Single consumer (this island) — no promotion to coreBridgeKoinModule needed.
    single<CommunityStatsRepository> { CommunityStatsRepositoryStub() }

    // ── The Koin island: HomeViewModel is now resolved by Koin, not Hilt. ──
    viewModel {
        HomeViewModel(
            userPrefsDataStore = get(),
            statsRepository = get(),
            deckRepository = get(),
            gameSessionRepository = get(),
            draftSimRepository = get(),
            tournamentRepository = get(),
            authRepository = get(),
            cardRepository = get(),
            scryfallRemoteDataSource = get(),
            getNewsFeedUseCase = get(),
            refreshNewsFeedUseCase = get(),
            manageSourcesUseCase = get(),
            communityStatsRepository = get(),
            draftRepository = get(),
            wishlistRepository = get(),
            getAccountNudgeUseCase = get(),
            gamificationRepository = get(),
        )
    }
}
