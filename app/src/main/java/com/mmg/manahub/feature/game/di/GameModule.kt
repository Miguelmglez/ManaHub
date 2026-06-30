package com.mmg.manahub.feature.game.di

import com.mmg.manahub.feature.game.data.repository.GameSessionRepositoryImpl
import com.mmg.manahub.feature.game.domain.repository.GameSessionRepository
import com.mmg.manahub.feature.game.domain.usecase.EvaluatePlayerEliminationUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
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
 *
 * [EvaluatePlayerEliminationUseCase] moved to `:shared:core-domain` `commonMain` (KMP Phase 4)
 * and lost its `@Inject` annotation (javax.inject is JVM-only). A `@Provides` factory is declared
 * here so Hilt can still inject it into legacy Hilt components ([GameViewModel]).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GameModule {

    @Binds
    @Singleton
    abstract fun bindGameSessionRepository(impl: GameSessionRepositoryImpl): GameSessionRepository

    companion object {

        /**
         * Provides the stateless [EvaluatePlayerEliminationUseCase] singleton.
         *
         * The use case lives in `commonMain` with no constructor dependencies, so construction is
         * trivial. Declared `@Singleton` to avoid unnecessary re-allocation on every injection.
         */
        @Provides
        @Singleton
        fun provideEvaluatePlayerEliminationUseCase(): EvaluatePlayerEliminationUseCase =
            EvaluatePlayerEliminationUseCase()
    }
}
