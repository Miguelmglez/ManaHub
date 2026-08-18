package com.mmg.manahub.feature.competitive.di

import android.content.Context
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.cache.CompetitiveLimitedRatingsCache
import com.mmg.manahub.core.data.cache.CompetitiveMetaCache
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.data.local.dao.CompetitiveLimitedRatingsCacheDao
import com.mmg.manahub.core.data.local.dao.CompetitiveMetaCacheDao
import com.mmg.manahub.core.data.network.CompetitiveRequestQueue
import com.mmg.manahub.core.data.remote.CompetitiveApi
import com.mmg.manahub.core.data.remote.CompetitiveApiContract
import com.mmg.manahub.core.data.repository.CompetitiveRepositoryImpl
import com.mmg.manahub.core.domain.repository.CompetitiveRepository
import com.mmg.manahub.feature.competitive.data.CompetitiveLimitedRatingsCacheImpl
import com.mmg.manahub.feature.competitive.data.CompetitiveMetaCacheImpl
import com.mmg.manahub.feature.competitive.presentation.CompetitiveViewModel
import com.mmg.manahub.feature.news.domain.usecase.GetProTourContentUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Hard cap on a single `manahub-competitive` Worker response body (5 MB) to protect against OOM. */
private const val MAX_COMPETITIVE_RESPONSE_BYTES = 5L * 1024 * 1024

/**
 * Koin module for the Competitive feature: a static deep-link catalog, an external event locator,
 * and a Pro Tour news filter, plus [CompetitiveViewModel].
 *
 * ## 2026-08 pivot — the data layer below is registered but DORMANT
 * Per Miguel's explicit decision, the app makes NO live/REST calls to third-party MTG data
 * services. [CompetitiveViewModel] no longer references [CompetitiveRepository] (or
 * `ImportDeckCardsUseCase`, its former "Import to Deck Studio" dependency) — the live weekly-meta
 * snapshot and LIVE 17lands-ratings sections were replaced by a compiled-in static catalog
 * (`CompetitiveResourceCatalog.kt`). Every binding below (`CompetitiveMetaCache`/
 * `CompetitiveLimitedRatingsCache`/`CompetitiveRequestQueue`/`CompetitiveApiContract`/
 * `CompetitiveRepository`) is left completely UNTOUCHED and stays registered, dormant and unused,
 * so this Worker data layer is intact for a possible future revisit — only the `viewModel {}`
 * block at the bottom was updated to match [CompetitiveViewModel]'s new (smaller) constructor.
 *
 * ## Room-owned dependencies, bridged from Hilt
 * [CompetitiveMetaCacheDao]/[CompetitiveLimitedRatingsCacheDao] come from the Hilt/Room graph
 * (`DatabaseModule`), exactly like [com.mmg.manahub.core.data.local.dao.CommunityAggregateDao] is
 * bridged into `communityAggregateKoinModule` — passed in as [metaCacheDao]/[limitedRatingsCacheDao]
 * and registered as Koin singles here.
 *
 * ## Reused cross-module singletons (resolved via `get()`)
 * [CrashReporter][com.mmg.manahub.core.common.CrashReporter]/
 * [DispatcherProvider][com.mmg.manahub.core.common.DispatcherProvider]/[UserPreferencesDataStore]
 * come from `coreBridgeKoinModule`. [com.mmg.manahub.core.domain.repository.NewsRepository] is
 * already registered in `newsKoinModule`, consumed here only by [GetProTourContentUseCase].
 *
 * @param metaCacheDao the Hilt/Room-owned [CompetitiveMetaCacheDao] singleton (this module only).
 * @param limitedRatingsCacheDao the Hilt/Room-owned [CompetitiveLimitedRatingsCacheDao] singleton
 *   (this module only).
 * @return a Koin [Module] providing the (dormant) Competitive data layer + [CompetitiveViewModel].
 */
fun competitiveKoinModule(
    metaCacheDao: CompetitiveMetaCacheDao,
    limitedRatingsCacheDao: CompetitiveLimitedRatingsCacheDao,
): Module = module {

    // ── Hilt → Koin bridge: the Room-owned cache DAOs (Room stays androidMain / Hilt/DatabaseModule). ──
    single { metaCacheDao }
    single { limitedRatingsCacheDao }

    // ── Cache abstractions (Room-backed on Android). ──
    single<CompetitiveMetaCache> { CompetitiveMetaCacheImpl(cacheDao = get()) }
    single<CompetitiveLimitedRatingsCache> { CompetitiveLimitedRatingsCacheImpl(cacheDao = get()) }

    // ── CompetitiveRequestQueue: first wiring anywhere in the app (this feature is its only caller). ──
    single { CompetitiveRequestQueue(crashReporter = get()) }

    // ── Dedicated Ktor HttpClient for the `manahub-competitive` Worker. Built from scratch (not the
    // app-wide OkHttpClient) so it keeps its own cache/User-Agent/response-size guard, mirroring
    // `provideCommunityHttpClient()` in `communityAggregateKoinModule` / `DraftKoinModule`'s Cloudflare
    // client recipe exactly. ──
    single<CompetitiveApiContract> {
        CompetitiveApi(
            httpClient = provideCompetitiveHttpClient(androidContext()),
            baseUrl = BuildConfig.COMPETITIVE_WORKER_URL,
        )
    }

    // ── Data layer: cache -> Worker, gated by the Competitive feature flag (competitiveEnabledFlow). ──
    single<CompetitiveRepository> {
        CompetitiveRepositoryImpl(
            api = get(),
            requestQueue = get(),
            metaCache = get(),
            limitedRatingsCache = get(),
            crashReporter = get(),
            dispatcherProvider = get(),
            now = { System.currentTimeMillis() },
            isEngineEnabled = { get<UserPreferencesDataStore>().competitiveEnabledFlow.first() },
        )
    }

    // ── Pro Tour news filter (pure in-memory re-slice of the already-cached News feed). ──
    single { GetProTourContentUseCase(repository = get()) }

    viewModel {
        CompetitiveViewModel(
            getProTourContent = get(),
            userPrefsDataStore = get(),
        )
    }
}

/**
 * Builds the dedicated Ktor [HttpClient] for [CompetitiveApi]: User-Agent header, HTTP logging
 * (BODY in debug, NONE in release), a 10 MB disk cache, a 5 MB response-size guard and standard
 * timeouts — the exact same recipe as `provideCommunityHttpClient()` in `communityAggregateKoinModule`
 * / the Cloudflare client in `draftKoinModule`.
 *
 * @param context the application [Context] (for the dedicated disk cache directory).
 */
private fun provideCompetitiveHttpClient(context: Context): HttpClient = HttpClient(OkHttp) {
    expectSuccess = true
    engine {
        config {
            connectTimeout(10, TimeUnit.SECONDS)
            readTimeout(30, TimeUnit.SECONDS)
            callTimeout(45, TimeUnit.SECONDS)
            addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "ManaHub/1.0 Android (competitive engine)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            })
            cache(Cache(File(context.cacheDir, "http_cache_competitive"), 10L * 1024 * 1024))
            addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val contentLength = response.header("Content-Length")?.toLongOrNull()
                if (contentLength != null && contentLength > MAX_COMPETITIVE_RESPONSE_BYTES) {
                    response.close()
                    throw IOException("Competitive Worker response too large: ${contentLength / 1024} KB")
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
