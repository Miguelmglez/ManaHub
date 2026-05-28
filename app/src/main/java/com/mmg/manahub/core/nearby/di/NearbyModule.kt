package com.mmg.manahub.core.nearby.di

import com.mmg.manahub.core.nearby.data.NearbySessionRepositoryImpl
import com.mmg.manahub.core.nearby.domain.repository.NearbySessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that binds the Nearby Connections session repository.
 *
 * Installed in [SingletonComponent] so that a single [NearbySessionRepositoryImpl] instance
 * is shared across all ViewModels for the lifetime of the application process. This is
 * required because the Nearby Connections lifecycle (advertising / discovery / connected
 * endpoints) must survive ViewModel recreation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NearbyModule {

    @Binds
    @Singleton
    abstract fun bindNearbySessionRepository(
        impl: NearbySessionRepositoryImpl,
    ): NearbySessionRepository
}
