package com.mmg.manahub.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.app.di.coreBridgeKoinModule
import com.mmg.manahub.core.data.cache.ManaSymbolStore
import com.mmg.manahub.core.data.local.PendingInviteStore
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.CardStrategyTagsCacheDao
import com.mmg.manahub.core.data.local.dao.ComboCacheDao
import com.mmg.manahub.core.data.local.dao.CommunityAggregateDao
import com.mmg.manahub.core.data.local.dao.CommunityDeckCacheDao
import com.mmg.manahub.core.data.local.dao.CompetitiveLimitedRatingsCacheDao
import com.mmg.manahub.core.data.local.dao.CompetitiveMetaCacheDao
import com.mmg.manahub.core.data.local.dao.DeckDao
import com.mmg.manahub.core.data.local.dao.DraftSessionDao
import com.mmg.manahub.core.data.local.dao.DraftSetDao
import com.mmg.manahub.core.data.local.dao.FriendDao
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.dao.GamificationDao
import com.mmg.manahub.core.data.local.dao.GamificationStatsDao
import com.mmg.manahub.core.data.local.dao.LocalOpenForTradeDao
import com.mmg.manahub.core.data.local.dao.LocalWishlistDao
import com.mmg.manahub.core.data.local.dao.NewsDao
import com.mmg.manahub.core.data.local.dao.PlaytestDao
import com.mmg.manahub.core.data.local.dao.PuzzleDao
import com.mmg.manahub.core.data.local.dao.StatsDao
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.data.local.dao.SurveyCardImpactDao
import com.mmg.manahub.core.data.local.dao.TournamentDao
import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.ScryfallClient
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.remote.push.PushTokenRemoteDataSource
import com.mmg.manahub.core.data.usecase.symbols.SyncManaSymbolsUseCase
import com.mmg.manahub.core.di.cardStrategyTagsKoinModule
import com.mmg.manahub.core.di.sharedDomainKoinModule
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.NotificationPrefsRepository
import com.mmg.manahub.core.domain.repository.PushTokenRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.gamification.data.sync.GamificationSyncManager
import com.mmg.manahub.core.gamification.data.sync.GamificationSyncWorker
import com.mmg.manahub.core.gamification.data.sync.QuestRotationWorker
import com.mmg.manahub.core.gamification.di.gamificationEngineKoinModule
import com.mmg.manahub.core.gamification.domain.GamificationEngine
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.engine.AchievementBackfill
import com.mmg.manahub.core.gamification.engine.EntitlementGranter
import com.mmg.manahub.core.gamification.engine.QuestReconciler
import com.mmg.manahub.core.nearby.domain.repository.NearbySessionRepository
import com.mmg.manahub.core.online.domain.usecase.AdvancePhaseUseCase
import com.mmg.manahub.core.online.domain.usecase.ConfirmDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.LeaveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.NextTurnUseCase
import com.mmg.manahub.core.online.domain.usecase.ObserveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.RevokeDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.ToggleLandPlayedUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateCommanderDamageUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateCounterUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateLifeUseCase
import com.mmg.manahub.core.push.di.pushKoinModule
import com.mmg.manahub.core.sync.CardBackfillWorker
import com.mmg.manahub.core.sync.CardHydrationWorker
import com.mmg.manahub.core.sync.CollectionMergeConflictResolver
import com.mmg.manahub.core.sync.CollectionStatsSyncWorker
import com.mmg.manahub.core.sync.CollectionSyncWorker
import com.mmg.manahub.core.sync.PriceRefreshWorker
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.sync.di.syncKoinModule
import com.mmg.manahub.core.tagging.TagDictionaryRepository
import com.mmg.manahub.core.ui.components.search.di.searchWidgetsKoinModule
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.voice.domain.VoiceCommandRecognizer
import com.mmg.manahub.core.voice.domain.VoiceModelRepository
import com.mmg.manahub.feature.addcard.di.addCardKoinModule
import com.mmg.manahub.feature.auth.di.authKoinModule
import com.mmg.manahub.feature.carddetail.di.cardDetailKoinModule
import com.mmg.manahub.feature.collection.di.collectionKoinModule
import com.mmg.manahub.feature.communitydecks.di.communityDecksKoinModule
import com.mmg.manahub.feature.competitive.di.competitiveKoinModule
import com.mmg.manahub.feature.decks.di.commanderSpellbookKoinModule
import com.mmg.manahub.feature.decks.di.communityAggregateKoinModule
import com.mmg.manahub.feature.decks.di.decksKoinModule
import com.mmg.manahub.feature.draft.di.draftKoinModule
import com.mmg.manahub.feature.friends.di.friendsKoinModule
import com.mmg.manahub.feature.game.di.gameKoinModule
import com.mmg.manahub.feature.gamification.di.gamificationKoinModule
import com.mmg.manahub.feature.home.di.homeKoinModule
import com.mmg.manahub.feature.massiveadd.di.massiveAddCardKoinModule
import com.mmg.manahub.feature.news.di.newsKoinModule
import com.mmg.manahub.feature.playtest.di.playtestKoinModule
import com.mmg.manahub.feature.profile.di.profileKoinModule
import com.mmg.manahub.feature.puzzle.di.puzzleKoinModule
import com.mmg.manahub.feature.settings.di.settingsKoinModule
import com.mmg.manahub.feature.splash.di.splashKoinModule
import com.mmg.manahub.feature.stats.di.statsKoinModule
import com.mmg.manahub.feature.survey.di.surveyKoinModule
import com.mmg.manahub.feature.tagdictionary.di.tagDictionaryKoinModule
import com.mmg.manahub.feature.tournament.di.tournamentKoinModule
import com.mmg.manahub.feature.trades.di.tradesKoinModule
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import javax.inject.Inject

