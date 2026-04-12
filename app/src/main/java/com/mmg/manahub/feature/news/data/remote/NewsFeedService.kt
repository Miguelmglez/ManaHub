package com.mmg.manahub.feature.news.data.remote

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NewsFeedService @Inject constructor(
    private val client: OkHttpClient,
) {
    suspend fun fetchFeed(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, */*")
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                Result.success(body)
            } else {
                Result.failure(Exception("HTTP ${response.code} for $url"))
            }
        } catch (e: Exception) {
            // Omit the raw URL from the log message to avoid leaking feed endpoints
            // (including any user-added custom feed URLs) into Logcat.
            Log.w("NewsFeedService", "Failed to fetch feed: ${e.message}")
            Result.failure(e)
        }
    }
}
