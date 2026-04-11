package com.mmg.magicfolder.feature.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import com.mmg.magicfolder.core.ui.theme.LocalPreferredCurrency
import com.mmg.magicfolder.core.util.PriceFormatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import android.content.Intent
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.magicfolder.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.Deck
import com.mmg.magicfolder.core.ui.components.ManaCostImages
import com.mmg.magicfolder.core.ui.components.ManaSymbolImage
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.decks.components.DeckImportSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    deckId:      Long,
    onBack:      () -> Unit,
    onAddCards:  () -> Unit,
    viewModel:   DeckDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc      = MaterialTheme.magicColors
    val ty      = MaterialTheme.magicTypography
    val context = LocalContext.current
    var showAddCardsSheet  by remember { mutableStateOf(false) }
    var showCoverPicker    by remember { mutableStateOf(false) }
    var showImportSheet    by remember { mutableStateOf(false) }
    var showOverflowMenu   by remember { mutableStateOf(false) }
    var selectedCardId     by remember { mutableStateOf<String?>(null) }

    // Keep the selected card detail in sync with deck state changes
    val selectedDeckCard = remember(selectedCardId, uiState.cards) {
        selectedCardId?.let { id -> uiState.cards.find { it.scryfallId == id } }
    }

    Scaffold(
        containerColor = mc.background,
        topBar = {
            Surface(color = mc.backgroundSecondary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint               = mc.textSecondary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            text     = uiState.deck?.name ?: "Deck",
                            style    = ty.titleMedium,
                            color    = mc.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        uiState.deck?.format?.let { fmt ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = mc.goldMtg.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text     = fmt.uppercase(),
                                    style    = ty.labelSmall,
                                    color    = mc.goldMtg,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    // Cover image picker button — only shown when deck has cards with art
                    if (uiState.cards.any { it.card?.imageArtCrop != null }) {
                        IconButton(onClick = { showCoverPicker = true }) {
                            Icon(
                                imageVector        = Icons.Default.Edit,
                                contentDescription = "Change cover image",
                                tint               = mc.textSecondary,
                            )
                        }
                    }
                    // Overflow menu: Share + Import
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector        = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint               = mc.textSecondary,
                            )
                        }
                        DropdownMenu(
                            expanded         = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text    = { Text(stringResource(R.string.deck_import_title), style = ty.bodyMedium) },
                                onClick = {
                                    showOverflowMenu = false
                                    showImportSheet  = true
                                },
                            )
                            DropdownMenuItem(
                                text    = { Text(stringResource(R.string.action_share), style = ty.bodyMedium) },
                                onClick = {
                                    showOverflowMenu = false
                                    val text = viewModel.exportDeckToText()
                                    if (text != null) {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type    = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, text)
                                            putExtra(Intent.EXTRA_SUBJECT, uiState.deck?.name ?: "Deck")
                                        }
                                        context.startActivity(
                                            Intent.createChooser(intent, context.getString(R.string.action_share))
                                        )
                                    }
                                },
                                enabled = uiState.cards.isNotEmpty(),
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = {
                    viewModel.showCollectionCards()
                    showAddCardsSheet = true
                },
                containerColor = mc.primaryAccent,
                contentColor   = mc.background,
                shape          = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.deckbuilder_add_cards))
            }
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }

            uiState.cards.isEmpty() -> Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = stringResource(R.string.deckbuilder_deck_empty),
                    style = ty.titleMedium,
                    color = mc.textSecondary,
                )
            }

            else -> DeckContent(
                uiState         = uiState,
                onRemove        = viewModel::removeCard,
                onCardClick     = { id -> selectedCardId = id },
                modifier        = Modifier.padding(padding),
            )
        }
    }

    // ── Cover image picker ────────────────────────────────────────────────────
    if (showCoverPicker) {
        CoverPickerSheet(
            cards     = uiState.cards.filter { it.card?.imageArtCrop != null },
            currentCoverId = uiState.deck?.coverCardId,
            onSelect  = { scryfallId ->
                viewModel.setCoverCard(scryfallId)
                showCoverPicker = false
            },
            onDismiss = { showCoverPicker = false },
        )
    }

    // Close import sheet automatically once import finishes without error
    LaunchedEffect(uiState.isImporting, uiState.importError) {
        if (showImportSheet && !uiState.isImporting && uiState.importError == null) {
            showImportSheet = false
        }
    }

    // ── Import cards ModalBottomSheet ────────────────────────────────────────
    if (showImportSheet) {
        DeckImportSheet(
            isLoading = uiState.isImporting,
            error     = uiState.importError,
            onImport  = { text -> viewModel.importCardsFromText(text) },
            onDismiss = {
                showImportSheet = false
                viewModel.clearImportError()
            },
        )
    }

    // ── Add cards ModalBottomSheet ────────────────────────────────────────────
    if (showAddCardsSheet) {
        AddCardsSheet(
            uiState       = uiState,
            format        = viewModel.deckFormat,
            onQueryChange = viewModel::onAddCardsQueryChange,
            onAdd         = viewModel::addCardToDeck,
            onRemove      = viewModel::removeCardFromDeck,
            onAddBasicLand    = viewModel::addBasicLandByName,
            onRemoveBasicLand = viewModel::removeBasicLandByName,
            viewModel = viewModel,
            onDismiss     = {
                showAddCardsSheet = false
                viewModel.clearAddCardsState()
            },
        )
    }

    // ── Card detail ModalBottomSheet ─────────────────────────────────────────
    if (selectedDeckCard != null) {
        CardDetailSheet(
            deckCard   = selectedDeckCard,
            deckFormat = viewModel.deckFormat,
            onAdd      = { viewModel.addCardToDeck(selectedDeckCard.scryfallId) },
            onRemove   = { viewModel.removeCardFromDeck(selectedDeckCard.scryfallId) },
            onDelete   = {
                viewModel.removeCard(selectedDeckCard.scryfallId)
                selectedCardId = null
            },
            onDismiss  = { selectedCardId = null },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Card detail ModalBottomSheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardDetailSheet(
    deckCard:   DeckCard,
    deckFormat: com.mmg.magicfolder.core.domain.model.DeckFormat?,
    onAdd:      () -> Unit,
    onRemove:   () -> Unit,
    onDelete:   () -> Unit,
    onDismiss:  () -> Unit,
) {
    val mc         = MaterialTheme.magicColors
    val ty         = MaterialTheme.magicTypography
    val card       = deckCard.card
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isCommander    = deckFormat?.uniqueCards == true
    val addEnabled     = !isCommander || deckCard.quantity < 1

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = mc.background,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            if (card != null) {
                // Card image
                val frontImageUrl = card.imageNormal ?: card.imageArtCrop
                if (!frontImageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(frontImageUrl).crossfade(true).build(),
                        contentDescription = card.name,
                        modifier           = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                        contentScale       = ContentScale.Fit,
                    )
                }
                if (!card.imageBackNormal.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(card.imageBackNormal).crossfade(true).build(),
                        contentDescription = "${card.name} (back)",
                        modifier           = Modifier.fillMaxWidth().aspectRatio(0.72f),
                        contentScale       = ContentScale.Fit,
                    )
                }

                Column(
                    modifier            = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Name + mana cost
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val displayName = card.printedName?.takeIf { it.isNotBlank() } ?: card.name
                        Text(
                            text     = displayName,
                            style    = ty.titleMedium,
                            color    = mc.textPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (!card.manaCost.isNullOrBlank()) {
                            ManaCostImages(manaCost = card.manaCost, symbolSize = 18.dp)
                        }
                    }

                    val typeLine = card.printedTypeLine?.takeIf { it.isNotBlank() } ?: card.typeLine
                    Text(typeLine, style = ty.labelMedium, color = mc.textSecondary)

                    HorizontalDivider(color = mc.surfaceVariant)

                    val displayText = card.oracleText?.takeIf { it.isNotBlank() }
                        ?: card.printedText?.takeIf { it.isNotBlank() }
                    if (displayText != null) {
                        Text(displayText, style = ty.bodySmall, color = mc.textPrimary)
                    }

                    when {
                        card.power != null && card.toughness != null ->
                            Text(
                                "${card.power}/${card.toughness}",
                                style      = ty.labelLarge,
                                color      = mc.textPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                        card.loyalty != null ->
                            Text("Loyalty: ${card.loyalty}", style = ty.labelLarge, color = mc.textPrimary, fontWeight = FontWeight.Bold)
                    }

                    if (!card.flavorText.isNullOrBlank()) {
                        HorizontalDivider(color = mc.surfaceVariant)
                        Text(card.flavorText, style = ty.bodySmall, color = mc.textSecondary, fontStyle = FontStyle.Italic)
                    }
                    if (!card.artist.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.draft_card_artist, card.artist),
                            style = ty.labelSmall,
                            color = mc.textDisabled,
                        )
                    }

                    val preferredCurrency = LocalPreferredCurrency.current
                    val priceText = PriceFormatter.formatFromScryfall(
                        priceUsd = card.priceUsd,
                        priceEur = card.priceEur,
                        preferredCurrency = preferredCurrency
                    )
                    if (priceText != "—") {
                        Text(
                            text = priceText,
                            style = ty.labelMedium,
                            color = mc.goldMtg,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            } else {
                // Card data not loaded yet
                Box(
                    modifier         = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(deckCard.scryfallId, style = ty.bodyMedium, color = mc.textDisabled)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color    = mc.surfaceVariant,
            )

            // Quantity controls
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text     = "En el mazo",
                    style    = ty.labelMedium,
                    color    = mc.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick  = onRemove,
                    enabled  = deckCard.quantity > 0,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = stringResource(R.string.action_remove),
                        tint               = if (deckCard.quantity > 0) mc.primaryAccent else mc.textDisabled,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = mc.primaryAccent.copy(alpha = 0.15f),
                ) {
                    Text(
                        text     = "${deckCard.quantity}",
                        style    = ty.titleMedium,
                        color    = mc.primaryAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                IconButton(
                    onClick  = onAdd,
                    enabled  = addEnabled,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add),
                        tint               = if (addEnabled) mc.primaryAccent else mc.textDisabled,
                    )
                }
            }

            // Remove from deck button
            OutlinedButton(
                onClick  = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                border   = androidx.compose.foundation.BorderStroke(1.dp, mc.lifeNegative.copy(alpha = 0.6f)),
                shape    = RoundedCornerShape(8.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint               = mc.lifeNegative,
                    modifier           = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text  = stringResource(R.string.action_remove),
                    color = mc.lifeNegative,
                    style = ty.labelLarge,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Add cards ModalBottomSheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardsSheet(
    uiState:          DeckDetailViewModel.UiState,
    format:           com.mmg.magicfolder.core.domain.model.DeckFormat?,
    onQueryChange:    (String) -> Unit,
    onAdd:            (String) -> Unit,
    onRemove:         (String) -> Unit,
    onAddBasicLand:   (String) -> Unit,
    onRemoveBasicLand:(String) -> Unit,
    viewModel: DeckDetailViewModel,
    onDismiss:        () -> Unit,
) {
    val mc         = MaterialTheme.magicColors
    val ty         = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = mc.backgroundSecondary,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text     = stringResource(R.string.deckbuilder_add_cards),
                style    = ty.titleMedium,
                color    = mc.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Search bar
            OutlinedTextField(
                value         = uiState.addCardsQuery,
                onValueChange = onQueryChange,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder   = { Text(stringResource(R.string.deckbuilder_add_cards_search_hint), color = mc.textDisabled) },
                leadingIcon   = {
                    if (uiState.isSearchingCards) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = mc.primaryAccent, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary)
                    }
                },
                trailingIcon = if (uiState.addCardsQuery.isNotEmpty()) {{
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_close), tint = mc.textSecondary)
                    }
                }} else null,
                singleLine = true,
                shape      = MaterialTheme.shapes.medium,
                colors     = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = mc.primaryAccent,
                    unfocusedBorderColor = mc.surfaceVariant,
                    focusedTextColor     = mc.textPrimary,
                    unfocusedTextColor   = mc.textPrimary,
                    cursorColor          = mc.primaryAccent,
                ),
            )

            LazyColumn(
                modifier       = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Basic lands section (always visible when query is blank)
                if (uiState.addCardsQuery.isBlank()) {
                    item(key = "basic_lands_header") {
                        Text(
                            text     = stringResource(R.string.deckbuilder_basic_lands),
                            style    = ty.labelLarge,
                            color    = mc.textSecondary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    items(DeckDetailViewModel.BASIC_LAND_NAMES, key = { "basic_$it" }) { landName ->
                        val quantity = uiState.cards
                            .filter { it.card?.name == landName }
                            .sumOf { it.quantity }
                        BasicLandRow(
                            name         = landName,
                            quantityInDeck = quantity,
                            onAdd        = { onAddBasicLand(landName) },
                            onRemove     = { onRemoveBasicLand(landName) },
                            manaCode = {viewModel.getManaCode(landName)}
                        )
                    }
                    item(key = "basic_lands_divider") {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color    = mc.surfaceVariant,
                        )
                    }
                }

                // Search results / collection
                items(uiState.addCardsResults, key = { it.card.scryfallId }) { row ->
                    AddCardSheetRow(
                        row      = row,
                        format   = format,
                        onAdd    = { onAdd(row.card.scryfallId) },
                        onRemove = { onRemove(row.card.scryfallId) },
                    )
                }

                if (uiState.addCardsResults.isEmpty() && !uiState.isSearchingCards && uiState.addCardsQuery.isNotBlank()) {
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text  = stringResource(R.string.deckbuilder_no_cards),
                                style = ty.bodyMedium,
                                color = mc.textDisabled,
                            )
                        }
                    }
                }
            }

            // Done button
            Button(
                onClick  = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(48.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                shape    = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text  = stringResource(R.string.deckbuilder_add_cards_done),
                    style = ty.titleMedium,
                    color = mc.background,
                )
            }
        }
    }
}

