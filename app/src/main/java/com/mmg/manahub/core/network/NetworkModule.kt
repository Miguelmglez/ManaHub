package com.mmg.manahub.core.network

import android.content.Context
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.remote.ScryfallApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Lenient [Json] instance used for the Scryfall Retrofit converter.
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

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.scryfall.com/")
        .client(client)
        .addConverterFactory(
            scryfallJson.asConverterFactory("application/json".toMediaType()),
        )
        .build()

    @Provides @Singleton
    fun provideScryfallApi(retrofit: Retrofit): ScryfallApi =
        retrofit.create(ScryfallApi::class.java)
}
