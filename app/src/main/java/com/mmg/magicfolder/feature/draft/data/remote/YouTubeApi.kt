package com.mmg.magicfolder.feature.draft.data.remote

import com.mmg.magicfolder.feature.draft.data.remote.dto.YouTubeSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface YouTubeApi {

    @GET("search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 15,
        @Query("order") order: String = "relevance",
        @Query("relevanceLanguage") language: String = "en",
        @Query("key") apiKey: String,
    ): YouTubeSearchResponse
}
