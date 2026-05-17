package com.mmg.manahub.feature.draft.domain.model

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
 * @property colors List of individual color letters (e.g. ["B", "R"]).
 * @property rarity Card rarity string.
 * @property pickOrderRank Relative pick order within the set (1 = first pick).
 * @property tierRating Tier letter for this card (e.g. "S").
 * @property note Evaluator's note about this card.
 * @property artCropUri Direct CDN URL for the art crop image.
 * @property imageNormalUri Direct CDN URL for the full card image.
 * @property typeLine Card type line.
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
)
