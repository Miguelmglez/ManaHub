package com.mmg.manahub.feature.draft.di

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.feature.draft.data.DraftRepositoryImpl
import com.mmg.manahub.feature.draft.data.remote.CloudflareContentApi
import com.mmg.manahub.feature.draft.data.remote.YouTubeApi
import com.mmg.manahub.feature.draft.data.remote.YouTubeApiKeyInterceptor
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

    companion object {

        private const val MAX_RESPONSE_BYTES = 5L * 1024 * 1024 // 5 MB

        @Provides
        @Singleton
        fun provideGson(): Gson = Gson()

        // -----------------------------------------------------------------------
        // YouTube
        // -----------------------------------------------------------------------

        /**
         * Dedicated OkHttpClient for YouTube with an API-key interceptor.
         * The key is injected at the HTTP layer so it never appears in
         * Retrofit call signatures or Logcat URL logs.
         */
        @Provides
        @Singleton
        @Named("youtube")
        fun provideYouTubeRetrofit(client: OkHttpClient): Retrofit {
            val youtubeClient = client.newBuilder()
                .addInterceptor(YouTubeApiKeyInterceptor(BuildConfig.YOUTUBE_API_KEY))
                .build()
            return Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/youtube/v3/")
                .client(youtubeClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }

        @Provides
        @Singleton
        fun provideYouTubeApi(@Named("youtube") retrofit: Retrofit): YouTubeApi =
            retrofit.create(YouTubeApi::class.java)

        // -----------------------------------------------------------------------
        // Cloudflare Worker
        // -----------------------------------------------------------------------

        /**
         * Dedicated OkHttpClient for the Cloudflare Worker.
         * - 30 s read timeout: tier-list files can be ~70 KB on slow connections.
         * - 5 MB response-size guard: rejects unexpectedly large bodies before Gson
         *   allocates memory for them, protecting against OOM on misconfigured or
         *   tampered responses.
         */
        @Provides
        @Singleton
        @Named("cloudflare")
        fun provideCloudflareRetrofit(client: OkHttpClient): Retrofit {
            val cloudflareClient = client.newBuilder()
                .readTimeout(30, TimeUnit.SECONDS)
                .addNetworkInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    val contentLength = response.header("Content-Length")?.toLongOrNull()
                    if (contentLength != null && contentLength > MAX_RESPONSE_BYTES) {
                        response.close()
                        throw IOException(
                            "Cloudflare response too large: ${contentLength / 1024} KB " +
                                "(limit ${MAX_RESPONSE_BYTES / 1024 / 1024} MB)"
                        )
                    }
                    response
                }
                .build()
            return Retrofit.Builder()
                .baseUrl(BuildConfig.CLOUDFLARE_WORKER_URL)
                .client(cloudflareClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }

        @Provides
        @Singleton
        fun provideCloudflareContentApi(@Named("cloudflare") retrofit: Retrofit): CloudflareContentApi =
            retrofit.create(CloudflareContentApi::class.java)

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
