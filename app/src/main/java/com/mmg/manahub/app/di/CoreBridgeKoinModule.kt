package com.mmg.manahub.app.di

import android.content.Context
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.common.provideCrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.FriendDao
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.dao.StatsDao
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.repository.DeckRepositoryImpl
import com.mmg.manahub.core.data.repository.StatsRepositoryImpl
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.feature.auth.data.repository.AuthRepositoryImpl
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.domain.repository.DraftRepository
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.feature.draft.data.DraftRepositoryImpl
import com.mmg.manahub.feature.draft.data.DraftSimRepositoryImpl
import com.mmg.manahub.feature.friends.data.repository.FriendRepositoryImpl
import com.mmg.manahub.feature.game.data.repository.GameSessionRepositoryImpl
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
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
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
 * ## KMP migration — Hilt→Koin cutover batch 4
 * [FriendRepository] and [GameSessionRepository] are now NATIVELY Koin-built here too (their impls lost
 * `@Inject`/`@Singleton`; the feature-private Hilt `FriendModule`/`GameModule` were deleted). [FriendDao]
 * and [GameSessionDao] are newly forward-bridged (Room stays androidMain) to build them.
 * [ProgressionEventBus] is ALSO now natively constructed here (`single { ProgressionEventBus() }`)
 * instead of bridging a Hilt instance — `ManaHubApp` switched its own field from `@Inject lateinit var`
 * to a Koin `by inject()` delegate (see `ManaHubApp`'s KDoc) so its direct `AppOpenedToday` emission and
 * the whole gamification engine graph (`gamificationEngineKoinModule`) still share this ONE instance.
 * [GamificationRepository] moved OUT of this module — it is now natively built in
 * `com.mmg.manahub.core.gamification.di.gamificationEngineKoinModule` (the whole gamification engine
 * graph lives there); Profile/Home/GamificationCelebration keep resolving it via `get()` unchanged.
 * `FriendRepository` has a surviving Hilt-only consumer (`core.sync.CollectionStatsSyncWorker`,
 * `@HiltWorker`) — reverse-bridged in `KoinToHiltBridgeModule`. `GameSessionRepository` was audited
 * (excluded online/voice/scanner/nearby trees + every `@HiltWorker`) — no Hilt-only consumer found, so
 * it needs no reverse bridge.
 *
 * ## KMP migration — Hilt→Koin cutover batch 6
 * [FriendRepository] and [GameSessionRepository] are now NATIVELY Koin-built here too (their impls lost
 * `@Inject`/`@Singleton`; the feature-private Hilt `FriendModule`/`GameModule` were deleted). [FriendDao]
 * and [GameSessionDao] are newly forward-bridged (Room stays androidMain) to build them.
 *
 * ## KMP migration — Hilt→Koin cutover batch 7
 * [StatsRepository] is now NATIVELY Koin-built here too (lost its `@Inject constructor`; its Hilt binding
 * in `RepositoryModule` was deleted). This breaks the startup-ordering hazard where `ManaHubApp`'s own
 * eager Hilt injection of this repository triggered Koin's reverse bridge (and thus
 * `GlobalContext.get()`) before `startKoin()` had run. [StatsDao] is newly forward-bridged to build it.
 *
 * @param userPreferencesRepo the Hilt-owned [UserPreferencesRepository] singleton (Settings + Stats).
 * @param userPrefsDataStore the Hilt-owned [UserPreferencesDataStore] singleton (Settings + Profile + Home).
 * @param scryfallRemoteDataSource the Hilt-owned [ScryfallRemoteDataSource] singleton (Stats + Home).
 * @param cardRepository the Hilt-owned [CardRepository] singleton (Home + CommunityDecks + CardDetail).
 * @param analyticsHelper the Hilt-owned [AnalyticsHelper] singleton (Settings + CardDetail).
 * @param deckDao the Hilt/Room-owned [DeckDao] singleton (Room stays androidMain) — needed to build
 *   [DeckRepositoryImpl] natively.
 * @param friendDao the Hilt/Room-owned [FriendDao] singleton (Room stays androidMain) — needed to build
 *   [FriendRepositoryImpl] natively.
 * @param gameSessionDao the Hilt/Room-owned [GameSessionDao] singleton (Room stays androidMain) — needed
 *   to build [GameSessionRepositoryImpl] natively.
 * @param statsDao the Hilt/Room-owned [StatsDao] singleton (Room stays androidMain) — needed to build
 *   [StatsRepositoryImpl] natively.
 * @param okHttpClient the Hilt-owned app-wide [OkHttpClient] singleton — needed to build
 *   `NewsFeedService` (`newsKoinModule`); ALSO still used directly by `ManaHubApp` for the Coil image
 *   loader (unchanged).
 * @param supabaseClient the Hilt-owned [SupabaseClient] singleton (`SupabaseModule`) — needed to build
 *   the five trades remote data sources (`tradesKoinModule`) and the Trades/Wishlist/OpenForTrade
 *   repositories below.
 * @param userCardRepository the Hilt-owned [UserCardRepository] provider (CardDetail + Trades).
 * @return a Koin [Module] exposing the cross-island bridged singletons plus the natively-Koin-constructed
 *   [TournamentRepository]/[DeckRepository]/[DraftRepository]/[DraftSimRepository]/[TradesRepository]/
 *   [WishlistRepository]/[OpenForTradeRepository]/[FriendRepository]/[GameSessionRepository]/[StatsRepository].
 */
