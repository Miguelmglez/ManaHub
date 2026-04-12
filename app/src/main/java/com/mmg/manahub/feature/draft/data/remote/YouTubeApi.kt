package com.mmg.manahub.feature.draft.data.remote

import com.mmg.manahub.feature.draft.data.remote.dto.YouTubeSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

// The API key is injected by YouTubeApiKeyInterceptor at the OkHttp layer so it
// never appears in Retrofit method signatures, call-site code, or network logs.
interface YouTubeApi {

    @GET("search")
    suspend fun searchVideos(
        @Query("part") part: String = "snippet",
        @Query("q") query: String,
        @Query("type") type: String = "video",
        @Query("maxResults") maxResults: Int = 15,
        @Query("order") order: String = "relevance",
        @Query("relevanceLanguage") language: String = "en",
    ): YouTubeSearchResponse
}
