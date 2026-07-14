package com.mmg.manahub.feature.draft.di

import android.content.Context
import com.google.gson.Gson
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.local.dao.DraftSessionDao
import com.mmg.manahub.core.data.local.dao.DraftSetDao
import com.mmg.manahub.core.data.remote.CloudflareContentClient
import com.mmg.manahub.core.data.remote.YouTubeClient
import com.mmg.manahub.core.domain.engine.BoosterGenerator
import com.mmg.manahub.core.domain.engine.BotDrafter
import com.mmg.manahub.core.domain.engine.DraftDeckBuilder
import com.mmg.manahub.core.domain.engine.DraftEngine
import com.mmg.manahub.feature.draft.data.engine.ArchetypeAwareBotDrafter
import com.mmg.manahub.feature.draft.data.engine.DefaultDraftEngine
import com.mmg.manahub.feature.draft.data.engine.HeuristicBotDrafter
import com.mmg.manahub.feature.draft.data.engine.ScoringDraftDeckBuilder
import com.mmg.manahub.feature.draft.data.engine.WeightedBoosterGenerator
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimViewModel
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftViewModel
import com.mmg.manahub.feature.draft.presentation.viewmodel.SetDraftDetailViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
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

private const val MAX_RESPONSE_BYTES = 5L * 1024 * 1024 // 5 MB

/**
 * KMP migration — Hilt→Koin cutover batch 3. The Draft feature is the Koin island for
 * [DraftViewModel] (set list), [SetDraftDetailViewModel] (guide/tier/videos) and [DraftSimViewModel]
 * (the setup → drafting → result simulator flow).
 *
 * ## Everything below is now natively Koin-built (the feature-private Hilt `DraftModule` was DELETED)
 * `DraftRepositoryImpl`, `DraftSimRepositoryImpl` and [ScoringDraftDeckBuilder] all had their
 * `@Inject`/`@Singleton` annotations stripped this batch — confirmed with a full-codebase consumer
 * audit that none of `DraftRepository`/`DraftSimRepository`/`DraftDeckBuilder`/`DraftEngine`/
 * `BotDrafter` had a remaining Hilt-only consumer (the excluded trio — online/scanner/voice/nearby —
 * touches none of them). `DraftRepository`/`DraftSimRepository` are built in
 * [com.mmg.manahub.app.di.coreBridgeKoinModule] (shared with Home); everything else Draft-only lives
 * here.
 *
 * ## Infra singletons declared here, resolved cross-module via `get()`
 * [Gson], [YouTubeClient], [CloudflareContentClient], [DraftSetDao] and [DraftSessionDao] have no other
 * consumer outside the two Draft repository impls (which are built in `coreBridgeKoinModule` — Koin
 * resolves `get()` across ALL loaded modules regardless of declaration site, exactly like the
 * pre-existing `TournamentDao`/`TournamentRepository` split).
 *
 * ## Dependencies resolved via `get()` from OTHER loaded modules (NOT re-registered here)
 * `ScryfallClient`/`ScryfallRequestQueue` (`SharedDomainKoinModule`), `DeckScorer` (`decksKoinModule`),
 * `GetDraftableSetsUseCase`/`GetSetGuideUseCase`/`GetSetTierListUseCase`/`GetSetVideosUseCase`/
 * `ObserveDraftUseCase`/`GetDraftableSimSetUseCase`/`StartDraftUseCase`/`MakePickUseCase`/
 * `AutoPickUseCase`/`CompleteDraftUseCase` (`SharedDomainKoinModule`), `AnalyticsHelper` +
 * `DraftRepository`/`DraftSimRepository` (`coreBridgeKoinModule`).
 *
 * @param draftSetDao the Hilt/Room-owned [DraftSetDao] singleton (Room stays androidMain).
 * @param draftSessionDao the Hilt/Room-owned [DraftSessionDao] singleton (Room stays androidMain).
 * @return a Koin [Module] exposing the Draft data/engine graph + the three Draft VM factories.
 */
