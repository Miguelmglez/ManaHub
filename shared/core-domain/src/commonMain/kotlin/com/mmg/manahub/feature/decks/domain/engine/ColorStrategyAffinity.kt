package com.mmg.manahub.feature.decks.domain.engine

/**
 * Deck Engine Unification plan (`docs/plans/deck-engine-unification-plan.md`, D9) — Flow B's
 * (colors-first) affinity signal: given a color combination, which [ArchetypeId]/[ThemeId]
 * strategies are commonly viable in that identity.
 *
 * **v2 (Deck Wizard & Engine Rework plan, `docs/plans/deck-wizard-rework-plan.md`, WS 1.4) —
 * FULL COVERAGE.** Replaces the v1 hand-curated placeholder (20 combos, ~2-3 entries each,
 * explicitly flagged in its own KDoc as "not authoritative") with a curated table covering EVERY
 * one of the 31 non-colorless subsets of {W,U,B,R,G}: 5 mono + 10 two-color guilds + 10 three-color
 * combos (5 shards + 5 wedges) + 5 four-color combos + 1 five-color (WUBRG). [FALLBACK] now fires
 * ONLY for the genuinely-colorless input (`{}`/`{C}` after filtering) -- every real color pick has
 * a real entry.
 *
 * Weights are a curated ranking informed by general Magic color-pie/constructed-archetype
 * convention (mono/guild colors' documented mechanical identities -- see each `put()` block's own
 * rationale comments) and general EDHREC popularity-by-color-identity convention, NOT a
 * statistically regenerated pipeline output -- that regeneration (aggregating real EDHREC
 * color-identity popularity data through the offline tag pipeline) stays documented future work,
 * per the plan's own scope note (WS 1.4: "the offline-pipeline regeneration stays future work, but
 * the table stops being wrong"). Every entry's (archetype, themes) pair is validated against
 * [StrategyCatalog.isValidCombination] (WS 1.1) -- see `ColorStrategyAffinityTest`.
 */
data class ColorStrategyEntry(
    val archetype: ArchetypeId? = null,
    /** Deck Analysis Engine v3 compat shim (2026-08-26): `RAMP`/`TEMPO` moved from [ArchetypeId] to
     * [PostureId] -- every entry below that used to declare `archetype = ArchetypeId.RAMP`/`TEMPO`
     * alone now declares a real macro (`MIDRANGE`/`AGGRO`, the posture's own default overlay per
     * spec §2/§3) PLUS this field, so the player-facing [label] can still read "Ramp"/"Tempo".
     * Pure display metadata here -- NOT threaded into [StrategyProfile]/[toStrategyProfile] (the
     * Wizard flow's own posture support is a follow-up, out of Phase 3a's scope). */
    val posture: PostureId? = null,
    val themes: List<ThemeId> = emptyList(),
    /** Rough viability ranking within this color combo, 0f..1f. Curated by hand (see class KDoc) —
     * not a statistically derived score. */
    val weight: Float,
) {
    /** Display label for this entry — [posture] (the more specific ex-macro/ex-theme identity)
     * when set, else the archetype name, else the first theme's name. */
    val label: String
        get() = posture?.displayName
            ?: archetype?.displayName
            ?: themes.firstOrNull()?.displayName
            ?: "Balanced"

    /** Resolves this entry into a pickable [StrategyProfile] (colors filled in by the caller, which
     * knows the color combo this entry came from). */
    fun toStrategyProfile(colors: Set<ManaColor>): StrategyProfile =
        StrategyProfile(archetype = archetype, themes = themes, colors = colors)
}

object ColorStrategyAffinity {

    /** Ranked (best-first) strategy entries for [colors] (colorless symbol ignored). Falls back to
     * [FALLBACK] only for the genuinely-colorless combination (see class KDoc) — every real color
     * pick (1-5 colors) has a real curated entry as of v2. */
    fun forColors(colors: Set<ManaColor>): List<ColorStrategyEntry> {
        val key = colors.filterTo(mutableSetOf()) { it != ManaColor.C }
        return TABLE[key] ?: FALLBACK
    }

