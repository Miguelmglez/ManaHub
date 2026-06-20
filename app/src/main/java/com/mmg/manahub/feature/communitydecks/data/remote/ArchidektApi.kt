package com.mmg.manahub.feature.communitydecks.data.remote

import com.mmg.manahub.feature.communitydecks.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.feature.communitydecks.data.remote.dto.ArchidektSearchResultDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the public Archidekt deck API.
 *
 * All calls MUST be wrapped in [ArchidektRequestQueue.execute] so that the
 * conservative inter-request spacing and retry/back-off policy are honoured.
 */
interface ArchidektApi {

    /** Fetches the full detail of a single deck by its Archidekt numeric id. */
    @GET("api/decks/{id}/")
    suspend fun getDeckById(@Path("id") id: Int): ArchidektDeckDetailDto

    /**
     * Searches community decks (Phase 2). All parameters are optional and omitted
     * from the request when null.
     */
    @GET("api/decks/v3/")
    suspend fun searchDecks(
        @Query("cardName") cardName: String? = null,
        @Query("deckFormat") deckFormat: Int? = null,
        @Query("orderBy") orderBy: String? = null,
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
    ): ArchidektSearchResultDto
}
