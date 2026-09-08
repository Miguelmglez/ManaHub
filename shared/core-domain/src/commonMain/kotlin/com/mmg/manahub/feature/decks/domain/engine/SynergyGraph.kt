package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════════════════
//  SynergyGraph — Deck Analysis Engine v3, PHASE 2 (docs/plans/deck-analysis-engine-v3-spec.md
//  §5.2-§5.4). Builds the directed producer -> payoff synergy graph and per-axis health.
//
//  PHASE 2 built this in SHADOW MODE (computed, exposed only behind [AnalysisEngine.evaluate]'s
//  debug-only opt-in param, read by NO pillar). PHASE 3 became the first real consumer (theme
//  detection reads [AxisState.isLive] instead of a role density check -- spec §4.4, in
//  [com.mmg.manahub.feature.decks.domain.usecase.InferDeckArchetypeUseCase], which builds its OWN
//  graph independently of [AnalysisEngine.evaluate]'s debug flag). PHASE 4 (spec §7) is the first
//  phase where THIS pillar's own P4 SYNERGY subscore is computed from this graph
//  ([AnalysisEngine.evaluateSynergy] now calls [build] unconditionally, not just under the debug
//  flag) -- "shadow mode" for THIS call site has ended; [AxisState.producerIdeal] and
//  [turnFourDrawProbability] below exist for that consumer.
//
//  PASS 1 of the pipeline (spec §1): runs BEFORE archetype/theme resolution, using only the
//  mainboard's own role vocabulary ([ArchetypeRoleClassifier.classify]) -- never the resolved
//  [ResolvedArchetypeSkeleton], which does not exist yet at this point in a real evaluation.
//
//  ── The 5-legacy-role gap (Phase 1 finding, `project_deck_analysis_v3_phase1_roles` memory) ──
//  `ramp`/`card_draw`/`removal_spot`/`removal_mass`/`tutor` are classified via [RoleClassifier]
//  directly and never become a [RoleSpec] (no `produces`/`consumes`/`amplifies`), so the spec's
//  LANDFALL "land-based ramp" producer cell and the SPELLS/ARTIFACTS/ENCHANTMENTS "type-line
//  density" producer cells have nowhere to attach via the generic per-[RoleSpec] path. This phase
//  resolves both WITHOUT touching [ArchetypeRoleClassifier]/[ArchetypeModels] (no score can move
//  from a file neither pillar nor Phase 1's byte-identical corpus gate reads):
//    - SPELLS/ARTIFACTS/ENCHANTMENTS producers: computed directly from [Card.typeLine] here (a
//      raw type-line density is not a role classification at all -- the spec itself lists it
//      SEPARATELY from the role-based producers in the same table cell, e.g. "instant/sorcery
//      type-line density · spell_copy"), see [structuralAxisContribution].
//    - LANDFALL's "land-based ramp": narrowly scoped to cards that ALREADY carry the pre-existing
//      `ramp` role (via [ArchetypeRoleClassifier.classify], reusing the SAME vocabulary, not a
//      second one) whose oracle text also puts a land onto the battlefield -- see
//      [isLandBasedRamp]. A mana rock/dork carries `ramp` but never matches this narrower text
//      check, so it correctly stays out of LANDFALL.
//  See this file's own KDoc on [TRIBE_AXIS_PREFIX] for the third (TRIBE) special case, which Phase
//  1's memory explicitly flagged as Phase 2's to solve.
//
//  ── Axis-ideal table (producer/payoff `[min,ideal,max]`-style counts feeding [health]) ──────
//  Spec §5.3 says "ideals come from the theme band table (§4)" -- but that table is rewritten in
//  PHASE 3 (`ArchetypeData.THEMES`), which this phase must not touch (taxonomy is explicitly out
//  of scope here). [COMMANDER_AXIS_IDEALS] below is this phase's OWN interim table: every entry
//  that maps 1:1 onto an EXISTING `ArchetypeData.THEMES` role band reuses that band's `ideal`
//  verbatim (cited inline); every entry with no existing band anywhere (most Phase-1 producer
//  roles: `lifegain_source`, `counters_source`, etc. -- genuinely new Phase 1 vocabulary with zero
//  prior calibration) uses a clearly-flagged PROVISIONAL number in the same order of magnitude as
//  its comparable already-banded sibling roles. This is Phase-3/4 interim scaffolding, not a
//  re-derivation of the real bands -- superseded the moment Phase 3 formally bands these roles.
//
//  These are real-Commander-deck-scale numbers (~65 real nonland cards, the density
//  [AnalysisWeights]'s own calibration KDoc cites). [effectiveIdeal] scales them down
//  PROPORTIONALLY by the evaluated deck's actual non-land count against that same documented
//  baseline -- this is NOT a threshold reverse-engineered from this corpus's pass/fail outcomes
//  (the anti-pattern this plan explicitly forbids): it is a straightforward density-normalization
//  identical in spirit to [ThemeDefinition.sixtyScale], which already halves a Commander-calibrated
//  band for a 60-card shell for exactly this reason (fewer total cards -> proportionally fewer
//  copies needed to "count"). The corpus's OWN documented density convention (~20-28 distinct
//  nonland cards per fixture, see `AnalysisV3TestSupport.kt`'s header) is simply an especially
//  compressed case of the SAME general principle, decided before any fixture was run against this
//  code, not tuned afterward to force a pass.
// ═══════════════════════════════════════════════════════════════════════════════

/** One directed producer -> payoff (or amplifier -> producer, see [DeckSynergyGraph]'s KDoc)
 * connection on a single [axis]. [weight] is the product of the two matcher confidences involved
 * (never re-derived from raw oracle text -- both confidences come from the pre-computed
 * [CardAxisProfile] for each card). */
data class SynergyEdge(val fromCardId: String, val toCardId: String, val axis: AxisKey, val weight: Float)

/** One axis's health snapshot for a specific deck. [amplifierCopies] is tracked but, per spec
 * §5.3's formula AS WRITTEN, deliberately NOT a factor in [health] itself -- see [health]'s own
 * KDoc. [producerIdeal] (Deck Analysis Engine v3, PHASE 4) is this axis's already deck-size-scaled
 * ([effectiveIdeal]) producer ideal -- exposed so [AnalysisEngine.evaluateSynergy]'s `axisHealth`
 * component (spec §7: "weightedMean(health(axis) for live axes, weight = axis producer ideal)")
 * can weight without re-deriving the ideal table a second time in a different file. */
