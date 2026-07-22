package com.mmg.manahub.feature.decks.di

import android.content.Context
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.cache.ComboCache
import com.mmg.manahub.core.data.local.dao.ComboCacheDao
import com.mmg.manahub.core.data.remote.CommanderSpellbookApi
import com.mmg.manahub.core.data.remote.CommanderSpellbookApiContract
import com.mmg.manahub.core.data.repository.CommanderSpellbookRepositoryImpl
import com.mmg.manahub.core.domain.repository.CommanderSpellbookRepository
import com.mmg.manahub.feature.decks.data.ComboCacheImpl
import com.mmg.manahub.feature.decks.domain.usecase.FindCombosUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Hard cap on a single Commander Spellbook response body (5 MB) to protect against OOM. */
private const val MAX_SPELLBOOK_RESPONSE_BYTES = 5L * 1024 * 1024

/** Commander Spellbook's public API base URL — a fixed, documented public endpoint (no API key,
 * no per-environment override), so it is hardcoded here rather than routed through
 * `local.properties`/`BuildConfig`, mirroring `provideArchidektClient()`'s
 * `"https://archidekt.com/"` precedent in `communityDecksKoinModule`. */
private const val COMMANDER_SPELLBOOK_BASE_URL = "https://backend.commanderspellbook.com/"

/**
 * Koin module for the Commander Spellbook `find-my-combos` data layer (Deck Engine Unification
 * plan D7, Phase 4.3 — synergy browser Combos tab) — mirrors `communityAggregateKoinModule`'s
 * structure exactly (same Ktor-client-hygiene recipe, same Room-owned-DAO bridge shape).
 *
 * @param cacheDao the Hilt/Room-owned [ComboCacheDao] singleton (this module only).
 */
fun commanderSpellbookKoinModule(
    cacheDao: ComboCacheDao,
): Module = module {

    // ── Hilt → Koin bridge: the Room-owned cache DAO, used only by this module. ──
    single { cacheDao }

    single<ComboCache> { ComboCacheImpl(cacheDao = get()) }

    // ── Dedicated Ktor HttpClient — polite User-Agent identification (courtesy default for an
    // unofficial-adjacent third party; Spellbook documents no rate limit but this app should
    // never look indistinguishable from abusive traffic). ──
    single<CommanderSpellbookApiContract> {
        CommanderSpellbookApi(
            httpClient = provideCommanderSpellbookHttpClient(androidContext()),
            baseUrl = COMMANDER_SPELLBOOK_BASE_URL,
        )
    }

    single<CommanderSpellbookRepository> {
        CommanderSpellbookRepositoryImpl(
            api = get(),
            cache = get(),
            crashReporter = get(),
            dispatcherProvider = get(),
            now = { System.currentTimeMillis() },
        )
    }

    single { FindCombosUseCase(commanderSpellbookRepository = get()) }
}

/**
 * Builds the dedicated Ktor [HttpClient] for [CommanderSpellbookApi]: polite User-Agent, HTTP
 * logging (BODY in debug, NONE in release), a 10 MB disk cache, a 5 MB response-size guard and a
 * 30 s read timeout — the exact same recipe as `provideCommunityHttpClient()` /
 * `provideArchidektClient()`.
 */
private fun provideCommanderSpellbookHttpClient(androidContext: Context): HttpClient = HttpClient(OkHttp) {
    expectSuccess = true
    engine {
        config {
            connectTimeout(10, TimeUnit.SECONDS)
            readTimeout(30, TimeUnit.SECONDS)
            callTimeout(45, TimeUnit.SECONDS)
            addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "ManaHub/1.0 Android (deck combo finder; contact via app store listing)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            })
            cache(Cache(File(androidContext.cacheDir, "http_cache_spellbook"), 10L * 1024 * 1024))
            addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val contentLength = response.header("Content-Length")?.toLongOrNull()
                if (contentLength != null && contentLength > MAX_SPELLBOOK_RESPONSE_BYTES) {
                    response.close()
                    throw IOException("Commander Spellbook response too large: ${contentLength / 1024} KB")
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
