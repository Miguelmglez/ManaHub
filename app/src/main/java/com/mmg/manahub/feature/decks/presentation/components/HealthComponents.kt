package com.mmg.manahub.feature.decks.presentation.components


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.DeckAnalysis
import com.mmg.manahub.feature.decks.domain.engine.Finding
import com.mmg.manahub.feature.decks.domain.engine.FindingSeverity
import com.mmg.manahub.feature.decks.domain.engine.PillarId
import com.mmg.manahub.feature.decks.domain.engine.PillarResult
import com.mmg.manahub.feature.decks.domain.engine.RoleCoverageEntry
import com.mmg.manahub.feature.decks.domain.engine.ScoreLimiter

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
 *
 * Widened from `private` to `internal` (Deck Analysis Category Sections rework, W7) so
 * `CardSectionComponents.kt`'s [com.mmg.manahub.feature.decks.presentation.components
 * .CardSectionHeader] can reuse the SAME good→mid→low ramp [RoleCoverageEntryRow] uses, rather
 * than duplicating it — a top-level `private` declaration is FILE-private in Kotlin, not
 * package-private, so same-package access from another file still needs `internal`.
 */
internal fun MagicColors.qualityColor(fraction: Float): Color {
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
    val animated by androidx.compose.animation.core.animateFloatAsState(targetValue = fraction, label = "healthScore")
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
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
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

/**
 * Small, tappable hint row shown near [HealthScoreRing] when [DeckAnalysis.limiter] reports that
 * ONE specific thing is dominantly holding [DeckAnalysis.totalScore] back — see [ScoreLimiter]'s
 * own KDoc for the live-device finding this exists to fix (a player reading a legality-capped
 * score as "the strategy switcher must be broken", when 3 illegal cards were hard-capping the total
 * regardless of the correctly-recomputing pillars underneath). Renders nothing for
 * [ScoreLimiter.None] — the common case. Styled as an inline hint/caption (cautionary
 * [MagicColors.goldMtg], matching [FindingSeverity.WARNING]'s own tint), not a
 * `FullErrorState`/`MagicAlertDialog` — this is informational, not an error.
 *
 * Tapping jumps focus to the relevant pillar tile via [onExpandPillar] — the SAME `expandedPillar`
 * state the pillar-tile row itself already drives (see `DeckStudioScreen`'s `SuggestionsTab` call
 * site), never a second expansion mechanism.
 */
@Composable
fun ScoreLimiterHint(
    limiter: ScoreLimiter,
    onExpandPillar: (PillarId) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (limiter == ScoreLimiter.None) return
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val (text, targetPillar) = when (limiter) {
        is ScoreLimiter.LegalityCapped ->
            stringResource(R.string.deck_analysis_limiter_legality_capped, limiter.uncappedScore) to PillarId.LEGALITY
        is ScoreLimiter.DominantPillar -> {
            val pillarLabel = limiter.pillarId.label()
            stringResource(R.string.deck_analysis_limiter_dominant_pillar, pillarLabel) to limiter.pillarId
        }
        ScoreLimiter.None -> return
    }
    val actionLabel = stringResource(R.string.deck_analysis_limiter_action)

    Surface(
        color = mc.backgroundSecondary,
        shape = CardShape,
        border = BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(mc.goldMtg.copy(alpha = 0.12f), mc.backgroundSecondary)
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .sizeIn(minHeight = 48.dp)
                    .clickable(onClickLabel = actionLabel, role = Role.Button) { onExpandPillar(targetPillar) }
                    .padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(mc.goldMtg.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = mc.goldMtg,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = text,
                    style = ty.bodySmall,
                    color = mc.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
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
 * decorative pairing with the tile's own text label. Returns either an [ImageVector] or a
 * resource ID [Int] (for ic_land). */
private fun PillarId.iconRes(): Any = when (this) {
    PillarId.MANA_BASE -> R.drawable.ic_land
    PillarId.CURVE -> Icons.AutoMirrored.Filled.TrendingUp
    PillarId.PLAN_ROLES -> Icons.Default.Checklist
    PillarId.SYNERGY -> Icons.Default.Hub
    PillarId.LEGALITY -> Icons.Default.Gavel
}

/**
 * One pillar tile in the horizontal pillar row (plan §3.4 item 3): icon, [PillarId.label], the
 * 0-100 [PillarResult.subscore], and a status color from [MagicColors.qualityColor]. Tapping
 * toggles [expanded] (hoisted — the caller owns which single pillar is expanded).
 *
 * Deck Analysis Engine v3 fix (Phase 5, 2026-08-27): [PillarResult.notApplicable] (currently only
 * SYNERGY, on a zero-edge deck) renders as a muted "N/A" badge instead of the forced `subscore = 0`
 * — that 0 is a scoring formality with no real measurement behind it (see the field's own KDoc);
 * showing it as a number here would read as "maximally incoherent" when the true state is "this
 * pillar does not apply". [MagicColors.textDisabled] (not the [MagicColors.qualityColor] ramp,
 * which would render 0 as an alert-red circle) keeps the tile visually distinct from a genuinely
 * low-scoring pillar.
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
    val statusColor = if (pillar.notApplicable) mc.textDisabled else mc.qualityColor(pillar.subscore / 100f)
    // Edge-case QA fix (HIGH, 2026-09-06): the pillar tile used to render icon + subscore +
    // Text(pillar.id.label()) -- a rewrite dropped the label text, leaving 4 of 5 tiles
    // unidentifiable (icon + bare number only) both visually and for screen readers (both Icon
    // branches below already passed contentDescription = null). Restoring the visible label AND
    // setting the icon's contentDescription (kept even though the label text is also visible, so
    // the pillar's identity survives on its own if the visible label is ever compacted away again)
    // fixes both regressions. `heightIn(min = ...)` (not a fixed `.height()`) lets the tile grow
    // for a 2-line label / larger font scale instead of clipping it.
    val pillarLabel = pillar.id.label()

    Surface(
        onClick = onClick,
        shape = CardShape,
        color = if (expanded) statusColor.copy(alpha = 0.14f) else mc.backgroundSecondary,
        border = BorderStroke(
            width = if (expanded) 2.dp else 1.dp,
            color = if (expanded) statusColor else mc.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.heightIn(min = 100.dp),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs, Alignment.CenterVertically),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                val icon = pillar.id.iconRes()
                if (icon is ImageVector) {
                    Icon(
                        imageVector = icon,
                        contentDescription = pillarLabel,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                } else if (icon is Int) {
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = pillarLabel,
                        tint = statusColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = if (pillar.notApplicable) stringResource(R.string.deck_analysis_pillar_not_applicable_badge) else pillar.subscore.toString(),
                style = ty.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = statusColor,
            )
            Text(
                text = pillarLabel,
                style = ty.labelSmall,
                color = mc.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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
 * on [RoleCoverageEntryRow]'s bar. Widened from `private` to `internal` (Deck Analysis Category
 * Sections rework, W7) so `CardSectionComponents.kt`'s [com.mmg.manahub.feature.decks
 * .presentation.components.CardSectionHeader] can reuse the exact same bar geometry. */
@Composable
internal fun RoleBandMarker(fraction: Float, color: Color) {
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

/** Severity → icon (Suggestions Tab UI Polish plan, W4 visual pass — replaces the old bare 8dp
 * color dot with a real icon-in-tonal-circle badge, matching [PillarTile]'s own icon-badge
 * treatment elsewhere in this file, for a stronger type/severity hierarchy than a flat dot gave). */
private fun FindingSeverity.icon(): ImageVector = when (this) {
    FindingSeverity.BLOCKER -> Icons.Default.Error
    FindingSeverity.WARNING -> Icons.Default.Warning
    FindingSeverity.INFO -> Icons.Default.Info
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

/**
 * One severity-tinted finding row (plan §3.4 item 4), mirroring the legacy `WarningChip`'s visual
 * language but colored per-[Finding.severity] instead of always the alert color.
 *
 * Suggestions Tab UI Polish plan (W4) visual pass: the old flat tonal [Surface] + bare 8dp color
 * dot + plain text ("se ven muy planos y con el texto muy aburrido", the user's own words) is
 * replaced with an icon-in-tonal-circle severity badge (mirrors [PillarTile]'s own badge
 * treatment elsewhere in this file) for a real type/severity hierarchy instead of a flat dot.
 */
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
        color = mc.backgroundSecondary,
        shape = CardShape,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.3f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.1f), mc.backgroundSecondary)
                )
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = finding.severity.icon(),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(14.dp),
                    )
                }
                if (cardNameSentence != null) {
                    val (prefix, cardName, suffix) = cardNameSentence
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (prefix.isNotEmpty()) {
                            Text(text = prefix, style = ty.bodyMedium, color = mc.textPrimary)
                        }
                        CardName(name = cardName, style = ty.bodyMedium, color = mc.textPrimary)
                        if (suffix.isNotEmpty()) {
                            Text(text = suffix, style = ty.bodyMedium, color = mc.textPrimary)
                        }
                    }
                } else {
                    Text(
                        text = finding.label(),
                        style = ty.bodyMedium,
                        color = mc.textPrimary,
                        fontWeight = if (finding.severity == FindingSeverity.BLOCKER) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

/**
 * Show-first-N / show-more-less over a pillar's FULL findings list (Suggestions Tab UI Polish
 * plan, W4/D5) — replaces the old engine-side hard truncation
 * ([com.mmg.manahub.feature.decks.domain.engine.AnalysisEngine]'s `budgetFindings` used to discard
 * anything past 3 non-BLOCKER findings; `PillarResult.collapsedFindingsCount`/
 * `CollapsedFindingsCaption` were the only surviving trace of what got dropped, with no way to
 * ever reveal it). [findings] is now the FULL BLOCKER-first, severity/magnitude-sorted list
 * (`AnalysisEngine.sortFindings`) — this composable folds the first [initiallyShown] itself and
 * reveals the rest on tap, entirely client-side, with no engine involvement.
 *
 * `showAll` is `remember`ed keyed on [findings] itself (not a pillar id) so it naturally resets
 * whenever the caller swaps in a different pillar's finding list (switching the expanded pillar
 * tile) — the simplest option per the plan's own note, and avoids a second id-keyed map alongside
 * `DeckStudioScreen`'s existing `collapsedCategorySections`.
 */
@Composable
fun FindingsList(
    findings: List<Finding>,
    modifier: Modifier = Modifier,
    initiallyShown: Int = 3,
) {
    if (findings.isEmpty()) return
    var showAll by remember(findings) { mutableStateOf(false) }
    val overflow = (findings.size - initiallyShown).coerceAtLeast(0)
    val visible = if (showAll || overflow == 0) findings else findings.take(initiallyShown)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xxs),
    ) {
        visible.forEach { finding -> FindingRow(finding = finding) }
        if (overflow > 0) {
            TextButton(
                onClick = { showAll = !showAll },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = if (showAll) stringResource(R.string.deck_analysis_findings_show_less)
                    else stringResource(R.string.deck_analysis_findings_show_more, overflow),
                    style = MaterialTheme.magicTypography.labelMedium,
                    color = MaterialTheme.magicColors.primaryAccent,
                )
            }
        }
    }
}

