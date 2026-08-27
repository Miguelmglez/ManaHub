package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.feature.decks.domain.engine.AxisKey
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.CurveShape
import com.mmg.manahub.feature.decks.domain.engine.DeckRole
import com.mmg.manahub.feature.decks.domain.engine.DeckWarning
import com.mmg.manahub.feature.decks.domain.engine.Finding
import com.mmg.manahub.feature.decks.domain.engine.PillarId
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
import com.mmg.manahub.feature.decks.domain.engine.ScoreReason
import com.mmg.manahub.feature.decks.domain.orchestrator.DoctorAnalysisStage
import com.mmg.manahub.feature.decks.domain.usecase.ArchetypeResolution
import kotlin.math.roundToInt

/**
 * Presentation-side localization for the scoring engine's structured outputs.
 *
 * The engine ([com.mmg.manahub.feature.decks.domain.engine.DeckScorer]) stays string-free:
 * it emits [DeckWarning] / [DeckRole] values, and these mappers turn them into user-facing English
 * text from `strings.xml`. Keeping the mapping here means adding a language later (should the policy
 * change) touches only resources, never the engine.
 */

/** Human-readable display name for a [DeckRole]. */
@Composable
fun DeckRole.label(): String = stringResource(
    when (this) {
        DeckRole.RAMP -> R.string.deck_role_ramp
        DeckRole.CARD_ADVANTAGE -> R.string.deck_role_card_advantage
        DeckRole.SPOT_REMOVAL -> R.string.deck_role_spot_removal
        DeckRole.BOARD_WIPE -> R.string.deck_role_board_wipe
        DeckRole.INTERACTION -> R.string.deck_role_interaction
        DeckRole.TUTOR -> R.string.deck_role_tutor
        DeckRole.PAYOFF -> R.string.deck_role_payoff
        DeckRole.SYNERGY -> R.string.deck_role_synergy
        DeckRole.THREAT -> R.string.deck_role_threat
        DeckRole.LAND -> R.string.deck_role_land
        DeckRole.FILLER -> R.string.deck_role_filler
    }
)

/** User-facing message for a [DeckWarning]. */
@Composable
fun DeckWarning.label(): String = when (this) {
    is DeckWarning.TooFewLands ->
        stringResource(R.string.deck_health_warning_too_few_lands, current, target)
    is DeckWarning.TooManyLands ->
        stringResource(R.string.deck_health_warning_too_many_lands, current, target)
    is DeckWarning.MissingRole ->
        stringResource(R.string.deck_health_warning_missing_role, role.label(), ideal)
    is DeckWarning.CurveTooHigh ->
        stringResource(R.string.deck_health_warning_curve_too_high, formatCmc(avgCmc))
    is DeckWarning.CurveTooLow ->
        stringResource(R.string.deck_health_warning_curve_too_low, formatCmc(avgCmc))
    is DeckWarning.LowSynergyDensity ->
        stringResource(R.string.deck_health_warning_low_synergy, (density * 100).toInt())
    is DeckWarning.UnresolvedCards ->
        stringResource(R.string.deck_health_warning_unresolved_cards, count)
    is DeckWarning.DeckTooSmall ->
        stringResource(R.string.deck_health_warning_deck_too_small, current, minimum)
    is DeckWarning.TooManyCopies ->
        stringResource(R.string.deck_health_warning_too_many_copies, cardName, copies, maxCopies)
    is DeckWarning.SingletonViolation ->
        stringResource(R.string.deck_health_warning_singleton_violation, cardName, copies)
    is DeckWarning.OffColorIdentity ->
        stringResource(R.string.deck_health_warning_off_color_identity, cardName)
    is DeckWarning.ColorSourceShortage ->
        stringResource(R.string.deck_health_warning_color_source_shortage, color.displayName, sources, needed)
    is DeckWarning.UnfixedSplash ->
        stringResource(R.string.deck_health_warning_unfixed_splash, color.displayName)
    is DeckWarning.ArchetypeRoleGap ->
        stringResource(R.string.deck_health_warning_archetype_role_gap, planLabel, roleKey.archetypeRoleLabel(), min, current)
    is DeckWarning.ArchetypeAntiRolePresent ->
        stringResource(R.string.deck_health_warning_archetype_anti_role, planLabel, roleKey.archetypeRoleLabel(), tolerance, current)
    is DeckWarning.CurveOutsideArchetypeBand ->
        stringResource(R.string.deck_health_warning_curve_outside_archetype_band, planLabel, formatCmc(avgCmc), formatCmc(min), formatCmc(max))
}

