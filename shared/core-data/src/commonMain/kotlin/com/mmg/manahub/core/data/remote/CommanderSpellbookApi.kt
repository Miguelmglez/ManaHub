package com.mmg.manahub.core.data.remote

import com.mmg.manahub.core.data.remote.dto.FindMyCombosRequestDto
import com.mmg.manahub.core.data.remote.dto.FindMyCombosResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Contract for [CommanderSpellbookApi] -- [CommanderSpellbookRepositoryImpl] depends on this
 * interface (not the concrete Ktor-backed class), mirroring [CommunityAggregateApiContract]'s
 * "no Ktor engine mock needed in tests" rationale.
 */
interface CommanderSpellbookApiContract {
    suspend fun findMyCombos(request: FindMyCombosRequestDto): FindMyCombosResponseDto
}

/**
 * KMP-pure HTTP client for the Commander Spellbook public API (Deck Engine Unification plan D7,
 * Phase 4.3) -- lives in `:shared:core-data` `commonMain` so it compiles for both Android and
 * wasmJs, mirroring [CommunityAggregateApi]'s pattern. No API key required (public docs).
 *
 * @param httpClient a Ktor [HttpClient] with `ContentNegotiation` (JSON) installed and a polite
 *   User-Agent header (see `CommanderSpellbookKoinModule`) -- this is an unofficial-adjacent
 *   third party, so identifying this app's traffic is the courteous default (`ScryfallRequestQueue`
 *   precedent), even though Spellbook itself doesn't document a rate limit.
 * @param baseUrl must end with `/` (e.g. `https://backend.commanderspellbook.com/`).
 */
class CommanderSpellbookApi(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : CommanderSpellbookApiContract {
    override suspend fun findMyCombos(request: FindMyCombosRequestDto): FindMyCombosResponseDto =
        httpClient.post("${baseUrl}find-my-combos") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
}
