package com.mmg.manahub.feature.decks.presentation.improvement.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints
import kotlin.math.roundToInt

/**
 * Selectable budget-filter bar for the ADD tab.
 *
 * Two preset rows (max €/card and max total) of selectable chips plus an "Any" reset per row, and a
 * clear-all action when any cap is set. Stateless — the caller owns [budget] and applies changes via
 * [onBudgetChanged]. Caps are fixed presets (not free text) so the control stays simple and the value
 * never collides with the engine's controlled Scryfall query inputs.
 */
@Composable
fun BudgetFilterBar(
    budget: BudgetConstraints,
    onBudgetChanged: (BudgetConstraints) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        color = mc.backgroundSecondary,
        shape = ChipShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.deck_doctor_budget_title).uppercase(),
                    style = ty.labelMedium,
                    color = mc.primaryAccent,
                    fontWeight = FontWeight.Bold,
                )
                if (!budget.isUnconstrained) {
                    IconButton(
                        onClick = { onBudgetChanged(budget.copy(maxPerCardEur = null, maxTotalEur = null)) },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.deck_doctor_budget_clear),
                            tint = mc.textSecondary,
                        )
                    }
                }
            }

            BudgetCapRow(
                label = stringResource(R.string.deck_doctor_budget_per_card),
                selected = budget.maxPerCardEur,
                presets = PER_CARD_PRESETS,
                onSelect = { onBudgetChanged(budget.copy(maxPerCardEur = it)) },
            )
            BudgetCapRow(
                label = stringResource(R.string.deck_doctor_budget_total),
                selected = budget.maxTotalEur,
                presets = TOTAL_PRESETS,
                onSelect = { onBudgetChanged(budget.copy(maxTotalEur = it)) },
            )
        }
    }
}

/** One labelled row: an "Any" reset chip followed by the numeric preset chips. */
@Composable
private fun BudgetCapRow(
    label: String,
    selected: Double?,
    presets: List<Int>,
    onSelect: (Double?) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs)) {
        Text(text = label, style = ty.labelSmall, color = mc.textSecondary)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) {
            item(key = "any") {
                BudgetChip(
                    text = stringResource(R.string.deck_doctor_budget_any),
                    selected = selected == null,
                    onClick = { onSelect(null) },
                )
            }
            items(presets, key = { it }) { cap ->
                BudgetChip(
                    text = stringResource(R.string.deck_doctor_budget_cap_label, cap),
                    selected = selected != null && selected.roundToInt() == cap,
                    onClick = { onSelect(cap.toDouble()) },
                )
            }
        }
    }
}

/** A single selectable budget chip (≥48dp tall touch target via vertical padding). */
@Composable
private fun BudgetChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val container = if (selected) mc.primaryAccent.copy(alpha = 0.20f) else mc.surface
    val content = if (selected) mc.primaryAccent else mc.textSecondary

    Surface(
        color = container,
        shape = ChipShape,
        modifier = Modifier.selectable(
            selected = selected,
            role = Role.RadioButton,
            onClick = onClick,
        ),
    ) {
        Text(
            text = text,
            style = ty.labelMedium,
            color = content,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.spacing.md,
                vertical = MaterialTheme.spacing.md,
            ),
        )
    }
}

/** Per-card € caps offered as quick presets. */
private val PER_CARD_PRESETS = listOf(1, 2, 5, 10, 20)

/** Total € caps offered as quick presets. */
private val TOTAL_PRESETS = listOf(10, 25, 50, 100, 200)
