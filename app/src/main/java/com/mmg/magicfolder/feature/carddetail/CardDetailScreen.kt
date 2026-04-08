package com.mmg.magicfolder.feature.carddetail


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import com.mmg.magicfolder.R
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.CardTag
import com.mmg.magicfolder.core.domain.model.SuggestedTag
import com.mmg.magicfolder.core.domain.model.TagCategory
import com.mmg.magicfolder.core.domain.model.UserCard
import com.mmg.magicfolder.core.domain.model.UserDefinedTag
import com.mmg.magicfolder.core.ui.components.CardRarity
import com.mmg.magicfolder.core.ui.components.FoilBadge
import com.mmg.magicfolder.core.ui.components.ManaCostImages
import com.mmg.magicfolder.core.ui.components.SetSymbol
import com.mmg.magicfolder.core.ui.components.StaleBadge
import com.mmg.magicfolder.core.domain.model.PreferredCurrency
import com.mmg.magicfolder.core.ui.theme.LocalPreferredCurrency
import com.mmg.magicfolder.core.util.PriceFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    onBack: () -> Unit,
    onNavigateToAddCard: () -> Unit,
    viewModel: CardDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                    Text(
                        text = uiState.card?.name ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

    ) { padding ->
        when {
            uiState.isLoading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.card != null -> CardDetailContent(
                card = uiState.card!!,
                userCards = uiState.userCards,
                isStale = uiState.isStale,
                onRemoveAutoTag = viewModel::onRemoveTag,
                onAddUserTag = viewModel::onAddUserTag,
                onRemoveUserTag = viewModel::onRemoveUserTag,
                onShowTagPicker = viewModel::onShowTagPicker,
                onConfirmSuggestedTag = viewModel::onConfirmSuggestedTag,
                onDismissSuggestedTag = viewModel::onDismissSuggestedTag,
                onShowAddSheet = viewModel::onShowAddSheet,
                onShowWishlistSheet = viewModel::onShowWishlistSheet,
                onShowTradeSheet = viewModel::onShowTradeSheet,
                onUpdateQuantity = { id, qty -> viewModel.onUpdateQuantity(id, qty) },
                onRequestDelete = viewModel::onRequestDelete,
                modifier = Modifier.padding(padding),
            )
        }
    }

    // Tag picker sheet
    if (uiState.showTagPicker) {
        TagPickerSheet(
            cardAutoTags = uiState.card?.tags ?: emptyList(),
            cardSuggestedTags = uiState.card?.suggestedTags ?: emptyList(),
            currentUserTags = uiState.card?.userTags ?: emptyList(),
            userDefinedTags = uiState.userDefinedTags,
            onAddUserTag = viewModel::onAddUserTag,
            onSaveAndAddCustomTag = viewModel::onSaveAndAddCustomTag,
            onDismiss = viewModel::onDismissTagPicker,
        )
    }

    // Add to collection sheet
    if (uiState.showAddSheet) {
        uiState.card?.let { card ->
            AddToCollectionSheet(
                cardName = card.name,
                onConfirm = { isFoil, isAltArt, condition, language, qty ->
                    viewModel.onAddToCollection(isFoil, isAltArt, condition, language, qty)
                },
                onDismiss = viewModel::onDismissAddSheet,
            )
        }
    }

    // Add to wishlist sheet
    if (uiState.showWishlistSheet) {
        uiState.card?.let { card ->
            AddToWishlistSheet(
                cardName = card.name,
                onConfirm = { isFoil, isAltArt, condition, language, qty ->
                    viewModel.onAddToWishlist(isFoil, isAltArt, condition, language, qty)
                },
                onDismiss = viewModel::onDismissWishlistSheet,
            )
        }
    }

    // Mark as tradeable sheet
    if (uiState.showTradeSheet) {
        MarkAsTradeableSheet(
            userCards = uiState.userCards,
            onConfirm = viewModel::onConfirmTradeSelection,
            onDismiss = viewModel::onDismissTradeSheet,
        )
    }

    // Delete confirmation
    uiState.cardToDelete?.let { uc ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissDeleteConfirm,
            title = { Text(stringResource(R.string.carddetail_delete_copy_title)) },
            text = { Text(stringResource(R.string.carddetail_delete_copy_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onDeleteCard(uc.id) }) {
                    Text(
                        stringResource(R.string.action_remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDeleteConfirm) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CardDetailContent(
    card: Card,
    userCards: List<UserCard>,
    isStale: Boolean,
    onRemoveAutoTag: (CardTag) -> Unit,
    onAddUserTag: (CardTag) -> Unit,
    onRemoveUserTag: (CardTag) -> Unit,
    onShowTagPicker: () -> Unit,
    onConfirmSuggestedTag: (CardTag) -> Unit,
    onDismissSuggestedTag: (CardTag) -> Unit,
    onShowAddSheet: () -> Unit,
    onShowWishlistSheet: () -> Unit,
    onShowTradeSheet: () -> Unit,
    onUpdateQuantity: (Long, Int) -> Unit,
    onRequestDelete: (UserCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var showBackFace by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Card image — tap to flip for DFC
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val imageUrl = if (showBackFace && card.imageBackNormal != null)
                card.imageBackNormal else card.imageNormal

            AsyncImage(
                model = imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .clip(MaterialTheme.shapes.medium)
                    .then(
                        if (card.imageBackNormal != null)
                            Modifier.clickable { showBackFace = !showBackFace }
                        else Modifier
                    ),
            )

            if (card.imageBackNormal != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text = if (showBackFace) "Tap to see front" else "Tap to flip",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        // Name + badges
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SetSymbol(
                setCode = card.setCode,
                rarity = CardRarity.fromString(card.rarity),
                size = 20.dp,
            )
            Text(card.name, style = MaterialTheme.typography.headlineSmall)
            if (isStale) StaleBadge()
        }

        // Mana cost + type
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            card.manaCost?.let {
                ManaCostImages(manaCost = it, symbolSize = 20.dp)
            }
            if (card.printedTypeLine.isNullOrEmpty()) {
                Text(
                    text = card.typeLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = card.printedTypeLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Oracle text
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            if (card.printedText.isNullOrEmpty()) {
                card.oracleText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            } else {
                Text(
                    text = card.printedText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }

        }

        // Flavor text
        card.flavorText?.let {
            Text(
                text = "\"$it\"",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Power/Toughness or Loyalty
        if (card.power != null && card.toughness != null) {
            Text(
                text = "${card.power}/${card.toughness}",
                style = MaterialTheme.typography.titleMedium,
            )
        } else card.loyalty?.let {
            Text("Loyalty: $it", style = MaterialTheme.typography.titleMedium)
        }

        HorizontalDivider()

        // Prices
        PriceSection(card = card)

        HorizontalDivider()

        // Collection section: copies list + add / wishlist / trade buttons
        CollectionSection(
            userCards = userCards,
            onShowAddSheet = onShowAddSheet,
            onShowWishlistSheet = onShowWishlistSheet,
            onShowTradeSheet = onShowTradeSheet,
            onUpdateQuantity = onUpdateQuantity,
            onRequestDelete = onRequestDelete,
        )

        HorizontalDivider()

        // Legalities
        LegalitySection(card = card)

        HorizontalDivider()

        // Tags
        TagsSection(
            autoTags = card.tags,
            userTags = card.userTags,
            isInCollection = userCards.isNotEmpty(),
            onRemoveAutoTag = onRemoveAutoTag,
            onRemoveUserTag = onRemoveUserTag,
            onShowTagPicker = onShowTagPicker,
        )

        // Suggested tags (confidence between suggest- and auto-thresholds)
        if (card.suggestedTags.isNotEmpty()) {
            SuggestedTagsSection(
                suggestions = card.suggestedTags,
                onConfirm = onConfirmSuggestedTag,
                onDismiss = onDismissSuggestedTag,
            )
        }

        // Scryfall link
        TextButton(onClick = { uriHandler.openUri(card.scryfallUri) }) {
            Icon(
                Icons.Default.OpenInBrowser,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.carddetail_view_scryfall))
        }

        // Extra bottom padding for FAB
        Spacer(Modifier.height(72.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Collection section: add button + list of existing copies
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CollectionSection(
    userCards: List<UserCard>,
    onShowAddSheet: () -> Unit,
    onShowWishlistSheet: () -> Unit,
    onShowTradeSheet: () -> Unit,
    onUpdateQuantity: (Long, Int) -> Unit,
    onRequestDelete: (UserCard) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        // Header: title + Wishlist + Add buttons

        Text(
            stringResource(R.string.carddetail_in_collection),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onShowWishlistSheet,
                modifier = Modifier.height(32.dp),
            ) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.carddetail_add_to_wishlist),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            OutlinedButton(
                onClick = onShowAddSheet,
                modifier = Modifier.height(32.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    stringResource(R.string.carddetail_add_copy),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }


    if (userCards.isEmpty()) {
        Text(
            text = stringResource(R.string.carddetail_no_copies),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        userCards.forEach { uc ->
            CollectionCopyRow(
                userCard = uc,
                onUpdateQuantity = onUpdateQuantity,
                onRequestDelete = onRequestDelete,
            )
        }

        // "Offer for trade" button — only shown when there are collection copies
        OutlinedButton(
            onClick = onShowTradeSheet,
            modifier = Modifier.height(32.dp),
        ) {
            Icon(
                Icons.Default.SwapHoriz,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.carddetail_offer_for_trade),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}


@Composable
private fun CollectionCopyRow(
    userCard: UserCard,
    onUpdateQuantity: (Long, Int) -> Unit,
    onRequestDelete: (UserCard) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Badges row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CopyBadge(label = userCard.language.uppercase())
                CopyBadge(label = userCard.condition)
                if (userCard.isFoil) FoilBadge()
                if (userCard.isAlternativeArt) {
                    CopyBadge(label = stringResource(R.string.carddetail_alternative_art_short))
                }
                if (userCard.isForTrade) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text = stringResource(R.string.carddetail_for_trade_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Quantity stepper + delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.carddetail_quantity_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onUpdateQuantity(userCard.id, userCard.quantity - 1) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = stringResource(R.string.action_remove),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = "${userCard.quantity}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(
                    onClick = { onUpdateQuantity(userCard.id, userCard.quantity + 1) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { onRequestDelete(userCard) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CopyBadge(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Add to collection bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToCollectionSheet(
    cardName: String,
    onConfirm: (isFoil: Boolean, isAlternativeArt: Boolean, condition: String, language: String, qty: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val conditions = listOf("NM", "LP", "MP", "HP", "DMG")
    val languages = listOf("en", "es", "de", "fr", "it", "pt", "ja", "ko", "ru")

    var isFoil by remember { mutableStateOf(false) }
    var isAlternativeArt by remember { mutableStateOf(false) }
    var condition by remember { mutableStateOf("NM") }
    var language by remember { mutableStateOf("en") }
    var qty by remember { mutableIntStateOf(1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.carddetail_add_copy),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = cardName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Foil toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.addcard_confirm_foil),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = isFoil, onCheckedChange = { isFoil = it })
            }

            // Alternative art toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.carddetail_alternative_art),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = isAlternativeArt, onCheckedChange = { isAlternativeArt = it })
            }

            // Quantity stepper
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.addcard_confirm_quantity),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = { if (qty > 1) qty-- }) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = stringResource(R.string.action_remove)
                    )
                }
                Text("$qty", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { qty++ }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add)
                    )
                }
            }

            // Condition chips
            Text(
                stringResource(R.string.addcard_confirm_condition),
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                conditions.forEach { c ->
                    FilterChip(
                        selected = c == condition,
                        onClick = { condition = c },
                        label = { Text(c) },
                    )
                }
            }

            // Language dropdown
            var langExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = it },
            ) {
                OutlinedTextField(
                    value = language.uppercase(),
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text(
                            stringResource(R.string.addcard_confirm_language),
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false },
                ) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.uppercase()) },
                            onClick = { language = lang; langExpanded = false },
                        )
                    }
                }
            }

            // Confirm / Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = {
                    onConfirm(
                        isFoil,
                        isAlternativeArt,
                        condition,
                        language,
                        qty
                    )
                }) {
                    Text(stringResource(R.string.action_add))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Add to wishlist bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToWishlistSheet(
    cardName: String,
    onConfirm: (isFoil: Boolean, isAlternativeArt: Boolean, condition: String, language: String, qty: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val conditions = listOf("NM", "LP", "MP", "HP", "DMG")
    val languages = listOf("en", "es", "de", "fr", "it", "pt", "ja", "ko", "ru")

    var isFoil by remember { mutableStateOf(false) }
    var isAlternativeArt by remember { mutableStateOf(false) }
    var condition by remember { mutableStateOf("NM") }
    var language by remember { mutableStateOf("en") }
    var qty by remember { mutableIntStateOf(1) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.carddetail_wishlist_sheet_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = cardName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Foil toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.addcard_confirm_foil),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = isFoil, onCheckedChange = { isFoil = it })
            }

            // Alternative art toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.carddetail_alternative_art),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Switch(checked = isAlternativeArt, onCheckedChange = { isAlternativeArt = it })
            }

            // Quantity stepper
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.addcard_confirm_quantity),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                IconButton(onClick = { if (qty > 1) qty-- }) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = stringResource(R.string.action_remove)
                    )
                }
                Text("$qty", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { qty++ }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add)
                    )
                }
            }

            // Condition chips
            Text(
                stringResource(R.string.addcard_confirm_condition),
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                conditions.forEach { c ->
                    FilterChip(
                        selected = c == condition,
                        onClick = { condition = c },
                        label = { Text(c) },
                    )
                }
            }

            // Language dropdown
            var langExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = langExpanded,
                onExpandedChange = { langExpanded = it },
            ) {
                OutlinedTextField(
                    value = language.uppercase(),
                    onValueChange = {},
                    readOnly = true,
                    label = {
                        Text(
                            stringResource(R.string.addcard_confirm_language),
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = langExpanded,
                    onDismissRequest = { langExpanded = false },
                ) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = { Text(lang.uppercase()) },
                            onClick = { language = lang; langExpanded = false },
                        )
                    }
                }
            }

            // Confirm / Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = {
                    onConfirm(
                        isFoil,
                        isAlternativeArt,
                        condition,
                        language,
                        qty
                    )
                }) {
                    Text(stringResource(R.string.action_add))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mark as tradeable bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkAsTradeableSheet(
    userCards: List<UserCard>,
    onConfirm: (Map<Long, Boolean>) -> Unit,
    onDismiss: () -> Unit,
) {
    val tradeState = remember(userCards) {
        mutableStateMapOf<Long, Boolean>().also { map ->
            userCards.forEach { map[it.id] = it.isForTrade }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.carddetail_trade_sheet_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.carddetail_trade_sheet_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (userCards.isEmpty()) {
                Text(
                    text = stringResource(R.string.carddetail_no_copies_for_trade),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                userCards.forEach { uc ->
                    val checked = tradeState[uc.id] ?: false
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tradeState[uc.id] = !checked },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { tradeState[uc.id] = it },
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CopyBadge(label = uc.language.uppercase())
                                CopyBadge(label = uc.condition)
                                if (uc.isFoil) FoilBadge()
                                if (uc.isAlternativeArt) {
                                    CopyBadge(label = stringResource(R.string.carddetail_alternative_art_short))
                                }
                            }
                            Text(
                                text = "×${uc.quantity}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // Confirm / Cancel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(
                    onClick = { onConfirm(tradeState.toMap()) },
                    enabled = userCards.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PriceSection(card: Card) {
    val preferredCurrency = LocalPreferredCurrency.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.carddetail_market_prices),
            style = MaterialTheme.typography.titleSmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PricePill(
                label = stringResource(R.string.carddetail_price_foil),
                price = if (preferredCurrency == PreferredCurrency.EUR) card.priceEurFoil else card.priceUsdFoil,
                currency = preferredCurrency
            )
            PricePill(
                label = stringResource(R.string.carddetail_price_normal),
                price = if (preferredCurrency == PreferredCurrency.EUR) card.priceEur else card.priceUsd,
                currency = preferredCurrency
            )
        }
    }
}

@Composable
private fun PricePill(label: String, price: Double?, currency: PreferredCurrency) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = PriceFormatter.format(price, currency),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun LegalitySection(card: Card) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.carddetail_legality),
            style = MaterialTheme.typography.titleSmall
        )
        val formats = listOf(
            "Standard" to card.legalityStandard,
            "Pioneer" to card.legalityPioneer,
            "Modern" to card.legalityModern,
            "Commander" to card.legalityCommander,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            formats.forEach { (format, legality) ->
                LegalityChip(format = format, legality = legality)
            }
        }
    }
}

