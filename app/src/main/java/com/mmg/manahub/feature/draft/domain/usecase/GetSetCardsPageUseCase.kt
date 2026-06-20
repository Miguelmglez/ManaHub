package com.mmg.manahub.feature.draft.domain.usecase

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import javax.inject.Inject

/**
 * Fetches a single page of a set's card pool together with Scryfall's `has_more` flag.
 *
 * Callers page until [DataResult.Success] reports `hasMore == false`, avoiding the trailing
 * request that would otherwise 422 when paging one step past the last page.
 */
class GetSetCardsPageUseCase @Inject constructor(
    private val repository: DraftRepository,
) {
    /** @return the page's cards paired with whether more pages remain. */
    suspend operator fun invoke(
        setCode: String,
        page: Int = 1,
    ): DataResult<Pair<List<Card>, Boolean>> =
        repository.getSetCardsPage(setCode, page)
}
