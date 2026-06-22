package com.mmg.manahub.core.di

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.repository.NotificationPrefsRepositoryImpl
import com.mmg.manahub.core.data.repository.PushTokenRepositoryImpl
import com.mmg.manahub.core.domain.repository.NotificationPrefsRepository
import com.mmg.manahub.core.domain.repository.PushTokenRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {

    @Binds
    @Singleton
    abstract fun bindPushTokenRepository(impl: PushTokenRepositoryImpl): PushTokenRepository

    companion object {

        @Provides
        @Singleton
        fun provideNotificationPrefsRepository(
            supabaseClient: SupabaseClient,
        ): NotificationPrefsRepository = NotificationPrefsRepositoryImpl(
            supabaseClient = supabaseClient,
            dispatcherProvider = DispatcherProvider(),
        )
    }
}
