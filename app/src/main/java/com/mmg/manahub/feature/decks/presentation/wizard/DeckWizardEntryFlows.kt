package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-21

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ManaColor

// ── Entry chooser ─────────────────────────────────────────────────────────────

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
 * A mana-symbol color toggle chip (48dp circle) -- every [ManaColor] incl. the exclusive Colorless
 * chip renders its real `{X}` symbol via [ManaSymbolImage].
 *
 * @param lockedDescriptionRes the accessibility copy for the [readOnly] (locked) state differs by
 *   WHY the color is locked: Commander's identity ("fixed by commander") vs. a seed-locked color
 *   ([DeckWizardUiState.lockedColors]).
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
    // Read-only: a plain Surface with no `onClick` overload so it never shows an interactive ripple.
    if (readOnly) {
        val fixedColorDescription = stringResource(lockedDescriptionRes, color.displayName)
        Surface(
            shape = CircleShape,
            color = background,
            border = border,
            modifier = Modifier.size(48.dp).semantics { contentDescription = fixedColorDescription },
            content = content,
        )
    } else {
        val colorDescription = color.displayName
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = background,
            border = border,
            modifier = Modifier.size(48.dp).semantics {
                contentDescription = colorDescription
                this.selected = selected
                role = Role.Checkbox
            },
            content = content,
        )
    }
}
