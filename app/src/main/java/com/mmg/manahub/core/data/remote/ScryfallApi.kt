package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.CardCollectionRequestDto
import com.mmg.manahub.core.data.remote.dto.CardCollectionResponseDto
import com.mmg.manahub.core.data.remote.dto.CardDto
import com.mmg.manahub.core.data.remote.dto.ScryfallSetsResponseDto
import com.mmg.manahub.core.data.remote.dto.SearchResultDto
import com.mmg.manahub.core.data.remote.dto.SymbologyListDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ScryfallApi {

    @GET("cards/named")
    suspend fun getCardByName(
        @Query("fuzzy") name: String,
        @Query("set") set: String? = null,
    ): CardDto

    @GET("cards/named")
    suspend fun getCardByExactName(@Query("exact") name: String): CardDto

    @GET("cards/search")
    suspend fun searchCards(
        @Query("q")      query:  String,
        @Query("order")  order:  String = "name",
        @Query("dir")    dir:    String = "auto",
        @Query("unique") unique: String = "cards",
        @Query("page")   page:   Int    = 1,
    ): SearchResultDto

    /**
     * Same as [searchCards] but bypasses the OkHttp disk cache.
     * Used as a fallback when OkHttp returns a 304 without being able to serve the
     * cached body (the body was evicted from the 50 MB disk cache by Coil image data).
     */
    @GET("cards/search")
    @Headers("Cache-Control: no-cache")
    suspend fun searchCardsNoCache(
        @Query("q")      query:  String,
        @Query("order")  order:  String = "name",
        @Query("dir")    dir:    String = "auto",
        @Query("unique") unique: String = "cards",
        @Query("page")   page:   Int    = 1,
    ): SearchResultDto

    @GET("cards/{id}")
    suspend fun getCardById(@Path("id") scryfallId: String): CardDto

    @GET("cards/{set}/{collector_number}")
    suspend fun getCardBySetAndNumber(
        @Path("set")              setCode:         String,
        @Path("collector_number") collectorNumber: String,
    ): CardDto

    @POST("cards/collection")
    suspend fun getCardCollection(@Body request: CardCollectionRequestDto): CardCollectionResponseDto

    @GET("symbology")
    suspend fun getAllSymbols(): SymbologyListDto

    @GET("sets")
    suspend fun getSets(): ScryfallSetsResponseDto
}