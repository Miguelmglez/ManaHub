package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.MagicTheme
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.StrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.StrategyPickerLogic
import com.mmg.manahub.feature.decks.domain.engine.StrategyPickerSelection
import com.mmg.manahub.feature.decks.domain.engine.ThemeId

/** A single tribe candidate the picker can offer on its "Tribe" axis (WS 1.2) -- sourced by the
 * CALLER from [com.mmg.manahub.feature.decks.domain.engine.TribeDeriver] signals (e.g. a
 * commander's payoff tribes, or the collection's dominant creature types). [key] is the raw
 * `tribe:<subtype>` runtime key ([com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
 * .TRIBE_PREFIX]-prefixed); [label] is the already-pluralized, display-ready English name (e.g.
 * "Elves"). This component never derives tribe candidates itself -- it only renders what it's
 * given, matching the plan's "sourced from TribeDeriver signals" (the caller's responsibility). */
data class TribeOption(val key: String, val label: String)

/**
 * Deck Wizard & Engine Rework plan (`docs/plans/deck-wizard-rework-plan.md`), Workstream 1.2 --
 * the ONE shared 3-axis strategy picker: "Game plan" (archetype, single-select) -> "Strategy"
 * (themes, up to [StrategyCatalog.MAX_THEMES], filtered-by-compatibility) -> "Tribe" (only when
 * [ThemeId.TRIBAL] is selected). Every item shows its [StrategyCatalog] description (decision D-B:
 * Casual Flow A requires a REAL strategy pick with a full explanation, no silent guessing).
 * Incompatible theme options render DISABLED with a reason, never hidden (WS 1.2's own
 * discoverability requirement) -- a player should be able to see why a combination doesn't fit,
 * not wonder where an option went.
 *
 * This composable is intentionally standalone in this pass (WS 1) -- it is NOT yet wired into the
 * wizard's Direction/strategy steps (WS 2/3) or the Deck Doctor's `ArchetypePlanSheet` (WS 8);
 * those workstreams will mount it as their strategy-picking surface later. Stateless per this
 * codebase's convention: [selection] is fully hoisted, every mutation is a callback routed through
 * [com.mmg.manahub.feature.decks.domain.engine.StrategyPickerLogic] by the caller.
 *
 * @param selection the current 3-axis pick.
 * @param availableTribes tribe candidates for the "Tribe" axis (see [TribeOption]); ignored/unused
 *   while [StrategyPickerSelection.requiresTribe] is false (the section doesn't render).
 * @param onSelectArchetype tap on a Game-plan row -- `null` when tapping the already-selected
 *   archetype again (deselect back to the neutral/unpinned state).
 * @param onToggleTheme tap on a Strategy row -- adds/removes that theme (caller should route this
 *   straight to [StrategyPickerLogic.toggleTheme] against the hoisted [selection]).
 * @param onSelectTribe tap on a Tribe chip -- `null` when tapping the already-selected tribe again.
 * @param availableArchetypes Deck Wizard & Engine Rework plan, Workstream 2.2 -- when non-null,
 *   restricts the "Game plan" axis to exactly these archetypes PLUS [ArchetypeId.GENERIC] (the
 *   picker itself always unions GENERIC into a restricted list -- the Commander-only "Balanced"
 *   escape hatch, D-B/plan 2.2; a caller that wants NO escape hatch, e.g. a future Casual Flow A
 *   caller under D-B's "no GENERIC escape" rule, should pass `null` here and gate the restriction
 *   some other way, since this parameter's contract is specifically "restricted list + GENERIC").
 *   `null` (the default) preserves the original full-catalog behavior -- every existing caller/
 *   preview is unaffected.
 * @param availableThemes same restriction for the "Strategy" (theme) axis -- `null` shows the full
 *   22-theme catalog (existing behavior); non-null shows only the given themes, in [ThemeId.entries]
 *   order. No implicit union here (unlike archetypes) -- there is no theme-level escape hatch.
 */
