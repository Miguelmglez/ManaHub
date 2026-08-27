package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.ui.components.MagicSelectionItem
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeFormat
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategy
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategyCatalog
import com.mmg.manahub.feature.decks.domain.engine.ResolvedStrategyInfo
import com.mmg.manahub.feature.decks.domain.engine.availableIn

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Analysis Engine v2 Phase 3 (plan §3.4 item 1) — the curated-strategy picker that
//  REPLACES ArchetypePlanSheet's usage in Deck Studio (D2: "a single flat list of curated
//  strategies", plan §3.2).
//
//  Naming note (deviation, documented): this file's `CuratedStrategyPickerSheet` is
//  DELIBERATELY NOT named `StrategyPickerSheet` — that name is already taken by an UNRELATED,
//  already-live component (`StrategyPickerSheet.kt`, Deck Wizard & Engine Rework plan WS1.2's
//  shared 3-axis archetype/theme/tribe picker, still mounted by `DeckWizardCommanderSteps
//  .StrategyStepContent`). The two pickers solve different problems (this one: pick ONE curated,
//  pre-validated strategy from a flat list; that one: freely compose archetype + up to 2 themes +
//  tribe from a restricted candidate set) and must stay separate composables — reusing the name
//  here would either collide at the same package/file scope or silently shadow the wizard's own
//  picker. [TribeOption] (declared in `StrategyPickerSheet.kt`) IS reused as-is below: it is a
//  plain, picker-agnostic (key, label) shape, not specific to that file's 3-axis flow.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The Studio "Analysis" tab header chip (plan §3.4 item 2): "Plan: <Strategy> (detected|manual)".
 * Tapping opens [CuratedStrategyPickerSheet]. Reads the v2 [ResolvedStrategyInfo] directly
 * (unlike the retired `ArchetypePlanChip`, which read the legacy `ArchetypeResolution`).
 */
@Composable
fun StrategyPlanChip(
    strategy: ResolvedStrategyInfo,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = mc.backgroundSecondary,
        border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier.background(
                Brush.verticalGradient(
                    listOf(mc.primaryAccent.copy(alpha = 0.12f), mc.backgroundSecondary)
                )
            )
        ) {
            Row(
                modifier = Modifier
                    .padding(spacing.md)
                    .heightIn(min = 60.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(mc.primaryAccent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (strategy.isManualOverride) Icons.Default.Person else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DECK STRATEGY",
                        style = ty.labelSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp),
                        color = mc.primaryAccent,
                    )
                    Text(
                        text = strategy.displayName,
                        style = ty.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (strategy.isManualOverride) mc.goldMtg.copy(alpha = 0.15f) else mc.lifePositive.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = (if (strategy.isManualOverride) "MANUAL" else "AUTO").uppercase(),
                        style = ty.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (strategy.isManualOverride) mc.goldMtg else mc.lifePositive,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/** Low-confidence hint shown under [StrategyPlanChip] when no override is set and the classifier
 * is not confident — mirrors the retired `ArchetypePlanHint` (same copy/behavior, v2 call site). */
@Composable
fun StrategyPlanHint(onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        onClick = onClick,
        shape = ChipShape,
        color = mc.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, mc.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = mc.textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(R.string.deck_studio_archetype_low_confidence_hint),
                style = ty.labelMedium,
                color = mc.textSecondary,
            )
        }
    }
}

