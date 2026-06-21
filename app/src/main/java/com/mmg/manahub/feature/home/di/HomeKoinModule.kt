package com.mmg.manahub.feature.home.di

import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CommunityStatsRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.feature.home.domain.usecase.GetAccountNudgeUseCase
import com.mmg.manahub.feature.home.presentation.HomeViewModel
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
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
 *
 * As features migrate, each `single { hiltInstance }` here is replaced by a real Koin provider and the
 * matching Hilt `@Provides`/`@Binds` is deleted — so the bridge shrinks to nothing without ever leaving
 * the app uncompilable between commits.
 *
 * @return a Koin [Module] that provides the Home-only bridged singletons and the [HomeViewModel] factory.
 */
fun homeKoinModule(
    draftSimRepository: DraftSimRepository,
    tournamentRepository: TournamentRepository,
    cardRepository: CardRepository,
    getNewsFeedUseCase: GetNewsFeedUseCase,
    refreshNewsFeedUseCase: RefreshNewsFeedUseCase,
    manageSourcesUseCase: ManageSourcesUseCase,
    communityStatsRepository: CommunityStatsRepository,
    draftRepository: DraftRepository,
    wishlistRepository: WishlistRepository,
    getAccountNudgeUseCase: GetAccountNudgeUseCase,
): Module = module {
    // ── Hilt → Koin bridge: re-expose the Home-only Hilt-owned singletons to Koin. ──
    // (UserPreferencesDataStore, AuthRepository, GameSessionRepository, StatsRepository, DeckRepository,
    //  ScryfallRemoteDataSource and GamificationRepository are shared → bridged in coreBridgeKoinModule,
    //  not here, and resolved below via get().)
    single { draftSimRepository }
    single { tournamentRepository }
    single { cardRepository }
    single { getNewsFeedUseCase }
    single { refreshNewsFeedUseCase }
    single { manageSourcesUseCase }
    single { communityStatsRepository }
    single { draftRepository }
    single { wishlistRepository }
    single { getAccountNudgeUseCase }

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
