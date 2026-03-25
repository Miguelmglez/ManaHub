package com.mmg.magicfolder.core.data.remote

import com.mmg.magicfolder.core.data.remote.dto.*
import com.mmg.magicfolder.core.data.remote.mapper.toDomain
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.network.ScryfallRequestQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScryfallRemoteDataSource @Inject constructor(
    private val api: ScryfallApi,
    private val requestQueue: ScryfallRequestQueue,
) {

    suspend fun searchCardByName(query: String): Result<Card> =
        safeCall { requestQueue.execute { api.getCardByName(query) }.toDomain() }

    suspend fun searchCards(query: String, page: Int = 1): Result<List<Card>> =
        safeCall { requestQueue.execute { api.searchCards(query, page = page) }.data.toDomain() }

    suspend fun getCardById(scryfallId: String): Result<Card> =
        safeCall { requestQueue.execute { api.getCardById(scryfallId) }.toDomain() }

    suspend fun getCardBySetAndNumber(set: String, number: String): Result<Card> =
        safeCall { requestQueue.execute { api.getCardBySetAndNumber(set, number) }.toDomain() }

    suspend fun getCardsBatch(scryfallIds: List<String>): Result<List<Card>> =
        safeCall {
            val allCards = mutableListOf<CardDto>()
            scryfallIds.chunked(75).forEach { chunk ->
                val identifiers = chunk.map { CardIdentifierDto(id = it) }
                val response = requestQueue.execute {
                    api.getCardCollection(CardCollectionRequestDto(identifiers))
                }
                allCards.addAll(response.data)
            }
            allCards.toDomain()
        }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching { block() } }
}
