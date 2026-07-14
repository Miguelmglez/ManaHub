package com.mmg.manahub.feature.decks.di

import android.content.Context
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.cache.CommunityAggregateCache
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.CommunityAggregateDao
import com.mmg.manahub.core.data.remote.ArchidektClient
import com.mmg.manahub.core.data.remote.ArchidektDirectFallbackSource
import com.mmg.manahub.core.data.remote.CommunityAggregateApi
import com.mmg.manahub.core.data.remote.CommunityAggregateApiContract
import com.mmg.manahub.core.data.remote.SixtyFallbackFetcher
import com.mmg.manahub.core.data.network.ArchidektRequestQueue
import com.mmg.manahub.core.data.repository.CommunityAggregateRepositoryImpl
import com.mmg.manahub.core.domain.repository.CommunityAggregateRepository
import com.mmg.manahub.feature.decks.data.CommunityAggregateCacheImpl
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Hard cap on a single `manahub-community` Worker response body (5 MB) to protect against OOM. */
private const val MAX_COMMUNITY_RESPONSE_BYTES = 5L * 1024 * 1024

/**
 * Koin module for the `manahub-community` Worker data layer (Deck Doctor Community/Archetype
 * plan, Phase 3.3 — `docs/adr/ADR-004-community-api-contracts.md`).
 *
 * This is additive infra: nothing outside this module resolves [CommunityAggregateRepository]
 * yet (Motor B's consumer UI lands in a later phase), so wiring it costs zero risk to any live
 * feature.
 *
 * ## Room-owned dependency, bridged from Hilt
 * [CommunityAggregateDao] comes from the Hilt/Room graph (`DatabaseModule`) exactly like
 * [com.mmg.manahub.core.data.local.dao.CommunityDeckCacheDao] is bridged into
 * `communityDecksKoinModule` — passed in as [cacheDao] and registered as a Koin `single` here.
 *
 * ## Reused cross-module singletons (resolved via `get()`)
 * [ArchidektClient] and [ArchidektRequestQueue] are already registered as Koin singles in
 * `communityDecksKoinModule` (loaded in the same `modules(...)` call in `ManaHubApp`) — they are
 * NOT re-declared here to avoid a `DefinitionOverrideException`. [CrashReporter]/
 * [com.mmg.manahub.core.common.DispatcherProvider] come from `coreBridgeKoinModule`.
 *
 * @param cacheDao the Hilt/Room-owned [CommunityAggregateDao] singleton (this module only).
 * @return a Koin [Module] providing the community aggregate data layer.
 */
fun communityAggregateKoinModule(
    cacheDao: CommunityAggregateDao,
): Module = module {

    // ── Hilt → Koin bridge: the Room-owned cache DAO, used only by this module. ──
    single { cacheDao }

    // ── Cache abstraction (Room-backed on Android). ──
    single<CommunityAggregateCache> { CommunityAggregateCacheImpl(cacheDao = get()) }

    // ── Dedicated Ktor HttpClient for the `manahub-community` Worker. Built from scratch (not
    // the app-wide OkHttpClient) so it keeps its own cache/User-Agent/response-size guard,
    // mirroring `provideArchidektClient()` in `communityDecksKoinModule`. ──
    single<CommunityAggregateApiContract> {
        CommunityAggregateApi(
            httpClient = provideCommunityHttpClient(androidContext()),
            baseUrl = BuildConfig.COMMUNITY_WORKER_URL,
        )
    }

    // ── The reduced-sample direct-Archidekt fallback (60-card path only, Phase 3.3). Reuses the
    // ArchidektClient + ArchidektRequestQueue already registered in communityDecksKoinModule. ──
    single<SixtyFallbackFetcher> {
        ArchidektDirectFallbackSource(
            archidektClient = get(),
            archidektRequestQueue = get(),
            crashReporter = get(),
            now = { System.currentTimeMillis() },
        )
    }

    // ── Data layer: cache -> Worker -> reduced-sample fallback, gated by the D4 feature flag. ──
    single<CommunityAggregateRepository> {
        CommunityAggregateRepositoryImpl(
            api = get(),
            cache = get(),
            sixtyFallback = get(),
            crashReporter = get(),
            dispatcherProvider = get(),
            now = { System.currentTimeMillis() },
            isEngineEnabled = { get<UserPreferencesDataStore>().communityEngineEnabledFlow.first() },
        )
    }
}

/**
 * Builds the dedicated Ktor [HttpClient] for [CommunityAggregateApi]: User-Agent header, HTTP
 * logging (BODY in debug, NONE in release), a 10 MB disk cache, a 5 MB response-size guard and a
 * 30 s read timeout — the exact same recipe as `provideArchidektClient()` in
 * `communityDecksKoinModule` / the Cloudflare client in `draftKoinModule`.
 *
 * @param context the application [Context] (for the dedicated disk cache directory).
 */
private fun provideCommunityHttpClient(context: Context): HttpClient = HttpClient(OkHttp) {
    expectSuccess = true
    engine {
        config {
            connectTimeout(10, TimeUnit.SECONDS)
            readTimeout(30, TimeUnit.SECONDS)
            callTimeout(45, TimeUnit.SECONDS)
            addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "ManaHub/1.0 Android (deck doctor community engine)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            })
            cache(Cache(File(context.cacheDir, "http_cache_community"), 10L * 1024 * 1024))
            addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val contentLength = response.header("Content-Length")?.toLongOrNull()
                if (contentLength != null && contentLength > MAX_COMMUNITY_RESPONSE_BYTES) {
                    response.close()
                    throw IOException("Community Worker response too large: ${contentLength / 1024} KB")
                }
                response
            }
        }
    }
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        })
    }
}
