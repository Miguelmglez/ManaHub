package com.mmg.manahub.core.di

import com.mmg.manahub.core.data.repository.CommunityStatsRepositoryStub
import com.mmg.manahub.core.domain.repository.CommunityStatsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the community-stats contract to its current stub implementation.
 *
 * Swap [CommunityStatsRepositoryStub] for a real impl when the backend feed exists;
 * no consumer changes are needed because they depend on the interface.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CommunityModule {

    @Binds
    @Singleton
    abstract fun bindCommunityStatsRepository(
        impl: CommunityStatsRepositoryStub,
    ): CommunityStatsRepository
}
