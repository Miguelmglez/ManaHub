package com.mmg.manahub.feature.draft.data.remote

import com.google.gson.JsonObject
import com.mmg.manahub.feature.draft.data.remote.dto.SetsIndexResponse
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Retrofit interface for the Cloudflare Worker that serves ManaHub draft content.
 * Base URL: https://manahub-draft-api.miguel-mglez.workers.dev/
 *
 * All responses are either strongly-typed DTOs (sets-index) or raw [JsonObject]
 * parsed manually to handle the complex nested structures of guide and tier-list JSON.
 */
interface CloudflareContentApi {

    /**
     * Fetches the index of all available draft sets with their content version metadata.
     * Used to determine which sets are available and whether local caches are stale.
     */
    @GET("draft/sets-index.json")
    suspend fun getSetsIndex(): SetsIndexResponse

    /**
     * Fetches the raw JSON for a set's draft guide.
     * Parsed manually in [com.mmg.manahub.feature.draft.data.DraftRepositoryImpl]
     * due to the deeply nested, polymorphic structure.
     *
     * @param setCode Lowercase set code (e.g. "eoe").
     */
    @GET("draft/{setCode}/guide.json")
    suspend fun getSetGuide(@Path("setCode") setCode: String): JsonObject

    /**
     * Fetches the raw JSON for a set's tier list.
     * Parsed manually in [com.mmg.manahub.feature.draft.data.DraftRepositoryImpl].
     *
     * @param setCode Lowercase set code (e.g. "eoe").
     */
    @GET("draft/{setCode}/tier-list.json")
    suspend fun getSetTierList(@Path("setCode") setCode: String): JsonObject

    /**
     * Fetches the raw booster.json describing the set's pack structure
     * (weighted booster variants + named card sheets). Parsed in
     * [com.mmg.manahub.feature.draft.data.DraftSimRepositoryImpl].
     *
     * @param setCode Lowercase set code (e.g. "tdm").
     */
    @GET("draft/{setCode}/booster.json")
    suspend fun getSetBooster(@Path("setCode") setCode: String): JsonObject

    /**
     * Fetches the raw `engine.json` describing the set's archetype decision engine
     * (per-card archetype weights + role flags + scoring params). Optional per set —
     * sets without one fall back to the heuristic bot drafter. Parsed in
     * [com.mmg.manahub.feature.draft.data.DraftSimRepositoryImpl].
     *
     * @param setCode Lowercase set code (e.g. "tdm").
     */
    @GET("draft/{setCode}/engine.json")
    suspend fun getSetEngine(@Path("setCode") setCode: String): JsonObject
}
