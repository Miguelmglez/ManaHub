package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.model.DraftVideo
import com.mmg.manahub.core.model.SetDraftGuide
import com.mmg.manahub.core.model.SetTierList

interface DraftRepository {
    suspend fun getDraftableSets(forceRefresh: Boolean = false): DataResult<List<DraftSet>>
    suspend fun getSetGuide(setCode: String): DataResult<SetDraftGuide>
    suspend fun getSetTierList(setCode: String): DataResult<SetTierList>
    suspend fun getSetCards(setCode: String, page: Int = 1): DataResult<List<Card>>

    /**
     * Like [getSetCards] but also returns Scryfall's `has_more` flag so callers can stop paging
     * without issuing a final request that 422s. The [Boolean] in the pair is `hasMore`.
     *
     * @param extraPoolSets Additional Scryfall set codes to widen the pool query to (from
     * [com.mmg.manahub.core.model.BoosterConfig.extraPoolSets]), e.g. `["soa"]` for SOS's
     * Mystical Archive. When empty (the default), the query is `set:$setCode lang:en`, exactly
     * as before; otherwise it becomes `(set:$setCode or set:soa or ...) lang:en`.
     */
    suspend fun getSetCardsPage(
        setCode: String,
        page: Int = 1,
        extraPoolSets: List<String> = emptyList(),
    ): DataResult<Pair<List<Card>, Boolean>>
    suspend fun getSetVideos(setCode: String, setName: String): DataResult<List<DraftVideo>>
    suspend fun resolveCardId(cardName: String, setCode: String): DataResult<String>
    suspend fun getCardByName(name: String, setCode: String): DataResult<Card>
}