data class AxisState(
    val axis: AxisKey,
    val producerCopies: Int,
    val payoffCopies: Int,
    val amplifierCopies: Int,
    val producerIdeal: Int,
    val health: Float,
    val isLive: Boolean,
)

/**
 * The four anti-synergy conflicts (spec §5.4) THIS phase is responsible for detecting. The
 * table's 5th row ("Anti-role overload") is deliberately NOT a 5th variant here -- it is already
 * covered by the existing [Finding.AntiRoleOverMax] (P3), and the spec's own table says "keep",
 * not "add a duplicate". Detection lives here (Phase 2); PHASE 4 is the first (and, as of this
 * phase, only) consumer that turns these into both a bounded P4 score penalty AND a user-facing
 * [Finding] (one [Finding] variant per [SynergyConflict] variant, see
 * [AnalysisEngine.evaluateSynergy]) -- Phase 2 itself never scored or surfaced these.
 */
sealed interface SynergyConflict {
    /** `graveyard_hate` >= 3 copies while the deck's OWN `GRAVEYARD` axis is live -- the deck is
     * fighting its own graveyard plan. */
    data class SelfDefeatingGraveyardHate(val graveyardHateCopies: Int) : SynergyConflict

    /** [producerCopies] already clears [producerIdeal] on [axis], but the axis has zero payoffs --
     * "9 token generators, 0 payoffs" is real, actionable deckbuilding advice (spec §5.4). */
    data class OrphanProducers(val axis: AxisKey, val producerCopies: Int, val producerIdeal: Int) : SynergyConflict

    /** [payoffCopies] already clears [payoffIdeal] on [axis], but producers sit below 25% of
     * their own ideal -- the mirror image of [OrphanProducers]. */
    data class OrphanPayoffs(val axis: AxisKey, val payoffCopies: Int, val payoffIdeal: Int) : SynergyConflict

    /** `stax_piece` >= 8 while `card_draw` >= CONTROL's own resolved ideal for this format -- a
     * lock piece count that also chokes the deck's OWN card-draw engine. [controlCardDrawIdeal] is
     * read live from [ArchetypeData] (never a hand-duplicated constant) so this conflict never
     * silently drifts from CONTROL's real band. */
    data class StaxVsOwnEngine(val staxPieceCopies: Int, val cardDrawCopies: Int, val controlCardDrawIdeal: Int) : SynergyConflict
}

/**
 * The full shadow-mode synergy graph for one deck evaluation. [edges] carries BOTH edge kinds
 * spec §5.2 calls for: a regular producer -> payoff edge on a shared axis, and an
 * amplifier -> producer edge for every axis a card [RoleSpec.amplifies] (this direction --
 * "the amplifier boosts what is already producing the resource" -- is a documented judgment call,
 * since the spec's 4-field [SynergyEdge] shape has no separate "kind" discriminator to pin the
 * direction unambiguously; it does not affect [axes] or [conflicts], which are computed from raw
 * per-axis copy counts, never by walking [edges]). "Cost reduction" is NOT a third edge kind: spec
 * §5.2 already routes `cost_reducer` through `amplifies = {"SPELLS","ARTIFACTS"}` (Phase 1's
 * `AXIS_AMPLIFIES` table), so it is produced by the SAME amplifier -> producer loop as `anthem`/
 * `recursion`/`protection`, not a special case.
 */
data class DeckSynergyGraph(
    val edges: List<SynergyEdge>,
    val axes: List<AxisState>,
    val conflicts: List<SynergyConflict>,
    /**
     * Deck Analysis Engine v3, PHASE 5 (UI) — per-axis card attribution, keyed the same as every
     * entry in [axes]. Purely additive/read-only: built from the SAME per-card [CardAxisProfile]
     * maps [build] already computes to sum [AxisState.producerCopies]/[AxisState.payoffCopies] —
     * this just keeps the per-card identity instead of collapsing it to a sum, so the Analysis tab
     * can render "producers -> payoffs" with the actual contributing cards (spec's own framing:
     * "you have 9 token generators and 0 payoffs" is real, actionable advice). Never read by any
     * pillar or [health]/[conflicts] computation — display-only, zero scoring impact. Empty map for
     * any axis not present in [axes] (never happens in practice — [build] always populates one entry
     * per axis it returns in [axes]).
     */
    val axisBreakdown: Map<AxisKey, AxisCardBreakdown> = emptyMap(),
)

/**
 * One axis's card-level producer/payoff attribution (Deck Analysis Engine v3, PHASE 5 UI) — the
 * per-card counterpart to [AxisState]'s aggregate [AxisState.producerCopies]/
 * [AxisState.payoffCopies]. [CardContribution.confidence] is the card's own `produces`/`consumes`
 * weight for this axis (mirrors [CardAxisProfile]'s per-axis `Float` maps verbatim, never
 * re-derived). Amplifier-only cards (no `produces`/`consumes` entry for this axis, only
 * `amplifies`) are deliberately absent from both lists — they modulate [AxisState.health] but are
 * not themselves a producer or a payoff (see [AxisState.amplifierCopies]'s own KDoc).
 */
data class AxisCardBreakdown(
    val producers: List<CardContribution>,
    val payoffs: List<CardContribution>,
)

object SynergyGraph {

    /** Spec §5.3: an axis is live once its [AxisState.health] clears this bar. */
    const val AXIS_LIVE_THRESHOLD = 0.45f

    /** Spec §5.3: `redundancyFactor` is clamped to this range so it modulates [health] rather than
     * dominating it. */
    private const val REDUNDANCY_FACTOR_MIN = 0.6f
    private const val REDUNDANCY_FACTOR_MAX = 1.0f

    /** Opening hand (7) + 3 draws by turn 4 (spec §5.3's own parenthetical). */
    private const val CARDS_SEEN_BY_TURN_4 = 10

    /** Spec §5.3: "+1 for the commander in Commander" -- the commander is always accessible from
     * the command zone, effectively one extra guaranteed "draw" toward turn 4. */
    private const val COMMANDER_BONUS_DRAW = 1

