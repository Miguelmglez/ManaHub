package com.mmg.magicfolder.code.core.data.remote


import com.mmg.magicfolder.code.core.data.remote.dto.*
import com.mmg.magicfolder.code.core.data.remote.mapper.toDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScryfallRemoteDataSource @Inject constructor(private val api: ScryfallApi) {

    suspend fun searchCardByName(query: String): Result =
        safeCall { api.getCardByName(query).toDomain() }

    suspend fun searchCards(query: String, page: Int = 1): Result> =
    safeCall { api.searchCards(query, page = page).data.toDomain() }

    suspend fun getCardById(scryfallId: String): Result =
        safeCall { api.getCardById(scryfallId).toDomain() }

    suspend fun getCardBySetAndNumber(set: String, number: String): Result =
        safeCall { api.getCardBySetAndNumber(set, number).toDomain() }

    suspend fun getCardsBatch(scryfallIds: List): Result> =
    safeCall {
        scryfallIds
            .chunked(75) { chunk ->
                val identifiers = chunk.map { CardIdentifierDto(id = it) }
                api.getCardCollection(CardCollectionRequestDto(identifiers)).data
            }
            .flatten()
            .toDomain()
    }

    private suspend fun  safeCall(block: suspend () -> T): Result =
        withContext(Dispatchers.IO) { runCatching { block() } }
}