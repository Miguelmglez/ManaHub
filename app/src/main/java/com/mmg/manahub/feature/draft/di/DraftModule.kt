package com.mmg.manahub.feature.draft.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.feature.draft.data.DraftRepositoryImpl
import com.mmg.manahub.feature.draft.data.DraftSimRepositoryImpl
import com.mmg.manahub.feature.draft.data.engine.ArchetypeAwareBotDrafter
import com.mmg.manahub.feature.draft.data.engine.DefaultDraftEngine
import com.mmg.manahub.feature.draft.data.engine.HeuristicBotDrafter
import com.mmg.manahub.feature.draft.data.engine.ScoringDraftDeckBuilder
import com.mmg.manahub.feature.draft.data.engine.WeightedBoosterGenerator
import com.mmg.manahub.core.data.remote.CloudflareContentClient
import com.mmg.manahub.core.data.remote.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import com.mmg.manahub.feature.draft.domain.engine.BoosterGenerator
import com.mmg.manahub.feature.draft.domain.engine.BotDrafter
import com.mmg.manahub.feature.draft.domain.engine.DraftDeckBuilder
import com.mmg.manahub.feature.draft.domain.engine.DraftEngine
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DraftModule {

    @Binds
    @Singleton
    abstract fun bindDraftRepository(impl: DraftRepositoryImpl): DraftRepository

    @Binds
    @Singleton
    abstract fun bindDraftSimRepository(impl: DraftSimRepositoryImpl): DraftSimRepository

    @Binds
    @Singleton
    abstract fun bindDraftDeckBuilder(impl: ScoringDraftDeckBuilder): DraftDeckBuilder

    companion object {

        private const val MAX_RESPONSE_BYTES = 5L * 1024 * 1024 // 5 MB

        @Provides
        @Singleton
        fun provideGson(): Gson = Gson()

        // -----------------------------------------------------------------------
        // Draft Simulator engine graph
        // -----------------------------------------------------------------------

        /**
         * The archetype-aware bot drafter, composed with the heuristic drafter as its fallback for
         * sets without an engine.json. Stateless and shared across draft sessions.
         */
        @Provides
        @Singleton
        fun provideBotDrafter(): BotDrafter =
            ArchetypeAwareBotDrafter(fallback = HeuristicBotDrafter())

        /** Weighted booster generator using the default (non-seeded) RNG in production. */
        @Provides
        @Singleton
        fun provideBoosterGenerator(): BoosterGenerator = WeightedBoosterGenerator()

        /** The draft engine, wired with the booster generator and bot drafter. */
        @Provides
        @Singleton
        fun provideDraftEngine(
            boosterGenerator: BoosterGenerator,
            botDrafter: BotDrafter,
        ): DraftEngine = DefaultDraftEngine(boosterGenerator, botDrafter)

        // -----------------------------------------------------------------------
        // YouTube (Ktor — KMP migration §9.6)
        // -----------------------------------------------------------------------

        /**
         * Dedicated Ktor [HttpClient] for the YouTube Data API v3.
         *
         * Inherits connection pool and timeouts from the global [OkHttpClient] but
         * does NOT carry the global network interceptor that forces
         * `Cache-Control: max-age=86400` (same isolation as Cloudflare above).
         * The API key is passed to [YouTubeClient] as a constructor parameter
         * and appended per-request, replacing the old [OkHttp interceptor][YouTubeApiKeyInterceptor].
         */
        @Provides
        @Singleton
        @Named("youtube")
        fun provideYouTubeHttpClient(client: OkHttpClient): HttpClient {
            val youtubeOkHttp = client.newBuilder().build()
            return HttpClient(OkHttp) {
                engine {
                    preconfigured = youtubeOkHttp
                }
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
        }

        @Provides
        @Singleton
        fun provideYouTubeClient(
            @Named("youtube") httpClient: HttpClient,
        ): YouTubeClient = YouTubeClient(
            httpClient = httpClient,
            baseUrl = "https://www.googleapis.com/youtube/v3/",
            apiKey = BuildConfig.YOUTUBE_API_KEY,
        )

        // -----------------------------------------------------------------------
        // Cloudflare Worker (Ktor — KMP migration §9.6)
        // -----------------------------------------------------------------------

        /**
         * Dedicated Ktor [HttpClient] for the Cloudflare Worker, using the OkHttp
         * engine so the existing interceptor stack is preserved:
         * - User-Agent header
         * - HTTP logging (BODY in debug, NONE in release)
         * - 10 MB disk cache at `cacheDir/http_cache_cloudflare`
         * - 5 MB response-size guard (network interceptor)
         * - 30 s read timeout
         *
         * Built from scratch — must NOT inherit the global OkHttpClient whose
         * network interceptor forces Cache-Control: max-age=86400, which would
         * silently override the Worker's max-age=300 and break version-based
         * cache invalidation.
         */
        @Provides
        @Singleton
        @Named("cloudflare")
        fun provideCloudflareHttpClient(
            @ApplicationContext context: Context,
        ): HttpClient {
            return HttpClient(OkHttp) {
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
        }

        @Provides
        @Singleton
        fun provideCloudflareContentClient(
            @Named("cloudflare") httpClient: HttpClient,
        ): CloudflareContentClient =
            CloudflareContentClient(httpClient, BuildConfig.CLOUDFLARE_WORKER_URL)

        // -----------------------------------------------------------------------
        // Draft content version preferences
        // -----------------------------------------------------------------------

        /**
         * SharedPreferences used to store the last-seen content version string per set.
         * Keys follow the pattern: "pref_draft_{setCode}_guide_version" / "..._tier_version".
         * Used by [DraftRepositoryImpl] to decide whether a local cached file is stale.
         */
        @Provides
        @Singleton
        @Named("draft_prefs")
        fun provideDraftVersionPreferences(
            @ApplicationContext context: Context,
        ): SharedPreferences =
            context.getSharedPreferences("draft_content_versions", Context.MODE_PRIVATE)
    }
}