    /** [AnalysisWeights]'s own calibration KDoc cites this as the realistic Commander nonland
     * density; [ArchetypeData] bands are calibrated at this scale. */
    private const val REALISTIC_COMMANDER_NONLAND = 65

    /** Analogous 60-card baseline (a ~23-land 60-card deck runs ~37 nonland cards) -- a documented
     * judgment call, same sourcing discipline as [ArchetypeData]'s own uncited WS9.3 numbers. */
    private const val REALISTIC_SIXTY_NONLAND = 37

    private const val TRIBE_AXIS_PREFIX = "TRIBE:"

    /** Every axis in the vocabulary that is NOT deck-relative (spec §5.2's table, minus `TRIBE:x`,
     * which is dynamically keyed per deck -- see [buildAxisState]). Declaration order is this
     * phase's own [DeckSynergyGraph.axes] display order. */
    private val STATIC_AXES: List<AxisKey> = listOf(
        "LIFE", "DEATH", "TOKENS", "COUNTERS", "LANDFALL", "GRAVEYARD", "ETB", "SPELLS",
        "ARTIFACTS", "ENCHANTMENTS", "ATTACHED", "ATTACK", "PLANESWALKERS", "GROUP", "MILL_OPP",
        "LOCK",
    )

    /** PHASE 4b -- deliberately NOT in [STATIC_AXES]. [InferDeckArchetypeUseCase] calls [build]
     * independently (its OWN `graph.axes`/theme-detection pass) and must not see this axis at all --
     * a first attempt that put ENGINE in [STATIC_AXES] unconditionally silently changed that
     * resolver's own "dominant axis by health" macro signal with ZERO resolver code touched (see
     * `project_deck_analysis_v3_phase4b_p4_corrective` memory). [build]'s own `includeEngineAxis`
     * parameter (default `false`) keeps every EXISTING call site (including the resolver's, a plain
     * 2-arg call) a byte-for-byte no-op; only [AnalysisEngine.evaluate]'s own call opts in. Safe to
     * leave [buildCardProfile]'s ENGINE credit unconditional (always computed into `produces`/
     * `consumes`/`amplifies`) since the resolver never reads [DeckSynergyGraph.edges], only `.axes`
     * -- excluding the axis KEY from [axisKeys] is sufficient, no need to thread the flag deeper. */
    private const val ENGINE_AXIS = "ENGINE"

    private data class AxisIdeal(val producerIdeal: Int, val payoffIdeal: Int)

    /** See this file's header for the sourcing discipline (cited where a real band exists,
     * PROVISIONAL where flagged). All Commander-scale; see [effectiveIdeal] for the density scale.
     *
     * Deck Analysis Engine v3, Phase 3 RECONCILIATION (2026-08-26): [ArchetypeData.THEMES]/
     * [ArchetypeData.POSTURES] were rewritten this phase (spec §4.2/§3) -- every citation below was
     * re-checked against the NEW bands. `ATTACHED`'s producer ideal is now cited from the real
     * `EQUIPMENT` theme (replacing the old "split from the since-retired VOLTRON theme" citation);
     * `GROUP`'s producer ideal is now cited from [PostureId.GROUP_SLUG] (VOLTRON/GROUP_HUG/
     * GROUP_SLUG moved out of [ArchetypeData.THEMES] into [ArchetypeData.POSTURES] this phase, but
     * the underlying `group_effect`/`equipment` numbers themselves are unchanged, so neither ideal
     * VALUE moved, only its citation). Every other entry's citation target either didn't change
     * shape this phase or has no new real band to cite yet (still PROVISIONAL, unchanged from
     * Phase 2) -- superseding this WHOLE interim table with a real per-axis calibration remains
     * future work, per this file's own header. */
    private val COMMANDER_AXIS_IDEALS: Map<AxisKey, AxisIdeal> = mapOf(
        "LIFE" to AxisIdeal(producerIdeal = 8 /* PROVISIONAL */, payoffIdeal = 12 /* LIFEGAIN lifegain_payoff ideal */),
        "DEATH" to AxisIdeal(producerIdeal = 6 /* ARISTOCRATS sac_outlet ideal */, payoffIdeal = 10 /* ARISTOCRATS death_payoff ideal */),
        "TOKENS" to AxisIdeal(producerIdeal = 12 /* TOKENS token_generator ideal */, payoffIdeal = 8 /* PROVISIONAL, combined death/counters/combat payoff -- TOKENS' own new `anthem` band (spec §4.2) is the AMPLIFIER side, not this payoff side, so it cannot supersede this citation */),
        "COUNTERS" to AxisIdeal(producerIdeal = 8 /* PROVISIONAL */, payoffIdeal = 13 /* PLUS1_COUNTERS counters_payoff ideal */),
        "LANDFALL" to AxisIdeal(producerIdeal = 8 /* PROVISIONAL, land-based-ramp subset of LANDFALL's ramp=16 */, payoffIdeal = 11 /* LANDFALL landfall_payoff ideal */),
        "GRAVEYARD" to AxisIdeal(producerIdeal = 10 /* REANIMATOR graveyard_enabler ideal */, payoffIdeal = 8 /* PROVISIONAL, combined reanimation/self_mill_payoff/recursion */),
        "ETB" to AxisIdeal(producerIdeal = 10 /* BLINK blink_effect ideal */, payoffIdeal = 18 /* BLINK etb_payoff ideal */),
        "SPELLS" to AxisIdeal(producerIdeal = 14 /* PROVISIONAL, instant/sorcery density */, payoffIdeal = 13 /* SPELLSLINGER spell_payoff ideal */),
        "ARTIFACTS" to AxisIdeal(producerIdeal = 14 /* PROVISIONAL, artifact density */, payoffIdeal = 12 /* ARTIFACTS artifact_payoff ideal */),
        "ENCHANTMENTS" to AxisIdeal(producerIdeal = 10 /* PROVISIONAL, enchantment density */, payoffIdeal = 10 /* ENCHANTRESS enchantment_payoff ideal */),
        "ATTACHED" to AxisIdeal(producerIdeal = 11 /* RECONCILED: EQUIPMENT theme's own `equipment` ideal (spec §4.2), replacing the old VOLTRON-derived PROVISIONAL 8 */, payoffIdeal = 5 /* RECONCILED: EQUIPMENT theme's own `evasion` ideal (the closest real payoff-side citation; `combat_payoff` still has no dedicated band) */),
        "ATTACK" to AxisIdeal(producerIdeal = 10 /* PROVISIONAL */, payoffIdeal = 6 /* PROVISIONAL */),
        "PLANESWALKERS" to AxisIdeal(producerIdeal = 14 /* SUPERFRIENDS planeswalker ideal */, payoffIdeal = 4 /* PROVISIONAL, counters_source via proliferate */),
        "GROUP" to AxisIdeal(producerIdeal = 15 /* RECONCILED: PostureId.GROUP_SLUG's own group_effect ideal (spec §3) -- GROUP_HUG/GROUP_SLUG moved out of ArchetypeData.THEMES into ArchetypeData.POSTURES this phase; GROUP_SLUG's 15 is the higher/more specific of the two siblings' identical-shaped bands */, payoffIdeal = 6 /* PROVISIONAL */),
        "MILL_OPP" to AxisIdeal(producerIdeal = 14 /* spec §4.2 explicit: mill_opponent 10-14-18 */, payoffIdeal = 1 /* payoff-optional -- unused, see payoffPolicyFor */),
        // Final engine-correction run, DEFECT 1 -- payoff-optional (see [payoffPolicyFor] and
        // [ArchetypeRoleClassifier.AXIS_PRODUCES]'s own KDoc on the "stax_piece" entry for the full
        // rationale). producerIdeal cited from PRISON's OWN Commander stax_piece ideal
        // (ArchetypeData.ARCHETYPES[PRISON][COMMANDER].roleTargets["stax_piece"].ideal = 14) --
        // this table is Commander-scale throughout (see this file's own header), scaled per-format
        // by [effectiveIdeal] exactly like every other row. payoffIdeal unused, same convention as
        // MILL_OPP above.
        "LOCK" to AxisIdeal(producerIdeal = 14, payoffIdeal = 1 /* payoff-optional -- unused, see payoffPolicyFor */),
        // Deck Analysis Engine v3, PHASE 4b (macro-identity path -- see this file's
        // buildCardProfile() KDoc on the ENGINE axis credit below for the full rationale and for
        // WHY the producer role set is narrow, not the obvious "ramp/card_draw/tutor" reading).
        // NOT a per-theme citation like every row above -- this axis exists for every macro that
        // runs interaction, not one theme, so its ideal is the average of the ONLY 2 macros with a
        // real Commander band for either producer role (ArchetypeData.ARCHETYPES):
        //   producer (counterspell/protection ideal): CONTROL counterspell=9, COMBO protection=6,
        //   PRISON protection=5 -- average 6.67, rounded to 7.
        //   payoff (finisher ideal): AGGRO 9, CONTROL 6, MIDRANGE 11, COMBO 5, PRISON 5 --
        //   average 7.2, rounded to 7 (unchanged from the first iteration -- finisher itself was
        //   never the problem, see buildCardProfile()'s KDoc).
        "ENGINE" to AxisIdeal(producerIdeal = 7, payoffIdeal = 7),
    )