/**
 * Human-readable fallback label for a [RoleKey] (the Appendix A dynamic-role vocabulary — no
 * per-key `strings.xml` entry, mirrors `CardTag.displayLabel`'s snake_case->Title Case fallback).
 */
fun RoleKey.archetypeRoleLabel(): String = replace('_', ' ').split(' ')
    .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

/**
 * Deck Wizard & Engine Rework plan, Workstream 8.4: human-readable label for a [DoctorAnalysisStage],
 * mirroring [com.mmg.manahub.feature.decks.presentation.wizard.BuildStage.label]'s own mapper (same
 * pattern, separate `strings.xml` copy since this is the Suggestions tab's analysis pass, not a
 * deck build).
 */
@Composable
fun DoctorAnalysisStage.label(): String = stringResource(
    when (this) {
        DoctorAnalysisStage.READING_DECK_PLAN -> R.string.deck_doctor_stage_reading_deck_plan
        DoctorAnalysisStage.SEARCHING_COMMUNITY -> R.string.deck_doctor_stage_searching_community
    }
)

/** Stable identity for a warning, used as a LazyColumn key. */
val DeckWarning.key: String
    get() = when (this) {
        is DeckWarning.TooFewLands -> "too_few_lands"
        is DeckWarning.TooManyLands -> "too_many_lands"
        is DeckWarning.MissingRole -> "missing_role_${role.name}"
        is DeckWarning.CurveTooHigh -> "curve_too_high"
        is DeckWarning.CurveTooLow -> "curve_too_low"
        is DeckWarning.LowSynergyDensity -> "low_synergy"
        is DeckWarning.UnresolvedCards -> "unresolved_cards"
        is DeckWarning.DeckTooSmall -> "deck_too_small"
        is DeckWarning.TooManyCopies -> "too_many_copies_$cardName"
        is DeckWarning.SingletonViolation -> "singleton_violation_$cardName"
        is DeckWarning.OffColorIdentity -> "off_color_identity_$cardName"
        is DeckWarning.ColorSourceShortage -> "color_source_shortage_${color.name}"
        is DeckWarning.UnfixedSplash -> "unfixed_splash_${color.name}"
        is DeckWarning.ArchetypeRoleGap -> "archetype_role_gap_$roleKey"
        is DeckWarning.ArchetypeAntiRolePresent -> "archetype_anti_role_$roleKey"
        is DeckWarning.CurveOutsideArchetypeBand -> "curve_outside_archetype_band"
    }

