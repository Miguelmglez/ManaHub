package com.mmg.manahub.app

// import com.mmg.manahub.feature.scanner.EmbeddingDatabaseUpdater  // COMMENTED OUT — replaced by ML Kit OCR
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.messaging.FirebaseMessaging
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.app.di.coreBridgeKoinModule
import com.mmg.manahub.core.data.local.PendingInviteStore
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.CommunityDeckCacheDao
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.dao.PlaytestDao
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.data.local.dao.SurveyCardImpactDao
import com.mmg.manahub.core.data.local.dao.TradeCollectionSyncDao
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CommunityStatsRepository
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.PushTokenRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.di.ApplicationScope
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.domain.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.GetCollectionUseCase
import com.mmg.manahub.core.domain.usecase.collection.RefreshCollectionPricesUseCase
import com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase
import com.mmg.manahub.core.domain.usecase.search.BuildScryfallQueryUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionSetCodesUseCase
import com.mmg.manahub.core.domain.usecase.stats.GetCollectionStatsUseCase
import com.mmg.manahub.core.domain.usecase.symbols.SyncManaSymbolsUseCase
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
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.voice.domain.VoiceModelRepository
import com.mmg.manahub.feature.addcard.di.addCardKoinModule
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.feature.auth.di.authKoinModule
import com.mmg.manahub.feature.carddetail.di.cardDetailKoinModule
import com.mmg.manahub.feature.collection.di.collectionKoinModule
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
import com.mmg.manahub.feature.draft.domain.engine.BotDrafter
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import com.mmg.manahub.feature.draft.domain.usecase.AutoPickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.CompleteDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSetsUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSimSetUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetGuideUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetTierListUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetSetVideosUseCase
import com.mmg.manahub.feature.draft.domain.usecase.MakePickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.ObserveDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.StartDraftUseCase
import com.mmg.manahub.feature.friends.di.friendsKoinModule
import com.mmg.manahub.feature.friends.domain.repository.FriendRepository
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.feature.home.di.homeKoinModule
import com.mmg.manahub.feature.home.domain.usecase.GetAccountNudgeUseCase
import com.mmg.manahub.feature.news.di.newsKoinModule
import com.mmg.manahub.feature.news.domain.usecase.GetNewsFeedUseCase
import com.mmg.manahub.feature.news.domain.usecase.ManageSourcesUseCase
import com.mmg.manahub.feature.news.domain.usecase.RefreshNewsFeedUseCase
import com.mmg.manahub.feature.playtest.di.playtestKoinModule
import com.mmg.manahub.feature.profile.di.profileKoinModule
import com.mmg.manahub.feature.settings.di.settingsKoinModule
import com.mmg.manahub.feature.splash.di.splashKoinModule
import com.mmg.manahub.feature.stats.di.statsKoinModule
import com.mmg.manahub.feature.survey.di.surveyKoinModule
import com.mmg.manahub.feature.survey.domain.usecase.CompleteSurveyUseCase
import com.mmg.manahub.feature.tagdictionary.di.tagDictionaryKoinModule
import com.mmg.manahub.feature.tournament.di.tournamentKoinModule
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import com.mmg.manahub.feature.tournament.domain.usecase.CalculateStandingsUseCase
import com.mmg.manahub.feature.tournament.domain.usecase.RecordMatchResultUseCase
import com.mmg.manahub.feature.trades.di.tradesKoinModule
import com.mmg.manahub.feature.trades.domain.repository.OpenForTradeRepository
import com.mmg.manahub.feature.trades.domain.repository.SharedListsRepository
import com.mmg.manahub.feature.trades.domain.repository.TradesRepository
import com.mmg.manahub.feature.trades.domain.repository.WishlistRepository
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import com.mmg.manahub.feature.trades.domain.usecase.MigrateLocalTradeListsUseCase
import dagger.hilt.android.HiltAndroidApp
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

@HiltAndroidApp
class ManaHubApp : Application() {

    @Inject lateinit var syncManaSymbols: SyncManaSymbolsUseCase
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

