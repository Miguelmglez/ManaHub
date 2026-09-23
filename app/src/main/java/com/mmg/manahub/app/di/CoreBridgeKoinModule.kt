package com.mmg.manahub.app.di
// COMMENTS_REVIEWED: 2026-09-22

import android.content.Context
import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.common.provideCrashReporter
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.queue.PersistentCardQueueRepository
import com.mmg.manahub.core.data.queue.SharedPreferencesCardQueueStore
import com.mmg.manahub.core.domain.repository.CardQueueRepository
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
import com.mmg.manahub.core.sync.CollectionMergeConflictResolver
import com.mmg.manahub.core.sync.SyncManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

// Cross-island singletons live here once: a second single<T> for the same type in a feature module throws DefinitionOverrideException
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
    syncManager: SyncManager,
    collectionMergeConflictResolver: CollectionMergeConflictResolver,
    appScope: CoroutineScope,
): Module = module {
    single<CrashReporter> { provideCrashReporter() }
    single { DispatcherProvider() }

    // Koin-built classes resolve named("io") so tests can swap in a TestDispatcher
    single<CoroutineDispatcher>(named("io")) { Dispatchers.IO }

    // Cross-cutting: owning it in a feature module once made it vanish on a cleanup and crashed launch
    single<CoroutineScope> { appScope }

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

    // Hilt-owned instances bridged into Koin
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
    single { syncManager }
    single { collectionMergeConflictResolver }

    // The one bus shared by the gamification engine graph and ManaHubApp's direct emissions
    single { ProgressionEventBus() }

    single<FriendRepository> {
        FriendRepositoryImpl(
            dao = get(),
            remote = get(),
            cardRepo = get(),
            progressionEventBus = get(),
            crashReporter = get(),
        )
    }

    single<GameSessionRepository> {
        GameSessionRepositoryImpl(
            dao = get(),
            progressionEventBus = get(),
            ioDispatcher = Dispatchers.IO,
            surveyAnswerDao = get(),
        )
    }

    single<TournamentRepository> {
        TournamentRepositoryImpl(
            dao = get(),
            progressionEventBus = get(),
            generateNextRound = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }

    single<DeckRepository> {
        DeckRepositoryImpl(
            deckDao = get(),
            progressionEventBus = get(),
            ioDispatcher = Dispatchers.IO,
        )
    }

    single<StatsRepository> {
        StatsRepositoryImpl(
            statsDao = get(),
            deckDao = get(),
            authRepository = get(),
            dispatcherProvider = get(),
        )
    }

    single<DraftRepository> {
        DraftRepositoryImpl(
            context = androidContext(),
            scryfallApi = get(),
            scryfallQueue = get(),
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
            cardRepository = get(),
            gson = get(),
            ioDispatcher = Dispatchers.IO,
            crashReporter = get(),
        )
    }

    single<TradesRepository> {
        TradesRepositoryImpl(
            remote = get(),
            cardDao = get(),
            cardRepository = get(),
            progressionEventBus = get(),
        )
    }
    single<WishlistRepository> {
        val authRepository = get<AuthRepository>()
        WishlistRepositoryImpl(
            dao = get(),
            remote = get(),
            currentUserId = { authRepository.getCurrentUser()?.id },
        )
    }
    single<OpenForTradeRepository> {
        val authRepository = get<AuthRepository>()
        OpenForTradeRepositoryImpl(
            dao = get(),
            remote = get(),
            currentUserId = { authRepository.getCurrentUser()?.id },
        )
    }

    // Shared Scanner + AddCard queue: exactly one instance app-wide (also bridged to Hilt).
    single<CardQueueRepository> {
        PersistentCardQueueRepository(
            store = SharedPreferencesCardQueueStore(androidContext()),
            crashReporter = get(),
        )
    }
}
