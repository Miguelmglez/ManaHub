package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-20

/**
 * The standard Magic color-combination display name for a set of [ManaColor]s — Deck Wizard UX
 * polish plan, Run 1 §1.5. Used by the wizard's STRATEGY_PICK combo sheet and review screens instead
 * of a raw `{W}{U}` symbol string.
 */
object ColorCombinationNames {

    private val MONO = mapOf(
        setOf(ManaColor.W) to "Mono-White",
        setOf(ManaColor.U) to "Mono-Blue",
        setOf(ManaColor.B) to "Mono-Black",
        setOf(ManaColor.R) to "Mono-Red",
        setOf(ManaColor.G) to "Mono-Green",
    )

    private val GUILDS = mapOf(
        setOf(ManaColor.W, ManaColor.U) to "Azorius",
        setOf(ManaColor.U, ManaColor.B) to "Dimir",
        setOf(ManaColor.B, ManaColor.R) to "Rakdos",
        setOf(ManaColor.R, ManaColor.G) to "Gruul",
        setOf(ManaColor.G, ManaColor.W) to "Selesnya",
        setOf(ManaColor.W, ManaColor.B) to "Orzhov",
        setOf(ManaColor.U, ManaColor.R) to "Izzet",
        setOf(ManaColor.B, ManaColor.G) to "Golgari",
        setOf(ManaColor.R, ManaColor.W) to "Boros",
        setOf(ManaColor.G, ManaColor.U) to "Simic",
    )

    private val SHARDS_AND_WEDGES = mapOf(
        // Shards (allied colors around a hub).
        setOf(ManaColor.W, ManaColor.U, ManaColor.B) to "Esper",
        setOf(ManaColor.U, ManaColor.B, ManaColor.R) to "Grixis",
        setOf(ManaColor.B, ManaColor.R, ManaColor.G) to "Jund",
        setOf(ManaColor.R, ManaColor.G, ManaColor.W) to "Naya",
        setOf(ManaColor.G, ManaColor.W, ManaColor.U) to "Bant",
        // Wedges (a color plus its two enemies).
        setOf(ManaColor.W, ManaColor.B, ManaColor.G) to "Abzan",
        setOf(ManaColor.U, ManaColor.R, ManaColor.W) to "Jeskai",
        setOf(ManaColor.B, ManaColor.G, ManaColor.U) to "Sultai",
        setOf(ManaColor.R, ManaColor.W, ManaColor.B) to "Mardu",
        setOf(ManaColor.G, ManaColor.U, ManaColor.R) to "Temur",
    )

    private val FOUR_COLOR = mapOf(
        setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R) to "Yore-Tiller",
        setOf(ManaColor.U, ManaColor.B, ManaColor.R, ManaColor.G) to "Glint-Eye",
        setOf(ManaColor.B, ManaColor.R, ManaColor.G, ManaColor.W) to "Dune-Brood",
        setOf(ManaColor.R, ManaColor.G, ManaColor.W, ManaColor.U) to "Ink-Treader",
        setOf(ManaColor.G, ManaColor.W, ManaColor.U, ManaColor.B) to "Witch-Maw",
    )

    /** The display name for [colors] — [ManaColor.C] is ignored when forming the lookup key, so a
     * WUBRG set with an incidental `C` resolves to the SAME name as its WUBRG-only counterpart. */
    fun nameFor(colors: Set<ManaColor>): String {
        val key = colors.filterTo(mutableSetOf()) { it != ManaColor.C }
        return when {
            key.isEmpty() -> "Colorless"
            key.size == 5 -> "Five-Color"
            else -> MONO[key] ?: GUILDS[key] ?: SHARDS_AND_WEDGES[key] ?: FOUR_COLOR[key]
                ?: error("ColorCombinationNames.nameFor: unhandled combination $key")
        }
    }
}