@HiltAndroidApp
class ManaHubApp : Application(), KoinComponent {

    // SyncManaSymbolsUseCase moved to SharedDomainKoinModule (Koin-native, batch 2). Resolved via
    // KoinComponent's `by inject()` instead of a Hilt `@Inject` field: this property is only read from
    // `appScope.launch { }` AFTER `startKoin()` has already run (below, in onCreate()), and `by inject()`
    // is lazy — it queries the running Koin container on first access, not at object-construction time
    // — so this is safe. (A Hilt `@Inject` field, by contrast, would be populated by Hilt BEFORE this
    // class's onCreate() body runs, i.e. before startKoin() — see KoinToHiltBridgeModule's KDoc for why
    // that ordering hazard rules out routing this the other way, Hilt-provider-calls-into-Koin.)
    private val syncManaSymbols: SyncManaSymbolsUseCase by inject()

    // ── KMP migration — Hilt→Koin cutover batch 4 ───────────────────────────────────────────────
    // The whole gamification engine graph (ADR-002) is now natively Koin-built in
    // `gamificationEngineKoinModule` — the Hilt `core.gamification.di.GamificationModule` that used to
    // provide these via `@Binds`/`@Provides`/implicit `@Inject constructor` satisfaction was deleted.
    // These six properties switched from Hilt `@Inject lateinit var` to Koin `by inject()` delegates —
    // the SAME lazy-resolution pattern already used above for `syncManaSymbols`: every read below
    // happens from `appScope.launch { }` blocks (or `gamificationEngine.start(appScope)` directly) that
    // run strictly AFTER `startKoin()` returns in `onCreate()`, so the lazy resolution is safe.
    private val gamificationEngine: GamificationEngine by inject()
    private val progressionEventBus: ProgressionEventBus by inject()
    private val achievementBackfill: AchievementBackfill by inject()
    private val questReconciler: QuestReconciler by inject()
    private val entitlementGranter: EntitlementGranter by inject()
    private val gamificationSyncManager: GamificationSyncManager by inject()

    // ── KMP migration — Hilt→Koin cutover batch 5 ───────────────────────────────────────────────
    // AuthRepository is now natively Koin-built in `coreBridgeKoinModule` (the feature-private Hilt
    // `AuthModule` was deleted) — switched from a Hilt `@Inject lateinit var` to a Koin `by inject()`
    // delegate, the same lazy-resolution pattern used above: its only direct use below
    // (`authRepository.sessionState.collect { }`) runs from an `appScope.launch { }` block strictly
    // AFTER `startKoin()` returns in `onCreate()`, so lazy resolution is safe.
    private val authRepository: AuthRepository by inject()

    @Inject lateinit var tagDictionaryRepo: TagDictionaryRepository
    @Inject lateinit var workManager: WorkManager
    @Inject lateinit var pushTokenRepository: PushTokenRepository
    // KMP migration — Hilt→Koin cutover batch 6 (WorkManager subsystem). PushTokenRemoteDataSource
    // keeps its Hilt @Inject constructor (PushTokenRepositoryImpl, still Hilt via PushModule, needs it) —
    // this field just forward-bridges the SAME already-constructed singleton to Koin's pushKoinModule
    // for the two push workers. See pushKoinModule's KDoc for the full ordering rationale.
    @Inject lateinit var pushTokenRemoteDataSource: PushTokenRemoteDataSource
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var userPreferencesDataStore: UserPreferencesDataStore

    // ── KMP migration — Hilt→Koin bridge dependencies ───────────────────────────────────────────
    // These singletons are still owned by Hilt. ManaHubApp is the bridge: it @Inject's them from the
    // Hilt graph and hands them to the per-feature Koin modules when starting Koin, so the
    // Koin-resolved ViewModels share the exact same singleton instances (no duplicate graph).
    //
    // Spike D (Settings island) + Phase 1 (Stats island, the second cutover).
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository  // shared: Settings + Stats
    @Inject lateinit var analyticsHelper: AnalyticsHelper
    @Inject lateinit var notificationPrefsRepository: NotificationPrefsRepository
    @Inject lateinit var voiceModelRepository: VoiceModelRepository
    @Inject lateinit var supabaseClient: SupabaseClient  // shared: Trades' five remote data sources

    // SharedDomainKoinModule (batch 2) forward-bridge deps. These three types have no Koin presence via
    // any other bridge yet — see SharedDomainKoinModule's KDoc for the full per-type rationale. (News'
    // NewsRepository and Draft's DraftEngine/DraftDeckBuilder are natively Koin-built as of batch 3 —
    // see newsKoinModule/draftKoinModule — so neither needs a bridge field here anymore.)
    @Inject lateinit var scryfallClient: ScryfallClient
    @Inject lateinit var scryfallRequestQueue: ScryfallRequestQueue
    @Inject lateinit var manaSymbolStore: ManaSymbolStore
    // DeckDao (Room, stays androidMain) — needed to build DeckRepositoryImpl natively in
    // coreBridgeKoinModule as of KMP migration batch 3 (Hilt `bindDeckRepository` was deleted).
    @Inject lateinit var deckDao: DeckDao

