package com.mmg.manahub.core.di

import com.mmg.manahub.core.data.repository.CommunityStatsRepositoryStub
import com.mmg.manahub.core.domain.repository.CommunityStatsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the community-stats contract via its current stub implementation.
 *
 * Swap [CommunityStatsRepositoryStub] for a real impl when the backend feed exists;
 * no consumer changes are needed because they depend on the interface.
 */
@Module
@InstallIn(SingletonComponent::class)
object CommunityModule {

    @Provides
    @Singleton
    fun provideCommunityStatsRepository(): CommunityStatsRepository =
        CommunityStatsRepositoryStub()
}
