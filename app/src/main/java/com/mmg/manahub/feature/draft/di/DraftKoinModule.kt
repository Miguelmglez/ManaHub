package com.mmg.manahub.feature.draft.di
// COMMENTS_REVIEWED: 2026-09-22

import com.google.gson.Gson
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.local.dao.DraftSessionDao
import com.mmg.manahub.core.data.local.dao.DraftSetDao
import com.mmg.manahub.core.data.remote.CloudflareContentClient
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

private const val MAX_RESPONSE_BYTES = 5L * 1024 * 1024

// DraftRepository/DraftSimRepository live in coreBridgeKoinModule (shared with Home) and resolve this module's infra via get()
fun draftKoinModule(
    draftSetDao: DraftSetDao,
    draftSessionDao: DraftSessionDao,
): Module = module {
    // Room DAOs are still Hilt-owned; bridged into Koin here
    single { draftSetDao }
    single { draftSessionDao }

    single { Gson() }

    single<BoosterGenerator> { WeightedBoosterGenerator() }
    single<BotDrafter> { ArchetypeAwareBotDrafter(fallback = HeuristicBotDrafter()) }
    single<DraftEngine> { DefaultDraftEngine(boosterGenerator = get(), botDrafter = get()) }
    single<DraftDeckBuilder> { ScoringDraftDeckBuilder(deckScorer = get()) }

    // Dedicated client: the shared OkHttp stack's Scryfall Cache-Control rewrite must not apply here
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
                        if (contentLength != null && contentLength > MAX_RESPONSE_BYTES) {
                            response.close()
                            throw IOException(
                                "Cloudflare response too large: ${contentLength / 1024} KB " +
                                    "(limit ${MAX_RESPONSE_BYTES / 1024 / 1024} MB)"
                            )
                        }
                        // Chunked/unknown-length bodies: pre-buffer limit+1 bytes to detect overflow before parsing
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
            getDraftableSetsUseCase = get(),
            defaultDispatcher = Dispatchers.Default,
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
            deckBuilder = get(),
            draftSimRepository = get(),
            defaultDispatcher = Dispatchers.Default,
        )
    }
}