    // Stats island (Phase 1) bridge deps. GetCollectionStatsUseCase/GetCollectionSetCodesUseCase/
    // RefreshCollectionPricesUseCase moved to SharedDomainKoinModule (batch 2) — statsKoinModule now
    // resolves all three via get(), so no field is needed for them anymore. DeckRepository is natively
    // Koin-built in coreBridgeKoinModule as of batch 3 — no bridge field needed for it anymore.
    // GameSessionRepository is natively Koin-built in coreBridgeKoinModule as of batch 4 (Hilt `GameModule`
    // deleted) — `gameSessionDao` below (Room, stays androidMain) is forward-bridged to build it.
    @Inject lateinit var scryfallRemoteDataSource: ScryfallRemoteDataSource
    @Inject lateinit var gameSessionDao: GameSessionDao  // shared: Stats + Profile + Home (via GameSessionRepository)
    // KMP migration — Hilt→Koin cutover batch 6 (WorkManager subsystem). StatsDao (Room, stays
    // androidMain / Hilt-DatabaseModule-owned) is a NEW forward-bridge, needed by the Koin-registered
    // CollectionStatsSyncWorker in core.sync.di.syncKoinModule AND by StatsRepositoryImpl (batch 7).
    @Inject lateinit var statsDao: StatsDao

    // Profile island (Phase 1) bridge deps. (userPreferencesDataStore + authRepository are shared with
    // Settings and gameSessionRepository is shared with Stats — all bridged in coreBridgeKoinModule.
    // statsRepository + gamificationRepository are now also shared with Home → bridged in coreBridge.)
    // claimQuestRewardUseCase is now a native single in gamificationEngineKoinModule (batch 4) — no bridge
    // field needed for it anymore. friendRepository/gamificationRepository likewise dropped: FriendRepository
    // is natively Koin-built in coreBridgeKoinModule via `friendDao` below; GamificationRepository is
    // natively Koin-built in gamificationEngineKoinModule.
    @Inject lateinit var surveyAnswerDao: SurveyAnswerDao

    // Home island (Phase 1) bridge deps. The shared deps (userPreferencesDataStore, authRepository,
    // gameSessionRepository, statsRepository, scryfallRemoteDataSource, gamificationRepository) are
    // bridged in coreBridgeKoinModule; only the Home-only deps are here. TournamentRepository,
    // DeckRepository, DraftRepository/DraftSimRepository and WishlistRepository are ALL natively
    // Koin-built in coreBridgeKoinModule (their owning Hilt modules were deleted) — no bridge field
    // needed for any of them anymore. CommunityStatsRepository is natively Koin-built in homeKoinModule.
    @Inject lateinit var cardRepository: CardRepository  // shared: Home + CommunityDecks (bridged in coreBridge)
    // GetAccountNudgeUseCase moved to :shared:core-domain and is natively Koin-built in homeKoinModule
    // (KMP migration — closing minor debt); no bridge field needed for it anymore.
    // GetNewsFeedUseCase/RefreshNewsFeedUseCase/ManageSourcesUseCase moved to SharedDomainKoinModule
    // (batch 2) — homeKoinModule/newsKoinModule now resolve all three via get(), no field needed.

    // AddCard island (Phase 1) bridge deps. UserPreferencesRepository is bridged in
    // coreBridgeKoinModule (shared with Settings + Stats). SearchCardsUseCase/BuildScryfallQueryUseCase
    // moved to SharedDomainKoinModule (batch 2) — addCardKoinModule now resolves both via get(), so this
    // island needs NO bridge field of its own anymore.

    // CommunityDecks island (Phase 1) bridge deps. UserPreferencesDataStore + DeckRepository + CardRepository
    // are all bridged in coreBridgeKoinModule (shared with other islands); the rest of this island's data
    // layer (ArchidektApi/RequestQueue/Repository/use cases) is now Koin-owned in communityDecksKoinModule.
    // Only the Room-owned cache DAO (this island only) is bridged here.
    @Inject lateinit var communityDeckCacheDao: CommunityDeckCacheDao

    // Community Aggregate (Motor B, Deck Doctor Community/Archetype plan Phase 3.3) bridge dep.
    // Unrelated to communityDeckCacheDao above (that's the Archidekt browse/import cache; this is
    // the EDHREC/Archidekt suggestion-aggregate cache). No consumer UI yet — wired ahead of Motor B.
    @Inject lateinit var communityAggregateDao: CommunityAggregateDao

    // Commander Spellbook combo cache (Deck Engine Unification plan D7, Phase 4.3 — synergy
    // browser Combos tab) bridge dep. Unrelated to communityAggregateDao above.
    @Inject lateinit var comboCacheDao: ComboCacheDao

    // Competitive feature (Phase 5) bridge deps: the two Room-owned cache DAOs for the
    // `manahub-competitive` Cloudflare Worker's weekly meta snapshots + 17lands Limited ratings.
    // Unrelated to communityAggregateDao/comboCacheDao above.
    @Inject lateinit var competitiveMetaCacheDao: CompetitiveMetaCacheDao
    @Inject lateinit var competitiveLimitedRatingsCacheDao: CompetitiveLimitedRatingsCacheDao

    // Card strategy tags cache (offline tag pipeline precomputed tags) — Deck Engine Unification
    // plan D8, Phase 5c. Serves cardStrategyTagsKoinModule (CardDetailViewModel's read point); the
    // eager-Hilt CardRepositoryImpl gets its OWN CardStrategyTagsRepository instance via a residual
    // SharedDomainUseCaseModule provider (same DAO singleton, see that provider's KDoc for why).
    @Inject lateinit var cardStrategyTagsCacheDao: CardStrategyTagsCacheDao

    // Daily Puzzle feature (Batch B1 foundation). Serves puzzleKoinModule — the Room-owned
    // PuzzleDao bridge, same pattern as cardStrategyTagsCacheDao above.
    @Inject lateinit var puzzleDao: PuzzleDao

