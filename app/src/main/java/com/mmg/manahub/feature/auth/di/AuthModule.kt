package com.mmg.manahub.feature.auth.di

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.auth.data.remote.UserProfileDataSource
import com.mmg.manahub.feature.auth.data.repository.AuthRepositoryImpl
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    companion object {
        @Provides
        @Singleton
        fun provideUserProfileDataSource(
            supabase: SupabaseClient,
            @IoDispatcher dispatcher: CoroutineDispatcher
        ): UserProfileDataSource = UserProfileDataSource(supabase, dispatcher)
    }
}
