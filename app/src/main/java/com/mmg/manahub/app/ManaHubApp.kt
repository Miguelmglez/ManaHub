package com.mmg.manahub.app

// import com.mmg.manahub.feature.scanner.EmbeddingDatabaseUpdater  // COMMENTED OUT — replaced by ML Kit OCR
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.app.di.coreBridgeKoinModule
import com.mmg.manahub.core.data.local.PendingInviteStore
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.CommunityDeckCacheDao
import com.mmg.manahub.core.data.local.dao.PlaytestDao
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.data.local.dao.SurveyCardImpactDao
import com.mmg.manahub.core.data.local.dao.TournamentDao
import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.PushTokenRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.di.ApplicationScope
import com.mmg.manahub.core.di.sharedDomainKoinModule
import com.mmg.manahub.core.data.cache.ManaSymbolStore
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.ScryfallClient
import com.mmg.manahub.core.data.usecase.symbols.SyncManaSymbolsUseCase
import com.mmg.manahub.core.domain.engine.DraftDeckBuilder
import com.mmg.manahub.core.domain.engine.DraftEngine
import com.mmg.manahub.core.domain.repository.NewsRepository
import com.mmg.manahub.core.gamification.data.sync.GamificationSyncManager
import com.mmg.manahub.core.gamification.data.sync.GamificationSyncWorker
import com.mmg.manahub.core.gamification.data.sync.QuestRotationWorker
import com.mmg.manahub.core.gamification.domain.GamificationEngine
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.core.gamification.engine.AchievementBackfill
import com.mmg.manahub.core.gamification.engine.EntitlementGranter
import com.mmg.manahub.core.gamification.engine.QuestReconciler
import com.mmg.manahub.core.sync.CollectionStatsSyncWorker
import com.mmg.manahub.core.sync.CollectionSyncWorker
import com.mmg.manahub.core.sync.PriceRefreshWorker
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.core.tagging.TagDictionaryRepository
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.domain.repository.NotificationPrefsRepository
import com.mmg.manahub.core.domain.repository.StatsRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.gamification.domain.usecase.ClaimQuestRewardUseCase
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.voice.domain.VoiceModelRepository
import com.mmg.manahub.feature.addcard.di.addCardKoinModule
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.feature.auth.di.authKoinModule
import com.mmg.manahub.feature.carddetail.di.cardDetailKoinModule
import com.mmg.manahub.feature.collection.di.collectionKoinModule
import com.mmg.manahub.core.ui.components.search.di.searchWidgetsKoinModule
import com.mmg.manahub.feature.communitydecks.di.communityDecksKoinModule
import com.mmg.manahub.feature.decks.di.decksKoinModule
import com.mmg.manahub.feature.decks.domain.engine.DeckMagicEngine
import com.mmg.manahub.feature.decks.domain.usecase.BuildDeckFromSeedsUseCase
import com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.ImportDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.InferDeckIdentityUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsWithBudgetUseCase
import com.mmg.manahub.feature.decks.domain.usecase.SuggestCutsUseCase
import com.mmg.manahub.feature.draft.di.draftKoinModule
import com.mmg.manahub.core.domain.engine.BotDrafter
import com.mmg.manahub.core.domain.repository.DraftRepository
import com.mmg.manahub.core.domain.repository.DraftSimRepository
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
import com.mmg.manahub.core.voice.domain.VoiceCommandRecognizer
import com.mmg.manahub.feature.friends.di.friendsKoinModule
import com.mmg.manahub.core.domain.repository.FriendRepository
import com.mmg.manahub.feature.gamification.di.gamificationKoinModule
import com.mmg.manahub.feature.game.di.gameKoinModule
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.feature.game.domain.usecase.EvaluatePlayerEliminationUseCase
import com.mmg.manahub.feature.home.di.homeKoinModule
import com.mmg.manahub.feature.home.domain.usecase.GetAccountNudgeUseCase
import com.mmg.manahub.feature.news.di.newsKoinModule
import com.mmg.manahub.feature.playtest.di.playtestKoinModule
import com.mmg.manahub.feature.profile.di.profileKoinModule
import com.mmg.manahub.feature.settings.di.settingsKoinModule
import com.mmg.manahub.feature.splash.di.splashKoinModule
import com.mmg.manahub.feature.stats.di.statsKoinModule
import com.mmg.manahub.feature.survey.di.surveyKoinModule
import com.mmg.manahub.feature.tagdictionary.di.tagDictionaryKoinModule
import com.mmg.manahub.feature.tournament.di.tournamentKoinModule
import com.mmg.manahub.feature.trades.di.tradesKoinModule
import com.mmg.manahub.core.domain.repository.OpenForTradeRepository
import com.mmg.manahub.core.domain.repository.SharedListsRepository
import com.mmg.manahub.core.data.repository.TradesRepository
import com.mmg.manahub.core.domain.repository.WishlistRepository
import dagger.hilt.android.HiltAndroidApp
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
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

    @Inject lateinit var tagDictionaryRepo: TagDictionaryRepository
    @Inject lateinit var workManager: WorkManager
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var pushTokenRepository: PushTokenRepository
    @Inject lateinit var okHttpClient: OkHttpClient
    @Inject lateinit var gamificationEngine: GamificationEngine
    @Inject lateinit var progressionEventBus: ProgressionEventBus
    @Inject lateinit var achievementBackfill: AchievementBackfill
    @Inject lateinit var questReconciler: QuestReconciler
    @Inject lateinit var entitlementGranter: EntitlementGranter
    @Inject lateinit var gamificationSyncManager: GamificationSyncManager
    @Inject lateinit var userPreferencesDataStore: UserPreferencesDataStore
    // @Inject lateinit var embeddingDatabaseUpdater: EmbeddingDatabaseUpdater  // COMMENTED OUT — replaced by ML Kit OCR

    // ── KMP migration — Hilt→Koin bridge dependencies ───────────────────────────────────────────
    // These singletons are still owned by Hilt. ManaHubApp is the bridge: it @Inject's them from the
    // Hilt graph and hands them to the per-feature Koin modules when starting Koin, so the
    // Koin-resolved ViewModels share the exact same singleton instances (no duplicate graph).
    //
    // Spike D (Settings island) + Phase 1 (Stats island, the second cutover).
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository  // shared: Settings + Stats
    @Inject lateinit var analyticsHelper: AnalyticsHelper
    @Inject lateinit var userProfileDataSource: UserProfileDataSource
    @Inject lateinit var notificationPrefsRepository: NotificationPrefsRepository
    @Inject lateinit var voiceModelRepository: VoiceModelRepository

    // SharedDomainKoinModule (batch 2) forward-bridge deps. These six types have no Koin presence via
    // any other bridge yet — see SharedDomainKoinModule's KDoc for the full per-type rationale.
    @Inject lateinit var scryfallClient: ScryfallClient
    @Inject lateinit var scryfallRequestQueue: ScryfallRequestQueue
    @Inject lateinit var manaSymbolStore: ManaSymbolStore
    @Inject lateinit var newsRepository: NewsRepository
    @Inject lateinit var draftEngine: DraftEngine
    @Inject lateinit var draftDeckBuilder: DraftDeckBuilder

    // Stats island (Phase 1) bridge deps. GetCollectionStatsUseCase/GetCollectionSetCodesUseCase/
    // RefreshCollectionPricesUseCase moved to SharedDomainKoinModule (batch 2) — statsKoinModule now
    // resolves all three via get(), so no field is needed for them anymore.
    @Inject lateinit var scryfallRemoteDataSource: ScryfallRemoteDataSource
    @Inject lateinit var gameSessionRepository: GameSessionRepository  // shared: Stats + Profile
    @Inject lateinit var deckRepository: DeckRepository

    // Profile island (Phase 1) bridge deps. (userPreferencesDataStore + authRepository are shared with
    // Settings and gameSessionRepository is shared with Stats — all bridged in coreBridgeKoinModule.
    // statsRepository + gamificationRepository are now also shared with Home → bridged in coreBridge.)
    @Inject lateinit var statsRepository: StatsRepository  // shared: Profile + Home
    @Inject lateinit var surveyAnswerDao: SurveyAnswerDao
    @Inject lateinit var claimQuestRewardUseCase: ClaimQuestRewardUseCase  // Profile island only
    @Inject lateinit var friendRepository: FriendRepository
    @Inject lateinit var gamificationRepository: GamificationRepository  // shared: Profile + Home

    // Home island (Phase 1) bridge deps. The shared deps (userPreferencesDataStore, authRepository,
    // gameSessionRepository, statsRepository, deckRepository, scryfallRemoteDataSource,
    // gamificationRepository) are bridged in coreBridgeKoinModule; only the Home-only deps are here.
    // TournamentRepository is natively Koin-built in coreBridgeKoinModule (Hilt TournamentModule
    // deleted) — no bridge field needed anymore. CommunityStatsRepository is natively Koin-built in
    // homeKoinModule (Hilt CommunityModule deleted) — no bridge field needed anymore.
    @Inject lateinit var draftSimRepository: DraftSimRepository
    @Inject lateinit var cardRepository: CardRepository  // shared: Home + CommunityDecks (bridged in coreBridge)
    @Inject lateinit var draftRepository: DraftRepository
    @Inject lateinit var wishlistRepository: WishlistRepository
    @Inject lateinit var getAccountNudgeUseCase: GetAccountNudgeUseCase
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

    // CardDetail island (Phase 1) bridge deps. The shared deps are NOT re-declared here:
    //  - AnalyticsHelper is now bridged in coreBridgeKoinModule (promoted from Settings; shared with it).
    //  - CardRepository, DeckRepository, UserPreferencesRepository, UserPreferencesDataStore and
    //    AuthRepository are all bridged in coreBridgeKoinModule (shared with other islands).
    //  - WishlistRepository is already a Home bridge field (`wishlistRepository`, above) and is bridged
    //    by homeKoinModule, so CardDetail resolves it via get() — it is NOT re-declared/re-registered.
    // Only the CardDetail-only deps are here.
    // AddCardToCollectionUseCase/AddToWishlistUseCase moved to SharedDomainKoinModule (batch 2) —
    // cardDetailKoinModule now resolves both via get(). AddToWishlistUseCase is ALSO consumed by the
    // still-Hilt (excluded) ScannerViewModel, via KoinToHiltBridgeModule's reverse bridge.
    @Inject lateinit var userCardRepository: UserCardRepository
    @Inject lateinit var openForTradeRepository: OpenForTradeRepository

    // Friends island (Phase 1) bridge deps. FriendRepository is shared with Profile → PROMOTED into
    // coreBridgeKoinModule (the existing `friendRepository` field above feeds it there now). AuthRepository
    // + AnalyticsHelper are also bridged in coreBridge (shared). Only the Friends-only singletons are here:
    //  - TradesRepository: FriendDetail trade history (also used by Hilt Trades VMs → still Hilt-owned).
    //  - PendingInviteStore: deferred invite codes for InviteDispatcher.
    @Inject lateinit var tradesRepository: TradesRepository
    @Inject lateinit var pendingInviteStore: PendingInviteStore

    // Survey island (Phase 1) bridge deps. The shared deps are NOT re-declared here:
    //  - SurveyAnswerDao is already injected (above, Profile island field) and bridged by profileKoinModule.
    //  - GameSessionDao is already injected (above, Stats island field) and bridged by statsKoinModule.
    //  - DeckRepository + UserPreferencesRepository are bridged in coreBridgeKoinModule.
    //  - The application Context + IO dispatcher are supplied by Koin (androidContext() / Dispatchers.IO).
    // Only the Survey-only singletons are here.
    @Inject lateinit var surveyCardImpactDao: SurveyCardImpactDao
    @Inject lateinit var cardDao: CardDao
    // CompleteSurveyUseCase moved to SharedDomainKoinModule (batch 2) — surveyKoinModule now resolves
    // it via get().

    // Splash island (Phase 1) needs NO new bridge field — its only dep (AuthRepository) is already
    // bridged in coreBridgeKoinModule. News island (Phase 1) likewise needs NO new bridge field — its
    // ViewModels' deps (the 3 news use cases + UserPreferencesDataStore) are already bridged
    // (SharedDomainKoinModule + coreBridgeKoinModule), so both modules take no constructor args.

    // Draft island (Phase 1) bridge dep. The feature-private Hilt DraftModule is KEPT (NOT deleted):
    // DraftSimRepositoryImpl (still-Hilt) consumes GetDraftableSetsUseCase/GetSetTierListUseCase/
    // GetSetCardsPageUseCase from the residual SharedDomainUseCaseModule, and DraftEngine/
    // DraftDeckBuilder are forward-bridged above (SharedDomainKoinModule section) for the Koin-native
    // Start/MakePick/AutoPick/CompleteDraft use cases. The other nine Draft use cases moved to
    // SharedDomainKoinModule (batch 2) — draftKoinModule now resolves all of them via get(). Only
    // BotDrafter (stateless, shared with no other island) stays a bridge field here.
    @Inject lateinit var botDrafter: BotDrafter

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

    // Trades island (Phase 1) bridge deps. The trades data layer is split across five repositories by
    // concern; three of them are shared with other islands → PROMOTED into coreBridgeKoinModule and the
    // older islands shrunk: TradesRepository (was Friends-only; shared with Trades + still-Hilt Home/
    // FriendDetail), WishlistRepository (was Home-only; shared with Trades + CardDetail + still-Hilt
    // Collection/DeckStudio/DeckImprovement), OpenForTradeRepository (was CardDetail-only; shared with
    // Trades + still-Hilt Collection) — fed there by the existing tradesRepository / wishlistRepository /
    // openForTradeRepository fields above. UserCardRepository is reused from cardDetailKoinModule and
    // AuthRepository/CardRepository/AnalyticsHelper/FriendRepository from coreBridge, all via get(). The
    // Hilt TradesModule is KEPT (its WishlistRepository/OpenForTradeRepository/TradeSuggestionsRepository
    // @Binds still serve Hilt features). Only the two trades-only bridged singletons are here.
    @Inject lateinit var sharedListsRepository: SharedListsRepository
    @Inject lateinit var tradeCollectionSyncDao: TradeCollectionSyncDao

    // Collection island (Phase 1) bridge deps. The shared deps are NOT re-declared here:
    //  - CardRepository, AuthRepository, UserPreferencesRepository, AnalyticsHelper, WishlistRepository
    //    and OpenForTradeRepository are all bridged in coreBridgeKoinModule (shared with other islands).
    //  - GetLocalWishlistUseCase is already a single in tradesKoinModule and UserCardRepository is already
    //    a single in cardDetailKoinModule — both resolved via get(), not re-registered.
    //  - WorkManager is already injected above (the same singleton used for global sync scheduling).
    // GetCollectionUseCase/MigrateLocalTradeListsUseCase moved to SharedDomainKoinModule (batch 2) —
    // collectionKoinModule now resolves both via get(). Only SyncManager stays a bridge field here.
    @Inject lateinit var syncManager: SyncManager

    // Decks island (Phase 1) bridge deps. The shared deps are NOT re-declared here:
    //  - DeckRepository, CardRepository, WishlistRepository, UserPreferencesRepository,
    //    UserPreferencesDataStore, AuthRepository and AnalyticsHelper are bridged in coreBridgeKoinModule.
    //  - UserCardRepository is already a single in cardDetailKoinModule, SyncManager in collectionKoinModule,
    //    SearchCardsUseCase in addCardKoinModule, and WorkManager in collectionKoinModule — all resolved via
    //    get(), never re-registered.
    // Only the Decks-only Hilt-built singletons are bridged here. The Deck Doctor engine (DeckScorer +
    // its @Inject graph) and the feature-private Hilt DeckDoctorModule are KEPT (still-Hilt Draft consumes
    // the SAME DeckScorer singleton via ScoringDraftDeckBuilder) — these use cases wrap it, so they are
    // bridged from the Hilt graph rather than rebuilt in Koin to keep ONE shared DeckScorer instance.
    // SuggestTagsUseCase/GetDeckGameStatsUseCase moved to SharedDomainKoinModule (batch 2) —
    // decksKoinModule now resolves both via get().
    @Inject lateinit var evaluateDeckUseCase: EvaluateDeckUseCase
    @Inject lateinit var inferDeckIdentityUseCase: InferDeckIdentityUseCase
    @Inject lateinit var suggestCutsUseCase: SuggestCutsUseCase
    @Inject lateinit var suggestAddsWithBudgetUseCase: SuggestAddsWithBudgetUseCase
    @Inject lateinit var buildDeckFromSeedsUseCase: BuildDeckFromSeedsUseCase
    @Inject lateinit var importDeckUseCase: ImportDeckUseCase
    @Inject lateinit var deckMagicEngine: DeckMagicEngine
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    // Game island (Phase 1, the LAST non-excluded island) bridge deps. The shared deps are NOT
    // re-declared here — GameSessionRepository, TournamentRepository, AnalyticsHelper and
    // UserPreferencesDataStore are bridged in coreBridgeKoinModule; RecordMatchResultUseCase is already a
    // single in tournamentKoinModule; VoiceModelRepository (GameSetup) is a single in settingsKoinModule;
    // gamificationEngine is already injected above (Phase 0 field) — all resolved via get(), never
    // re-registered (a duplicate single<T> would throw DefinitionOverrideException).
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
    @Inject lateinit var evaluatePlayerEliminationUseCase: EvaluatePlayerEliminationUseCase

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
                    authRepository = authRepository,
                    gameSessionRepository = gameSessionRepository,
                    statsRepository = statsRepository,
                    deckRepository = deckRepository,
                    scryfallRemoteDataSource = scryfallRemoteDataSource,
                    gamificationRepository = gamificationRepository,
                    cardRepository = cardRepository,
                    analyticsHelper = analyticsHelper,
                    friendRepository = friendRepository,
                    draftRepository = draftRepository,
                    draftSimRepository = draftSimRepository,
                    tradesRepository = tradesRepository,
                    wishlistRepository = wishlistRepository,
                    openForTradeRepository = openForTradeRepository,
                    progressionEventBus = progressionEventBus,
                ),
                settingsKoinModule(
                    userProfileDataSource = userProfileDataSource,
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
                    newsRepository = newsRepository,
                    draftEngine = draftEngine,
                    draftDeckBuilder = draftDeckBuilder,
                ),
                statsKoinModule(),
                profileKoinModule(
                    surveyAnswerDao = surveyAnswerDao,
                    claimQuestRewardUseCase = claimQuestRewardUseCase,
                ),
                homeKoinModule(
                    getAccountNudgeUseCase = getAccountNudgeUseCase,
                ),
                tagDictionaryKoinModule(
                    tagDictionaryRepository = tagDictionaryRepo,
                ),
                addCardKoinModule(),
                communityDecksKoinModule(
                    cacheDao = communityDeckCacheDao,
                ),
                cardDetailKoinModule(
                    userCardRepository = userCardRepository,
                ),
                friendsKoinModule(
                    pendingInviteStore = pendingInviteStore,
                ),
                splashKoinModule(),
                surveyKoinModule(
                    surveyCardImpactDao = surveyCardImpactDao,
                    cardDao = cardDao,
                ),
                newsKoinModule(),
                draftKoinModule(
                    botDrafter = botDrafter,
                ),
                playtestKoinModule(
                    playtestDao = playtestDao,
                ),
                tournamentKoinModule(
                    tournamentDao = tournamentDao,
                ),
                tradesKoinModule(
                    sharedListsRepository = sharedListsRepository,
                    tradeCollectionSyncDao = tradeCollectionSyncDao,
                ),
                collectionKoinModule(
                    syncManager = syncManager,
                    workManager = workManager,
                ),
                searchWidgetsKoinModule(),
                gamificationKoinModule(),
                decksKoinModule(
                    evaluateDeck = evaluateDeckUseCase,
                    inferDeckIdentity = inferDeckIdentityUseCase,
                    suggestCuts = suggestCutsUseCase,
                    suggestAddsWithBudget = suggestAddsWithBudgetUseCase,
                    buildDeckFromSeeds = buildDeckFromSeedsUseCase,
                    importDeck = importDeckUseCase,
                    deckMagicEngine = deckMagicEngine,
                    applicationScope = applicationScope,
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
                    evaluatePlayerElimination = evaluatePlayerEliminationUseCase,
                    gamificationEngine = gamificationEngine,
                ),
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
                .build()
        }

        appScope.launch {
            runCatching { syncManaSymbols() }
            runCatching { tagDictionaryRepo.loadAndApply() }
        }

        // Start the gamification engine collecting the progression bus (idempotent), then
        // emit the daily-open event. The engine's ledger (key app_open:{localDate}) dedupes
        // multiple cold starts the same day, so a plain emit on every launch is correct.
        gamificationEngine.start(appScope)

        // One-shot Family-A achievement backfill (ADR-002 §4): retroactively unlock achievements the
        // user already qualifies for, suppressing celebrations. Guarded by a DataStore flag so it runs
        // exactly once after the v39 migration. Failures are swallowed — never block app start.
        appScope.launch {
            runCatching {
                if (!userPreferencesDataStore.isGamificationBackfillDone()) {
                    achievementBackfill.run()
                    userPreferencesDataStore.setGamificationBackfillDone()
                }
            }
            // Retroactive cosmetic catch-up (ADR-002 §10): grant entitlements the player already
            // qualifies for (current level + all unlocked achievements, incl. any just backfilled).
            // Idempotent — only inserts missing rows — so it is safe on every launch. Runs AFTER the
            // backfill block above so backfilled achievement unlocks are visible to it. Failures
            // swallowed; never block app start.
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

        // Roll quests over on app start (local-first: runs regardless of auth). Idempotent — settles
        // any stale instances and generates the current period if missing. Failures swallowed.
        appScope.launch {
            runCatching { questReconciler.reconcile() }
        }

        PriceRefreshWorker.scheduleDailyRefresh(workManager)
        CollectionStatsSyncWorker.scheduleDailySync(workManager)
        QuestRotationWorker.scheduleDaily(workManager)

        // COMMENTED OUT — Cloudflare R2 embedding DB download replaced by ML Kit OCR
        // embeddingDatabaseUpdater.scheduleUpdateCheck()

        // Schedule/cancel the periodic background sync based on auth state.
        // CollectionViewModel also does this for the collection screen, but this
        // global observer ensures sync is cancelled even when that screen is not alive.
        appScope.launch {
            authRepository.sessionState.collect { state ->
                when (state) {
                    is SessionState.Authenticated -> {
                        CollectionSyncWorker.schedulePeriodicSync(workManager)
                        // Gamification Phase 4 sync (ADR-002 §11): schedule the periodic worker AND run a
                        // one-time guest→account reconcile so an anonymous/guest's local progress merges
                        // into the account exactly once on sign-in. Monotonic merges make the reconcile
                        // idempotent, so a harmless re-run on a later session re-emission is safe.
                        GamificationSyncWorker.schedulePeriodicSync(workManager)
                        appScope.launch {
                            runCatching { gamificationSyncManager.reconcileOnSignIn(state.user.id) }
                        }
                        appScope.launch {
                            runCatching {
                                val token = FirebaseMessaging.getInstance().token.await()
                                pushTokenRepository.register(token)
                            }
                        }
                    }
                    is SessionState.Unauthenticated -> {
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_PERIODIC)
                        workManager.cancelUniqueWork(CollectionSyncWorker.WORK_NAME_ONE_TIME)
                        workManager.cancelUniqueWork(GamificationSyncWorker.WORK_NAME_PERIODIC)
                        workManager.cancelUniqueWork(GamificationSyncWorker.WORK_NAME_ONE_TIME)
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