/** One-decimal CMC formatting, locale-stable. */
private fun formatCmc(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

// ─────────────────────────────────────────────────────────────────────────────
//  Deck Analysis Engine v2 (Phase 3 UI) — Finding / PillarId localization
//
//  Mirrors DeckWarning.label()/.key above exactly (same pattern, new sealed type): the engine
//  (AnalysisEngine) stays string-free and emits [Finding] values; this mapper turns them into the
//  ALREADY-AUTHORED `deck_analysis_finding_*` strings.xml copy (Phase 2 landed the strings, this
//  phase is the first consumer).
// ─────────────────────────────────────────────────────────────────────────────

/** User-facing message for a [Finding]. */
@Composable
fun Finding.label(): String = when (this) {
    is Finding.LandCountOffTarget ->
        stringResource(R.string.deck_analysis_finding_land_count_off_target, current, karstenTarget, bandMin, bandMax)
    is Finding.ColorSourceShortage ->
        stringResource(R.string.deck_analysis_finding_color_source_shortage, color.displayName, have, need)
    is Finding.UnfixedSplash ->
        stringResource(R.string.deck_analysis_finding_unfixed_splash, color.displayName)
    is Finding.ManaFixShortage ->
        stringResource(R.string.deck_analysis_finding_mana_fix_shortage, current, min)
    is Finding.LandMixOffTarget ->
        stringResource(
            R.string.deck_analysis_finding_land_mix_off_target,
            (basicsRatio * 100).roundToInt(),
            (targetMin * 100).roundToInt(),
            (targetMax * 100).roundToInt(),
        )
    is Finding.CurveOffBand ->
        stringResource(R.string.deck_analysis_finding_curve_off_band, formatCmc(avgMv), formatCmc(bandMin), formatCmc(bandMax))
    is Finding.CurveShapeMismatch ->
        stringResource(R.string.deck_analysis_finding_curve_shape_mismatch, shape.displayLabel())
    is Finding.RoleGap ->
        stringResource(R.string.deck_analysis_finding_role_gap, label, current, min)
    is Finding.RoleBelowIdeal ->
        stringResource(R.string.deck_analysis_finding_role_below_ideal, label, current, ideal)
    is Finding.AntiRoleOverMax ->
        stringResource(R.string.deck_analysis_finding_anti_role_over_max, label, current, max)
    is Finding.SelfDefeatingGraveyardHate ->
        stringResource(R.string.deck_analysis_finding_self_defeating_graveyard_hate, graveyardHateCopies)
    is Finding.OrphanProducers ->
        stringResource(R.string.deck_analysis_finding_orphan_producers, axisLabel, producerCopies)
    is Finding.OrphanPayoffs ->
        stringResource(R.string.deck_analysis_finding_orphan_payoffs, axisLabel, payoffCopies, payoffIdeal)
    is Finding.StaxVsOwnEngine ->
        stringResource(R.string.deck_analysis_finding_stax_vs_own_engine, staxPieceCopies, cardDrawCopies, controlCardDrawIdeal)
    is Finding.DeckTooSmall ->
        stringResource(R.string.deck_analysis_finding_deck_too_small, current, minimum)
    is Finding.TooManyCopies ->
        stringResource(R.string.deck_analysis_finding_too_many_copies, cardName, copies, maxCopies)
    is Finding.SingletonViolation ->
        stringResource(R.string.deck_analysis_finding_singleton_violation, cardName, copies)
    is Finding.OffColorIdentity ->
        stringResource(R.string.deck_analysis_finding_off_color_identity, cardName)
    is Finding.IllegalCard ->
        stringResource(R.string.deck_analysis_finding_illegal_card, cardName)
    is Finding.SideboardOversized ->
        stringResource(R.string.deck_analysis_finding_sideboard_oversized, count)
    is Finding.UnresolvedCards ->
        stringResource(R.string.deck_analysis_finding_unresolved_cards, count)
}

/** Stable identity for a [Finding], used as a LazyColumn key. */
val Finding.key: String
    get() = when (this) {
        is Finding.LandCountOffTarget -> "land_count_off_target"
        is Finding.ColorSourceShortage -> "color_source_shortage_${color.name}"
        is Finding.UnfixedSplash -> "unfixed_splash_${color.name}"
        is Finding.ManaFixShortage -> "mana_fix_shortage"
        is Finding.LandMixOffTarget -> "land_mix_off_target"
        is Finding.CurveOffBand -> "curve_off_band"
        is Finding.CurveShapeMismatch -> "curve_shape_mismatch"
        is Finding.RoleGap -> "role_gap_$roleKey"
        is Finding.RoleBelowIdeal -> "role_below_ideal_$roleKey"
        is Finding.AntiRoleOverMax -> "anti_role_over_max_$roleKey"
        is Finding.SelfDefeatingGraveyardHate -> "self_defeating_graveyard_hate"
        is Finding.OrphanProducers -> "orphan_producers_$axis"
        is Finding.OrphanPayoffs -> "orphan_payoffs_$axis"
        is Finding.StaxVsOwnEngine -> "stax_vs_own_engine"
        is Finding.DeckTooSmall -> "deck_too_small"
        is Finding.TooManyCopies -> "too_many_copies_$cardName"
        is Finding.SingletonViolation -> "singleton_violation_$cardName"
        is Finding.OffColorIdentity -> "off_color_identity_$cardName"
        is Finding.IllegalCard -> "illegal_card_$cardName"
        is Finding.SideboardOversized -> "sideboard_oversized"
        is Finding.UnresolvedCards -> "unresolved_cards"
    }

/** English-only display word for a [CurveShape] — a closed, 3-value engine enum with no
 * `displayName` of its own (mirrors [RoleKey.archetypeRoleLabel]'s "plain function, not a
 * `stringResource`" precedent for small closed vocabularies below). */
private fun CurveShape.displayLabel(): String = when (this) {
    CurveShape.FRONT -> "front-loaded"
    CurveShape.BELL -> "bell-shaped"
    CurveShape.BACK -> "back-loaded"
}

/** Human-readable display name for a [PillarId] (Phase 3's pillar-tile row, plan §3.4 item 3). */
@Composable
fun PillarId.label(): String = stringResource(
    when (this) {
        PillarId.MANA_BASE -> R.string.deck_analysis_pillar_mana_base
        PillarId.CURVE -> R.string.deck_analysis_pillar_curve
        PillarId.PLAN_ROLES -> R.string.deck_analysis_pillar_plan_roles
        PillarId.SYNERGY -> R.string.deck_analysis_pillar_synergy
        PillarId.LEGALITY -> R.string.deck_analysis_pillar_legality
    }
)

// ─────────────────────────────────────────────────────────────────────────────
//  Deck Analysis Engine v3, Phase 5 (UI) — AxisKey labels + archetype resemblance copy.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * English display label for an [AxisKey] (Synergy package sections) — a presentation-layer mirror
 * of [com.mmg.manahub.feature.decks.domain.engine.AnalysisEngine]'s own private `AXIS_LABELS`
 * table/fallback (core-domain stays string-free per [Finding]'s own discipline — same "each layer
 * keeps its own fallback" precedent as [RoleKey.archetypeRoleLabel]/`CardTag.displayLabel`). Falls
 * back to a humanized `snake_case -> Title Case` reading for any axis this table doesn't know about
 * (mirrors the engine's own fallback shape), so a future new axis never renders a raw key.
 */
@Composable
fun AxisKey.axisDisplayLabel(): String = when {
    startsWith("TRIBE:") -> stringResource(
        R.string.deck_analysis_axis_tribe_format,
        removePrefix("TRIBE:").replaceFirstChar { it.uppercase() },
    )
    this == "LIFE" -> stringResource(R.string.deck_analysis_axis_life)
    this == "DEATH" -> stringResource(R.string.deck_analysis_axis_death)
    this == "TOKENS" -> stringResource(R.string.deck_analysis_axis_tokens)
    this == "COUNTERS" -> stringResource(R.string.deck_analysis_axis_counters)
    this == "LANDFALL" -> stringResource(R.string.deck_analysis_axis_landfall)
    this == "GRAVEYARD" -> stringResource(R.string.deck_analysis_axis_graveyard)
    this == "ETB" -> stringResource(R.string.deck_analysis_axis_etb)
    this == "SPELLS" -> stringResource(R.string.deck_analysis_axis_spells)
    this == "ARTIFACTS" -> stringResource(R.string.deck_analysis_axis_artifacts)
    this == "ENCHANTMENTS" -> stringResource(R.string.deck_analysis_axis_enchantments)
    this == "ATTACHED" -> stringResource(R.string.deck_analysis_axis_attached)
    this == "ATTACK" -> stringResource(R.string.deck_analysis_axis_attack)
    this == "PLANESWALKERS" -> stringResource(R.string.deck_analysis_axis_planeswalkers)
    this == "GROUP" -> stringResource(R.string.deck_analysis_axis_group)
    this == "MILL_OPP" -> stringResource(R.string.deck_analysis_axis_mill_opp)
    this == "LOCK" -> stringResource(R.string.deck_analysis_axis_lock)
    this == "ENGINE" -> stringResource(R.string.deck_analysis_axis_engine)
    else -> replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * The archetype/hybrid display label for an [ArchetypeResolution] (Deck Analysis Engine v3, spec
 * §2.1) — mirrors [com.mmg.manahub.feature.decks.domain.engine.ResolvedStrategyInfo.displayName]'s
 * OWN "never silently coerce" contract, but built from [ArchetypeResolution.resemblance] (always
 * populated on the inference path, see that field's own KDoc) rather than
 * `ResolvedStrategyInfo`'s `runnerUpArchetype` — no production call site threads a runner-up
 * through to that type today, and every input this function needs is already on
 * [ArchetypeResolution] itself. A confident [ArchetypeResolution.macro] renders its own
 * [ArchetypeId.displayName] verbatim; an ambiguous (`null`) resolution renders a real hybrid label
 * from the TOP TWO resemblance shares (e.g. "Control / Midrange") when at least two exist, or the
 * bare "Custom" sentinel only when [ArchetypeResolution.resemblance] itself is empty (a completely
 * empty deck) — NEVER a silent fallback to a single confident-looking name.
 */
@Composable
fun ArchetypeResolution.planLabel(): String {
    macro?.let { return it.displayName }
    val top = resemblance.take(2)
    return if (top.size >= 2) {
        stringResource(R.string.deck_analysis_archetype_hybrid_format, top[0].macro.displayName, top[1].macro.displayName)
    } else {
        stringResource(R.string.deck_analysis_archetype_custom_label)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ScoreReason localization (Cut / Add suggestion tags)
// ─────────────────────────────────────────────────────────────────────────────

/** User-facing English label for a single [ScoreReason]. */
@Composable
fun ScoreReason.label(): String = when (this) {
    is ScoreReason.SynergyMatch -> stringResource(R.string.deck_reason_synergy)
    is ScoreReason.FillsGap -> stringResource(R.string.deck_reason_fills_gap, role.label())
    is ScoreReason.OverCovered ->
        stringResource(R.string.deck_reason_overcovered, role.label(), current, ideal)
    ScoreReason.OnCurve -> stringResource(R.string.deck_reason_on_curve)
    is ScoreReason.CurveGap -> stringResource(R.string.deck_reason_curve_gap)
    ScoreReason.HighPower -> stringResource(R.string.deck_reason_high_power)
    ScoreReason.GameChanger -> stringResource(R.string.deck_reason_game_changer)
    ScoreReason.BelowPowerFloor -> stringResource(R.string.deck_reason_low_power)
    ScoreReason.OffStrategy -> stringResource(R.string.deck_reason_off_strategy)
    ScoreReason.OutOfColorIdentity -> stringResource(R.string.deck_reason_off_color)
    ScoreReason.Colorless -> stringResource(R.string.deck_reason_colorless)
    ScoreReason.InCollection -> stringResource(R.string.deck_reason_in_collection)
    is ScoreReason.UnsupportedPipCost -> stringResource(R.string.deck_reason_unsupported_pip, intensity, color.displayName)
    is ScoreReason.FillsArchetypeGap ->
        stringResource(R.string.deck_reason_fills_archetype_gap, planLabel, roleKey.archetypeRoleLabel(), ideal, current)
    is ScoreReason.OverArchetypeBand ->
        stringResource(R.string.deck_reason_over_archetype_band, planLabel, roleKey.archetypeRoleLabel(), max, current)
}

/**
 * Picks the single most informative reason to surface as a CUT chip.
 *
 * Cut candidates are ranked by lowest fit, so the most useful explanation is *why* the card scores
 * low. Priority (most → least telling for a cut): unsupported pip cost (WS9.5 -- the manabase
 * genuinely cannot cast it reliably, the single most actionable cut reason when present), over an
 * archetype band (WS8.3 -- this card's own role is already over-stuffed, a concrete "you have too
 * many of these" signal), off-strategy, off-color, low power, over-covered, curve gap. We
 * deliberately ignore positive reasons (SynergyMatch / FillsGap / FillsArchetypeGap / HighPower)
 * here — they explain a good fit, not a cut. Returns null when no negative reason applies (rare;
 * the row then shows only its fit score).
 */
fun CardFit.primaryCutReason(): ScoreReason? =
    reasons.firstOrNull { it is ScoreReason.UnsupportedPipCost }
        ?: reasons.firstOrNull { it is ScoreReason.OverArchetypeBand }
        ?: reasons.firstOrNull { it is ScoreReason.OffStrategy }
        ?: reasons.firstOrNull { it is ScoreReason.OutOfColorIdentity }
        ?: reasons.firstOrNull { it is ScoreReason.BelowPowerFloor }
        ?: reasons.firstOrNull { it is ScoreReason.OverCovered }
        ?: reasons.firstOrNull { it is ScoreReason.CurveGap }

/** The role gap this card fills, if any — used for the ADD "Fills: <role>" tag. */
fun CardFit.fillsGapRole(): DeckRole? =
    (reasons.firstOrNull { it is ScoreReason.FillsGap } as? ScoreReason.FillsGap)?.role

/** WS8.2 -- the archetype/theme band this card fills, if any (the dynamic-[RoleKey]-vocabulary
 * counterpart to [fillsGapRole]) — used for the ADD "Fills: <band>" tag on an archetype-aware deck. */
fun CardFit.fillsArchetypeGapReason(): ScoreReason.FillsArchetypeGap? =
    reasons.firstOrNull { it is ScoreReason.FillsArchetypeGap } as? ScoreReason.FillsArchetypeGap
