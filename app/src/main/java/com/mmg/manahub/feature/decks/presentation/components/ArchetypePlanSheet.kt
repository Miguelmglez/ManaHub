package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ThemeId

/**
 * The Studio Suggestions header chip (Deck Doctor Community/Archetype plan, Phase 1.7): "Deck
 * plan: <Archetype> · <themes>" with a small icon distinguishing a manual pin (person icon) from
 * an auto-detected plan (sparkle icon). Tapping opens [ArchetypePlanSheetContent].
 */
@Composable
fun ArchetypePlanChip(
    macro: ArchetypeId,
    themes: List<ThemeId>,
    isManualOverride: Boolean,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val label = buildString {
        append(macro.displayName)
        if (themes.isNotEmpty()) append(" · ").append(themes.joinToString(", ") { it.displayName })
    }
    Row(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .background(mc.surfaceVariant, ChipShape)
            .clickable(onClickLabel = stringResource(R.string.deck_studio_archetype_chip_action)) { onClick() }
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Icon(
            imageVector = if (isManualOverride) Icons.Default.Person else Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = mc.primaryAccent,
            modifier = Modifier.sizeIn(maxWidth = 18.dp, maxHeight = 18.dp),
        )
        Text(
            text = stringResource(R.string.deck_studio_archetype_chip_prefix, label),
            style = MaterialTheme.magicTypography.labelLarge,
            color = mc.textPrimary,
        )
    }
}

/**
 * Low-confidence hint shown under the chip when no override is set and the classifier is not
 * confident (a non-blocking `EmptyState`-style nudge, never a dialog).
 */
@Composable
fun ArchetypePlanHint(onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    Text(
        text = stringResource(R.string.deck_studio_archetype_low_confidence_hint),
        style = MaterialTheme.magicTypography.bodySmall,
        color = mc.textSecondary,
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .clickable(onClickLabel = stringResource(R.string.deck_studio_archetype_chip_action)) { onClick() }
            .padding(vertical = MaterialTheme.spacing.xs),
    )
}

/**
 * The archetype/theme picker sheet content: single-select macro archetype + multi-select themes
 * (capped at 2, enforced here) + "Auto-detect" (clears the pin) + "Apply".
 *
 * Stateless: the caller ([DeckStudioScreen]) owns the sheet's open/closed state; this composable
 * only holds its own local selection draft (reset each time it is recomposed with new initial
 * values, since the sheet is freshly composed on every open).
 */
@Composable
fun ArchetypePlanSheetContent(
    initialMacro: ArchetypeId,
    initialThemes: List<ThemeId>,
    onApply: (ArchetypeId, List<ThemeId>) -> Unit,
    onAutoDetect: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    var selectedMacro by remember { mutableStateOf(initialMacro) }
    var selectedThemes by remember { mutableStateOf(initialThemes) }

    Column(Modifier.padding(spacing.lg)) {
        Text(
            text = stringResource(R.string.deck_studio_archetype_sheet_title),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        Box(Modifier.padding(top = spacing.sm, bottom = spacing.xs)) {
            Text(
                text = stringResource(R.string.deck_studio_archetype_sheet_macro_label),
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.textSecondary,
            )
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            items(ArchetypeId.entries.toList()) { archetype ->
                SelectableChip(
                    label = archetype.displayName,
                    selected = selectedMacro == archetype,
                    onClick = { selectedMacro = archetype },
                )
            }
        }
        Box(Modifier.padding(top = spacing.md, bottom = spacing.xs)) {
            Text(
                text = stringResource(R.string.deck_studio_archetype_sheet_themes_label),
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.textSecondary,
            )
        }
        LazyColumn(
            modifier = Modifier.sizeIn(maxHeight = 260.dp),
            contentPadding = PaddingValues(vertical = spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            items(ThemeId.entries.toList()) { theme ->
                val isSelected = theme in selectedThemes
                // Max-2 enforcement: a 3rd tap is a no-op unless it deselects one already picked.
                val disabled = !isSelected && selectedThemes.size >= MAX_THEMES
                ThemeRow(
                    label = theme.displayName,
                    selected = isSelected,
                    enabled = !disabled,
                    onToggle = {
                        selectedThemes = if (isSelected) selectedThemes - theme
                        else if (selectedThemes.size < MAX_THEMES) selectedThemes + theme
                        else selectedThemes
                    },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            OutlinedButton(
                onClick = {
                    onAutoDetect()
                    onDismiss()
                },
                modifier = Modifier.sizeIn(minHeight = 48.dp),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                Box(Modifier.padding(start = spacing.xs)) {
                    Text(stringResource(R.string.deck_studio_archetype_auto_detect))
                }
            }
            Button(
                onClick = {
                    onApply(selectedMacro, selectedThemes)
                    onDismiss()
                },
                modifier = Modifier.sizeIn(minHeight = 48.dp),
            ) {
                Text(stringResource(R.string.deck_studio_archetype_apply))
            }
        }
    }
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val background = if (selected) mc.primaryAccent else mc.surfaceVariant
    val contentColor = if (selected) mc.onAccent else mc.textPrimary
    Row(
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .background(background, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.magicTypography.labelLarge, color = contentColor)
    }
}

@Composable
private fun ThemeRow(label: String, selected: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val contentColor = if (enabled) mc.textPrimary else mc.textDisabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 48.dp)
            .clickable(enabled = enabled) { onToggle() }
            .padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = mc.primaryAccent)
        } else {
            Box(Modifier.sizeIn(minWidth = 24.dp, minHeight = 24.dp))
        }
        Text(text = label, style = MaterialTheme.magicTypography.bodyMedium, color = contentColor)
    }
}

private const val MAX_THEMES = 2
