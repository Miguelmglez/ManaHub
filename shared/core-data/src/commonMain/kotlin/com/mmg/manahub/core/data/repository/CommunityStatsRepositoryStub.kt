package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.model.CommunityStats
import com.mmg.manahub.core.domain.repository.CommunityStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
/**
 * Placeholder [CommunityStatsRepository] that always emits `null`.
 *
 * Community statistics require an aggregated, privacy-preserving backend feed that
 * does not exist yet. Until that pipeline lands, this stub keeps the community
 * widgets in a non-crashing loading state.
 *
 * TODO(home-community): replace with a real implementation backed by an aggregated
 *  Supabase view / Edge Function once the community-stats pipeline is built.
 */
class CommunityStatsRepositoryStub : CommunityStatsRepository {
    override fun observeCommunityStats(): Flow<CommunityStats?> = flowOf(null)
}
