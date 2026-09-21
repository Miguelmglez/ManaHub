package com.mmg.manahub.feature.scanner.presentation.components
// COMMENTS_REVIEWED: 2026-09-17

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.core.ui.components.DeckCardQueueItem
import com.mmg.manahub.core.ui.components.DeckCardQueueSheet
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.usecase.DeckBoard

/**
 * The scanner's own deck-target queue — a thin wrapper (Deck Wizard 60-card wave v6 P4, plan 4.4)
 * mapping the scanner's per-deck [QueuedCard] queue onto the generic [DeckCardQueueSheet].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckScannerQueueSheet(
    cards: List<QueuedCard>,
    preferredCurrency: PreferredCurrency,
    isCommitting: Boolean,
    toastMessage: String?,
    toastType: MagicToastType,
    onToastDismissed: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    onDismiss: () -> Unit,
    onRemoveEntry: (QueuedCard) -> Unit,
    onEditEntry: (QueuedCard) -> Unit,
    onClearSession: () -> Unit,
    onAddEntryToDeck: (QueuedCard, DeckBoard) -> Unit,
    onAddAllToDeck: (DeckBoard) -> Unit,
    isAutoDeleteOnAddEnabled: Boolean,
    onToggleAutoDeleteOnAdd: () -> Unit,
    onIncrementQuantity: (QueuedCard) -> Unit,
    onDecrementQuantity: (QueuedCard) -> Unit,
) {
    val entriesById = remember(cards) { cards.associateBy { it.id } }
    val foilLabel = stringResource(R.string.scanner_edit_foil_value)
    val items = remember(cards, foilLabel) {
        cards.map { entry ->
            DeckCardQueueItem(
                id = entry.id,
                card = entry.card,
                quantity = entry.quantity,
                supportingLabel = if (entry.isFoil) foilLabel else null,
                isInCollection = false,
            )
        }
    }

    DeckCardQueueSheet(
        title = stringResource(R.string.scanner_deck_queue_title, cards.size),
        items = items,
        preferredCurrency = preferredCurrency,
        isBusy = isCommitting,
        onDismiss = onDismiss,
        onIncrement = { item -> entriesById[item.id]?.let(onIncrementQuantity) },
        onDecrement = { item -> entriesById[item.id]?.let(onDecrementQuantity) },
        onRemove = { item -> entriesById[item.id]?.let(onRemoveEntry) },
        emptyTitle = stringResource(R.string.scanner_deck_queue_empty_title),
        emptySubtitle = stringResource(R.string.scanner_deck_queue_empty_subtitle),
        onRowClick = { item -> entriesById[item.id]?.let(onEditEntry) },
        onClearAll = onClearSession,
        noMatchesTitle = stringResource(R.string.scanner_deck_queue_no_matches_title),
        noMatchesSubtitle = stringResource(R.string.scanner_deck_queue_no_matches_subtitle),
        clearAllDescription = stringResource(R.string.scanner_deck_queue_clear_all),
        searchPlaceholder = stringResource(R.string.scanner_queue_search_placeholder),
        listState = listState,
        toastMessage = toastMessage,
        toastType = toastType,
        onToastDismissed = onToastDismissed,
        headerContent = {
            DeckScannerSettingsToggleRow(
                checked = isAutoDeleteOnAddEnabled,
                enabled = !isCommitting,
                onCheckedChange = { _ -> onToggleAutoDeleteOnAdd() },
            )
        },
        rowActions = { item ->
            entriesById[item.id]?.let { entry ->
                DeckScannerQueueRowActions(
                    entry = entry,
                    isCommitting = isCommitting,
                    onMainboard = { onAddEntryToDeck(entry, DeckBoard.MAINBOARD) },
                    onSideboard = { onAddEntryToDeck(entry, DeckBoard.SIDEBOARD) },
                    onDiscard = { onRemoveEntry(entry) },
                )
            }
        },
        footerContent = {
            MagicCtaButton(
                onClick = { onAddAllToDeck(DeckBoard.MAINBOARD) },
                text = stringResource(R.string.scanner_deck_add_all_mainboard),
                color = MagicCtaColor.Primary,
                enabled = !isCommitting && cards.isNotEmpty(),
                isLoading = isCommitting,
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
            )
            MagicCtaButton(
                onClick = { onAddAllToDeck(DeckBoard.SIDEBOARD) },
                text = stringResource(R.string.scanner_deck_add_all_sideboard),
                color = MagicCtaColor.Accent,
                style = MagicCtaStyle.Outlined,
                enabled = !isCommitting && cards.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
            )
        },
    )
}

/** The Mainboard / Sideboard / Discard action row below each scanned card (unchanged from before the extraction). */
@Composable
private fun DeckScannerQueueRowActions(
    entry: QueuedCard,
    isCommitting: Boolean,
    onMainboard: () -> Unit,
    onSideboard: () -> Unit,
    onDiscard: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
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
