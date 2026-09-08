package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card

// ═══════════════════════════════════════════════════════════════════════════════
//  ArchetypeRoleClassifier — Deck Doctor Community/Archetype plan, Phase 1.1/1.4
//
//  Maps a Card -> Map<RoleKey, Float> over the Appendix A role vocabulary (a much
//  finer-grained taxonomy than the legacy 11-value [DeckRole] enum). Existing roles
//  map 1:1 onto this model via [LEGACY_ROLE_MAP] (RAMP/CARD_ADVANTAGE/SPOT_REMOVAL/
//  BOARD_WIPE/TUTOR — reusing [RoleClassifier]'s tag + oracle-fallback classification
//  wholesale, so nothing already covered by the existing engine is re-implemented or
//  silently dropped). The remaining ~30 Appendix A keys are NEW [RoleSpec]s, mostly
//  backed 1:1 by the Phase-0-added `TagCategory.ROLE` tag-dictionary entries.
//
//  D11 (fixed internal confidence threshold, independent of the user's tag-editor
//  sliders): [tagMatcher] treats a CONFIRMED tag (`card.tags`/`card.userTags`) as full
//  confidence (1.0) and additionally accepts a SUGGESTED tag whose OWN stored
//  confidence clears [SUGGESTED_TAG_FLOOR] (0.6 — the documented floor at which a
//  suggestion exists at all per [com.mmg.manahub.core.model.SuggestedTag]'s KDoc,
//  "[0.6, autoThreshold)"). This is the fixed threshold the engine applies REGARDLESS
//  of the user's personal `autoThreshold` slider (which only controls what gets
//  auto-CONFIRMED into `card.tags` at analysis time, a per-user side effect the
//  archetype layer must not silently inherit) — see `project_archetype_engine` memory
//  for the full reasoning (a from-scratch re-run of `SuggestTagsUseCase` at a fixed
//  threshold was considered and rejected as out of Phase-1 scope: it would require
//  re-classifying every mainboard card against the full tag dictionary on every
//  analysis instead of trusting the already-persisted `tags`/`suggestedTags`).
//
//  ── Classifier audit (Deck Analysis Engine v2 plan, Phase 2 §1, 2026-08-19) ──────────────────
//  This audit compares [ROLE_SPECS] matcher coverage against the legacy TagDictionary-backed
//  [RoleClassifier] for the same conceptual roles, now that this classifier becomes the SOLE
//  source of role counts on the analysis path (it was built for warnings, where a weaker matcher
//  was less visible; see this file's own header above).
//
//  FINDING: [LEGACY_ROLE_MAP] already reuses [RoleClassifier]'s tag + oracle-fallback
//  classification WHOLESALE for ramp/card_draw/removal_spot/removal_mass/tutor — no gap there.
//  Of the remaining [ROLE_SPECS] entries, TWO were weaker than the legacy signal: `"counterspell"`
//  and `"protection"` were plain [tagMatcher] lookups (`DIRECT_TAG_ROLES`) — CONFIRMED/SUGGESTED-
//  tag-only, with no oracle-text safety net — while [RoleClassifier]'s `INTERACTION` role already
//  has a validated oracle fallback for BOTH (`COUNTER_SPELL` — "counter target ... spell",
//  confidence 0.9; `GRANT_PROTECTION` — "gains/have/has hexproof/indestructible/protection
//  from/shroud", confidence 0.6). A tag-less counterspell/protection-granting card (an unresolved
//  suggestion, a brand-new printing the tag dictionary has not caught up to yet) would silently
//  undercount in the v2 plan-roles pillar even though the legacy engine already recognizes it.
//  FIX: folded the SAME two regexes into [counterspellMatcher]/[protectionMatcher] below as a
//  fallback under the tag hit (D11 discipline preserved: a confirmed/suggested tag hit is always
//  full/its-own confidence; the oracle fallback only fires when no tag signal exists at all).
//
//  Every other [ROLE_SPECS] entry (`sac_outlet`, `death_payoff`, `finisher`, `threat_early`,
//  `equipment_or_aura`, `mana_fix`, `recursion`, `evasion`, …) has NO legacy [DeckRole] counterpart
//  to compare against (the legacy 11-value enum simply does not model these Appendix A roles), so
//  there is nothing to fold in for them — they stay exactly as Phase 1 built them.
// ═══════════════════════════════════════════════════════════════════════════════

object ArchetypeRoleClassifier {

    /** D11's fixed engine-side confidence floor for counting a SUGGESTED (not yet confirmed) tag. */
    const val SUGGESTED_TAG_FLOOR = 0.6f

