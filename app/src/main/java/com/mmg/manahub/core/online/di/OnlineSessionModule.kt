package com.mmg.manahub.core.online.di

import com.mmg.manahub.core.online.data.repository.OnlineSessionRepositoryImpl
import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class OnlineSessionModule {

    @Binds
    @Singleton
    abstract fun bindOnlineSessionRepository(
        impl: OnlineSessionRepositoryImpl,
    ): OnlineSessionRepository
}
