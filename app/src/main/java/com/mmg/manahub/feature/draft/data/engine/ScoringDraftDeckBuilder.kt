package com.mmg.manahub.feature.draft.data.engine

import com.mmg.manahub.core.model.BASIC_LAND_NAMES
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.draft.data.engine.ScoringDraftDeckBuilder.Companion.BASIC_LANDS_TOTAL
import com.mmg.manahub.core.domain.engine.DraftDeckBuilder
import com.mmg.manahub.core.model.BasicLandSlot
import com.mmg.manahub.core.model.DraftDeck
import com.mmg.manahub.core.model.DraftSeat

/**
 * Builds a 40-card limited deck (23 non-land picks + 17 basic lands) from a finished [DraftSeat].
 *
 * Pipeline:
 * 1. Determine the seat's top-2 colors from [DraftSeat.colorCommitment] (falls back to counting
 *    card colors in the pool when no commitment was recorded, e.g. a human-only sealed pool).
 * 2. Score every non-land card in the pool with [DeckScorer] against those colors and take the
 *    23 highest combined scores.
 * 3. Allocate 17 basic lands across the two colors proportionally to their commitment weights.
 *
 * [buildBasicLands] only computes the proportional per-color COUNT — it does NOT resolve a real
 * Scryfall id (the fake placeholder UUIDs this class used to emit never resolved to a real
 * [com.mmg.manahub.core.model.Card] once persisted). [com.mmg.manahub.core.model.BasicLandSlot.scryfallId]
 * is left empty here; the caller ([com.mmg.manahub.feature.draft.data.DraftSimRepositoryImpl
 * .completeAndSaveDeck]) resolves the real id per basic-land name via `CardRepository` right
 * before persisting.
 *
 * KMP migration — Hilt→Koin cutover batch 3. Plain class (no `@Inject`); built as a native Koin
 * `single` in [com.mmg.manahub.feature.draft.di.draftKoinModule], sharing the SAME [DeckScorer]
 * singleton the Decks island builds (`feature.decks.di.decksKoinModule`).
 */
class ScoringDraftDeckBuilder(
    private val deckScorer: DeckScorer,
) : DraftDeckBuilder {

    override fun build(seat: DraftSeat): DraftDeck {
        val topColorWeights = resolveTopColorWeights(seat)
        val selectedColors = topColorWeights.keys
            .mapNotNull { letter -> ManaColor.values().find { it.symbol == letter } }
            .toSet()

        val nonLands = seat.pool.filterNot {
            it.card.typeLine.contains("Land", ignoreCase = true)
        }

        // Build a lightweight profile for DRAFT format with the selected colors.
        val dummyMainboard = emptyList<DeckEntry>()
        val profile = deckScorer.profile(
            mainboard = dummyMainboard,
            format = DeckFormat.DRAFT,
            colorIdentity = selectedColors,
            seedTags = emptyList(),
        )

        val mainboard = nonLands
            .sortedByDescending { draftCard ->
                deckScorer.fit(draftCard.card, profile, isOwned = true).score
            }
            .take(MAINBOARD_SIZE)

        val basics = buildBasicLands(topColorWeights, BASIC_LANDS_TOTAL)

        return DraftDeck(mainboard = mainboard, basics = basics)
    }

    // ── Color resolution ───────────────────────────────────────────────────────

    /**
     * Returns the top-2 color letters mapped to their (positive) weights. Prefers the seat's
     * accumulated [DraftSeat.colorCommitment]; if that is empty, derives weights by counting the
     * colors of cards in the pool. Always returns at least one entry as long as the pool has any
     * colored card; an entirely colorless pool yields an empty map (no colored lands needed).
     */
    private fun resolveTopColorWeights(seat: DraftSeat): Map<String, Float> {
        val source: Map<String, Float> = if (seat.colorCommitment.isNotEmpty()) {
            seat.colorCommitment
        } else {
            seat.pool
                .flatMap { it.card.colors }
                .groupingBy { it }
                .eachCount()
                .mapValues { it.value.toFloat() }
        }

        return source
            .filter { it.value > 0f }
            .entries
            .sortedByDescending { it.value }
            .take(MAX_DECK_COLORS)
            .associate { it.key to it.value }
    }

    // ── Basic lands ──────────────────────────────────────────────────────────────

    /**
     * Distributes [totalLandCount] lands across the deck's colors proportionally to their
     * weights. Counts are floored then the remainder is handed to the colors with the largest
     * fractional parts so the total always sums to exactly [totalLandCount]. A colorless deck
     * (no weights) defaults to [totalLandCount] Plains so the deck is still playable.
     *
     * Public + parameterized (Phase D) so the Deck tab's "Magic Land Suggestions" autofill can
     * call the SAME distribution math against a user-adjustable target instead of the fixed
     * [BASIC_LANDS_TOTAL] default; [build] just forwards here with that constant.
     */
    override fun buildBasicLands(colorWeights: Map<String, Float>, totalLandCount: Int): List<BasicLandSlot> {
        if (totalLandCount <= 0) return emptyList()

        if (colorWeights.isEmpty()) {
            return listOf(landSlot("W", totalLandCount))
        }

        val totalWeight = colorWeights.values.sum()
        val raw = colorWeights.mapValues { (_, w) -> w / totalWeight * totalLandCount }
        val floored = raw.mapValues { it.value.toInt() }.toMutableMap()
        var assigned = floored.values.sum()

        // Hand out the remaining lands to the largest fractional remainders.
        val remainders = raw.entries
            .sortedByDescending { it.value - it.value.toInt() }
            .map { it.key }
        var i = 0
        while (assigned < totalLandCount && remainders.isNotEmpty()) {
            val color = remainders[i % remainders.size]
            floored[color] = (floored[color] ?: 0) + 1
            assigned++
            i++
        }

        return floored
            .filter { it.value > 0 }
            .map { (color, count) -> landSlot(color, count) }
    }

    /**
     * Builds a [BasicLandSlot] for [colorLetter] with [count] copies. `scryfallId` is
     * intentionally left empty — this class has no Scryfall/network access; the real id is
     * resolved by the caller (see the class KDoc). Never reintroduce a hardcoded/placeholder id
     * here (a prior version did, and those fake UUIDs never resolved to a real persisted card).
     */
    private fun landSlot(colorLetter: String, count: Int): BasicLandSlot {
        val name = BASIC_LAND_NAME_BY_COLOR[colorLetter] ?: BASIC_LAND_NAMES.first()
        return BasicLandSlot(scryfallId = "", name = name, count = count)
    }

    private companion object {
        const val MAINBOARD_SIZE = 23
        const val BASIC_LANDS_TOTAL = 17
        const val MAX_DECK_COLORS = 2

        /** Color letter → basic land name. */
        val BASIC_LAND_NAME_BY_COLOR = mapOf(
            "W" to "Plains",
            "U" to "Island",
            "B" to "Swamp",
            "R" to "Mountain",
            "G" to "Forest",
        )
    }
}