fun coreBridgeKoinModule(
    userPreferencesRepo: UserPreferencesRepository,
    userPrefsDataStore: UserPreferencesDataStore,
    scryfallRemoteDataSource: ScryfallRemoteDataSource,
    cardRepository: CardRepository,
    analyticsHelper: AnalyticsHelper,
    deckDao: DeckDao,
    friendDao: FriendDao,
    gameSessionDao: GameSessionDao,
    statsDao: StatsDao,
    okHttpClient: OkHttpClient,
    supabaseClient: SupabaseClient,
    userCardRepository: () -> UserCardRepository,
): Module = module {
    // ── KMP platform abstractions (not Hilt-owned — instantiated directly). ──
    single<CrashReporter> { provideCrashReporter() }
    single { DispatcherProvider() }

    // ── Named IO dispatcher qualifier (Trades audit finding 4.2, 2026-07-10). Koin-built classes
    //    should resolve `get(named("io"))` instead of passing `Dispatchers.IO` literally: a literal
    //    (a) is not available on wasmJs and (b) can't be swapped for a `TestDispatcher` in ViewModel
    //    tests without changing the DI graph. Mirrors the still-Hilt `DispatcherModule.provideIoDispatcher()`
    //    — the two coexist during the migration; this is NOT yet wired to every Koin island's existing
    //    `Dispatchers.IO` literal (only `tradesKoinModule` consumes it so far — a broader sweep is a
    //    separate follow-up, out of this fix's scope). ──
    single<CoroutineDispatcher>(named("io")) { Dispatchers.IO }

    // ── AuthRepository: natively Koin-constructed (KMP migration batch 5; Hilt `AuthModule` deleted).
    //    Shared across nearly every island — registered exactly once here. `Auth` is derived directly
    //    from the already-bridged SupabaseClient single (no separate bridge/single needed); the
    //    `@Named("supabase")` OkHttpClient and UserProfileClient/UserProfileDataSource are natively
    //    Koin-built in `authKoinModule` — resolved cross-module via `get()`. `applicationScope` reuses
    //    the CoroutineScope single already registered by `decksKoinModule`. ──
    single<AuthRepository> {
        AuthRepositoryImpl(
            supabaseAuth = get<SupabaseClient>().auth,
            userProfileDataSource = get(),
            userProfileClient = get(),
            userPreferencesDataStore = get(),
            supabaseOkHttpClient = get(named("supabase")),
            applicationScope = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }

    // Shared across the Settings + Stats + Profile + Home + CommunityDecks + CardDetail + Friends +
    // Draft + Tournament + Trades + Decks islands — each registered exactly once.
    single { userPreferencesRepo }
    single { userPrefsDataStore }
    single { scryfallRemoteDataSource }
    single { cardRepository }
    single { analyticsHelper }
    single { deckDao }
    single { friendDao }
    single { gameSessionDao }
    single { statsDao }
    single { okHttpClient }
    single { supabaseClient }
    single { userCardRepository() }

    // Natively constructed (batch 4) — the SAME instance the whole gamification engine graph
    // (gamificationEngineKoinModule) and ManaHubApp's own direct emission share.
    single { ProgressionEventBus() }

    // ── FriendRepository: natively Koin-constructed (KMP migration batch 4; Hilt `FriendModule`
    //    deleted). Shared by Profile + Friends — registered exactly once here. `FriendRemoteDataSource`
    //    comes from `friendsKoinModule` — resolved cross-module via `get()`. ──
    single<FriendRepository> {
        FriendRepositoryImpl(
            dao = get(),
            remote = get(),
            cardRepo = get(),
            progressionEventBus = get(),
            crashReporter = get(),
        )
    }

    // ── GameSessionRepository: natively Koin-constructed (KMP migration batch 4; Hilt `GameModule`
    //    deleted). Shared by Stats + Profile + Home + Survey — registered exactly once here.
    //    `SurveyAnswerDao` is already a single in `profileKoinModule` — resolved cross-module via
    //    `get()`. ──
    single<GameSessionRepository> {
        GameSessionRepositoryImpl(
            dao = get(),
            progressionEventBus = get(),
            ioDispatcher = Dispatchers.IO,
            surveyAnswerDao = get(),
        )
    }

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

    // ── StatsRepository: natively Koin-constructed (KMP migration batch 7; Residual binding deleted
    //    from RepositoryModule). Shared across nearly every island — registered exactly once here. ──
    single<StatsRepository> {
        StatsRepositoryImpl(
            statsDao = get(),
            deckDao = get(),
            authRepository = get(),
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
            cardRepository = get(),
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