/**
 * The curated-strategy picker sheet content (plan §3.4 item 1): a flat, searchable, grouped list
 * ("Core plans" / "Build-around themes") sourced straight from [CuratedStrategyCatalog.ALL],
 * filtered to strategies offered for [currentFormat]. Tapping a non-tribal strategy applies it
 * immediately; a [CuratedStrategy.requiresTribe] strategy instead reveals an inline tribe
 * sub-picker (from [availableTribes]) and applies once a tribe is chosen. "Auto-detect" clears the
 * pin and re-infers.
 *
 * Stateless per this codebase's convention: [selectedStrategyId] is hoisted (the caller reads it
 * off the live [DeckHealth][com.mmg.manahub.feature.decks.domain.usecase.DeckHealth]
 * `.analysis.strategy.curatedStrategyId`); every mutation is a callback.
 *
 * @param currentFormat the live deck's format — strategies not offered for it ([CuratedStrategy
 *        .availableIn], Wave 2 B1's `DeckFormat`-granular availability; e.g. Voltron/Group Hug/
 *        Group Slug/Clones & Theft are Commander-only, and only a curated subset is offered for
 *        Standard) are filtered out. A format with no [ArchetypeFormat] mapping (Draft) is
 *        treated as "no format restriction" (every entry shown) — Draft has no archetype skeleton
 *        at all ([ArchetypeFormat.of]'s own KDoc), so a strategy pin there is inert either way.
 * @param availableTribes tribe candidates for a `requiresTribe` strategy's sub-picker (see
 *        [TribeOption]); the caller derives these (Deck Studio: the live deck's own
 *        `DeckProfile.tagFingerprint` `tribe:` keys — see `DeckStudioScreen`'s call site).
 * @param onApply the chosen strategy plus its tribe pick (`null` unless `requiresTribe`).
 * @param currentStrategyName the live deck's currently RESOLVED strategy display name (manual pin
 *        or auto-detected), shown as an "Currently: X" hint under the Auto-detect row (review fix
 *        P1 #4) — `null` while no strategy has resolved yet. Distinct from [selectedStrategyId]:
 *        that one is a CURATED-catalog id only (`null` for a non-curated archetype match), while
 *        this is the resolved [com.mmg.manahub.feature.decks.domain.engine.ResolvedStrategyInfo
 *        .displayName] the caller already has in scope, curated or not.
 */
