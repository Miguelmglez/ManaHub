package com.mmg.manahub.feature.decks.presentation.improvement.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckRole
import com.mmg.manahub.feature.decks.domain.engine.DeckWarning
import com.mmg.manahub.feature.decks.domain.engine.ScoreReason

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
}

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
    }

/** One-decimal CMC formatting, locale-stable. */
private fun formatCmc(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

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
}

/**
 * Picks the single most informative reason to surface as a CUT chip.
 *
 * Cut candidates are ranked by lowest fit, so the most useful explanation is *why* the card scores
 * low. Priority (most → least telling for a cut): off-strategy, off-color, low power, over-covered,
 * curve gap. We deliberately ignore positive reasons (SynergyMatch / FillsGap / HighPower) here —
 * they explain a good fit, not a cut. Returns null when no negative reason applies (rare; the row
 * then shows only its fit score).
 */
fun CardFit.primaryCutReason(): ScoreReason? =
    reasons.firstOrNull { it is ScoreReason.OffStrategy }
        ?: reasons.firstOrNull { it is ScoreReason.OutOfColorIdentity }
        ?: reasons.firstOrNull { it is ScoreReason.BelowPowerFloor }
        ?: reasons.firstOrNull { it is ScoreReason.OverCovered }
        ?: reasons.firstOrNull { it is ScoreReason.CurveGap }

/** The role gap this card fills, if any — used for the ADD "Fills: <role>" tag. */
fun CardFit.fillsGapRole(): DeckRole? =
    (reasons.firstOrNull { it is ScoreReason.FillsGap } as? ScoreReason.FillsGap)?.role
