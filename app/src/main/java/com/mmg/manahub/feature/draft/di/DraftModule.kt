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
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DraftModule {

    @Binds
    @Singleton
    abstract fun bindDraftRepository(impl: DraftRepositoryImpl): DraftRepository

    companion object {

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
         * Retrofit instance pointed at the ManaHub Cloudflare Worker.
         * Uses the shared OkHttpClient (includes logging, cache, User-Agent).
         */
        @Provides
        @Singleton
        @Named("cloudflare")
        fun provideCloudflareRetrofit(client: OkHttpClient): Retrofit =
            Retrofit.Builder()
                .baseUrl("https://manahub-draft-api.miguel-mglez.workers.dev/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

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
