package com.mmg.manahub.core.model

/**
 * Card condition grades used to filter a friend's collection.
 *
 * @property label Short display label matching the server-side condition descriptor
 *   (e.g. "NM", "LP", "MP", "HP", "DMG").
 */
enum class Condition(val label: String) {
    NM("NM"), LP("LP"), MP("MP"), HP("HP"), DMG("DMG")
}

/**
 * Filter criteria applied when browsing a friend's card list.
 *
 * All fields default to their "no filter" state — empty sets and `false`.
 *
 * @property sets      Set codes to restrict results to.
 * @property rarities  Rarity filter values.
 * @property colors    MTG colour identity filter values.
 * @property foilOnly  When `true`, only foil entries are returned.
 * @property conditions Condition grade filter values.
 * @property languages Language code filter values (e.g. "en", "ja").
 */
data class FolderFilters(
    val sets: Set<String> = emptySet(),
    val rarities: Set<Rarity> = emptySet(),
    val colors: Set<MtgColor> = emptySet(),
    val foilOnly: Boolean = false,
    val conditions: Set<Condition> = emptySet(),
    val languages: Set<String> = emptySet()
) {
    /** `true` when at least one filter criterion is active. */
    val hasFilters: Boolean
        get() = sets.isNotEmpty() || rarities.isNotEmpty() || colors.isNotEmpty() || foilOnly || conditions.isNotEmpty() || languages.isNotEmpty()
}