    /** Legacy [DeckRole] -> new [RoleKey], for the 5 roles the existing engine already classifies
     * well (tag + oracle-fallback via [RoleClassifier]) and Appendix A also names directly. */
    private val LEGACY_ROLE_MAP: Map<DeckRole, RoleKey> = mapOf(
        DeckRole.RAMP to "ramp",
        DeckRole.CARD_ADVANTAGE to "card_draw",
        DeckRole.SPOT_REMOVAL to "removal_spot",
        DeckRole.BOARD_WIPE to "removal_mass",
        DeckRole.TUTOR to "tutor",
    )

    private val legacyClassifier = RoleClassifier()

    /** key -> English label for every role backed by a single Phase-0 `TagCategory.ROLE` tag.
     * Declared BEFORE [ROLE_SPECS] — `object` property initializers run top-to-bottom. */
    private val DIRECT_TAG_ROLES: List<Pair<RoleKey, String>> = listOf(
        "sac_outlet" to "Sacrifice Outlet",
        "death_payoff" to "Death Payoff",
        "graveyard_enabler" to "Graveyard Enabler",
        "reanimation" to "Reanimation",
        "graveyard_hate" to "Graveyard Hate",
        "self_mill_payoff" to "Self-Mill Payoff",
        "stax_piece" to "Stax Piece",
        "landfall_payoff" to "Landfall Payoff",
        "lifegain_payoff" to "Lifegain Payoff",
        "counters_payoff" to "Counters Payoff",
        "spell_payoff" to "Spell Payoff",
        "mill_engine" to "Mill Engine",
        "wheel" to "Wheel",
        "token_generator" to "Token Generator",
        "aura_buff" to "Aura Buff",
        "blink_effect" to "Blink Effect",
        "etb_payoff" to "ETB Payoff",
        "planeswalker" to "Planeswalker",
        "vehicle" to "Vehicle",
        "clone_theft_effect" to "Clone / Theft Effect",
        "group_effect" to "Group Effect",
        "enchantment_payoff" to "Enchantment Payoff",
        "artifact_payoff" to "Artifact Payoff",
        "tribe_payoff" to "Tribe Payoff",
        // "counterspell"/"protection" REMOVED from here (classifier audit, Phase 2 §1) -- they now
        // get a dedicated matcher with an oracle-text fallback (see ROLE_SPECS below), not a bare
        // tagMatcher, so a tag-less card is not silently undercounted.
        "recursion" to "Recursion",
        "evasion" to "Evasion",
        // ── Deck Analysis Engine v3, Phase 1 (spec §5.1) -- reused pre-existing tag entries ──
        // (anthem/untapper/spell_copy/equipment already had a real TagDictionary DetectionRule
        // from earlier work; they were simply never wired into this classifier's vocabulary).
        "anthem" to "Anthem",
        "untapper" to "Untapper",
        "spell_copy" to "Spell Copy",
        "equipment" to "Equipment",
        // ── Deck Analysis Engine v3, Phase 1 -- brand-new producer roles (spec §5.1) ────────
        "lifegain_source" to "Lifegain Source",
        "counters_source" to "Counters Source",
        "treasure_source" to "Treasure Source",
        "extra_land_drop" to "Extra Land Drop",
        "discard_outlet" to "Discard Outlet",
        "cost_reducer" to "Cost Reducer",
        "haste_source" to "Haste Source",
        "mill_opponent" to "Mill Opponent",
        "mill_self" to "Mill Self",
        "combat_payoff" to "Combat Payoff",
        "removal_artifact_enchant" to "Artifact/Enchantment Removal",
    )

