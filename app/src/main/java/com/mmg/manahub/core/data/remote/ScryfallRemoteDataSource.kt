package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.CardCollectionRequestDto
import com.mmg.manahub.core.data.remote.dto.CardCollectionResponseDto
import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.data.remote.dto.CardIdentifierDto
import com.mmg.manahub.core.data.remote.dto.SearchResultDto
import com.mmg.manahub.core.data.remote.mapper.toDomain
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.MagicSet
import com.mmg.manahub.core.domain.model.PLAYABLE_SET_TYPES
import com.mmg.manahub.core.domain.model.SetType
import com.mmg.manahub.core.network.ScryfallRequestQueue
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

    suspend fun getCardByExactName(name: String): Result<Card> =
        safeCall { requestQueue.execute { api.getCardByExactName(name) }.toDomain() }

    suspend fun searchCards(query: String, page: Int = 1): Result<List<Card>> =
        safeCall { requestQueue.execute { api.searchCards(query, page = page) }.data.toDomain() }

    suspend fun getCardById(scryfallId: String): Result<Card> =
        safeCall { requestQueue.execute { api.getCardById(scryfallId) }.toDomain() }

    suspend fun getCardBySetAndNumber(set: String, number: String): Result<Card> =
        safeCall { requestQueue.execute { api.getCardBySetAndNumber(set, number) }.toDomain() }

    suspend fun getCardCollection(
        scryfallIds: List<String>,
    ): CardCollectionResponseDto {
        val identifiers = scryfallIds.map { CardIdentifierDto(id = it) }
        return requestQueue.execute {
            api.getCardCollection(CardCollectionRequestDto(identifiers))
        }
    }

    suspend fun searchWithRawQuery(query: String): List<Card> =
        safeCall {
            requestQueue.execute { api.searchCards(query, page = 1) }.data.toDomain()
        }.getOrDefault(emptyList())

    suspend fun getCardsBatch(scryfallIds: List<String>): Result<List<Card>> =
        safeCall {
            // Guard: Scryfall enforces a hard cap of 75 identifiers per /cards/collection
            // request. We also apply an overall limit so a caller with a very large
            // collection cannot trigger hundreds of consecutive API requests in a single
            // call, which would exhaust the rate-limit budget and stall other operations.
            val MAX_TOTAL = 1_000
            val sanitized = scryfallIds.take(MAX_TOTAL)

            val allCards = mutableListOf<CardDto>()
            sanitized.chunked(75).forEach { chunk ->
                val identifiers = chunk.map { CardIdentifierDto(id = it) }
                val response = requestQueue.execute {
                    api.getCardCollection(CardCollectionRequestDto(identifiers))
                }
                allCards.addAll(response.data)
            }
            allCards.toDomain()
        }

    suspend fun getAllSets(): List<MagicSet> {
        return requestQueue.execute { api.getSets() }
            .data
            .filter { dto ->
                !dto.digital &&
                SetType.from(dto.setType) in PLAYABLE_SET_TYPES
            }
            .map { dto ->
                MagicSet(
                    code       = dto.code,
                    name       = dto.name,
                    setType    = SetType.from(dto.setType),
                    releasedAt = dto.releasedAt,
                    cardCount  = dto.cardCount,
                    iconSvgUri = dto.iconSvgUri,
                )
            }
            .sortedByDescending { it.releasedAt ?: "" }
    }

    // Reutilizes /cards/search with unique=art to get one entry per unique artwork
    suspend fun searchPlaneswalkerArts(
        query: String,
        page:  Int = 1,
    ): SearchResultDto = requestQueue.execute {
        api.searchCards(
            query  = query,
            order  = "name",
            unique = "art",
            page   = page,
        )
    }

    suspend fun getCardArtVariants(name: String): Result<List<Card>> =
        safeCall {
            val safeName = name.replace("\"", "").replace("\\", "").trim()
            if (safeName.isBlank()) return@safeCall emptyList()
            requestQueue.execute {
                api.searchCards(query = "!\"$safeName\"", unique = "art", order = "released")
            }.data.toDomain()
        }

    private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching { block() } }
}
