package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.ExtraSmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.PriceFormatter
import org.jetbrains.compose.resources.painterResource

/**
 * Stateless full-height sheet listing the shared card queue (Scanner + AddCard "Select multiple"):
 * per-entry quantity/edit/duplicate/remove and add-to-collection/wishlist, a name filter, an
 * "auto-delete on add" switch and the bulk add-all footer.
 *
 * @param isCommitting disables the "add all to collection" CTA while a commit is in flight.
 * @param isAddingAllToWishlist disables the "add all to wishlist" CTA while that batch is in flight.
 * @param inFlightEntryIds entries being written right now; their add, remove, edit and quantity
 *   controls are disabled so a write can neither be doubled nor lose a mid-write change.
 * @param toastMessage one-shot message shown in the sheet's own toast host; [onToastShown] is
 *   called once it has been handed to the host so the caller can clear it.
 * @param ownedCardIdentityKeys oracleIds and exact names already in the collection, driving the
 *   "already in collection" badge (a card is owned when either key matches).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardQueueSheet(
    cards: List<QueuedCard>,
    preferredCurrency: PreferredCurrency,
    ownedCardIdentityKeys: Set<String>,
    isAutoDeleteOnAddEnabled: Boolean,
    isCommitting: Boolean,
    isAddingAllToWishlist: Boolean,
    inFlightEntryIds: Set<String>,
    toastMessage: String?,
    toastType: MagicToastType,
    onToastShown: () -> Unit,
    onDismiss: () -> Unit,
    onRemoveCard: (QueuedCard) -> Unit,
    onEditCard: (QueuedCard) -> Unit,
    onClearQueue: () -> Unit,
    onAddAllToCollection: () -> Unit,
    onAddAllToWishlist: () -> Unit,
    onAddEntryToCollection: (QueuedCard) -> Unit,
    onAddEntryToWishlist: (QueuedCard) -> Unit,
    onCardClick: (QueuedCard) -> Unit,
    onDuplicateCard: (QueuedCard) -> Unit,
    onToggleAutoDeleteOnAdd: () -> Unit,
    onIncrementQuantity: (QueuedCard) -> Unit,
    onDecrementQuantity: (QueuedCard) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(cards, searchQuery) {
        if (searchQuery.isBlank()) cards
        else cards.filter { it.card.name.contains(searchQuery, ignoreCase = true) }
    }

    val sheetToastState = rememberMagicToastState()
    val currentOnToastShown by rememberUpdatedState(onToastShown)
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            sheetToastState.show(it, toastType)
            currentOnToastShown()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
        dragHandle = null,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = spacing.xs, end = spacing.lg, top = spacing.sm, bottom = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = mc.textSecondary
                        )
                    }
                    Text(
                        text = pluralStringResource(R.plurals.card_queue_title, cards.size, cards.size),
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearQueue) {
                        Icon(Icons.Rounded.Delete, stringResource(R.string.card_queue_clear_cd), tint = mc.lifeNegative)
                    }
                }

                // Sticky switch: stays visible above the scrollable list.
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.xs)) {
                    QueueToggleRow(
                        title = stringResource(R.string.scanner_queue_auto_delete_title),
                        subtitle = stringResource(R.string.scanner_queue_auto_delete_desc),
                        checked = isAutoDeleteOnAddEnabled,
                        onCheckedChange = { onToggleAutoDeleteOnAdd() },
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg),
                    placeholder = { Text(stringResource(R.string.scanner_queue_search_placeholder), style = ty.bodyMedium, color = mc.textDisabled) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = mc.textDisabled) },
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = mc.textPrimary.copy(alpha = 0.05f),
                        focusedContainerColor = mc.textPrimary.copy(alpha = 0.05f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        focusedTextColor = mc.textPrimary,
                        unfocusedTextColor = mc.textPrimary
                    )
                )

                Spacer(Modifier.height(spacing.lg))

                LazyColumn(modifier = Modifier.weight(1f), state = listState) {
                    items(filtered, key = { it.id }) { entry ->
                        QueueCardItem(
                            entry = entry,
                            preferredCurrency = preferredCurrency,
                            isInCollection = entry.card.isOwnedIn(ownedCardIdentityKeys),
                            isWriteInFlight = entry.id in inFlightEntryIds,
                            onEdit = { onEditCard(entry) },
                            onDelete = { onRemoveCard(entry) },
                            onAddToCollection = { onAddEntryToCollection(entry) },
                            onAddToWishlist = { onAddEntryToWishlist(entry) },
                            onClick = { onCardClick(entry) },
                            onDuplicate = { onDuplicateCard(entry) },
                            onIncrement = { onIncrementQuantity(entry) },
                            onDecrement = { onDecrementQuantity(entry) },
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(mc.backgroundSecondary)
                        .padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    MagicCtaButton(
                        onClick = onAddAllToCollection,
                        text = stringResource(R.string.scanner_queue_add_all),
                        icon = @Composable { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, modifier = Modifier.size(18.dp)) },
                        enabled = !isCommitting,
                        isLoading = isCommitting,
                        modifier = Modifier.fillMaxWidth()
                    )

                    MagicCtaButton(
                        onClick = onAddAllToWishlist,
                        text = stringResource(R.string.scanner_queue_add_all_wishlist),
                        icon = @Composable { Icon(Icons.Rounded.FavoriteBorder, null, modifier = Modifier.size(18.dp)) },
                        style = MagicCtaStyle.Outlined,
                        enabled = !isAddingAllToWishlist,
                        isLoading = isAddingAllToWishlist,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Positioned above the bulk action footer.
            MagicToastHost(
                state = sheetToastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp)
            )
        }
    }
}

// Rows cached before the oracle_id backfill have a blank oracleId, so the name must match too.
private fun Card.isOwnedIn(ownedKeys: Set<String>): Boolean =
    (oracleId.isNotBlank() && oracleId in ownedKeys) || name in ownedKeys

/**
 * Edit sheet for one queue entry (foil / condition / language / quantity), with an entry point to
 * the printing picker ([onOpenVariantSelector], typically a [VariantSelectorSheet] stacked on top).
 */
