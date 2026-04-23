package com.mmg.manahub.feature.deckmagic

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.feature.addcard.AdvancedSearchSheet
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.feature.deckmagic.components.MagicLandSuggestionStatic
import com.mmg.manahub.feature.decks.DeckSlotEntry
import com.mmg.manahub.feature.decks.components.ManaCurveChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckMagicDetailScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    viewModel: DeckMagicDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val context = LocalContext.current

    var showAddCardsSheet by remember { mutableStateOf(false) }
    var showBasicLandsSheet by remember { mutableStateOf(false) }
    var showEditDeckSheet by remember { mutableStateOf(false) }
    var selectedCardId by remember { mutableStateOf<String?>(null) }

    val handleBack = remember(viewModel, uiState.step) {
        {
            if (uiState.step == DetailStep.REVIEW) {
                viewModel.goBackToView()
            } else {
                viewModel.onNavigatingBack()
                onBack()
            }
        }
    }
    BackHandler(onBack = handleBack)

    val selectedDeckCard = remember(selectedCardId, uiState.cards, uiState.addCardsResults, uiState.scryfallResults) {
        selectedCardId?.let { id ->
            uiState.cards.find { it.scryfallId == id }
                ?: (uiState.addCardsResults + uiState.scryfallResults).find { it.card.scryfallId == id }?.let { row ->
                    DeckSlotEntry(row.card.scryfallId, row.quantityInDeck, false, row.card)
                }
        }
    }

    Scaffold(
        containerColor = mc.background,
        topBar = {
            Surface(color = mc.backgroundSecondary) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = handleBack) {
                        val icon = if (uiState.step == DetailStep.REVIEW) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.ArrowBack
                        Icon(icon, contentDescription = null, tint = mc.textSecondary)
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            text = when (uiState.step) {
                                DetailStep.VIEW -> uiState.deck?.name ?: "Deck Magic"
                                DetailStep.REVIEW -> stringResource(R.string.deckbuilder_step_review)
                            },
                            style = ty.titleMedium,
                            color = mc.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (uiState.step == DetailStep.VIEW) {
                            uiState.deck?.format?.let { fmt ->
                                Surface(shape = RoundedCornerShape(4.dp), color = mc.goldMtg.copy(alpha = 0.15f)) {
                                    Text(
                                        text = fmt.uppercase(),
                                        style = ty.labelSmall,
                                        color = mc.goldMtg,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                    
                    if (uiState.step == DetailStep.VIEW) {
                        IconButton(onClick = { showEditDeckSheet = true }) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = mc.textSecondary)
                        }
                        IconButton(onClick = {
                            val text = viewModel.exportDeckToText()
                            if (text != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Deck"))
                            }
                        }, enabled = uiState.cards.isNotEmpty()) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = mc.textSecondary)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (uiState.step == DetailStep.VIEW) {
                FloatingActionButton(
                    onClick = {
                        viewModel.showCollectionCards()
                        showAddCardsSheet = true
                    },
                    containerColor = mc.primaryAccent,
                    contentColor = mc.background,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = uiState.step,
            modifier = Modifier.padding(padding),
            label = "StepTransition"
        ) { step ->
            when (step) {
                DetailStep.VIEW -> ViewStepContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onCardClick = { selectedCardId = it },
                    onAddBasicLands = { showBasicLandsSheet = true }
                )
                DetailStep.REVIEW -> ReviewStepContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onCardClick = { selectedCardId = it }
                )
            }
        }
    }

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

    if (showBasicLandsSheet) {
        BasicLandsSheet(
            uiState = uiState,
            onAddBasicLand = viewModel::addBasicLandByName,
            onRemoveBasicLand = viewModel::removeBasicLandByName,
            viewModel = viewModel,
            onDismiss = { showBasicLandsSheet = false },
        )
    }

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ViewStepContent(
    uiState: DeckMagicDetailUiState,
    viewModel: DeckMagicDetailViewModel,
    onCardClick: (String) -> Unit,
    onAddBasicLands: () -> Unit
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = mc.primaryAccent)
        }
    } else if (uiState.cards.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Deck is empty", style = ty.titleMedium, color = mc.textSecondary)
        }
    } else {
        val groupedCards = groupCards(uiState.cards, uiState.groupingMode)
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = mc.surface)
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                            val deckCards = uiState.cards.filter { it.card != null && !BasicLandCalculator.isLand(it.card!!) }.map {
                                com.mmg.manahub.core.domain.model.DeckCard(it.card!!, it.quantity, it.scryfallId in uiState.collectionIds)
                            }
                            ManaCurveChart(cards = deckCards, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    GroupingFlowSelector(
                        selected = uiState.groupingMode,
                        onSelect = viewModel::setGroupingMode
                    )
                }
            }

            groupedCards.forEach { (groupLabel, cards) ->
                item(key = "header_$groupLabel") {
                    GroupHeader(
                        label = groupLabel, 
                        count = cards.sumOf { it.quantity },
                        showSuggestionToggle = groupLabel == "Lands",
                        isSuggestionEnabled = uiState.showLandSuggestions,
                        onToggleSuggestion = viewModel::toggleLandSuggestions,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                
                if (groupLabel == "Lands") {
                    if (uiState.showLandSuggestions && uiState.landDeltas.isNotEmpty()) {
                        item(key = "magic_land_suggestion") {
                            MagicLandSuggestionStatic(
                                deltas = uiState.landDeltas,
                                onClick = viewModel::applyLandSuggestions,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                    item(key = "add_basic_lands_btn") {
                        AddBasicLandsRow(onClick = onAddBasicLands, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }

                items(cards, key = { it.scryfallId + groupLabel }) { entry ->
                    CardRow(
                        entry = entry,
                        isInCollection = entry.scryfallId in uiState.collectionIds,
                        onClick = { onCardClick(entry.scryfallId) },
                        onRemove = { viewModel.removeCard(entry.scryfallId) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
            
            item {
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.setStep(DetailStep.REVIEW) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Visibility, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.deckbuilder_step_review), style = ty.titleMedium)
                }
            }
            
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
private fun ReviewStepContent(
    uiState: DeckMagicDetailUiState,
    viewModel: DeckMagicDetailViewModel,
    onCardClick: (String) -> Unit
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                val deckCards = uiState.cards.filter { it.card != null && !BasicLandCalculator.isLand(it.card!!) }.map {
                    com.mmg.manahub.core.domain.model.DeckCard(it.card!!, it.quantity, it.scryfallId in uiState.collectionIds)
                }
                ManaCurveChart(cards = deckCards, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
            }

            val grouped = uiState.cards.groupBy { entry ->
                val type = entry.card?.typeLine ?: ""
                when {
                    type.contains("Creature") -> "Creatures"
                    type.contains("Land") -> "Lands"
                    else -> "Non-creature Spells"
                }
            }

            grouped.entries.sortedBy { it.key }.forEach { (groupLabel, cards) ->
                item(key = "review_header_$groupLabel") {
                    GroupHeader(label = groupLabel, count = cards.sumOf { it.quantity })
                }
                items(cards, key = { "review_${it.scryfallId}" }) { entry ->
                    CardRow(
                        entry = entry,
                        isInCollection = entry.scryfallId in uiState.collectionIds,
                        onClick = { onCardClick(entry.scryfallId) },
                        onRemove = { viewModel.removeCard(entry.scryfallId) }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(mc.background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = { viewModel.saveDeck { viewModel.goBackToView() } },
                enabled = uiState.totalCards > 0 && !uiState.isSaving,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.primaryAccent,
                    disabledContainerColor = mc.surfaceVariant,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(color = mc.background, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        text = stringResource(R.string.deckbuilder_save_button),
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = if (uiState.totalCards > 0) mc.background else mc.textDisabled,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupingFlowSelector(
    selected: GroupingMode,
    onSelect: (GroupingMode) -> Unit
) {
    val mc = MaterialTheme.magicColors
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GroupingMode.entries.forEach { mode ->
            val label = when (mode) {
                GroupingMode.COST -> "Cost"
                else -> mode.name.lowercase().replaceFirstChar { it.uppercase() }
            }
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mc.primaryAccent,
                    selectedLabelColor = mc.background
                )
            )
        }
    }
}

@Composable
private fun GroupHeader(
    label: String, 
    count: Int,
    modifier: Modifier = Modifier,
    showSuggestionToggle: Boolean = false,
    isSuggestionEnabled: Boolean = true,
    onToggleSuggestion: () -> Unit = {},
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val colorName = when(label) {
            "W" -> "White"
            "U" -> "Blue"
            "B" -> "Black"
            "R" -> "Red"
            "G" -> "Green"
            else -> label
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (label.length == 1 && label[0] in "WUBRG") {
                ManaSymbolImage(token = label, size = 20.dp)
            }
            Text(colorName, style = ty.titleMedium, color = mc.goldMtg)
            Text("($count)", style = ty.bodyMedium, color = mc.textSecondary)
        }
        
        if (showSuggestionToggle) {
            TextButton(
                onClick = onToggleSuggestion,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (isSuggestionEnabled) "Disable suggestions" else "Enable suggestions",
                    style = ty.labelSmall,
                    color = mc.primaryAccent
                )
            }
        }
    }
}

@Composable
private fun CardRow(
    entry: DeckSlotEntry,
    isInCollection: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = mc.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = entry.card?.imageNormal,
                contentDescription = null,
                modifier = Modifier.size(width = 44.dp, height = 60.dp).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f)) {
                Text(
                    entry.card?.name ?: "Unknown", 
                    style = ty.bodyMedium, 
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                entry.card?.typeLine?.let {
                    Text(it, style = ty.bodySmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                entry.card?.manaCost?.let {
                    ManaCostImages(manaCost = it, symbolSize = 14.dp)
                }
            }
            if (entry.quantity > 1) {
                Surface(shape = RoundedCornerShape(4.dp), color = mc.primaryAccent.copy(alpha = 0.2f)) {
                    Text("×${entry.quantity}", style = ty.labelMedium, color = mc.primaryAccent, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            if (isInCollection) {
                Icon(Icons.Default.Star, contentDescription = null, tint = mc.goldMtg, modifier = Modifier.size(14.dp))
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, tint = mc.textDisabled, modifier = Modifier.size(16.dp))
            }
        }
    }
}

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
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            if (card != null) {
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
                        modifier = Modifier.fillMaxWidth(0.75f).aspectRatio(0.716f).graphicsLayer {
                            rotationY = rotation
                            cameraDistance = 12f * density
                        }.clip(RoundedCornerShape(12.dp)).then(
                            if (hasBackFace) Modifier.clickable { showBackFace = !showBackFace } else Modifier
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(card.imageNormal ?: card.imageArtCrop).crossfade(true).build(),
                            contentDescription = card.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (rotation >= -90f) 1f else 0f },
                        )
                        if (hasBackFace) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(card.imageBackNormal).crossfade(true).build(),
                                contentDescription = card.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f; alpha = if (rotation < -90f) 1f else 0f },
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(card.name, style = ty.titleMedium, color = mc.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        card.manaCost?.let { ManaCostImages(manaCost = it, symbolSize = 18.dp) }
                    }
                    Text(card.typeLine, style = ty.labelMedium, color = mc.textSecondary)
                    HorizontalDivider(color = mc.surfaceVariant)
                    OracleText(text = card.oracleText ?: "", style = MaterialTheme.typography.bodySmall)
                    
                    val preferredCurrency = LocalPreferredCurrency.current
                    val priceText = PriceFormatter.formatFromScryfall(card.priceUsd, card.priceEur, preferredCurrency)
                    if (priceText != "—") {
                        Text(priceText, style = ty.labelMedium, color = mc.goldMtg, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("In deck", style = ty.labelMedium, color = mc.textSecondary, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove, enabled = deckCard.quantity > 0) {
                    Icon(Icons.Default.Remove, contentDescription = null, tint = if (deckCard.quantity > 0) mc.primaryAccent else mc.textDisabled)
                }
                Surface(shape = RoundedCornerShape(8.dp), color = mc.primaryAccent.copy(alpha = 0.15f)) {
                    Text("${deckCard.quantity}", style = ty.titleMedium, color = mc.primaryAccent, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
                IconButton(onClick = onAdd, enabled = addEnabled) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = if (addEnabled) mc.primaryAccent else mc.textDisabled)
                }
            }

            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = mc.lifeNegative)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Remove All", style = ty.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardsSheet(
    uiState: DeckMagicDetailUiState,
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
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.90f).padding(bottom = 16.dp)) {
            Text("Add Cards", style = ty.titleMedium, color = mc.textPrimary, modifier = Modifier.padding(16.dp))

            OutlinedTextField(
                value = uiState.addCardsQuery,
                onValueChange = { if (selectedTab == 0) onQueryChange(it) else onScryfallSearch(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Search cards...", color = mc.textDisabled) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = mc.textSecondary) },
                trailingIcon = {
                    IconButton(onClick = { showAdvancedSearch = true }) {
                        Icon(Icons.Default.Tune, null, tint = mc.primaryAccent)
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = mc.primaryAccent,
                    unfocusedBorderColor = mc.surfaceVariant,
                    cursorColor = mc.primaryAccent
                )
            )

            TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, contentColor = mc.primaryAccent) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Collection") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Scryfall") })
            }

            val results = if (selectedTab == 0) uiState.addCardsResults else uiState.scryfallResults
            LazyColumn(modifier = Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.card.scryfallId }) { row ->
                    AddCardSheetRow(
                        row = row,
                        format = format,
                        onAdd = { onAdd(row.card.scryfallId) },
                        onRemove = { onRemove(row.card.scryfallId) },
                        onClick = { onCardClick(row.card.scryfallId) }
                    )
                }
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp)) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun AddCardSheetRow(
    row: com.mmg.manahub.feature.decks.DeckDetailViewModel.AddCardRow,
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

    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = mc.surface) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AsyncImage(model = card.imageArtCrop, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(4.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(card.name, style = ty.bodyMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(card.typeLine, style = ty.bodySmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (row.quantityInDeck > 0) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Remove, null, tint = mc.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    Text("${row.quantityInDeck}", style = ty.labelMedium, color = mc.primaryAccent)
                }
                IconButton(onClick = onAdd, enabled = addEnabled, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, null, tint = if (addEnabled) mc.primaryAccent else mc.textDisabled, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BasicLandsSheet(
    uiState: DeckMagicDetailUiState,
    onAddBasicLand: (String) -> Unit,
    onRemoveBasicLand: (String) -> Unit,
    viewModel: DeckMagicDetailViewModel,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = mc.backgroundSecondary) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Basic Lands", style = ty.titleMedium, color = mc.textPrimary, modifier = Modifier.padding(vertical = 8.dp))
            listOf("Plains", "Island", "Swamp", "Mountain", "Forest").forEach { landName ->
                val quantity = uiState.cards.filter { it.card?.name == landName }.sumOf { it.quantity }
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

@Composable
private fun BasicLandRow(name: String, quantityInDeck: Int, onAdd: () -> Unit, onRemove: () -> Unit, manaCode: () -> String?) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(shape = RoundedCornerShape(8.dp), color = mc.surface) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            manaCode()?.let { ManaSymbolImage(token = it, size = 24.dp) }
            Text(name, style = ty.bodyMedium, color = mc.textPrimary, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (quantityInDeck > 0) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Remove, null, tint = mc.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    Text("$quantityInDeck", style = ty.labelMedium, color = mc.primaryAccent)
                }
                IconButton(onClick = onAdd, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, null, tint = mc.primaryAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun AddBasicLandsRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        shape = RoundedCornerShape(8.dp), 
        color = mc.surface, 
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Park, null, tint = mc.primaryAccent, modifier = Modifier.size(20.dp))
            Text("Add Basic Lands", style = ty.bodyMedium, color = mc.primaryAccent, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Add, null, tint = mc.primaryAccent, modifier = Modifier.size(18.dp))
        }
    }
}

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
    ) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(bottom = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Edit Deck", style = ty.titleMedium, color = mc.textPrimary)
                Button(onClick = { onSave(nameText, selectedCoverId) }) { Text("Save") }
            }
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Deck Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = mc.primaryAccent, cursorColor = mc.primaryAccent)
            )
            
            val coverCards = cards.filter { it.card?.imageArtCrop != null }
            if (coverCards.isNotEmpty()) {
                Text("Pick Cover Art", style = ty.labelMedium, modifier = Modifier.padding(16.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    items(coverCards) { dc ->
                        val isSelected = dc.scryfallId == selectedCoverId
                        Box(
                            modifier = Modifier.aspectRatio(1.37f).padding(4.dp).clip(RoundedCornerShape(8.dp))
                                .clickable { selectedCoverId = dc.scryfallId }
                                .then(if (isSelected) Modifier.border(2.dp, mc.primaryAccent, RoundedCornerShape(8.dp)) else Modifier)
                        ) {
                            AsyncImage(
                                model = dc.card?.imageArtCrop,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun groupCards(cards: List<DeckSlotEntry>, mode: GroupingMode): List<Pair<String, List<DeckSlotEntry>>> {
    return when (mode) {
        GroupingMode.TYPE -> {
            val order = listOf("Creatures", "Instants", "Sorceries", "Enchantments", "Artifacts", "Planeswalkers", "Lands", "Other")
            val groups = cards.groupBy { entry ->
                val type = entry.card?.typeLine ?: ""
                when {
                    type.contains("Creature") -> "Creatures"
                    type.contains("Instant") -> "Instants"
                    type.contains("Sorcery") -> "Sorceries"
                    type.contains("Enchantment") -> "Enchantments"
                    type.contains("Artifact") -> "Artifacts"
                    type.contains("Planeswalker") -> "Planeswalkers"
                    type.contains("Land") -> "Lands"
                    else -> "Other"
                }
            }
            order.filter { it in groups }.map { it to (groups[it] ?: emptyList()) }
        }
        GroupingMode.COLOR -> {
            val order = listOf("W", "U", "B", "R", "G", "Multicolor", "Colorless", "Land")
            val groups = cards.groupBy { entry ->
                val card = entry.card ?: return@groupBy "Other"
                if (card.typeLine.contains("Land")) return@groupBy "Land"
                when (card.colors.size) {
                    0 -> "Colorless"
                    1 -> card.colors.first()
                    else -> "Multicolor"
                }
            }
            order.filter { it in groups }.map { it to (groups[it] ?: emptyList()) }
        }
        GroupingMode.COST -> {
            val nonLands = cards.filter { it.card != null && !BasicLandCalculator.isLand(it.card!!) }
            val groups = nonLands.groupBy { entry ->
                val cmc = entry.card?.cmc?.toInt()?.coerceIn(0, 7) ?: 0
                if (cmc == 7) "7+ Cost" else "$cmc Cost"
            }
            groups.entries.sortedBy { it.key }.map { it.key to it.value }
        }
        GroupingMode.TAG -> {
            val tagMap = mutableMapOf<String, MutableList<DeckSlotEntry>>()
            cards.forEach { entry ->
                val tags = (entry.card?.tags ?: emptyList()) + (entry.card?.userTags ?: emptyList())
                if (tags.isEmpty()) {
                    tagMap.getOrPut("Untagged") { mutableListOf() }.add(entry)
                } else {
                    tags.forEach { tag ->
                        tagMap.getOrPut(tag.label) { mutableListOf() }.add(entry)
                    }
                }
            }
            tagMap.entries.sortedByDescending { it.value.size }.map { it.key to it.value }
        }
    }
}
