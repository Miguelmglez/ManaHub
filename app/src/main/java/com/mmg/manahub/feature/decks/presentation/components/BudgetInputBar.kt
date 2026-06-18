package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * Free-text budget input bar for the Deck Studio Suggestions surface.
 *
 * The free-text counterpart to the preset-chip `BudgetFilterBar`: two numeric
 * [OutlinedTextField]s (per-card € and total €) plus an "owned cards are free"
 * toggle, with an inline error slot shown when [hasError] is true.
 *
 * This composable is **purely stateless and presentational**: it surfaces the raw
 * text the caller holds in UI state and forwards every change verbatim via
 * [onPerCardChange] / [onTotalChange] / [onOwnedFreeChange]. It NEVER parses the
 * text into numbers and NEVER constructs a
 * [com.mmg.manahub.feature.decks.domain.usecase.BudgetConstraints] — that parse
 * (and its `IllegalArgumentException` guard) lives in the ViewModel, so an invalid
 * amount can never crash the UI nor build an invalid constraints object.
 *
 * @param perCardText raw per-card € text (may be blank or invalid mid-typing).
 * @param totalText raw total € text (may be blank or invalid mid-typing).
 * @param ownedCardsAreFree whether owned cards are treated as 0 €.
 * @param hasError true when the current text failed to parse into valid constraints;
 *        renders the inline [R.string.deck_studio_budget_invalid_error] hint.
 * @param onPerCardChange emits the new raw per-card text.
 * @param onTotalChange emits the new raw total text.
 * @param onOwnedFreeChange emits the new toggle value.
 * @param onClear clears both fields back to blank (no constraint).
 */
@Composable
fun BudgetInputBar(
    perCardText: String,
    totalText: String,
    ownedCardsAreFree: Boolean,
    hasError: Boolean,
    onPerCardChange: (String) -> Unit,
    onTotalChange: (String) -> Unit,
    onOwnedFreeChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val hasAnyText = perCardText.isNotEmpty() || totalText.isNotEmpty()

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
                    text = stringResource(R.string.deck_studio_budget_title).uppercase(),
                    style = ty.labelMedium,
                    color = mc.primaryAccent,
                    fontWeight = FontWeight.Bold,
                )
                AnimatedVisibility(
                    visible = hasAnyText,
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(150)),
                ) {
                    IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.deck_studio_budget_clear),
                            tint = mc.textSecondary,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            ) {
                BudgetField(
                    value = perCardText,
                    onValueChange = onPerCardChange,
                    label = stringResource(R.string.deck_studio_budget_per_card_label),
                    isError = hasError,
                    modifier = Modifier.weight(1f),
                )
                BudgetField(
                    value = totalText,
                    onValueChange = onTotalChange,
                    label = stringResource(R.string.deck_studio_budget_total_label),
                    isError = hasError,
                    modifier = Modifier.weight(1f),
                )
            }

            // Inline error slot — only occupies space while invalid.
            AnimatedVisibility(
                visible = hasError,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
            ) {
                Text(
                    text = stringResource(R.string.deck_studio_budget_invalid_error),
                    style = ty.bodySmall,
                    color = mc.lifeNegative,
                )
            }

            // Owned-cards-are-free toggle (≥48dp touch target via the Row height).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.deck_studio_budget_owned_free),
                    style = ty.bodyMedium,
                    color = mc.textPrimary,
                )
                Switch(
                    checked = ownedCardsAreFree,
                    onCheckedChange = onOwnedFreeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = mc.background,
                        checkedTrackColor = mc.primaryAccent,
                        uncheckedThumbColor = mc.textSecondary,
                        uncheckedTrackColor = mc.surfaceVariant,
                    ),
                    modifier = Modifier.size(width = 52.dp, height = 48.dp),
                )
            }
        }
    }
}

/** A single numeric € input field with a leading "€" affix. */
@Composable
private fun BudgetField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = ty.labelSmall) },
        leadingIcon = { Text("€", style = ty.bodyMedium, color = mc.textSecondary) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = CardShape,
        textStyle = ty.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = mc.primaryAccent,
            unfocusedBorderColor = mc.surfaceVariant,
            errorBorderColor = mc.lifeNegative,
            focusedTextColor = mc.textPrimary,
            unfocusedTextColor = mc.textPrimary,
            cursorColor = mc.primaryAccent,
            focusedLabelColor = mc.primaryAccent,
            unfocusedLabelColor = mc.textSecondary,
        ),
        modifier = modifier,
    )
}
