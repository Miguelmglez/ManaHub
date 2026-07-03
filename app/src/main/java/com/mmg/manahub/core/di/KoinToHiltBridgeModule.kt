package com.mmg.manahub.core.di

import com.mmg.manahub.core.data.usecase.collection.RefreshCollectionPricesUseCase
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
 * - [RefreshCollectionPricesUseCase] → `core.sync.PriceRefreshWorker` (`@HiltWorker`, built lazily by
 *   `HiltWorkerFactory` whenever `WorkManager` actually runs the work).
 *
 * Each `@Provides` below pulls the SAME singleton instance Koin already built — `GlobalContext.get()`
 * returns the one running `KoinApplication`, and `.get<T>()` resolves the existing `single` — so Hilt
 * and Koin consumers share one instance, exactly like every other Hilt↔Koin bridge in this migration.
 *
 * ## Startup-ordering guarantee (why this is safe here but NOT for every use case)
 * `GlobalContext.get()` throws if Koin has not been started yet. `ManaHubApp.onCreate()` calls
 * `startKoin { ... }` as its very first statement, and BOTH Hilt-only consumers above are constructed
 * lazily, strictly after that point:
 * - `PriceRefreshWorker` is instantiated by `HiltWorkerFactory` only when `WorkManager` actually runs
 *   the periodic/one-time work request — always well after `Application.onCreate()` has returned.
 * - `ScannerViewModel` is instantiated by `hiltViewModel()` only when the user navigates to the Scanner
 *   screen — again strictly after `onCreate()`.
 *
 * This is why these three (and ONLY these three) use cases can be safely provided via this reverse
 * bridge. Three other moved types — [com.mmg.manahub.core.domain.usecase.card.ComputeCardTagsUseCase],
 * `GetDraftableSetsUseCase` and `GetSetTierListUseCase` — are needed by `CardRepositoryImpl` /
 * `DraftSimRepositoryImpl`, which ARE constructed eagerly (they satisfy `ManaHubApp`'s OWN `@Inject`
 * fields, e.g. `cardRepository`/`draftSimRepository`, injected by Hilt BEFORE `onCreate()`'s body — and
 * therefore before `startKoin()` — ever runs). Routing those through `GlobalContext.get()` would crash
 * with "KoinApplication has not been started" on cold start. That is why those three (plus
 * `ScryfallRemoteDataSource`/`ScryfallCache`, needed by the same eager `CardRepositoryImpl` AND
 * `SyncManager`) instead keep a small residual `@Provides` in the shrunk
 * [com.mmg.manahub.core.di.SharedDomainUseCaseModule] — self-contained, never touching Koin. All are
 * pure/stateless wrappers (verified by reading their bodies), so a Koin-native duplicate used by the
 * migrated islands and a separately Hilt-built instance used by the still-Hilt repositories are
 * behaviourally identical; only [RefreshCollectionPricesUseCase] wraps a real singleton
 * ([com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource]) but that singleton itself is NOT
 * duplicated — see [com.mmg.manahub.core.di.SharedDomainUseCaseModule] for why.
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
    fun provideRefreshCollectionPricesUseCase(): RefreshCollectionPricesUseCase =
        GlobalContext.get().get()
}
