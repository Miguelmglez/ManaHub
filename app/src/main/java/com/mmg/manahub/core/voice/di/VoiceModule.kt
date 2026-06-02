package com.mmg.manahub.core.voice.di

import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.voice.data.VoiceModelRepositoryImpl
import com.mmg.manahub.core.voice.data.VoskVoiceRecognizer
import com.mmg.manahub.core.voice.domain.VoiceCommandRecognizer
import com.mmg.manahub.core.voice.domain.VoiceModelRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {

    @Binds
    @Singleton
    abstract fun bindVoiceCommandRecognizer(impl: VoskVoiceRecognizer): VoiceCommandRecognizer

    @Binds
    @Singleton
    abstract fun bindVoiceModelRepository(impl: VoiceModelRepositoryImpl): VoiceModelRepository

    companion object {

        @Provides
        @Singleton
        @Named("voice")
        fun provideVoiceHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()

        @Provides
        @Named("cloudflare_base_url")
        fun provideCloudflareBaseUrl(): String = BuildConfig.CLOUDFLARE_WORKER_URL
    }
}
