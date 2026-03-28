package com.mmg.magicfolder.feature.carddetail


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    onBack:    () -> Unit,
    viewModel: CardDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.card?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    uiState.userCard?.let {
                        IconButton(onClick = viewModel::onShowEditDialog) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = viewModel::onShowDeleteConfirm) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.card != null -> CardDetailContent(
                card          = uiState.card!!,
                userCard      = uiState.userCard,
                isStale       = uiState.isStale,
                onAddTag      = viewModel::onAddTag,
                onRemoveTag   = viewModel::onRemoveTag,
                onShowTagPicker = viewModel::onShowTagPicker,
                modifier      = Modifier.padding(padding),
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

    // Edit bottom sheet
    if (uiState.showEditDialog) {
        uiState.userCard?.let { uc ->
            EditCardSheet(
                userCard       = uc,
                onDismiss      = viewModel::onDismissEditDialog,
                onUpdateQty    = { viewModel.onUpdateQuantity(uc.id, it) },
                onUpdateCond   = { viewModel.onUpdateCondition(uc, it) },
            )
        }
    }

    // Delete confirmation
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissDeleteConfirm,
            title   = { Text(stringResource(R.string.carddetail_delete_title)) },
            text    = { Text(stringResource(R.string.carddetail_delete_message, uiState.card?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = { viewModel.onDeleteCard(uiState.userCard!!.id) }) {
                    Text(stringResource(R.string.action_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissDeleteConfirm) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun CardDetailContent(
    card:            Card,
    userCard:        UserCard?,
    isStale:         Boolean,
    onAddTag:        (CardTag) -> Unit,
    onRemoveTag:     (CardTag) -> Unit,
    onShowTagPicker: () -> Unit,
    modifier:        Modifier = Modifier,
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

            // DFC flip hint
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
            if (userCard?.isFoil == true) FoilBadge()
            if (isStale) StaleBadge()
        }

        // Mana cost + type
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            card.manaCost?.let {
                ManaCostImages(manaCost = it, symbolSize = 20.dp)
            }
            Text(card.typeLine, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Oracle text
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            card.oracleText?.let {
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
        PriceSection(card = card, userCard = userCard)

        HorizontalDivider()

        // Collection info
        userCard?.let { uc ->
            CollectionInfoSection(userCard = uc)
        }

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
            Icon(Icons.Default.OpenInBrowser, contentDescription = null,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.carddetail_view_scryfall))
        }
    }
}

@Composable
private fun PriceSection(card: Card, userCard: UserCard?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.carddetail_market_prices), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PricePill(label = stringResource(R.string.carddetail_price_regular), price = card.priceUsd, currency = "$")
            PricePill(label = stringResource(R.string.carddetail_price_foil),    price = card.priceUsdFoil, currency = "$")
            PricePill(label = stringResource(R.string.carddetail_price_eur),     price = card.priceEur, currency = "€")
        }
    }
}

@Composable
private fun PricePill(label: String, price: Double?, currency: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text  = if (price != null) "$currency${String.format("%.2f", price)}" else "—",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun CollectionInfoSection(userCard: UserCard) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.carddetail_in_collection), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            InfoPill(label = stringResource(R.string.carddetail_quantity_label),  value = "×${userCard.quantity}")
            InfoPill(label = stringResource(R.string.carddetail_condition), value = userCard.condition)
            InfoPill(label = stringResource(R.string.carddetail_language),  value = userCard.language.uppercase())
        }
    }
}

@Composable
private fun InfoPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
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
//  Tags section (expandable) in CardDetailContent
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TagsSection(
    tags:            List<CardTag>,
    onRemoveTag:     (CardTag) -> Unit,
    onShowTagPicker: () -> Unit,
) {
    var expanded by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header row
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
                // Wrap chips using a simple flow-like column of rows
                tags.chunked(3).forEach { rowTags ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowTags.forEach { tag ->
                            InputChip(
                                selected        = true,
                                onClick         = { onRemoveTag(tag) },
                                label           = { Text(tag.label, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon    = {
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCardSheet(
    userCard:     UserCard,
    onDismiss:    () -> Unit,
    onUpdateQty:  (Int) -> Unit,
    onUpdateCond: (String) -> Unit,
) {
    val conditions = listOf("NM", "LP", "MP", "HP", "DMG")
    var qty by remember { mutableIntStateOf(userCard.quantity) }
    var cond by remember { mutableStateOf(userCard.condition) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.carddetail_edit_title), style = MaterialTheme.typography.titleMedium)

            // Quantity stepper
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.carddetail_quantity_label), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { if (qty > 1) qty-- }) {
                    Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_remove))
                }
                Text("$qty", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { qty++ }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                }
            }

            // Condition selector
            Text(stringResource(R.string.carddetail_condition), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                conditions.forEach { c ->
                    FilterChip(
                        selected = c == cond,
                        onClick  = { cond = c },
                        label    = { Text(c) },
                    )
                }
            }

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = {
                    onUpdateQty(qty)
                    onUpdateCond(cond)
                    onDismiss()
                }) { Text(stringResource(R.string.action_save)) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}