@Composable
private fun BasicLandRow(
    name:          String,
    quantityInDeck: Int,
    onAdd:         () -> Unit,
    onRemove:      () -> Unit,
    manaCode: () -> String?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(shape = RoundedCornerShape(8.dp), color = mc.surface) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            manaCode()?.let { ManaSymbolImage(token = it, size = 24.dp, modifier = Modifier.padding(2.dp)) }
                Text(
                    text     = name,
                    style    = ty.bodyMedium,
                    color    = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                )


            // Quantity controls
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (quantityInDeck > 0) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = null, tint = mc.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    Surface(shape = RoundedCornerShape(4.dp), color = mc.primaryAccent.copy(alpha = 0.15f)) {
                        Text(
                            text     = "$quantityInDeck",
                            style    = ty.labelMedium,
                            color    = mc.primaryAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).widthIn(min = 24.dp),
                        )
                    }
                }
                IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = mc.primaryAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AddCardSheetRow(
    row:      DeckDetailViewModel.AddCardRow,
    format:   com.mmg.magicfolder.core.domain.model.DeckFormat?,
    onAdd:    () -> Unit,
    onRemove: () -> Unit,
) {
    val mc   = MaterialTheme.magicColors
    val ty   = MaterialTheme.magicTypography
    val card = row.card

    val isCommander = format?.uniqueCards == true
    val addEnabled  = if (isCommander) row.quantityInDeck < 1 else true

    Surface(shape = RoundedCornerShape(8.dp), color = mc.surface) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model              = card.imageArtCrop,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .size(width = 52.dp, height = 38.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(mc.surfaceVariant),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text     = card.name,
                        style    = ty.bodyMedium,
                        color    = mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!row.isOwned) {
                        Surface(shape = RoundedCornerShape(4.dp), color = mc.surfaceVariant) {
                            Text(
                                text     = "Scryfall",
                                style    = ty.labelSmall,
                                color    = mc.textDisabled,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Text(
                    text     = card.typeLine,
                    style    = ty.bodySmall,
                    color    = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (row.quantityInDeck > 0) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.action_remove), tint = mc.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    Surface(shape = RoundedCornerShape(4.dp), color = mc.primaryAccent.copy(alpha = 0.15f)) {
                        Text(
                            text     = "${row.quantityInDeck}",
                            style    = ty.labelMedium,
                            color    = mc.primaryAccent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp).widthIn(min = 24.dp),
                        )
                    }
                }
                IconButton(
                    onClick  = onAdd,
                    enabled  = addEnabled,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add),
                        tint               = if (addEnabled) mc.primaryAccent else mc.textDisabled,
                        modifier           = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Deck content (summary + grouped card list)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DeckContent(
    uiState:     DeckDetailViewModel.UiState,
    onRemove:    (String) -> Unit,
    onCardClick: (String) -> Unit,
    modifier:    Modifier = Modifier,
) {
    val deck       = uiState.deck ?: return
    val cardGroups = groupCardsByType(uiState.cards)
    val maxInCurve = uiState.manaCurve.values.maxOrNull() ?: 1

    LazyColumn(
        modifier            = modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DeckSummaryCard(
                deck       = deck,
                totalCards = uiState.totalCards,
                manaCurve  = uiState.manaCurve,
                maxInCurve = maxInCurve,
            )
        }

        cardGroups.forEach { (typeName, cards) ->
            item(key = "header_$typeName") {
                TypeGroupHeader(
                    typeName = typeName,
                    count    = cards.sumOf { it.quantity },
                )
            }
            items(cards, key = { it.scryfallId }) { deckCard ->
                CardRow(
                    deckCard    = deckCard,
                    onRemove    = { onRemove(deckCard.scryfallId) },
                    onCardClick = { onCardClick(deckCard.scryfallId) },
                )
            }
        }

        // Spacer so FAB doesn't overlap last card
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Deck summary card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DeckSummaryCard(
    deck:       Deck,
    totalCards: Int,
    manaCurve:  Map<Int, Int>,
    maxInCurve: Int,
) {
    val mc          = MaterialTheme.magicColors
    val ty          = MaterialTheme.magicTypography
    val isCommander = deck.format.lowercase() == "commander"
    val targetCount = if (isCommander) 100 else 60

    Surface(shape = RoundedCornerShape(12.dp), color = mc.surface) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Total Cards", style = ty.labelMedium, color = mc.textSecondary)
                Text(
                    text  = "$totalCards / $targetCount",
                    style = ty.titleMedium,
                    color = if (totalCards >= targetCount) mc.lifePositive else mc.textPrimary,
                )
            }
            if (totalCards < targetCount) {
                LinearProgressIndicator(
                    progress  = { totalCards.toFloat() / targetCount },
                    modifier  = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color     = mc.primaryAccent,
                    trackColor = mc.surfaceVariant,
                )
            }
            if (manaCurve.isNotEmpty()) {
                Text("MANA CURVE", style = ty.labelSmall, color = mc.textSecondary)
                ManaCurveBar(manaCurve = manaCurve, maxInCurve = maxInCurve)
            }
        }
    }
}

