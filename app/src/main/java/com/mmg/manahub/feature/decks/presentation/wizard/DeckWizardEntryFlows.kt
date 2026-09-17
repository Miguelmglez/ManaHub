package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-17

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ColorStrategyEntry
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.StrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.ThemeId

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Engine Unification plan (`docs/plans/deck-engine-unification-plan.md` §5 Phase 3) --
//  the entry chooser (3.1). Deck Wizard 60-card wave (v6, plan §5 Phase 5.1): the pre-v6
//  colors-first/strategy-first DIRECTION content (`ColorsFlowDirectionContent`/
//  `StrategyFlowDirectionContent`) was deleted here along with `DeckWizardDirectionIdentity.kt`
//  (both dismantled the same DIRECTION/IDENTITY step machinery, S1's UI unification) -- their
//  replacements (`ColorPickStepContent`/`StrategyPickStepContent`) land in run B (plan §5 Phase
//  5.3). [StrategyOptionRow]/[InlineColorComboSection]/[ColorComboChip] below SURVIVE (self-
//  contained, parameterized, no deleted-state dependency) for run B to reuse.
// ═══════════════════════════════════════════════════════════════════════════════

// ── Entry chooser (3.1) ───────────────────────────────────────────────────────

/**
 * The new first Casual step (Commander skips this entirely -- see [WizardEntryFlow]'s KDoc):
 * "Start from cards" / "Start from colors" / "Start from strategy". Tapping a card BOTH picks the
 * flow AND advances (`onSelect` drives [DeckWizardViewModel.onSelectEntryFlow], which sets the phase
 * itself) -- there is nothing else to configure on this screen, so no separate sticky "Next" CTA.
 */
@Composable
internal fun EntryStepContent(onSelect: (WizardEntryFlow) -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = spacing.lg, vertical = spacing.md)) {
        Text(stringResource(R.string.deck_wizard_entry_title), style = ty.titleLarge, color = mc.textPrimary)
        Text(
            stringResource(R.string.deck_wizard_entry_subtitle),
            style = ty.bodyMedium,
            color = mc.textSecondary,
            modifier = Modifier.padding(top = spacing.xxs, bottom = spacing.lg),
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            EntryFlowCard(
                icon = Icons.Default.AutoAwesome,
                title = stringResource(R.string.deck_wizard_entry_cards_title),
                subtitle = stringResource(R.string.deck_wizard_entry_cards_subtitle),
                onClick = { onSelect(WizardEntryFlow.CARDS) },
            )
            EntryFlowCard(
                icon = Icons.Default.Palette,
                title = stringResource(R.string.deck_wizard_entry_colors_title),
                subtitle = stringResource(R.string.deck_wizard_entry_colors_subtitle),
                onClick = { onSelect(WizardEntryFlow.COLORS) },
            )
            EntryFlowCard(
                icon = Icons.Default.Style,
                title = stringResource(R.string.deck_wizard_entry_strategy_title),
                subtitle = stringResource(R.string.deck_wizard_entry_strategy_subtitle),
                onClick = { onSelect(WizardEntryFlow.STRATEGY) },
            )
        }
    }
}

@Composable
private fun EntryFlowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(onClick = onClick, shape = CardShape, color = mc.surface, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(spacing.lg).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Surface(shape = ChipShape, color = mc.primaryAccent.copy(alpha = 0.14f)) {
                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = mc.primaryAccent, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = ty.titleMedium, color = mc.textPrimary)
                Text(subtitle, style = ty.bodySmall, color = mc.textSecondary, modifier = Modifier.padding(top = spacing.xxs))
            }
        }
    }
}

/**
 * Deck Wizard & Engine Rework plan, Workstream 3.1 — a WUBRG mana-symbol color toggle chip.
 * Deck Wizard 60-card wave (v6, plan §5 Phase 5.1): relocated here from the deleted
 * `DeckWizardDirectionIdentity.kt` (that file's whole DIRECTION/IDENTITY step machinery was
 * dismantled by S1's UI unification) since run B's `ColorPickStepContent` (plan §5 Phase 5.3)
 * still needs this exact chip — body unchanged.
 *
 * @param lockedDescriptionRes the accessibility copy for the [readOnly] (locked) state differs by
 *   WHY the color is locked: Commander's identity ("fixed by commander") vs. a seed-locked color
 *   ([DeckWizardUiState.lockedColors]). Defaults to the original Commander string so every
 *   pre-existing call site is unaffected.
 */
@Composable
internal fun ColorToggleChip(
    color: ManaColor,
    selected: Boolean,
    readOnly: Boolean,
    onClick: () -> Unit,
    lockedDescriptionRes: Int = R.string.deck_wizard_color_fixed_by_commander,
) {
    val mc = MaterialTheme.magicColors
    val border = if (selected) BorderStroke(1.5.dp, mc.primaryAccent) else BorderStroke(0.5.dp, mc.surfaceVariant)
    val background = if (selected) mc.primaryAccent.copy(alpha = 0.18f) else mc.surface
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            ManaSymbolImage(token = color.symbol, size = 24.dp)
        }
    }
    // Read-only (Commander -- colors are derived from the commander, not user-editable; OR a
    // color a currently-picked seed's identity requires): a plain Surface with no `onClick`
    // overload so it never shows an interactive ripple. An explicit contentDescription
    // distinguishes the two variants for TalkBack users.
    if (readOnly) {
        val fixedColorDescription = stringResource(lockedDescriptionRes, color.displayName)
        Surface(
            shape = CircleShape,
            color = background,
            border = border,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = fixedColorDescription },
            content = content,
        )
    } else {
        Surface(onClick = onClick, shape = CircleShape, color = background, border = border, modifier = Modifier.size(48.dp), content = content)
    }
}

