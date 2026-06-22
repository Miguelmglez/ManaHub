package com.mmg.manahub.feature.communitydecks.domain.usecase

import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.domain.repository.CommunityDecksRepository

/**
 * Fetches the full detail of a single community deck (Archidekt) by its numeric id.
 *
 * Thin pass-through to [CommunityDecksRepository.getDeckById]; the cache-first /
 * stale-fallback logic lives in the repository.
 */
class GetCommunityDeckUseCase(
    private val repository: CommunityDecksRepository,
) {
    suspend operator fun invoke(archidektId: Int): DataResult<CommunityDeck> =
        repository.getDeckById(archidektId)
}
