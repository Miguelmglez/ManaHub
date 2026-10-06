package com.mmg.manahub.feature.collection.di

import androidx.work.WorkManager
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.feature.collection.data.*
import com.mmg.manahub.feature.collection.data.RoomTransferCollectionExecutor
import com.mmg.manahub.feature.collection.data.RoomTransferWishlistExecutor
import com.mmg.manahub.feature.trades.data.WishlistMutationCoordinator
import com.mmg.manahub.feature.collection.data.RoomTransferWishlistDeliveryStore
import com.mmg.manahub.feature.collection.data.AndroidTransferWishlistDeliveryGateway
import com.mmg.manahub.core.domain.collection.transfer.TransferWishlistSync
import com.mmg.manahub.core.domain.collection.transfer.TransferWishlistDeliveryStore
import com.mmg.manahub.core.domain.collection.transfer.TransferWishlistDeliveryGateway
import com.mmg.manahub.feature.collection.data.TransferAuthSessionObserver
import com.mmg.manahub.feature.collection.data.VerifiedTransferGuestIdentity
import com.mmg.manahub.core.domain.collection.transfer.TransferSessionGate
import com.mmg.manahub.core.domain.collection.transfer.TransferWishlistExecutor
import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import kotlinx.datetime.Clock
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.dao.CollectionTransferDao
import com.mmg.manahub.core.sync.CollectionMergeConflictResolver
import com.mmg.manahub.core.sync.CollectionSyncWorker
import com.mmg.manahub.core.sync.SyncManager
import com.mmg.manahub.feature.collection.presentation.CollectionViewModel
import com.mmg.manahub.core.data.queue.PersistentCardQueueRepository
import com.mmg.manahub.core.data.queue.SharedPreferencesCardQueueStore
import com.mmg.manahub.core.domain.collection.transfer.CollectionFileGateway
import com.mmg.manahub.core.domain.collection.transfer.CollectionImportUnresolvedStore
import com.mmg.manahub.core.domain.repository.CardQueueRepository
import com.mmg.manahub.core.domain.usecase.collection.CommitImportedCardsUseCase
import com.mmg.manahub.core.domain.usecase.queue.CardQueueActions
import com.mmg.manahub.feature.collection.data.AndroidCollectionFileGateway
import com.mmg.manahub.feature.collection.data.LegacyCollectionImportQuarantine
import com.mmg.manahub.feature.collection.data.RoomCollectionTransferRepository
import com.mmg.manahub.core.domain.collection.transfer.CollectionTransferRepository
import com.mmg.manahub.feature.collection.data.SharedPreferencesImportUnresolvedStore
import com.mmg.manahub.feature.collection.data.AndroidTransferResolutionGateway
import com.mmg.manahub.feature.collection.data.TransferNetworkAvailability
import com.mmg.manahub.core.domain.collection.transfer.TransferResolutionGateway
import com.mmg.manahub.feature.collection.presentation.importexport.CollectionImportViewModel
import com.mmg.manahub.feature.trades.domain.usecase.AddAllToWishlistUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.workmanager.dsl.worker
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * KMP migration — Phase 1 Hilt→Koin cutover. The Collection feature is the seventeenth "Koin island":
 * its single [CollectionViewModel] is resolved by Koin (`koinViewModel()`) while every other un-migrated
 * feature stays on Hilt. This continues the incremental, per-feature cutover proven by Spike D (after
 * Settings, Stats, Profile, Home, TagDictionary, AddCard, CommunityDecks, CardDetail, Friends, Splash,
 * Survey, News, Draft, Playtest, Tournament and Trades).
 *
 * ## Bridge pattern (same as the earlier islands)
 * [CollectionViewModel] takes thirteen constructor arguments, all of which are singletons still owned by the
 * Hilt object graph. Rather than re-providing them in Koin — which would risk duplicate construction /
 * divergent state — `ManaHubApp` is the bridge: it `@Inject`s the already-constructed Hilt instances and
 * passes the Collection-only ones into [collectionKoinModule], which re-exposes them to Koin as
 * `single { }`. The shared ones are resolved via `get()` from the modules that already register them.
 *
 * `savedStateHandle = get()` resolves the Koin-injected [androidx.lifecycle.SavedStateHandle] that carries
 * the `tab` nav arg (if any), so the nav-arg behaviour is identical to the previous Hilt resolution.
 * All three Collection call-sites (Collection / DeckList / Trades tabs in `AppNavGraph`) use the screen's
 * default `viewModel` param, so the only call-site change is swapping that default `hiltViewModel()` →
 * `koinViewModel()` in `CollectionScreen`. No `AppNavGraph` edit.
 *
 * Six of the thirteen dependencies are SHARED with other islands and are therefore NOT registered here —
 * they are bridged exactly once in `coreBridgeKoinModule` (registering the same type in two loaded modules
 * would throw `DefinitionOverrideException`) and resolved below via `get()`:
 * - `CardRepository` — shared with Home + CommunityDecks + CardDetail.
 * - `AuthRepository` — shared with Settings + Profile + Home + CardDetail + Friends.
 * - `UserPreferencesRepository` — shared with Settings + Stats + CardDetail.
 * - `AnalyticsHelper` — shared with Settings + CardDetail + Friends + Draft.
 * - `WishlistRepository` — shared with Trades + Home + CardDetail (PROMOTED into `coreBridgeKoinModule`
 *   by the Trades island).
 * - `OpenForTradeRepository` — shared with Trades + CardDetail (PROMOTED into `coreBridgeKoinModule` by
 *   the Trades island).
 *
 * Two more dependencies are already `single`s in OTHER loaded feature modules and are resolved via `get()`
 * WITHOUT being re-registered here (a `single<T>` is resolvable from any loaded module; re-registering
 * would throw `DefinitionOverrideException`):
 * - `GetLocalWishlistUseCase` — already a `single` in `tradesKoinModule`.
 * - `UserCardRepository` — already a `single` in `cardDetailKoinModule`.
 *
 * The remaining two are Collection-only bridged singletons consumed by no other Koin island and are the
 * [Module] params here: [SyncManager] and [WorkManager] (the same Hilt-owned singleton `ManaHubApp`
 * already `@Inject`s for its own use).
 *
 * `GetCollectionUseCase` and `MigrateLocalTradeListsUseCase` (KMP migration batch 2) are now natively
 * Koin-built in `SharedDomainKoinModule` — resolved below via `get()`, not registered here anymore.
 *
 * As features migrate further in Phase 1, each `single { hiltInstance }` here is replaced by a real Koin
 * provider and the matching Hilt `@Provides`/`@Binds` is deleted — so the bridge shrinks to nothing
 * without ever leaving the app uncompilable between commits.
 *
 * ## KMP migration — Hilt→Koin cutover batch 6 (WorkManager subsystem)
 * [CollectionSyncWorker] was converted from `@HiltWorker`/`@AssistedInject` to a plain `CoroutineWorker`
 * registered here via Koin's `worker { }` DSL, co-located with the [SyncManager] bridge single it needs.
 * [SyncManager] itself KEEPS its Hilt `@Inject constructor` because `ManaHubApp` (`@AndroidEntryPoint`)
 * still Hilt-injects it as a bridge field passed into this module — so this stays a forward bridge, not
 * a native conversion. `AuthRepository` is a native Koin single in `coreBridgeKoinModule`, resolved via
 * `get()`.
 *
 * @param workManager the Hilt-owned [WorkManager] singleton (this island only; the same instance
 *   `ManaHubApp` already injects for global sync scheduling).
 * @return a Koin [Module] that provides the Collection-only bridged singletons and the
 *   [CollectionViewModel] factory.
 */
