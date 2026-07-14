package com.mmg.manahub.core.di

import com.mmg.manahub.core.domain.auth.AuthRepository
import com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase
import com.mmg.manahub.core.domain.usecase.collection.CommitScannedCardsUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.koin.core.context.GlobalContext
import javax.inject.Singleton

/**
 * KMP migration — Hilt→Koin cutover batch 2, the REVERSE bridge (Koin→Hilt).
 *
 * `SharedDomainUseCaseModule` (the Hilt module that used to build these use cases) was mostly deleted:
 * [com.mmg.manahub.core.di.SharedDomainKoinModule] now builds them natively as Koin `single`s. This is
 * a problem for the small number of use cases that still have a **Hilt-only** consumer — a class that
 * is not (yet) migrated to Koin and therefore can only receive its dependencies via `@Inject`:
 *
 * - [AddToWishlistUseCase] → `feature.scanner.presentation.ScannerViewModel` (`@HiltViewModel`; the
 *   scanner feature is explicitly EXCLUDED from the KMP migration — CLAUDE.md).
 * - [CommitScannedCardsUseCase] → the same `ScannerViewModel`.
 * - [AuthRepository] (batch 5) → the excluded `feature.online` lobby ViewModels
 *   (`LobbyHostViewModel`/`LobbyJoinViewModel`, `@HiltViewModel` — `feature/online` is explicitly
 *   EXCLUDED from the KMP migration). The repo itself is now natively Koin-built in
 *   `app.di.coreBridgeKoinModule` (the feature-private Hilt `AuthModule` was deleted).
 *
 * ## KMP migration — Hilt→Koin cutover batch 6 (WorkManager subsystem)
 * `RefreshCollectionPricesUseCase` (→ `core.sync.PriceRefreshWorker`), `FriendRepository` (→
 * `core.sync.CollectionStatsSyncWorker`), `GamificationSyncManager` (→
 * `core.gamification.data.sync.GamificationSyncWorker`) and `QuestReconciler` (→
 * `core.gamification.data.sync.QuestRotationWorker`) were REMOVED from this reverse bridge: all 7
 * `@HiltWorker`s were converted to Koin `worker { }` registrations this batch (see
 * `core.di.SyncModule.provideWorkManager` for the resulting `DelegatingWorkerFactory`), so none of these
 * four types has a remaining Hilt-only consumer (re-audited against the excluded online/voice/scanner/
 * nearby trees). `AuthRepository` is the only entry below that STILL has a genuine Hilt-only consumer
 * (the excluded online feature) and therefore stays reverse-bridged.
 *
 * Each `@Provides` below pulls the SAME singleton instance Koin already built — `GlobalContext.get()`
 * returns the one running `KoinApplication`, and `.get<T>()` resolves the existing `single` — so Hilt
 * and Koin consumers share one instance, exactly like every other Hilt↔Koin bridge in this migration.
 *
 * ## Startup-ordering guarantee (why this is safe here but NOT for every use case)
 * `GlobalContext.get()` throws if Koin has not been started yet. `ManaHubApp.onCreate()` calls
 * `startKoin { ... }` as its very first statement, and every Hilt-only consumer above is constructed
 * lazily, strictly after that point:
 * - `ScannerViewModel` is instantiated by `hiltViewModel()` only when the user navigates to the Scanner
 *   screen — strictly after `onCreate()`.
 * - The excluded online lobby ViewModels are likewise instantiated by `hiltViewModel()` only on
 *   navigation, strictly after `onCreate()`.
 *
 * This is why these three (and ONLY these three) use cases/repositories can be safely provided via this
 * reverse bridge. One other moved type —
 * [com.mmg.manahub.core.domain.usecase.card.ComputeCardTagsUseCase] — is needed by `CardRepositoryImpl`,
 * which IS constructed eagerly (it satisfies `ManaHubApp`'s OWN `@Inject` field `cardRepository`,
 * injected by Hilt BEFORE `onCreate()`'s body — and therefore before `startKoin()` — ever runs). Routing
 * it through `GlobalContext.get()` would crash with "KoinApplication has not been started" on cold
 * start. That is why it (plus `ScryfallRemoteDataSource`/`ScryfallCache`, needed by the same eager
 * `CardRepositoryImpl` AND `SyncManager`) instead keeps a small residual `@Provides` in the shrunk
 * [com.mmg.manahub.core.di.SharedDomainUseCaseModule] — self-contained, never touching Koin. (As of KMP
 * migration batch 3, `DraftSimRepositoryImpl`/`DraftRepositoryImpl` are ALSO natively Koin-built — they
 * no longer need this eager-Hilt exemption; see `coreBridgeKoinModule`.) The remaining residual providers
 * are pure/stateless wrappers EXCEPT [com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource], which
 * genuinely must stay a single shared instance — see [com.mmg.manahub.core.di.SharedDomainUseCaseModule]
 * for why.
 */
@Module
@InstallIn(SingletonComponent::class)
object KoinToHiltBridgeModule {

    @Provides
    @Singleton
    fun provideAddToWishlistUseCase(): AddToWishlistUseCase =
        GlobalContext.get().get()

    @Provides
    @Singleton
    fun provideCommitScannedCardsUseCase(): CommitScannedCardsUseCase =
        GlobalContext.get().get()

    @Provides
    @Singleton
    fun provideAuthRepository(): AuthRepository =
        GlobalContext.get().get()
}
