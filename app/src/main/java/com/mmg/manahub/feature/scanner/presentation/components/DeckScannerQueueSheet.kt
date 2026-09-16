package com.mmg.manahub.feature.scanner.presentation.components
// COMMENTS_REVIEWED: 2026-09-16

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.core.model.CardSelectionEntry
import com.mmg.manahub.core.model.CardSelectionSession
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.components.CardRow
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.usecase.DeckBoard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScannerQueueSheet(
    session: CardSelectionSession,
    preferredCurrency: PreferredCurrency,
    isCommitting: Boolean,
    toastMessage: String?,
    toastType: MagicToastType,
    onToastDismissed: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    onDismiss: () -> Unit,
    onRemoveEntry: (CardSelectionEntry) -> Unit,
    onEditEntry: (CardSelectionEntry) -> Unit,
    onClearSession: () -> Unit,
    onAddEntryToDeck: (CardSelectionEntry, DeckBoard) -> Unit,
    onAddAllToDeck: (DeckBoard) -> Unit,
    isAutoDeleteOnAddEnabled: Boolean,
    onToggleAutoDeleteOnAdd: () -> Unit,
    onIncrementQuantity: (CardSelectionEntry) -> Unit,
    onDecrementQuantity: (CardSelectionEntry) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )
    var searchQuery by remember { mutableStateOf("") }
    val filteredEntries = remember(session.entries, searchQuery) {
        if (searchQuery.isBlank()) session.entries
        else session.entries.filter { it.card.name.contains(searchQuery, ignoreCase = true) }
    }
    val sheetToastState = rememberMagicToastState()

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            sheetToastState.show(it, toastType)
            onToastDismissed()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = mc.background,
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = spacing.xs, end = spacing.sm, top = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss, enabled = !isCommitting) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel), tint = mc.textSecondary)
                    }
                    Text(
                        text = stringResource(R.string.scanner_deck_queue_title, session.entries.size),
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClearSession, enabled = !isCommitting) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.scanner_deck_queue_clear_all),
                            tint = mc.lifeNegative,
                        )
                    }
                }

                DeckScannerSettingsToggleRow(
                    checked = isAutoDeleteOnAddEnabled,
                    enabled = !isCommitting,
                    onCheckedChange = { _ -> onToggleAutoDeleteOnAdd() },
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    enabled = !isCommitting,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm),
                    placeholder = { Text(stringResource(R.string.scanner_queue_search_placeholder), style = ty.bodyMedium, color = mc.textDisabled) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = mc.textDisabled) },
                    shape = ButtonShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = mc.textPrimary.copy(alpha = 0.05f),
                        focusedContainerColor = mc.textPrimary.copy(alpha = 0.05f),
                        unfocusedBorderColor = mc.background.copy(alpha = 0f),
                        focusedBorderColor = mc.primaryAccent,
                        disabledBorderColor = mc.background.copy(alpha = 0f),
                        focusedTextColor = mc.textPrimary,
                        unfocusedTextColor = mc.textPrimary,
                        disabledTextColor = mc.textDisabled,
                    ),
                )

                if (filteredEntries.isEmpty()) {
                    EmptyState(
                        title = stringResource(
                            if (session.entries.isEmpty()) R.string.scanner_deck_queue_empty_title
                            else R.string.scanner_deck_queue_no_matches_title,
                        ),
                        subtitle = stringResource(
                            if (session.entries.isEmpty()) R.string.scanner_deck_queue_empty_subtitle
                            else R.string.scanner_deck_queue_no_matches_subtitle,
                        ),
                        icon = Icons.Rounded.Style,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        items(filteredEntries, key = { it.id }) { entry ->
                            DeckScannerQueueRow(
                                entry = entry,
                                preferredCurrency = preferredCurrency,
                                isCommitting = isCommitting,
                                onEdit = { onEditEntry(entry) },
                                onMainboard = { onAddEntryToDeck(entry, DeckBoard.MAINBOARD) },
                                onSideboard = { onAddEntryToDeck(entry, DeckBoard.SIDEBOARD) },
                                onDiscard = { onRemoveEntry(entry) },
                                onIncrement = { onIncrementQuantity(entry) },
                                onDecrement = { onDecrementQuantity(entry) },
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().background(mc.backgroundSecondary).padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    MagicCtaButton(
                        onClick = { onAddAllToDeck(DeckBoard.MAINBOARD) },
                        text = stringResource(R.string.scanner_deck_add_all_mainboard),
                        color = MagicCtaColor.Primary,
                        enabled = !isCommitting && session.entries.isNotEmpty(),
                        isLoading = isCommitting,
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    )
                    MagicCtaButton(
                        onClick = { onAddAllToDeck(DeckBoard.SIDEBOARD) },
                        text = stringResource(R.string.scanner_deck_add_all_sideboard),
                        color = MagicCtaColor.Accent,
                        style = MagicCtaStyle.Outlined,
                        enabled = !isCommitting && session.entries.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
                    )
                }
            }

            MagicToastHost(
                state = sheetToastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = spacing.xxl + spacing.xxl + spacing.xxl + spacing.xxl + spacing.lg),
            )
        }
    }
}