    // CardDetail island (Phase 1) bridge deps. The shared deps are NOT re-declared here:
    //  - AnalyticsHelper is now bridged in coreBridgeKoinModule (promoted from Settings; shared with it).
    //  - CardRepository, DeckRepository, UserPreferencesRepository, UserPreferencesDataStore,
    //    AuthRepository, WishlistRepository and OpenForTradeRepository are ALL bridged in
    //    coreBridgeKoinModule (shared with other islands, natively Koin-built as of batch 3).
    // Only the CardDetail-only deps are here.
    // AddCardToCollectionUseCase/AddToWishlistUseCase moved to SharedDomainKoinModule (batch 2) —
    // cardDetailKoinModule now resolves both via get(). AddToWishlistUseCase is ALSO consumed by the
    // still-Hilt (excluded) ScannerViewModel, via KoinToHiltBridgeModule's reverse bridge.
    @Inject lateinit var userCardRepository: Lazy<UserCardRepository>

    // Friends island (Phase 1) bridge deps. FriendRepository is shared with Profile → natively Koin-built
    // in coreBridgeKoinModule as of batch 4 (Hilt `FriendModule` deleted); `friendDao` below (Room, stays
    // androidMain) is forward-bridged to build it there. AuthRepository + AnalyticsHelper are also bridged
    // in coreBridge (shared). TradesRepository is natively Koin-built in coreBridgeKoinModule as of
    // batch 3 — no bridge field needed for it anymore. The `@Named("supabaseKtor")` HttpClient is now
    // natively Koin-built in `authKoinModule` (batch 5; Hilt `AuthModule` deleted) — `friendsKoinModule`
    // resolves it cross-module via `get(named("supabaseKtor"))` instead of a forward-bridge field, so no
    // field is needed for it here anymore. Only PendingInviteStore (deferred invite codes for
    // InviteDispatcher) is still Friends-only here.
    @Inject lateinit var pendingInviteStore: PendingInviteStore
    @Inject lateinit var friendDao: FriendDao

    // Survey island (Phase 1) bridge deps. The shared deps are NOT re-declared here:
    //  - SurveyAnswerDao is already injected (above, Profile island field) and bridged by profileKoinModule.
    //  - GameSessionDao is already injected (above, Stats island field) and bridged by coreBridgeKoinModule
    //    (batch 4 — it feeds the natively-Koin-built GameSessionRepository there).
    //  - DeckRepository + UserPreferencesRepository are bridged in coreBridgeKoinModule.
    //  - The application Context + IO dispatcher are supplied by Koin (androidContext() / Dispatchers.IO).
    // Only the Survey-only singletons are here.
    @Inject lateinit var surveyCardImpactDao: SurveyCardImpactDao
    @Inject lateinit var cardDao: CardDao
    // CompleteSurveyUseCase moved to SharedDomainKoinModule (batch 2) — surveyKoinModule now resolves
    // it via get().

    // Splash island (Phase 1) needs NO new bridge field — its only dep (AuthRepository) is already
    // bridged in coreBridgeKoinModule. News island (KMP migration batch 3) needs only the Room-owned
    // NewsDao (Room stays androidMain) — its data layer (NewsRepositoryImpl + collaborators) is now
    // natively Koin-built in newsKoinModule; the 3 news use cases + UserPreferencesDataStore are already
    // bridged elsewhere (SharedDomainKoinModule + coreBridgeKoinModule).
    @Inject lateinit var newsDao: NewsDao

    // Draft island (KMP migration batch 3) bridge deps. The feature-private Hilt DraftModule was
    // CONVERTED and DELETED: DraftRepository/DraftSimRepository (coreBridgeKoinModule) and
    // DraftEngine/DraftDeckBuilder/BotDrafter (draftKoinModule) are all natively Koin-built now — only
    // the Room-owned DAOs (Room stays androidMain) are still bridged here.
    @Inject lateinit var draftSetDao: DraftSetDao
    @Inject lateinit var draftSessionDao: DraftSessionDao

    // Playtest island (Phase 1) bridge dep. The feature-private Hilt PlaytestModule was converted +
    // DELETED (its @Binds PlaytestRepository is consumed by no Hilt feature) → the repo + the six use
    // cases are now Koin-owned in playtestKoinModule. Only the Room/DatabaseModule-owned PlaytestDao
    // (still Hilt-provided, this island only) is bridged here. DeckRepository is reused from
    // coreBridgeKoinModule and CardDao from surveyKoinModule via get().
    @Inject lateinit var playtestDao: PlaytestDao

    // Tournament island (Phase 1) bridge deps. The feature-private Hilt `TournamentModule` was CONVERTED
    // and DELETED (KMP migration batch, 2026-07): GameViewModel is already a plain Koin-resolved class
    // (gameKoinModule), so nothing in the Hilt graph consumes TournamentRepository / CalculateStandingsUseCase
    // / RecordMatchResultUseCase / GenerateNextRoundUseCase anymore — they are now built directly by Koin
    // (tournamentKoinModule + coreBridgeKoinModule). Only the Room-owned TournamentDao (still Hilt/
    // DatabaseModule-provided, Room stays androidMain) is bridged here.
    @Inject lateinit var tournamentDao: TournamentDao

    // Trades island (KMP migration batch 3) bridge deps. The feature-private Hilt TradesModule was
    // CONVERTED and DELETED: TradesRepository/WishlistRepository/OpenForTradeRepository are natively
    // Koin-built in coreBridgeKoinModule (shared across islands); SharedListsRepository/
    // TradeSuggestionsRepository (trades-only) plus all five remote data sources are natively Koin-built
    // in tradesKoinModule. Only the Room-owned DAOs (Room stays androidMain) are still bridged here.
    @Inject lateinit var tradeCollectionSyncDao: TradeCollectionSyncDao
    @Inject lateinit var localWishlistDao: LocalWishlistDao
    @Inject lateinit var localOpenForTradeDao: LocalOpenForTradeDao

