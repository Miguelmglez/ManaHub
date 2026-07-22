package com.mmg.manahub.core.di

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CardStrategyTagsCache
import com.mmg.manahub.core.data.cache.CardStrategyTagsCacheImpl
import com.mmg.manahub.core.data.local.dao.CardStrategyTagsCacheDao
import com.mmg.manahub.core.data.network.ScryfallCache
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.core.data.remote.CardStrategyTagsRemoteDataSource
import com.mmg.manahub.core.data.remote.CardStrategyTagsRemoteDataSourceContract
import com.mmg.manahub.core.data.remote.ScryfallClient
import com.mmg.manahub.core.data.remote.ScryfallRemoteDataSource
import com.mmg.manahub.core.data.remote.edhrec.EdhrecCardTagEnrichmentSource
import com.mmg.manahub.core.data.remote.edhrec.EdhrecCardTagEnrichmentSourceContract
import com.mmg.manahub.core.data.repository.CardStrategyTagsRepositoryImpl
import com.mmg.manahub.core.data.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.usecase.card.ComputeCardTagsUseCase
import com.mmg.manahub.core.domain.usecase.card.ResolveCardStrategyTagsUseCase
import com.mmg.manahub.core.tagging.createStrategyAnalyzer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * KMP migration — Hilt→Koin cutover batch 2. This module used to provide ~30 pure domain use cases;
 * ALL of them (bar the six below) now live natively in [com.mmg.manahub.core.di.SharedDomainKoinModule]
 * (Koin), consumed via `get()` by the migrated Koin islands. Two of the moved ones
 * ([com.mmg.manahub.feature.trades.domain.usecase.AddToWishlistUseCase],
 * [com.mmg.manahub.core.domain.usecase.collection.CommitScannedCardsUseCase]) still have a
 * Hilt-only consumer (`ScannerViewModel`) and are re-exposed to Hilt via
 * [com.mmg.manahub.core.di.KoinToHiltBridgeModule] (`GlobalContext.get().get()`).
 * [com.mmg.manahub.core.data.usecase.collection.RefreshCollectionPricesUseCase] used to be a THIRD
 * reverse-bridged entry (its Hilt-only consumer was `core.sync.PriceRefreshWorker`, `@HiltWorker`) but
 * that worker was converted to a Koin `worker { }` registration in KMP migration batch 6 — the reverse
 * bridge entry was deleted, since no Hilt-only consumer remains.
 *
 * ## Why the remaining two providers COULD NOT move (the ordering hazard the reverse bridge can't fix)
 * `GlobalContext.get()` throws unless Koin has already been started. That is safe for
 * `KoinToHiltBridgeModule`'s two use cases because their Hilt-only consumer (`ScannerViewModel`) is built
 * LAZILY, strictly after `ManaHubApp.onCreate()` has called `startKoin()`.
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

    /**
     * Deck Engine Unification plan, D8, §5 Phase 5c. [ResolveCardStrategyTagsUseCase] is a
     * constructor param of `CardRepositoryImpl`, which is subject to the SAME eager-Hilt ordering
     * hazard documented on this module's class KDoc ([ComputeCardTagsUseCase] above) — it cannot
     * be resolved via `GlobalContext.get()`. This residual provider builds its OWN
     * [CardStrategyTagsRepositoryImpl] instance, independent of the Koin-native one
     * `CardStrategyTagsKoinModule` builds for [com.mmg.manahub.feature.carddetail.presentation
     * .CardDetailViewModel] — the two share the SAME underlying [CardStrategyTagsCacheDao]
     * singleton (Room-scoped `@Singleton` via `DatabaseModule`), so their caches stay coherent
     * even though the repository objects themselves are distinct instances (harmless: both are
     * stateless wrappers over the same DAO + a Ktor-free `SupabaseClient` call).
     */
    @Provides
    @Singleton
    fun provideCardStrategyTagsRepository(
        supabaseClient: SupabaseClient,
        cacheDao: CardStrategyTagsCacheDao,
        crashReporter: CrashReporter,
    ): CardStrategyTagsRepository {
        val remote: CardStrategyTagsRemoteDataSourceContract =
            CardStrategyTagsRemoteDataSource(supabaseClient, DispatcherProvider())
        val cache: CardStrategyTagsCache = CardStrategyTagsCacheImpl(cacheDao)
        return CardStrategyTagsRepositoryImpl(
            remote = remote,
            cache = cache,
            crashReporter = crashReporter,
            dispatcherProvider = DispatcherProvider(),
            now = { System.currentTimeMillis() },
        )
    }

    /**
     * Plan §8a addendum — dedicated, minimal Ktor [HttpClient] for [EdhrecCardTagEnrichmentSource]'s
     * on-device per-card theme-page shortlist check. Self-contained (same rationale as
     * [provideComputeCardTagsUseCase]/[provideCardStrategyTagsRepository] above): this residual Hilt
     * provider cannot reach into Koin, so it builds its own client rather than sharing
     * `CommunityAggregateKoinModule`'s. No disk cache (unlike that one) — this client's traffic is a
     * handful of EDHREC theme-page fetches per app session at most, in-memory-memoized by
     * [EdhrecCardTagEnrichmentSource] itself, so a persistent cache would add complexity for no
     * measurable benefit. User-Agent + Referer are REQUIRED headers — verified live (Deck Engine
     * Unification plan D6): EDHREC 403s a request without them, even against a real, existing page.
     */
    @Provides
    @Singleton
    fun provideEdhrecHttpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = true
        engine {
            config {
                connectTimeout(10, TimeUnit.SECONDS)
                readTimeout(20, TimeUnit.SECONDS)
                callTimeout(30, TimeUnit.SECONDS)
            }
        }
        install(DefaultRequest) {
            header("User-Agent", "ManaHub/1.0 Android (on-device EDHREC theme shortlist)")
            header("Referer", "https://edhrec.com/")
            header("Accept", "application/json")
        }
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
        }
    }

    @Provides
    @Singleton
    fun provideEdhrecCardTagEnrichmentSource(
        httpClient: HttpClient,
    ): EdhrecCardTagEnrichmentSourceContract = EdhrecCardTagEnrichmentSource(httpClient)

    @Provides
    @Singleton
    fun provideResolveCardStrategyTagsUseCase(
        computeCardTagsUseCase: ComputeCardTagsUseCase,
        cardStrategyTagsRepository: CardStrategyTagsRepository,
        edhrecCardTagEnrichmentSource: EdhrecCardTagEnrichmentSourceContract,
    ): ResolveCardStrategyTagsUseCase =
        ResolveCardStrategyTagsUseCase(computeCardTagsUseCase, cardStrategyTagsRepository, edhrecCardTagEnrichmentSource)
}