    /** TRIBAL theme's `tribe_members`/`tribe_payoff` ideals (ArchetypeData), reused verbatim for
     * every `TRIBE:<subtype>` axis regardless of which subtype is dominant. */
    private const val TRIBE_PRODUCER_IDEAL = 28
    private const val TRIBE_PAYOFF_IDEAL = 9

    private enum class PayoffPolicy { REQUIRED, OPTIONAL, OPTIONAL_WHEN_AMPLIFIED }

    /** Spec §5.3: "Two axes are payoff-optional (MILL_OPP, and TRIBE:x with a lord present) --
     * mark them explicitly in the table rather than special-casing in code." This IS that table;
     * [health] reads it uniformly, no `if` branch dedicated to either axis lives in the edge/health
     * loops themselves. */
    private fun payoffPolicyFor(axis: AxisKey): PayoffPolicy = when {
        axis == "MILL_OPP" -> PayoffPolicy.OPTIONAL
        // Final engine-correction run, DEFECT 1 -- LOCK (stax_piece) has no dedicated payoff role
        // at all (see [ArchetypeRoleClassifier.AXIS_PRODUCES]'s own KDoc), the same "the producers
        // ARE the win condition" shape as MILL_OPP.
        axis == "LOCK" -> PayoffPolicy.OPTIONAL
        axis.startsWith(TRIBE_AXIS_PREFIX) -> PayoffPolicy.OPTIONAL_WHEN_AMPLIFIED
        else -> PayoffPolicy.REQUIRED
    }

    private val ROLE_SPECS_BY_KEY: Map<RoleKey, RoleSpec> by lazy { ArchetypeRoleClassifier.ROLE_SPECS.associateBy { it.key } }

    /** PHASE 4b fix (see [buildConflicts]'s own inline KDoc): the 3 axes whose spec §5.2 producer
     * cell is "type-line density · &lt;dedicated role&gt;" -- [OrphanProducers] conflict detection
     * for these axes reads the dedicated role's own count only, never the density-inflated
     * [AxisState.producerCopies]. */
    private val DENSITY_PRODUCER_AXES: Map<AxisKey, RoleKey> = mapOf(
        "SPELLS" to "spell_copy",
        "ARTIFACTS" to "treasure_source",
        "ENCHANTMENTS" to "aura_buff",
    )

    /** A card whose oracle text puts a land onto the battlefield IF it already carries the
     * pre-existing `ramp` role (reuses [ArchetypeRoleClassifier]'s own vocabulary, never a second
     * one) -- resolves the LANDFALL "land-based ramp" producer cell (Phase 1 finding #2). A mana
     * rock/dork carries `ramp` but never matches this text, so it correctly stays out of LANDFALL. */
    private val LAND_RAMP_ORACLE = Regex("(search[^.]*land[^.]*battlefield|put[^.]*land[^.]*battlefield)")

    private fun isLandBasedRamp(card: Card, roleConfidence: Map<RoleKey, Float>): Boolean {
        if ((roleConfidence["ramp"] ?: 0f) <= 0f) return false
        val oracle = card.oracleText?.lowercase() ?: return false
        return LAND_RAMP_ORACLE.containsMatchIn(oracle)
    }

