package com.mmg.manahub.core.model

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
 * @property keyCommonsByColor Best commons grouped by color label (e.g. "{W} White"), schema v2.
 *   Has been emitted in the JSON for a long time but was never modelled until now, so v1 guides
 *   silently carried this data without the app ever showing it. Empty map when unavailable.
 * @property formatSpeed Overall format speed descriptor (e.g. "Fast", "Slow/Grindy"), schema v2,
 *   from `set_overview.format_speed`. Empty when unavailable.
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
    val keyCommonsByColor: Map<String, List<ArchetypeKeyCard>> = emptyMap(),
    val formatSpeed: String = "",
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
 * @property manaCost Mana cost string (e.g. "{2}{U}{U}"), schema v2. Empty when unavailable.
 * @property cmc Converted mana cost, schema v2. Null when unavailable.
 * @property colorIdentity Color identity letters, schema v2. Empty when unavailable.
 * @property sourceSet Scryfall set code this card was sourced from, schema v2. Empty when unavailable.
 * @property stats Aggregate 17Lands performance stats, schema v2. Null when unavailable.
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
    val manaCost: String = "",
    val cmc: Double? = null,
    val colorIdentity: List<String> = emptyList(),
    val sourceSet: String = "",
    val stats: DraftCardStats? = null,
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
 * @property colorLetters Color letters for this archetype (e.g. ["B", "G"]), schema v2. Preferred
 *   over parsing [colors] when non-empty; callers should fall back to parsing [colors] for v1 guides.
 * @property signpostCards Signpost/build-around cards for this archetype, schema v2. Empty when
 *   unavailable.
 * @property cardsToAvoid Cards that underperform in this archetype, schema v2. Empty when unavailable.
 * @property archetypeWinRate Aggregate 17Lands win rate for this archetype (0.0-1.0), schema v2.
 *   Null when unavailable.
 * @property archetypeGames Sample size backing [archetypeWinRate], schema v2. Null when unavailable.
 * @property notes Additional freeform notes about this archetype, schema v2. Empty when unavailable.
 */
data class ArchetypeGuide(
    val colors: String,
    val name: String,
    val tier: String,
    val strategy: String,
    val difficulty: String,
    val keyCards: List<ArchetypeKeyCard>,
    val colorLetters: List<String> = emptyList(),
    val signpostCards: List<ArchetypeKeyCard> = emptyList(),
    val cardsToAvoid: List<ArchetypeKeyCard> = emptyList(),
    val archetypeWinRate: Double? = null,
    val archetypeGames: Int? = null,
    val notes: String = "",
)

/**
 * A card object within a guide's archetype section (key cards, signpost cards, cards to avoid) or
 * the guide root's key-commons-by-color map, carrying direct image URLs from the JSON.
 * No Scryfall API call required to display art crops.
 *
 * @property name Card name.
 * @property scryfallId Scryfall UUID.
 * @property colors List of color identity letters (e.g. ["G", "U"]).
 * @property typeLine Card type line.
 * @property artCropUri Direct CDN URL for the art crop image.
 * @property imageNormalUri Direct CDN URL for the full card image.
 * @property rarity Card rarity string (lowercase).
 * @property manaCost Mana cost string (e.g. "{2}{U}{U}"), schema v2. Empty when unavailable.
 * @property cmc Converted mana cost, schema v2. Null when unavailable.
 * @property colorIdentity Color identity letters, schema v2. Empty when unavailable.
 * @property sourceSet Scryfall set code this card was sourced from, schema v2. Empty when unavailable.
 * @property tierRating Tier rating string, schema v2. Empty when unavailable.
 * @property pickOrderRank Numeric pick-order position, schema v2. 0 when absent.
 * @property stats Aggregate 17Lands performance stats, schema v2. Null when unavailable.
 */
data class ArchetypeKeyCard(
    val name: String,
    val scryfallId: String,
    val colors: List<String>,
    val typeLine: String,
    val artCropUri: String,
    val imageNormalUri: String,
    val rarity: String,
    val manaCost: String = "",
    val cmc: Double? = null,
    val colorIdentity: List<String> = emptyList(),
    val sourceSet: String = "",
    val tierRating: String = "",
    val pickOrderRank: Int = 0,
    val stats: DraftCardStats? = null,
)
