package com.mmg.manahub.feature.draft.data.engine

import com.mmg.manahub.core.domain.model.BASIC_LAND_NAMES
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.feature.decks.presentation.engine.DeckEntry
import com.mmg.manahub.feature.decks.presentation.engine.DeckScorer
import com.mmg.manahub.feature.decks.presentation.engine.ManaColor
import com.mmg.manahub.feature.draft.domain.engine.DraftDeckBuilder
import com.mmg.manahub.feature.draft.domain.model.BasicLandSlot
import com.mmg.manahub.feature.draft.domain.model.DraftCard
import com.mmg.manahub.feature.draft.domain.model.DraftDeck
import com.mmg.manahub.feature.draft.domain.model.DraftSeat
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Builds a 40-card limited deck (23 non-land picks + 17 basic lands) from a finished [DraftSeat].
 *
 * Pipeline:
 * 1. Determine the seat's top-2 colors from [DraftSeat.colorCommitment] (falls back to counting
 *    card colors in the pool when no commitment was recorded, e.g. a human-only sealed pool).
 * 2. Score every non-land card in the pool with [DeckScorer] against those colors and take the
 *    23 highest combined scores.
 * 3. Allocate 17 basic lands across the two colors proportionally to their commitment weights.
 */
class ScoringDraftDeckBuilder @Inject constructor(
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

        val basics = buildBasicLands(topColorWeights)

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
     * Distributes [BASIC_LANDS_TOTAL] lands across the deck's colors proportionally to their
     * weights. Counts are floored then the remainder is handed to the colors with the largest
     * fractional parts so the total always sums to exactly [BASIC_LANDS_TOTAL]. A colorless deck
     * (no weights) defaults to 17 Plains so the deck is still playable.
     */
    private fun buildBasicLands(colorWeights: Map<String, Float>): List<BasicLandSlot> {
        if (colorWeights.isEmpty()) {
            return listOf(landSlot("W", BASIC_LANDS_TOTAL))
        }

        val totalWeight = colorWeights.values.sum()
        val raw = colorWeights.mapValues { (_, w) -> w / totalWeight * BASIC_LANDS_TOTAL }
        val floored = raw.mapValues { it.value.toInt() }.toMutableMap()
        var assigned = floored.values.sum()

        // Hand out the remaining lands to the largest fractional remainders.
        val remainders = raw.entries
            .sortedByDescending { it.value - it.value.toInt() }
            .map { it.key }
        var i = 0
        while (assigned < BASIC_LANDS_TOTAL && remainders.isNotEmpty()) {
            val color = remainders[i % remainders.size]
            floored[color] = (floored[color] ?: 0) + 1
            assigned++
            i++
        }

        return floored
            .filter { it.value > 0 }
            .map { (color, count) -> landSlot(color, count) }
    }

    private fun landSlot(colorLetter: String, count: Int): BasicLandSlot {
        val name = BASIC_LAND_NAME_BY_COLOR[colorLetter] ?: BASIC_LAND_NAMES.first()
        val scryfallId = BASIC_LAND_ID_BY_NAME[name] ?: ""
        return BasicLandSlot(scryfallId = scryfallId, name = name, count = count)
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

        /**
         * Placeholder Scryfall IDs for the five basic lands. P4 will replace these with a proper
         * set-aware lookup; P6 will verify them. Do not rely on these resolving to a specific
         * printing.
         */
        val BASIC_LAND_ID_BY_NAME = mapOf(
            "Plains" to "c7a4b9b5-1f85-4e15-b70d-8aa57ea95b8c",
            "Island" to "9c4f0cca-3b77-4b0f-9e77-19b200e86da6",
            "Swamp" to "a77e84c8-87c7-48ca-85a8-0bd77f5a9d85",
            "Mountain" to "d2228f27-f0d2-4c4e-a1f1-6a52a2e3e8f8",
            "Forest" to "1f16e66f-e0c9-48f5-baef-cb6a4ff8c2b4",
        )
    }
}
