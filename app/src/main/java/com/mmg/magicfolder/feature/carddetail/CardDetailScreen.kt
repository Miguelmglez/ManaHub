package com.mmg.magicfolder.feature.carddetail


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.CardTag
import com.mmg.magicfolder.core.domain.model.TagCategory
import com.mmg.magicfolder.core.domain.model.UserCard
import com.mmg.magicfolder.core.ui.components.CardRarity
import com.mmg.magicfolder.core.ui.components.FoilBadge
import com.mmg.magicfolder.core.ui.components.ManaCostImages
import com.mmg.magicfolder.core.ui.components.SetSymbol
import com.mmg.magicfolder.core.ui.components.StaleBadge
import com.mmg.magicfolder.core.util.CardTypeTranslator
import com.mmg.magicfolder.core.util.PriceFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    onBack:               () -> Unit,
    onNavigateToAddCard:  () -> Unit,
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
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Text(
                        text = uiState.card?.name ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

    ) { padding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.card != null -> CardDetailContent(
                card             = uiState.card!!,
                userCards        = uiState.userCards,
                isStale          = uiState.isStale,
                onAddTag         = viewModel::onAddTag,
                onRemoveTag      = viewModel::onRemoveTag,
                onShowTagPicker  = viewModel::onShowTagPicker,
                onShowAddSheet   = viewModel::onShowAddSheet,
                onUpdateQuantity = { id, qty -> viewModel.onUpdateQuantity(id, qty) },
                onRequestDelete  = viewModel::onRequestDelete,
                modifier         = Modifier.padding(padding),
            )
        }
    }

    // Tag picker sheet
    if (uiState.showTagPicker) {
        val currentTags = uiState.card?.tags ?: emptyList()
        TagPickerSheet(
            currentTags = currentTags,
            onAddTag    = viewModel::onAddTag,
            onDismiss   = viewModel::onDismissTagPicker,
        )
    }

    // Add to collection sheet
    if (uiState.showAddSheet) {
        uiState.card?.let { card ->
            AddToCollectionSheet(
                cardName  = card.name,
                onConfirm = { isFoil, isAltArt, condition, language, qty ->
                    viewModel.onAddToCollection(isFoil, isAltArt, condition, language, qty)
                },
                onDismiss = viewModel::onDismissAddSheet,
            )
        }
    }

    // Delete confirmation
    uiState.cardToDelete?.let { uc ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissDeleteConfirm,
            title   = { Text(stringResource(R.string.carddetail_delete_copy_title)) },
            text    = { Text(stringResource(R.string.carddetail_delete_copy_message)) },
            confirmButton = {
                TextButton(onClick = { viewModel.onDeleteCard(uc.id) }) {
                    Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
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
    card:             Card,
    userCards:        List<UserCard>,
    isStale:          Boolean,
    onAddTag:         (CardTag) -> Unit,
    onRemoveTag:      (CardTag) -> Unit,
    onShowTagPicker:  () -> Unit,
    onShowAddSheet:   () -> Unit,
    onUpdateQuantity: (Long, Int) -> Unit,
    onRequestDelete:  (UserCard) -> Unit,
    modifier:         Modifier = Modifier,
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
            modifier          = Modifier.fillMaxWidth(),
            contentAlignment  = Alignment.Center,
        ) {
            val imageUrl = if (showBackFace && card.imageBackNormal != null)
                card.imageBackNormal else card.imageNormal

            AsyncImage(
                model              = imageUrl,
                contentDescription = card.name,
                contentScale       = ContentScale.FillWidth,
                modifier           = Modifier
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
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    color    = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape    = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text     = if (showBackFace) "Tap to see front" else "Tap to flip",
                        style    = MaterialTheme.typography.labelSmall,
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
                rarity  = CardRarity.fromString(card.rarity),
                size    = 20.dp,
            )
            Text(card.name, style = MaterialTheme.typography.headlineSmall)
            if (isStale) StaleBadge()
        }

        // Mana cost + type
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            card.manaCost?.let {
                ManaCostImages(manaCost = it, symbolSize = 20.dp)
            }
            Text(
                CardTypeTranslator.translateTypeLine(card.printedTypeLine ?: card.typeLine),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Oracle text
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            card.printedText?.let {
                Text(
                    text     = it,
                    style    = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        // Flavor text
        card.flavorText?.let {
            Text(
                text      = "\"$it\"",
                style     = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Power/Toughness or Loyalty
        if (card.power != null && card.toughness != null) {
            Text(
                text  = "${card.power}/${card.toughness}",
                style = MaterialTheme.typography.titleMedium,
            )
        } else card.loyalty?.let {
            Text("Loyalty: $it", style = MaterialTheme.typography.titleMedium)
        }

        HorizontalDivider()

        // Prices
        PriceSection(card = card)

        HorizontalDivider()

        // Collection section: copies list + add button
        CollectionSection(
            userCards        = userCards,
            onShowAddSheet   = onShowAddSheet,
            onUpdateQuantity = onUpdateQuantity,
            onRequestDelete  = onRequestDelete,
        )

        HorizontalDivider()

        // Legalities
        LegalitySection(card = card)

        HorizontalDivider()

        // Tags
        TagsSection(
            tags            = card.tags,
            onRemoveTag     = onRemoveTag,
            onShowTagPicker = onShowTagPicker,
        )

        // Scryfall link
        TextButton(onClick = { uriHandler.openUri(card.scryfallUri) }) {
            Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(20.dp))
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
    userCards:        List<UserCard>,
    onShowAddSheet:   () -> Unit,
    onUpdateQuantity: (Long, Int) -> Unit,
    onRequestDelete:  (UserCard) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.carddetail_in_collection),
                style = MaterialTheme.typography.titleSmall,
            )
            OutlinedButton(
                onClick  = onShowAddSheet,
                modifier = Modifier.height(32.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.carddetail_add_copy), style = MaterialTheme.typography.labelSmall)
            }
        }

        if (userCards.isEmpty()) {
            Text(
                text  = stringResource(R.string.carddetail_no_copies),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            userCards.forEach { uc ->
                CollectionCopyRow(
                    userCard         = uc,
                    onUpdateQuantity = onUpdateQuantity,
                    onRequestDelete  = onRequestDelete,
                )
            }
        }
    }
}

@Composable
private fun CollectionCopyRow(
    userCard:         UserCard,
    onUpdateQuantity: (Long, Int) -> Unit,
    onRequestDelete:  (UserCard) -> Unit,
) {
    Surface(
        color  = MaterialTheme.colorScheme.surfaceVariant,
        shape  = MaterialTheme.shapes.small,
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
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                CopyBadge(label = userCard.language.uppercase())
                CopyBadge(label = userCard.condition)
                if (userCard.isFoil) FoilBadge()
                if (userCard.isAlternativeArt) {
                    CopyBadge(label = stringResource(R.string.carddetail_alternative_art_short))
                }
            }

            // Quantity stepper + delete
            Row(
                modifier          = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.carddetail_quantity_label),
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick  = { onUpdateQuantity(userCard.id, userCard.quantity - 1) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = stringResource(R.string.action_remove),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text  = "${userCard.quantity}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(
                    onClick  = { onUpdateQuantity(userCard.id, userCard.quantity + 1) },
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
                    onClick  = { onRequestDelete(userCard) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint     = MaterialTheme.colorScheme.error,
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
            text     = label,
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onSecondaryContainer,
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
    cardName:  String,
    onConfirm: (isFoil: Boolean, isAlternativeArt: Boolean, condition: String, language: String, qty: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val conditions = listOf("NM", "LP", "MP", "HP", "DMG")
    val languages  = listOf("en", "es", "de", "fr", "it", "pt", "ja", "ko", "ru")

    var isFoil           by remember { mutableStateOf(false) }
    var isAlternativeArt by remember { mutableStateOf(false) }
    var condition        by remember { mutableStateOf("NM") }
    var language         by remember { mutableStateOf("en") }
    var qty              by remember { mutableIntStateOf(1) }

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
                text  = stringResource(R.string.carddetail_add_copy),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text  = cardName,
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
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_remove))
                }
                Text("$qty", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { qty++ }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                }
            }

            // Condition chips
            Text(stringResource(R.string.addcard_confirm_condition), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                conditions.forEach { c ->
                    FilterChip(
                        selected = c == condition,
                        onClick  = { condition = c },
                        label    = { Text(c) },
                    )
                }
            }

            // Language dropdown
            var langExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded        = langExpanded,
                onExpandedChange = { langExpanded = it },
            ) {
                OutlinedTextField(
                    value         = language.uppercase(),
                    onValueChange = {},
                    readOnly      = true,
                    label         = {
                        Text(stringResource(R.string.addcard_confirm_language),
                            style = MaterialTheme.typography.labelLarge)
                    },
                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) },
                    modifier      = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded          = langExpanded,
                    onDismissRequest  = { langExpanded = false },
                ) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text    = { Text(lang.uppercase()) },
                            onClick = { language = lang; langExpanded = false },
                        )
                    }
                }
            }

            // Confirm / Cancel
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = { onConfirm(isFoil, isAlternativeArt, condition, language, qty) }) {
                    Text(stringResource(R.string.action_add))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PriceSection(card: Card) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.carddetail_market_prices), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PricePill(label = stringResource(R.string.carddetail_price_foil_eur), price = card.priceEurFoil, currency = "€")
            PricePill(label = stringResource(R.string.carddetail_price_foil_usd), price = card.priceUsdFoil, currency = "$")
            PricePill(label = stringResource(R.string.carddetail_price_eur), price = card.priceEur, currency = "€")
            PricePill(label = stringResource(R.string.carddetail_price_usd), price = card.priceUsd, currency = "$")
        }
    }
}

