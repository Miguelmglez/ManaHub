package com.mmg.manahub.feature.decks.presentation.wizard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.components.ManaCostImages
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
import com.mmg.manahub.core.ui.components.CardRow

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Engine Unification plan (`docs/plans/deck-engine-unification-plan.md` §5 Phase 3) --
//  the entry chooser (3.1) + the two NEW wizard entry flows (3.3 colors-first, 3.4 strategy-first).
//  Flow A (cards-first, 3.2) is CardsFlowDirectionContent in DeckWizardDirectionIdentity.kt --
//  unchanged in shape, only gained a "suggested strategies" section there.
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

// ── Flow B — colors-first (3.3) ───────────────────────────────────────────────

@Composable
internal fun ColorsFlowDirectionContent(
    uiState: DeckWizardUiState,
    onToggleColor: (ManaColor) -> Unit,
    onSelectAffinityEntry: (ColorStrategyEntry) -> Unit,
    onToggleSuggestedSeed: (Card) -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, top = spacing.md, bottom = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item(key = "header") {
                Column {
                    Text(stringResource(R.string.deck_wizard_colors_flow_title), style = ty.titleLarge, color = mc.textPrimary)
                    Text(
                        stringResource(R.string.deck_wizard_colors_flow_subtitle),
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }
            item(key = "color_picker") {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    ManaColor.entries.filter { it != ManaColor.C }.forEach { color ->
                        ColorToggleChip(
                            color = color,
                            selected = color in uiState.colorIdentity,
                            readOnly = false,
                            onClick = { onToggleColor(color) },
                        )
                    }
                }
            }

            if (uiState.colorAffinityEntries.isNotEmpty()) {
                item(key = "affinity_header") {
                    Text(
                        stringResource(R.string.deck_wizard_viable_strategies_title),
                        style = ty.labelLarge,
                        color = mc.primaryAccent,
                    )
                }
                items(
                    uiState.colorAffinityEntries,
                    key = { "affinity_${it.archetype?.name}_${it.themes.joinToString { theme -> theme.name }}" },
                ) { entry ->
                    StrategyOptionRow(
                        label = entry.label,
                        description = entry.description(),
                        selected = entry == uiState.selectedColorAffinityEntry,
                        onClick = { onSelectAffinityEntry(entry) },
                    )
                }
            } else if (uiState.colorIdentity.isEmpty()) {
                item(key = "affinity_empty") {
                    Text(
                        stringResource(R.string.deck_wizard_colors_flow_pick_hint),
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                    )
                }
            } else {
                // Defensive: colors picked but no affinity entries resolved for them yet -- without
                // this branch neither the chip list nor the pick-hint renders, leaving a blank gap.
                item(key = "affinity_no_matches") {
                    Text(
                        stringResource(R.string.deck_wizard_colors_flow_no_matches),
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                    )
                }
            }

            suggestedSeedSection(uiState = uiState, onToggleSuggestedSeed = onToggleSuggestedSeed)
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = uiState.colorIdentity.isNotEmpty(),
            onClick = onNext,
        )
    }
}

// ── Flow C — strategy-first (3.4) ─────────────────────────────────────────────

