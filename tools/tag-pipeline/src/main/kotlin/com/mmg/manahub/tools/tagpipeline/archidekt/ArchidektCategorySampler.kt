package com.mmg.manahub.tools.tagpipeline.archidekt

import com.mmg.manahub.core.data.remote.ArchidektClient
import com.mmg.manahub.core.data.network.ArchidektRequestQueue
import com.mmg.manahub.core.model.CommunityDeckSearchFilters

/**
 * Bulk-friendly, best-effort Archidekt "default category" enrichment (secondary signal — see
 * [ARCHIDEKT_CATEGORY_TO_CARD_TAG]'s KDoc for why this can never be a primary/required source).
 *
 * There is no per-card category lookup, so this samples a bounded number of PUBLIC DECKS
 * (`GET /api/decks/{id}/`, which already returns every card's categories in one response — see
 * [ArchidektClient.getDeckById]) and aggregates `oracle_id -> (mapped CardTag key -> observation
 * count)`, then picks the majority category per card once it clears a minimum-confidence bar
 * ([resolveDominantCategories]). Reuses the app's EXISTING [ArchidektClient]/[ArchidektRequestQueue]
 * verbatim (same DTOs, same 429/503 retry/back-off/jitter policy) rather than a second hand-rolled
 * client — see `build.gradle.kts`'s KDoc for why Ktor was added to this module specifically for this.
 *
 * Every deck fetch is fallible-by-design (a single deck 404/500/parse-failure just contributes zero
 * observations, never aborts the sampling run) — same posture as [com.mmg.manahub.tools.tagpipeline
 * .edhrec.EdhrecThemeClient].
 */
class ArchidektCategorySampler(
    private val client: ArchidektClient,
    private val queue: ArchidektRequestQueue = ArchidektRequestQueue(),
) {

    /**
     * Pages through `GET /api/decks/v3/` collecting up to [targetCount] distinct deck ids, cycling
     * across a few `orderBy` variants ([ORDER_BY_VARIANTS]) for a broader, less popularity-biased
     * sample than a single ordering would give (still real public decks only — no format/color
     * filter is applied, so the sample spans Commander/60-card/etc. roughly as Archidekt users do).
     * Archidekt's own documented `pageSize` unreliability ("small values return MORE rows, values
     * > 60 are capped" — [CommunityDeckSearchFilters]'s KDoc) means this always requests the
     * practical cap ([PAGE_SIZE]) and stops early if a page comes back short/empty (exhausted).
     */
    suspend fun collectDeckIds(targetCount: Int): List<Int> {
        if (targetCount <= 0) return emptyList()
        val ids = LinkedHashSet<Int>()
        var variantIndex = 0
        var page = 1
        // Counts consecutive pages that added ZERO new ids — not just literally-empty pages. A page
        // can come back non-empty but entirely made of ids already collected (a saturated/looping
        // feed, or a test double returning a fixed page) — without tracking actual progress this
        // loop would never terminate. Once every ordering variant in a row makes no progress, the
        // sample is exhausted for this run's purposes.
        var noProgressStreak = 0
        while (ids.size < targetCount && noProgressStreak < ORDER_BY_VARIANTS.size) {
            val orderBy = ORDER_BY_VARIANTS[variantIndex % ORDER_BY_VARIANTS.size]
            val filters = CommunityDeckSearchFilters(orderBy = orderBy, page = page, pageSize = PAGE_SIZE)
            val sizeBefore = ids.size
            val batch = try {
                queue.execute { client.searchDecks(filters) }.results
            } catch (e: Exception) {
                System.err.println("[tag-pipeline] Archidekt deck search page $page ($orderBy) failed, skipping: ${e.message}")
                emptyList()
            }
            batch.forEach { ids.add(it.id) }
            val madeProgress = ids.size > sizeBefore
            if (!madeProgress) {
                noProgressStreak++
                variantIndex++
                page = 1
            } else {
                noProgressStreak = 0
                page++
                // Rotate variant every few pages so no single ordering dominates the sample once its
                // "recent"/"popular" head has already been fully consumed by earlier iterations.
                if (page > PAGES_PER_VARIANT) {
                    variantIndex++
                    page = 1
                }
            }
        }
        return ids.take(targetCount).toList()
    }

    /**
     * Fetches every deck in [deckIds] and tallies `oracle_id -> (CardTag key -> observation count)`,
     * counting ONLY categories that resolve via [mapArchidektCategoryToCardTag] — raw type-echo/
     * custom-junk category strings never enter the aggregate at all, so [resolveDominantCategories]'s
     * share threshold is computed purely over signal, not diluted by noise.
     */
    suspend fun harvestCategoryObservations(deckIds: List<Int>): Map<String, MutableMap<String, Int>> {
        val observations = HashMap<String, MutableMap<String, Int>>()
        var fetched = 0
        var failed = 0
        deckIds.forEach { id ->
            val deck = try {
                queue.execute { client.getDeckById(id) }
            } catch (e: Exception) {
                failed++
                null
            }
            if (deck == null) return@forEach
            fetched++
            deck.cards.forEach { entry ->
                val oracleId = entry.card?.oracleCard?.uid?.takeIf { it.isNotBlank() } ?: return@forEach
                entry.categories.orEmpty().forEach { rawCategory ->
                    val tagKey = mapArchidektCategoryToCardTag(rawCategory) ?: return@forEach
                    val byTag = observations.getOrPut(oracleId) { mutableMapOf() }
                    byTag[tagKey] = (byTag[tagKey] ?: 0) + 1
                }
            }
        }
        System.err.println(
            "[tag-pipeline] Archidekt sample: $fetched deck(s) fetched, $failed failed, " +
                "${observations.size} oracle_id(s) with at least one mapped category observation",
        )
        return observations
    }

    companion object {
        private const val PAGE_SIZE = 60
        private const val PAGES_PER_VARIANT = 25
        private val ORDER_BY_VARIANTS = listOf("-viewCount", "-createdAt", "-updatedAt")
    }
}

/**
 * Reduces [observations] (`oracle_id -> tagKey -> count`) to `oracle_id -> tagKey` for cards that
 * clear BOTH a minimum absolute observation count ([minObservations]) and a minimum majority share
 * ([minShare]) — a single outlier deck's custom labeling of a card is never enough to assign it a
 * category (see [ArchidektCategoryMapping]'s "Pingers" example — a real, single-deck custom category
 * that must never leak in as if it were an Archidekt default category).
 */
fun resolveDominantCategories(
    observations: Map<String, Map<String, Int>>,
    minObservations: Int,
    minShare: Float,
): Map<String, String> = buildMap {
    observations.forEach { (oracleId, byTag) ->
        val total = byTag.values.sum()
        if (total < minObservations) return@forEach
        val (topTag, topCount) = byTag.maxByOrNull { it.value } ?: return@forEach
        if (topCount.toFloat() / total >= minShare) {
            put(oracleId, topTag)
        }
    }
}