@Composable
fun StrategyPickerSheet(
    selection: StrategyPickerSelection,
    availableTribes: List<TribeOption>,
    onSelectArchetype: (ArchetypeId?) -> Unit,
    onToggleTheme: (ThemeId) -> Unit,
    onSelectTribe: (String?) -> Unit,
    modifier: Modifier = Modifier,
    availableArchetypes: Set<ArchetypeId>? = null,
    availableThemes: Set<ThemeId>? = null,
) {
    val spacing = MaterialTheme.spacing
    // Deck Analysis Engine v3: ArchetypeId.GENERIC no longer exists (removed from the enum) -- the
    // old "always allow GENERIC ('Balanced') as a row" union is gone; the picker's "clear pin"
    // interaction is still available via toggling the currently-selected row off (see the onClick
    // below), it just no longer renders as its own dedicated list row. A dedicated "Balanced/no
    // pin" row is real, flagged follow-up UI debt (out of Phase 3a's scope).
    val archetypeItems = if (availableArchetypes != null) {
        ArchetypeId.entries.filter { it in availableArchetypes }
    } else {
        ArchetypeId.entries
    }
    val themeItems = if (availableThemes != null) {
        ThemeId.entries.filter { it in availableThemes }
    } else {
        ThemeId.entries
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item(key = "game_plan_header") {
            SectionHeader(title = stringResource(R.string.deck_strategy_picker_game_plan_title))
        }
        items(archetypeItems, key = { "archetype_${it.name}" }) { archetype ->
            StrategyOptionRow(
                title = archetype.displayName,
                description = StrategyCatalog.description(archetype),
                selected = selection.archetype == archetype,
                enabled = true,
                disabledReason = null,
                onClick = {
                    onSelectArchetype(if (selection.archetype == archetype) null else archetype)
                },
            )
        }

        item(key = "strategy_header") {
            SectionHeader(
                title = stringResource(R.string.deck_strategy_picker_strategy_title),
                subtitle = stringResource(R.string.deck_strategy_picker_strategy_subtitle),
            )
        }
        items(themeItems, key = { "theme_${it.name}" }) { theme ->
            val selectable = StrategyPickerLogic.isThemeSelectable(selection, theme)
            val isSelected = theme in selection.themes
            val atCap = selection.themes.size >= StrategyCatalog.MAX_THEMES && !isSelected
            StrategyOptionRow(
                title = theme.displayName,
                description = StrategyCatalog.description(theme),
                selected = isSelected,
                enabled = isSelected || (selectable && !atCap),
                disabledReason = when {
                    isSelected -> null
                    !selectable -> selection.archetype
                        ?.let { stringResource(R.string.deck_strategy_picker_incompatible_reason, it.displayName) }
                    atCap -> stringResource(R.string.deck_strategy_picker_max_themes_reason)
                    else -> null
                },
                onClick = { onToggleTheme(theme) },
            )
        }

        if (selection.requiresTribe) {
            item(key = "tribe_header") {
                SectionHeader(
                    title = stringResource(R.string.deck_strategy_picker_tribe_title),
                    subtitle = stringResource(R.string.deck_strategy_picker_tribe_subtitle),
                )
            }
            item(key = "tribe_chips") {
                TribeChipRow(
                    availableTribes = availableTribes,
                    selectedTribe = selection.tribe,
                    onSelectTribe = onSelectTribe,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Column {
        Text(text = title.uppercase(), style = ty.labelMedium, color = mc.primaryAccent)
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = ty.bodySmall,
                color = mc.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
    }
}

/**
 * One row shared by BOTH the Game-plan and Strategy axes -- name + [StrategyCatalog] description,
 * a checkmark when [selected], and a visibly disabled (dimmed, non-clickable) state with an
 * explicit [disabledReason] caption when the option can't be picked right now (WS 1.2: "incompatible
 * options render disabled with a reason, never hidden").
 */
@Composable
private fun StrategyOptionRow(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    disabledReason: String?,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val titleColor = when {
        !enabled -> mc.textDisabled
        selected -> mc.primaryAccent
        else -> mc.textPrimary
    }
    val descriptionColor = if (enabled) mc.textSecondary else mc.textDisabled

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CardShape,
        color = if (selected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface,
        border = if (selected) BorderStroke(1.dp, mc.primaryAccent) else BorderStroke(0.5.dp, mc.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(spacing.md)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(title, style = ty.bodyLarge, color = titleColor)
                Text(description, style = ty.bodySmall, color = descriptionColor)
                if (!enabled && disabledReason != null) {
                    Text(disabledReason, style = ty.labelSmall, color = mc.textDisabled)
                }
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = mc.primaryAccent)
            }
        }
    }
}

/** The "Tribe" axis: a wrapping row of chips, one per [TribeOption]. Mirrors
 * `DirectionChip`'s (`DeckWizardDirectionIdentity.kt`) visual language and 48dp touch-target floor
 * so the picker's third axis looks consistent with the rest of the wizard's chip precedent. */
@Composable
private fun TribeChipRow(
    availableTribes: List<TribeOption>,
    selectedTribe: String?,
    onSelectTribe: (String?) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    if (availableTribes.isEmpty()) {
        val mc = MaterialTheme.magicColors
        val ty = MaterialTheme.magicTypography
        Text(
            text = stringResource(R.string.deck_strategy_picker_tribe_empty),
            style = ty.bodySmall,
            color = mc.textSecondary,
        )
        return
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        availableTribes.forEach { option ->
            TribeChip(
                option = option,
                selected = option.key == selectedTribe,
                onClick = { onSelectTribe(if (option.key == selectedTribe) null else option.key) },
            )
        }
    }
}

@Composable
private fun TribeChip(option: TribeOption, selected: Boolean, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        onClick = onClick,
        shape = ChipShape,
        color = if (selected) mc.primaryAccent.copy(alpha = 0.2f) else mc.surface,
        border = BorderStroke(1.dp, if (selected) mc.primaryAccent else mc.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Text(text = option.label, style = ty.labelMedium, color = if (selected) mc.primaryAccent else mc.textSecondary)
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = mc.primaryAccent, modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StrategyPickerSheetPreview() {
    MagicTheme {
        StrategyPickerSheet(
            selection = StrategyPickerSelection(
                archetype = ArchetypeId.MIDRANGE,
                themes = listOf(ThemeId.TRIBAL),
                tribe = "tribe:elf",
            ),
            availableTribes = listOf(
                TribeOption("tribe:elf", "Elves"),
                TribeOption("tribe:goblin", "Goblins"),
                TribeOption("tribe:zombie", "Zombies"),
            ),
            onSelectArchetype = {},
            onToggleTheme = {},
            onSelectTribe = {},
        )
    }
}