    /**
     * Deck Analysis Engine v3, Phase 1 (spec §5.2) -- the derived synergy-axis table, one entry
     * per [RoleSpec] that participates in at least one axis. Kept as three separate lookup maps
     * (rather than inline per-[RoleSpec] literals) so this table can be read top-to-bottom against
     * the spec's own table and audited in one place. A key absent from a map simply defaults to
     * `emptySet()` via [axisSetFor] -- most roles (e.g. `finisher`, `mana_fix`,
     * `equipment_or_aura`, `mill_engine`, `removal_artifact_enchant`) are not part of the axis
     * model at all and are deliberately absent from all three maps.
     *
     * `equipment_or_aura` and `mill_engine` are intentionally NOT re-pointed at any axis here --
     * they stay exactly as they were (a derived union of their own pre-existing matcher logic,
     * untouched by this phase); the axis metadata lives on their SPLIT children (`equipment`/
     * `aura_buff` and `mill_self`/`mill_opponent`) instead, per spec §5.1's split instruction.
     *
     * `tribe_members` is deliberately absent from [AXIS_PRODUCES] even though it is the TRIBE
     * axis's producer role: TRIBE is deck-relative (`TRIBE:<subtype>`, spec §5.2), so a static
     * per-[RoleSpec] `produces` set cannot express it -- Phase 2's [SynergyGraph] (not yet built)
     * substitutes the deck's own dominant subtype at graph-build time, the same way P3 already
     * special-cases `tribe_members` via [tribeMemberCount]/[dominantTribeKey] rather than through
     * [ROLE_SPECS]. `tribe_payoff`'s `consumes = {"TRIBE"}` and `anthem`'s `amplifies` entry are
     * enough to establish the axis has a real payoff/amplifier side today.
     */
    private val AXIS_PRODUCES: Map<RoleKey, Set<AxisKey>> = mapOf(
        "lifegain_source" to setOf("LIFE"),
        "sac_outlet" to setOf("DEATH"),
        "sacrifice_fodder" to setOf("DEATH"),
        "token_generator" to setOf("TOKENS", "ETB", "DEATH"),
        "counters_source" to setOf("COUNTERS"),
        "extra_land_drop" to setOf("LANDFALL"),
        "mill_self" to setOf("GRAVEYARD"),
        "discard_outlet" to setOf("GRAVEYARD"),
        "graveyard_enabler" to setOf("GRAVEYARD"),
        "blink_effect" to setOf("ETB"),
        "clone_theft_effect" to setOf("ETB"),
        "spell_copy" to setOf("SPELLS"),
        "treasure_source" to setOf("ARTIFACTS"),
        "aura_buff" to setOf("ENCHANTMENTS", "ATTACHED"),
        "equipment" to setOf("ATTACHED"),
        "haste_source" to setOf("ATTACK"),
        "evasion" to setOf("ATTACK"),
        "threat_early" to setOf("ATTACK"),
        "planeswalker" to setOf("PLANESWALKERS"),
        "group_effect" to setOf("GROUP"),
        "mill_opponent" to setOf("MILL_OPP"),
        // Final engine-correction run, DEFECT 1: `stax_piece` previously formed zero graph edges on
        // ANY axis (absent from every table in this file) -- PRISON's own `linearity` prototype
        // (0.60 Commander / 0.65 60-card) demanded a coordinate the model could not produce for ANY
        // stax-shaped deck, in EITHER format (measured `health=0.0` on every one of 15 axes for both
        // Grand Arbiter (Commander) and Death and Taxes (60-card) -- see
        // `project_deck_analysis_v3_sixtycard_corpus_expansion`/`_phase4b_p4_corrective` memory).
        // Fixed via a new payoff-optional `LOCK` axis (spec §5.3's own documented mechanism for
        // "the producers ARE the win condition", already used by `MILL_OPP` and `TRIBE:x` with a
        // lord) -- `stax_piece` is this axis's sole producer; no dedicated payoff role exists for
        // it (a lock piece does not "pay off" into anything else, it IS the plan), mirroring
        // `MILL_OPP`'s own producer-only shape exactly rather than inventing a self-consuming edge.
        "stax_piece" to setOf("LOCK"),
    )

    private val AXIS_CONSUMES: Map<RoleKey, Set<AxisKey>> = mapOf(
        "lifegain_payoff" to setOf("LIFE", "GROUP"),
        "death_payoff" to setOf("DEATH", "TOKENS", "GROUP"),
        "counters_payoff" to setOf("COUNTERS", "TOKENS"),
        "landfall_payoff" to setOf("LANDFALL"),
        "reanimation" to setOf("GRAVEYARD"),
        "self_mill_payoff" to setOf("GRAVEYARD"),
        "recursion" to setOf("GRAVEYARD"),
        "etb_payoff" to setOf("ETB"),
        "spell_payoff" to setOf("SPELLS"),
        // "counterspell" deliberately NOT mapped to SPELLS (Deck Analysis Engine v3 spec §5.2
        // AMENDMENT, 2026-08-26, user-approved during phase 3a). A counterspell is interaction,
        // not a spellslinger payoff -- it does not reward casting spells, it IS the spell. As
        // originally written, any control deck's instant/sorcery type-line density (SPELLS
        // producer) plus its counterspell suite (SPELLS payoff) lit the axis by construction, with
        // no dedicated spellslinger payoff present -- root cause of fixtures 05/11/15's spurious
        // SPELLSLINGER theme. Genuine spellslinger decks still light SPELLS through `spell_payoff`
        // alone (Guttersnipe/Young Pyromancer/Veyran-style "whenever you cast an instant or
        // sorcery" effects), which is the correct signal. `counterspell` stays axis-inert -- it
        // still counts toward the CONTROL/PRISON/interaction role bands (ArchetypeData.kt) and the
        // §3 posture TEMPO signal, just not the synergy-graph SPELLS axis.
        "artifact_payoff" to setOf("ARTIFACTS"),
        "enchantment_payoff" to setOf("ENCHANTMENTS"),
        "combat_payoff" to setOf("ATTACHED", "ATTACK", "TOKENS"),
        "evasion" to setOf("ATTACHED"),
        "tribe_payoff" to setOf("TRIBE"),
        "counters_source" to setOf("PLANESWALKERS"),
    )