    // Collection island (Phase 1) bridge deps. The shared deps are NOT re-declared here:
    //  - CardRepository, AuthRepository, UserPreferencesRepository, AnalyticsHelper, WishlistRepository
    //    and OpenForTradeRepository are all bridged in coreBridgeKoinModule (shared with other islands).
    //  - GetLocalWishlistUseCase is already a single in tradesKoinModule and UserCardRepository is already
    //    a single in cardDetailKoinModule — both resolved via get(), not re-registered.
    //  - WorkManager is already injected above (the same singleton used for global sync scheduling).
    // GetCollectionUseCase/MigrateLocalTradeListsUseCase moved to SharedDomainKoinModule (batch 2) —
    // collectionKoinModule now resolves both via get(). Only SyncManager stays a bridge field here.
    @Inject lateinit var syncManager: SyncManager

    // Collection sync data-loss fix, Phase 7 (write-path hardening, 2026-09-06): bridge field for
    // CollectionMergeConflictResolver — resolves pending guest/account collection conflicts left
    // behind by UserCardCollectionDao.assignUserId's NOT EXISTS collision guard. Consumed only by
    // CollectionViewModel today; bridged here (not directly in collectionKoinModule) following the
    // same "cross-cutting-owned-once, feature-consumed-via-get()" convention as syncManager above.
    @Inject lateinit var collectionMergeConflictResolver: CollectionMergeConflictResolver

    // Decks island (KMP migration batch 3) bridge dep. The feature-private Hilt DeckDoctorModule was
    // CONVERTED and DELETED: the entire Deck Doctor scoring engine (DeckScorer + its graph) and all six
    // deck use cases are now natively Koin-built in decksKoinModule (they were already plain classes in
    // :shared:core-domain — the ONLY class needing @Inject stripped was Draft's own
    // ScoringDraftDeckBuilder). DeckRepository, CardRepository, WishlistRepository,
    // UserPreferencesRepository, UserPreferencesDataStore, AuthRepository and AnalyticsHelper are
    // bridged in coreBridgeKoinModule; UserCardRepository is a single in cardDetailKoinModule;
    // SyncManager is a single in collectionKoinModule; SearchCardsUseCase/SuggestTagsUseCase/
    // GetDeckGameStatsUseCase are singles in SharedDomainKoinModule — all resolved via get(), never
    // re-registered. The Hilt `@ApplicationScope` CoroutineScope bridge was REMOVED (Deck Wizard &
    // Engine Rework plan, WS7.1) along with the legacy `DeckMagicDetailViewModel` it existed
    // solely for — decksKoinModule() now takes no bridge deps.
    //
    // Production crash fix (2026-07-29): removing that registration orphaned every OTHER Koin
    // consumer of a bare CoroutineScope (AuthRepositoryImpl, CommunityDeckImportCoordinator) —
    // decksKoinModule happened to be the only place registering it, so nothing else could resolve
    // it once Decks stopped needing it. The CoroutineScope itself is now bridged into Koin via
    // `coreBridgeKoinModule`'s own `appScope` param below (this class's existing `appScope` field,
    // see line ~393), NOT decksKoinModule — a shared cross-island singleton, owned by the shared
    // bridge module, so it can never again be silently orphaned by a single feature's retirement.

    // Game island (Phase 1, the LAST non-excluded island) bridge deps. The shared deps are NOT
    // re-declared here — GameSessionRepository, TournamentRepository, AnalyticsHelper and
    // UserPreferencesDataStore are bridged in coreBridgeKoinModule; RecordMatchResultUseCase is already a
    // single in tournamentKoinModule; VoiceModelRepository (GameSetup) is a single in settingsKoinModule;
    // gamificationEngine is now a native Koin single in gamificationEngineKoinModule (batch 4) — all
    // resolved via get(), never re-registered (a duplicate single<T> would throw DefinitionOverrideException).
    // EvaluatePlayerEliminationUseCase is likewise now a plain native single in gameKoinModule itself
    // (no ctor deps) — no bridge field needed for it anymore.
    //
    // GameViewModel integrates the DEFERRED core/voice + core/online + core/nearby features, which stay
    // 100% Hilt-owned. Those singletons are bridged here from the Hilt graph (NOT de-Hilt'd) so the one
    // shared instance serves both DI graphs — exactly the tournament-use-case bridge pattern. Nothing
    // under core/voice, feature/online, core/nearby or feature/scanner is migrated.
    @Inject lateinit var observeSessionUseCase: ObserveSessionUseCase
    @Inject lateinit var updateLifeUseCase: UpdateLifeUseCase
    @Inject lateinit var advancePhaseUseCase: AdvancePhaseUseCase
    @Inject lateinit var nextTurnUseCase: NextTurnUseCase
    @Inject lateinit var updateCounterUseCase: UpdateCounterUseCase
    @Inject lateinit var updateCommanderDamageUseCase: UpdateCommanderDamageUseCase
    @Inject lateinit var confirmDefeatUseCase: ConfirmDefeatUseCase
    @Inject lateinit var revokeDefeatUseCase: RevokeDefeatUseCase
    @Inject lateinit var leaveSessionUseCase: LeaveSessionUseCase
    @Inject lateinit var toggleLandPlayedUseCase: ToggleLandPlayedUseCase
    @Inject lateinit var nearbySessionRepository: NearbySessionRepository
    @Inject lateinit var voiceCommandRecognizer: VoiceCommandRecognizer