    /** One card's pre-computed axis signal -- built ONCE per unique [DeckEntry] (never per copy,
     * never re-scanning oracle text inside the O(n^2) edge loop -- task 5's perf requirement).
     * `Confidence` maps use [AxisKey] with the TRIBE substitution already applied (`"TRIBE:elf"`,
     * never the generic `"TRIBE"` a [RoleSpec] declares). */
    private data class CardAxisProfile(
        val cardId: String,
        val quantity: Int,
        val produces: Map<AxisKey, Float>,
        val consumes: Map<AxisKey, Float>,
        val amplifies: Map<AxisKey, Float>,
    )

    private fun MutableMap<AxisKey, Float>.credit(axis: AxisKey, confidence: Float) {
        val prev = this[axis] ?: 0f
        if (confidence > prev) this[axis] = confidence
    }

    /** SPELLS/ARTIFACTS/ENCHANTMENTS type-line density (Phase 1 finding #2) -- a raw structural
     * signal, not a role classification, per spec §5.2's own table listing it separately from the
     * role-based producers on those same axes. */
    private fun structuralProducerAxes(card: Card): List<AxisKey> = buildList {
        if (card.typeLine.contains("Instant", ignoreCase = true) || card.typeLine.contains("Sorcery", ignoreCase = true)) add("SPELLS")
        if (card.typeLine.contains("Artifact", ignoreCase = true)) add("ARTIFACTS")
        if (card.typeLine.contains("Enchantment", ignoreCase = true)) add("ENCHANTMENTS")
    }

    private fun buildCardProfile(
        entry: DeckEntry,
        dominantTribeAxis: AxisKey?,
        dominantTribeKey: String?,
    ): CardAxisProfile {
        val card = entry.card
        val produces = mutableMapOf<AxisKey, Float>()
        val consumes = mutableMapOf<AxisKey, Float>()
        val amplifies = mutableMapOf<AxisKey, Float>()

        val roleConfidence = ArchetypeRoleClassifier.classify(card)
        roleConfidence.forEach { (roleKey, confidence) ->
            if (confidence <= 0f) return@forEach
            val spec = ROLE_SPECS_BY_KEY[roleKey] ?: return@forEach
            spec.produces.forEach { axis -> produces.credit(substituteTribe(axis, dominantTribeAxis), confidence) }
            spec.consumes.forEach { axis -> consumes.credit(substituteTribe(axis, dominantTribeAxis), confidence) }
            spec.amplifies.forEach { axis -> amplifies.credit(substituteTribe(axis, dominantTribeAxis), confidence) }
        }

        // Structural additions this phase resolves without a RoleSpec (see file header).
        structuralProducerAxes(card).forEach { axis -> produces.credit(axis, 1f) }
        if (isLandBasedRamp(card, roleConfidence)) produces.credit("LANDFALL", roleConfidence.getValue("ramp"))

        // ── ENGINE axis (Deck Analysis Engine v3, PHASE 4b -- macro-identity path) ──────────────
        // Phase 4's own gate finding: NONE of a macro's own core roles carry ANY axis membership --
        // Phase 1's 15 axes deliberately model THEMES, not macro identity, so a themeless CONTROL/
        // PRISON/COMBO deck was structurally unable to light a single axis no matter how well built.
        //
        // FIRST ATTEMPT (iteration 1, abandoned -- see `project_deck_analysis_v3_phase4b_p4_corrective`
        // memory for the full post-mortem): producers = ramp/card_draw/tutor, payoff = finisher.
        // MEASURED to regress the negative fixture's own P4 UP (52 -> 93) and flip its macro from
        // `null` to `COMBO` -- a hand audit of that fixture's own card list showed it runs ~8 ramp,
        // ~8 card_draw, ~5 tutor, ~7 finisher, STATISTICALLY INDISTINGUISHABLE from a real CONTROL
        // build's own counts. Ramp/card_draw/tutor/finisher are not niche roles -- they are exactly
        // the staples EVERY well-optimized Commander deck of ANY archetype accumulates, "goodstuff"
        // included by construction. No producerIdeal threshold on that role set can discriminate
        // "built as a team" from "independently strong staples that happen to share a common role"
        // when the role itself is universal -- this was a wrong-signal problem, not a miscalibration.
        //
        // REVISED (this version): producers are counterspell/protection ONLY -- roles genuinely
        // absent from a random goodstuff pile (verified against every corpus fixture: the negative
        // fixture runs ZERO of either, by the fixture author's own deliberate choice, while UW
        // Control runs 10 counterspells and Grand Arbiter runs 5 protection pieces). `finisher`
        // alone was never the weak link; pairing it with a NARROW producer half fixes the
        // discrimination problem without touching `finisher` at all. Deliberately NOT re-checking
        // "do you have enough of role X" (P3's own job) -- this is the SAME multiplicative
        // producer x payoff gate every other axis already uses, just with a producer role pair that
        // actually distinguishes intent from incidental staple density.
        //   - producers: counterspell / protection -- narrow, deliberate roles: a deck doesn't
        //     accidentally run several dedicated protection pieces or a stack of counterspells the
        //     way it accidentally accumulates format-staple ramp/removal/draw.
        //   - payoff: finisher -- what the deck is protecting/holding up mana for.
        //   - amplifiers: removal_spot / removal_mass / ramp / card_draw / tutor -- these get real,
        //     EARNED edges (coverage/connectivity credit) but do NOT affect [health]/liveness at all
        //     (spec §5.3: amplifierCopies is tracked, never a health factor). An amplifier only
        //     creates an edge if a real producer already exists on the axis -- for a deck with zero
        //     counterspell/protection (the negative fixture's own case), `produces` for ENGINE stays
        //     completely empty, so NO amplifier edges form either: coverage/connectivity/health all
        //     read 0, exactly matching "zero live axes must hold". `stax_piece` deliberately
        //     EXCLUDED even as an amplifier: crediting it here would contradict
        //     `SynergyConflict.StaxVsOwnEngine` (spec §5.4), which flags heavy stax as CHOKING the
        //     deck's own card_draw engine -- amplifying the very axis that conflict warns stax can
        //     undermine would be internally inconsistent.
        listOf("counterspell", "protection").forEach { key ->
            val confidence = roleConfidence[key] ?: 0f
            if (confidence > 0f) produces.credit("ENGINE", confidence)
        }
        (roleConfidence["finisher"] ?: 0f).let { confidence -> if (confidence > 0f) consumes.credit("ENGINE", confidence) }
        listOf("removal_spot", "removal_mass", "ramp", "card_draw", "tutor").forEach { key ->
            val confidence = roleConfidence[key] ?: 0f
            if (confidence > 0f) amplifies.credit("ENGINE", confidence)
        }

        // TRIBE:<subtype> -- deck-scoped, mirrors ArchetypeRoleClassifier's own tribe_members/
        // dominantTribeKey special-case (Phase 1 memory: "SynergyGraph will need to special-case
        // tribe production the same way P3 already does, not read it off a RoleSpec").
        if (dominantTribeAxis != null && dominantTribeKey != null) {
            if (dominantTribeKey in TribeDeriver.subtypeKeys(card)) produces.credit(dominantTribeAxis, 1f)
            // A card whose oracle text names the dominant tribe as a payoff ("Other Vampires you
            // control get +1/+1") is BOTH a structural tribe_payoff (consumer) AND a lord/anthem
            // (amplifier) for that tribe -- TribeDeriver.payoffTribeKeys is the SAME pre-existing,
            // already-tested helper DeckScorer.fit/profile already use for this exact purpose, not
            // a new detector.
            if (dominantTribeKey in TribeDeriver.payoffTribeKeys(card)) {
                consumes.credit(dominantTribeAxis, 1f)
                amplifies.credit(dominantTribeAxis, 1f)
            }
        }

        return CardAxisProfile(card.scryfallId, entry.quantity, produces, consumes, amplifies)
    }