    // Stats island (Phase 1) bridge deps.
    @Inject lateinit var getCollectionStatsUseCase: GetCollectionStatsUseCase
    @Inject lateinit var getCollectionSetCodesUseCase: GetCollectionSetCodesUseCase
    @Inject lateinit var scryfallRemoteDataSource: ScryfallRemoteDataSource
    @Inject lateinit var refreshCollectionPricesUseCase: RefreshCollectionPricesUseCase
    @Inject lateinit var gameSessionDao: GameSessionDao
    @Inject lateinit var gameSessionRepository: GameSessionRepository  // shared: Stats + Profile
    @Inject lateinit var deckRepository: DeckRepository

    // Profile island (Phase 1) bridge deps. (userPreferencesDataStore + authRepository are shared with
    // Settings and gameSessionRepository is shared with Stats — all bridged in coreBridgeKoinModule.
    // statsRepository + gamificationRepository are now also shared with Home → bridged in coreBridge.)
    @Inject lateinit var statsRepository: StatsRepository  // shared: Profile + Home
    @Inject lateinit var surveyAnswerDao: SurveyAnswerDao
    @Inject lateinit var friendRepository: FriendRepository
    @Inject lateinit var gamificationRepository: GamificationRepository  // shared: Profile + Home

    // Home island (Phase 1) bridge deps. The shared deps (userPreferencesDataStore, authRepository,
    // gameSessionRepository, statsRepository, deckRepository, scryfallRemoteDataSource,
    // gamificationRepository) are bridged in coreBridgeKoinModule; only the Home-only deps are here.
    @Inject lateinit var draftSimRepository: DraftSimRepository
    @Inject lateinit var tournamentRepository: TournamentRepository  // shared: Home + Tournament + Hilt GameViewModel (bridged in coreBridge)
    @Inject lateinit var cardRepository: CardRepository  // shared: Home + CommunityDecks (bridged in coreBridge)
    @Inject lateinit var getNewsFeedUseCase: GetNewsFeedUseCase
    @Inject lateinit var refreshNewsFeedUseCase: RefreshNewsFeedUseCase
    @Inject lateinit var manageSourcesUseCase: ManageSourcesUseCase
    @Inject lateinit var communityStatsRepository: CommunityStatsRepository
    @Inject lateinit var draftRepository: DraftRepository
    @Inject lateinit var wishlistRepository: WishlistRepository
    @Inject lateinit var getAccountNudgeUseCase: GetAccountNudgeUseCase

    // AddCard island (Phase 1) bridge deps. UserPreferencesRepository is bridged in
    // coreBridgeKoinModule (shared with Settings + Stats); only the AddCard-only use cases are here.
    @Inject lateinit var searchCardsUseCase: SearchCardsUseCase
    @Inject lateinit var buildScryfallQueryUseCase: BuildScryfallQueryUseCase

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
    @Inject lateinit var userCardRepository: UserCardRepository
    @Inject lateinit var addCardToCollectionUseCase: AddCardToCollectionUseCase
    @Inject lateinit var addToWishlistUseCase: AddToWishlistUseCase
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
    @Inject lateinit var completeSurveyUseCase: CompleteSurveyUseCase

    // Splash island (Phase 1) needs NO new bridge field — its only dep (AuthRepository) is already
    // bridged in coreBridgeKoinModule. News island (Phase 1) likewise needs NO new bridge field — its
    // ViewModels' deps (the 3 news use cases + UserPreferencesDataStore) are already bridged
    // (homeKoinModule + coreBridgeKoinModule), so both modules take no constructor args.

