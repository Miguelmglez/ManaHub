package com.mmg.manahub.feature.decks.domain.engine

/**
 * Deck Engine Unification plan (`docs/plans/deck-engine-unification-plan.md`, D9) — Flow B's
 * (colors-first) affinity signal: given a color combination, which [ArchetypeId]/[ThemeId]
 * strategies are commonly viable in that identity.
 *
 * **THIS IS A HAND-CURATED v1 PLACEHOLDER, NOT PIPELINE OUTPUT.** Phase 5a (`docs/plans
 * /deck-engine-unification-plan.md` §5) will regenerate this table from real EDHREC theme/color
 * aggregate data gathered by the offline tag pipeline (emitted as generated Kotlin or a bundled
 * resource — see D9's KDoc in the plan). The entries below are seeded from general constructed-Magic
 * archetype convention (mono-color identity leans, the 10 two-color guilds, the 5 three-color
 * wedges) — a reasonable v1 ranking, not an authoritative one. Do not treat [weight] as anything more
 * precise than a rough ordering signal.
 *
 * Coverage: 5 mono colors + 10 two-color guild pairs + 5 three-color wedges (the combinations a
 * colors-first picker is most likely to land on). An unlisted combination (an unlisted wedge/shard
 * orientation, 4-color, 5-color, or colorless) falls back to [FALLBACK] — a conservative, broadly
 * applicable MIDRANGE/CONTROL pair — so Flow B never dead-ends with an empty suggestion list on an
 * exotic color pick.
 */
data class ColorStrategyEntry(
    val archetype: ArchetypeId? = null,
    val themes: List<ThemeId> = emptyList(),
    /** Rough viability ranking within this color combo, 0f..1f. Curated by hand (see class KDoc) —
     * not a statistically derived score. */
    val weight: Float,
) {
    /** Display label for this entry — the archetype name when set, else the first theme's name. */
    val label: String
        get() = archetype?.takeIf { it != ArchetypeId.GENERIC }?.displayName
            ?: themes.firstOrNull()?.displayName
            ?: archetype?.displayName
            ?: "Balanced"

    /** Resolves this entry into a pickable [StrategyProfile] (colors filled in by the caller, which
     * knows the color combo this entry came from). */
    fun toStrategyProfile(colors: Set<ManaColor>): StrategyProfile =
        StrategyProfile(archetype = archetype, themes = themes, colors = colors)
}

object ColorStrategyAffinity {

    /** Ranked (best-first) strategy entries for [colors] (colorless symbol ignored). Falls back to
     * [FALLBACK] for any combination not in the curated table (see class KDoc). */
    fun forColors(colors: Set<ManaColor>): List<ColorStrategyEntry> {
        val key = colors.filterTo(mutableSetOf()) { it != ManaColor.C }
        return TABLE[key] ?: FALLBACK
    }

    /**
     * Reverse lookup for Flow C (strategy-first, plan §5 3.4): every color combo in the curated
     * table whose entry list contains [archetype] (when non-null/non-GENERIC) or [theme]
     * (when non-null), paired with that entry's own [ColorStrategyEntry.weight]. Sorted best-first.
     * Both null returns an empty list (nothing to rank against).
     */
    fun combosFor(archetype: ArchetypeId?, theme: ThemeId?): List<Pair<Set<ManaColor>, Float>> {
        if ((archetype == null || archetype == ArchetypeId.GENERIC) && theme == null) return emptyList()
        return TABLE.entries
            .mapNotNull { (colors, entries) ->
                val match = entries.firstOrNull { entry ->
                    (archetype != null && archetype != ArchetypeId.GENERIC && entry.archetype == archetype) ||
                        (theme != null && theme in entry.themes)
                }
                match?.let { colors to it.weight }
            }
            .sortedByDescending { it.second }
    }

