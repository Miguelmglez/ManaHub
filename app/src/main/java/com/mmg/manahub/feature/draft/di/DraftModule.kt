package com.mmg.manahub.feature.draft.di

import com.google.gson.Gson
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.feature.draft.data.DraftRepositoryImpl
import com.mmg.manahub.feature.draft.data.remote.YouTubeApi
import com.mmg.manahub.feature.draft.data.remote.YouTubeApiKeyInterceptor
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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

        @Provides
        @Singleton
        @Named("youtube")
        fun provideYouTubeRetrofit(client: OkHttpClient): Retrofit {
            // Build a dedicated OkHttpClient that adds the API key via an interceptor.
            // This keeps the key out of Retrofit call signatures and Logcat URL logs.
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
    }
}
