package com.mmg.manahub.core.di

import com.mmg.manahub.core.data.repository.PushTokenRepositoryImpl
import com.mmg.manahub.core.domain.repository.PushTokenRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PushModule {

    @Binds
    @Singleton
    abstract fun bindPushTokenRepository(impl: PushTokenRepositoryImpl): PushTokenRepository
}
