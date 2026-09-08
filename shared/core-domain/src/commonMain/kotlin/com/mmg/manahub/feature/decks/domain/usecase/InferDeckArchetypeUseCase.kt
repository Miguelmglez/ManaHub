package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeData
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.ColorStrategyAffinity
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.DeckSynergyGraph
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.PostureId
import com.mmg.manahub.feature.decks.domain.engine.SynergyGraph
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ═══════════════════════════════════════════════════════════════════════════════
//  InferDeckArchetypeUseCase — Deck Analysis Engine v3, PHASE 3 (spec §2.1, §3, §4.3-4.4;
//  plan §4.3 "Prototype-based macro resolver")
//
//  REWRITTEN this phase. The old A.6 5-way hand-weighted-sum resolver (AGGRO/CONTROL/COMBO/RAMP/
//  TEMPO scores + a MIDRANGE-fallback-on-theme-hit rule) is GONE — see
//  `docs/deck-plan-selection-mechanics.md` (W12) for the full diagnosis of why it structurally
//  over-produced MIDRANGE. Replaced by:
//   1. Four continuous deck axes (clock/interaction/inevitability/linearity), the last one read
//      from Phase 2's [SynergyGraph] — the FIRST production call site to read that graph on the
//      normal evaluation path (Phase 2 kept it debug-only/unread).
//   2. A prototype-distance macro resolver over the 5 real macros (spec §2.1) — MIDRANGE now
//      competes in the SAME argmax as every other macro (it is the space's own centre), instead of
//      being an unscored fallback. `macro` is `null` when the winning margin over the runner-up
//      falls below [MACRO_AMBIGUITY_MARGIN] — this is the new "ambiguous / Custom" state that
//      replaces the old GENERIC fallback (spec: "never silently coerce").
//   3. A second-stage posture classifier (spec §3), run only once a real macro is fixed.
//   4. Theme detection reads LIVE AXES from the same [SynergyGraph] (spec §4.4) instead of a raw
//      role/type-line density normalized against a per-theme anchor — this is the single change
//      that kills the MIDRANGE bias (a handful of token generators with zero payoffs no longer
//      trips TOKENS).
//
//  `MACRO_CONFIDENCE_THRESHOLD`/`THEME_CONFIDENCE_THRESHOLD`/the per-theme anchor table/the
//  hardcoded MIDRANGE fallback branch are all DELETED — they have no analogue in this model.
//
//  COMMANDER PRIOR + RESEMBLANCE PROFILE (user-requested workstream, 2026-08-26, out of the
//  original v3 plan's scope — NOT phase 5, does not touch [evaluatePlanRoles]/[AnalysisWeights]/
//  [SynergyGraph.STATIC_AXES]/the P4 formula):
//   5. [commanderTags] (previously accepted and silently dropped — the only reference to the
//      param anywhere in this file was its own declaration) now supplies a BOUNDED prior over the
//      macro argmax (see [commanderMacroPrior]'s KDoc for the exact bias-not-override mechanics)
//      and over theme ranking (see [detectThemes]'s own commander-prior step). [commanderColorIdentity]
//      is a second, deliberately WEAKER prior (color-pie convention via the existing
//      [ColorStrategyAffinity] table — no new vocabulary), used ONLY as a tiebreak when the
//      commander carries no tag-based archetype signal at all. Both priors are gated on
//      `format == ArchetypeFormat.COMMANDER` — a 60-card deck structurally has no commander, so
//      this whole mechanism is a byte-for-byte no-op there regardless of what a caller passes.
//   6. The old binary "confident macro or a bare `null`" is replaced by [ArchetypeInference
//      .resemblance] — the SAME per-macro scores the argmax already computes (post-prior),
//      normalized to sum to 1.0 and ranked descending, ALWAYS populated (never empty, never
//      discarded below the ambiguity margin). `macro`/`confidence` are UNCHANGED in shape and
//      meaning (still `null`/`0f` below [MACRO_AMBIGUITY_MARGIN]) — [resemblance] is purely
//      additive, so no existing consumer of `macro`/`confidence` sees any behavior change.
//
//  Every numeric axis-normalization anchor below that is NOT directly transcribed from the spec's
//  own prototype tables is a documented judgment call, NOT re-derived from the phase 0 calibration
//  corpus (the plan's own anti-pattern rule: the corpus is a regression harness, not a training
//  set) — each cites either a spec table value directly, or a PRE-EXISTING [ArchetypeData] band
//  (never a Phase-0-fixture-derived number).
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * @property macro the winning macro archetype, or `null` when the margin over the runner-up falls
 *           below [InferDeckArchetypeUseCase.MACRO_AMBIGUITY_MARGIN] (spec §2.1: "surfaces as
 *           Custom or a hybrid label ... never silently coerce"). The engine still grades the deck
 *           against the bare generic baseline skeleton in this case — see
 *           [com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver.resolve]'s own
 *           `null` convention — only the DISPLAY label degrades.
 * @property posture the second-stage posture classification (spec §3), or `null` when no posture
 *           cleared its own detection margin. Detected using the argmax (nearest-prototype) macro
 *           as the reference band REGARDLESS of whether that macro's own margin cleared
 *           [InferDeckArchetypeUseCase.MACRO_AMBIGUITY_MARGIN] (spec §3 AMENDMENT, 2026-08-26,
 *           user-approved during phase 3a) — a deck's ramp/counterspell/recursion density is a
 *           structural fact independent of whether the macro resolution is confident, so
 *           [posture] can be non-null even when [macro] is `null`.
 * @property themes at most 2 confidently-live theme axes (spec §4.4), ordered by descending axis
 *           health (or, for the handful of themes with no dedicated Phase 2 axis, a direct
 *           role-band check — see [InferDeckArchetypeUseCase]'s theme-detection KDoc below).
 * @property confidence the winning macro's margin over the runner-up (spec §2.1: "confidence
 *           becomes a real margin instead of a theme score reported as if it were a macro score").
 *           `0f` when [macro] is `null`.
 * @property runnerUpMacro the macro that finished second, kept ONLY so a caller can render a hybrid
 *           label (`"Midrange / Control"`, spec §2.1) when [macro] is `null`. `null` whenever
 *           [macro] is non-null (a confident resolution needs no hybrid label) or no runner-up
 *           existed (a completely empty deck).
 * @property resemblance the FULL ranked, normalised resemblance across all 5 macros (share of
 *           each summing to 1.0, descending) — see this file's header "COMMANDER PRIOR +
 *           RESEMBLANCE PROFILE" section. Always populated (even when [macro] is `null`), so a
 *           caller never has to fall back to a bare "no plan" — there is always a nearest plan
 *           with an honest confidence beside it. Empty ONLY for a completely empty deck (mirrors
 *           [runnerUpMacro]'s own empty-deck case).
 */
data class ArchetypeInference(
    val macro: ArchetypeId?,
    val posture: PostureId?,
    val themes: List<ThemeId>,
    val confidence: Float,
    val runnerUpMacro: ArchetypeId? = null,
    val resemblance: List<MacroResemblance> = emptyList(),
)

/**
 * One macro's normalised resemblance share within [ArchetypeInference.resemblance] — e.g.
 * `MacroResemblance(ArchetypeId.AGGRO, 0.70f)` renders as "70% Aggro". All 5 shares in one
 * [ArchetypeInference.resemblance] list always sum to (approximately) `1.0`.
 */
data class MacroResemblance(val macro: ArchetypeId, val share: Float)

/** One deck's continuous position in the spec §2.1 prototype space — all 4 axes normalized to
 * (approximately) `[0,1]`. */
private data class DeckAxes(val clock: Float, val interaction: Float, val inevitability: Float, val linearity: Float)

/** One macro's target position in the same space (spec §2.1's own Commander/60-card tables). */
private data class MacroPrototype(val clock: Float, val interaction: Float, val inevitability: Float, val linearity: Float)

class InferDeckArchetypeUseCase {

    operator fun invoke(
        mainboard: List<DeckEntry>,
        format: ArchetypeFormat,
        commanderTags: List<CardTag> = emptyList(),
        commanderColorIdentity: Set<ManaColor> = emptySet(),
    ): ArchetypeInference {
        val nonLand = mainboard.filterNot { BasicLandCalculator.isLand(it.card) }
        val nonLandCount = nonLand.sumOf { it.quantity }
        if (nonLandCount == 0) return ArchetypeInference(macro = null, posture = null, themes = emptyList(), confidence = 0f)

        val roleCounts = ArchetypeRoleClassifier.deckRoleCounts(mainboard)
        val avgMv = nonLand.sumOf { it.card.cmc * it.quantity } / nonLandCount.toDouble()
        val graph = SynergyGraph.build(mainboard, format)

        val axes = computeAxes(roleCounts, nonLandCount, avgMv, graph, format)
        val prototypes = if (format == ArchetypeFormat.COMMANDER) COMMANDER_PROTOTYPES else SIXTY_PROTOTYPES

        // Commander prior (user-requested workstream, see this file's header) -- gated on
        // `format == COMMANDER` so a 60-card deck is byte-for-byte inert regardless of what a
        // caller passes for commanderTags/commanderColorIdentity (defense in depth: today's only
        // real caller already only supplies these for Commander decks, but a format deck has no
        // commander concept at all, so this is a structural invariant, not a caller convention).
        val macroPrior = if (format == ArchetypeFormat.COMMANDER) {
            commanderMacroPrior(commanderTags, commanderColorIdentity)
        } else {
            null
        }
        val scores = prototypes.mapValues { (archetypeId, prototype) ->
            val base = prototypeScore(axes, prototype)
            if (macroPrior != null && archetypeId == macroPrior.archetype) (base + macroPrior.bonus).coerceIn(0f, 1f) else base
        }

        val ranked = scores.entries.sortedByDescending { it.value }
        val best = ranked[0]
        val runnerUp = ranked.getOrNull(1)
        val margin = best.value - (runnerUp?.value ?: 0f)
        val isAmbiguous = margin < MACRO_AMBIGUITY_MARGIN

        val macro = if (isAmbiguous) null else best.key
        val runnerUpMacro = if (isAmbiguous) runnerUp?.key else null
        val resemblance = resemblanceProfile(scores)

        // spec §3 AMENDMENT (2026-08-26): posture is detected against the argmax macro (`best.key`,
        // the nearest prototype) regardless of whether the macro resolution itself is confident
        // enough to be DISPLAYED (`macro`, possibly null/ambiguous). A deck's ramp/counterspell/
        // recursion density is a structural fact independent of macro confidence -- gating posture
        // on `macro != null` hid every posture on any deck whose macro landed inside the ambiguity
        // margin (fixture 04/Omnath: ~24 ramp copies, 2x+ the RAMP threshold, never evaluated).
        // NOT extended with the commander prior this workstream -- out of Task 1's stated scope
        // ("inform macro and theme resolution"), and posture already has its own argmax-macro-
        // reference-band mechanism that this workstream was not asked to touch.
        val posture = detectPosture(best.key, format, roleCounts, avgMv, nonLandCount)
        val themePriors = if (format == ArchetypeFormat.COMMANDER) commanderThemePriors(commanderTags) else emptySet()
        val themes = detectThemes(format, roleCounts, graph, themePriors)

        return ArchetypeInference(
            macro = macro,
            posture = posture,
            themes = themes,
            confidence = margin.coerceIn(0f, 1f),
            runnerUpMacro = runnerUpMacro,
            resemblance = resemblance,
        )
    }

    // ── Commander prior (macro) ─────────────────────────────────────────────────────────────

    /** One resolved commander-prior candidate: the [archetype] it nudges toward and how strong
     * that nudge is. See [commanderMacroPrior]'s own KDoc. */
    private data class MacroPrior(val archetype: ArchetypeId, val bonus: Float)

    /**
     * Resolves the commander into a BOUNDED bias over exactly one macro's score, or `null` when
     * the commander carries no usable signal at all. Two tiers, tag-based STRICTLY preferred over
     * color-based (spec brief: "colour identity is a weak signal... use it as a tiebreak, not a
     * driver"):
     *
     * 1. **Tag-based** ([commanderTags], the commander card's own resolved tags — A.6 "commander
     *    tags" classifier signal, already threaded through every call site, previously unread).
     *    Reuses [DeckIdentitySeedTags.archetypeForTag] verbatim — the SAME reverse map the
     *    wizard's Direction-step chip taps already use — rather than authoring a second
     *    tag→archetype vocabulary parallel to [ArchetypeRoleClassifier]'s `RoleKey` table (a
     *    standing plan anti-pattern this workstream was explicitly told to avoid). Bonus =
     *    [MACRO_AMBIGUITY_MARGIN] itself, not a new invented constant: the prior is capped at
     *    EXACTLY the model's own definition of "a decisive margin," so it can turn a genuinely
     *    close/ambiguous call (deck-only gap <= [MACRO_AMBIGUITY_MARGIN]) into a confident one,
     *    but can never manufacture a win over a candidate the deck's OWN composition already
     *    separated by more than that — i.e. it can bias, but by construction cannot override a
     *    clear compositional read. Verified by hand against the calibration corpus (see this
     *    phase's memory file): a confidently-WRONG deck-only resolution (e.g. fixture 9 Rhys,
     *    deck-only margin .139) is pulled down to honest ambiguity by the correct-macro tag, but
     *    is NOT flipped outright to the (correct) tag-suggested macro — the deck's own card
     *    composition still dominates the outcome, exactly the "bias, never override" contract.
     * 2. **Color-based** ([commanderColorIdentity], used ONLY when step 1 found nothing). Reuses
     *    [ColorStrategyAffinity.forColors] verbatim (the existing color-pie/EDH-convention table,
     *    D9/WS1.4) rather than inventing a second color→archetype heuristic — the brief's own
     *    warning that "mono-red is not automatically aggro" is exactly what that curated table
     *    already encodes (mono-red's own best entry is AGGRO at weight 0.9, not a hardcoded
     *    certainty). Bonus is deliberately much smaller (roughly a third of the tag-based bonus) —
     *    color identity alone is the weakest signal available (a 2-5 color identity often has no
     *    single dominant archetype lean at all), so it should nudge, not decide. Verified by hand:
     *    at this magnitude it does not change ANY corpus fixture's displayed macro (see the gate
     *    report) — it only ever narrows an already-ambiguous margin.
     */
    private fun commanderMacroPrior(commanderTags: List<CardTag>, commanderColorIdentity: Set<ManaColor>): MacroPrior? {
        val tagArchetype = commanderTags.firstNotNullOfOrNull { DeckIdentitySeedTags.archetypeForTag(it) }
        if (tagArchetype != null) return MacroPrior(tagArchetype, MACRO_AMBIGUITY_MARGIN)
        if (commanderColorIdentity.isEmpty()) return null
        val colorArchetype = ColorStrategyAffinity.forColors(commanderColorIdentity).firstOrNull()?.archetype ?: return null
        return MacroPrior(colorArchetype, MACRO_PRIOR_COLOR_BONUS)
    }

    /** Reverse-maps [commanderTags] onto [ThemeId]s via [DeckIdentitySeedTags.themeForTag] (same
     * anti-duplicate-vocabulary discipline as [commanderMacroPrior]). Returns every match, not
     * just one — [detectThemes] only ever uses this to NUDGE an axis-derived candidate that
     * already exists, never to invent one, so multiple simultaneous theme priors are safe. */
    private fun commanderThemePriors(commanderTags: List<CardTag>): Set<ThemeId> =
        commanderTags.mapNotNull { DeckIdentitySeedTags.themeForTag(it) }.toSet()

    /** Normalises the post-prior [scores] (the SAME map the argmax above already computed) into
     * [MacroResemblance] shares summing to 1.0, descending — spec brief Task 2: "always emit the
     * ranked, normalised resemblance ... instead of discarding the scores below the ambiguity
     * margin."
     *
     * RESCALE (user-requested, 2026-08-26, same workstream as the commander prior above): the raw
     * `score / total` normalisation used to flatten every reading toward ~20% each, because all 5
     * [prototypeScore]s already live in a narrow high band (`score = 1 - dist/maxDist`, typically
     * ~0.6-0.9) — a textbook UW Control deck reported a 9-point-above-noise "29% Control", nine
     * points off "this deck looks 70% aggro" honesty. The scores themselves are not the problem;
     * the flat scale is. Fix: normalise each macro's ADVANTAGE over the worst-fitting one of the
     * five, not its raw share of the total. `advantage(m) = score(m) - min(scores)`, then
     * `share(m) = advantage(m) / sum(advantage)`. Chosen over a softmax specifically because it
     * introduces no new constant to calibrate, and because it preserves genuine ties: a real
     * Aggro/Midrange dead-heat still resolves to two comparably-large shares after rescaling
     * (verified by hand against the corpus, see this workstream's memory file), it does not
     * manufacture false confidence for either side of a tie.
     *
     * Accepted side effect: the single worst-fitting macro always reports exactly `0%` (its own
     * advantage over itself is zero by construction) — this is intentional, not a bug: it is
     * always the macro the deck resembles LEAST, so a 0% reading is honest, not a data loss.
     *
     * Degenerate case: when every score is IDENTICAL (`max == min`, so every advantage is `0f`,
     * `total <= 0f`) — a deck exactly equidistant from all 5 prototypes, or (unreachably in
     * practice) all 5 raw scores already at `0f` — falls back to an equal 20% split rather than
     * dividing by zero or fabricating a lean; this is the correct reading for a genuine 5-way tie,
     * not merely an error guard. */
    private fun resemblanceProfile(scores: Map<ArchetypeId, Float>): List<MacroResemblance> {
        val worst = scores.values.minOrNull() ?: 0f
        val advantages = scores.mapValues { (_, score) -> score - worst }
        val total = advantages.values.sum()
        return if (total <= 0f) {
            scores.keys.map { MacroResemblance(it, 1f / scores.size) }
        } else {
            advantages.entries.map { (id, advantage) -> MacroResemblance(id, advantage / total) }.sortedByDescending { it.share }
        }
    }

    // ── Axes (spec §2.1) ────────────────────────────────────────────────────────────────────

    /**
     * The 4 continuous deck axes. Every raw density sub-component is naturally `[0,1]` (a role's
     * copy count can never exceed the non-land count it's divided by); the 3 that are further
     * divided by an ANCHOR below normalize a "typical fully-committed density" up to ~1.0 — each
     * anchor is cited from an EXISTING [ArchetypeData] archetype band (never re-derived from the
     * Phase 0 calibration corpus), the same discipline [SynergyGraph]'s own `effectiveIdeal`
     * scaling already uses.
     *
     * PHASE 3a-CALIBRATION (2026-08-26): every anchor is now a PAIR, one per [ArchetypeFormat] —
     * the pre-calibration code used a single Commander-derived anchor for BOTH formats even though
     * [ArchetypeId] role bands scale very differently by format (Commander ~65 realistic nonland
     * vs. a 60-card deck's ~35-40), which meant a 60-card deck's raw role densities (naturally
     * higher, since the same absolute role count divides into far fewer total slots) were being
     * measured against a Commander-scaled "fully committed" reference point and read as far more
     * extreme than they really are. Confirmed empirically: fixture 14 (Mono-Red Burn, 60-card)
     * measured `interaction=0.833` under the single Commander anchor (0.6) before this fix — a
     * clearly wrong reading for an aggro-burn deck — because 24 of its 48 nonland cards carry a
     * `removal_spot`-classified burn spell (a real classifier characteristic of Modern burn, not a
     * fixture-authoring error: these ARE flexible burn/removal spells), which is a huge RAW density
     * even though it says little about the deck's true reactive/proactive posture.
     *
     * Every anchor below is derived the SAME way: `density = sum of the archetype's own role-band
     * IDEALS / (format deck size - that SAME archetype's own lands-band ideal)`, then
     * `anchor = density / thatArchetype'sOwnPrototypeValueForThisAxis` (spec §2.1's own table) —
     * self-consistent (the nonland-count denominator comes from the SAME archetype's own lands
     * band, never a borrowed convention from an unrelated subsystem) and never touches a Phase 0
     * fixture.
     */
    private fun computeAxes(roleCounts: Map<String, Int>, nonLandCount: Int, avgMv: Double, graph: DeckSynergyGraph, format: ArchetypeFormat): DeckAxes {
        fun density(key: String): Float = (roleCounts[key] ?: 0) / nonLandCount.toFloat()
        val isCommander = format == ArchetypeFormat.COMMANDER

        // clock: threat_early + evasion density (fast-clock signals) + an inverted-curve bonus
        // (spec: "average MV (inverted)"). CLOCK_DENSITY_ANCHOR derived from AGGRO's OWN band per
        // format (AGGRO's own prototype clock is the most extreme of the 5, 0.85/0.90, so it is the
        // natural archetype to anchor this axis against):
        //  - Commander: threat_early ideal 18 / nonland (100 - AGGRO's own 35 lands ideal = 65)
        //    = 0.277 density; AGGRO's own curveDelta-free curve ideal 2.1 gives curveLowBonus =
        //    1 - 2.1/4 = 0.475 (contributes 0.35*0.475 = 0.166 on its own, no anchor needed); AGGRO
        //    Commander bands no `evasion` role at all, so the evasion term is 0 at AGGRO's own
        //    ideal. Solving `0.85 = 0.45*(0.277/A) + 0.166` gives A = 0.182.
        //  - 60-card: threat_early ideal 20 / nonland (60 - AGGRO's own 21 lands ideal = 39) =
        //    0.513 density; curve ideal 1.8 gives curveLowBonus = 0.55. Solving
        //    `0.90 = 0.45*(0.513/A) + 0.35*0.55` gives A = 0.326 — a much higher raw-density
        //    reference point than Commander's, which is exactly the format difference a single
        //    shared anchor was missing (a 60-card aggro deck legitimately runs proportionally many
        //    more early threats than a 99-card Commander deck can).
        //  `evasion` has no archetype band anywhere (AGGRO does not band it; only the EQUIPMENT
        //  theme and VOLTRON posture do, neither a clean per-format-archetype citation for THIS
        //  axis) — it stays on the SAME anchor as threat_early, a judgment call inherited unchanged
        //  from the pre-calibration code (both represent "aggressive board commitment" at a similar
        //  order of magnitude).
        // Final engine-correction run, DEFECT 3: `direct_damage` (burn/reach spells -- "deals X
        // damage to any target/target player") folded into the SAME 0.20-weighted, SAME-anchor
        // bucket `evasion` already occupies, rather than a new weight/anchor of its own. Both are
        // "supplementary clock signal reusing the primary threat_early anchor" by the SAME judgment
        // call this class's own KDoc already documents for evasion ("both represent aggressive
        // board commitment at a similar order of magnitude") -- `direct_damage` is a third instance
        // of that exact judgment call, not a new one: a deck closing the game via burn spells to the
        // face is the same "aggressive board commitment" concept evasion already models, just via
        // spells instead of unblockable creatures. No new anchor/weight constant needed. Confirmed
        // root cause + fix target: fixture 14 (Mono-Red Burn)'s `removal_spot`-tagged burn suite
        // previously contributed ONLY to `interaction` (see that axis's own KDoc below), reading
        // `clock=0.463` -- far short of AGGRO's own 0.90 (60-card) prototype -- purely because the
        // resolver had no way to see a burn spell as a clock component at all (spec §2.1 names
        // `threat_early`/curve/`evasion`/finisher-MV as clock's 4 components; burn/reach spells are
        // outside that list entirely, a genuine model gap this fix closes for the SAME reason
        // `evasion` itself was already an extension beyond the spec's named 3 sub-terms).
        val clockAnchor = if (isCommander) CLOCK_DENSITY_ANCHOR_COMMANDER else CLOCK_DENSITY_ANCHOR_SIXTY
        val threatEarlyDensity = density("threat_early")
        val evasionDensity = density("evasion")
        val directDamageDensity = density("direct_damage")
        val curveLowBonus = (1.0 - avgMv / 4.0).coerceIn(0.0, 1.0).toFloat()
        val clock = (
            0.45f * (threatEarlyDensity / clockAnchor).coerceIn(0f, 1f) +
                0.35f * curveLowBonus +
                0.20f * ((evasionDensity + directDamageDensity) / clockAnchor).coerceIn(0f, 1f)
            ).coerceIn(0f, 1f)

        // interaction: removal_spot + removal_mass + counterspell + stax_piece density.
        // INTERACTION_ANCHOR derived from CONTROL's OWN band per format:
        //  - Commander: removal_spot(13) + removal_mass(7) + counterspell(9) = 29 / nonland
        //    (100 - CONTROL's own 38 lands ideal = 62) = 0.468 density; CONTROL/Commander
        //    interaction prototype = 0.75 -> anchor = 0.468/0.75 = 0.624.
        //  - 60-card: counterspell(13) + removal_spot(8) + removal_mass(5) = 26 / nonland
        //    (60 - CONTROL's own 26 lands ideal = 34) = 0.765 density; CONTROL/60-card interaction
        //    prototype = 0.85 -> anchor = 0.765/0.85 = 0.900. A 60-card control shell legitimately
        //    packs far more interaction per nonland slot than a Commander deck (far fewer total
        //    card slots overall), so the anchors differ substantially by design, not by omission.
        val interactionAnchor = if (isCommander) INTERACTION_ANCHOR_COMMANDER else INTERACTION_ANCHOR_SIXTY
        val interactionDensity = density("removal_spot") + density("removal_mass") + density("counterspell") + density("stax_piece")
        val interaction = (interactionDensity / interactionAnchor).coerceIn(0f, 1f)

        // inevitability: card_draw + recursion + tutor + finisher density ("if the game goes long,
        // do I win"). INEVITABILITY_ANCHOR derived from COMBO's OWN band per format:
        //  - Commander: card_draw(12) + tutor(8) + finisher(5) + GENERIC/Commander's own
        //    recursion(2, COMBO itself bands no recursion) = 27 / nonland (100 - COMBO's own 38
        //    lands ideal = 62) = 0.435 density; COMBO/Commander inevitability prototype = 0.90 ->
        //    anchor = 0.435/0.90 = 0.484.
        //  - 60-card: card_draw(13) + tutor(8) + finisher(6) = 27 / nonland (60 - COMBO's own 23
        //    lands ideal = 37) = 0.730 density (GENERIC/60-card bands no `recursion` role at all --
        //    unlike Commander, there is no fallback figure to add, so this format's sum omits it
        //    rather than inventing one); COMBO/60-card inevitability prototype = 0.85 -> anchor =
        //    0.730/0.85 = 0.859.
        val inevitabilityAnchor = if (isCommander) INEVITABILITY_ANCHOR_COMMANDER else INEVITABILITY_ANCHOR_SIXTY
        val inevitabilityDensity = density("card_draw") + density("recursion") + density("tutor") + density("finisher")
        val inevitability = (inevitabilityDensity / inevitabilityAnchor).coerceIn(0f, 1f)

        // linearity: share of non-land copies participating (as producer OR payoff) in the
        // synergy graph's own dominant axis (spec §2.1: "from PASS 1"). Two documented judgment
        // calls, since the spec only defines linearity in words, not as a precise expression:
        //  1. "Dominant axis" = the axis with the HIGHEST [AxisState.health] (spec §5.3's own
        //     continuous [0,1] coherence formula: producer-fill x payoff-fill x redundancy),
        //     picked by ranking ALL axes, not filtering to a live/dead cut first.
        //  2. "Participating" (the numerator once that axis is picked) = its raw
        //     producerCopies + payoffCopies share of nonland — kept literal to the spec's own
        //     "share of non-land copies" wording, rather than substituting the axis's health value
        //     itself (health already contains a multiplicative redundancy factor that is not a
        //     "share of copies" at all, so reusing it as linearity's VALUE would silently change
        //     what the number means, not just how the axis is picked).
        //
        // PHASE 3a-CALIBRATION FIX: pre-calibration, "dominant" meant "largest raw
        // producerCopies+payoffCopies sum" with NO health involved at all, which let a
        // structurally-produced axis with ZERO real payoffs (e.g. SPELLS, credited from every
        // instant/sorcery TYPE LINE per [SynergyGraph.structuralProducerAxes] regardless of any
        // dedicated payoff) win by sheer producer count even though its own [AxisState.health]
        // scores it near zero (payoff factor ~0). This was the confirmed root cause of fixture 11
        // (UW Control) reading `linearity=0.547` off its own removal-heavy SPELLS producer count —
        // a control deck's own answers being misread as "combo-linear" synergy — which pulled it
        // past PRISON's higher linearity prototype (0.60) instead of CONTROL's low one (0.20).
        // Picking the dominant axis by HEALTH instead of raw count fixes this directly: a card
        // count on a payoff-less axis can no longer outrank a smaller, but genuinely coherent, one.
        // A deck with literally no axis carrying ANY health (every producer or payoff sits at 0)
        // has, by construction, no resource the whole build revolves around, so `linearity = 0` for
        // it — the negative fixture's own near-zero-everywhere axis table (see this file's own
        // report for its exact measured value) is the concrete instance of this floor.
        val dominantAxis = graph.axes.maxByOrNull { it.health }
        val linearity = if (dominantAxis == null) {
            0f
        } else {
            ((dominantAxis.producerCopies + dominantAxis.payoffCopies).toFloat() / nonLandCount).coerceIn(0f, 1f)
        }

        return DeckAxes(clock = clock, interaction = interaction, inevitability = inevitability, linearity = linearity)
    }

    /** `score(a) = 1 - weightedEuclideanDistance(deck, prototype) / maxDistance` (spec §2.1
     * verbatim). Equal per-axis weights -- NOT the "simplest default" placeholder the
     * pre-calibration code used, but a re-checked, DERIVED conclusion (phase 3a-calibration): the
     * population standard deviation of each axis's 5 values ACROSS the prototype table itself
     * (spec §2.1) is near-identical --
     *  - Commander: clock std=0.250, interaction std=0.252, inevitability std=0.270,
     *    linearity std=0.246 (range: 0.246-0.270, a <10% spread across all 4 axes).
     *  - 60-card: clock std=0.260, interaction std=0.292, inevitability std=0.267,
     *    linearity std=0.267 (range: 0.260-0.292, a ~12% spread).
     * Per this class's own KDoc guidance ("an axis on which all five prototypes cluster carries
     * little information; one on which they spread carries a lot"), the spec's OWN prototype table
     * shows every axis spreading the 5 macros by a comparable amount -- no axis clusters, none
     * dominates -- so uniform weighting is the empirically-justified reading of the table's own
     * structure, not merely the simplest option. Both [axes] and [prototype] components live in
     * `[0,1]`, so the worst-case per-axis gap is `1.0` and `maxDistance = sqrt(4 * 1.0^2) = 2.0`. */
    private fun prototypeScore(axes: DeckAxes, prototype: MacroPrototype): Float {
        val dClock = axes.clock - prototype.clock
        val dInteraction = axes.interaction - prototype.interaction
        val dInevitability = axes.inevitability - prototype.inevitability
        val dLinearity = axes.linearity - prototype.linearity
        val distance = sqrt((dClock * dClock + dInteraction * dInteraction + dInevitability * dInevitability + dLinearity * dLinearity).toDouble()).toFloat()
        return (1f - distance / MAX_PROTOTYPE_DISTANCE).coerceIn(0f, 1f)
    }

    // ── Posture (spec §3) ───────────────────────────────────────────────────────────────────

    /**
     * A second-stage classifier run against the argmax (nearest-prototype) macro, regardless of
     * whether that macro's own margin cleared [MACRO_AMBIGUITY_MARGIN] (spec §3 AMENDMENT,
     * 2026-08-26 — see the call site in [invoke]). [macroRef] is the reference band ONLY; it may
     * differ from the [ArchetypeInference.macro] the caller ultimately displays. Detects at most
     * one posture; a false-positive posture is worse than none (spec §3) because it reshapes the
     * skeleton — every candidate below must clear its own gate AND beat the runner-up candidate by
     * [POSTURE_TIE_MARGIN], else `null` ("if two tie, take none").
     *
     * RAMP/TEMPO's gates are spec §3 verbatim ("ramp density >= 1.4x the macro's ideal AND average
     * MV above the macro band" / "counterspell >= 6 raw copies AND threat_early density above the
     * macro band"). The spec gives no worked gate for ATTRITION/TOOLBOX/VOLTRON/GROUP_HUG/
     * GROUP_SLUG beyond their own `adds`/`relaxes` bands (§3's table) — the 5 gates below are a
     * documented judgment call reading the SAME shape (a relevant role clearing a real threshold,
     * relative to the FIXED macro's own band, mirroring RAMP/TEMPO's own pattern), not a spec-cited
     * formula. GROUP_HUG vs GROUP_SLUG share the identical `group_effect` producer role in today's
     * vocabulary — the two cannot be distinguished from that role alone, so this is a known,
     * flagged limitation: the higher of the two thresholds wins when both would otherwise fire.
     */
    private fun detectPosture(
        macroRef: ArchetypeId,
        format: ArchetypeFormat,
        roleCounts: Map<String, Int>,
        avgMv: Double,
        nonLandCount: Int,
    ): PostureId? {
        val macroDef = ArchetypeData.ARCHETYPES[macroRef]?.get(format) ?: return null

        data class Candidate(val posture: PostureId, val strength: Float)
        val candidates = mutableListOf<Candidate>()

        val rampIdeal = macroDef.roleTargets["ramp"]?.ideal ?: 0
        val rampHave = roleCounts["ramp"] ?: 0
        if (rampIdeal > 0 && rampHave >= RAMP_MULTIPLIER * rampIdeal && avgMv > macroDef.curve.max) {
            candidates += Candidate(PostureId.RAMP, rampHave / (RAMP_MULTIPLIER * rampIdeal))
        }

        val counterspellHave = roleCounts["counterspell"] ?: 0
        val threatEarlyBand = macroDef.roleTargets["threat_early"]
        val threatEarlyHave = roleCounts["threat_early"] ?: 0
        if (counterspellHave >= TEMPO_COUNTERSPELL_MIN && threatEarlyBand != null && threatEarlyHave > threatEarlyBand.max) {
            candidates += Candidate(PostureId.TEMPO, counterspellHave / TEMPO_COUNTERSPELL_MIN.toFloat())
        }

        val recursionHave = roleCounts["recursion"] ?: 0
        val attritionIdealSum = (macroDef.roleTargets["card_draw"]?.ideal ?: 0) + (macroDef.roleTargets["removal_spot"]?.ideal ?: 0)
        val attritionHaveSum = (roleCounts["card_draw"] ?: 0) + (roleCounts["removal_spot"] ?: 0)
        if (recursionHave >= ATTRITION_RECURSION_MIN && attritionIdealSum > 0 && attritionHaveSum > attritionIdealSum) {
            candidates += Candidate(PostureId.ATTRITION, recursionHave / ATTRITION_RECURSION_MIN.toFloat())
        }

        val tutorHave = roleCounts["tutor"] ?: 0
        val finisherBand = macroDef.roleTargets["finisher"]
        val finisherHave = roleCounts["finisher"] ?: 0
        if (tutorHave >= TOOLBOX_TUTOR_MIN && finisherBand != null && finisherHave < finisherBand.min) {
            candidates += Candidate(PostureId.TOOLBOX, tutorHave / TOOLBOX_TUTOR_MIN.toFloat())
        }

        val attachedHave = (roleCounts["equipment"] ?: 0) + (roleCounts["aura_buff"] ?: 0)
        val threatEarlyBand2 = macroDef.roleTargets["threat_early"]
        if (attachedHave >= VOLTRON_ATTACHED_MIN && threatEarlyBand2 != null && threatEarlyHave < threatEarlyBand2.min) {
            candidates += Candidate(PostureId.VOLTRON, attachedHave / VOLTRON_ATTACHED_MIN.toFloat())
        }

        if (format == ArchetypeFormat.COMMANDER) {
            val groupEffectHave = roleCounts["group_effect"] ?: 0
            when {
                groupEffectHave >= GROUP_SLUG_MIN -> candidates += Candidate(PostureId.GROUP_SLUG, groupEffectHave / GROUP_SLUG_MIN.toFloat())
                groupEffectHave >= GROUP_HUG_MIN -> candidates += Candidate(PostureId.GROUP_HUG, groupEffectHave / GROUP_HUG_MIN.toFloat())
            }
        }

        if (candidates.isEmpty()) return null
        val sorted = candidates.sortedByDescending { it.strength }
        val top = sorted[0]
        val runnerUp = sorted.getOrNull(1)
        return if (runnerUp != null && abs(top.strength - runnerUp.strength) < POSTURE_TIE_MARGIN) null else top.posture
    }

    // ── Themes (spec §4.3/§4.4 — reads live axes, not densities) ───────────────────────────

    /** Every axis with a clean 1:1 named theme (spec §5.2's table). `ATTACK`/`GROUP` are
     * deliberately absent — neither maps onto a surviving [ThemeId] (`ATTACK` is a posture-support
     * axis with no theme of its own; `GROUP` fed the two ex-themes now in [PostureId]). */
    private val AXIS_TO_THEME: Map<String, ThemeId> = mapOf(
        "LIFE" to ThemeId.LIFEGAIN,
        "DEATH" to ThemeId.ARISTOCRATS,
        "TOKENS" to ThemeId.TOKENS,
        "COUNTERS" to ThemeId.PLUS1_COUNTERS,
        "LANDFALL" to ThemeId.LANDFALL,
        "ETB" to ThemeId.BLINK,
        "SPELLS" to ThemeId.SPELLSLINGER,
        "ARTIFACTS" to ThemeId.ARTIFACTS,
        "ENCHANTMENTS" to ThemeId.ENCHANTRESS,
        "ATTACHED" to ThemeId.EQUIPMENT,
        "PLANESWALKERS" to ThemeId.SUPERFRIENDS,
        "MILL_OPP" to ThemeId.MILL_OPPONENT,
    )

    /** Themes with NO dedicated Phase 2 axis (spec §5.2's table never lists one for these 5) —
     * detected via a direct role-band check against their OWN (real, Phase 3-rewritten)
     * [ArchetypeData.THEMES] band instead, per spec §4.4's same underlying rule ("producers >= min
     * AND payoffs >= min") applied without the axis-health indirection. */
    private val AXIS_LESS_THEMES: List<ThemeId> = listOf(ThemeId.WHEELS, ThemeId.VEHICLES, ThemeId.CLONES_THEFT, ThemeId.TREASURE, ThemeId.STORM)

    private fun detectThemes(
        format: ArchetypeFormat,
        roleCounts: Map<String, Int>,
        graph: DeckSynergyGraph,
        commanderThemePriors: Set<ThemeId> = emptySet(),
    ): List<ThemeId> {
        val candidates = mutableMapOf<ThemeId, Float>()

        graph.axes.forEach { axisState ->
            if (!axisState.isLive) return@forEach
            when {
                // GRAVEYARD is shared by REANIMATOR (reanimation payoff) and SELF_MILL
                // (self_mill_payoff) — spec §5.2 combines both under one axis; disambiguated here
                // by which theme's OWN payoff role is actually present (a documented judgment call,
                // not a spec-given split — see this class's file header).
                axisState.axis == "GRAVEYARD" -> {
                    if ((roleCounts["reanimation"] ?: 0) > 0) candidates[ThemeId.REANIMATOR] = axisState.health
                    if ((roleCounts["self_mill_payoff"] ?: 0) > 0) candidates[ThemeId.SELF_MILL] = axisState.health
                }
                axisState.axis.startsWith("TRIBE:") -> candidates[ThemeId.TRIBAL] = axisState.health
                else -> AXIS_TO_THEME[axisState.axis]?.let { candidates[it] = axisState.health }
            }
        }

        // putIfAbsent is JVM-only (java.util.Map) -- plain insert-only-if-missing check instead
        // (KMP-safe, mirrors DeckIdentitySeedTags' own reverse-map construction convention).
        AXIS_LESS_THEMES.forEach { theme ->
            if (theme !in candidates && fallbackThemeFires(theme, format, roleCounts)) candidates[theme] = 1f
        }

        // Commander prior (see this file's header + [commanderThemePriors]'s own KDoc): NUDGES an
        // ALREADY-live candidate only -- never adds a theme with zero underlying axis/role signal
        // of its own. This is deliberate and load-bearing: a theme absent from [candidates] here
        // has, by construction, no real card-composition support (spec §4.4's own "producers >=
        // min AND payoffs >= min" rule, or a dead axis), so letting the commander alone conjure a
        // theme would silently violate the "themes reflect what's actually IN the deck" invariant
        // this phase's memory files call out as hard-won (16/16 across the calibration corpus).
        // The bonus is intentionally smaller than the macro tag bonus -- themes are a 2-of-many
        // ranked cut, not a binary argmax, so a smaller nudge is enough to matter on a genuine
        // near-tie without being able to casually reorder a clear top-2.
        commanderThemePriors.forEach { theme ->
            candidates[theme]?.let { candidates[theme] = (it + THEME_PRIOR_BONUS).coerceIn(0f, 1f) }
        }

        return candidates.entries.sortedByDescending { it.value }.take(MAX_THEMES).map { it.key }
    }

    /** Direct role-band check (all of the theme's own `adds` keys must clear their scaled `min`) —
     * see [AXIS_LESS_THEMES]'s own KDoc. Format-gated ([ThemeDefinition.commanderOnly]/
     * [ThemeDefinition.sixtyOnly]) the same way [ArchetypeSkeletonResolver] itself would reject an
     * incompatible pin. */
    private fun fallbackThemeFires(theme: ThemeId, format: ArchetypeFormat, roleCounts: Map<String, Int>): Boolean {
        val def = ArchetypeData.THEMES.getValue(theme)
        if (def.commanderOnly && format != ArchetypeFormat.COMMANDER) return false
        if (def.sixtyOnly && format != ArchetypeFormat.SIXTY) return false
        val scale = if (format == ArchetypeFormat.COMMANDER) 1.0 else def.sixtyScale
        return def.adds.all { (key, band) -> (roleCounts[key] ?: 0) >= (band.min * scale).roundToInt() }
    }

    private companion object {
        /** Spec §2.1: "confidence < MACRO_AMBIGUITY_MARGIN (start at 0.08) surfaces as Custom or a
         * hybrid label." Reused verbatim (not a fresh literal) as the TAG-based commander-prior
         * bonus in [commanderMacroPrior] -- see that function's own KDoc for why capping the prior
         * at exactly this value is what makes it bias rather than override. */
        const val MACRO_AMBIGUITY_MARGIN = 0.08f

        /** Color-identity commander-prior bonus (see [commanderMacroPrior]'s KDoc) -- deliberately
         * much smaller than [MACRO_AMBIGUITY_MARGIN] (roughly a third of it) since color identity
         * alone is the weakest signal this workstream uses: a tiebreak, never a driver. Judgment
         * call, verified by hand against the calibration corpus to never flip a displayed macro on
         * its own (see the phase gate report) -- only ever narrows an already-ambiguous margin. */
        const val MACRO_PRIOR_COLOR_BONUS = 0.03f

        /** Commander theme-prior nudge (see [detectThemes]'s own commander-prior step). Smaller
         * than either macro bonus above -- themes are a ranked top-2 cut, not a binary argmax, so
         * a smaller nudge is enough to matter on a genuine near-tie between two ALREADY-live
         * candidates without casually reordering a clear top-2. Judgment call. */
        const val THEME_PRIOR_BONUS = 0.05f

        /** Max simultaneously-detected themes (D2 / Studio UI multi-select cap) — unchanged from
         * the retired A.6 resolver. */
        const val MAX_THEMES = 2

        /** See [computeAxes]'s own KDoc for the full per-format derivation of every anchor below
         * (phase 3a-calibration, 2026-08-26) -- each cites an [ArchetypeData] band ideal for the
         * SAME format, never a Phase 0 fixture. */
        const val CLOCK_DENSITY_ANCHOR_COMMANDER = 0.182f
        const val CLOCK_DENSITY_ANCHOR_SIXTY = 0.326f
        const val INTERACTION_ANCHOR_COMMANDER = 0.624f
        const val INTERACTION_ANCHOR_SIXTY = 0.900f
        const val INEVITABILITY_ANCHOR_COMMANDER = 0.484f
        const val INEVITABILITY_ANCHOR_SIXTY = 0.859f

        /** `sqrt(4 axes * 1.0^2 max per-axis gap)` — see [prototypeScore]'s own KDoc. */
        val MAX_PROTOTYPE_DISTANCE = sqrt(4.0).toFloat()

        /** Spec §3 verbatim: "RAMP = ramp density >= 1.4x the macro's ideal." */
        const val RAMP_MULTIPLIER = 1.4f

        /** Spec §3 verbatim: "TEMPO = counterspell >= 6 raw copies." */
        const val TEMPO_COUNTERSPELL_MIN = 6

        /** Judgment call (spec gives no explicit gate for ATTRITION) — see [detectPosture]'s KDoc. */
        const val ATTRITION_RECURSION_MIN = 4

        /** Judgment call, matches [PostureId.TOOLBOX]'s own `tutor` band minimum (spec §3: 8-12-18). */
        const val TOOLBOX_TUTOR_MIN = 8

        /** Judgment call, matches [PostureId.VOLTRON]'s own `equipment` band minimum (spec §3: 6-9-13). */
        const val VOLTRON_ATTACHED_MIN = 6

        /** Judgment call, matches [PostureId.GROUP_HUG]'s own `group_effect` band minimum (spec §3: 9-12-16). */
        const val GROUP_HUG_MIN = 9

        /** Judgment call, matches [PostureId.GROUP_SLUG]'s own `group_effect` band minimum (spec §3: 11-15-20). */
        const val GROUP_SLUG_MIN = 11

        /** A real margin required between the top 2 posture candidates, else neither fires (spec
         * §3: "if two tie, take none"). Documented judgment call. */
        const val POSTURE_TIE_MARGIN = 0.15f

        // ── Prototype tables (spec §2.1, transcribed verbatim) ─────────────────────────────

        val COMMANDER_PROTOTYPES: Map<ArchetypeId, MacroPrototype> = mapOf(
            ArchetypeId.AGGRO to MacroPrototype(clock = 0.85f, interaction = 0.20f, inevitability = 0.15f, linearity = 0.55f),
            ArchetypeId.MIDRANGE to MacroPrototype(clock = 0.50f, interaction = 0.45f, inevitability = 0.45f, linearity = 0.30f),
            ArchetypeId.CONTROL to MacroPrototype(clock = 0.20f, interaction = 0.75f, inevitability = 0.80f, linearity = 0.20f),
            ArchetypeId.COMBO to MacroPrototype(clock = 0.40f, interaction = 0.30f, inevitability = 0.90f, linearity = 0.90f),
            ArchetypeId.PRISON to MacroPrototype(clock = 0.15f, interaction = 0.85f, inevitability = 0.70f, linearity = 0.60f),
        )

        val SIXTY_PROTOTYPES: Map<ArchetypeId, MacroPrototype> = mapOf(
            ArchetypeId.AGGRO to MacroPrototype(clock = 0.90f, interaction = 0.20f, inevitability = 0.10f, linearity = 0.60f),
            ArchetypeId.MIDRANGE to MacroPrototype(clock = 0.55f, interaction = 0.55f, inevitability = 0.40f, linearity = 0.30f),
            ArchetypeId.CONTROL to MacroPrototype(clock = 0.20f, interaction = 0.85f, inevitability = 0.75f, linearity = 0.20f),
            ArchetypeId.COMBO to MacroPrototype(clock = 0.45f, interaction = 0.25f, inevitability = 0.85f, linearity = 0.95f),
            ArchetypeId.PRISON to MacroPrototype(clock = 0.20f, interaction = 0.90f, inevitability = 0.60f, linearity = 0.65f),
        )
    }
}
