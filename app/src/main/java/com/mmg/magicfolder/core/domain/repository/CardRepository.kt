package com.mmg.magicfolder.core.domain.repository

import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.CardTag
import com.mmg.magicfolder.core.domain.model.DataResult
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    suspend fun searchCardByName(query: String): DataResult<Card>
    suspend fun searchCards(query: String, page: Int = 1): DataResult<List<Card>>
    suspend fun getCardById(scryfallId: String): DataResult<Card>
    fun observeCard(scryfallId: String): Flow<Card?>
    suspend fun refreshCollectionPrices()
    suspend fun evictStaleCache()
    /** Replace the tag list for a card already in the local cache. */
    suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>)
}