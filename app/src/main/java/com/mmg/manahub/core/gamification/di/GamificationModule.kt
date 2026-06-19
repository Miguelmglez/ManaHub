package com.mmg.manahub.core.gamification.di

import com.mmg.manahub.core.gamification.data.remote.GamificationRemoteDataSource
import com.mmg.manahub.core.gamification.data.remote.SupabaseGamificationDataSource
import com.mmg.manahub.core.gamification.data.repository.GamificationRepositoryImpl
import com.mmg.manahub.core.gamification.di.GamificationModule.Companion.provideZoneId
import com.mmg.manahub.core.gamification.domain.GamificationEngine
import com.mmg.manahub.core.gamification.domain.repository.GamificationRepository
import com.mmg.manahub.core.gamification.engine.GamificationEngineImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

/**
 * Hilt bindings for the gamification engine (ADR-002).
 *
 * - [GamificationDao][com.mmg.manahub.core.data.local.dao.GamificationDao] is ALREADY provided by
 *   `DatabaseModule` — do NOT add a duplicate `@Provides` here (mirrors the PlaytestModule rule).
 * - The bus, engine, XpGranter, and evaluators are constructor-injected `@Singleton`s; only the
 *   interface bindings and the system [Clock]/[ZoneId] need declaring.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GamificationModule {

    /** Binds the engine implementation to its interface. */
    @Binds
    @Singleton
    abstract fun bindGamificationEngine(impl: GamificationEngineImpl): GamificationEngine

    /** Binds the read-side repository implementation to its interface. */
    @Binds
    @Singleton
    abstract fun bindGamificationRepository(impl: GamificationRepositoryImpl): GamificationRepository

    /** Binds the Phase-4 sync remote data source (Supabase) to its interface. */
    @Binds
    @Singleton
    abstract fun bindGamificationRemoteDataSource(
        impl: SupabaseGamificationDataSource,
    ): GamificationRemoteDataSource

    companion object {

        /** System UTC-anchored clock; XpGranter combines it with [provideZoneId] for local windows. */
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemDefaultZone()

        /** The device's default time zone, used to compute local day/week cap windows. */
        @Provides
        @Singleton
        fun provideZoneId(): ZoneId = ZoneId.systemDefault()
    }
}
