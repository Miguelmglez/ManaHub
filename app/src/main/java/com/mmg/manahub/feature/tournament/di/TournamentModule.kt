package com.mmg.manahub.feature.tournament.di

import com.mmg.manahub.feature.tournament.data.repository.TournamentRepositoryImpl
import com.mmg.manahub.feature.tournament.domain.repository.TournamentRepository
import com.mmg.manahub.feature.tournament.domain.usecase.CalculateStandingsUseCase
import com.mmg.manahub.feature.tournament.domain.usecase.GenerateNextRoundUseCase
import com.mmg.manahub.feature.tournament.domain.usecase.RecordMatchResultUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
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
 *
 * [CalculateStandingsUseCase] and [RecordMatchResultUseCase] moved to :shared:core-domain
 * (KMP Phase 4) so they carry zero `javax.inject` / Room dependencies; they are constructed here
 * via [Provides] since their constructors are no longer `@Inject`-annotated.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TournamentModule {

    @Binds
    @Singleton
    abstract fun bindTournamentRepository(impl: TournamentRepositoryImpl): TournamentRepository

    companion object {

        @Provides
        @Singleton
        fun provideCalculateStandingsUseCase(
            repository: TournamentRepository,
        ): CalculateStandingsUseCase = CalculateStandingsUseCase(repository)

        @Provides
        @Singleton
        fun provideRecordMatchResultUseCase(
            repository: TournamentRepository,
        ): RecordMatchResultUseCase = RecordMatchResultUseCase(repository)

        @Provides
        @Singleton
        fun provideGenerateNextRoundUseCase(): GenerateNextRoundUseCase = GenerateNextRoundUseCase()
    }
}
