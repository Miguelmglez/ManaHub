package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.*
import retrofit2.http.*

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