package com.mmg.manahub.feature.game.di

import com.mmg.manahub.feature.game.data.repository.GameSessionRepositoryImpl
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI bindings owned by the game feature.
 *
 * The [GameSessionRepository] contract + impl moved out of `core/data` (KMP modularization,
 * Phase 0.5 Blocker 4): the impl depends on the game-feature [com.mmg.manahub.feature.game.domain.model.GameResult]
 * model, so core must not own its binding. The underlying `GameSessionDao` still comes from the
 * core `DatabaseModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GameModule {

    @Binds
    @Singleton
    abstract fun bindGameSessionRepository(impl: GameSessionRepositoryImpl): GameSessionRepository
}
