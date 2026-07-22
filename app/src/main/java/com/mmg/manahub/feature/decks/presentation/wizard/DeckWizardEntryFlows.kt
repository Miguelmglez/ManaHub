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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ColorStrategyEntry
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ThemeId

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
                item(key = "affinity_chips") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        uiState.colorAffinityEntries.forEach { entry ->
                            DirectionChip(
                                label = entry.label,
                                selected = entry == uiState.selectedColorAffinityEntry,
                                onClick = { onSelectAffinityEntry(entry) },
                            )
                        }
                    }
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
    val filteredArchetypes = remember(query) {
        ArchetypeId.entries.filter { it != ArchetypeId.GENERIC && (query.isEmpty() || it.displayName.contains(query, ignoreCase = true)) }
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

            if (filteredArchetypes.isNotEmpty()) {
                item(key = "archetype_header") {
                    Text(stringResource(R.string.deck_wizard_archetypes_title), style = ty.labelLarge, color = mc.primaryAccent)
                }
                item(key = "archetype_chips") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        filteredArchetypes.forEach { archetype ->
                            DirectionChip(
                                label = archetype.displayName,
                                selected = archetype == uiState.selectedArchetype,
                                onClick = { onSelectArchetype(archetype) },
                            )
                        }
                    }
                }
            }
            if (filteredThemes.isNotEmpty()) {
                item(key = "theme_header") {
                    Text(stringResource(R.string.deck_wizard_themes_title), style = ty.labelLarge, color = mc.primaryAccent)
                }
                item(key = "theme_chips") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        filteredThemes.forEach { theme ->
                            DirectionChip(
                                label = theme.displayName,
                                selected = theme == uiState.selectedDirectionTheme,
                                onClick = { onSelectTheme(theme) },
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

            if (hasPick) {
                item(key = "combo_header") {
                    Text(stringResource(R.string.deck_wizard_color_combos_title), style = ty.labelLarge, color = mc.primaryAccent)
                }
                if (uiState.colorComboSuggestions.isEmpty()) {
                    item(key = "combo_empty") {
                        Text(stringResource(R.string.deck_wizard_color_combos_empty), style = ty.bodySmall, color = mc.textSecondary)
                    }
                } else {
                    item(key = "combo_chips") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            uiState.colorComboSuggestions.forEach { combo ->
                                DirectionChip(
                                    label = combo.colors.joinToString(" ") { it.symbol }.ifEmpty { stringResource(R.string.deck_seeds_identity_colorless) },
                                    selected = combo.colors == uiState.colorIdentity,
                                    onClick = { onSelectCombo(combo) },
                                )
                            }
                        }
                    }
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
        val isSelected = uiState.seedCards.any { it.scryfallId == card.scryfallId }
        SuggestedSeedRow(card = card, isSelected = isSelected, onToggle = { onToggleSuggestedSeed(card) })
    }
}

@Composable
private fun SuggestedSeedRow(card: Card, isSelected: Boolean, onToggle: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        onClick = onToggle,
        shape = CardShape,
        color = if (isSelected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface,
        border = if (isSelected) BorderStroke(1.dp, mc.primaryAccent) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacing.sm).heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
        ) {
            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(width = 52.dp, height = 38.dp).clip(ChipShape),
            )
            CardName(
                name = card.name, style = ty.bodyMedium, color = mc.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Surface(
                shape = CircleShape,
                color = if (isSelected) mc.primaryAccent else mc.surfaceVariant,
                modifier = Modifier.size(24.dp),
            ) {
                if (isSelected) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = mc.onAccent, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
