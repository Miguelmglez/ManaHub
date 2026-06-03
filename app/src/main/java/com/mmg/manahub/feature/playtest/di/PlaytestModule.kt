package com.mmg.manahub.feature.playtest.di

import com.mmg.manahub.feature.playtest.data.repository.PlaytestRepositoryImpl
import com.mmg.manahub.feature.playtest.domain.repository.PlaytestRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for the Deck Playtest feature.
 *
 * Note: [PlaytestDao] is already provided by [com.mmg.manahub.core.data.local.DatabaseModule]
 * (which calls [MtgDatabase.playtestDao]). This module only needs to bind the
 * repository interface to its implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaytestModule {

    /** Binds [PlaytestRepositoryImpl] to the [PlaytestRepository] interface. */
    @Binds
    @Singleton
    abstract fun bindPlaytestRepository(impl: PlaytestRepositoryImpl): PlaytestRepository
}