    /** Substitutes the generic `"TRIBE"` axis a [RoleSpec] declares (e.g. `tribe_payoff.consumes`,
     * `anthem.amplifies`) for this deck's real `"TRIBE:<subtype>"` axis; every other axis passes
     * through unchanged. No-op (returns the input) when the deck has no dominant tribe. */
    private fun substituteTribe(axis: AxisKey, dominantTribeAxis: AxisKey?): AxisKey =
        if (axis == "TRIBE" && dominantTribeAxis != null) dominantTribeAxis else axis

    private fun effectiveIdeal(baseIdeal: Int, nonLandCount: Int, realisticBaseline: Int): Int =
        ((baseIdeal.toDouble() * nonLandCount / realisticBaseline).roundToInt()).coerceAtLeast(1)

    /**
     * Hypergeometric P(>= 1 success) drawing [sampleSize] cards (without replacement) from a
     * population of [populationSize] containing [successCount] successes. Computed as the
     * numerically-stable complement (product form) rather than via factorials/`C(n,k)`, which
     * would overflow for a 99-card population.
     */
    private fun hypergeometricAtLeastOne(populationSize: Int, successCount: Int, sampleSize: Int): Float {
        if (successCount <= 0 || populationSize <= 0) return 0f
        if (successCount >= populationSize) return 1f
        val n = sampleSize.coerceAtMost(populationSize)
        var probabilityOfZero = 1.0
        for (i in 0 until n) {
            val remainingFailures = (populationSize - successCount - i).toDouble()
            val remainingTotal = (populationSize - i).toDouble()
            if (remainingFailures <= 0.0) { probabilityOfZero = 0.0; break }
            probabilityOfZero *= remainingFailures / remainingTotal
        }
        return (1.0 - probabilityOfZero).toFloat()
    }

    private fun redundancyFactor(axis: AxisKey, deckSize: Int, producerCopies: Int, format: ArchetypeFormat): Float {
        val sampleSize = CARDS_SEEN_BY_TURN_4 + if (format == ArchetypeFormat.COMMANDER) COMMANDER_BONUS_DRAW else 0
        val raw = hypergeometricAtLeastOne(deckSize, producerCopies, sampleSize)
        return raw.coerceIn(REDUNDANCY_FACTOR_MIN, REDUNDANCY_FACTOR_MAX)
    }

    /**
     * Deck Analysis Engine v3, PHASE 4 (spec §7) -- the "consistency" P4 component: `hypergeometric
     * (>= 1 producer of dominant axis by turn 4)`. Reuses the EXACT same sample-size model
     * [redundancyFactor] uses (opening hand + 3 draws by turn 4, +1 for the Commander bonus draw),
     * deliberately WITHOUT that helper's `[0.6,1.0]` clamp -- spec §5.3 asks for the clamp
     * specifically so redundancy MODULATES rather than dominates axis HEALTH; spec §7 never asks
     * for one on this term, and P4's `consistency` is its own standalone `[0,1]` probability (a
     * genuinely near-zero producer count on the dominant axis should read as near-zero consistency,
     * not floor at 0.6). [producerCopies] is the caller's already-resolved dominant axis's raw
     * producer count (e.g. `graph.axes.maxByOrNull { it.health }?.producerCopies`) -- this function
     * does not pick the axis itself, mirroring [InferDeckArchetypeUseCase]'s own "max by health"
     * dominant-axis definition (Phase 3a) rather than inventing a second one.
     */
    fun turnFourDrawProbability(deckSize: Int, producerCopies: Int, format: ArchetypeFormat): Float {
        val sampleSize = CARDS_SEEN_BY_TURN_4 + if (format == ArchetypeFormat.COMMANDER) COMMANDER_BONUS_DRAW else 0
        return hypergeometricAtLeastOne(deckSize, producerCopies, sampleSize)
    }

    /**
     * Spec §5.3, implemented exactly as written (no re-derivation): `min(1, producer/producerIdeal)
     * * min(1, payoff/payoffIdeal) * redundancyFactor`. The payoff factor is forced to `1f` for a
     * [PayoffPolicy.OPTIONAL] axis (`MILL_OPP`) and for [PayoffPolicy.OPTIONAL_WHEN_AMPLIFIED]
     * (`TRIBE:x`) ONLY when at least one amplifier (lord) is present on that axis in THIS deck --
     * otherwise it falls back to the normal required-payoff computation. [amplifierCopies] is
     * tracked on [AxisState] for visibility but is NOT a multiplicative factor here -- the spec's
     * formula does not include it, and this phase must not "improve" the formula.
     */
    private fun health(
        axis: AxisKey,
        producerCopies: Int,
        payoffCopies: Int,
        amplifierCopies: Int,
        ideal: AxisIdeal,
        deckSize: Int,
        format: ArchetypeFormat,
    ): Float {
        val producerFactor = kotlin.math.min(1f, producerCopies.toFloat() / ideal.producerIdeal)
        val payoffFactor = when (payoffPolicyFor(axis)) {
            PayoffPolicy.REQUIRED -> kotlin.math.min(1f, payoffCopies.toFloat() / ideal.payoffIdeal)
            PayoffPolicy.OPTIONAL -> 1f
            PayoffPolicy.OPTIONAL_WHEN_AMPLIFIED ->
                if (amplifierCopies > 0) 1f else kotlin.math.min(1f, payoffCopies.toFloat() / ideal.payoffIdeal)
        }
        val redundancy = redundancyFactor(axis, deckSize, producerCopies, format)
        return producerFactor * payoffFactor * redundancy
    }