@Composable
private fun DeckScannerQueueRow(
    entry: CardSelectionEntry,
    preferredCurrency: PreferredCurrency,
    isCommitting: Boolean,
    onEdit: () -> Unit,
    onMainboard: () -> Unit,
    onSideboard: () -> Unit,
    onDiscard: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val mainboardActionDescription = stringResource(
        R.string.scanner_deck_action_mainboard_a11y,
        entry.card.name,
    )
    val sideboardActionDescription = stringResource(
        R.string.scanner_deck_action_sideboard_a11y,
        entry.card.name,
    )
    val discardActionDescription = stringResource(
        R.string.scanner_deck_action_discard_a11y,
        entry.card.name,
    )
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        CardRow(
            card = entry.card,
            isInCollection = false,
            onClick = onEdit,
            onRemove = onDecrement,
            quantity = entry.quantity,
            onAdd = onIncrement,
            preferredCurrency = preferredCurrency,
            extraSupportingContent = {
                if (entry.isFoil) {
                    Text(
                        text = stringResource(R.string.scanner_edit_foil_value),
                        style = ty.labelSmall,
                        color = mc.goldMtg,
                    )
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QueueActionButton(
                icon = Icons.Default.Add,
                label = stringResource(R.string.scanner_deck_action_mainboard),
                tint = if (isCommitting) mc.textDisabled else mc.primaryAccent,
                onClick = onMainboard,
                enabled = !isCommitting,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = mainboardActionDescription
                    },
            )
            QueueActionButton(
                icon = Icons.Default.Add,
                label = stringResource(R.string.scanner_deck_action_sideboard),
                tint = if (isCommitting) mc.textDisabled else mc.secondaryAccent,
                onClick = onSideboard,
                enabled = !isCommitting,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = sideboardActionDescription
                    },
                )
            QueueActionButton(
                icon = Icons.Rounded.Clear,
                label = stringResource(R.string.scanner_deck_action_discard),
                tint = if (isCommitting) mc.textDisabled else mc.lifeNegative,
                onClick = onDiscard,
                enabled = !isCommitting,
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription = discardActionDescription
                    },
            )
        }
    }
}

@Composable
private fun QueueActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val ty = MaterialTheme.magicTypography
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = ty.labelSmall.copy(fontSize = 9.sp),
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun DeckScannerSettingsToggleRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val autoDeleteDescription = stringResource(R.string.scanner_deck_queue_auto_delete_a11y)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.scanner_deck_queue_auto_delete_title),
                style = ty.bodyMedium,
                color = if (enabled) mc.textPrimary else mc.textDisabled,
            )
            Text(
                text = stringResource(R.string.scanner_deck_queue_auto_delete_desc),
                style = ty.bodySmall,
                color = if (enabled) mc.textSecondary else mc.textDisabled,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = autoDeleteDescription
            },
        )
    }
}
