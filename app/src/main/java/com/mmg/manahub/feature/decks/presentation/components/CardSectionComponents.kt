package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import androidx.compose.ui.res.painterResource as androidPainterResource
import org.jetbrains.compose.resources.painterResource
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.MiniProgressRing
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.CardSection

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Analysis Category Sections rework (W7, docs/plans/deck-analysis-category-sections-plan.md)
//  + Suggestions Tab UI Polish plan (2026-08-25, W1/W2/W3/W5/W6/W8) — see each composable's own
//  KDoc below for what changed in the polish pass. Renders a per-category section list inside
//  EVERY expanded pillar tile (MANA_BASE/CURVE/PLAN_ROLES/SYNERGY/LEGALITY all populate
//  PillarResult.sections). Each section is now individually collapsible, wrapped in its own
//  gradient "card" surface for visual separation, with a compact header (SectionHeader, shared
//  component) whose trailing slot carries either a plain count or a MiniProgressRing + count for
//  banded sections — replacing the old always-expanded linear band bar.
// ═══════════════════════════════════════════════════════════════════════════════

/** Prefix [AnalysisEngine][com.mmg.manahub.feature.decks.domain.engine.AnalysisEngine]'s Mana
 * Base pillar uses for its per-color "produces:X" sections — kept in sync with that file's own
 * `CardSection(id = "produces:${color.symbol}", ...)` construction. */
private const val PRODUCES_SECTION_PREFIX = "produces:"

/**
 * One section's header row: [CardSection.label], its `current`/`ideal` (or `current`/max N for an
 * anti-role) count, and — only when the section carries a real target band (`min`/`ideal`/`max`
 * all non-null, e.g. every `role:*` section) — a compact [MiniProgressRing] next to the count
 * (Suggestions Tab UI Polish plan, W8/D6 — replaces the old always-visible linear band bar with
 * `RoleBandMarker` min/max ticks; the min/max markers themselves are dropped, not translated onto
 * the ring, per that same decision's documented simplicity trade-off). A bandless section (curve/
 * mana-color/synergy/legality/offplan) renders the count alone, no ring.
 *
 * Rendering itself is delegated to the shared [SectionHeader] component (W0) — this function only
 * computes this screen's specific quality/color/leading-icon logic and wires it into that
 * component's `leading`/`trailing` slots. Mana Base's per-color sections (`"produces:X"` ids) pass
 * a real [ManaSymbolImage] as `leading` instead of a plain color-name string baked into the label
 * (W6/D-b) — [AnalysisEngine] emits a plain color name as `label` now (e.g. "White"), never the
 * raw `"{W}"` token.
 */
