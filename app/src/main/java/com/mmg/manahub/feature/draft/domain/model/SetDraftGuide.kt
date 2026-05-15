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
 * Key example cards for a mechanic, split by performance.
 *
 * @property overperformers Cards that perform better than expected.
 * @property underperformers Cards that perform worse than expected.
 */
data class MechanicExamples(
    val overperformers: List<String>,
    val underperformers: List<String>,
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
