package com.mmg.manahub.feature.draft.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Appends the YouTube Data API key as a query parameter at the OkHttp layer.
 *
 * Injecting the key here instead of passing it through Retrofit's @Query annotation
 * prevents the key from appearing in Retrofit method signatures, call-site code,
 * and network-inspector logs that display the full request URL.
 *
 * The key is still transmitted over HTTPS (encrypted in transit), but it is no
 * longer visible in plain-text logs or captured by tools that only inspect
 * Retrofit-level traffic.
 */
internal class YouTubeApiKeyInterceptor(private val apiKey: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url.newBuilder()
            .addQueryParameter("key", apiKey)
            .build()
        val request = original.newBuilder().url(url).build()
        return chain.proceed(request)
    }
}
