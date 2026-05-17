package com.mmg.manahub.feature.draft.domain.model

/**
 * Domain model for a set's draft guide, sourced from the Cloudflare Worker.
 * Maps to the new JSON format served at /draft/{setCode}/guide.json.
 *
 * @property setCode Three-letter set code (e.g. "EOE").
 * @property setName Full set name.
 * @property lastUpdated Last update date string.
 * @property summary High-level overview of the draft format.
 * @property colorRanking Ordered list of colors by strength (e.g. ["{G} Green", "{B} Black"]).
 * @property colorNotes Per-color notes keyed by the same string used in colorRanking.
 * @property keyGameplayNotes General gameplay tips for this format.
 * @property mechanics List of mechanics present in the set.
 * @property archetypes List of two-color archetypes, grouped by tier.
 */
data class SetDraftGuide(
    val setCode: String,
    val setName: String,
    val lastUpdated: String,
    val summary: String,
    val colorRanking: List<String>,
    val colorNotes: Map<String, String>,
    val keyGameplayNotes: List<String>,
    val mechanics: List<MechanicGuide>,
    val archetypes: List<ArchetypeGuide>,
)

/**
 * Domain model for a single mechanic present in the set.
 *
 * @property name Mechanic name.
 * @property summary Short explanation of how the mechanic works.
 * @property performance How this mechanic performs in draft.
 * @property keyExamples Cards that over/underperform with this mechanic.
 */
data class MechanicGuide(
    val name: String,
    val summary: String,
    val performance: String,
    val keyExamples: MechanicExamples?,
)

/**
 * A key card within a mechanic's examples, carrying direct image URLs from the JSON.
 * All image-bearing fields default to empty string for cards that omit image_uris.
 *
 * @property name Card name.
 * @property scryfallId Scryfall UUID (empty when the JSON omits the "id" field).
 * @property artCropUri Direct CDN URL for the art crop image (empty when image_uris is absent).
 * @property imageNormalUri Direct CDN URL for the full card image (empty when image_uris is absent).
 * @property note Draft note describing the card's role in the mechanic.
 * @property tierRating Tier rating string (e.g. "A", "C-").
 * @property pickOrderRank Numeric pick-order position (0 when absent).
 * @property color Single-color letter string (e.g. "B", "WR").
 * @property rarity Card rarity string.
 * @property colors List of color identity letters (e.g. ["B"]).
 * @property typeLine Card type line.
 */
data class MechanicKeyCard(
    val name: String,
    val scryfallId: String = "",
    val artCropUri: String = "",
    val imageNormalUri: String = "",
    val note: String = "",
    val tierRating: String = "",
    val pickOrderRank: Int = 0,
    val color: String = "",
    val rarity: String = "",
    val colors: List<String> = emptyList(),
    val typeLine: String = "",
)

/**
 * Key example cards for a mechanic, split by performance.
 *
 * When the JSON provides a flat array (Variant B), all cards are placed in [overperformers]
 * and [underperformers] is empty; callers should treat this as a generic "Key Cards" list.
 *
 * @property overperformers Cards that perform better than expected (also used for flat-array variant).
 * @property underperformers Cards that perform worse than expected.
 */
data class MechanicExamples(
    val overperformers: List<MechanicKeyCard> = emptyList(),
    val underperformers: List<MechanicKeyCard> = emptyList(),
)

/**
 * Domain model for a two-color draft archetype.
 *
 * @property colors Mana symbol string for the archetype's colors (e.g. "{G}{U}").
 * @property name Archetype name (e.g. "Simic Ramp").
 * @property tier Tier label (e.g. "Tier 1 — Best Deck").
 * @property strategy Detailed description of the archetype's game plan.
 * @property difficulty Draft difficulty rating (e.g. "Medium").
 * @property keyCards Key cards for the archetype with full image data.
 */
data class ArchetypeGuide(
    val colors: String,
    val name: String,
    val tier: String,
    val strategy: String,
    val difficulty: String,
    val keyCards: List<ArchetypeKeyCard>,
)

/**
 * A key card within an archetype, carrying direct image URLs from the JSON.
 * No Scryfall API call required to display art crops.
 *
 * @property name Card name.
 * @property scryfallId Scryfall UUID.
 * @property colors List of color identity letters (e.g. ["G", "U"]).
 * @property typeLine Card type line.
 * @property artCropUri Direct CDN URL for the art crop image.
 * @property imageNormalUri Direct CDN URL for the full card image.
 * @property rarity Card rarity string (lowercase).
 */
data class ArchetypeKeyCard(
    val name: String,
    val scryfallId: String,
    val colors: List<String>,
    val typeLine: String,
    val artCropUri: String,
    val imageNormalUri: String,
    val rarity: String,
)
