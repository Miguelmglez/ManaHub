package com.mmg.manahub.app.di

import android.content.Context
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.common.provideCrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.repository.DeckRepositoryImpl
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.domain.repository.DraftRepository
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.feature.draft.data.DraftRepositoryImpl
import com.mmg.manahub.feature.draft.data.DraftSimRepositoryImpl
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.feature.tournament.data.repository.TournamentRepositoryImpl
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import com.mmg.manahub.feature.trades.data.repository.OpenForTradeRepositoryImpl
import com.mmg.manahub.feature.trades.data.repository.TradesRepositoryImpl
import com.mmg.manahub.feature.trades.data.repository.WishlistRepositoryImpl
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. Shared "Koin bridge" for Hilt-owned singletons that are
 * consumed by MORE THAN ONE Koin island (currently Settings, Stats, Profile, Home, CommunityDecks,
 * CardDetail, Friends, Draft, Trades, Collection and Decks).
 *
 * ## Why a shared module
 * Each Koin island re-exposes its Hilt-owned dependencies as `single { instance }` (the Spike-D bridge
 * pattern). When two islands depend on the SAME singleton, registering it in both feature modules would
 * load two `single<T>` definitions for the same type into one Koin container, which throws
 * `DefinitionOverrideException` at `startKoin`. Bridged singletons that are shared across islands
 * therefore live here, in ONE place, and each feature module just resolves them via `get()`.
 *
 * As features migrate further in Phase 1, a dependency's `single { hiltInstance }` here is replaced by a
 * real Koin provider and the matching Hilt `@Provides`/`@Binds` is deleted — without ever leaving the
 * app uncompilable between commits.
 *
 * ## KMP migration — Hilt→Koin cutover batch 3
 * [DeckRepository], [DraftRepository], [DraftSimRepository], [TradesRepository], [WishlistRepository]
 * and [OpenForTradeRepository] are now NATIVELY Koin-built here (their impls lost `@Inject`/`@Singleton`
 * this batch — a full-codebase audit confirmed none had a remaining Hilt-only consumer). Each repo's
 * OWN infra (Room DAOs, Ktor clients, remote data sources) is registered as a `single` in its owning
 * feature module (`draftKoinModule`, `tradesKoinModule`) or here (`deckDao`) — Koin resolves `get()`
 * across ALL loaded modules regardless of declaration site, exactly like the pre-existing
 * `TournamentDao`/`TournamentRepository` split below. [OkHttpClient] and [SupabaseClient] are newly
 * forward-bridged this batch (no Koin presence before): the former feeds `NewsFeedService`
 * (`newsKoinModule`), the latter feeds all five trades remote data sources (`tradesKoinModule`).
 *
 * @param userPreferencesRepo the Hilt-owned [UserPreferencesRepository] singleton (Settings + Stats).
 * @param userPrefsDataStore the Hilt-owned [UserPreferencesDataStore] singleton (Settings + Profile + Home).
 * @param authRepository the Hilt-owned [AuthRepository] singleton (Settings + Profile + Home).
 * @param gameSessionRepository the Hilt-owned [GameSessionRepository] singleton (Stats + Profile + Home).
 * @param statsRepository the Hilt-owned [StatsRepository] singleton (Profile + Home).
 * @param scryfallRemoteDataSource the Hilt-owned [ScryfallRemoteDataSource] singleton (Stats + Home).
 * @param gamificationRepository the Hilt-owned [GamificationRepository] singleton (Profile + Home).
 * @param cardRepository the Hilt-owned [CardRepository] singleton (Home + CommunityDecks + CardDetail).
 * @param analyticsHelper the Hilt-owned [AnalyticsHelper] singleton (Settings + CardDetail).
 * @param friendRepository the Hilt-owned [FriendRepository] singleton (Profile + Friends).
 * @param progressionEventBus the Hilt-owned [ProgressionEventBus] singleton (gamification event bus).
 *   Bridged here (from the ALREADY-existing `ManaHubApp` field used by the gamification engine) so that
 *   natively-Koin-constructed repositories emit onto the SAME instance the Hilt-owned
 *   `GamificationEngine` collects. Constructing a second `ProgressionEventBus` in Koin would silently
 *   orphan its events (nothing would ever collect them).
 * @param deckDao the Hilt/Room-owned [DeckDao] singleton (Room stays androidMain) — needed to build
 *   [DeckRepositoryImpl] natively.
 * @param okHttpClient the Hilt-owned app-wide [OkHttpClient] singleton — needed to build
 *   `NewsFeedService` (`newsKoinModule`); ALSO still used directly by `ManaHubApp` for the Coil image
 *   loader (unchanged).
 * @param supabaseClient the Hilt-owned [SupabaseClient] singleton (`SupabaseModule`) — needed to build
 *   the five trades remote data sources (`tradesKoinModule`) and the Trades/Wishlist/OpenForTrade
 *   repositories below.
 * @return a Koin [Module] exposing the cross-island bridged singletons plus the natively-Koin-constructed
 *   [TournamentRepository]/[DeckRepository]/[DraftRepository]/[DraftSimRepository]/[TradesRepository]/
 *   [WishlistRepository]/[OpenForTradeRepository].
 */