@Composable
fun CuratedStrategyPickerSheet(
    currentFormat: DeckFormat,
    selectedStrategyId: String?,
    availableTribes: List<TribeOption>,
    onApply: (CuratedStrategy, String?) -> Unit,
    onAutoDetect: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    currentStrategyName: String? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    var query by remember { mutableStateOf("") }
    var tribePending by remember { mutableStateOf<CuratedStrategy?>(null) }

    // B1: filters via CuratedStrategy.availableIn(currentFormat) — DeckFormat-granular (Standard
    // now sees only its curated subset; Draft keeps the pre-B1 "no restriction" passthrough).
    val filtered = remember(query, currentFormat) {
        CuratedStrategyCatalog.ALL.filter { entry ->
            entry.availableIn(currentFormat) &&
                (query.isBlank() || entry.displayName.contains(query, ignoreCase = true) || entry.description.contains(query, ignoreCase = true))
        }
    }
    val corePlans = filtered.filter { it.themes.isEmpty() }
    val themedPresets = filtered.filter { it.themes.isNotEmpty() }

    Column(modifier.fillMaxSize().padding(horizontal = spacing.lg, vertical = spacing.md)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = mc.textSecondary
                )
            }
            Text(
                text = stringResource(R.string.deck_studio_archetype_sheet_title),
                style = ty.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                color = mc.textPrimary,
            )
        }
        Text(
            text = stringResource(R.string.deck_studio_archetype_sheet_subtitle),
            style = ty.bodySmall,
            color = mc.textSecondary,
        )
        Spacer(Modifier.height(spacing.md))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.deck_curated_strategy_picker_search_placeholder), style = ty.bodyMedium) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = mc.primaryAccent,
                unfocusedBorderColor = mc.surfaceVariant,
                focusedContainerColor = mc.backgroundSecondary,
                unfocusedContainerColor = mc.backgroundSecondary,
            ),
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { query = "" }, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.deck_studio_inspirations_search_clear),
                            tint = mc.textSecondary,
                        )
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            shape = ChipShape,
        )
        Spacer(Modifier.height(spacing.md))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            val pending = tribePending
            if (pending != null) {
                item(key = "tribe_prompt") {
                    TribePickerSection(
                        strategy = pending,
                        availableTribes = availableTribes,
                        onSelectTribe = { tribeKey ->
                            onApply(pending, tribeKey)
                            onDismiss()
                        },
                        onCancel = { tribePending = null },
                    )
                }
            } else {
                item(key = "auto_detect") {
                    val desc = if (currentStrategyName != null) {
                        stringResource(R.string.deck_curated_strategy_picker_auto_detect_hint_current, currentStrategyName)
                    } else {
                        stringResource(R.string.deck_curated_strategy_picker_auto_detect_hint_manual)
                    }
                    MagicSelectionItem(
                        title = stringResource(R.string.deck_studio_archetype_auto_detect),
                        description = desc,
                        isSelected = selectedStrategyId == null,
                        accentColor = mc.primaryAccent,
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(mc.primaryAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = mc.primaryAccent)
                            }
                        },
                        onClick = { onAutoDetect(); onDismiss() }
                    )
                }
                if (corePlans.isNotEmpty()) {
                    item(key = "core_plans_header") {
                        PickerSectionHeader(stringResource(R.string.deck_curated_strategy_picker_core_plans_header))
                    }
                    items(corePlans, key = { "core_${it.id}" }) { entry ->
                        MagicSelectionItem(
                            title = entry.displayName,
                            description = entry.description,
                            isSelected = entry.id == selectedStrategyId,
                            accentColor = mc.primaryAccent,
                            onClick = {
                                if (entry.requiresTribe) tribePending = entry
                                else { onApply(entry, null); onDismiss() }
                            }
                        )
                    }
                }
                if (themedPresets.isNotEmpty()) {
                    item(key = "themed_header") {
                        PickerSectionHeader(stringResource(R.string.deck_curated_strategy_picker_themed_header))
                    }
                    items(themedPresets, key = { "themed_${it.id}" }) { entry ->
                        MagicSelectionItem(
                            title = entry.displayName,
                            description = entry.description,
                            isSelected = entry.id == selectedStrategyId,
                            accentColor = mc.primaryAccent,
                            onClick = {
                                if (entry.requiresTribe) tribePending = entry
                                else { onApply(entry, null); onDismiss() }
                            }
                        )
                    }
                }
                if (filtered.isEmpty()) {
                    item(key = "no_results") {
                        Text(
                            text = stringResource(R.string.deck_curated_strategy_picker_no_results),
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.magicTypography.labelMedium,
        color = MaterialTheme.magicColors.primaryAccent,
        modifier = Modifier.padding(top = MaterialTheme.spacing.sm, bottom = MaterialTheme.spacing.xxs),
    )
}

// AutoDetectRow and CuratedStrategyRow were replaced by MagicSelectionItem

/** The inline "now pick a tribe for <Strategy>" step shown after tapping a `requiresTribe`
 * strategy (plan §3.4 item 1's "tribe sub-picker shown when the selected entry has
 * `requiresTribe=true`") — replaces the flat list until a tribe is chosen or the user backs out. */
@Composable
private fun TribePickerSection(
    strategy: CuratedStrategy,
    availableTribes: List<TribeOption>,
    onSelectTribe: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = stringResource(R.string.deck_curated_strategy_picker_tribe_prompt, strategy.displayName),
            style = ty.titleMedium,
            color = mc.textPrimary,
        )
        if (availableTribes.isEmpty()) {
            Text(
                text = stringResource(R.string.deck_strategy_picker_tribe_empty),
                style = ty.bodySmall,
                color = mc.textSecondary,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                availableTribes.forEach { option ->
                    Surface(
                        onClick = { onSelectTribe(option.key) },
                        shape = ChipShape,
                        color = mc.surface,
                        border = BorderStroke(1.dp, mc.surfaceVariant),
                    ) {
                        Row(
                            modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically).padding(horizontal = spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(option.label, style = ty.labelMedium, color = mc.textPrimary)
                        }
                    }
                }
            }
        }
        Text(
            text = stringResource(R.string.deck_curated_strategy_picker_tribe_cancel),
            style = ty.labelLarge,
            color = mc.textSecondary,
            modifier = Modifier
                .sizeIn(minHeight = 48.dp)
                .clickable(onClick = onCancel)
                .padding(vertical = spacing.xs),
        )
    }
}
