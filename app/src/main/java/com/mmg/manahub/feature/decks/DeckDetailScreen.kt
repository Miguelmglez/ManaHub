package com.mmg.manahub.feature.decks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.mmg.manahub.core.domain.model.*
import com.mmg.manahub.feature.addcard.AdvancedSearchSheet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.decks.components.DeckImportSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    deckId: String,
    onBack: () -> Unit,
    onAddCards: () -> Unit,
    viewModel: DeckDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val context = LocalContext.current
    var showAddCardsSheet by remember { mutableStateOf(false) }
    var showEditDeckSheet by remember { mutableStateOf(false) }
    var showBasicLandsSheet by remember { mutableStateOf(false) }
    var selectedCardId by remember { mutableStateOf<String?>(null) }

    // Sync deck to Supabase on any back navigation (system or top-bar button).
    val handleBack = remember(viewModel) {
        {
            viewModel.onNavigatingBack()
            onBack()
        }
    }
    BackHandler(onBack = handleBack)

    // Keep the selected card detail in sync with deck state changes
    val selectedDeckCard = remember(selectedCardId, uiState.cards, uiState.addCardsResults, uiState.scryfallResults) {
        selectedCardId?.let { id ->
            // 1. Try to find in current deck
            uiState.cards.find { it.scryfallId == id }
                ?: run {
                    // 2. Try to find in search results (collection tab)
                    uiState.addCardsResults.find { it.card.scryfallId == id }?.let { row ->
                        DeckSlotEntry(
                            scryfallId = row.card.scryfallId,
                            quantity = row.quantityInDeck,
                            isSideboard = false,
                            card = row.card
                        )
                    }
                }
                ?: run {
                    // 3. Try to find in search results (scryfall tab)
                    uiState.scryfallResults.find { it.card.scryfallId == id }?.let { row ->
                        DeckSlotEntry(
                            scryfallId = row.card.scryfallId,
                            quantity = row.quantityInDeck,
                            isSideboard = false,
                            card = row.card
                        )
                    }
                }
        }
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
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textSecondary,
                        )
                    }
                    Column(modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)) {
                        Text(
                            text = uiState.deck?.name ?: stringResource(R.string.deck_default_name),
                            style = ty.titleMedium,
                            color = mc.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        uiState.deck?.format?.let { fmt ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = mc.goldMtg.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = fmt.uppercase(),
                                    style = ty.labelSmall,
                                    color = mc.goldMtg,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    // Edit deck button — visible whenever deck is loaded
                    if (uiState.deck != null) {
                        IconButton(onClick = { showEditDeckSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.deck_edit_title),
                                tint = mc.textSecondary,
                            )
                        }
                    }
                    // Share button
                    IconButton(
                        onClick = {
                            val text = viewModel.exportDeckToText()
                            if (text != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        uiState.deck?.name ?: context.getString(R.string.deck_default_name)
                                    )
                                }
                                context.startActivity(
                                    Intent.createChooser(
                                        intent,
                                        context.getString(R.string.action_share)
                                    )
                                )
                            }
                        },
                        enabled = uiState.cards.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.action_share),
                            tint = mc.textSecondary,
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    viewModel.showCollectionCards()
                    showAddCardsSheet = true
                },
                containerColor = mc.primaryAccent,
                contentColor = mc.background,
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.deckbuilder_add_cards)
                )
            }
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }

            uiState.cards.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.deckbuilder_deck_empty),
                    style = ty.titleMedium,
                    color = mc.textSecondary,
                )
            }

            else -> DeckContent(
                uiState = uiState,
                onRemove = viewModel::removeCard,
                onCardClick = { id -> selectedCardId = id },
                onShowBasicLands = { showBasicLandsSheet = true },
                modifier = Modifier.padding(padding),
            )
        }
    }

    // ── Edit deck sheet ───────────────────────────────────────────────────────
    if (showEditDeckSheet) {
        EditDeckSheet(
            deck = uiState.deck,
            cards = uiState.cards,
            onSave = { newName, newCoverId ->
                if (newName != null) viewModel.updateDeckName(newName)
                if (newCoverId != null) viewModel.setCoverCard(newCoverId)
                showEditDeckSheet = false
            },
            onDismiss = { showEditDeckSheet = false },
        )
    }

    // ── Basic lands sheet ─────────────────────────────────────────────────────
    if (showBasicLandsSheet) {
        BasicLandsSheet(
            uiState = uiState,
            onAddBasicLand = viewModel::addBasicLandByName,
            onRemoveBasicLand = viewModel::removeBasicLandByName,
            viewModel = viewModel,
            onDismiss = { showBasicLandsSheet = false },
        )
    }

    // ── Add cards ModalBottomSheet ────────────────────────────────────────────
    if (showAddCardsSheet) {
        AddCardsSheet(
            uiState = uiState,
            format = viewModel.deckFormat,
            onQueryChange = viewModel::onAddCardsQueryChange,
            onScryfallSearch = viewModel::searchScryfallDirect,
            onAdd = viewModel::addCardToDeck,
            onRemove = viewModel::removeCardFromDeck,
            onCardClick = { id -> selectedCardId = id },
            onDismiss = {
                showAddCardsSheet = false
                viewModel.clearAddCardsState()
            },
        )
    }

    // ── Card detail ModalBottomSheet ─────────────────────────────────────────
    if (selectedDeckCard != null) {
        CardDetailSheet(
            deckCard = selectedDeckCard,
            deckFormat = viewModel.deckFormat,
            onAdd = { viewModel.addCardToDeck(selectedDeckCard.scryfallId) },
            onRemove = { viewModel.removeCardFromDeck(selectedDeckCard.scryfallId) },
            onDelete = {
                viewModel.removeCard(selectedDeckCard.scryfallId)
                selectedCardId = null
            },
            onDismiss = { selectedCardId = null },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Card detail ModalBottomSheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardDetailSheet(
    deckCard: DeckSlotEntry,
    deckFormat: com.mmg.manahub.core.domain.model.DeckFormat?,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val card = deckCard.card
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showBackFace by remember { mutableStateOf(false) }

    val isCommander = deckFormat?.uniqueCards == true
    val addEnabled = !isCommander || deckCard.quantity < 1

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            if (card != null) {
                // Card image
                val hasBackFace = !card.imageBackNormal.isNullOrBlank()

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val rotation by animateFloatAsState(
                        targetValue = if (showBackFace) -180f else 0f,
                        animationSpec = tween(durationMillis = 500),
                        label = "CardFlip"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .aspectRatio(0.716f)
                            .graphicsLayer {
                                rotationY = rotation
                                cameraDistance = 12f * density
                            }
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (hasBackFace)
                                    Modifier.clickable { showBackFace = !showBackFace }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Front Face
                        val frontImageUrl = card.imageNormal ?: card.imageArtCrop
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(frontImageUrl).crossfade(true).build(),
                            contentDescription = card.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = if (rotation >= -90f) 1f else 0f
                                },
                        )

                        // Back Face
                        if (hasBackFace) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(card.imageBackNormal).crossfade(true).build(),
                                contentDescription = stringResource(
                                    R.string.carddetail_back_face_description,
                                    card.name
                                ),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        rotationY = 180f
                                        alpha = if (rotation < -90f) 1f else 0f
                                    },
                            )
                        }
                    }

                    if (hasBackFace) {
                        Text(
                            text = if (showBackFace)
                                stringResource(R.string.carddetail_flip_see_front)
                            else
                                stringResource(R.string.carddetail_flip_see_back),
                            style = ty.labelMedium,
                            color = mc.primaryAccent,
                        )
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Name + mana cost
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val displayName = card.printedName?.takeIf { it.isNotBlank() } ?: card.name
                        Text(
                            text = displayName,
                            style = ty.titleMedium,
                            color = mc.textPrimary,
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

                    // Oracle / printed text with inline mana symbols
                    val oracleDisplayText = card.oracleText?.takeIf { it.isNotBlank() }
                        ?: card.printedText?.takeIf { it.isNotBlank() }
                    if (!oracleDisplayText.isNullOrEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ) {
                            OracleText(
                                text = oracleDisplayText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    when {
                        card.power != null && card.toughness != null ->
                            Text(
                                "${card.power}/${card.toughness}",
                                style = ty.labelLarge,
                                color = mc.textPrimary,
                                fontWeight = FontWeight.Bold,
                            )

                        card.loyalty != null ->
                            Text(
                                stringResource(R.string.carddetail_loyalty_value, card.loyalty),
                                style = ty.labelLarge,
                                color = mc.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                    }

                    if (!card.flavorText.isNullOrBlank()) {
                        HorizontalDivider(color = mc.surfaceVariant)
                        Text(
                            card.flavorText,
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                            fontStyle = FontStyle.Italic
                        )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(deckCard.scryfallId, style = ty.bodyMedium, color = mc.textDisabled)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = mc.surfaceVariant,
            )

            // Quantity controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.deckdetail_in_deck_label),
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onRemove,
                    enabled = deckCard.quantity > 0,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Remove,
                        contentDescription = stringResource(R.string.action_remove),
                        tint = if (deckCard.quantity > 0) mc.primaryAccent else mc.textDisabled,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = mc.primaryAccent.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = "${deckCard.quantity}",
                        style = ty.titleMedium,
                        color = mc.primaryAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                IconButton(
                    onClick = onAdd,
                    enabled = addEnabled,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add),
                        tint = if (addEnabled) mc.primaryAccent else mc.textDisabled,
                    )
                }
            }

            // Remove from deck button
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    mc.lifeNegative.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = mc.lifeNegative,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_remove_all),
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
    uiState: DeckDetailViewModel.UiState,
    format: com.mmg.manahub.core.domain.model.DeckFormat?,
    onQueryChange: (String) -> Unit,
    onScryfallSearch: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onCardClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAdvancedSearch by remember { mutableStateOf(false) }

    // When switching to Scryfall tab, trigger a search if query is not blank
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1 && uiState.addCardsQuery.isNotBlank()) {
            onScryfallSearch(uiState.addCardsQuery)
        }
    }

    if (showAdvancedSearch) {
        AdvancedSearchSheet(
            onDismiss = { showAdvancedSearch = false },
            onSearch = { _, rawQuery ->
                showAdvancedSearch = false
                selectedTab = 1
                onScryfallSearch(rawQuery)
            },
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.90f)
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.deckbuilder_add_cards),
                style = ty.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            // Search bar + advanced search icon
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.addCardsQuery,
                    onValueChange = { query ->
                        if (selectedTab == 0) onQueryChange(query) else onScryfallSearch(query)
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            stringResource(R.string.deckbuilder_add_cards_search_hint),
                            color = mc.textDisabled
                        )
                    },
                    leadingIcon = {
                        val isSearching = if (selectedTab == 0) uiState.isSearchingCards else uiState.isSearchingScryfall
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = mc.primaryAccent,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary)
                        }
                    },
                    trailingIcon = if (uiState.addCardsQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = {
                                onQueryChange("")
                                onScryfallSearch("")
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_close), tint = mc.textSecondary)
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                        focusedTextColor = mc.textPrimary,
                        unfocusedTextColor = mc.textPrimary,
                        cursorColor = mc.primaryAccent,
                    ),
                )
                IconButton(
                    onClick = { showAdvancedSearch = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(mc.surface),
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = stringResource(R.string.advsearch_title),
                        tint = mc.primaryAccent,
                    )
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = mc.backgroundSecondary,
                contentColor = mc.primaryAccent,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            stringResource(R.string.deckbuilder_tab_collection),
                            style = ty.labelLarge,
                            color = if (selectedTab == 0) mc.primaryAccent else mc.textSecondary,
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        if (uiState.addCardsQuery.isNotBlank()) onScryfallSearch(uiState.addCardsQuery)
                    },
                    text = {
                        Text(
                            stringResource(R.string.deckdetail_tab_scryfall),
                            style = ty.labelLarge,
                            color = if (selectedTab == 1) mc.primaryAccent else mc.textSecondary,
                        )
                    },
                )
            }

            // Card list per tab
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (selectedTab == 0) {
                    // ── Collection tab ────────────────────────────────────────
                    items(uiState.addCardsResults, key = { it.card.scryfallId }) { row ->
                        AddCardSheetRow(
                            row = row,
                            format = format,
                            onAdd = { onAdd(row.card.scryfallId) },
                            onRemove = { onRemove(row.card.scryfallId) },
                            onClick = { onCardClick(row.card.scryfallId) },
                        )
                    }
                    if (uiState.addCardsResults.isEmpty() && !uiState.isSearchingCards) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.deckbuilder_no_cards),
                                    style = ty.bodyMedium,
                                    color = mc.textDisabled,
                                )
                            }
                        }
                    }
                } else {
                    // ── Scryfall tab ──────────────────────────────────────────
                    items(uiState.scryfallResults, key = { "sf_${it.card.scryfallId}" }) { row ->
                        AddCardSheetRow(
                            row = row,
                            format = format,
                            onAdd = { onAdd(row.card.scryfallId) },
                            onRemove = { onRemove(row.card.scryfallId) },
                            onClick = { onCardClick(row.card.scryfallId) },
                        )
                    }
                    if (uiState.scryfallResults.isEmpty() && !uiState.isSearchingScryfall) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (uiState.addCardsQuery.isBlank())
                                        stringResource(R.string.deckbuilder_add_cards_search_hint)
                                    else
                                        stringResource(R.string.deckbuilder_no_cards),
                                    style = ty.bodyMedium,
                                    color = mc.textDisabled,
                                )
                            }
                        }
                    }
                }
            }

            // Done button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.deckbuilder_add_cards_done),
                    style = ty.titleMedium,
                    color = mc.background,
                )
            }
        }
    }
}