@Composable
fun CardSectionHeader(
    section: CardSection,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val hasBand = section.min != null && section.ideal != null && section.max != null

    val leading: (@Composable () -> Unit)? = when {
        section.id.startsWith(PRODUCES_SECTION_PREFIX) -> {
            val token = section.id.removePrefix(PRODUCES_SECTION_PREFIX)
            ({ ManaSymbolImage(token = token, size = 20.dp) })
        }
        section.id == "land_count" || section.id == "mana_fix" -> {
            ({ Icon(androidPainterResource(R.drawable.ic_land), contentDescription = null, tint = mc.textSecondary, modifier = Modifier.size(20.dp)) })
        }
        // Deck Analysis Engine v3, Phase 5 (UI) — the 3-way "offplan" split (spec §7) gets a
        // distinct icon + tint PER bucket so the three read as different categories at a glance,
        // not just three differently-labeled rows: "interaction" is a legitimate, positive signal
        // (a Swords to Plowshares must never read as off-plan — lifePositive/Shield), "standalone"
        // is neutral ("individually strong, no edges" — goldMtg/Star), "offplan" is the true
        // catch-all (textDisabled/HelpOutline — least visual weight, never alarming red).
        section.id == "interaction" -> ({ Icon(Icons.Default.Shield, contentDescription = null, tint = mc.lifePositive, modifier = Modifier.size(20.dp)) })
        section.id == "standalone" -> ({ Icon(Icons.Default.Star, contentDescription = null, tint = mc.goldMtg, modifier = Modifier.size(20.dp)) })
        section.id == "offplan" -> ({ Icon(Icons.Default.HelpOutline, contentDescription = null, tint = mc.textDisabled, modifier = Modifier.size(20.dp)) })
        else -> null
    }

    if (!hasBand) {
        SectionHeader(
            title = section.label,
            expanded = expanded,
            onToggle = onToggle,
            leading = leading,
            trailing = {
                Text(
                    text = section.current.toString(),
                    style = ty.labelLarge,
                    color = mc.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = MaterialTheme.spacing.xs)
                )
            },
            modifier = modifier,
        )
        return
    }

    // Same [0,1] quality shape RoleCoverageEntryRow's own `quality` local used (and this file's
    // old linear-bar version before the W8 ring swap) — both must agree on what "healthy" looks
    // like for the SAME underlying band.
    val min = section.min ?: 0
    val ideal = section.ideal ?: 0
    val max = section.max ?: 0
    val quality = if (section.isAntiRole) {
        if (section.current <= max) 1f else (max.toFloat() / section.current).coerceIn(0f, 1f)
    } else when {
        ideal <= 0 -> 1f
        section.current <= ideal -> (section.current.toFloat() / ideal).coerceIn(0f, 1f)
        section.current <= max -> 1f
        else -> (max.toFloat() / section.current).coerceIn(0f, 1f)
    }
    // See RoleCoverageEntryRow's own comment for why an over-max anti-role bypasses the continuous
    // ramp entirely (a mid-ramp gold reads as mild caution, but this must read as an immediate alert
    // — same rule FindingRow applies to Finding.AntiRoleOverMax).
    val ringColor = if (section.isAntiRole && section.current > max) mc.lifeNegative else mc.qualityColor(quality)

    val valueText = if (section.isAntiRole) {
        stringResource(R.string.deck_analysis_role_anti_max_format, section.current, max)
    } else {
        stringResource(R.string.deck_analysis_section_progress_format, section.current, ideal)
    }

    SectionHeader(
        title = section.label,
        expanded = expanded,
        onToggle = onToggle,
        leading = leading,
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                modifier = Modifier.padding(end = MaterialTheme.spacing.xs)
            ) {
                Text(
                    text = valueText,
                    style = ty.labelLarge,
                    color = ringColor,
                    fontWeight = FontWeight.Bold,
                )
                MiniProgressRing(value = quality, color = ringColor, contentDescription = valueText)
            }
        },
        modifier = modifier,
    )
}

/**
 * One category "card" inside the expanded pillar detail: a subtle gradient-filled [Box] (W5 —
 * the visual anchor that separates consecutive sections into distinct cards instead of a
 * continuous wall of text) wrapping [CardSectionHeader] and, only while [expanded] (W1 — every
 * section is now individually collapsible, mirroring `CollectionScreen.kt`'s
 * `CollectionGroupHeader`/`collapsedSections` pattern), a horizontally-scrolling row of the deck's
 * own cards that fill it (or, when [CardSection.contributions] is empty, a consistent placeholder
 * — W3) plus an optional "Browse for &lt;Category&gt;" button.
 *
 * [resolveCard] is expected to read from the caller's already-loaded deck slots
 * (`DeckStudioUiState.cards`) — [CardSection]/[CardContribution][com.mmg.manahub.feature.decks
 * .domain.engine.CardContribution] carry only ids/primitives by design (never a [Card] object), so
 * this composable never resolves cards itself. A contribution whose id [resolveCard] cannot
 * resolve (e.g. an in-flight/unresolved deck slot) is silently skipped rather than crashing — this
 * pillar list must degrade gracefully on a sparse/degenerate deck (an empty Draft pillar, a
 * just-imported deck still resolving cards), not throw.
 *
 * @param expanded hoisted per-section collapse state — the caller keys this by a composite
 *   `"${pillarId}:${section.id}"` string (see `DeckStudioScreen.kt`'s `SuggestionsTab`).
 * @param onBrowse `null` hides the "Browse for &lt;Category&gt;" button entirely (Off-plan, and any
 *   section [com.mmg.manahub.feature.decks.domain.engine.SectionSearchQuery.fragmentFor] returns
 *   `null` for — e.g. `role:tribe_members` on a deck with no dominant tribe).
 */
