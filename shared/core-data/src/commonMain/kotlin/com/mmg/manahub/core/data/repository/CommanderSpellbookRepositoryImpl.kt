package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.ComboCache
import com.mmg.manahub.core.data.remote.CommanderSpellbookApiContract
import com.mmg.manahub.core.data.remote.ComboCacheKeys
import com.mmg.manahub.core.data.remote.dto.CardInDeckRequestDto
import com.mmg.manahub.core.data.remote.dto.FindMyCombosRequestDto
import com.mmg.manahub.core.data.remote.dto.FindMyCombosResponseDto
import com.mmg.manahub.core.data.remote.dto.VariantDto
import com.mmg.manahub.core.domain.repository.CommanderSpellbookRepository
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.feature.decks.domain.model.AlmostCombo
import com.mmg.manahub.feature.decks.domain.model.Combo
import com.mmg.manahub.feature.decks.domain.model.ComboResult
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

private val comboJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

/** 7-day freshness window for cached combo results -- matches [CommunityAggregateRepositoryImpl]'s
 * `AGGREGATE_FRESH_MS` convention (plan D7: "~7 days"). */
private const val COMBO_FRESH_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Cache-first, Commander-Spellbook-backed implementation of [CommanderSpellbookRepository] (Deck
 * Engine Unification plan D7, Phase 4.3).
 *
 * Layering mirrors [CommunityAggregateRepositoryImpl]: Room/IndexedDB cache (7-day freshness) ->
 * [api] -> stale cache on failure -> [ComboResult.EMPTY] if nothing is cached either. Never
 * throws -- every failure path is caught and degrades, since this is an unofficial-adjacent third
 * party and the Combos tab must never block the rest of the synergy browser.
 *
 * [api] and [cache] are narrow interfaces (not the concrete Ktor/Room classes) so this class's own
 * cache/API dispatch logic is unit-testable with trivial fakes -- see
 * `CommanderSpellbookRepositoryImplTest` (commonTest, no Ktor engine mock needed), mirroring
 * `CommunityAggregateRepositoryImplTest`.
 */
class CommanderSpellbookRepositoryImpl(
    private val api: CommanderSpellbookApiContract,
    private val cache: ComboCache,
    private val crashReporter: CrashReporter,
    private val dispatcherProvider: DispatcherProvider,
    private val now: () -> Long,
) : CommanderSpellbookRepository {

    override suspend fun findCombos(
        cardNames: List<String>,
        commanderNames: List<String>,
    ): DataResult<ComboResult> = withContext(dispatcherProvider.io) {
        if (cardNames.isEmpty() && commanderNames.isEmpty()) {
            return@withContext DataResult.Success(ComboResult.EMPTY)
        }

        val key = ComboCacheKeys.cacheKey(cardNames, commanderNames)
        val cached = cache.get(key)
        if (cached != null && isFresh(cached.cachedAt)) {
            val decoded = decode(cached.json, cardNames, commanderNames)
            return@withContext if (decoded != null) DataResult.Success(decoded) else DataResult.Error("Corrupt combo cache entry")
        }

        try {
            val request = FindMyCombosRequestDto(
                main = cardNames.map { CardInDeckRequestDto(card = it) },
                commanders = commanderNames.map { CardInDeckRequestDto(card = it) },
            )
            val dto = api.findMyCombos(request)
            cache.insert(key, comboJson.encodeToString(FindMyCombosResponseDto.serializer(), dto), now())
            val result = toDomain(dto, cardNames, commanderNames)
            DataResult.Success(result)
        } catch (e: Exception) {
            recordFailure(e)
            val stale = cached?.let { decode(it.json, cardNames, commanderNames) }
            if (stale != null) DataResult.Success(stale, isStale = true) else DataResult.Success(ComboResult.EMPTY, isStale = true)
        }
    }

    private fun isFresh(cachedAt: Long): Boolean = now() - cachedAt < COMBO_FRESH_MS

    private fun decode(json: String, cardNames: List<String>, commanderNames: List<String>): ComboResult? =
        try {
            toDomain(comboJson.decodeFromString(FindMyCombosResponseDto.serializer(), json), cardNames, commanderNames)
        } catch (e: Exception) {
            null
        }

    private fun recordFailure(e: Exception) {
        crashReporter.log("commander_spellbook_find_combos_failed")
        val statusCode = (e as? ResponseException)?.response?.status?.value
        if (statusCode != null) crashReporter.setCustomKey("commander_spellbook_http_code", statusCode.toString())
    }

    /**
     * Maps the raw Spellbook response into [ComboResult]. `included` -> [ComboResult.complete]
     * verbatim (every card in the variant is already owned by construction of the request).
     * `almostIncluded` -> [ComboResult.almostThere]: the missing card is derived client-side as
     * whichever of the variant's [VariantDto.uses] card names is NOT in the queried set (case-
     * insensitive) -- defensive per [CommanderSpellbookRepository]'s KDoc; a variant reporting
     * zero or more-than-one missing card (not expected, but this is an unofficial-adjacent third
     * party) is silently dropped rather than surfacing a nonsensical "missing" label.
     */
    private fun toDomain(
        dto: FindMyCombosResponseDto,
        cardNames: List<String>,
        commanderNames: List<String>,
    ): ComboResult {
        val ownedLower = (cardNames + commanderNames).map { it.lowercase() }.toSet()
        val results = dto.results ?: return ComboResult.EMPTY

        val complete = results.included.map { variant ->
            Combo(
                id = variant.id,
                cardNames = variant.uses.map { it.card.name },
                description = variant.description,
                produces = variant.produces.map { it.feature.name },
            )
        }

        val almostThere = results.almostIncluded.mapNotNull { variant ->
            val missing = variant.uses.map { it.card.name }.filter { it.lowercase() !in ownedLower }
            val missingCardName = missing.singleOrNull() ?: return@mapNotNull null
            AlmostCombo(
                id = variant.id,
                ownedCardNames = variant.uses.map { it.card.name }.filterNot { it == missingCardName },
                missingCardName = missingCardName,
                description = variant.description,
                produces = variant.produces.map { it.feature.name },
            )
        }

        return ComboResult(complete = complete, almostThere = almostThere)
    }
}