    private val AXIS_AMPLIFIES: Map<RoleKey, Set<AxisKey>> = mapOf(
        "anthem" to setOf("TOKENS", "COUNTERS", "TRIBE", "ATTACK"),
        "recursion" to setOf("DEATH"),
        "cost_reducer" to setOf("SPELLS", "ARTIFACTS"),
        "protection" to setOf("ATTACHED", "PLANESWALKERS"),
    )

    private fun axisSetFor(map: Map<RoleKey, Set<AxisKey>>, key: RoleKey): Set<AxisKey> = map[key].orEmpty()

    /**
     * Every [RoleSpec] this classifier evaluates BEYOND [LEGACY_ROLE_MAP] (the 5 legacy-backed
     * roles are handled directly in [classify], not via this list, to avoid a double lookup).
     */
    val ROLE_SPECS: List<RoleSpec> = buildList {
        // ── Direct 1:1 tag-key roles (Phase 0.2 dictionary expansion + Phase 1 additions) ────
        DIRECT_TAG_ROLES.forEach { (key, label) ->
            add(RoleSpec(key, label, tagMatcher(key), axisSetFor(AXIS_PRODUCES, key), axisSetFor(AXIS_CONSUMES, key), axisSetFor(AXIS_AMPLIFIES, key)))
        }

        // ── Structural / composite roles (no single tag covers them) ───────────────
        add(RoleSpec("finisher", "Finisher", ::finisherMatcher))
        add(RoleSpec("threat_early", "Early Threat", ::threatEarlyMatcher, produces = axisSetFor(AXIS_PRODUCES, "threat_early")))
        add(RoleSpec("equipment_or_aura", "Equipment / Aura", ::equipmentOrAuraMatcher))
        add(RoleSpec(ArchetypeData.MANA_FIX_KEY, "Mana Fixing", ::manaFixMatcher))
        // Classifier audit fix (Phase 2 §1) -- tag hit first, oracle-text fallback second (mirrors
        // RoleClassifier's own validated INTERACTION patterns, folded in rather than duplicated at
        // a weaker confidence). No `consumes` -- v3 spec §5.2 AMENDMENT: counterspell is
        // intentionally axis-inert (see AXIS_CONSUMES's own comment above for the rationale).
        add(RoleSpec("counterspell", "Counterspell", ::counterspellMatcher))
        add(RoleSpec("protection", "Protection", ::protectionMatcher, amplifies = axisSetFor(AXIS_AMPLIFIES, "protection")))
        // Deck Analysis Engine v3, Phase 1 (spec §5.1) -- structural, no single tag covers it:
        // a cheap creature built to die profitably, OR a token generator (the tokens it makes ARE
        // the fodder -- "distinct from token_generator" per the spec's own wording means a
        // separate RoleKey, not that the two are mutually exclusive on the same card).
        add(RoleSpec("sacrifice_fodder", "Sacrifice Fodder", ::sacrificeFodderMatcher, produces = axisSetFor(AXIS_PRODUCES, "sacrifice_fodder")))
        // Final engine-correction run, DEFECT 3 -- a burn/reach spell ("deals X damage to any
        // target/target player/target opponent") is genuinely DUAL-PURPOSE: it can kill a creature
        // (legitimately `removal_spot`, untouched) AND close the game (a `clock` component the
        // resolver's own §2.1 axis had no way to see, forcing every burn spell's density entirely
        // into `interaction` -- confirmed root cause of fixture 14/Mono-Red Burn resolving MIDRANGE,
        // see `project_deck_analysis_v3_sixtycard_corpus_expansion` memory). No `produces`/
        // `consumes`/`amplifies` here -- this role feeds ONLY `InferDeckArchetypeUseCase.computeAxes`'s
        // `clock` density term (a NEW consumer this phase adds), not the SynergyGraph axis model,
        // which spec §5.2's own table never lists a "reach"/direct-damage axis for.
        add(RoleSpec("direct_damage", "Direct Damage", ::directDamageMatcher))
        // Deck Analysis Engine v3, Phase 1 -- fixes the gap #2 documented in ColorRoleAffinity.kt's
        // own header ("tabled for documentation/future use only -- no matcher, no band"). Now a
        // real DIRECT_TAG_ROLES entry above; this comment lives here since ColorRoleAffinity is the
        // file that originally flagged the gap.
    }