@Composable
private fun ManaCurveBar(manaCurve: Map<Int, Int>, maxInCurve: Int) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(
        modifier              = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.Bottom,
    ) {
        (0..7).forEach { cmc ->
            val count    = manaCurve[cmc] ?: 0
            val fraction = if (maxInCurve > 0) count.toFloat() / maxInCurve else 0f
            Column(
                modifier            = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(mc.primaryAccent.copy(alpha = 0.5f + 0.5f * fraction)),
                    )
                }
                Text(if (cmc == 7) "7+" else cmc.toString(), style = ty.labelSmall, color = mc.textDisabled)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Type group header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TypeGroupHeader(typeName: String, count: Int) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(typeName, style = ty.titleMedium, color = mc.goldMtg)
        Text("($count)", style = ty.bodyMedium, color = mc.textSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Card row (clickable)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardRow(
    deckCard:    DeckCard,
    onRemove:    () -> Unit,
    onCardClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        shape   = RoundedCornerShape(8.dp),
        color   = mc.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model              = deckCard.card?.imageNormal,
                contentDescription = deckCard.card?.name ?: deckCard.scryfallId,
                modifier           = Modifier
                    .size(width = 44.dp, height = 60.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(mc.surfaceVariant),
                contentScale       = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = deckCard.card?.name ?: deckCard.scryfallId,
                    style    = ty.bodyMedium,
                    color    = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                deckCard.card?.typeLine?.let { type ->
                    Text(text = type, style = ty.bodySmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                deckCard.card?.manaCost?.let { cost ->
                    Text(text = cost, style = ty.labelSmall, color = mc.textSecondary)
                }
            }
            if (deckCard.quantity > 1) {
                Surface(shape = RoundedCornerShape(4.dp), color = mc.primaryAccent.copy(alpha = 0.2f)) {
                    Text(
                        text     = "\u00d7${deckCard.quantity}",
                        style    = ty.labelMedium,
                        color    = mc.primaryAccent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector        = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_remove),
                    tint               = mc.textDisabled,
                    modifier           = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun groupCardsByType(cards: List<DeckCard>): List<Pair<String, List<DeckCard>>> {
    val typeOrder = listOf(
        "Creatures", "Instants", "Sorceries",
        "Enchantments", "Artifacts", "Planeswalkers", "Lands", "Other",
    )
    val groups = mutableMapOf<String, MutableList<DeckCard>>()
    cards.forEach { deckCard ->
        val typeLine = deckCard.card?.typeLine ?: ""
        val group = when {
            typeLine.contains("Creature",     ignoreCase = true) -> "Creatures"
            typeLine.contains("Instant",      ignoreCase = true) -> "Instants"
            typeLine.contains("Sorcery",      ignoreCase = true) -> "Sorceries"
            typeLine.contains("Enchantment",  ignoreCase = true) -> "Enchantments"
            typeLine.contains("Artifact",     ignoreCase = true) -> "Artifacts"
            typeLine.contains("Planeswalker", ignoreCase = true) -> "Planeswalkers"
            typeLine.contains("Land",         ignoreCase = true) -> "Lands"
            else                                                   -> "Other"
        }
        groups.getOrPut(group) { mutableListOf() }.add(deckCard)
    }
    return typeOrder
        .filter { it in groups }
        .map { it to (groups[it]!! as List<DeckCard>) }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Cover image picker ModalBottomSheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CoverPickerSheet(
    cards:          List<DeckCard>,
    currentCoverId: String?,
    onSelect:       (String) -> Unit,
    onDismiss:      () -> Unit,
) {
    val mc         = MaterialTheme.magicColors
    val ty         = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = mc.backgroundSecondary,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text     = "Choose Cover Image",
                style    = ty.titleMedium,
                color    = mc.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Text(
                text     = "Tap a card's art to use it as the deck cover",
                style    = ty.bodySmall,
                color    = mc.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
            )

            LazyVerticalGrid(
                columns             = GridCells.Fixed(3),
                contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement   = Arrangement.spacedBy(8.dp),
                modifier            = Modifier.fillMaxSize(),
            ) {
                items(cards, key = { it.scryfallId }) { deckCard ->
                    val artUrl    = deckCard.card?.imageArtCrop ?: return@items
                    val isSelected = deckCard.scryfallId == currentCoverId
                    Box(
                        modifier = Modifier
                            .aspectRatio(1.37f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(deckCard.scryfallId) },
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(artUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = deckCard.card!!.name,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize(),
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(mc.primaryAccent.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Surface(
                                    color = mc.primaryAccent,
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text(
                                        text     = "✓",
                                        color    = mc.background,
                                        style    = ty.labelMedium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        // Card name tooltip at bottom
                        Box(
                            modifier         = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text     = deckCard.card!!.name,
                                style    = ty.labelSmall,
                                color    = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