@Composable
fun EditQueuedCardSheet(
    queuedCard: QueuedCard,
    availablePrints: List<Card>,
    isLoadingPrints: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (QueuedCard) -> Unit,
    onOpenVariantSelector: () -> Unit,
) {
    AddCardSheet(
        cardName = queuedCard.card.name,
        onConfirm = { foil: Boolean, cond: String, lang: String, q: Int ->
            onConfirm(
                queuedCard.copy(
                    isFoil = foil,
                    condition = cond,
                    language = lang,
                    quantity = q,
                )
            )
        },
        onDismiss = onDismiss,
        cardImage = queuedCard.card.imageNormal,
        initialFoil = queuedCard.isFoil,
        initialCondition = queuedCard.condition,
        initialLanguage = queuedCard.language,
        initialQty = queuedCard.quantity,
        confirmButtonText = stringResource(R.string.scanner_edit_save),
        setCode = queuedCard.card.setCode,
        setName = queuedCard.card.setName,
        rarity = queuedCard.card.rarity,
        onOpenVariantSelector = onOpenVariantSelector,
        // Reserves the art footprint so switching entries never jumps while Coil loads.
        cardImagePlaceholder = painterResource(Res.drawable.mtg_card_back),
    )
}

@Composable
private fun QueueCardItem(
    entry: QueuedCard,
    preferredCurrency: PreferredCurrency,
    isInCollection: Boolean,
    isWriteInFlight: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddToCollection: () -> Unit,
    onAddToWishlist: () -> Unit,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = spacing.md)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(spacing.lg)
        ) {
            AsyncImage(
                model = entry.card.imageArtCrop ?: entry.card.imageNormal,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(ChipShape)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.card.name,
                    style = ty.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(spacing.xs))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetSymbol(
                        setCode = entry.card.setCode,
                        rarity = CardRarity.fromString(entry.card.rarity),
                        size = 16.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.card_queue_set_and_number, entry.card.setName, entry.card.collectorNumber),
                        style = ty.labelMedium,
                        color = mc.secondaryAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(spacing.sm))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        LanguageBadge(langCode = entry.language)
                        AttrTag(entry.condition)

                        if (entry.isFoil) {
                            AttrTag(stringResource(R.string.scanner_foil))
                        }

                        // Any printing/language of the same oracle identity counts as owned.
                        if (isInCollection) {
                            Icon(
                                imageVector = Icons.Rounded.CollectionsBookmark,
                                contentDescription = stringResource(R.string.scanner_already_in_collection),
                                tint = mc.primaryAccent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    QuantitySelector(
                        quantity = entry.quantity,
                        enabled = !isWriteInFlight,
                        onIncrement = onIncrement,
                        onDecrement = onDecrement
                    )
                }

                Spacer(Modifier.height(spacing.sm))

                Text(
                    text = PriceFormatter.formatFromScryfall(
                        if (entry.isFoil) entry.card.priceUsdFoil else entry.card.priceUsd,
                        if (entry.isFoil) entry.card.priceEurFoil else entry.card.priceEur,
                        preferredCurrency,
                    ),
                    style = ty.labelLarge.copy(fontWeight = FontWeight.Bold, color = mc.goldMtg),
                )
            }
        }

        Spacer(Modifier.height(spacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.sm),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            QueueActionButton(
                icon = Icons.Rounded.Style,
                label = stringResource(R.string.action_add),
                tint = mc.primaryAccent,
                onClick = onAddToCollection,
                enabled = !isWriteInFlight,
                modifier = Modifier.weight(1f)
            )
            QueueActionButton(
                icon = Icons.Rounded.FavoriteBorder,
                label = stringResource(R.string.carddetail_add_to_wishlist),
                tint = mc.secondaryAccent,
                onClick = onAddToWishlist,
                enabled = !isWriteInFlight,
                modifier = Modifier.weight(1f)
            )
            QueueActionButton(
                icon = Icons.Rounded.Clear,
                label = stringResource(R.string.action_remove),
                tint = mc.lifeNegative,
                onClick = onDelete,
                enabled = !isWriteInFlight,
                modifier = Modifier.weight(1f)
            )
            QueueActionButton(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.action_edit),
                tint = mc.textSecondary,
                onClick = onEdit,
                enabled = !isWriteInFlight,
                modifier = Modifier.weight(1f)
            )
            QueueActionButton(
                icon = Icons.Rounded.ContentCopy,
                label = stringResource(R.string.scanner_duplicate_entry),
                tint = mc.textSecondary,
                onClick = onDuplicate,
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = spacing.sm),
            color = mc.textPrimary.copy(alpha = 0.05f)
        )
    }
}