/**
 * SYNERGY-pillar-only sub-caption ("Aligned N / M non-lands") rendered under the pillar detail
 * header — Deck Analysis Category Sections rework (W2). Reads
 * [PillarResult.alignedNonLandCopies]/[PillarResult.totalNonLandCopies] directly (raw Ints the
 * engine exposes; it never formats the sentence itself, per [Finding]'s own string-free
 * discipline) so the number on screen is traceable to the SAME `alignedCopies`/`nonLandCount`
 * [AnalysisEngine.evaluateSynergy]'s `subscore` is built from — never re-derived from
 * [PillarResult.sections] (summing aligned sections' `current` would double-count a card aligned
 * on 2+ keys). Deliberately plain/no extra visual weight: SYNERGY is the weakest-calibrated of the
 * 5 pillars (see [AnalysisEngine.evaluateSynergy]'s KDoc) — this caption is the "honest framing"
 * the plan calls for, not a badge or a second score.
 */
@Composable
fun SynergyAlignmentCaption(alignedCopies: Int, totalCopies: Int, modifier: Modifier = Modifier) {
    if (totalCopies <= 0) return
    Text(
        text = stringResource(R.string.deck_analysis_synergy_alignment_caption, alignedCopies, totalCopies),
        style = MaterialTheme.magicTypography.labelSmall,
        color = MaterialTheme.magicColors.textSecondary,
        modifier = modifier,
    )
}

// ExpandChevron was deleted (Suggestions Tab UI Polish plan, W0/D1) — its one call site
// (DeckStudioScreen.kt's SuggestionsTab pillar-detail header) was a hardcoded `expanded = true`,
// decorative and non-functional (D1). The shared `SectionHeader` component
// (`shared/core-ui/.../components/SectionHeader.kt`) now owns chevron rendering everywhere a
// real (non-decorative) collapse toggle is needed, folding in the exact same icon-swap shape this
// function used to provide.