    // Gamification engine island (KMP migration batch 4) bridge deps. The whole Hilt
    // `core.gamification.di.GamificationModule` was deleted — `gamificationEngineKoinModule` builds the
    // entire engine graph natively. Only the Room-owned DAOs (Room stays androidMain) are bridged here;
    // ProgressionEventBus is natively constructed in coreBridgeKoinModule (see its KDoc).
    @Inject lateinit var gamificationDao: GamificationDao
    @Inject lateinit var gamificationStatsDao: GamificationStatsDao

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // ── KMP migration — Phase 0 Spike D: start Koin ALONGSIDE the Hilt graph ────────────────
        // Hilt field injection has already run by the time super.onCreate() returns, so the bridged
        // singletons below are non-null. Koin and Hilt run side-by-side: the Settings "Koin island"
        // resolves SettingsViewModel via koinViewModel(); every other feature still uses Hilt.
        // This is the proof that the Hilt→Koin cutover can be incremental, not big-bang.
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.ERROR)
            androidContext(this@ManaHubApp)
            modules(
                // Shared bridged singletons used by more than one Koin island
                // (Settings + Stats + Profile + Home).
                coreBridgeKoinModule(
                    userPreferencesRepo = userPreferencesRepository,
                    userPrefsDataStore = userPreferencesDataStore,
                    scryfallRemoteDataSource = scryfallRemoteDataSource,
                    cardRepository = cardRepository,
                    analyticsHelper = analyticsHelper,
                    deckDao = deckDao,
                    friendDao = friendDao,
                    gameSessionDao = gameSessionDao,
                    statsDao = statsDao,
                    okHttpClient = okHttpClient,
                    supabaseClient = supabaseClient,
                    userCardRepository = { userCardRepository.get() },
                    syncManager = syncManager,
                    collectionMergeConflictResolver = collectionMergeConflictResolver,
                    appScope = appScope,
                ),
                // The gamification engine graph (ADR-002), natively Koin-built (batch 4; Hilt
                // `core.gamification.di.GamificationModule` deleted). Must load alongside coreBridgeKoinModule
                // (ProgressionEventBus) and every island below that resolves GamificationRepository/
                // GamificationEngine/ClaimQuestRewardUseCase via get() (Profile, Home, Game, GamificationCelebration).
                gamificationEngineKoinModule(
                    gamificationDao = gamificationDao,
                    gamificationStatsDao = gamificationStatsDao,
                ),
                settingsKoinModule(
                    pushTokenRepository = pushTokenRepository,
                    notificationPrefsRepository = notificationPrefsRepository,
                    voiceModelRepository = voiceModelRepository,
                ),
                authKoinModule(),
                // The cross-island shared domain use cases (batch 2). Must be loaded alongside every
                // island below that resolves one of its ~27 singles via get() — Koin doesn't care about
                // declaration order within one modules(...) call, only that all modules load together.
                sharedDomainKoinModule(
                    scryfallClient = scryfallClient,
                    scryfallRequestQueue = scryfallRequestQueue,
                    manaSymbolStore = manaSymbolStore,
                ),
                statsKoinModule(),
                profileKoinModule(
                    surveyAnswerDao = surveyAnswerDao,
                ),
                homeKoinModule(),
                tagDictionaryKoinModule(
                    tagDictionaryRepository = tagDictionaryRepo,
                ),
                addCardKoinModule(),
                communityDecksKoinModule(
                    cacheDao = communityDeckCacheDao,
                ),
                communityAggregateKoinModule(
                    cacheDao = communityAggregateDao,
                ),
                commanderSpellbookKoinModule(
                    cacheDao = comboCacheDao,
                ),
                cardStrategyTagsKoinModule(
                    cacheDao = cardStrategyTagsCacheDao,
                ),
                puzzleKoinModule(
                    puzzleDao = puzzleDao,
                ),
                cardDetailKoinModule(),
                friendsKoinModule(
                    pendingInviteStore = pendingInviteStore,
                ),
                splashKoinModule(),
                surveyKoinModule(
                    surveyCardImpactDao = surveyCardImpactDao,
                    cardDao = cardDao,
                ),
                newsKoinModule(
                    newsDao = newsDao,
                ),
                draftKoinModule(
                    draftSetDao = draftSetDao,
                    draftSessionDao = draftSessionDao,
                ),
                playtestKoinModule(
                    playtestDao = playtestDao,
                ),
                tournamentKoinModule(
                    tournamentDao = tournamentDao,
                ),
                tradesKoinModule(
                    tradeCollectionSyncDao = tradeCollectionSyncDao,
                    localWishlistDao = localWishlistDao,
                    localOpenForTradeDao = localOpenForTradeDao,
                ),
                collectionKoinModule(
                    workManager = workManager,
                ),
                // KMP migration — Hilt→Koin cutover batch 6 (WorkManager subsystem): the two remaining
                // core/sync workers (CollectionStatsSyncWorker, PriceRefreshWorker — CollectionSyncWorker's
                // worker { } lives in collectionKoinModule above) and the two core/push workers.
                syncKoinModule(
                    statsDao = statsDao,
                ),
                pushKoinModule(
                    pushTokenRemoteDataSource = pushTokenRemoteDataSource,
                ),
                searchWidgetsKoinModule(),
                gamificationKoinModule(),
                decksKoinModule(),
                competitiveKoinModule(
                    metaCacheDao = competitiveMetaCacheDao,
                    limitedRatingsCacheDao = competitiveLimitedRatingsCacheDao,
                ),
                gameKoinModule(
                    observeSession = observeSessionUseCase,
                    updateLife = updateLifeUseCase,
                    advancePhase = advancePhaseUseCase,
                    nextTurn = nextTurnUseCase,
                    updateCounter = updateCounterUseCase,
                    updateCommanderDamage = updateCommanderDamageUseCase,
                    confirmDefeat = confirmDefeatUseCase,
                    revokeDefeat = revokeDefeatUseCase,
                    leaveSession = leaveSessionUseCase,
                    toggleLandPlayed = toggleLandPlayedUseCase,
                    nearbyRepository = nearbySessionRepository,
                    voiceCommandRecognizer = voiceCommandRecognizer,
                ),
                massiveAddCardKoinModule()
            )
        }

        createNotificationChannels()

        FirebaseCrashlytics.getInstance().apply {
            isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
            setCustomKey("app_version_name", BuildConfig.VERSION_NAME)
        }

        SingletonImageLoader.setSafe {
            ImageLoader.Builder(this)
                .components {
                    add(SvgDecoder.Factory())
                    add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                }
                .memoryCache {
                    MemoryCache.Builder()
                        // Large heap is enabled in Manifest, but we tune this down to 15% (from default 25%)
                        // to ensure background services and OS components have enough free RAM.
                        .maxSizePercent(this@ManaHubApp, 0.15)
                        .build()
                }
                .build()
        }

        appScope.launch {
            runCatching { syncManaSymbols() }
            runCatching { tagDictionaryRepo.loadAndApply() }
        }
        // Backend & Performance Optimization plan, WS1+WS3 Part B item 8 (2026-07-28): the
        // oracle-id + strategy-tags opportunistic backfills that used to run inline here (Edge-case
        // audit A3, 2026-07-15 / 2026-07-22) moved to a daily WorkManager job — see
        // CardBackfillWorker's KDoc for the ordering invariant + sync-window deferral it preserves.
        CardBackfillWorker.scheduleDaily(workManager)

        // Collection sync data-loss fix, Phase 4 (2026-09-06): hourly retry of any
        // pending-hydration card placeholder SyncManager.ensureCardsExist wrote when Scryfall
        // couldn't resolve a card during a pull — see CardHydrationWorker's KDoc for why this is
        // a separate, more frequent worker than CardBackfillWorker above. Ungated by auth (a
        // placeholder can belong to a guest's local-only collection too).
        CardHydrationWorker.schedulePeriodic(workManager)

        // ── Gamification backend gate (WS1+WS3 Part A, backend-performance-optimization-plan.md §1,
        //    F1) ────────────────────────────────────────────────────────────────────────────────
        // `gamificationEnabledFlow` defaults to false (the UI is hidden for this release) but
        // previously gated ONLY the UI: the engine's permanent event-bus collector, the retroactive
        // backfill/reconcile passes, the daily AppOpenedToday ledger write and the quest reconciler
        // + its daily worker all ran unconditionally on every cold start regardless of the flag — a
        // feature nobody can see was still doing Room aggregate scans + a standing collector on
        // every device. Gated here on the SAME flag, REACTIVELY (`collect`, not `.first()`) so a
        // future flag flip (e.g. from Settings, once its currently-commented-out switch is
        // re-enabled) starts this work without an app restart.
        //
        // DELIBERATE ADR-002 OVERRIDE: ADR-002 says the engine should keep recording silently while
        // the UI is hidden. That is overridden here by explicit user decision (2026-07-28): every
        // write path this gate skips is idempotent and RETROACTIVE (the XP ledger's UNIQUE key,
        // AchievementBackfill/EntitlementGranter's own idempotent-insert guards, `reconcileAll`'s
        // full re-derivation from current level + unlocked achievements) — enabling the flag later
        // recomputes the user's true state from scratch, so nothing is lost by not recording while
        // it is off. See ADR-002 §12 + memory `project_gamification_backend_gate_2026-07-28` so a
        // future agent does not "fix" this back to always-on.
        //
        // `gamificationEngine.start()` is documented idempotent (an internal AtomicBoolean guard —
        // see GamificationEngineImpl), so re-observing `enabled=true` after a hypothetical
        // OFF→ON→OFF→ON flip sequence is a harmless no-op re-call. The one-shot-per-process tasks
        // below (backfill/reconcileAll/AppOpenedToday/quest reconcile) are additionally guarded by
        // `gamificationOneShotStartupTasksRun` so a flag flip mid-session cannot re-run them
        // repeatedly — they fire on the FIRST observed `enabled=true` only, exactly once per process
        // lifetime (their own idempotency guards, e.g. the backfill's DataStore flag, are a SEPARATE
        // cross-launch concern and are kept unchanged).
        var gamificationOneShotStartupTasksRun = false
        appScope.launch {
            userPreferencesDataStore.gamificationEnabledFlow
                .distinctUntilChanged()
                .collect { enabled ->
                    FirebaseCrashlytics.getInstance()
                        .log(if (enabled) "gamification_gate_enabled" else "gamification_gate_disabled")
                    if (enabled) {
                        gamificationEngine.start(appScope)
                        QuestRotationWorker.scheduleDaily(workManager)

                        if (!gamificationOneShotStartupTasksRun) {
                            gamificationOneShotStartupTasksRun = true

                            // One-shot Family-A achievement backfill (ADR-002 §4): retroactively
                            // unlock achievements the user already qualifies for, suppressing
                            // celebrations. Guarded by a DataStore flag so it runs exactly once
                            // after the v39 migration. Failures are swallowed — never block app
                            // start.
                            appScope.launch {
                                runCatching {
                                    if (!userPreferencesDataStore.isGamificationBackfillDone()) {
                                        achievementBackfill.run()
                                        userPreferencesDataStore.setGamificationBackfillDone()
                                    }
                                }
                                // Retroactive cosmetic catch-up (ADR-002 §10): grant entitlements
                                // the player already qualifies for (current level + all unlocked
                                // achievements, incl. any just backfilled). Idempotent — only
                                // inserts missing rows — so it is safe on every launch. Runs AFTER
                                // the backfill block above so backfilled unlocks are visible to it.
                                // Failures swallowed; never block app start.
                                runCatching { entitlementGranter.reconcileAll() }
                            }

                            appScope.launch {
                                runCatching {
                                    progressionEventBus.emit(
                                        ProgressionEvent.AppOpenedToday(
                                            localDate = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString(),
                                            occurredAt = Clock.System.now(),
                                        )
                                    )
                                }
                            }

                            // Roll quests over on app start (local-first: runs regardless of auth).
                            // Idempotent — settles any stale instances and generates the current
                            // period if missing. Failures swallowed.
                            appScope.launch {
                                runCatching { questReconciler.reconcile() }
                            }
                        }
                    } else {
                        // The engine's event-bus collector has no `stop()` (see GamificationEngine's
                        // KDoc) — not a gap in practice today: the flag is not currently reachable
                        // from any UI (SettingsScreen's switch is commented out,
                        // SettingsScreen.kt:289-290), so an ON→OFF flip mid-session cannot happen in
                        // production yet. If that switch is ever re-enabled, revisit this branch to
                        // also stop event processing. Cancelling the daily quest worker IS reachable
                        // today (a user with a stale enqueue from a previous install) and is cheap
                        // regardless of whether it was ever scheduled.
                        workManager.cancelUniqueWork(QuestRotationWorker.WORK_NAME)
                    }
                }
        }

        PriceRefreshWorker.scheduleDailyRefresh(workManager)
        CollectionStatsSyncWorker.scheduleDailySync(workManager)

        // Schedule/cancel the periodic background sync based on auth state.
        // CollectionViewModel also does this for the collection screen, but this
        // global observer ensures sync is cancelled even when that screen is not alive.
        //
        // Collection sync data-loss fix, Phase 5 (2026-09-06): the offline-to-online
        // first-login full pull (SyncManager.assignUserIdAndSync) used to be launched from
        // `CollectionViewModel.observeSessionChanges` on `viewModelScope` — navigating away from
        // the Collection screen mid-pull cancelled it, silently leaving a first-login account
        // partially migrated with no automatic retry. It is now dispatched here as durable
        // WorkManager unique work (CollectionSyncWorker.enqueueFirstLoginSync), which survives
        // both navigation and process death, mirroring why this observer already lives at the
        // app scope for periodic scheduling. `previousUserId` (captured by this launch's closure,
        // living for the app process) is the app-scope equivalent of the ViewModel's old
        // `previouslyAuthenticated` flag: it guards against a rapid second `Authenticated` emission
        // (profile enrichment) re-triggering the first-login pull for the SAME user, while still
        // firing again if a DIFFERENT user signs in after a sign-out.
        var previousUserId: String? = null
        appScope.launch {
            authRepository.sessionState.collect { state ->
                when (state) {
                    is SessionState.Authenticated -> {
                        CollectionSyncWorker.schedulePeriodicSync(workManager)
                        if (previousUserId != state.user.id) {
                            previousUserId = state.user.id
                            CollectionSyncWorker.enqueueFirstLoginSync(workManager)
                        }
                        appScope.launch {
                            runCatching {
                                val token = FirebaseMessaging.getInstance().token.await()
                                pushTokenRepository.register(token)
                            }
                        }
                    }
                    is SessionState.Unauthenticated -> {
                        previousUserId = null
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_PERIODIC)
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_ONE_TIME)
                        // Own unique name (write-path hardening audit, 2026-09-06): must be
                        // cancelled too, or a stale first-login work would KEEP-block the next
                        // account's enqueueFirstLoginSync call.
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_FIRST_LOGIN)
                        appScope.launch {
                            runCatching {
                                val token = FirebaseMessaging.getInstance().token.await()
                                pushTokenRepository.unregister(token)
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        // Gamification Phase 4 sync (ADR-002 §11), now ALSO gated on the backend master flag
        // (WS1+WS3 Part A item 2, backend-performance-optimization-plan.md §1): schedule the
        // periodic worker AND run a one-time guest→account reconcile ONLY when the user is BOTH
        // authenticated AND gamification is enabled; ALWAYS cancel both work names otherwise — this
        // covers a user who already has the periodic worker enqueued from a previous install, or
        // who disables the flag while signed in. A separate `combine` collector (not folded into the
        // auth branch above) so an unrelated flag flip never re-triggers the push-token register/
        // unregister calls, which must stay auth-only. Monotonic merges make `reconcileOnSignIn`
        // idempotent, so re-observing the same (Authenticated, true) pair — e.g. an unrelated
        // session-state re-emission — is a harmless re-run, same tolerance the single collector this
        // replaced already had.
        appScope.launch {
            combine(
                authRepository.sessionState,
                userPreferencesDataStore.gamificationEnabledFlow,
            ) { state, enabled -> state to enabled }
                .distinctUntilChanged()
                .collect { (state, enabled) ->
                    if (state is SessionState.Authenticated && enabled) {
                        GamificationSyncWorker.schedulePeriodicSync(workManager)
                        appScope.launch {
                            runCatching { gamificationSyncManager.reconcileOnSignIn(state.user.id) }
                        }
                    } else {
                        workManager.cancelUniqueWork(GamificationSyncWorker.WORK_NAME_PERIODIC)
                        workManager.cancelUniqueWork(GamificationSyncWorker.WORK_NAME_ONE_TIME)
                    }
                }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel("trades_high", "Trade Proposals", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New trade proposals and counter-proposals"
            },
            NotificationChannel("trades_updates", "Trade Updates", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Trade accepted, declined, cancelled, completed"
            },
            NotificationChannel("friends", "Friends", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Friend requests and acceptances"
            }
        ).forEach { nm.createNotificationChannel(it) }
    }
}
