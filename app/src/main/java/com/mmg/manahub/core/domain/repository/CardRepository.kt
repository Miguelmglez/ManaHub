package com.mmg.manahub.core.domain.repository

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.model.SuggestedTag
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    suspend fun searchCardByName(query: String): DataResult<Card>
    suspend fun searchCards(query: String, page: Int = 1): DataResult<List<Card>>
    suspend fun getCardById(scryfallId: String): DataResult<Card>
    fun observeCard(scryfallId: String): Flow<Card?>
    suspend fun refreshCollectionPrices()
    suspend fun updatePrices(
        scryfallId:   String,
        priceUsd:     Double?,
        priceUsdFoil: Double?,
        priceEur:     Double?,
        priceEurFoil: Double?,
    )
    suspend fun evictStaleCache()

    /** Replace the confirmed tag list for a card already in the local cache. */
    suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>)

    /** Replace the user-added tag list for a card. */
    suspend fun updateUserTags(scryfallId: String, userTags: List<CardTag>)

    /** Replace the suggested-tag list (used when the user dismisses suggestions). */
    suspend fun updateSuggestedTags(scryfallId: String, suggestions: List<SuggestedTag>)

    /** Promote a suggested tag to a confirmed tag (and remove it from suggestions). */
    suspend fun confirmSuggestedTag(scryfallId: String, tag: CardTag)

    /** Drop a suggested tag without confirming it. */
    suspend fun dismissSuggestedTag(scryfallId: String, tag: CardTag)
}