@Composable
fun CardSectionRow(
    section: CardSection,
    resolveCard: (String) -> Card?,
    onCardClick: (String) -> Unit,
    onBrowse: (() -> Unit)?,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing

    Surface(
        shape = CardShape,
        color = mc.backgroundSecondary,
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(mc.surface.copy(alpha = 0.3f), mc.backgroundSecondary)
                    )
                )
                .padding(spacing.sm),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                CardSectionHeader(section = section, expanded = expanded, onToggle = onToggleExpanded)

                if (expanded) {
                    Spacer(Modifier.height(spacing.sm))
                    if (section.contributions.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            contentPadding = PaddingValues(horizontal = spacing.xxs, vertical = spacing.xxs),
                        ) {
                            // Stable key includes the SECTION id, not just the card id — a card can
                            // legitimately appear in multiple sections at once (e.g. a dual land in
                            // two `produces:X` sections, a card aligned on two SYNERGY fingerprint
                            // keys), and this row only ever renders ONE section's contributions at a
                            // time, so this is enough to stay unique within it.
                            items(section.contributions, key = { "${section.id}:${it.scryfallId}" }) { contribution ->
                                val card = resolveCard(contribution.scryfallId)
                                if (card != null) {
                                    SectionCardThumbnail(card = card, onClick = { onCardClick(card.scryfallId) })
                                }
                            }
                        }
                    } else {
                        EmptySectionPlaceholder()
                    }

                    if (onBrowse != null) {
                        Spacer(Modifier.height(spacing.md))
                        MagicCtaButton(
                            text = stringResource(R.string.deck_analysis_section_browse, section.label),
                            onClick = onBrowse,
                            style = MagicCtaStyle.Outlined,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/** [CardSectionRow]'s thumbnail footprint (W2's enlarged size) — reused by [EmptySectionPlaceholder]
 * (W3) so the placeholder occupies the exact same height a real card row would, keeping every
 * section's collapsed/expanded footprint visually consistent regardless of whether it has cards. */
private val SECTION_THUMBNAIL_WIDTH = 72.dp
private val SECTION_THUMBNAIL_HEIGHT = 104.dp

/**
 * One card thumbnail in a [CardSectionRow]'s [LazyRow]: just the card's art, tappable (→
 * [onClick], the screen's normal card-detail entry point). Suggestions Tab UI Polish plan (W2/D4):
 * the per-card 🔗 "find decks with this card" link icon that used to stack below the art was
 * deleted outright (over-scoped per the user's direct feedback) — the thumbnail is now a single
 * tappable [Box] with no second tap target, so it can grow to fill the freed vertical space
 * (56×80dp → 72×104dp, close to `SmallCardShape`'s real card aspect ratio).
 */
@Composable
private fun SectionCardThumbnail(
    card: Card,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors

    Box(
        modifier = modifier
            .size(width = SECTION_THUMBNAIL_WIDTH, height = SECTION_THUMBNAIL_HEIGHT)
            .clip(SmallCardShape)
            .background(mc.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = card.imageNormal,
            contentDescription = card.name,
            placeholder = painterResource(Res.drawable.mtg_card_back),
            error = painterResource(Res.drawable.mtg_card_back),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Muted placeholder shown in place of the card [LazyRow] when [CardSection.contributions] is
 * empty (Suggestions Tab UI Polish plan, W3) — previously the row was simply omitted, which is
 * exactly why sections looked inconsistent (some had a card row, some didn't, no visual anchor for
 * a real gap). Matches [SectionCardThumbnail]'s own height so a section's footprint doesn't jump
 * around depending on whether it happens to have cards.
 */
@Composable
private fun EmptySectionPlaceholder(modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SECTION_THUMBNAIL_HEIGHT)
            .clip(SmallCardShape)
            .background(mc.surfaceVariant.copy(alpha = 0.4f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.deck_analysis_section_empty),
            style = ty.labelSmall,
            color = mc.textDisabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