    private val FALLBACK = listOf(
        ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.6f),
        ColorStrategyEntry(archetype = ArchetypeId.CONTROL, weight = 0.5f),
    )

    private val TABLE: Map<Set<ManaColor>, List<ColorStrategyEntry>> = buildMap {
        // ── Mono colors (5) ─────────────────────────────────────────────────────────────────
        put(setOf(ManaColor.W), listOf(
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, weight = 0.9f),
            ColorStrategyEntry(themes = listOf(ThemeId.LIFEGAIN), weight = 0.7f),
            ColorStrategyEntry(themes = listOf(ThemeId.TOKENS), weight = 0.6f),
        ))
        put(setOf(ManaColor.U), listOf(
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, weight = 0.9f),
            ColorStrategyEntry(themes = listOf(ThemeId.SPELLSLINGER), weight = 0.6f),
            ColorStrategyEntry(themes = listOf(ThemeId.MILL), weight = 0.5f),
        ))
        put(setOf(ManaColor.B), listOf(
            ColorStrategyEntry(themes = listOf(ThemeId.REANIMATOR), weight = 0.8f),
            ColorStrategyEntry(themes = listOf(ThemeId.ARISTOCRATS), weight = 0.75f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.6f),
        ))
        put(setOf(ManaColor.R), listOf(
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, weight = 0.9f),
            ColorStrategyEntry(themes = listOf(ThemeId.SPELLSLINGER), weight = 0.7f),
            ColorStrategyEntry(archetype = ArchetypeId.COMBO, weight = 0.4f),
        ))
        put(setOf(ManaColor.G), listOf(
            ColorStrategyEntry(archetype = ArchetypeId.RAMP, weight = 0.9f),
            ColorStrategyEntry(themes = listOf(ThemeId.PLUS1_COUNTERS), weight = 0.6f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.55f),
        ))
        // ── Two-color guilds (10) ───────────────────────────────────────────────────────────
        put(setOf(ManaColor.W, ManaColor.U), listOf( // Azorius
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, weight = 0.9f),
            ColorStrategyEntry(themes = listOf(ThemeId.BLINK), weight = 0.6f),
            ColorStrategyEntry(archetype = ArchetypeId.TEMPO, weight = 0.5f),
        ))
        put(setOf(ManaColor.U, ManaColor.B), listOf( // Dimir
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, weight = 0.75f),
            ColorStrategyEntry(themes = listOf(ThemeId.MILL), weight = 0.7f),
            ColorStrategyEntry(themes = listOf(ThemeId.SELF_MILL), weight = 0.5f),
        ))
        put(setOf(ManaColor.B, ManaColor.R), listOf( // Rakdos
            ColorStrategyEntry(themes = listOf(ThemeId.ARISTOCRATS), weight = 0.85f),
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, weight = 0.7f),
            ColorStrategyEntry(themes = listOf(ThemeId.SPELLSLINGER), weight = 0.5f),
        ))
        put(setOf(ManaColor.R, ManaColor.G), listOf( // Gruul
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, weight = 0.8f),
            ColorStrategyEntry(themes = listOf(ThemeId.PLUS1_COUNTERS), weight = 0.65f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.6f),
        ))
        put(setOf(ManaColor.G, ManaColor.W), listOf( // Selesnya
            ColorStrategyEntry(themes = listOf(ThemeId.TOKENS), weight = 0.8f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.65f),
            ColorStrategyEntry(themes = listOf(ThemeId.PLUS1_COUNTERS), weight = 0.5f),
        ))
        put(setOf(ManaColor.W, ManaColor.B), listOf( // Orzhov
            ColorStrategyEntry(themes = listOf(ThemeId.ARISTOCRATS), weight = 0.8f),
            ColorStrategyEntry(themes = listOf(ThemeId.LIFEGAIN), weight = 0.7f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.5f),
        ))
        put(setOf(ManaColor.U, ManaColor.R), listOf( // Izzet
            ColorStrategyEntry(themes = listOf(ThemeId.SPELLSLINGER), weight = 0.9f),
            ColorStrategyEntry(archetype = ArchetypeId.TEMPO, weight = 0.6f),
            ColorStrategyEntry(archetype = ArchetypeId.COMBO, weight = 0.5f),
        ))
        put(setOf(ManaColor.B, ManaColor.G), listOf( // Golgari
            ColorStrategyEntry(themes = listOf(ThemeId.REANIMATOR), weight = 0.75f),
            ColorStrategyEntry(themes = listOf(ThemeId.SELF_MILL), weight = 0.6f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.65f),
        ))
        put(setOf(ManaColor.R, ManaColor.W), listOf( // Boros
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, weight = 0.9f),
            ColorStrategyEntry(themes = listOf(ThemeId.TOKENS), weight = 0.6f),
            ColorStrategyEntry(archetype = ArchetypeId.TEMPO, weight = 0.55f),
        ))
        put(setOf(ManaColor.G, ManaColor.U), listOf( // Simic
            ColorStrategyEntry(archetype = ArchetypeId.RAMP, weight = 0.8f),
            ColorStrategyEntry(themes = listOf(ThemeId.PLUS1_COUNTERS), weight = 0.65f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.55f),
        ))
        // ── Three-color wedges (5) -- the most commonly built 3-color combos ────────────────
        put(setOf(ManaColor.W, ManaColor.B, ManaColor.R), listOf( // Mardu
            ColorStrategyEntry(themes = listOf(ThemeId.ARISTOCRATS), weight = 0.8f),
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, weight = 0.7f),
        ))
        put(setOf(ManaColor.U, ManaColor.R, ManaColor.G), listOf( // Temur
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.7f),
            ColorStrategyEntry(archetype = ArchetypeId.RAMP, weight = 0.65f),
        ))
        put(setOf(ManaColor.W, ManaColor.B, ManaColor.G), listOf( // Abzan
            ColorStrategyEntry(themes = listOf(ThemeId.PLUS1_COUNTERS), weight = 0.75f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.7f),
        ))
        put(setOf(ManaColor.W, ManaColor.U, ManaColor.R), listOf( // Jeskai
            ColorStrategyEntry(themes = listOf(ThemeId.SPELLSLINGER), weight = 0.8f),
            ColorStrategyEntry(archetype = ArchetypeId.TEMPO, weight = 0.6f),
        ))
        put(setOf(ManaColor.U, ManaColor.B, ManaColor.G), listOf( // Sultai
            ColorStrategyEntry(themes = listOf(ThemeId.SELF_MILL), weight = 0.75f),
            ColorStrategyEntry(themes = listOf(ThemeId.REANIMATOR), weight = 0.65f),
        ))
    }
}
