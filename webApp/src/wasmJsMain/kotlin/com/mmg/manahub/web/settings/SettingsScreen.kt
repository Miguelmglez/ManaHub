package com.mmg.manahub.web.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mmg.manahub.core.model.CardLanguage
import com.mmg.manahub.core.model.CollectionGroupingMode
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicFilterChip
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Settings -- new web MVP screen (web scope expansion approved 2026-08-04). A destination you
 * navigate INTO from [com.mmg.manahub.web.auth.AuthScreen]'s "Settings" row, deliberately NOT a
 * top-level nav item -- the nav rail/bottom bar already carries 6 items and this expansion wave is
 * expected to add Profile and Add Card too, so a 7th-9th tab was rejected up front in favor of the
 * same UX precedent Android itself follows: `feature/settings/presentation/SettingsScreen.kt` hangs
 * off the account/profile surface, not the primary bottom bar.
 *
 * Pure UI over the ALREADY-COMPLETE web data layer -- see [SettingsViewModel]'s KDoc for exactly
 * which of [com.mmg.manahub.core.domain.repository.UserPreferencesRepository]'s 9 setters are
 * exposed here and why the other 5 deliberately are not (dead-UI avoidance for a single-value/
 * not-yet-built-on-web feature, or "not a settings toggle" per CLAUDE.md).
 *
 * Form-style (no card grid), so unlike most other real `:webApp` screens this does not take a
 * [com.mmg.manahub.core.ui.layout.ManaWindowSizeClass] param -- matches
 * [com.mmg.manahub.web.auth.AuthScreen]'s own precedent (a scrolling `Column`, naturally responsive
 * without a breakpoint-driven layout switch).
 */
@Composable
fun SettingsScreen() {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<SettingsViewModel>()
    val preferences by viewModel.preferences.collectAsState()
    val groupingMode by viewModel.collectionGroupingMode.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        Text(
            text = "Settings",
            style = typography.titleLarge,
            color = colors.textPrimary,
        )

        SettingsChoiceSection(
            title = "Card search language",
            subtitle = "Preferred language for card data. English is always available as a fallback.",
            options = CardLanguage.entries,
            selected = preferences.cardLanguage,
            optionLabel = { it.displayName },
            onSelect = viewModel::setCardLanguage,
        )

        HorizontalDivider(color = colors.surfaceVariant.copy(alpha = 0.5f))
        SettingsChoiceSection(
            title = "Preferred currency",
            subtitle = "Currency shown first on card prices.",
            options = PreferredCurrency.entries,
            selected = preferences.preferredCurrency,
            optionLabel = { it.displayName },
            onSelect = viewModel::setPreferredCurrency,
        )

        HorizontalDivider(color = colors.surfaceVariant.copy(alpha = 0.5f))
        SettingsChoiceSection(
            title = "Collection view",
            subtitle = "How the Collection screen lays out your cards.",
            options = CollectionViewMode.entries,
            selected = preferences.collectionViewMode,
            optionLabel = CollectionViewMode::toDisplayLabel,
            onSelect = viewModel::setCollectionViewMode,
        )

        HorizontalDivider(color = colors.surfaceVariant.copy(alpha = 0.5f))
        SettingsChoiceSection(
            title = "Group collection by",
            subtitle = "How the Collection screen buckets your cards into sections.",
            options = CollectionGroupingMode.entries,
            selected = groupingMode,
            optionLabel = CollectionGroupingMode::toDisplayLabel,
            onSelect = viewModel::setCollectionGroupingMode,
        )
    }
}

private fun CollectionViewMode.toDisplayLabel(): String = when (this) {
    CollectionViewMode.GRID -> "Grid"
    CollectionViewMode.LIST -> "List"
}

private fun CollectionGroupingMode.toDisplayLabel(): String = when (this) {
    CollectionGroupingMode.NONE -> "None"
    CollectionGroupingMode.TYPE -> "Type"
    CollectionGroupingMode.COLOR -> "Color"
    CollectionGroupingMode.CMC -> "Mana value"
    CollectionGroupingMode.SET -> "Set"
    CollectionGroupingMode.RARITY -> "Rarity"
    CollectionGroupingMode.TAG -> "Tag"
}

/**
 * A titled, subtitled [FlowRow] of [FilterChip]s -- one reusable single-select control shape for
 * every preference on this screen (2, 2, 7, or 11 options all wrap cleanly at any viewport width,
 * unlike a fixed unwrapped [androidx.compose.foundation.layout.Row], which is why this uses
 * `FlowRow` rather than [com.mmg.manahub.web.deckeditor.DeckEditorScreen]'s fixed-3-chip
 * `horizontalScroll` `Row` precedent -- that screen's chip COUNT is fixed at 3 and known to fit;
 * this screen's [CardLanguage] section has 11).
 */
@Composable
private fun <T> SettingsChoiceSection(
    title: String,
    subtitle: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = title, style = typography.titleMedium, color = colors.textPrimary)
        Text(text = subtitle, style = typography.bodySmall, color = colors.textSecondary)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            options.forEach { option ->
                MagicFilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = optionLabel(option),
                )
            }
        }
    }
}