@Composable
internal fun StrategyFlowDirectionContent(
    uiState: DeckWizardUiState,
    onQueryChange: (String) -> Unit,
    onSelectArchetype: (ArchetypeId) -> Unit,
    onSelectTheme: (ThemeId) -> Unit,
    onSelectCombo: (ColorComboSuggestion) -> Unit,
    onToggleSuggestedSeed: (Card) -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val query = uiState.taxonomyQuery.trim()
    // Deck Analysis Engine v3 removed ArchetypeId.GENERIC -- every entries value is now a real,
    // specialized macro, so no filter is needed (was previously excluding the neutral default).
    val filteredArchetypes = remember(query) {
        ArchetypeId.entries.filter { query.isEmpty() || it.displayName.contains(query, ignoreCase = true) }
    }
    val filteredThemes = remember(query) {
        ThemeId.entries.filter { query.isEmpty() || it.displayName.contains(query, ignoreCase = true) }
    }
    val hasPick = uiState.selectedArchetype != null || uiState.selectedDirectionTheme != null

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, top = spacing.md, bottom = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item(key = "header") {
                Column {
                    Text(stringResource(R.string.deck_wizard_strategy_flow_title), style = ty.titleLarge, color = mc.textPrimary)
                    Text(
                        stringResource(R.string.deck_wizard_strategy_flow_subtitle),
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }
            item(key = "taxonomy_search") {
                OutlinedTextField(
                    value = uiState.taxonomyQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.deck_wizard_strategy_search_hint), style = ty.bodyMedium, color = mc.textDisabled) },
                    shape = CardShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                        focusedTextColor = mc.textPrimary,
                        unfocusedTextColor = mc.textPrimary,
                        cursorColor = mc.primaryAccent,
                    ),
                )
            }

            // Deck Wizard & Engine Rework plan, Workstream 3.3 -- each row's color-combo picker now
            // expands INLINE directly below IT (not in a separate section at the top of the list, the
            // old two-phase pick "B1's dead-chip fix papered over" per the plan). Picking strategy +
            // combo is one continuous interaction: tap the row to select the strategy, its own combo
            // chips appear right there, tap one to finish. uiState.colorComboSuggestions is already
            // scoped to whichever single archetype/theme is currently selected (recomputed by
            // DeckWizardViewModel.recomputeColorComboSuggestions on every pick), so the SAME data
            // renders wherever the currently-selected row happens to be.
            if (filteredArchetypes.isNotEmpty()) {
                item(key = "archetype_header") {
                    Text(stringResource(R.string.deck_wizard_archetypes_title), style = ty.labelLarge, color = mc.primaryAccent)
                }
                items(filteredArchetypes, key = { "archetype_${it.name}" }) { archetype ->
                    val selected = archetype == uiState.selectedArchetype
                    Column {
                        StrategyOptionRow(
                            label = archetype.displayName,
                            description = archetype.description(),
                            selected = selected,
                            onClick = { onSelectArchetype(archetype) },
                        )
                        if (selected) {
                            InlineColorComboSection(
                                suggestions = uiState.colorComboSuggestions,
                                selectedColors = uiState.colorIdentity,
                                onSelectCombo = onSelectCombo,
                            )
                        }
                    }
                }
            }
            if (filteredThemes.isNotEmpty()) {
                item(key = "theme_header") {
                    Text(stringResource(R.string.deck_wizard_themes_title), style = ty.labelLarge, color = mc.primaryAccent)
                }
                items(filteredThemes, key = { "theme_${it.name}" }) { theme ->
                    val selected = theme == uiState.selectedDirectionTheme
                    Column {
                        StrategyOptionRow(
                            label = theme.displayName,
                            description = theme.description(),
                            selected = selected,
                            onClick = { onSelectTheme(theme) },
                        )
                        if (selected) {
                            InlineColorComboSection(
                                suggestions = uiState.colorComboSuggestions,
                                selectedColors = uiState.colorIdentity,
                                onSelectCombo = onSelectCombo,
                            )
                        }
                    }
                }
            }
            if (filteredArchetypes.isEmpty() && filteredThemes.isEmpty()) {
                item(key = "taxonomy_empty") {
                    Text(stringResource(R.string.deck_wizard_strategy_search_empty), style = ty.bodySmall, color = mc.textSecondary)
                }
            }

            suggestedSeedSection(uiState = uiState, onToggleSuggestedSeed = onToggleSuggestedSeed)
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = hasPick,
            onClick = onNext,
        )
    }
}

/** Workstream 3.3 -- the inline color-combo picker rendered directly below whichever
 * [StrategyOptionRow] is currently selected in [StrategyFlowDirectionContent]. Reuses the exact
 * [ColorComboChip] visual [ColorsFlowDirectionContent]/the pre-WS3 top-of-list section already used. */