    /**
     * Builds the full shadow-mode synergy graph for [mainboard]. Pure, deterministic, computed
     * ENTIRELY from the mainboard's own role/structural signal -- never reads a resolved
     * [ResolvedArchetypeSkeleton] or archetype/theme (this runs in PASS 1, before either exists).
     *
     * Performance: `O(n^2)` over UNIQUE [DeckEntry]s (never per-copy, never per raw-oracle-text
     * rescan) -- a 250-unique-card deck is ~62,000 ordered-pair checks against small pre-computed
     * maps, trivial (see `SynergyGraphPerformanceTest`).
     *
     * @param includeEngineAxis PHASE 4b, default `false` -- see [ENGINE_AXIS]'s own KDoc. Only
     *   [AnalysisEngine.evaluate]'s call site passes `true`; every other (pre-existing) call site is
     *   unaffected by this parameter's addition.
     */
    fun build(mainboard: List<DeckEntry>, format: ArchetypeFormat, includeEngineAxis: Boolean = false): DeckSynergyGraph {
        val deckSize = mainboard.sumOf { it.quantity }
        val dominantTribeKey = ArchetypeRoleClassifier.dominantTribeKey(mainboard)
            .takeIf { ArchetypeRoleClassifier.tribeMemberCount(mainboard) > 0 }
        val dominantTribeAxis = dominantTribeKey?.let { "$TRIBE_AXIS_PREFIX${it.removePrefix(TribeDeriver.TRIBE_PREFIX)}" }

        val profiles = mainboard.map { entry -> buildCardProfile(entry, dominantTribeAxis, dominantTribeKey) }
        val profilesById = profiles // small deck sizes; linear scans below are fine at this N

        // ── Regular producer -> payoff edges ────────────────────────────────────────────────
        val edges = mutableListOf<SynergyEdge>()
        for (from in profilesById) {
            if (from.produces.isEmpty()) continue
            for (to in profilesById) {
                if (from.cardId == to.cardId) continue
                if (to.consumes.isEmpty()) continue
                val sharedAxes = from.produces.keys intersect to.consumes.keys
                sharedAxes.forEach { axis ->
                    val weight = (from.produces[axis] ?: 0f) * (to.consumes[axis] ?: 0f)
                    if (weight > 0f) edges += SynergyEdge(from.cardId, to.cardId, axis, weight)
                }
            }
        }
        // ── Amplifier -> producer edges (also covers "cost reduction", see DeckSynergyGraph KDoc) ──
        for (amp in profilesById) {
            if (amp.amplifies.isEmpty()) continue
            amp.amplifies.forEach { (axis, ampConfidence) ->
                profilesById.forEach { producer ->
                    if (producer.cardId == amp.cardId) return@forEach
                    val producerConfidence = producer.produces[axis] ?: return@forEach
                    edges += SynergyEdge(amp.cardId, producer.cardId, axis, ampConfidence * producerConfidence)
                }
            }
        }

        // ── Per-axis copy counts (quantity-weighted, rounded -- mirrors deckRoleCounts' own convention) ──
        val axisKeys = STATIC_AXES + listOfNotNull(dominantTribeAxis) + listOfNotNull(ENGINE_AXIS.takeIf { includeEngineAxis })
        val realisticBaseline = if (format == ArchetypeFormat.COMMANDER) REALISTIC_COMMANDER_NONLAND else REALISTIC_SIXTY_NONLAND
        val nonLandCount = mainboard.filterNot { BasicLandCalculator.isLand(it.card) }.sumOf { it.quantity }

        val axes = axisKeys.map { axis ->
            val producerCopies = profiles.sumOf { p -> (p.quantity * (p.produces[axis] ?: 0f)).toDouble() }.roundToInt()
            val payoffCopies = profiles.sumOf { p -> (p.quantity * (p.consumes[axis] ?: 0f)).toDouble() }.roundToInt()
            val amplifierCopies = profiles.sumOf { p -> (p.quantity * (p.amplifies[axis] ?: 0f)).toDouble() }.roundToInt()
            val ideal = if (axis.startsWith(TRIBE_AXIS_PREFIX)) {
                AxisIdeal(TRIBE_PRODUCER_IDEAL, TRIBE_PAYOFF_IDEAL)
            } else {
                COMMANDER_AXIS_IDEALS.getValue(axis)
            }
            val scaledIdeal = AxisIdeal(
                producerIdeal = effectiveIdeal(ideal.producerIdeal, nonLandCount, realisticBaseline),
                payoffIdeal = effectiveIdeal(ideal.payoffIdeal, nonLandCount, realisticBaseline),
            )
            val h = health(axis, producerCopies, payoffCopies, amplifierCopies, scaledIdeal, deckSize, format)
            AxisState(
                axis = axis,
                producerCopies = producerCopies,
                payoffCopies = payoffCopies,
                amplifierCopies = amplifierCopies,
                producerIdeal = scaledIdeal.producerIdeal,
                health = h,
                isLive = h >= AXIS_LIVE_THRESHOLD,
            )
        }

        val conflicts = buildConflicts(mainboard, axes, format)
        // PHASE 5 (UI) -- see AxisCardBreakdown's own KDoc: reuses the SAME `profiles` this function
        // already summed into axes[].producerCopies/payoffCopies, just kept per-card instead of
        // collapsed. Computed for every axis this call returns (STATIC_AXES + tribe + ENGINE when
        // included), never a superset -- display-only, no scoring path reads this map.
        val axisBreakdown = axisKeys.associateWith { axis ->
            AxisCardBreakdown(
                producers = profiles.filter { (it.produces[axis] ?: 0f) > 0f }
                    .map { CardContribution(it.cardId, it.quantity, it.produces.getValue(axis)) },
                payoffs = profiles.filter { (it.consumes[axis] ?: 0f) > 0f }
                    .map { CardContribution(it.cardId, it.quantity, it.consumes.getValue(axis)) },
            )
        }
        return DeckSynergyGraph(edges = edges, axes = axes, conflicts = conflicts, axisBreakdown = axisBreakdown)
    }