/** Deck Wizard 60-card wave (v6), plan §5 Phase 5.3: the inline color-combo picker rendered
 * directly below whichever [StrategyOptionRow] is currently selected in run B's
 * `StrategyPickStepContent`. Reuses the exact [ColorComboChip] visual. */
@Composable
internal fun InlineColorComboSection(
    suggestions: List<ColorComboSuggestion>,
    selectedColors: Set<ManaColor>,
    onSelectCombo: (ColorComboSuggestion) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Column(modifier = Modifier.padding(start = spacing.md, top = spacing.xs)) {
        Text(stringResource(R.string.deck_wizard_color_combos_title), style = ty.labelMedium, color = mc.primaryAccent)
        if (suggestions.isEmpty()) {
            Text(
                stringResource(R.string.deck_wizard_color_combos_empty),
                style = ty.bodySmall,
                color = mc.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                modifier = Modifier.padding(top = spacing.xxs),
            ) {
                suggestions.forEach { combo ->
                    ColorComboChip(colors = combo.colors, selected = combo.colors == selectedColors, onClick = { onSelectCombo(combo) })
                }
            }
        }
    }
}

// ── Color-combo suggestion chip (real mana-symbol icons, not letters) ────────────────────────

/** A [ColorComboSuggestion] pick, rendered as real WUBRG mana-symbol icons ([ManaCostImages])
 * instead of plain `.symbol` letters -- same pattern `DeckWizardScreen.ReviewColorsRow` already
 * established (`ManaColor.symbol` is a bare letter, wrapped in `{}` only to satisfy
 * [ManaCostImages]' cost-string parser). Falls back to the existing "Colorless" copy for an empty
 * [colors] set. */
@Composable
internal fun ColorComboChip(colors: Set<ManaColor>, selected: Boolean, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        onClick = onClick,
        shape = ChipShape,
        color = if (selected) mc.primaryAccent.copy(alpha = 0.2f) else mc.surface,
    ) {
        Box(
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = spacing.md),
            contentAlignment = Alignment.Center,
        ) {
            if (colors.isEmpty()) {
                Text(
                    text = stringResource(R.string.deck_seeds_identity_colorless),
                    style = ty.labelMedium,
                    color = if (selected) mc.primaryAccent else mc.textSecondary,
                )
            } else {
                ManaCostImages(
                    manaCost = colors.sortedBy { it.ordinal }.joinToString(separator = "") { "{${it.symbol}}" },
                    symbolSize = 18.dp,
                    spacing = spacing.xxs,
                )
            }
        }
    }
}

// ── Shared -- one-per-row strategy option (run B's COLOR_PICK/STRATEGY_PICK, plan §5 Phase 5.3) ──

/**
 * A one-row-per-strategy option: label + a short plain-English explanation of what the strategy
 * means in deckbuilding terms, plus a check-circle selected indicator. Deck Wizard 60-card wave
 * (v6): reused by run B's `ColorPickStepContent`/`StrategyPickStepContent` (plan §5 Phase 5.3) --
 * the pre-v6 Flow A/B/C callers that used to share this row were deleted with the DIRECTION step.
 *
 * @param misfitContent optional trailing content rendered below the description; `null` (every
 *   current call site) renders nothing extra.
 */
@Composable
internal fun StrategyOptionRow(
    label: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    misfitContent: (@Composable () -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = if (selected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface,
        border = if (selected) BorderStroke(1.dp, mc.primaryAccent) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md).heightIn(min = 48.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = ty.titleMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    description,
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
                misfitContent?.invoke()
            }
            Surface(
                shape = CircleShape,
                color = if (selected) mc.primaryAccent else mc.surfaceVariant,
                modifier = Modifier.size(24.dp),
            ) {
                if (selected) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = mc.onAccent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * Deck Wizard & Engine Rework plan, Workstream 1.1/3 -- delegates to the single shared
 * [StrategyCatalog] (`commonMain`) instead of this file's own `R.string`-mapping duplicate (retired
 * by this workstream per WS1's own memory note: "once WS2/3 wire the wizard onto StrategyCatalog,
 * retire the private description mapping ... don't let both copies drift independently"). Text is
 * unchanged (StrategyCatalog mirrors these exact strings verbatim) -- this is a pure de-duplication,
 * not a copy change. Kept as a thin extension (not inlined at every call site) so `archetype
 * .description()` reads the same as before at every existing call site in this file.
 */
private fun ArchetypeId.description(): String = StrategyCatalog.description(this)

/** See [ArchetypeId.description]'s KDoc -- same de-duplication, [ThemeId] side. */
private fun ThemeId.description(): String = StrategyCatalog.description(this)

/** [ColorStrategyEntry.description] -- same fallback order as [ColorStrategyEntry.label]'s own KDoc:
 * the archetype's description first, else the first theme's, else a plain "Balanced" fallback
 * (Deck Analysis Engine v3 removed `ArchetypeId.GENERIC` -- a [ColorStrategyEntry] with neither is
 * never actually curated in [ColorStrategyAffinity]'s table today, but the fallback keeps this
 * total rather than crashing if one ever is). */
private fun ColorStrategyEntry.description(): String {
    val archetype = archetype
    return when {
        archetype != null -> archetype.description()
        themes.isNotEmpty() -> themes.first().description()
        else -> "A flexible, balanced approach with no single dominant strategy."
    }
}