@Composable
private fun BasicLandRow(
    name: String,
    quantityInDeck: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    manaCode: () -> String?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(shape = RoundedCornerShape(8.dp), color = mc.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            manaCode()?.let {
                ManaSymbolImage(
                    token = it,
                    size = 24.dp,
                    modifier = Modifier.padding(2.dp)
                )
            }
            Text(
                text = name,
                style = ty.bodyMedium,
                color = mc.textPrimary,
                modifier = Modifier.weight(1f),
            )


            // Quantity controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (quantityInDeck > 0) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = null,
                            tint = mc.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = mc.primaryAccent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "$quantityInDeck",
                            style = ty.labelMedium,
                            color = mc.primaryAccent,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .widthIn(min = 24.dp),
                        )
                    }
                }
                IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCardSheetRow(
    row: AddCardRow,
    format: com.mmg.manahub.core.domain.model.DeckFormat?,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val card = row.card

    val isCommander = format?.uniqueCards == true
    val addEnabled = if (isCommander) row.quantityInDeck < 1 else true

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = mc.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 52.dp, height = 38.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(mc.surfaceVariant),
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = card.name,
                        style = ty.bodyMedium,
                        color = mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = card.typeLine,
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (row.quantityInDeck > 0) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = stringResource(R.string.action_remove),
                            tint = mc.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = mc.primaryAccent.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${row.quantityInDeck}",
                            style = ty.labelMedium,
                            color = mc.primaryAccent,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .widthIn(min = 24.dp),
                        )
                    }
                }
                IconButton(
                    onClick = onAdd,
                    enabled = addEnabled,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.action_add),
                        tint = if (addEnabled) mc.primaryAccent else mc.textDisabled,
                        modifier = Modifier.size(16.dp),
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
    uiState: DeckDetailViewModel.UiState,
    onRemove: (String) -> Unit,
    onCardClick: (String) -> Unit,
    onShowBasicLands: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deck = uiState.deck ?: return
    val cardGroups = groupCardsByType(uiState.cards)
    val maxInCurve = uiState.manaCurve.values.maxOrNull() ?: 1
    val collectionIds = uiState.collectionIds
    val landsResId = R.string.deckdetail_group_lands

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DeckSummaryCard(
                deck = deck,
                totalCards = uiState.totalCards,
                manaCurve = uiState.manaCurve,
                maxInCurve = maxInCurve,
            )
        }

        // Render all groups except Lands normally
        cardGroups.filter { (typeResId, _) -> typeResId != landsResId }
            .forEach { (typeResId, cards) ->
                item(key = "header_$typeResId") {
                    TypeGroupHeader(typeResId = typeResId, count = cards.sumOf { it.quantity })
                }
                items(cards, key = { it.scryfallId }) { deckCard ->
                    CardRow(
                        deckCard = deckCard,
                        isInCollection = deckCard.scryfallId in collectionIds,
                        onRemove = { onRemove(deckCard.scryfallId) },
                        onCardClick = { onCardClick(deckCard.scryfallId) },
                    )
                }
            }

        // Lands group — always rendered, even when empty
        val landCards = cardGroups.find { (typeResId, _) -> typeResId == landsResId }?.second ?: emptyList()
        item(key = "header_$landsResId") {
            TypeGroupHeader(typeResId = landsResId, count = landCards.sumOf { it.quantity })
        }
        items(landCards, key = { it.scryfallId }) { deckCard ->
            CardRow(
                deckCard = deckCard,
                isInCollection = deckCard.scryfallId in collectionIds,
                onRemove = { onRemove(deckCard.scryfallId) },
                onCardClick = { onCardClick(deckCard.scryfallId) },
            )
        }
        // "Add basic lands" action row
        item(key = "add_basic_lands_btn") {
            AddBasicLandsRow(onClick = onShowBasicLands)
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
    deck: Deck,
    totalCards: Int,
    manaCurve: Map<Int, Int>,
    maxInCurve: Int,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val isCommander = deck.format.lowercase() == "commander"
    val targetCount = if (isCommander) 100 else 60

    Surface(shape = RoundedCornerShape(12.dp), color = mc.surface) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.deck_total_cards), style = ty.labelMedium, color = mc.textSecondary)
                Text(
                    text = "$totalCards / $targetCount",
                    style = ty.titleMedium,
                    color = if (totalCards >= targetCount) mc.lifePositive else mc.textPrimary,
                )
            }
            if (totalCards < targetCount) {
                LinearProgressIndicator(
                    progress = { totalCards.toFloat() / targetCount },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = mc.primaryAccent,
                    trackColor = mc.surfaceVariant,
                )
            }
            if (manaCurve.isNotEmpty()) {
                Text(stringResource(R.string.deck_mana_curve_uppercase), style = ty.labelSmall, color = mc.textSecondary)
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
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        (0..7).forEach { cmc ->
            val count = manaCurve[cmc] ?: 0
            val fraction = if (maxInCurve > 0) count.toFloat() / maxInCurve else 0f
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                                        if (count > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(mc.primaryAccent.copy(alpha = 0.5f + 0.5f * fraction)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = count.toString(),
                                style = ty.labelSmall,
                                color = mc.surface
                            )
                        }
                    }
                }
                Text(
                    text = if (cmc == 7) "7+" else cmc.toString(),
                    style = ty.labelSmall,
                    color = mc.textSecondary
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Type group header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TypeGroupHeader(typeResId: Int, count: Int) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(stringResource(typeResId), style = ty.titleMedium, color = mc.goldMtg)
        Text("($count)", style = ty.bodyMedium, color = mc.textSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Card row (clickable)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardRow(
    deckCard: DeckSlotEntry,
    isInCollection: Boolean,
    onRemove: () -> Unit,
    onCardClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = mc.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = deckCard.card?.imageNormal,
                contentDescription = deckCard.card?.name ?: deckCard.scryfallId,
                modifier = Modifier
                    .size(width = 44.dp, height = 60.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(mc.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = deckCard.card?.name ?: deckCard.scryfallId,
                    style = ty.bodyMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                deckCard.card?.typeLine?.let { type ->
                    Text(
                        text = type,
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                deckCard.card?.manaCost?.let { cost ->
                    Spacer(Modifier.height(4.dp))
                    ManaCostImages(manaCost = cost, symbolSize = 14.dp)
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (deckCard.quantity > 1) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = mc.primaryAccent.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = "\u00d7${deckCard.quantity}",
                            style = ty.labelMedium,
                            color = mc.primaryAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                if (isInCollection) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = stringResource(R.string.deckdetail_owned_icon_desc),
                        tint = mc.goldMtg,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_remove),
                    tint = mc.textDisabled,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun groupCardsByType(cards: List<DeckSlotEntry>): List<Pair<Int, List<DeckSlotEntry>>> {
    val typeOrder = listOf(
        R.string.deckdetail_group_creatures,
        R.string.deckdetail_group_instants,
        R.string.deckdetail_group_sorceries,
        R.string.deckdetail_group_enchantments,
        R.string.deckdetail_group_artifacts,
        R.string.deckdetail_group_planeswalkers,
        R.string.deckdetail_group_lands,
        R.string.deckdetail_group_other,
    )
    val groups = mutableMapOf<Int, MutableList<DeckSlotEntry>>()
    cards.forEach { deckCard ->
        val typeLine = deckCard.card?.typeLine ?: ""
        val group = when {
            typeLine.contains("Creature", ignoreCase = true) -> R.string.deckdetail_group_creatures
            typeLine.contains("Instant", ignoreCase = true) -> R.string.deckdetail_group_instants
            typeLine.contains("Sorcery", ignoreCase = true) -> R.string.deckdetail_group_sorceries
            typeLine.contains("Enchantment", ignoreCase = true) -> R.string.deckdetail_group_enchantments
            typeLine.contains("Artifact", ignoreCase = true) -> R.string.deckdetail_group_artifacts
            typeLine.contains("Planeswalker", ignoreCase = true) -> R.string.deckdetail_group_planeswalkers
            typeLine.contains("Land", ignoreCase = true) -> R.string.deckdetail_group_lands
            else -> R.string.deckdetail_group_other
        }
        groups.getOrPut(group) { mutableListOf() }.add(deckCard)
    }
    return typeOrder
        .filter { it in groups }
        .map { it to (groups[it]!! as List<DeckSlotEntry>) }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Edit deck ModalBottomSheet (name + cover image)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDeckSheet(
    deck: com.mmg.manahub.core.domain.model.Deck?,
    cards: List<DeckSlotEntry>,
    onSave: (newName: String?, newCoverId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var nameText by remember(deck?.name) { mutableStateOf(deck?.name ?: "") }
    var selectedCoverId by remember(deck?.coverCardId) { mutableStateOf(deck?.coverCardId) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.deck_edit_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
                Button(
                    onClick = {
                        val newName = nameText.trim().takeIf { it.isNotEmpty() && it != deck?.name }
                        val newCover = selectedCoverId.takeIf { it != deck?.coverCardId }
                        onSave(newName, newCover)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(stringResource(R.string.action_save), style = ty.labelLarge, color = mc.background)
                }
            }

            HorizontalDivider(color = mc.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))

            // Deck name field
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    text = stringResource(R.string.deckbuilder_setup_name_label),
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(stringResource(R.string.deckbuilder_name_hint), color = mc.textDisabled)
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                        focusedTextColor = mc.textPrimary,
                        unfocusedTextColor = mc.textPrimary,
                        cursorColor = mc.primaryAccent,
                    ),
                )
            }

            // Cover image section (only if deck has cards with art)
            val coverCards = cards.filter { it.card?.imageArtCrop != null }
            if (coverCards.isNotEmpty()) {
                HorizontalDivider(color = mc.surfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                Text(
                    text = stringResource(R.string.deck_edit_cover_section),
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
                Text(
                    text = stringResource(R.string.deck_cover_picker_subtitle),
                    style = ty.bodySmall,
                    color = mc.textDisabled,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(coverCards, key = { "cover_${it.scryfallId}" }) { deckCard ->
                        val artUrl = deckCard.card?.imageArtCrop ?: return@items
                        val isSelected = deckCard.scryfallId == selectedCoverId
                        Box(
                            modifier = Modifier
                                .aspectRatio(1.37f)
                                .clip(RoundedCornerShape(8.dp))
                                .then(
                                    if (isSelected) Modifier.border(2.dp, mc.primaryAccent, RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .clickable { selectedCoverId = deckCard.scryfallId },
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(artUrl).crossfade(true).build(),
                                contentDescription = deckCard.card!!.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(mc.primaryAccent.copy(alpha = 0.30f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Surface(color = mc.primaryAccent, shape = RoundedCornerShape(50)) {
                                        Text(
                                            text = "✓",
                                            color = mc.background,
                                            style = ty.labelMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                            // Card name overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = deckCard.card!!.name,
                                    style = ty.labelSmall,
                                    color = Color.White,
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
}

// ─────────────────────────────────────────────────────────────────────────────
//  Basic lands ModalBottomSheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicLandsSheet(
    uiState: DeckDetailViewModel.UiState,
    onAddBasicLand: (String) -> Unit,
    onRemoveBasicLand: (String) -> Unit,
    viewModel: DeckDetailViewModel,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.deckdetail_basic_lands_sheet_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            DeckDetailViewModel.BASIC_LAND_NAMES.forEach { landName ->
                val quantity = uiState.cards
                    .filter { it.card?.name == landName }
                    .sumOf { it.quantity }
                BasicLandRow(
                    name = landName,
                    quantityInDeck = quantity,
                    onAdd = { onAddBasicLand(landName) },
                    onRemove = { onRemoveBasicLand(landName) },
                    manaCode = { viewModel.getManaCode(landName) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  "Add basic lands" action row inside DeckContent
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AddBasicLandsRow(onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = mc.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = mc.primaryAccent.copy(alpha = 0.12f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Park,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Text(
                text = stringResource(R.string.deckdetail_add_basic_lands),
                style = ty.bodyMedium,
                color = mc.primaryAccent,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