    /** Spec §5.4's 4 detectable conflicts (the 5th, anti-role overload, already exists as
     * [Finding.AntiRoleOverMax] -- see [SynergyConflict]'s own KDoc). Detection only; no penalty
     * is applied anywhere in this phase. */
    private fun buildConflicts(mainboard: List<DeckEntry>, axes: List<AxisState>, format: ArchetypeFormat): List<SynergyConflict> {
        val conflicts = mutableListOf<SynergyConflict>()
        val roleCounts = ArchetypeRoleClassifier.deckRoleCounts(mainboard)

        val graveyardHate = roleCounts["graveyard_hate"] ?: 0
        val graveyardAxis = axes.firstOrNull { it.axis == "GRAVEYARD" }
        if (graveyardHate >= 3 && graveyardAxis?.isLive == true) {
            conflicts += SynergyConflict.SelfDefeatingGraveyardHate(graveyardHate)
        }

        axes.forEach { axisState ->
            val ideal = if (axisState.axis.startsWith(TRIBE_AXIS_PREFIX)) {
                AxisIdeal(TRIBE_PRODUCER_IDEAL, TRIBE_PAYOFF_IDEAL)
            } else {
                COMMANDER_AXIS_IDEALS[axisState.axis] ?: return@forEach
            }
            val nonLandCount = mainboard.filterNot { BasicLandCalculator.isLand(it.card) }.sumOf { it.quantity }
            val realisticBaseline = if (format == ArchetypeFormat.COMMANDER) REALISTIC_COMMANDER_NONLAND else REALISTIC_SIXTY_NONLAND
            val producerIdeal = effectiveIdeal(ideal.producerIdeal, nonLandCount, realisticBaseline)
            val payoffIdeal = effectiveIdeal(ideal.payoffIdeal, nonLandCount, realisticBaseline)

            // Deck Analysis Engine v3, PHASE 4b fix -- SPELLS/ARTIFACTS/ENCHANTMENTS' producer side
            // is "type-line density (or role) · <dedicated role>" per spec §5.2's own table: for
            // AXIS HEALTH/coverage/connectivity, raw instant-sorcery/artifact/enchantment density is
            // a legitimate structural signal (spec's own words), left untouched above. But for THIS
            // conflict specifically, density made OrphanProducers(SPELLS) fire on 13/17 corpus
            // fixtures -- any deck with a normal removal/counterspell suite and NO dedicated
            // `spell_payoff` tripped it, a near-universal false positive ("Swords to Plowshares +
            // Brainstorm" is not a broken spellslinger deck). The single distinguishing signal of
            // genuine "built for this axis but forgot the payoff" deckbuilding intent is the
            // DEDICATED role, not incidental type-line count -- so for these 3 axes only, gate on
            // the dedicated-role-only count from [ArchetypeRoleClassifier.deckRoleCounts] (already
            // computed above for graveyard_hate/stax_piece/card_draw) instead of
            // [AxisState.producerCopies]. Same fix applied to ARTIFACTS (treasure_source) and
            // ENCHANTMENTS (aura_buff) for consistency -- both share the identical structural-
            // density-producer pattern spec §5.2's table gives SPELLS, so leaving them unfixed would
            // be the same bug left alive in two siblings.
            // Final engine-correction run, DEFECT 1 fix's own follow-on: a payoff-OPTIONAL axis
            // (MILL_OPP, LOCK -- see [payoffPolicyFor]) has NO dedicated payoff role in the
            // vocabulary AT ALL, by design ("the producers ARE the win condition", spec §5.3) --
            // its [AxisState.payoffCopies] is therefore STRUCTURALLY always 0, not merely low, so
            // the plain `payoffCopies == 0` check below would misfire as "orphaned" on every single
            // well-built stax/mill deck regardless of quality. Skip OrphanProducers entirely for
            // [PayoffPolicy.OPTIONAL] axes (mirrors [health]'s own unconditional `payoffFactor = 1f`
            // for this policy); [PayoffPolicy.OPTIONAL_WHEN_AMPLIFIED] (TRIBE:x) only skips when a
            // lord is actually present, the SAME condition [health] itself uses -- an unamplified
            // tribal axis with genuinely zero payoff is still a real orphan-producer finding.
            val skipOrphanCheck = when (payoffPolicyFor(axisState.axis)) {
                PayoffPolicy.OPTIONAL -> true
                PayoffPolicy.OPTIONAL_WHEN_AMPLIFIED -> axisState.amplifierCopies > 0
                PayoffPolicy.REQUIRED -> false
            }
            val dedicatedProducerKey = DENSITY_PRODUCER_AXES[axisState.axis]
            val orphanProducerCopies = if (dedicatedProducerKey != null) roleCounts[dedicatedProducerKey] ?: 0 else axisState.producerCopies
            if (!skipOrphanCheck && orphanProducerCopies >= producerIdeal && axisState.payoffCopies == 0) {
                conflicts += SynergyConflict.OrphanProducers(axisState.axis, orphanProducerCopies, producerIdeal)
            }
            if (axisState.payoffCopies >= payoffIdeal && axisState.producerCopies < producerIdeal * 0.25f) {
                conflicts += SynergyConflict.OrphanPayoffs(axisState.axis, axisState.payoffCopies, payoffIdeal)
            }
        }

        val staxPieces = roleCounts["stax_piece"] ?: 0
        val cardDraw = roleCounts["card_draw"] ?: 0
        val controlCardDrawIdeal = ArchetypeData.ARCHETYPES[ArchetypeId.CONTROL]?.get(format)?.roleTargets?.get("card_draw")?.ideal
        if (staxPieces >= 8 && controlCardDrawIdeal != null && cardDraw >= controlCardDrawIdeal) {
            conflicts += SynergyConflict.StaxVsOwnEngine(staxPieces, cardDraw, controlCardDrawIdeal)
        }

        return conflicts
    }
}