    // Draft island (Phase 1) bridge deps. The feature-private Hilt DraftModule is KEPT (NOT deleted):
    // the Home island bridges DraftRepository/DraftSimRepository from the Hilt graph, so the whole Draft
    // Hilt sub-graph (Cloudflare/YouTube Retrofit, engine, repos, use cases) must stay intact. The two
    // repos are now SHARED with Draft → PROMOTED into coreBridgeKoinModule (the existing draftRepository /
    // draftSimRepository fields above feed it there now); AnalyticsHelper is also bridged in coreBridge.
    // Only the Draft-only singletons (the ten use cases + the BotDrafter) are here.
    @Inject lateinit var startDraftUseCase: StartDraftUseCase
    @Inject lateinit var makePickUseCase: MakePickUseCase
    @Inject lateinit var autoPickUseCase: AutoPickUseCase
    @Inject lateinit var observeDraftUseCase: ObserveDraftUseCase
    @Inject lateinit var completeDraftUseCase: CompleteDraftUseCase
    @Inject lateinit var getDraftableSimSetUseCase: GetDraftableSimSetUseCase
    @Inject lateinit var getDraftableSetsUseCase: GetDraftableSetsUseCase
    @Inject lateinit var getSetGuideUseCase: GetSetGuideUseCase
    @Inject lateinit var getSetTierListUseCase: GetSetTierListUseCase
    @Inject lateinit var getSetVideosUseCase: GetSetVideosUseCase
    @Inject lateinit var botDrafter: BotDrafter

    // Playtest island (Phase 1) bridge dep. The feature-private Hilt PlaytestModule was converted +
    // DELETED (its @Binds PlaytestRepository is consumed by no Hilt feature) → the repo + the six use
    // cases are now Koin-owned in playtestKoinModule. Only the Room/DatabaseModule-owned PlaytestDao
    // (still Hilt-provided, this island only) is bridged here. DeckRepository is reused from
    // coreBridgeKoinModule and CardDao from surveyKoinModule via get().
    @Inject lateinit var playtestDao: PlaytestDao

