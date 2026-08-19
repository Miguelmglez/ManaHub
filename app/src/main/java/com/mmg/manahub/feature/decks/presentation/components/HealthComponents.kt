package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.Finding
import com.mmg.manahub.feature.decks.domain.engine.FindingSeverity
import com.mmg.manahub.feature.decks.domain.engine.PillarId
import com.mmg.manahub.feature.decks.domain.engine.PillarResult
import com.mmg.manahub.feature.decks.domain.engine.RoleCoverageEntry

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Doctor — Health view
//
//  Stateless, theme-token-only composables for the read-only Health evaluation:
//  a score ring, pillar tiles, the plan-role table and severity-tinted finding rows.
//  Relocated from `improvement/components/` in Phase 0.5 of
//  `docs/claude-code-prompt-deck-doctor-community.md` (D10) when the standalone
//  Deck Improvement screen was retired — Deck Studio's Suggestions ("Analysis") tab
//  is the only consumer now. This file's own `ManaCurveChart` was DROPPED during the
//  move (dead code once `DeckImprovementScreen` was deleted, and it would have
//  collided with the unrelated, already-live `ManaCurveChart` in this same package
//  used by `DeckSummaryCard`).
//
//  Deck Analysis Engine v2 Phase 3 (plan §3.4 items 2-4): [RoleCoverageRow] (legacy
//  `RoleCoverage`-enum-typed) and [WarningChip] were REMOVED here — both had exactly
//  ONE call site, the flat warning-chip-wall / legacy Role Coverage rows in
//  `DeckStudioScreen`'s Suggestions tab, which this phase replaces with the pillar-tile
//  UI below ([PillarTile]/[RoleCoverageEntryRow]/[FindingRow], all typed against the
//  v2 [PillarResult]/[RoleCoverageEntry]/[Finding] models). The legacy `DeckWarning`/
//  `RoleCoverage` TYPES themselves are untouched (still back `DeckHealth.evaluation`,
//  per the Phase 2 KDoc) — only their now-orphaned UI was deleted, not the engine data.
//  All colors derive from MagicColors (good → mid → low = lifePositive → goldMtg →
//  lifeNegative); none are hardcoded.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Resolves a 0..1 quality fraction to a theme color on the good → mid → low ramp.
 * 1.0 = healthy (`lifePositive`), ~0.5 = caution (`goldMtg`), 0.0 = alert (`lifeNegative`).
 */
private fun MagicColors.qualityColor(fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return if (f >= 0.5f) {
        lerp(goldMtg, lifePositive, (f - 0.5f) / 0.5f)
    } else {
        lerp(lifeNegative, goldMtg, f / 0.5f)
    }
}

/**
 * Circular 0–100 health score with a short verdict in the center.
 *
 * @param score deck health score in [0,100].
 */