    /**
     * Deck Wizard & Engine Rework plan, Workstream 2.3 -- English label for a [RoleKey] this
     * classifier can produce (both the 5 [LEGACY_ROLE_MAP] roles and every [ROLE_SPECS] entry),
     * used by the wizard's MANUAL_ADDS step for its role-section headers. Falls back to a humanized
     * version of the raw key for any role key not covered here (should not happen in practice for a
     * key that came from a real [ResolvedArchetypeSkeleton.roleTargets] map, since every possible
     * role key this engine assigns a band to is produced by [classify] and therefore covered by one
     * of the two tables below).
     */
    fun label(key: RoleKey): String = ROLE_LABELS[key] ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }

    private val ROLE_LABELS: Map<RoleKey, String> = buildMap {
        put("ramp", "Ramp")
        put("card_draw", "Card Draw")
        put("removal_spot", "Spot Removal")
        put("removal_mass", "Mass Removal")
        put("tutor", "Tutor")
        ROLE_SPECS.forEach { spec -> put(spec.key, spec.label) }
    }

    /**
     * The full role map for one card: the 5 [LEGACY_ROLE_MAP] roles (via [RoleClassifier],
     * unmodified) plus every [ROLE_SPECS] hit (confidence > 0). Never mutates/reclassifies the
     * card; pure function.
     */
    fun classify(card: Card): Map<RoleKey, Float> {
        val result = mutableMapOf<RoleKey, Float>()
        val legacy = legacyClassifier.classify(card)
        LEGACY_ROLE_MAP.forEach { (deckRole, roleKey) -> legacy[deckRole]?.let { result[roleKey] = it } }
        ROLE_SPECS.forEach { spec ->
            val confidence = spec.matcher(card)
            if (confidence > 0f) {
                val prev = result[spec.key] ?: 0f
                if (confidence > prev) result[spec.key] = confidence
            }
        }
        return result
    }

    // ── Matcher builders ─────────────────────────────────────────────────────────

    /** A card matches [key] (any `TagCategory.ROLE` tag with that key) at D11-gated confidence. */
    private fun tagMatcher(key: RoleKey): (Card) -> Float = matcher@{ card ->
        val confirmed = (card.tags + card.userTags).any { it.key == key }
        if (confirmed) return@matcher 1f
        val suggested = card.suggestedTags.filter { it.tag.key == key && it.confidence >= SUGGESTED_TAG_FLOOR }
        suggested.maxOfOrNull { it.confidence } ?: 0f
    }

    /**
     * WIN_CON-tagged cards are full-confidence finishers; a big creature body (cmc >= 5, power >=
     * 4) is a strong structural finisher signal even without the tag (A.1: this cell is
     * deliberately soft — see the annex's own methodology note; tuned via the golden tests).
     */
    private fun finisherMatcher(card: Card): Float {
        val tagHit = tagMatcher("win_con")(card)
        if (tagHit > 0f) return tagHit
        if (!card.typeLine.contains("Creature", ignoreCase = true)) return 0f
        if (card.cmc < 5.0) return 0f
        val power = card.power?.toIntOrNull() ?: return 0f
        return if (power >= 4) 0.7f else 0f
    }

    /**
     * A cheap (cmc <= 3), meaningfully-bodied creature — the AGGRO/TEMPO "curve out early"
     * signal. No tag backs this in the dictionary (`threat_early` is manual-pick only per the
     * Phase-0 dictionary audit), so this is purely structural.
     */
    private fun threatEarlyMatcher(card: Card): Float {
        if (BasicLandCalculator.isLand(card)) return 0f
        if (!card.typeLine.contains("Creature", ignoreCase = true)) return 0f
        if (card.cmc > 3.0) return 0f
        val power = card.power?.toIntOrNull() ?: 0
        val hasEvasion = card.oracleText?.let { text ->
            EVASION_KEYWORDS.any { text.contains(it, ignoreCase = true) }
        } ?: false
        return when {
            power >= 3 -> 0.9f
            power == 2 -> 0.7f
            hasEvasion -> 0.5f
            else -> 0f
        }
    }

    /**
     * Deck Analysis Engine v3, Phase 1 (spec §5.1): a cheap creature built to be sacrificed
     * profitably (MV <= 2 with its own ETB or death trigger -- structural, no single tag covers
     * "wants to die young"), OR any token generator (the tokens themselves are the fodder; see
     * [sacrificeFodderMatcher]'s call site comment on why this is a separate RoleKey rather than
     * folded into `token_generator`).
     */
    private fun sacrificeFodderMatcher(card: Card): Float {
        if (!BasicLandCalculator.isLand(card) && card.typeLine.contains("Creature", ignoreCase = true) && card.cmc <= 2.0) {
            val oracle = card.oracleText?.lowercase().orEmpty()
            val hasEtbOrDeathTrigger = oracle.contains("when") && (oracle.contains("enters") || oracle.contains("dies"))
            if (hasEtbOrDeathTrigger) return 0.7f
        }
        val tokenGeneratorHit = tagMatcher("token_generator")(card)
        if (tokenGeneratorHit > 0f) return 0.6f
        return 0f
    }

    /** Voltron gear: Equipment type-line (full confidence) or an Aura that buffs (aura_buff tag,
     * or a `+X/+X`-shaped oracle clause as a structural fallback). */
    private fun equipmentOrAuraMatcher(card: Card): Float {
        if (card.typeLine.contains("Equipment", ignoreCase = true)) return 1f
        if (card.typeLine.contains("Aura", ignoreCase = true)) {
            val auraBuffTag = tagMatcher("aura_buff")(card)
            if (auraBuffTag > 0f) return auraBuffTag
            val oracle = card.oracleText.orEmpty()
            if (PLUS_BUFF_PATTERN.containsMatchIn(oracle)) return 0.6f
        }
        return 0f
    }

    /**
     * Mana-fixing: a land producing >= 2 distinct colors (D14's [Card.producedMana]) is a
     * structural fixing source at full confidence; otherwise fall back to the `mana_fix` tag
     * (non-land payoffs like Chromatic Lantern, or a nonbasic whose production Scryfall has not
     * classified into [Card.producedMana] yet).
     */
    private fun manaFixMatcher(card: Card): Float {
        if (BasicLandCalculator.isLand(card)) {
            val distinctColors = card.producedMana.toSet().count { it in "WUBRG" }
            if (distinctColors >= 2) return 1f
        }
        return tagMatcher(ArchetypeData.MANA_FIX_KEY)(card)
    }

    /**
     * Classifier audit fix (Phase 2 §1): a confirmed/suggested `counterspell` tag hit wins outright
     * (D11 confidence discipline, [tagMatcher]); otherwise falls back to [COUNTER_SPELL_ORACLE] at
     * [COUNTERSPELL_ORACLE_CONFIDENCE] — the SAME pattern/confidence [RoleClassifier]'s own
     * INTERACTION role already validates, so a tag-less counterspell is no longer invisible here.
     */
    private fun counterspellMatcher(card: Card): Float {
        val tagHit = tagMatcher("counterspell")(card)
        if (tagHit > 0f) return tagHit
        val oracle = card.oracleText?.lowercase().orEmpty()
        return if (COUNTER_SPELL_ORACLE.containsMatchIn(oracle)) COUNTERSPELL_ORACLE_CONFIDENCE else 0f
    }

    /**
     * Classifier audit fix (Phase 2 §1): mirrors [counterspellMatcher] for the `protection` role,
     * falling back to [GRANT_PROTECTION_ORACLE] (the same oracle pattern [RoleClassifier]'s
     * INTERACTION role already validates) when no tag exists.
     */
    private fun protectionMatcher(card: Card): Float {
        val tagHit = tagMatcher("protection")(card)
        if (tagHit > 0f) return tagHit
        val oracle = card.oracleText?.lowercase().orEmpty()
        return if (GRANT_PROTECTION_ORACLE.containsMatchIn(oracle)) PROTECTION_ORACLE_CONFIDENCE else 0f
    }

    /**
     * Final engine-correction run, DEFECT 3: tag hit first (`"burn"`, `CardTag.BURN`'s key -- a
     * pre-existing `TagCategory.STRATEGY` tag; [tagMatcher] compares by key string only,
     * category-agnostic, so a card carrying it is matched regardless), oracle-text fallback second.
     * [DIRECT_DAMAGE_ORACLE] covers the SAME "deals ... damage to any target/target player/target
     * opponent/each opponent/each player" wording `TagDictionary`'s own "burn" strategy-tag rule 1
     * detects (deliberately NOT its rule 2, "damage to target creature" alone with no player
     * component, which would tag pure single-target creature removal as "can hit face") -- but as
     * ONE contiguous regex (mirrors [counterspellMatcher]/[protectionMatcher]'s own
     * classifier-audit-fix pattern) rather than two independent whole-text `contains` checks: a
     * multi-clause card can genuinely contain "damage to <player>" in one sentence and, say, "each
     * player" in a LATER, unrelated sentence (caught during this fix — Chainer fixture's own
     * Rankle, Master of Pranks: "...deals combat damage to a player, choose ... Each player
     * discards..." -- two unrelated clauses, not a reach spell) — an independent-substring check
     * would false-positive on that; the contiguous regex correctly does not.
     */
    private fun directDamageMatcher(card: Card): Float {
        val tagHit = tagMatcher("burn")(card)
        if (tagHit > 0f) return tagHit
        val oracle = card.oracleText?.lowercase().orEmpty()
        return if (DIRECT_DAMAGE_ORACLE.containsMatchIn(oracle)) DIRECT_DAMAGE_ORACLE_CONFIDENCE else 0f
    }

    private val DIRECT_DAMAGE_ORACLE = Regex("deals? [^.]*?damage to (?:any target|target player|target opponent|each opponent|each player)")
    private const val DIRECT_DAMAGE_ORACLE_CONFIDENCE = 0.85f // mirrors TagDictionary's own "burn" rule 1 confidence

    private val EVASION_KEYWORDS = listOf(
        "flying", "menace", "trample", "shadow", "fear", "intimidate",
        "skulk", "horsemanship", "unblockable", "can't be blocked",
    )
    private val PLUS_BUFF_PATTERN = Regex("""gets? \+\d+/\+\d+""")

    // ── Classifier audit fix (Phase 2 §1) oracle fallbacks — mirror RoleClassifier's private
    // COUNTER_SPELL/GRANT_PROTECTION patterns and confidences EXACTLY (that file's regexes are
    // private to its own companion object, so these are intentionally re-declared here rather than
    // exposing a cross-file surface for two small regexes).
    private val COUNTER_SPELL_ORACLE = Regex("counter target (?:[^.]*?)?spell")
    private val GRANT_PROTECTION_ORACLE = Regex("(?:gains?|have|has) (?:hexproof|indestructible|protection from|shroud)")
    private const val COUNTERSPELL_ORACLE_CONFIDENCE = 0.9f
    private const val PROTECTION_ORACLE_CONFIDENCE = 0.6f

    // ── Deck-level aggregation (mirrors DeckScorer.profile's roleCounts pattern) ───

    /**
     * Quantity x confidence role-count map for a full mainboard (lands included — `classify`
     * short-circuits lands to an empty map here since no Appendix A role targets a land by
     * itself; mana-fixing lands are counted via [manaFixMatcher] regardless of the land
     * short-circuit, since that matcher runs INSIDE `classify` before any land filtering).
     */
    fun deckRoleCounts(mainboard: List<DeckEntry>): Map<RoleKey, Int> {
        val counts = mutableMapOf<RoleKey, Float>()
        mainboard.forEach { entry ->
            classify(entry.card).forEach { (key, confidence) ->
                counts[key] = (counts[key] ?: 0f) + entry.quantity * confidence
            }
        }
        val result = counts.mapValues { (_, v) -> kotlin.math.round(v).toInt() }
        // Deck Analysis Engine v3, Phase 1 (spec §5.1) -- fixes gap #1 ("tribe_members" has no
        // classifier). Injected here rather than via a per-card RoleSpec because "the deck's
        // dominant creature subtype" is a whole-deck fact no stateless (Card) -> Float matcher can
        // compute -- see the KDoc above [AXIS_PRODUCES] for the full reasoning. P3's own
        // `AnalysisEngine.evaluatePlanRoles` special-case (untouched by this phase) reads
        // [tribeMemberCount] directly and never this map's "tribe_members" entry, so this addition
        // cannot move P3's score; it exists so every OTHER `deckRoleCounts` consumer (Phase 2's
        // future SynergyGraph, Suggest*/SuggestCuts use cases) stops seeing a permanent 0.
        val tribeMembers = tribeMemberCount(mainboard)
        return if (tribeMembers > 0) result + ("tribe_members" to tribeMembers) else result
    }

    /**
     * Deck Analysis Category Sections rework (W1) — per-role card attribution, the SAME
     * classification pass as [deckRoleCounts], nothing discarded (that function collapses straight
     * to a rounded `Int`; this one keeps every (card, quantity, confidence) triple that produced
     * it). Sorted by confidence descending so a section's card row leads with its strongest
     * matches. See [com.mmg.manahub.feature.decks.domain.engine.CardContribution]'s KDoc for why
     * this list will not always sum back to [deckRoleCounts]'s rounded count for the same key.
     */
    fun deckRoleAttribution(mainboard: List<DeckEntry>): Map<RoleKey, List<CardContribution>> {
        val base = buildMap<RoleKey, MutableList<CardContribution>> {
            mainboard.forEach { entry ->
                classify(entry.card).forEach { (key, confidence) ->
                    getOrPut(key) { mutableListOf() } +=
                        CardContribution(entry.card.scryfallId, entry.quantity, confidence)
                }
            }
        }.mapValues { (_, list) ->
            list.groupBy { it.scryfallId }
                .map { (id, group) ->
                    CardContribution(
                        scryfallId = id,
                        quantity   = group.sumOf { it.quantity },
                        confidence = group.maxOf { it.confidence }
                    )
                }
                .sortedByDescending { it.confidence }
        }
        // Mirrors deckRoleCounts' "tribe_members" injection above -- SAME dominant-tribe source
        // ([dominantTribeKey]/[tribeSubtypeCopyCounts]) so the two can never disagree. Confidence
        // 1f matches AnalysisEngine's own private `toContributions()` helper (a structural
        // subtype-membership match, not a fuzzy oracle-text one).
        val dominantTribe = dominantTribeKey(mainboard) ?: return base
        val tribeContributions = mainboard
            .filter { dominantTribe in TribeDeriver.subtypeKeys(it.card) }
            .groupBy { it.card.scryfallId }
            .map { (id, group) -> CardContribution(id, group.sumOf { it.quantity }, 1f) }
        return if (tribeContributions.isEmpty()) base else base + ("tribe_members" to tribeContributions)
    }

    /**
     * Per-`tribe:<subtype>` copy-count map for a mainboard ([TribeDeriver.subtypeKeys] — a card's
     * OWN creature subtypes only, never payoff-named tribes). Shared by [tribeMemberCount] and
     * [dominantTribeKey] so both always agree on which tribe is dominant.
     */
    private fun tribeSubtypeCopyCounts(mainboard: List<DeckEntry>): Map<String, Int> {
        val tribeCopies = mutableMapOf<String, Int>()
        mainboard.forEach { entry ->
            TribeDeriver.subtypeKeys(entry.card).forEach { key ->
                tribeCopies[key] = (tribeCopies[key] ?: 0) + entry.quantity
            }
        }
        return tribeCopies
    }

    /**
     * TRIBAL-theme-only role: copies of cards whose [TribeDeriver.tribeKeys] intersect the deck's
     * DOMINANT derived tribe (the same per-tribe fingerprint logic [DeckScorer.profile] already
     * uses — mirrored here rather than shared, since [DeckScorer]'s fingerprint building is
     * private). Returns 0 when the deck has no creatures or no tribe clears the threshold.
     */
    fun tribeMemberCount(mainboard: List<DeckEntry>): Int =
        tribeSubtypeCopyCounts(mainboard).maxByOrNull { it.value }?.value ?: 0

    /**
     * Deck Analysis Category Sections rework (W1) — the `tribe:<subtype>` key ([tribeMemberCount]
     * counted) with the most copies in [mainboard], or `null` for a creatureless/tribeless deck.
     * [AnalysisEngine]'s PLAN_ROLES section builder uses this to attribute `tribe_members`
     * contributions to the SAME tribe [tribeMemberCount] already counts (originally fixing the bug
     * where `roleCounts["tribe_members"]` was always absent since no [RoleSpec] produced that key;
     * Deck Analysis Engine v3 Phase 1's [deckRoleCounts]/[deckRoleAttribution] injection now also
     * uses this same source, so every consumer agrees) — this and [tribeMemberCount] deliberately
     * share [tribeSubtypeCopyCounts] so the two numbers can never disagree.
     */
    fun dominantTribeKey(mainboard: List<DeckEntry>): String? =
        tribeSubtypeCopyCounts(mainboard).maxByOrNull { it.value }?.key
}
