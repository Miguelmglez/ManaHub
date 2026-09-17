package com.mmg.manahub.core.ui.components
// COMMENTS_REVIEWED: 2026-09-17

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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * One row of a [DeckCardQueueSheet] — a card plus the quantity/collection state the sheet needs to
 * render it, decoupled from any specific queue's own entry model (`CardSelectionEntry`,
 * `WizardSeed`, ...).
 */
data class DeckCardQueueItem(
    val id: String,
    val card: Card,
    val quantity: Int,
    val supportingLabel: String? = null,
    val maxQuantity: Int? = null,
    val isInCollection: Boolean = false,
)

/**
 * A generic bottom sheet listing cards queued for a deck (Deck Wizard 60-card wave v6 P4,
 * plan 4.3) — extracted from the scanner's own `DeckScannerQueueSheet`, which now wraps this with
 * its own `CardSelectionEntry`-shaped signature (plan 4.4).
 *
 * Named `DeckCardQueueSheet`/`DeckCardQueueItem` rather than the plan's literal `CardQueueSheet`/
 * `CardQueueItem` — this package already declares an UNRELATED `CardQueueSheet`/`CardQueueItem`
 * pair (`core/ui/components/CardQueueSheet.kt`, the Multi Add/collection-and-wishlist bulk-add
 * queue consumed by `MultiAddCardScreen` and the scanner's collection-target mode) predating this
 * plan. Renamed the NEW component instead of touching that unrelated, live one — same resolution
 * this codebase already used for the `StrategyPickerSheet` name collision (see feature/decks
 * CLAUDE.md).
 *
 * [noMatchesTitle]/[noMatchesSubtitle] are an addition beyond the plan's literal signature: without
 * them, a caller's search-filtered-to-zero state would have to reuse [emptyTitle]/[emptySubtitle]
 * (the "nothing queued at all" copy), which is wrong copy and would NOT be byte-identical to the
 * scanner's existing dual-message behavior that Gate 4 requires. Default null falls back to
 * [emptyTitle]/[emptySubtitle] for callers (e.g. the Wizard's seed queue) that don't need the
 * distinction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckCardQueueSheet(
    title: String,
    items: List<DeckCardQueueItem>,
    preferredCurrency: PreferredCurrency,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onIncrement: (DeckCardQueueItem) -> Unit,
    onDecrement: (DeckCardQueueItem) -> Unit,
    onRemove: (DeckCardQueueItem) -> Unit,
    emptyTitle: String,
    emptySubtitle: String,
    onRowClick: ((DeckCardQueueItem) -> Unit)? = null,
    onImageClick: ((DeckCardQueueItem) -> Unit)? = null,
    onClearAll: (() -> Unit)? = null,
    noMatchesTitle: String? = null,
    noMatchesSubtitle: String? = null,
    clearAllDescription: String = stringResource(R.string.card_queue_clear_all),
    searchPlaceholder: String = stringResource(R.string.card_queue_search_placeholder),
    listState: LazyListState = rememberLazyListState(),
    toastMessage: String? = null,
    toastType: MagicToastType = MagicToastType.INFO,
    onToastDismissed: () -> Unit = {},
    headerContent: (@Composable () -> Unit)? = null,
    rowActions: (@Composable (DeckCardQueueItem) -> Unit)? = null,
    footerContent: (@Composable () -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden },
    )
    var searchQuery by remember { mutableStateOf("") }
    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { it.card.name.contains(searchQuery, ignoreCase = true) }
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
                    IconButton(onClick = onDismiss, enabled = !isBusy) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel), tint = mc.textSecondary)
                    }
                    Text(
                        text = title,
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (onClearAll != null) {
                        IconButton(onClick = onClearAll, enabled = !isBusy) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = clearAllDescription,
                                tint = mc.lifeNegative,
                            )
                        }
                    }
                }

                headerContent?.invoke()

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.sm),
                    placeholder = { Text(searchPlaceholder, style = ty.bodyMedium, color = mc.textDisabled) },
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

                if (filteredItems.isEmpty()) {
                    val showNoMatches = items.isNotEmpty()
                    EmptyState(
                        title = if (showNoMatches) noMatchesTitle ?: emptyTitle else emptyTitle,
                        subtitle = if (showNoMatches) noMatchesSubtitle ?: emptySubtitle else emptySubtitle,
                        icon = Icons.Rounded.Style,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        items(filteredItems, key = { it.id }) { item ->
                            DeckCardQueueRow(
                                item = item,
                                preferredCurrency = preferredCurrency,
                                onRowClick = onRowClick,
                                onImageClick = onImageClick,
                                onIncrement = onIncrement,
                                onDecrement = onDecrement,
                                onRemove = onRemove,
                                rowActions = rowActions,
                            )
                        }
                    }
                }

                if (footerContent != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(mc.backgroundSecondary).padding(spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        footerContent()
                    }
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
private fun DeckCardQueueRow(
    item: DeckCardQueueItem,
    preferredCurrency: PreferredCurrency,
    onRowClick: ((DeckCardQueueItem) -> Unit)?,
    onImageClick: ((DeckCardQueueItem) -> Unit)?,
    onIncrement: (DeckCardQueueItem) -> Unit,
    onDecrement: (DeckCardQueueItem) -> Unit,
    onRemove: (DeckCardQueueItem) -> Unit,
    rowActions: (@Composable (DeckCardQueueItem) -> Unit)?,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        CardRow(
            card = item.card,
            isInCollection = item.isInCollection,
            onClick = { onRowClick?.invoke(item) },
            onRemove = { onDecrement(item) },
            quantity = item.quantity,
            onAdd = { onIncrement(item) },
            addEnabled = item.maxQuantity?.let { item.quantity < it } ?: true,
            preferredCurrency = preferredCurrency,
            onImageClick = onImageClick?.let { callback -> { callback(item) } },
            extraSupportingContent = item.supportingLabel?.let { label ->
                @Composable {
                    Text(
                        text = label,
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.goldMtg,
                    )
                }
            },
        )
        if (rowActions != null) {
            rowActions(item)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { onRemove(item) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove), tint = mc.textDisabled)
                }
            }
        }
    }
}