@Composable
fun HealthScoreRing(
    score: Int,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val fraction = (score / 100f).coerceIn(0f, 1f)
    val animated by animateFloatAsState(targetValue = fraction, label = "healthScore")
    val arcColor = mc.qualityColor(fraction)

    val verdict = when {
        score >= 80 -> stringResource(R.string.deck_health_verdict_excellent)
        score >= 60 -> stringResource(R.string.deck_health_verdict_solid)
        score >= 40 -> stringResource(R.string.deck_health_verdict_needs_work)
        else -> stringResource(R.string.deck_health_verdict_rough)
    }
    val ringDescription = stringResource(R.string.deck_health_score_cd, score)

    Box(
        modifier = modifier
            .size(168.dp)
            .clearAndSetSemantics { contentDescription = ringDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(168.dp)) {
            val stroke = 14.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            // Track
            drawArc(
                color = mc.surfaceVariant,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            // Progress
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = score.toString(),
                style = ty.displayLarge,
                color = mc.textPrimary,
            )
            Text(
                text = verdict,
                style = ty.labelMedium,
                color = arcColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Analysis Engine v2 Phase 3 (plan §3.4 items 2-4) — pillar tiles, the plan-role
//  table, and severity-tinted finding rows. All typed against the v2 engine models
//  ([PillarResult]/[RoleCoverageEntry]/[Finding]) — see this file's header for what
//  they replace.
// ═══════════════════════════════════════════════════════════════════════════════

/** Icon per [PillarId] (plan §3.4 item 3: "icon, name, subscore, status color"). Purely
 * decorative pairing with the tile's own text label, real `androidx.compose.material.icons`. */
private fun PillarId.icon(): ImageVector = when (this) {
    PillarId.MANA_BASE -> Icons.Default.Landscape
    PillarId.CURVE -> Icons.AutoMirrored.Filled.TrendingUp
    PillarId.PLAN_ROLES -> Icons.Default.Checklist
    PillarId.SYNERGY -> Icons.Default.Hub
    PillarId.LEGALITY -> Icons.Default.Gavel
}

/**
 * One pillar tile in the horizontal pillar row (plan §3.4 item 3): icon, [PillarId.label], the
 * 0-100 [PillarResult.subscore], and a status color from [MagicColors.qualityColor]. Tapping
 * toggles [expanded] (hoisted — the caller owns which single pillar is expanded).
 */
@Composable
fun PillarTile(
    pillar: PillarResult,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val statusColor = mc.qualityColor(pillar.subscore / 100f)

    Surface(
        onClick = onClick,
        shape = CardShape,
        color = if (expanded) statusColor.copy(alpha = 0.14f) else mc.surface,
        border = BorderStroke(1.dp, if (expanded) statusColor else mc.surfaceVariant),
        // Deck Analysis Engine v2 Phase 3 review fix (P1 #1): no `minWidth` here — this tile is
        // always used with `Modifier.weight(1f)` across a 5-wide Row (DeckStudioScreen's pillar
        // row); a minWidth floor fights the weight distribution and overflows/clips on narrow
        // (~360dp) devices. `minHeight` alone is enough to keep the tile from collapsing vertically.
        modifier = modifier.sizeIn(minHeight = 76.dp),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs, Alignment.CenterVertically),
        ) {
            Icon(pillar.id.icon(), contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
            Text(
                text = pillar.subscore.toString(),
                style = ty.titleMedium,
                color = statusColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = pillar.id.label(),
                style = ty.labelSmall,
                color = mc.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * One row of the "Removal 3/8"-style plan-role table (plan §3.4 item 4): the role's
 * [RoleCoverageEntry.label] on the left, a track bar with min/ideal/max band markers, and the
 * `current/ideal` (or, for an anti-role, the INVERTED `current / max N`) value on the right —
 * replaces the legacy [DeckRole][com.mmg.manahub.feature.decks.domain.engine.DeckRole]-keyed
 * `RoleCoverageRow` this file used to carry (deleted, see this file's header).
 */
@Composable
fun RoleCoverageEntryRow(
    entry: RoleCoverageEntry,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    // Symmetric [0,1] quality — same shape as AnalysisEngine's own `bandRatioScore`/anti-role
    // scoring (mirrored here in the UI layer since the engine only returns the already-composed
    // subscore, not a per-row quality fraction) so the row's color always agrees with what the
    // engine itself considers "healthy" for this exact band.
    val quality = if (entry.isAntiRole) {
        if (entry.current <= entry.max) 1f else (entry.max.toFloat() / entry.current).coerceIn(0f, 1f)
    } else when {
        entry.ideal <= 0 -> 1f
        entry.current <= entry.ideal -> (entry.current.toFloat() / entry.ideal).coerceIn(0f, 1f)
        entry.current <= entry.max -> 1f
        else -> (entry.max.toFloat() / entry.current).coerceIn(0f, 1f)
    }
    // Deck Analysis Engine v2 Phase 3 review fix (P1 #2): an over-max anti-role must read as an
    // immediate alert, not a point on the continuous good->mid->low ramp. `quality = max/current`
    // for e.g. max=1/current=2 lands at 0.5, which `qualityColor` renders as mild caution (gold) --
    // inconsistent with FindingRow, which always tints Finding.AntiRoleOverMax as BLOCKER (alert).
    // Bypass the ramp entirely for this exact condition so the two surfaces agree.
    val barColor = if (entry.isAntiRole && entry.current > entry.max) mc.lifeNegative else mc.qualityColor(quality)

    // Scale the track to whatever is largest (max band edge or an over-max current) so every
    // marker + the fill always land inside [0,1] of the track width.
    val scale = maxOf(entry.max, entry.current, 1).toFloat()
    val fillFraction = (entry.current / scale).coerceIn(0f, 1f)
    val minFraction = (entry.min / scale).coerceIn(0f, 1f)
    val maxFraction = (entry.max / scale).coerceIn(0f, 1f)

    val valueText = if (entry.isAntiRole) {
        stringResource(R.string.deck_analysis_role_anti_max_format, entry.current, entry.max)
    } else {
        "${entry.current}/${entry.ideal}"
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = entry.label,
            style = ty.bodyMedium,
            color = mc.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(104.dp),
        )
        Spacer(Modifier.width(MaterialTheme.spacing.md))
        // Deck Analysis Engine v2 Phase 3 review (P2 #6): documented gap, left as-is. None of the
        // named shape tokens (ChipShape/ButtonShape/CardShape/BottomSheetShape) are a thin-pill
        // shape suitable for a 10dp-tall progress-bar track/fill — forcing a wrong-shaped token
        // here would look worse than the current explicit `RoundedCornerShape(5.dp)` (a true
        // capsule at this height). Revisit if/when a dedicated "pill" token is added to the design
        // system. (FindingRow's severity dot, the other RoundedCornerShape in this file, WAS fixed
        // to `CircleShape` — that one is a real M3 shape, not an app-specific gap.)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(mc.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillFraction)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(barColor),
            )
            RoleBandMarker(fraction = minFraction, color = mc.textSecondary)
            RoleBandMarker(fraction = maxFraction, color = mc.textDisabled)
        }
        Spacer(Modifier.width(MaterialTheme.spacing.md))
        Text(
            text = valueText,
            style = ty.labelMedium,
            color = barColor,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(64.dp),
        )
    }
}

/** A single vertical tick at [fraction] of the track's width — used for the min/max band markers
 * on [RoleCoverageEntryRow]'s bar. */
@Composable
private fun RoleBandMarker(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction.coerceIn(0f, 1f))
            .height(10.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Box(Modifier.width(2.dp).height(10.dp).background(color))
    }
}

/** Severity → theme color for a [Finding] row (BLOCKER = alert, WARNING = gold/caution, INFO =
 * neutral secondary text) — plan §3.4 item 4's "severity-tinted rows". */
@Composable
private fun FindingSeverity.tint(): Color {
    val mc = MaterialTheme.magicColors
    return when (this) {
        FindingSeverity.BLOCKER -> mc.lifeNegative
        FindingSeverity.WARNING -> mc.goldMtg
        FindingSeverity.INFO -> mc.textSecondary
    }
}

/** Sentinel substituted for the card-name format arg when building a splittable sentence template
 * for [findingCardNameSentence] — a private-use marker vanishingly unlikely to collide with any
 * real `strings.xml` copy or card name. */
private const val CARD_NAME_TOKEN = "CARD_NAME"

/**
 * Deck Analysis Engine v2 Phase 3 review fix (P1 #5): for the 4 [Finding] variants whose English
 * sentence embeds a raw card name mid-string (`TooManyCopies`/`SingletonViolation`/
 * `OffColorIdentity`/`IllegalCard`), resolves the SAME `strings.xml` template used by this file's
 * `Finding.label()` mapper (`DeckDoctorStrings.kt`) but with [CARD_NAME_TOKEN] in place of the
 * name, then splits on that token — so the caller can render the name through [CardName]
 * (CLAUDE.md's mandatory component rule: "A-" prefix / Alchemy-icon substitution) instead of
 * interpolating it as plain text. Returns `null` for every other [Finding] (those render
 * `finding.label()` unsplit, unchanged).
 */
@Composable
private fun findingCardNameSentence(finding: Finding): Triple<String, String, String>? {
    val (template, cardName) = when (finding) {
        is Finding.TooManyCopies ->
            stringResource(R.string.deck_analysis_finding_too_many_copies, CARD_NAME_TOKEN, finding.copies, finding.maxCopies) to finding.cardName
        is Finding.SingletonViolation ->
            stringResource(R.string.deck_analysis_finding_singleton_violation, CARD_NAME_TOKEN, finding.copies) to finding.cardName
        is Finding.OffColorIdentity ->
            stringResource(R.string.deck_analysis_finding_off_color_identity, CARD_NAME_TOKEN) to finding.cardName
        is Finding.IllegalCard ->
            stringResource(R.string.deck_analysis_finding_illegal_card, CARD_NAME_TOKEN) to finding.cardName
        else -> return null
    }
    val tokenIndex = template.indexOf(CARD_NAME_TOKEN)
    if (tokenIndex < 0) return null
    return Triple(
        template.substring(0, tokenIndex),
        cardName,
        template.substring(tokenIndex + CARD_NAME_TOKEN.length),
    )
}

/** One severity-tinted finding row (plan §3.4 item 4), mirroring the legacy `WarningChip`'s visual
 * language but colored per-[Finding.severity] instead of always the alert color. */
@Composable
fun FindingRow(
    finding: Finding,
    modifier: Modifier = Modifier,
) {
    val ty = MaterialTheme.magicTypography
    val mc = MaterialTheme.magicColors
    val tint = finding.severity.tint()
    val cardNameSentence = findingCardNameSentence(finding)

    Surface(
        color = tint.copy(alpha = 0.14f),
        shape = ChipShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
            Spacer(Modifier.width(MaterialTheme.spacing.sm))
            if (cardNameSentence != null) {
                val (prefix, cardName, suffix) = cardNameSentence
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (prefix.isNotEmpty()) {
                        Text(text = prefix, style = ty.bodyMedium, color = mc.textPrimary)
                    }
                    CardName(name = cardName, style = ty.bodyMedium, color = mc.textPrimary)
                    if (suffix.isNotEmpty()) {
                        Text(text = suffix, style = ty.bodyMedium, color = mc.textPrimary)
                    }
                }
            } else {
                Text(text = finding.label(), style = ty.bodyMedium, color = mc.textPrimary)
            }
        }
    }
}

/** Inert "+N more" caption for a pillar's [PillarResult.collapsedFindingsCount] (plan §3.3's
 * finding budget) — [PillarResult] only ever exposes the OVERFLOW COUNT, never the budgeted-out
 * [Finding] objects themselves ([com.mmg.manahub.feature.decks.domain.engine.AnalysisEngine]'s
 * `budgetFindings` computes the count then discards the rest), so there is nothing to reveal on
 * tap — this is deliberately NOT clickable (see this phase's report for why no Phase 2 touch-up
 * was needed here). */
@Composable
fun CollapsedFindingsCaption(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Text(
        text = stringResource(R.string.deck_analysis_findings_collapsed_count, count),
        style = MaterialTheme.magicTypography.labelSmall,
        color = MaterialTheme.magicColors.textSecondary,
        modifier = modifier,
    )
}

/** Expand/collapse chevron affordance — reused wherever a pillar's detail section can be toggled
 * (kept here, not inline, since [PillarTile] itself never renders it: the WHOLE tile is the tap
 * target, per plan §3.4 item 3, "tapping a tile expands its detail section"). */
@Composable
fun ExpandChevron(expanded: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.magicColors.textSecondary,
        modifier = modifier.size(20.dp),
    )
}
