package com.mmg.manahub.core.di

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.network.ScryfallCache
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.ScryfallClient
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.domain.usecase.card.ComputeCardTagsUseCase
import com.mmg.manahub.core.tagging.createStrategyAnalyzer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * KMP migration — Hilt→Koin cutover batch 2. This module used to provide ~30 pure domain use cases;
 * ALL of them (bar the six below) now live natively in [com.mmg.manahub.core.di.SharedDomainKoinModule]
 * (Koin), consumed via `get()` by the migrated Koin islands. Three of the moved ones
 * ([com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase],
 * [com.mmg.manahub.core.domain.usecase.collection.CommitScannedCardsUseCase],
 * [com.mmg.manahub.core.data.usecase.collection.RefreshCollectionPricesUseCase]) still have a
 * Hilt-only consumer and are re-exposed to Hilt via [com.mmg.manahub.core.di.KoinToHiltBridgeModule]
 * (`GlobalContext.get().get()`).
 *
 * ## Why the remaining two providers COULD NOT move (the ordering hazard the reverse bridge can't fix)
 * `GlobalContext.get()` throws unless Koin has already been started. That is safe for
 * `KoinToHiltBridgeModule`'s three use cases because their Hilt-only consumers
 * (`ScannerViewModel`/`PriceRefreshWorker`) are built LAZILY, strictly after `ManaHubApp.onCreate()`
 * has called `startKoin()`.
 *
 * The two providers below feed classes with NO such luxury: [ScryfallRemoteDataSource] and
 * [ComputeCardTagsUseCase] are `@Inject` constructor params of `CardRepositoryImpl` /
 * `core.sync.SyncManager` (both `@Singleton`, still-Hilt — "repos stay Hilt-bridged"). Both impls are
 * eagerly built the moment `ManaHubApp`'s own `@Inject lateinit var` fields (`cardRepository`, etc.)
 * are populated — which Hilt does BEFORE `ManaHubApp.onCreate()`'s body runs, i.e. before
 * `startKoin()`. A `@Provides` that called into Koin here would crash on cold start with
 * "KoinApplication has not been started". [ScryfallCache] only exists to build
 * [ScryfallRemoteDataSource] and has no other consumer, so it stays alongside it.
 *
 * ## KMP migration — Hilt→Koin cutover batch 3
 * `GetDraftableSetsUseCase`/`GetSetTierListUseCase`/`GetSetCardsPageUseCase` providers were DELETED
 * this batch: `DraftSimRepositoryImpl` (their only Hilt-only consumer) is now natively Koin-built
 * (`coreBridgeKoinModule`) and resolves the same three use cases via `get()` from
 * `SharedDomainKoinModule` instead. `DraftRepository` therefore has NO remaining Hilt consumer and its
 * import was removed.
 *
 * ## Why this isn't a real behaviour split
 * Both remaining providers are pure/stateless wrappers EXCEPT [ScryfallRemoteDataSource], which
 * genuinely must stay a single shared instance (it owns the ONE app-wide [ScryfallRequestQueue] rate
 * limiter — CLAUDE.md: "All Scryfall calls must be wrapped in `ScryfallRequestQueue.execute{}`"). That
 * singleton's provisioning is UNCHANGED: still built here, still forward-bridged into
 * `coreBridgeKoinModule` via the pre-existing `ManaHubApp.scryfallRemoteDataSource` field — every
 * consumer (Hilt or Koin) shares the exact same instance, so there is no divergence risk.
 * [ComputeCardTagsUseCase] has no Koin consumer at all, so it stays 100% Hilt-only, self-contained
 * (inlines its own [SuggestTagsUseCase]/`StrategyAnalyzer` rather than sharing Koin's instance — again
 * harmless, both are pure functions with no shared state).
 */
@Module
@InstallIn(SingletonComponent::class)
object SharedDomainUseCaseModule {

    @Provides
    @Singleton
    fun provideScryfallCache(): ScryfallCache = ScryfallCache()

    @Provides
    @Singleton
    fun provideScryfallRemoteDataSource(
        api: ScryfallClient,
        requestQueue: ScryfallRequestQueue,
        cache: ScryfallCache,
    ): ScryfallRemoteDataSource = ScryfallRemoteDataSource(api, requestQueue, cache, DispatcherProvider())

    /**
     * Self-contained: builds its own [SuggestTagsUseCase]/`StrategyAnalyzer` instance rather than
     * sharing [com.mmg.manahub.core.di.SharedDomainKoinModule]'s Koin-native one. Both are pure/
     * stateless (no I/O, no shared mutable state), so this is behaviourally identical to sharing an
     * instance — and keeps this Hilt-only provider from ever needing to reach into Koin.
     */
    @Provides
    @Singleton
    fun provideComputeCardTagsUseCase(): ComputeCardTagsUseCase =
        ComputeCardTagsUseCase(SuggestTagsUseCase(createStrategyAnalyzer()))
}