fun draftKoinModule(
    draftSetDao: DraftSetDao,
    draftSessionDao: DraftSessionDao,
): Module = module {
    // ── Hilt → Koin bridge: the Room-owned DAOs (Room stays androidMain / Hilt/DatabaseModule). ──
    single { draftSetDao }
    single { draftSessionDao }

    // ── Draft-only infra (natively Koin-built; DraftModule's Hilt sibling was DELETED). ──
    single { Gson() }

    single<BoosterGenerator> { WeightedBoosterGenerator() }
    single<BotDrafter> { ArchetypeAwareBotDrafter(fallback = HeuristicBotDrafter()) }
    single<DraftEngine> { DefaultDraftEngine(boosterGenerator = get(), botDrafter = get()) }
    single<DraftDeckBuilder> { ScoringDraftDeckBuilder(deckScorer = get()) }

    /**
     * Dedicated Ktor [HttpClient] for the YouTube Data API v3. Built from scratch (no shared OkHttp
     * interceptor stack) — same isolation the Hilt provider had.
     */
    single {
        val youtubeHttpClient = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        YouTubeClient(
            httpClient = youtubeHttpClient,
            baseUrl = "https://www.googleapis.com/youtube/v3/",
            apiKey = BuildConfig.YOUTUBE_API_KEY,
        )
    }

    /**
     * Dedicated Ktor [HttpClient] for the Cloudflare Worker: User-Agent header, HTTP logging
     * (BODY in debug, NONE in release), a 10 MB disk cache, a 5 MB response-size guard and a 30 s
     * read timeout — ported verbatim from the deleted Hilt `DraftModule`.
     */
    single<CloudflareContentClient> {
        val context = androidContext()
        val cloudflareHttpClient = HttpClient(OkHttp) {
            engine {
                config {
                    readTimeout(30, TimeUnit.SECONDS)
                    addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", "ManaHub/1.0 Android")
                            .build()
                        chain.proceed(request)
                    }
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                                else HttpLoggingInterceptor.Level.NONE
                    })
                    cache(
                        Cache(
                            File(context.cacheDir, "http_cache_cloudflare"),
                            10L * 1024 * 1024,
                        ),
                    )
                    addNetworkInterceptor { chain ->
                        val response = chain.proceed(chain.request())
                        val contentLength = response.header("Content-Length")?.toLongOrNull()
                        // Guard 1: reject early if Content-Length already exceeds limit
                        if (contentLength != null && contentLength > MAX_RESPONSE_BYTES) {
                            response.close()
                            throw IOException(
                                "Cloudflare response too large: ${contentLength / 1024} KB " +
                                    "(limit ${MAX_RESPONSE_BYTES / 1024 / 1024} MB)"
                            )
                        }
                        // Guard 2: for chunked/unknown-length responses, pre-buffer up to
                        // limit+1 bytes so we can detect overflow before the body is parsed.
                        val body = response.body
                        val source = body.source()
                        source.request(MAX_RESPONSE_BYTES + 1)
                        if (source.buffer.size > MAX_RESPONSE_BYTES) {
                            body.close()
                            throw IOException(
                                "Cloudflare response exceeds " +
                                    "${MAX_RESPONSE_BYTES / 1024 / 1024} MB limit"
                            )
                        }
                        response
                    }
                }
            }
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        CloudflareContentClient(cloudflareHttpClient, BuildConfig.CLOUDFLARE_WORKER_URL)
    }

    // ── The Koin island: all three Draft ViewModels. ──
    viewModel {
        DraftViewModel(
            getDraftableSetsUseCase = get(),
        )
    }

    viewModel {
        SetDraftDetailViewModel(
            savedStateHandle = get(),
            getSetGuideUseCase = get(),
            getSetTierListUseCase = get(),
            getSetVideosUseCase = get(),
            getDraftableSetsUseCase = get(),
        )
    }

    viewModel {
        DraftSimViewModel(
            savedStateHandle = get(),
            startDraft = get(),
            makePick = get(),
            autoPick = get(),
            observeDraft = get(),
            completeDraft = get(),
            getDraftableSimSet = get(),
            analytics = get(),
            botDrafter = get(),
            draftSimRepository = get(),
            defaultDispatcher = Dispatchers.Default,
        )
    }
}
