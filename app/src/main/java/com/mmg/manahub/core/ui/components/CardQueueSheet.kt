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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.CardSelectionEntry
import com.mmg.manahub.core.model.CardSelectionSession
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.PriceFormatter

/**
 * A general bottom sheet for managing a queue of cards to be added to collection or wishlist.
 * Used by the Scanner and potentially other bulk-add features.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardQueueSheet(
    session: CardSelectionSession,
    preferredCurrency: PreferredCurrency,
    ownedCardIdentityKeys: Set<String>,
    isAutoDeleteOnAddEnabled: Boolean,
    isCommitting: Boolean,
    toastMessage: String?,
    toastType: MagicToastType,
    onToastDismissed: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
    onDismiss: () -> Unit,
    onRemoveEntry: (CardSelectionEntry) -> Unit,
    onEditEntry: (CardSelectionEntry) -> Unit,
    onClearSession: () -> Unit,
    onAddAllToCollection: () -> Unit,
    onAddAllToWishlist: () -> Unit,
    onAddEntryToCollection: (CardSelectionEntry) -> Unit,
    onAddEntryToWishlist: (CardSelectionEntry) -> Unit,
    onNavigateToCardDetail: (scryfallId: String) -> Unit,
    onDuplicateEntry: (CardSelectionEntry) -> Unit,
    onToggleAutoDeleteOnAdd: () -> Unit,
    onIncrementQuantity: (CardSelectionEntry) -> Unit,
    onDecrementQuantity: (CardSelectionEntry) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(session.entries, searchQuery) {
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
        containerColor = mc.background,
        dragHandle = null,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
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
                        text = stringResource(R.string.scanner_queue_title, session.entries.size),
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearSession) {
                        Icon(Icons.Rounded.Delete, null, tint = mc.lifeNegative)
                    }
                }

                // Sticky "Auto-delete on add" switch
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SettingsToggleRow(
                        title = stringResource(R.string.scanner_queue_auto_delete_title),
                        subtitle = stringResource(R.string.scanner_queue_auto_delete_desc),
                        checked = isAutoDeleteOnAddEnabled,
                        onCheckedChange = { onToggleAutoDeleteOnAdd() },
                    )
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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

                Spacer(Modifier.height(16.dp))

                // Card List
                LazyColumn(modifier = Modifier.weight(1f), state = listState) {
                    items(filtered, key = { it.id }) { entry ->
                        CardQueueItem(
                            entry = entry,
                            preferredCurrency = preferredCurrency,
                            isInCollection = entry.card.oracleId.ifBlank { entry.card.name } in ownedCardIdentityKeys,
                            onEdit = { onEditEntry(entry) },
                            onDelete = { onRemoveEntry(entry) },
                            onAddToCollection = { onAddEntryToCollection(entry) },
                            onAddToWishlist = { onAddEntryToWishlist(entry) },
                            onClick = { onNavigateToCardDetail(entry.card.scryfallId) },
                            onDuplicate = { onDuplicateEntry(entry) },
                            onIncrement = { onIncrementQuantity(entry) },
                            onDecrement = { onDecrementQuantity(entry) },
                        )
                    }
                }

                // Bulk Actions Footer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(mc.backgroundSecondary)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            MagicToastHost(
                state = sheetToastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp)
            )
        }
    }
}

@Composable
fun CardQueueItem(
    entry: CardSelectionEntry,
    preferredCurrency: PreferredCurrency,
    isInCollection: Boolean,
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = entry.card.imageArtCrop ?: entry.card.imageNormal,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.card.name,
                    style = ty.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp),
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetSymbol(
                        setCode = entry.card.setCode,
                        rarity = CardRarity.fromString(entry.card.rarity),
                        size = 16.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${entry.card.setName} #${entry.card.collectorNumber}",
                        style = ty.labelMedium,
                        color = mc.secondaryAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageBadge(langCode = entry.language)
                        AttrTag(entry.condition)

                        if (entry.isFoil) {
                            AttrTag(stringResource(R.string.scanner_foil))
                        }

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
                        onIncrement = onIncrement,
                        onDecrement = onDecrement
                    )
                }

                Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            QueueActionButton(
                icon = Icons.Rounded.Style,
                label = stringResource(R.string.action_add),
                tint = mc.primaryAccent,
                onClick = onAddToCollection,
                modifier = Modifier.weight(1f)
            )
            QueueActionButton(
                icon = Icons.Rounded.FavoriteBorder,
                label = stringResource(R.string.carddetail_add_to_wishlist),
                tint = mc.secondaryAccent,
                onClick = onAddToWishlist,
                modifier = Modifier.weight(1f)
            )
            QueueActionButton(
                icon = Icons.Rounded.Clear,
                label = stringResource(R.string.action_remove),
                tint = mc.lifeNegative,
                onClick = onDelete,
                modifier = Modifier.weight(1f)
            )
            QueueActionButton(
                icon = Icons.Rounded.Edit,
                label = stringResource(R.string.action_edit),
                tint = mc.textSecondary,
                onClick = onEdit,
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
            modifier = Modifier.padding(top = 8.dp),
            color = mc.textPrimary.copy(alpha = 0.05f)
        )
    }
}

@Composable
fun QuantitySelector(
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(
        modifier = modifier
            .background(mc.textPrimary.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = onDecrement, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Remove, null, tint = mc.textPrimary, modifier = Modifier.size(16.dp))
        }
        Text(
            text = quantity.toString(),
            style = ty.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = mc.secondaryAccent,
            modifier = Modifier.widthIn(min = 20.dp),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onIncrement, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Add, null, tint = mc.textPrimary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun QueueActionButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ty = MaterialTheme.magicTypography
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            text = label,
            style = ty.labelSmall.copy(fontSize = 9.sp),
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun AttrTag(text: String) {
    val mc = MaterialTheme.magicColors
    Surface(
        color = mc.textPrimary.copy(alpha = 0.1f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp),
            color = mc.textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val mc = MaterialTheme.magicColors
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.magicTypography.bodyMedium, color = mc.textPrimary)
            Text(text = subtitle, style = MaterialTheme.magicTypography.bodySmall, color = mc.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
