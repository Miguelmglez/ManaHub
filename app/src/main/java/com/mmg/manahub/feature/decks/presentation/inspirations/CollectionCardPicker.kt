package com.mmg.manahub.feature.decks.presentation.inspirations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.components.CardRow
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/** The owned-card search field both tabs share; the clear icon only appears once something is typed. */
@Composable
internal fun CollectionCardSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val placeholder = stringResource(R.string.deck_inspirations_search_owned_hint)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        // Placeholder text alone is not exposed as a field name to TalkBack.
        modifier = modifier.fillMaxWidth().semantics { contentDescription = placeholder },
        singleLine = true,
        placeholder = { Text(placeholder, style = ty.bodyMedium, color = mc.textDisabled) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.deck_studio_inspirations_search_clear), tint = mc.textSecondary)
                }
            }
        } else {
            null
        },
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

/** The pinned card that replaces the search field: a -/+ stepper for the selection and a clear action. */
@Composable
internal fun PinnedCollectionCard(
    card: Card,
    caption: String,
    quantity: Int,
    addEnabled: Boolean,
    onAdd: () -> Unit,
    onDecrement: () -> Unit,
    onInspect: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Text(text = caption, style = ty.labelMedium, color = mc.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
            CardRow(
                card = card,
                isInCollection = true,
                onClick = onInspect,
                onRemove = onDecrement,
                quantity = quantity,
                onAdd = onAdd,
                addEnabled = addEnabled,
                onImageClick = onInspect,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.deck_inspirations_clear_pinned_a11y), tint = mc.textSecondary)
            }
        }
    }
}

/** One owned-card search result; tapping the row pins it, tapping the art inspects it. */
@Composable
internal fun CollectionCardResultRow(
    card: Card,
    onPick: () -> Unit,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CardRow(
        card = card,
        isInCollection = true,
        onClick = onPick,
        onRemove = null,
        onImageClick = onInspect,
        onClickLabel = stringResource(R.string.deck_inspirations_pick_card_a11y),
        modifier = modifier,
    )
}
