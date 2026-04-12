package com.mmg.manahub.feature.draft.di

import com.google.gson.Gson
import com.mmg.manahub.feature.draft.data.DraftRepositoryImpl
import com.mmg.manahub.feature.draft.data.remote.YouTubeApi
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
        fun provideYouTubeRetrofit(client: OkHttpClient): Retrofit =
            Retrofit.Builder()
                .baseUrl("https://www.googleapis.com/youtube/v3/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

        @Provides
        @Singleton
        fun provideYouTubeApi(@Named("youtube") retrofit: Retrofit): YouTubeApi =
            retrofit.create(YouTubeApi::class.java)
    }
}
