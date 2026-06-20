package com.mmg.manahub.feature.tournament.di

import com.mmg.manahub.feature.tournament.data.repository.TournamentRepositoryImpl
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * DI bindings owned by the tournament feature.
 *
 * The [TournamentRepository] contract + impl moved out of `core/data` (KMP modularization,
 * Phase 0.5 Blocker 4): the impl depends on tournament-feature engine/use-case types
 * (StandingsCalculator, TournamentIdCodec, GenerateNextRoundUseCase), so core must not own its
 * binding. The underlying `TournamentDao` still comes from the core `DatabaseModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TournamentModule {

    @Binds
    @Singleton
    abstract fun bindTournamentRepository(impl: TournamentRepositoryImpl): TournamentRepository
}
