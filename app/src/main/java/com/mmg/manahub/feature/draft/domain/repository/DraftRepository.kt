package com.mmg.manahub.feature.draft.domain.repository

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.feature.draft.domain.model.DraftVideo
import com.mmg.manahub.feature.draft.domain.model.SetDraftGuide
import com.mmg.manahub.feature.draft.domain.model.SetTierList

interface DraftRepository {
    suspend fun getDraftableSets(forceRefresh: Boolean = false): DataResult<List<DraftSet>>
    suspend fun getSetGuide(setCode: String): DataResult<SetDraftGuide>
    suspend fun getSetTierList(setCode: String): DataResult<SetTierList>
    suspend fun getSetCards(setCode: String, page: Int = 1): DataResult<List<Card>>

    /**
     * Like [getSetCards] but also returns Scryfall's `has_more` flag so callers can stop paging
     * without issuing a final request that 422s. The [Boolean] in the pair is `hasMore`.
     */
    suspend fun getSetCardsPage(
        setCode: String,
        page: Int = 1,
    ): DataResult<Pair<List<Card>, Boolean>>
    suspend fun getSetVideos(setCode: String, setName: String): DataResult<List<DraftVideo>>
    suspend fun resolveCardId(cardName: String, setCode: String): DataResult<String>
    suspend fun getCardByName(name: String, setCode: String): DataResult<Card>
}
