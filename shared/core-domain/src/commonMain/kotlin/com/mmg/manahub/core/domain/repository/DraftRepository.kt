package com.mmg.manahub.core.domain.repository
// COMMENTS_REVIEWED: 2026-09-22

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.model.SetDraftGuide
import com.mmg.manahub.core.model.SetTierList

interface DraftRepository {
    suspend fun getDraftableSets(forceRefresh: Boolean = false): DataResult<List<DraftSet>>
    suspend fun getSetGuide(setCode: String): DataResult<SetDraftGuide>
    suspend fun getSetTierList(setCode: String): DataResult<SetTierList>
    suspend fun getSetCards(setCode: String, page: Int = 1): DataResult<List<Card>>

    // Returns (cards, hasMore) so callers stop paging before a final request that would 422
    suspend fun getSetCardsPage(
        setCode: String,
        page: Int = 1,
        extraPoolSets: List<String> = emptyList(),
    ): DataResult<Pair<List<Card>, Boolean>>
    suspend fun resolveCardId(cardName: String, setCode: String): DataResult<String>
    suspend fun getCardByName(name: String, setCode: String): DataResult<Card>
}
