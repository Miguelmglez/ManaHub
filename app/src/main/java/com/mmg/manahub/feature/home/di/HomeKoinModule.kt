package com.mmg.manahub.feature.home.di

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.CommunityStatsRemoteDataSource
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.repository.CommunityStatsRepositoryImpl
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.CommunityStatsRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.PlaytestRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.repository.TradeSuggestionsRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.domain.usecase.home.GetAccountNudgeUseCase
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.feature.communitydecks.domain.usecase.SearchCommunityDecksUseCase
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
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
 * natively Koin-built here as the real, Supabase-RPC-backed [CommunityStatsRepositoryImpl] (see the
 * Home feature overhaul Phase 1.2.b block below) instead of bridged from Hilt. It replaced the old
 * always-null `CommunityStatsRepositoryStub()` this comment previously described.
 *
 * `GetNewsFeedUseCase`/`RefreshNewsFeedUseCase`/`ManageSourcesUseCase` (KMP migration batch 2) are now
 * natively Koin-built in `SharedDomainKoinModule` — resolved below via `get()`, not registered here
 * anymore.
 *
 * `GetAccountNudgeUseCase` (KMP migration — closing minor debt) moved to `:shared:core-domain`
 * (`com.mmg.manahub.core.domain.usecase.home`) and lost its Hilt `@Inject` — it is a stateless,
 * dependency-free class, so it is now natively Koin-built here instead of bridged from Hilt.
 *
 * @return a Koin [Module] that provides the Home-only singletons and the [HomeViewModel] factory.
 */
fun homeKoinModule(): Module = module {
    // ── Bridged shared singletons (UserPreferencesDataStore, AuthRepository, GameSessionRepository,
    //    StatsRepository, DeckRepository, ScryfallRemoteDataSource, GamificationRepository,
    //    CardRepository, DraftRepository, DraftSimRepository, TournamentRepository, WishlistRepository,
    //    UserCardRepository, TradesRepository, OpenForTradeRepository) live in coreBridgeKoinModule;
    //    FriendRepository is also bridged there (shared with Trades). TradeSuggestionsRepository is a
    //    single in tradesKoinModule. PlaytestRepository is a single in playtestKoinModule.
    //    ArchidektClient is a single in communityDecksKoinModule. CommunityAggregateCache is a single
    //    in communityAggregateKoinModule. All these modules load unconditionally in the same
    //    ManaHubApp `modules(...)` call, so they are all resolved below via get(). ──
    single { GetAccountNudgeUseCase() }

    // ── CommunityStatsRepository (Home feature overhaul Phase 1.2.b): real Supabase-RPC-backed
    //    impl. DORMANT since the Home widget board overhaul (TASK 5c) — Home no longer consumes
    //    it (the old SOCIAL_HUB MostWishlisted/Milestones slides were dropped as low-value when
    //    SOCIAL_HUB was split into FRIENDS + COMMUNITY_DECKS). Left registered rather than deleted
    //    per this codebase's dormant-registration convention (see `project_dormant_budget_pool`
    //    memory) in case a future surface wants the same community-stats backend. ──
    single { CommunityStatsRemoteDataSource(supabaseClient = get()) }
    single<CommunityStatsRepository> {
        CommunityStatsRepositoryImpl(
            remote = get(),
            cache = get(),
            crashReporter = get(),
            now = { System.currentTimeMillis() },
        )
    }

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
            draftRepository = get(),
            wishlistRepository = get(),
            getAccountNudgeUseCase = get(),
            gamificationRepository = get(),
            userCardRepository = get(),
            tradesRepository = get(),
            openForTradeRepository = get(),
            tradeSuggestionsRepository = get(),
            friendRepository = get(),
            playtestRepository = get(),
            // Backend & Performance Optimization plan, WS1+WS3 Part B item 9 (2026-07-28) — SyncManager
            // is bridged in coreBridgeKoinModule (promoted from Collection; see that module's KDoc).
            syncManager = get(),
            // Home widget board overhaul, TASK 5b — from communityDecksKoinModule (loaded in the
            // same ManaHubApp `modules(...)` call), no parallel data path.
            searchCommunityDecksUseCase = get(),
            // Deck Doctor Community/Archetype plan, Phase 5 — from communityAggregateKoinModule
            // (loaded in the same ManaHubApp `modules(...)` call).
            communityAggregateRepository = get(),
        )
    }
}
