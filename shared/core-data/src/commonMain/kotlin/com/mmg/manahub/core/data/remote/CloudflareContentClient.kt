package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.SetsIndexResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * KMP-pure HTTP client for the Cloudflare Worker that serves ManaHub draft content.
 *
 * Replaces the Retrofit-based `CloudflareContentApi` interface. Lives in
 * `:shared:core-data` `commonMain` so it compiles for both Android and wasmJs.
 *
 * The typed endpoint (`getSetsIndex`) deserialises via Ktor's `ContentNegotiation`
 * plugin (kotlinx-serialization JSON). The four per-set endpoints return raw JSON
 * strings — callers parse them with their own JSON logic (Gson on Android today,
 * kotlinx-serialization when the parsers are migrated later).
 *
 * @param httpClient A Ktor [HttpClient] with `ContentNegotiation` (JSON) installed.
 *   On Android the OkHttp engine is used (reuses existing interceptors); on wasmJs
 *   the Js engine is used.
 * @param baseUrl The Worker's base URL, must end with `/`.
 */
class CloudflareContentClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) {

    /** Fetches the index of all available draft sets with their content version metadata. */
    suspend fun getSetsIndex(): SetsIndexResponse =
        httpClient.get("${baseUrl}draft/sets-index.json").body()

    /**
     * Fetches the raw JSON for a set's draft guide.
     *
     * @param setCode Lowercase set code (e.g. "eoe").
     */
    suspend fun getSetGuide(setCode: String): String =
        httpClient.get("${baseUrl}draft/${setCode}/guide.json").bodyAsText()

    /**
     * Fetches the raw JSON for a set's tier list.
     *
     * @param setCode Lowercase set code (e.g. "eoe").
     */
    suspend fun getSetTierList(setCode: String): String =
        httpClient.get("${baseUrl}draft/${setCode}/tier-list.json").bodyAsText()

    /**
     * Fetches the raw booster.json describing the set's pack structure.
     *
     * @param setCode Lowercase set code (e.g. "tdm").
     */
    suspend fun getSetBooster(setCode: String): String =
        httpClient.get("${baseUrl}draft/${setCode}/booster.json").bodyAsText()

    /**
     * Fetches the raw engine.json describing the set's archetype decision engine.
     * Optional per set -- sets without one fall back to the heuristic bot drafter.
     *
     * @param setCode Lowercase set code (e.g. "tdm").
     */
    suspend fun getSetEngine(setCode: String): String =
        httpClient.get("${baseUrl}draft/${setCode}/engine.json").bodyAsText()
}
