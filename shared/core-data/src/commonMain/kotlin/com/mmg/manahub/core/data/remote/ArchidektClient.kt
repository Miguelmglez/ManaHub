package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.core.data.remote.dto.ArchidektSearchResultDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class ArchidektClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {
    suspend fun getDeckById(id: Int): ArchidektDeckDetailDto =
        httpClient.get("${baseUrl}api/decks/${id}/").body()

    suspend fun searchDecks(
        cardName: String? = null,
        deckFormat: Int? = null,
        orderBy: String? = null,
        page: Int? = null,
        pageSize: Int? = null,
    ): ArchidektSearchResultDto =
        httpClient.get("${baseUrl}api/decks/v3/") {
            cardName?.let { parameter("cardName", it) }
            deckFormat?.let { parameter("deckFormat", it) }
            orderBy?.let { parameter("orderBy", it) }
            page?.let { parameter("page", it) }
            pageSize?.let { parameter("pageSize", it) }
        }.body()
}
