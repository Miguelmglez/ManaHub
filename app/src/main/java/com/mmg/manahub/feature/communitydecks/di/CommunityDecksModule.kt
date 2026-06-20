package com.mmg.manahub.feature.communitydecks.di

import android.content.Context
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.feature.communitydecks.data.CommunityDecksRepositoryImpl
import com.mmg.manahub.feature.communitydecks.data.remote.ArchidektApi
import com.mmg.manahub.feature.communitydecks.domain.repository.CommunityDecksRepository
import dagger.Binds
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
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module for the Community Decks (Archidekt) feature.
 *
 * The Archidekt OkHttpClient is built FROM SCRATCH (not `globalClient.newBuilder()`) — mirroring
 * the Cloudflare client in `DraftModule` — so it does not inherit the app-wide network interceptor
 * (which forces an aggressive `Cache-Control` that would conflict with this dedicated HTTP cache),
 * and so the User-Agent / timeouts / response-size guard are scoped to Archidekt only.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CommunityDecksModule {

    @Binds
    @Singleton
    abstract fun bindRepository(impl: CommunityDecksRepositoryImpl): CommunityDecksRepository

    companion object {

        /** Hard cap on a single Archidekt response body (5 MB) to protect against OOM. */
        private const val MAX_RESPONSE_BYTES = 5L * 1024 * 1024

        @Provides
        @Singleton
        @Named("archidekt")
        fun provideArchidektRetrofit(@ApplicationContext context: Context): Retrofit {
            val json = Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "ManaHub/1.0 Android (deck browser)")
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(request)
                }
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                            else HttpLoggingInterceptor.Level.NONE
                })
                .cache(Cache(File(context.cacheDir, "http_cache_archidekt"), 10L * 1024 * 1024))
                .addNetworkInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    val contentLength = response.header("Content-Length")?.toLongOrNull()
                    if (contentLength != null && contentLength > MAX_RESPONSE_BYTES) {
                        response.close()
                        throw IOException("Archidekt response too large: ${contentLength / 1024} KB")
                    }
                    response
                }
                .build()

            return Retrofit.Builder()
                .baseUrl("https://archidekt.com/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
        }

        @Provides
        @Singleton
        fun provideArchidektApi(@Named("archidekt") retrofit: Retrofit): ArchidektApi =
            retrofit.create(ArchidektApi::class.java)
    }
}
