package com.mmg.manahub.core.network

import android.content.Context
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.remote.ScryfallClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Lenient [Json] instance shared by the Scryfall Ktor [HttpClient].
     * [ignoreUnknownKeys] allows the Scryfall API to add new fields without breaking parsing.
     * [coerceInputValues] handles unexpected null/type mismatches gracefully.
     */
    private val scryfallJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "ManaHub/1.0 Android")
                    .header("Accept", "application/json;q=0.9,*/*;q=0.8")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.NONE
            })
            .cache(Cache(File(context.cacheDir, "http_cache"), 50L * 1024 * 1024))
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val path = request.url.encodedPath

                // Apply different Cache-Control max-age per endpoint so the
                // OkHttp disk cache serves as a cold-start fallback behind
                // the in-memory ScryfallCache.
                val maxAge = when {
                    path.contains("/symbology")   -> 604_800  // 7 days — symbols rarely change
                    path.contains("/sets")         -> 86_400   // 24 h  — new sets are infrequent
                    path.contains("/cards/search") -> 300      // 5 min — searches should be fresh
                    path.contains("/cards/")       -> 86_400   // 24 h  — card data (prices daily)
                    else                           -> 3_600    // 1 h   — safe default
                }

                chain.proceed(request).newBuilder()
                    .header("Cache-Control", "public, max-age=$maxAge")
                    .removeHeader("Pragma")
                    .build()
            }
            .build()

    /**
     * Dedicated Ktor [HttpClient] for the Scryfall API, backed by the OkHttp engine
     * with the global [OkHttpClient] (User-Agent, logging, 50 MB disk cache, per-endpoint
     * Cache-Control network interceptor).
     *
     * `expectSuccess = true` makes Ktor throw [io.ktor.client.plugins.ResponseException]
     * on non-2xx responses so [ScryfallRequestQueue] can inspect the status code for retry.
     */
    @Provides @Singleton
    @Named("scryfall")
    fun provideScryfallHttpClient(client: OkHttpClient): HttpClient =
        HttpClient(OkHttp) {
            engine { preconfigured = client }
            install(ContentNegotiation) {
                json(scryfallJson)
            }
            expectSuccess = true
        }

    @Provides @Singleton
    fun provideScryfallClient(@Named("scryfall") httpClient: HttpClient): ScryfallClient =
        ScryfallClient(httpClient, "https://api.scryfall.com/")
}