@Composable
private fun InlineColorComboSection(
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

// ── Shared -- suggested-seed check/uncheck list (Flow B/C) ───────────────────

private fun LazyListScope.suggestedSeedSection(
    uiState: DeckWizardUiState,
    onToggleSuggestedSeed: (Card) -> Unit,
) {
    if (uiState.suggestedSeedCards.isEmpty()) return
    item(key = "suggested_seeds_header") {
        val mc = MaterialTheme.magicColors
        val ty = MaterialTheme.magicTypography
        val spacing = MaterialTheme.spacing
        Column {
            Text(stringResource(R.string.deck_wizard_suggested_seeds_title), style = ty.labelLarge, color = mc.primaryAccent)
            Text(
                stringResource(R.string.deck_wizard_suggested_seeds_subtitle),
                style = ty.bodySmall,
                color = mc.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
    }
    items(uiState.suggestedSeedCards, key = { "suggested_${it.scryfallId}" }) { card ->
        // Every card in suggestedSeedCards comes from RankOwnedCardsForProfileUseCase, which ranks
        // ONLY the user's owned collection -- isInCollection = true always holds here (never guessed).
        val isSelected = uiState.seedCards.any { it.scryfallId == card.scryfallId }
        CardRow(
            card = card,
            isInCollection = true,
            onClick = { onToggleSuggestedSeed(card) },
            // Single-tap toggles either way (onClick); a SELECTED row ALSO gets the shared
            // primaryAccent selected-tint (below) plus a persistent remove (X) affordance -- two
            // reinforcing cues, matching StrategyOptionRow/DirectionChip's selected convention
            // elsewhere in this wizard.
            selected = isSelected,
            onRemove = if (isSelected) { { onToggleSuggestedSeed(card) } } else null,
        )
    }
}

// ── Flow C -- color-combo suggestion chip (real mana-symbol icons, not letters) ──────────────

/** A [ColorComboSuggestion] pick, rendered as real WUBRG mana-symbol icons ([ManaCostImages])
 * instead of the plain `.symbol` letters [DirectionChip] used before -- same pattern
 * `DeckWizardScreen.ReviewColorsRow` already established (`ManaColor.symbol` is a bare letter,
 * wrapped in `{}` only to satisfy [ManaCostImages]' cost-string parser). Same selected color
 * language as [DirectionChip]/`StrategiesTabContent`'s search chips (primaryAccent @ 0.2f fill,
 * no border). Falls back to the existing "Colorless" copy for an empty [colors] set. */
@Composable
private fun ColorComboChip(colors: Set<ManaColor>, selected: Boolean, onClick: () -> Unit) {
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

// ── Shared -- one-per-row strategy option (Flow B viable strategies / Flow C archetypes+themes) ──

/**
 * A richer, one-row-per-strategy replacement for the old bare [DirectionChip] grid: label + a short
 * plain-English explanation of what the strategy means in deckbuilding terms, plus the same
 * check-circle selected indicator [CardRow]-adjacent rows in this file use. Used by Flow B's
 * "Viable strategies" list, Flow C's "Archetypes"/"Themes" lists, and (Workstream 3.1) Flow A's
 * seed-ranked strategy list -- one row shape, four call sites. Internal (not `private`) so
 * [CardsFlowDirectionContent] in `DeckWizardDirectionIdentity.kt` can reuse it.
 *
 * @param misfitContent Workstream 3.1 coherence hints -- optional trailing content rendered below
 *   the description (a "Better without: …" chip row); `null` (every pre-WS3 call site) renders
 *   nothing extra.
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
 * Deck Wizard & Engine Rework plan, Workstream 3.1 -- the "Better without: …" coherence hint row,
 * rendered inline inside a [StrategyOptionRow] (via its `misfitContent` slot) whenever a
 * [com.mmg.manahub.feature.decks.domain.usecase.SeedStrategyCandidate] carries `misfitSeeds` and/or
 * `misfitColors`. Tapping a seed chip removes that seed ([onRemoveSeed]); tapping a color chip
 * deselects that color ([onDeselectColor], a no-op if it happens to be locked -- see
 * [DeckWizardViewModel.onToggleCardsFlowColor]'s own guard). Either action re-ranks the strategy
 * list automatically (both handlers already trigger `recomputeSeedStrategySuggestion` in the VM).
 * Never hides the candidate itself -- this is purely an additive hint below its description.
 */
@Composable
internal fun MisfitHintRow(
    seeds: List<Card>,
    colors: Set<ManaColor>,
    onRemoveSeed: (Card) -> Unit,
    onDeselectColor: (ManaColor) -> Unit,
) {
    if (seeds.isEmpty() && colors.isEmpty()) return
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Column(modifier = Modifier.padding(top = spacing.xs)) {
        Text(stringResource(R.string.deck_wizard_misfit_hint_title), style = ty.labelSmall, color = mc.textSecondary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
            modifier = Modifier.padding(top = spacing.xxs),
        ) {
            seeds.forEach { seed -> MisfitChip(label = seed.name, onClick = { onRemoveSeed(seed) }) }
            colors.forEach { color -> MisfitChip(label = color.displayName, onClick = { onDeselectColor(color) }) }
        }
    }
}

@Composable
private fun MisfitChip(label: String, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(onClick = onClick, shape = ChipShape, color = mc.surfaceVariant) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(label, style = ty.labelSmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.Clear, contentDescription = null, tint = mc.textSecondary, modifier = Modifier.size(14.dp))
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
