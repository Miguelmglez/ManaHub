package com.mmg.manahub.core.data.remote.edhrec

import com.mmg.manahub.feature.decks.domain.engine.EDHREC_SLUG_TO_THEME_ID
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lightweight, mobile-appropriate on-device counterpart to `:tools:tag-pipeline`'s bulk EDHREC
 * harvest (Deck Engine Unification plan §8a addendum). Given a card name and a SMALL shortlist of
 * candidate [ThemeId]s (seeded by the on-device rule engine's own SUGGESTED tags via
 * [CARD_TAG_KEY_TO_THEME_ID] — never invented from nothing), fetches at most [MAX_CANDIDATES]
 * EDHREC theme pages (the SAME `pages/tags/{slug}.json` endpoint and [harvestCardSignals] parser
 * the offline pipeline uses) and reports which candidates genuinely list this card with a positive
 * synergy weight.
 *
 * **This is deliberately NOT a per-card EDHREC lookup** — `json.edhrec.com/pages/cards/{slug}.json`
 * was verified live (2026-07-21) to return "cards commonly played with this card" + combos + type
 * distribution, never the card's OWN theme/archetype membership. See [CARD_TAG_KEY_TO_THEME_ID]'s
 * KDoc for the full reasoning behind this shortlist-based substitute.
 *
 * Fully fallible per [EdhrecThemeClient]'s own contract, mirrored here: a network failure, non-200,
 * or decode error for any one candidate's page simply drops that candidate — it NEVER throws to the
 * caller and never aborts the rest of the shortlist. Pages are memoized in-process per session
 * (a [Mutex]-guarded map, not persisted) — multiple missing cards that share a candidate theme
 * (e.g. several Aristocrats-adjacent cards resolved back-to-back) fetch that theme's page ONCE.
 */
interface EdhrecCardTagEnrichmentSourceContract {
    /**
     * @param cardName the card's exact English name (Scryfall/EDHREC names match verbatim in the
     *   overwhelming majority of cases; a name-mismatch simply yields no match for that candidate,
     *   never a crash).
     * @param candidates the shortlist to verify — already capped by the caller to the top few
     *   on-device-suggested tags; this class also enforces its OWN [MAX_CANDIDATES] cap as
     *   defense-in-depth against a caller passing an unbounded list.
     * @return `ThemeId -> EDHREC synergy weight` for every candidate whose page genuinely lists
     *   [cardName] with a positive weight. Empty when nothing matched or every fetch failed.
     */
    suspend fun confirmThemes(cardName: String, candidates: List<ThemeId>): Map<ThemeId, Float>
}

class EdhrecCardTagEnrichmentSource(
    private val httpClient: HttpClient,
) : EdhrecCardTagEnrichmentSourceContract {

    private val pageCacheMutex = Mutex()
    private val pageCache = mutableMapOf<String, EdhrecThemePageDto?>()

    override suspend fun confirmThemes(cardName: String, candidates: List<ThemeId>): Map<ThemeId, Float> {
        if (cardName.isBlank() || candidates.isEmpty()) return emptyMap()

        val result = mutableMapOf<ThemeId, Float>()
        candidates.distinct().take(MAX_CANDIDATES).forEach { themeId ->
            val slug = EDHREC_SLUG_TO_THEME_ID.entries.firstOrNull { it.value == themeId }?.key ?: return@forEach
            val page = fetchPageCached(slug) ?: return@forEach
            val signal = harvestCardSignals(page).firstOrNull { it.cardName.equals(cardName, ignoreCase = true) }
            if (signal != null && signal.weight > 0f) result[themeId] = signal.weight
        }
        return result
    }

    private suspend fun fetchPageCached(slug: String): EdhrecThemePageDto? = pageCacheMutex.withLock {
        if (pageCache.containsKey(slug)) return@withLock pageCache[slug]
        val page = runCatching {
            httpClient.get("$BASE_URL/pages/tags/$slug.json").body<EdhrecThemePageDto>()
        }.getOrNull()
        pageCache[slug] = page
        page
    }

    companion object {
        const val BASE_URL = "https://json.edhrec.com"

        /** Bounds the per-card network cost — this is a single-card on-device check, never the
         *  pipeline's ~20-theme bulk crawl. 3 covers the top-3 on-device-suggested tags, which is
         *  already generous for a background, best-effort enrichment. */
        const val MAX_CANDIDATES = 3
    }
}
