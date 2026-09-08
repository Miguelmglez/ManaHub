package com.mmg.manahub.core.model

/**
 * Domain model for a set's tier list, sourced from the Cloudflare Worker.
 * Maps to the new JSON format served at /draft/{setCode}/tier-list.json.
 *
 * @property setCode Three-letter set code (e.g. "EOE").
 * @property setName Full set name.
 * @property lastUpdated Last update date string.
 * @property tierKey Map describing what each tier label means (S, A, B, C, D, F).
 * @property tiers Ordered list of tier groups, from best (S) to worst (F).
 */
data class SetTierList(
    val setCode: String,
    val setName: String,
    val lastUpdated: String,
    val tierKey: Map<String, String>,
    val tiers: List<TierGroup>,
)

/**
 * A group of cards sharing the same tier rating.
 *
 * @property tier Tier letter (e.g. "S", "A").
 * @property label Priority label (e.g. "Bombs").
 * @property description Description of what qualifies for this tier.
 * @property cards Cards in this tier, ordered by [TierCard.pickOrderRank].
 */
data class TierGroup(
    val tier: String,
    val label: String,
    val description: String,
    val cards: List<TierCard>,
)

/**
 * A single card entry in the tier list, carrying direct image URLs from the JSON.
 * No Scryfall API call needed to display art crops.
 *
 * @property name Card name.
 * @property scryfallId Scryfall UUID (from "id" field in JSON).
 * @property color Combined color string for compact display (e.g. "BR").
 * @property colors List of individual color letters (e.g. ["B", "R"]). Scryfall-accurate.
 * @property rarity Card rarity string. Scryfall-accurate.
 * @property pickOrderRank Relative pick order within the set (1 = first pick).
 * @property tierRating Tier letter for this card (e.g. "S").
 * @property note Evaluator's note about this card.
 * @property artCropUri Direct CDN URL for the art crop image.
 * @property imageNormalUri Direct CDN URL for the full card image.
 * @property typeLine Card type line.
 * @property manaCost Mana cost string (e.g. "{2}{U}{U}"), schema v2. Empty for lands, DFC backs,
 *   and v1 guides.
 * @property cmc Converted mana cost, schema v2. Null when unavailable (v1 guides).
 * @property oracleText Oracle rules text, schema v2. Empty when unavailable.
 * @property colorIdentity Color identity letters, schema v2. Empty when unavailable.
 * @property sourceSet Scryfall set code this card was sourced from, schema v2 (relevant for
 *   cross-product pools). Empty when unavailable.
 * @property ratingConfidence Confidence label for this card's rating (e.g. "high"), schema v2.
 *   Empty when unavailable.
 * @property ratingSources List of sources backing this rating, schema v2. Empty when unavailable.
 * @property stats Aggregate 17Lands performance stats, schema v2. Null when unavailable.
 * @property inBoosters Whether this card is actually opened in boosters, schema v2. Null when
 *   unavailable (v1 guides never distinguished this).
 */
data class TierCard(
    val name: String,
    val scryfallId: String,
    val color: String,
    val colors: List<String>,
    val rarity: String,
    val pickOrderRank: Int,
    val tierRating: String,
    val note: String,
    val artCropUri: String,
    val imageNormalUri: String,
    val typeLine: String,
    val manaCost: String = "",
    val cmc: Double? = null,
    val oracleText: String = "",
    val colorIdentity: List<String> = emptyList(),
    val sourceSet: String = "",
    val ratingConfidence: String = "",
    val ratingSources: List<String> = emptyList(),
    val stats: DraftCardStats? = null,
    val inBoosters: Boolean? = null,
)

/**
 * Aggregate 17Lands performance stats for a single card, schema v2 (optional, additive).
 *
 * @property gihWinRate Games-in-hand win rate (0.0-1.0), null when unavailable.
 * @property gihGames Sample size backing [gihWinRate], null when unavailable.
 * @property iwd Improvement-when-drawn delta, null when unavailable.
 */
data class DraftCardStats(
    val gihWinRate: Double? = null,
    val gihGames: Int? = null,
    val iwd: Double? = null,
)