@Composable
private fun PricePill(label: String, price: Double?, currency: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text  = PriceFormatter.format(price, currency),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun LegalitySection(card: Card) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.carddetail_legality), style = MaterialTheme.typography.titleSmall)
        val formats = listOf(
            "Standard"  to card.legalityStandard,
            "Pioneer"   to card.legalityPioneer,
            "Modern"    to card.legalityModern,
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
                text  = if (isLegal) "Legal" else "Not legal",
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

@Composable
private fun TagsSection(
    tags:            List<CardTag>,
    onRemoveTag:     (CardTag) -> Unit,
    onShowTagPicker: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text     = "Tags",
                style    = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector        = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
            )
        }

        if (expanded) {
            if (tags.isEmpty()) {
                Text(
                    text  = "Sin etiquetas — toca + para añadir",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                tags.chunked(3).forEach { rowTags ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowTags.forEach { tag ->
                            InputChip(
                                selected     = true,
                                onClick      = { onRemoveTag(tag) },
                                label        = { Text(tag.label, style = MaterialTheme.typography.labelSmall) },
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
                }
            }

            OutlinedButton(
                onClick  = onShowTagPicker,
                modifier = Modifier.height(32.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Añadir etiqueta", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tag picker bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPickerSheet(
    currentTags: List<CardTag>,
    onAddTag:    (CardTag) -> Unit,
    onDismiss:   () -> Unit,
) {
    val categories = TagCategory.entries

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text("Añadir etiqueta", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            LazyColumn(
                contentPadding      = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                categories.forEach { category ->
                    val tagsInCategory = CardTag.entries.filter {
                        it.category == category && it !in currentTags
                    }
                    if (tagsInCategory.isNotEmpty()) {
                        item(key = category.name) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text  = category.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                tagsInCategory.chunked(3).forEach { rowTags ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        rowTags.forEach { tag ->
                                            SuggestionChip(
                                                onClick = { onAddTag(tag); onDismiss() },
                                                label   = {
                                                    Text(tag.label, style = MaterialTheme.typography.labelSmall)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