fun collectionKoinModule(
    workManager: WorkManager,
    transferDatabase: MtgDatabase,
): Module = module {
    single<MtgDatabase> { transferDatabase }
    single<CollectionTransferDao> { get<MtgDatabase>().collectionTransferDao() }
    single { TransferSessionGate() }
    single { VerifiedTransferGuestIdentity(androidContext()) }
    single { TransferAuthSessionObserver(get<AuthRepository>().sessionState,get(),get(),get()) }
    single<CollectionTransferRepository> { RoomCollectionTransferRepository(get(),get(),get<TransferAuthSessionObserver>()::matchesObserved,scheduler=get()) }
    single { RoomCollectionSelectionRepository(get(),get(),get()) }
    single<CollectionSelectionRepository> { get<RoomCollectionSelectionRepository>() }
    single<CollectionOwnershipRepository> { RoomCollectionOwnershipRepository(get(),get(),get()) }
    single<CollectionExportRepository> { RoomCollectionExportRepository(androidContext(),get(),get(),get(),get(),get(),get()) }
    viewModel { com.mmg.manahub.feature.collection.presentation.importexport.DurableExportViewModel(get(),get(),get(),get(),get()) }
    factory { com.mmg.manahub.feature.collection.presentation.CollectionSelectionRuntime(get(),get(),get(),get()) }
    viewModel { com.mmg.manahub.feature.collection.presentation.importexport.TransferIntakeViewModel(get(),androidContext().contentResolver,get(),get(),get(),get(),get()) }
    single { com.mmg.manahub.feature.collection.data.AndroidTransferReportWriter(get(),get(),get(),androidContext().contentResolver,androidContext().filesDir) }
    viewModel { parameters -> com.mmg.manahub.feature.collection.presentation.importexport.DurableTransferViewModel(parameters.get(),get(),get(),get(),get(),get(),get(),get(),androidContext().contentResolver,get(),get()) }
    single { RoomTransferCollectionExecutor(get(),get(),System::currentTimeMillis,{
        get<ProgressionEventBus>().emit(ProgressionEvent.CollectionChanged(Clock.System.now()))
        if((get<TransferSessionGate>().currentSession as? com.mmg.manahub.core.domain.collection.transfer.TransferSession.Available)?.owner is com.mmg.manahub.core.domain.collection.transfer.TransferOwner.Account)
            CollectionSyncWorker.enqueueTransferSync(get())
    },matchesObservedOwner=get<TransferAuthSessionObserver>()::matchesObserved) }
    single<TransferWishlistExecutor> { RoomTransferWishlistExecutor(get(),get(),System::currentTimeMillis,get<TransferAuthSessionObserver>()::matchesObserved) }
    single<TransferWishlistDeliveryStore> { RoomTransferWishlistDeliveryStore(get(),get(),get<TransferAuthSessionObserver>()::matchesObserved) }
    single<TransferWishlistDeliveryGateway> { AndroidTransferWishlistDeliveryGateway(get(),get<TransferAuthSessionObserver>()::matchesObserved) }
    single { TransferWishlistSync(get(),get(),get(),get<TransferAuthSessionObserver>()::matchesObserved,get()) }
    single { WishlistMutationCoordinator(get(),get(),get<AuthRepository>().sessionState,get(),get(),get()) }
    single { TransferNetworkAvailability(androidContext()) }
    single<TransferWorkScheduler> { AndroidTransferWorkScheduler(get()) }
    single {
        val gate=get<TransferSessionGate>(); val observer=get<TransferAuthSessionObserver>()
        AndroidCollectionTransferFileStore(androidContext().filesDir,get(),{ observer.observedSession(gate) })
    }
    single<CollectionTransferFileStore> { get<AndroidCollectionTransferFileStore>() }
    single<TransferResolutionStore> {
        val gate=get<TransferSessionGate>(); val observer=get<TransferAuthSessionObserver>()
        RoomTransferResolutionStore(get(),{ observer.observedSession(gate) })
    }
    single { DurableTransferResolver(get(),get(),System::currentTimeMillis) }
    single {
        val gate=get<TransferSessionGate>(); val observer=get<TransferAuthSessionObserver>()
        RoomTransferReviewBuilder(get(),{ observer.observedSession(gate) },System::currentTimeMillis)
    }
    single { RoomCollectionTransferCoordinator(get(),get(),get<TransferAuthSessionObserver>()::matchesObserved,get(),get(),get(),get(),get(),get(),get()) }
    single<CollectionTransferCoordinator> { get<RoomCollectionTransferCoordinator>() }
    single { RunTransferWork(get()) }
    worker { CollectionTransferWorker(androidContext(),it.get(),{ get() }) }
    worker { TransferWishlistDeliveryWorker(androidContext(),it.get(),{ get() }) }
    single<TransferResolutionGateway> { AndroidTransferResolutionGateway(get<MtgDatabase>().cardDao(),get(),get(),get<TransferNetworkAvailability>()::isOnline) }
    // ── Hilt → Koin bridge: re-expose the Collection-only Hilt-owned singletons to Koin. ──
    // (CardRepository, AuthRepository, UserPreferencesRepository, AnalyticsHelper, WishlistRepository and
    //  OpenForTradeRepository are shared → bridged in coreBridgeKoinModule; GetLocalWishlistUseCase and
    //  UserCardRepository are already singles in tradesKoinModule / cardDetailKoinModule; GetCollectionUseCase
    //  and MigrateLocalTradeListsUseCase are now singles in SharedDomainKoinModule. All resolved below via
    //  get(), never re-registered here — a second single<T> for the same type across two loaded modules
    //  would throw DefinitionOverrideException.
    //  [SyncManager] was ALSO a Collection-only single here until the backend-performance-optimization-
    //  plan WS1+WS3 promoted it to `coreBridgeKoinModule` (Home now needs its `syncState` too) — it now
    //  resolves cross-module via get(), same as every other promoted dep in this comment block.)
    single { workManager }

    // ── KMP migration — Hilt→Koin cutover batch 6: WorkManager subsystem. ──
    worker {
        CollectionSyncWorker(
            appContext = androidContext(),
            workerParams = it.get(),
            syncManager = get(),
            authRepository = get(),
            progressionEventBus = get(),
        )
    }

    // ── The Koin island: CollectionViewModel is now resolved by Koin, not Hilt. ──
    viewModel {
        CollectionViewModel(
            savedStateHandle = get(),
            getCollection = get(),
            cardRepository = get(),
            userCardRepository = get(),
            authRepository = get(),
            syncManager = get(),
            workManager = get(),
            migrateLocalTradeLists = get(),
            getLocalWishlist = get(),
            wishlistRepository = get(),
            openForTradeRepository = get(),
            userPreferencesRepository = get(),
            analyticsHelper = get(),
            collectionMergeConflictResolver = get(),
            fileGateway = get(),
            crashReporter = get(),
            selectionRuntime = get(),
        )
    }

    // ── Collection import / export. ──
    single { LegacyCollectionImportQuarantine(androidContext()) }
    single<CollectionFileGateway> { AndroidCollectionFileGateway(androidContext(), get(named("io"))) }
    // Private review queue: must stay a single instance over its store, and never the shared queue.
    // Its own preference file + off-main persistence: this queue holds thousands of rows, so both
    // the re-encode and the SharedPreferences write have to stay off the caller's thread.
    single<CardQueueRepository>(named(COLLECTION_IMPORT_QUEUE)) {
        PersistentCardQueueRepository(
            store = get<LegacyCollectionImportQuarantine>().queueStore(),
            crashReporter = get(),
            persistenceScope = get(),
        )
    }
    single(named(COLLECTION_IMPORT_QUEUE)) {
        CardQueueActions(
            queueRepository = get(named(COLLECTION_IMPORT_QUEUE)),
            committer = get<CommitImportedCardsUseCase>(),
            addToWishlist = get(),
            wishlistBatchWriter = get<AddAllToWishlistUseCase>(),
        )
    }
    single<CollectionImportUnresolvedStore> { get<LegacyCollectionImportQuarantine>().unresolvedStore() }
    viewModel {
        CollectionImportViewModel(
            resolveImport = get(),
            fileGateway = get(),
            queueRepository = get(named(COLLECTION_IMPORT_QUEUE)),
            queueActions = get(named(COLLECTION_IMPORT_QUEUE)),
            cardRepository = get(),
            userCardRepository = get(),
            userPreferencesRepository = get(),
            unresolvedStore = get(),
            crashReporter = get(),
            appScope = get(),
            ioDispatcher = get(named("io")),
        )
    }
}

/** Koin qualifier of the Collection import review queue and its actions. */
const val COLLECTION_IMPORT_QUEUE = "collectionImportQueue"


