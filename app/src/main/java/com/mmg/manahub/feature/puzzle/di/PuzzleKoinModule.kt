package com.mmg.manahub.feature.puzzle.di

import android.content.Context
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.local.dao.PuzzleDao
import com.mmg.manahub.core.data.remote.PuzzleApi
import com.mmg.manahub.core.data.remote.PuzzleApiContract
import com.mmg.manahub.core.data.repository.PuzzleRepositoryImpl
import com.mmg.manahub.core.domain.repository.PuzzleRepository
import com.mmg.manahub.feature.puzzle.domain.usecase.GetTodayPuzzleUseCase
import com.mmg.manahub.feature.puzzle.domain.usecase.SavePuzzleResultUseCase
import com.mmg.manahub.feature.puzzle.domain.usecase.SubmitPuzzleGuessUseCase
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

/** Hard cap on a single `manahub-draft-api` puzzle response body (1 MB) to protect against OOM. */
private const val MAX_PUZZLE_RESPONSE_BYTES = 1L * 1024 * 1024

/**
 * Koin module for the Daily Puzzle feature's FOUNDATION layer (Batch B1): data layer (remote +
 * local + repository) and use cases only. Presentation (ViewModel) and the rest of the
 * gamification engine wiring are separate follow-up batches (B2/B3) — this module intentionally
 * registers no `viewModel { }`.
 *
 * ## Room-owned dependency, bridged from Hilt
 * [PuzzleDao] comes from the Hilt/Room graph (`DatabaseModule`) exactly like
 * [com.mmg.manahub.core.data.local.dao.CardStrategyTagsCacheDao] is bridged into
 * `cardStrategyTagsKoinModule` — passed in as [puzzleDao] and registered as a Koin `single` here.
 *
 * ## Reused cross-module singletons (resolved via `get()`)
 * [com.mmg.manahub.core.gamification.domain.ProgressionEventBus] and
 * [com.mmg.manahub.core.domain.repository.CardRepository] are already registered as Koin singles in
 * `coreBridgeKoinModule` (loaded in the same `modules(...)` call in `ManaHubApp`) — they are NOT
 * re-declared here to avoid a `DefinitionOverrideException`.
 *
 * ## Same Worker as Draft content, different path
 * The puzzle endpoint (`GET puzzle/today`) is served by the SAME `manahub-draft-api` Cloudflare
 * Worker as [com.mmg.manahub.core.data.remote.CloudflareContentClient]'s draft content, so
 * [BuildConfig.CLOUDFLARE_WORKER_URL] is reused as the base URL. A dedicated Ktor `HttpClient` is
 * built here (not shared with `draftKoinModule`'s) so this module has no load-order dependency on
 * the Draft feature's module being loaded first — mirrors `communityAggregateKoinModule`'s
 * dedicated-client rationale.
 *
 * @param puzzleDao the Hilt/Room-owned [PuzzleDao] singleton (this module only).
 * @return a Koin [Module] providing the Daily Puzzle data layer + use cases.
 */
fun puzzleKoinModule(
    puzzleDao: PuzzleDao,
): Module = module {

    // ── Hilt → Koin bridge: the Room-owned puzzle DAO, used only by this module. ──
    single { puzzleDao }

    // ── Dedicated Ktor HttpClient for the manahub-draft-api Worker's puzzle endpoint. ──
    single<PuzzleApiContract> {
        PuzzleApi(
            httpClient = providePuzzleHttpClient(androidContext()),
            baseUrl = BuildConfig.CLOUDFLARE_WORKER_URL,
        )
    }

    // ── Data layer: remote fetch + Room-backed local store + XP emission on solve. ──
    single<PuzzleRepository> {
        PuzzleRepositoryImpl(
            remote = get(),
            puzzleDao = get(),
            progressionEventBus = get(),
        )
    }

    // ── Use cases. ──
    single { GetTodayPuzzleUseCase(puzzleRepository = get()) }
    single { SubmitPuzzleGuessUseCase(cardRepository = get()) }
    single { SavePuzzleResultUseCase(puzzleRepository = get()) }
}

/**
 * Dedicated Ktor [HttpClient] for the `manahub-draft-api` Worker's puzzle endpoint: User-Agent
 * header, HTTP logging (BODY in debug, NONE in release), a 5 MB disk cache, a 1 MB response-size
 * guard and a 15 s read timeout — the same recipe as `provideCommunityHttpClient()` in
 * `communityAggregateKoinModule`, sized down for the much smaller daily-puzzle payload.
 *
 * @param context the application [Context] (for the dedicated disk cache directory).
 */
private fun providePuzzleHttpClient(context: Context): HttpClient = HttpClient(OkHttp) {
    expectSuccess = true
    engine {
        config {
            connectTimeout(10, TimeUnit.SECONDS)
            readTimeout(15, TimeUnit.SECONDS)
            callTimeout(30, TimeUnit.SECONDS)
            addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "ManaHub/1.0 Android (daily puzzle)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            })
            cache(Cache(File(context.cacheDir, "http_cache_puzzle"), 5L * 1024 * 1024))
            addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val contentLength = response.header("Content-Length")?.toLongOrNull()
                if (contentLength != null && contentLength > MAX_PUZZLE_RESPONSE_BYTES) {
                    response.close()
                    throw IOException("Puzzle Worker response too large: ${contentLength / 1024} KB")
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