@Composable
private fun LegalityChip(format: String, legality: String) {
    val isLegal = legality == "legal"
    Surface(
        color = if (isLegal) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(format, style = MaterialTheme.typography.labelSmall)
            Text(
                text = if (isLegal) "Legal" else "Not legal",
                style = MaterialTheme.typography.labelSmall,
                color = if (isLegal) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tags section
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsSection(
    autoTags: List<CardTag>,
    userTags: List<CardTag>,
    isInCollection: Boolean,
    onRemoveAutoTag: (CardTag) -> Unit,
    onRemoveUserTag: (CardTag) -> Unit,
    onShowTagPicker: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }
    val hasAnyTag = autoTags.isNotEmpty() || (isInCollection && userTags.isNotEmpty())

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }

        if (expanded) {
            // ── Auto-generated tags ──────────────────────────────────────────
            if (autoTags.isNotEmpty()) {
                Text(
                    text = "Etiquetas automáticas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    autoTags.forEach { tag ->
                        if (isInCollection) {
                            InputChip(
                                selected = true,
                                onClick = { onRemoveAutoTag(tag) },
                                label = {
                                    Text(
                                        tag.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Quitar ${tag.label}",
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                            )
                        } else {
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(
                                        tag.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // ── User tags (only when in collection) ──────────────────────────
            if (isInCollection) {
                if (autoTags.isNotEmpty() || userTags.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                }
                Text(
                    text = "Mis etiquetas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (userTags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        userTags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { onRemoveUserTag(tag) },
                                label = {
                                    Text(
                                        tag.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Quitar ${tag.label}",
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Sin etiquetas personales — toca + para añadir",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onShowTagPicker,
                    modifier = Modifier.height(32.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Añadir etiqueta", style = MaterialTheme.typography.labelSmall)
                }
            } else if (!hasAnyTag) {
                Text(
                    text = "Sin etiquetas automáticas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Suggested tags section — chips with confirm (✓) / dismiss (✕)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SuggestedTagsSection(
    suggestions: List<SuggestedTag>,
    onConfirm: (CardTag) -> Unit,
    onDismiss: (CardTag) -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Etiquetas sugeridas",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }

        if (expanded) {
            Text(
                text = "Confirma las que apliquen o descártalas. No se añaden hasta que tú las apruebes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            suggestions.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowItems.forEach { sug ->
                        SuggestedTagChip(
                            suggestion = sug,
                            onConfirm = { onConfirm(sug.tag) },
                            onDismiss = { onDismiss(sug.tag) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedTagChip(
    suggestion: SuggestedTag,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pct = (suggestion.confidence * 100).toInt()
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = "${suggestion.tag.label}  $pct%",
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = onConfirm, modifier = Modifier.size(20.dp)) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Confirmar ${suggestion.tag.label}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Descartar ${suggestion.tag.label}",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tag picker bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

private data class TagItem(val key: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TagPickerSheet(
    cardAutoTags: List<CardTag>,
    cardSuggestedTags: List<SuggestedTag>,
    currentUserTags: List<CardTag>,
    userDefinedTags: List<UserDefinedTag>,
    onAddUserTag: (CardTag) -> Unit,
    onSaveAndAddCustomTag: (label: String, categoryKey: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val userTagKeys = currentUserTags.map { it.key }.toSet()

    // Built-in categories (excluding CUSTOM which is the fallback for raw custom tags)
    val builtInCategories = TagCategory.entries.filter { it != TagCategory.CUSTOM }

    // User-created category keys that don't map to built-in categories
    val userCustomCategoryKeys = userDefinedTags
        .map { it.categoryKey }
        .filter { key -> builtInCategories.none { it.name == key } }
        .distinct()

    // ── Custom tag creator state ──────────────────────────────────────────────
    var customLabel by remember { mutableStateOf("") }
    var selectedCategoryKey by remember { mutableStateOf(TagCategory.STRATEGY.name) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        LazyColumn(
            modifier = Modifier.navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // ── Header ──────────────────────────────────────────────────────
            item {
                Text("Añadir etiqueta", style = MaterialTheme.typography.titleMedium)
            }

            // ── Custom tag creator ───────────────────────────────────────────
            item {
                CustomTagCreatorSection(
                    label = customLabel,
                    onLabelChange = { customLabel = it },
                    selectedCategoryKey = selectedCategoryKey,
                    onCategorySelected = { selectedCategoryKey = it },
                    builtInCategories = builtInCategories,
                    userCustomCategoryKeys = userCustomCategoryKeys,
                    onNewCategoryClick = { showNewCategoryDialog = true },
                    onAdd = {
                        if (customLabel.isNotBlank()) {
                            onSaveAndAddCustomTag(customLabel, selectedCategoryKey)
                            customLabel = ""
                        }
                    },
                )
            }

            // ── Auto-generated tags for this card ────────────────────────────
            if (cardAutoTags.isNotEmpty()) {
                item {
                    TagPickerSection(
                        title = "Auto-generadas para esta carta",
                        tags = cardAutoTags.map { TagItem(it.key, it.label) },
                        onAdd = { key ->
                            val tag = cardAutoTags.find { it.key == key } ?: return@TagPickerSection
                            onAddUserTag(tag); onDismiss()
                        },
                    )
                }
            }

            // ── Suggested tags for this card ─────────────────────────────────
            val availableSuggestions = cardSuggestedTags.filter { it.tag.key !in userTagKeys }
            if (availableSuggestions.isNotEmpty()) {
                item {
                    TagPickerSection(
                        title = "Sugeridas para esta carta",
                        tags = availableSuggestions.map { sug ->
                            TagItem(
                                sug.tag.key,
                                "${sug.tag.label}  ${(sug.confidence * 100).toInt()}%"
                            )
                        },
                        onAdd = { key ->
                            val tag = availableSuggestions.find { it.tag.key == key }?.tag
                                ?: return@TagPickerSection
                            onAddUserTag(tag); onDismiss()
                        },
                    )
                }
            }

            // ── Built-in categories ──────────────────────────────────────────
            builtInCategories.forEach { category ->
                val canonical =
                    CardTag.canonical.filter { it.category == category && it.key !in userTagKeys }
                val userDefined =
                    userDefinedTags.filter { it.categoryKey == category.name && it.key !in userTagKeys }
                val items = canonical.map { TagItem(it.key, it.label) } +
                        userDefined.map { TagItem(it.key, it.label) }
                if (items.isNotEmpty()) {
                    item(key = "cat_${category.name}") {
                        TagPickerSection(
                            title = category.name,
                            tags = items,
                            onAdd = { key ->
                                val tag = canonical.find { it.key == key }
                                    ?: CardTag(key, category)
                                onAddUserTag(tag); onDismiss()
                            },
                        )
                    }
                }
            }

            // ── User custom categories ────────────────────────────────────────
            userCustomCategoryKeys.forEach { categoryKey ->
                val items = userDefinedTags
                    .filter { it.categoryKey == categoryKey && it.key !in userTagKeys }
                    .map { TagItem(it.key, it.label) }
                if (items.isNotEmpty()) {
                    item(key = "custom_$categoryKey") {
                        TagPickerSection(
                            title = categoryKey,
                            tags = items,
                            onAdd = { key ->
                                onAddUserTag(CardTag(key, TagCategory.CUSTOM)); onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }

    if (showNewCategoryDialog) {
        NewCategoryDialog(
            onDismiss = { showNewCategoryDialog = false },
            onConfirm = { name ->
                if (name.isNotBlank()) selectedCategoryKey = name.trim()
                showNewCategoryDialog = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomTagCreatorSection(
    label: String,
    onLabelChange: (String) -> Unit,
    selectedCategoryKey: String,
    onCategorySelected: (String) -> Unit,
    builtInCategories: List<TagCategory>,
    userCustomCategoryKeys: List<String>,
    onNewCategoryClick: () -> Unit,
    onAdd: () -> Unit,
) {
    val allCategories: List<String> = builtInCategories.map { it.name } + userCustomCategoryKeys

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Nueva etiqueta personalizada",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = label,
                onValueChange = onLabelChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nombre de la etiqueta…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAdd() }),
            )

            Text(
                "Tipo:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Horizontally scrollable category chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(allCategories) { cat ->
                    FilterChip(
                        selected = cat == selectedCategoryKey,
                        onClick = { onCategorySelected(cat) },
                        label = { Text(cat, style = MaterialTheme.typography.labelSmall) },
                    )
                }
                item {
                    SuggestionChip(
                        onClick = onNewCategoryClick,
                        label = { Text("+ Nueva", style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            Button(
                onClick = onAdd,
                enabled = label.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Añadir")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPickerSection(
    title: String,
    tags: List<TagItem>,
    onAdd: (key: String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tags.forEach { tag ->
                SuggestionChip(
                    onClick = { onAdd(tag.key) },
                    label = { Text(tag.label, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

@Composable
private fun NewCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva categoría") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Nombre de la categoría…") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Crear")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
