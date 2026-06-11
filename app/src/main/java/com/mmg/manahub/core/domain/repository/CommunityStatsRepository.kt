package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.domain.model.CommunityStats
import kotlinx.coroutines.flow.Flow

/**
 * Contract for aggregated community statistics.
 *
 * The Home community widgets (top commanders, meta, most-wishlisted, milestones)
 * consume this flow. A `null` emission means "not yet loaded" and drives a loading
 * state in the UI; a populated [CommunityStats] with empty lists drives an empty
 * state.
 *
 * No production data source exists yet — see [com.mmg.manahub.core.data.repository.CommunityStatsRepositoryStub].
 */
interface CommunityStatsRepository {
    fun observeCommunityStats(): Flow<CommunityStats?>
}
