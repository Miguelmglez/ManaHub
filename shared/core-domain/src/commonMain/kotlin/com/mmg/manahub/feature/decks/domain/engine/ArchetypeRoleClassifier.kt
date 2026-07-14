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
        "counterspell" to "Counterspell",
        "protection" to "Protection",
        "recursion" to "Recursion",
        "evasion" to "Evasion",
    )

    /**
     * Every [RoleSpec] this classifier evaluates BEYOND [LEGACY_ROLE_MAP] (the 5 legacy-backed
     * roles are handled directly in [classify], not via this list, to avoid a double lookup).
     */
    val ROLE_SPECS: List<RoleSpec> = buildList {
        // ── Direct 1:1 tag-key roles (Phase 0.2 dictionary expansion) ──────────────
        DIRECT_TAG_ROLES.forEach { (key, label) -> add(RoleSpec(key, label, tagMatcher(key))) }

        // ── Structural / composite roles (no single tag covers them) ───────────────
        add(RoleSpec("finisher", "Finisher", ::finisherMatcher))
        add(RoleSpec("threat_early", "Early Threat", ::threatEarlyMatcher))
        add(RoleSpec("equipment_or_aura", "Equipment / Aura", ::equipmentOrAuraMatcher))
        add(RoleSpec(ArchetypeData.MANA_FIX_KEY, "Mana Fixing", ::manaFixMatcher))
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

    private val EVASION_KEYWORDS = listOf(
        "flying", "menace", "trample", "shadow", "fear", "intimidate",
        "skulk", "horsemanship", "unblockable", "can't be blocked",
    )
    private val PLUS_BUFF_PATTERN = Regex("""gets? \+\d+/\+\d+""")

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
        return counts.mapValues { (_, v) -> kotlin.math.round(v).toInt() }
    }

    /**
     * TRIBAL-theme-only role: copies of cards whose [TribeDeriver.tribeKeys] intersect the deck's
     * DOMINANT derived tribe (the same per-tribe fingerprint logic [DeckScorer.profile] already
     * uses — mirrored here rather than shared, since [DeckScorer]'s fingerprint building is
     * private). Returns 0 when the deck has no creatures or no tribe clears the threshold.
     */
    fun tribeMemberCount(mainboard: List<DeckEntry>): Int {
        val tribeCopies = mutableMapOf<String, Int>()
        mainboard.forEach { entry ->
            TribeDeriver.subtypeKeys(entry.card).forEach { key ->
                tribeCopies[key] = (tribeCopies[key] ?: 0) + entry.quantity
            }
        }
        val dominant = tribeCopies.maxByOrNull { it.value } ?: return 0
        return dominant.value
    }
}