    // Tournament island (Phase 1) bridge deps. TournamentRepository is shared with Home + the still-Hilt
    // GameViewModel → PROMOTED into coreBridgeKoinModule (the existing `tournamentRepository` field above
    // feeds it there now; the Hilt TournamentModule binding is KEPT for GameViewModel). Only the two
    // Tournament use cases are here:
    //  - CalculateStandingsUseCase: used only by TournamentViewModel (kept Hilt-built so its TournamentDao
    //    + IO-dispatcher deps stay in the Hilt graph — no DAO bridging needed).
    //  - RecordMatchResultUseCase: the SINGLE finish-and-advance entry point, STILL consumed by the Hilt
    //    GameViewModel → its Hilt @Inject binding is KEPT and the same singleton is bridged here.
    @Inject lateinit var calculateStandingsUseCase: CalculateStandingsUseCase
    @Inject lateinit var recordMatchResultUseCase: RecordMatchResultUseCase

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
    // Only the three Collection-only singletons are here.
    @Inject lateinit var getCollectionUseCase: GetCollectionUseCase
    @Inject lateinit var syncManager: SyncManager
    @Inject lateinit var migrateLocalTradeListsUseCase: MigrateLocalTradeListsUseCase

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
    @Inject lateinit var suggestTagsUseCase: SuggestTagsUseCase
    @Inject lateinit var evaluateDeckUseCase: EvaluateDeckUseCase
    @Inject lateinit var inferDeckIdentityUseCase: InferDeckIdentityUseCase
    @Inject lateinit var suggestCutsUseCase: SuggestCutsUseCase
    @Inject lateinit var suggestAddsWithBudgetUseCase: SuggestAddsWithBudgetUseCase
    @Inject lateinit var buildDeckFromSeedsUseCase: BuildDeckFromSeedsUseCase
    @Inject lateinit var getDeckGameStatsUseCase: GetDeckGameStatsUseCase
    @Inject lateinit var importDeckUseCase: ImportDeckUseCase
    @Inject lateinit var deckMagicEngine: DeckMagicEngine
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

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
                    tournamentRepository = tournamentRepository,
                    tradesRepository = tradesRepository,
                    wishlistRepository = wishlistRepository,
                    openForTradeRepository = openForTradeRepository,
                ),
                settingsKoinModule(
                    userProfileDataSource = userProfileDataSource,
                    pushTokenRepository = pushTokenRepository,
                    notificationPrefsRepository = notificationPrefsRepository,
                    voiceModelRepository = voiceModelRepository,
                ),
                authKoinModule(),
                statsKoinModule(
                    getCollectionStats = getCollectionStatsUseCase,
                    getCollectionSetCodes = getCollectionSetCodesUseCase,
                    refreshPricesUseCase = refreshCollectionPricesUseCase,
                    gameSessionDao = gameSessionDao,
                ),
                profileKoinModule(
                    surveyAnswerDao = surveyAnswerDao,
                ),
                homeKoinModule(
                    getNewsFeedUseCase = getNewsFeedUseCase,
                    refreshNewsFeedUseCase = refreshNewsFeedUseCase,
                    manageSourcesUseCase = manageSourcesUseCase,
                    communityStatsRepository = communityStatsRepository,
                    getAccountNudgeUseCase = getAccountNudgeUseCase,
                ),
                tagDictionaryKoinModule(
                    tagDictionaryRepository = tagDictionaryRepo,
                ),
                addCardKoinModule(
                    searchCards = searchCardsUseCase,
                    buildScryfallQuery = buildScryfallQueryUseCase,
                ),
                communityDecksKoinModule(
                    cacheDao = communityDeckCacheDao,
                ),
                cardDetailKoinModule(
                    userCardRepository = userCardRepository,
                    addToCollection = addCardToCollectionUseCase,
                    addToWishlistUseCase = addToWishlistUseCase,
                ),
                friendsKoinModule(
                    pendingInviteStore = pendingInviteStore,
                ),
                splashKoinModule(),
                surveyKoinModule(
                    surveyCardImpactDao = surveyCardImpactDao,
                    cardDao = cardDao,
                    completeSurvey = completeSurveyUseCase,
                ),
                newsKoinModule(),
                draftKoinModule(
                    startDraft = startDraftUseCase,
                    makePick = makePickUseCase,
                    autoPick = autoPickUseCase,
                    observeDraft = observeDraftUseCase,
                    completeDraft = completeDraftUseCase,
                    getDraftableSimSet = getDraftableSimSetUseCase,
                    getDraftableSets = getDraftableSetsUseCase,
                    getSetGuide = getSetGuideUseCase,
                    getSetTierList = getSetTierListUseCase,
                    getSetVideos = getSetVideosUseCase,
                    botDrafter = botDrafter,
                ),
                playtestKoinModule(
                    playtestDao = playtestDao,
                ),
                tournamentKoinModule(
                    calculateStandings = calculateStandingsUseCase,
                    recordMatchResult = recordMatchResultUseCase,
                ),
                tradesKoinModule(
                    sharedListsRepository = sharedListsRepository,
                    tradeCollectionSyncDao = tradeCollectionSyncDao,
                ),
                collectionKoinModule(
                    getCollection = getCollectionUseCase,
                    syncManager = syncManager,
                    workManager = workManager,
                    migrateLocalTradeLists = migrateLocalTradeListsUseCase,
                ),
                decksKoinModule(
                    suggestTags = suggestTagsUseCase,
                    evaluateDeck = evaluateDeckUseCase,
                    inferDeckIdentity = inferDeckIdentityUseCase,
                    suggestCuts = suggestCutsUseCase,
                    suggestAddsWithBudget = suggestAddsWithBudgetUseCase,
                    buildDeckFromSeeds = buildDeckFromSeedsUseCase,
                    getDeckGameStats = getDeckGameStatsUseCase,
                    importDeck = importDeckUseCase,
                    deckMagicEngine = deckMagicEngine,
                    applicationScope = applicationScope,
                ),
            )
        }

        createNotificationChannels()

        FirebaseCrashlytics.getInstance().apply {
            isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG
            setCustomKey("app_version_name", BuildConfig.VERSION_NAME)
        }

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(okHttpClient)
                .components { add(SvgDecoder.Factory()) }
                .build()
        )

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
                        localDate = LocalDate.now().toString(),
                        occurredAt = Instant.now(),
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