fun coreBridgeKoinModule(
    userPreferencesRepo: UserPreferencesRepository,
    userPrefsDataStore: UserPreferencesDataStore,
    authRepository: AuthRepository,
    gameSessionRepository: GameSessionRepository,
    statsRepository: StatsRepository,
    scryfallRemoteDataSource: ScryfallRemoteDataSource,
    gamificationRepository: GamificationRepository,
    cardRepository: CardRepository,
    analyticsHelper: AnalyticsHelper,
    friendRepository: FriendRepository,
    progressionEventBus: ProgressionEventBus,
    deckDao: DeckDao,
    okHttpClient: OkHttpClient,
    supabaseClient: SupabaseClient,
): Module = module {
    // ── KMP platform abstractions (not Hilt-owned — instantiated directly). ──
    single<CrashReporter> { provideCrashReporter() }
    single { DispatcherProvider() }

    // Shared across the Settings + Stats + Profile + Home + CommunityDecks + CardDetail + Friends +
    // Draft + Tournament + Trades + Decks islands — each registered exactly once.
    single { userPreferencesRepo }
    single { userPrefsDataStore }
    single { authRepository }
    single { gameSessionRepository }
    single { statsRepository }
    single { scryfallRemoteDataSource }
    single { gamificationRepository }
    single { cardRepository }
    single { analyticsHelper }
    single { friendRepository }
    single { progressionEventBus }
    single { deckDao }
    single { okHttpClient }
    single { supabaseClient }

    // ── TournamentRepository: natively Koin-constructed (Hilt `TournamentModule` deleted). ──
    // Shared by the Home + Game + Tournament islands (all Koin now) — registered exactly once here.
    // `TournamentDao` and `GenerateNextRoundUseCase` are registered as singles in `tournamentKoinModule`
    // (Tournament-only); Koin resolves `get()` across modules regardless of declaration site.
    single<TournamentRepository> {
        TournamentRepositoryImpl(
            dao = get(),
            progressionEventBus = get(),
            generateNextRound = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }

    // ── DeckRepository: natively Koin-constructed (KMP migration batch 3; Hilt `bindDeckRepository`
    //    deleted from RepositoryModule). Shared by many islands — registered exactly once here. ──
    single<DeckRepository> {
        DeckRepositoryImpl(
            deckDao = get(),
            progressionEventBus = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }

    // ── DraftRepository / DraftSimRepository: natively Koin-constructed (KMP migration batch 3;
    //    Hilt `DraftModule` deleted). Shared by Home + Draft — registered exactly once here.
    //    `ScryfallClient`/`ScryfallRequestQueue` come from `SharedDomainKoinModule`; `YouTubeClient`/
    //    `CloudflareContentClient`/`Gson`/`DraftSetDao`/`DraftSessionDao` come from `draftKoinModule` —
    //    all resolved cross-module via `get()`. ──
    single<DraftRepository> {
        DraftRepositoryImpl(
            context = androidContext(),
            scryfallApi = get(),
            scryfallQueue = get(),
            youTubeClient = get(),
            cloudflareClient = get(),
            draftSetDao = get(),
            gson = get(),
            draftPrefs = androidContext().getSharedPreferences(
                "draft_content_versions",
                Context.MODE_PRIVATE,
            ),
            ioDispatcher = Dispatchers.IO,
        )
    }
    single<DraftSimRepository> {
        DraftSimRepositoryImpl(
            context = androidContext(),
            cloudflareClient = get(),
            getDraftableSets = get(),
            getSetTierList = get(),
            getSetCardsPage = get(),
            deckRepository = get(),
            draftSessionDao = get(),
            gson = get(),
            ioDispatcher = Dispatchers.IO,
            crashReporter = get(),
        )
    }

    // ── TradesRepository / WishlistRepository / OpenForTradeRepository: natively Koin-constructed
    //    (KMP migration batch 3; Hilt `TradesModule` deleted). Shared across Trades + Home + CardDetail
    //    + Collection + Decks + Friends — registered exactly once here. Their remote data sources +
    //    Room DAOs are registered as singles in `tradesKoinModule`; `CardDao` is a single in
    //    `surveyKoinModule` — all resolved cross-module via `get()`. ──
    single<TradesRepository> {
        TradesRepositoryImpl(
            remote = get(),
            cardDao = get(),
            progressionEventBus = get(),
        )
    }
    single<WishlistRepository> {
        WishlistRepositoryImpl(
            dao = get(),
            remote = get(),
        )
    }
    single<OpenForTradeRepository> {
        OpenForTradeRepositoryImpl(
            dao = get(),
            remote = get(),
        )
    }
}