    /**
     * Reverse lookup for Flow C (strategy-first, plan §5 3.4): every color combo in the curated
     * table whose entry list contains [archetype] (when non-null -- Deck Analysis Engine v3
     * removed `ArchetypeId.GENERIC`, so any real value now qualifies) or [theme] (when non-null),
     * paired with that entry's own [ColorStrategyEntry.weight]. Sorted best-first. Both null
     * returns an empty list (nothing to rank against).
     */
    fun combosFor(archetype: ArchetypeId?, theme: ThemeId?): List<Pair<Set<ManaColor>, Float>> {
        if (archetype == null && theme == null) return emptyList()
        return TABLE.entries
            .mapNotNull { (colors, entries) ->
                val match = entries.firstOrNull { entry ->
                    (archetype != null && entry.archetype == archetype) ||
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
        // ── Mono colors (5) -- each color's single-color mechanical identity is the strongest,
        //    least ambiguous signal in the whole table (color-pie primary function). ─────────────
        put(setOf(ManaColor.W), listOf(
            // White's primary function is efficient small creatures + combat tricks (AGGRO); its
            // secondary strengths are lifegain and go-wide token production.
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, weight = 0.9f),
            ColorStrategyEntry(themes = listOf(ThemeId.LIFEGAIN), weight = 0.7f),
            ColorStrategyEntry(themes = listOf(ThemeId.TOKENS), weight = 0.6f),
        ))
        put(setOf(ManaColor.U), listOf(
            // Blue's primary function is card draw + counterspells (the archetypal CONTROL color);
            // instant/sorcery density and mill are its well-known secondary identities.
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, weight = 0.9f),
            ColorStrategyEntry(themes = listOf(ThemeId.SPELLSLINGER), weight = 0.6f),
            ColorStrategyEntry(themes = listOf(ThemeId.MILL_OPPONENT), weight = 0.5f),
        ))
        put(setOf(ManaColor.B), listOf(
            // Black's primary functions are graveyard recursion/reanimation and sacrifice-for-value
            // -- mono-black has no dedicated macro-archetype the way W/U/R/G do, so its two
            // strongest themes rank above a generic MIDRANGE fallback.
            ColorStrategyEntry(themes = listOf(ThemeId.REANIMATOR), weight = 0.8f),
            ColorStrategyEntry(themes = listOf(ThemeId.ARISTOCRATS), weight = 0.75f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.6f),
        ))
        put(setOf(ManaColor.R), listOf(
            // Red's primary function is the fastest, most efficient damage output (AGGRO); its
            // secondary identity is spells/burn density, with combo as a minor tertiary lean
            // (rituals/free spells).
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, weight = 0.9f),
            ColorStrategyEntry(themes = listOf(ThemeId.SPELLSLINGER), weight = 0.7f),
            ColorStrategyEntry(archetype = ArchetypeId.COMBO, weight = 0.4f),
        ))
        put(setOf(ManaColor.G), listOf(
            // Green's primary function is mana acceleration into big threats (the archetypal RAMP
            // color); +1/+1 counters is its best-known secondary theme.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, posture = PostureId.RAMP, weight = 0.9f),
            ColorStrategyEntry(themes = listOf(ThemeId.PLUS1_COUNTERS), weight = 0.6f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.55f),
        ))
        // ── Two-color guilds (10) -- each pair's identity is well-established constructed/EDH
        //    convention; several entries pair an archetype WITH a theme so isValidCombination has
        //    a real (non-trivial) check to clear, not just archetype-only/theme-only entries. ────
        put(setOf(ManaColor.W, ManaColor.U), listOf( // Azorius
            // W wipes + U counters/draw is the definitive tempo-control shell; ETB-blink value is
            // Azorius' best-known secondary theme (W removal-on-ETB + U card selection).
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.BLINK), weight = 0.75f),
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, posture = PostureId.TEMPO, weight = 0.6f),
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, weight = 0.5f),
        ))
        put(setOf(ManaColor.U, ManaColor.B), listOf( // Dimir
            // U card selection + B graveyard hate/removal is the classic mill control shell; the
            // pure control read (no theme) and self-mill (Dimir's own graveyard enabler side) round
            // out the guild.
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.MILL_OPPONENT), weight = 0.75f),
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, weight = 0.6f),
            ColorStrategyEntry(themes = listOf(ThemeId.SELF_MILL), weight = 0.5f),
        ))
        put(setOf(ManaColor.B, ManaColor.R), listOf( // Rakdos
            // B sac outlets/drain + R sacrifice payoffs/damage is THE definitive aristocrats guild
            // (built as a fast MIDRANGE-value shell, not a control one); pure aggro and spellslinger
            // round out the secondary leans.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.ARISTOCRATS), weight = 0.85f),
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, weight = 0.7f),
            ColorStrategyEntry(themes = listOf(ThemeId.SPELLSLINGER), weight = 0.5f),
        ))
        put(setOf(ManaColor.R, ManaColor.G), listOf( // Gruul
            // R aggression + G's biggest creatures/counters is the definitive stompy-aggro guild;
            // +1/+1 counters is Gruul's signature secondary theme.
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, themes = listOf(ThemeId.PLUS1_COUNTERS), weight = 0.8f),
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, weight = 0.65f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.6f),
        ))
        put(setOf(ManaColor.G, ManaColor.W), listOf( // Selesnya
            // G creature bulk + W token/anthem production is the definitive go-wide midrange guild.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.TOKENS), weight = 0.8f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.65f),
            ColorStrategyEntry(themes = listOf(ThemeId.PLUS1_COUNTERS), weight = 0.5f),
        ))
        put(setOf(ManaColor.W, ManaColor.B), listOf( // Orzhov
            // W drain/lifegain + B sac-and-drain is the definitive aristocrats-lifegain guild --
            // the two themes stack directly (life gained fuels/pays off the same sac loop).
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.ARISTOCRATS), weight = 0.8f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.LIFEGAIN), weight = 0.7f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.5f),
        ))
        put(setOf(ManaColor.U, ManaColor.R), listOf( // Izzet
            // U card selection/copy + R burn/damage is THE definitive spellslinger guild, built as
            // a tempo shell; pure combo (storm-adjacent) is the secondary lean.
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, posture = PostureId.TEMPO, themes = listOf(ThemeId.SPELLSLINGER), weight = 0.9f),
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.SPELLSLINGER), weight = 0.55f),
            ColorStrategyEntry(archetype = ArchetypeId.COMBO, weight = 0.5f),
        ))
        put(setOf(ManaColor.B, ManaColor.G), listOf( // Golgari
            // B reanimation + G graveyard-filling ramp/creatures is the definitive
            // reanimator/graveyard-value guild.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.REANIMATOR), weight = 0.75f),
            ColorStrategyEntry(themes = listOf(ThemeId.SELF_MILL), weight = 0.6f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.65f),
        ))
        put(setOf(ManaColor.R, ManaColor.W), listOf( // Boros
            // R's fast clock + W's go-wide tokens/combat tricks is the definitive aggressive
            // go-wide guild.
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, themes = listOf(ThemeId.TOKENS), weight = 0.9f),
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, weight = 0.6f),
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, posture = PostureId.TEMPO, weight = 0.55f),
        ))
        put(setOf(ManaColor.G, ManaColor.U), listOf( // Simic
            // G ramp + U card selection/big-spell payoffs is the definitive ramp-value guild;
            // +1/+1 counters (proliferate) is Simic's well-known secondary theme.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, posture = PostureId.RAMP, themes = listOf(ThemeId.PLUS1_COUNTERS), weight = 0.8f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, posture = PostureId.RAMP, weight = 0.6f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.55f),
        ))
        // ── Three-color WEDGES (5) -- Tarkir wedges: a color plus its two ENEMY colors
        //    (skip-one on the pentagon). Well-established as the "disruptive"/aggressive-value
        //    triads relative to the shards below. ──────────────────────────────────────────────
        put(setOf(ManaColor.W, ManaColor.B, ManaColor.R), listOf( // Mardu
            // W/B/R is the definitive aggressive-aristocrats wedge (Rakdos sac shell plus White's
            // go-wide/combat-trick support) -- the fastest of the three-color aristocrats builds.
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, themes = listOf(ThemeId.ARISTOCRATS), weight = 0.8f),
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, weight = 0.65f),
        ))
        put(setOf(ManaColor.U, ManaColor.R, ManaColor.G), listOf( // Temur
            // U/R/G's "big creatures + card selection + burn" mix supports both a value-midrange
            // read and a ramp-into-threats read equally well -- no single archetype clearly wins.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.7f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, posture = PostureId.RAMP, weight = 0.65f),
        ))
        put(setOf(ManaColor.W, ManaColor.B, ManaColor.G), listOf( // Abzan
            // W/B/G's synergy with +1/+1 counters (Outlast/Renown convention) is Abzan's signature
            // identity; the wedge's other well-known shell is WB aristocrats with G's bigger bodies
            // to sacrifice.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.PLUS1_COUNTERS), weight = 0.75f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.ARISTOCRATS), weight = 0.6f),
        ))
        put(setOf(ManaColor.W, ManaColor.U, ManaColor.R), listOf( // Jeskai
            // W/U/R's dense removal + card selection + burn is THE definitive spellslinger wedge;
            // a non-spells-focused aggressive-tempo build is the secondary lean.
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, posture = PostureId.TEMPO, themes = listOf(ThemeId.SPELLSLINGER), weight = 0.8f),
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, posture = PostureId.TEMPO, weight = 0.6f),
        ))
        put(setOf(ManaColor.U, ManaColor.B, ManaColor.G), listOf( // Sultai
            // U/B/G's card selection + graveyard-filling is the definitive self-mill/value wedge;
            // reanimating what self-mill puts in the yard is the natural follow-up shell.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.SELF_MILL), weight = 0.75f),
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.REANIMATOR), weight = 0.65f),
        ))
        // ── Three-color SHARDS (5, NEW in v2) -- three consecutive (allied) colors on the
        //    pentagon (Alara shards). Generally read as more "constructive/value" triads than the
        //    wedges above, since every pair of colors in a shard is allied rather than enemy. ────
        put(setOf(ManaColor.G, ManaColor.W, ManaColor.U), listOf( // Bant
            // G ramp + W removal/wipes + U card draw is the classic "durdle into value" shell --
            // ETB-blink value is Bant's best-known Commander theme (removal-on-ETB, repeatable).
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.BLINK), weight = 0.75f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, posture = PostureId.RAMP, weight = 0.65f),
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.SUPERFRIENDS), weight = 0.5f),
        ))
        put(setOf(ManaColor.W, ManaColor.U, ManaColor.B), listOf( // Esper
            // W wipes + U counters/draw + B removal/reanimation-backup covers essentially every
            // answer type in the game -- the definitive "answers for everything" control shard.
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, weight = 0.85f),
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.SUPERFRIENDS), weight = 0.55f),
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.ARTIFACTS), weight = 0.45f),
        ))
        put(setOf(ManaColor.U, ManaColor.B, ManaColor.R), listOf( // Grixis
            // U tempo/selection + B removal + R burn/copy is THE definitive spellslinger shard;
            // B reanimation with U/R support (discard outlets, card selection) is the classic
            // secondary shell, with pure combo (storm-adjacent) close behind.
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, posture = PostureId.TEMPO, themes = listOf(ThemeId.SPELLSLINGER), weight = 0.8f),
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.REANIMATOR), weight = 0.65f),
            ColorStrategyEntry(archetype = ArchetypeId.COMBO, weight = 0.5f),
        ))
        put(setOf(ManaColor.B, ManaColor.R, ManaColor.G), listOf( // Jund
            // B/R removal + G big creatures is THE definitive "good stuff" midrange shard (the
            // convention every constructed format's Jund shell is built on); sac-and-drain with G's
            // bigger bodies to feed it is the natural secondary read.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.85f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.ARISTOCRATS), weight = 0.65f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, posture = PostureId.RAMP, weight = 0.5f),
        ))
        put(setOf(ManaColor.R, ManaColor.G, ManaColor.W), listOf( // Naya
            // R/G/W all excel at cheap, powerful creatures -- the definitive wide-creature-aggro
            // shard; token production with combat-trick backup is the natural secondary shell.
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, weight = 0.8f),
            ColorStrategyEntry(archetype = ArchetypeId.AGGRO, themes = listOf(ThemeId.TOKENS), weight = 0.65f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.PLUS1_COUNTERS), weight = 0.55f),
        ))
        // ── Four-color combos (5, NEW in v2) -- keyed by the color EXCLUDED. Informal community
        //    "Nephilim" nicknames noted for readability only (not persisted/authoritative naming,
        //    unlike shard/wedge names which are official Magic terms). ─────────────────────────
        put(setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R), listOf( // "Yore-Tiller" (no green)
            // The 3 classic "answer" colors (W/U/B) plus R's removal/burn, missing only green's
            // ramp/big-creature package -- reads as a dense, answer-heavy control shell.
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, weight = 0.7f),
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.SPELLSLINGER), weight = 0.55f),
        ))
        put(setOf(ManaColor.U, ManaColor.B, ManaColor.R, ManaColor.G), listOf( // "Glint-Eye" (no white)
            // U/B/R/G covers removal, card advantage, and big creatures without white's board-wipe
            // premium -- a value-midrange shell; G's counters synergy with U/B/R support is the
            // secondary read.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.7f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.PLUS1_COUNTERS), weight = 0.55f),
        ))
        put(setOf(ManaColor.W, ManaColor.B, ManaColor.R, ManaColor.G), listOf( // "Dune-Brood" (no blue)
            // W/B/R/G is the definitive sacrifice-and-drain shell without blue's card-draw crutch --
            // recursion and raw value fill the gap; G ramp into removal-backed threats is secondary.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, themes = listOf(ThemeId.ARISTOCRATS), weight = 0.75f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, posture = PostureId.RAMP, weight = 0.5f),
        ))
        put(setOf(ManaColor.W, ManaColor.U, ManaColor.R, ManaColor.G), listOf( // "Ink-Treader" (no black)
            // G ramp fueling W/U/R support pieces -- the definitive big-mana shell without black's
            // reanimation shortcuts; W/U removal+draw protecting aggressive walkers is secondary.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, posture = PostureId.RAMP, weight = 0.7f),
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.SUPERFRIENDS), weight = 0.55f),
        ))
        put(setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.G), listOf( // "Witch-Maw" (no red)
            // W/U/B/G covers removal, draw, and recursion without red's variance -- the classic
            // grindy value shell; G/W enchantment density with U/B backup is the secondary read.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.7f),
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, themes = listOf(ThemeId.ENCHANTRESS), weight = 0.5f),
        ))
        // ── Five-color (1, NEW in v2) -- WUBRG. No single archetype dominates when every color
        //    pulls its weight, so the ranking favours the safest/most flexible reads. ──────────
        put(setOf(ManaColor.W, ManaColor.U, ManaColor.B, ManaColor.R, ManaColor.G), listOf(
            // A flexible "good stuff" pile is the safest default with access to every color; heavy
            // fixing doubles as ramp, favouring a secondary big-mana read; full color access also
            // supports a toolbox of situational answers.
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, weight = 0.6f),
            ColorStrategyEntry(archetype = ArchetypeId.MIDRANGE, posture = PostureId.RAMP, weight = 0.5f),
            ColorStrategyEntry(archetype = ArchetypeId.CONTROL, posture = PostureId.TOOLBOX, weight = 0.45f),
        ))
    }
}