@Composable
private fun QuantitySelector(
    quantity: Int,
    enabled: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(
        modifier = modifier
            .background(mc.textPrimary.copy(alpha = 0.05f), ChipShape)
            .padding(horizontal = spacing.xs, vertical = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        IconButton(onClick = onDecrement, enabled = enabled, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Remove, stringResource(R.string.card_queue_decrease_quantity_cd), tint = if (enabled) mc.textPrimary else mc.textDisabled, modifier = Modifier.size(16.dp))
        }
        Text(
            text = quantity.toString(),
            style = ty.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = mc.secondaryAccent,
            modifier = Modifier.widthIn(min = 20.dp),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onIncrement, enabled = enabled, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Add, stringResource(R.string.card_queue_increase_quantity_cd), tint = if (enabled) mc.textPrimary else mc.textDisabled, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun QueueActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val effectiveTint = if (enabled) tint else MaterialTheme.magicColors.textDisabled
    Column(
        modifier = modifier
            .clip(ChipShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = spacing.sm, horizontal = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Icon(icon, null, tint = effectiveTint, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = ty.labelSmall.copy(fontSize = 9.sp),
            color = effectiveTint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun AttrTag(text: String) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Surface(
        color = mc.textPrimary.copy(alpha = 0.1f),
        shape = ExtraSmallCardShape,
        modifier = Modifier.padding(vertical = spacing.xxs)
    ) {
        Text(
            text = text,
            style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp),
            color = mc.textPrimary,
            modifier = Modifier.padding(horizontal = spacing.xs, vertical = 1.dp)
        )
    }
}

@Composable
private fun QueueToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.magicTypography.bodyMedium, color = mc.textPrimary)
            Text(text = subtitle, style = MaterialTheme.magicTypography.bodySmall, color = mc.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
