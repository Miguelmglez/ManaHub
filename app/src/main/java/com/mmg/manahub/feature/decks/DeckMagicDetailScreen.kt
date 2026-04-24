package com.mmg.manahub.feature.decks

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.feature.decks.components.DeckSummaryCard
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.AddCardRow
import com.mmg.manahub.core.domain.model.BASIC_LAND_NAMES
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.DeckCard
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.core.domain.model.DeckSlotEntry
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.feature.addcard.AdvancedSearchSheet
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.feature.decks.components.MagicLandSuggestionStatic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckMagicDetailScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    onImproveDeck: (String) -> Unit,
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

    val handleBack = remember(viewModel) {
        {
            viewModel.onNavigatingBack()
            onBack()
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = mc.textSecondary)
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            text = uiState.deck?.name ?: "Deck Magic",
                            style = ty.titleMedium,
                            color = mc.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                Icon(Icons.Default.Add, contentDescription = null)
            }
        }
    ) { padding ->
        ViewStepContent(
            uiState = uiState,
            viewModel = viewModel,
            onCardClick = { selectedCardId = it },
            onAddBasicLands = { showBasicLandsSheet = true },
            modifier = Modifier.padding(padding)
        )
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
            onAdd = { viewModel.addCardToDeck(selectedDeckCard.scryfallId, selectedDeckCard.isSideboard) },
            onRemove = { viewModel.removeCardFromDeck(selectedDeckCard.scryfallId, selectedDeckCard.isSideboard) },
            onDelete = {
                viewModel.removeCard(selectedDeckCard.scryfallId, selectedDeckCard.isSideboard)
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
    onAddBasicLands: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    if (uiState.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = mc.primaryAccent)
        }
    } else {
        Box(modifier.fillMaxSize()) {
            val mainboardCards = uiState.cards.filter { !it.isSideboard }
            val sideboardCards = uiState.cards.filter { it.isSideboard }.sortedBy { it.card?.name }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    val targetCount = when (viewModel.deckFormat) {
                        DeckFormat.COMMANDER -> 100
                        else -> 60
                    }
                    val maxInCurve = uiState.manaCurve.values.maxOrNull() ?: 0
                    val deckCards = uiState.cards.filter { it.card != null && !it.isSideboard && !BasicLandCalculator.isLand(it.card!!) }.map {
                        DeckCard(it.card!!, it.quantity, it.scryfallId in uiState.collectionIds)
                    }
                    
                    DeckSummaryCard(
                        totalCards = uiState.totalCards,
                        targetCount = targetCount,
                        manaCurve = uiState.manaCurve,
                        maxInCurve = maxInCurve,
                        deckCards = deckCards,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }

                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        GroupingFlowSelector(
                            selected = uiState.groupingMode,
                            onSelect = viewModel::setGroupingMode
                        )
                    }
                }

                // ── Mainboard Section ─────────────────────────────────────────
                item {
                    MainSectionHeader(
                        title = stringResource(R.string.deckdetail_tab_mainboard, mainboardCards.sumOf { it.quantity }),
                        expanded = uiState.mainboardExpanded,
                        onToggle = viewModel::toggleMainboard,
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )
                }

                if (uiState.mainboardExpanded) {
                    val groupedMain = groupCards(mainboardCards, uiState.groupingMode)
                    groupedMain.forEach { (groupLabel, cards) ->
                        val isLandGroup = groupLabel == "Lands" || groupLabel == "Land"
                        item(key = "main_header_$groupLabel") {
                            GroupHeader(
                                label = groupLabel, 
                                count = cards.sumOf { it.quantity },
                                showSuggestionToggle = isLandGroup,
                                isSuggestionEnabled = uiState.showLandSuggestions,
                                onToggleSuggestion = viewModel::toggleLandSuggestions,
                                modifier = Modifier.padding(horizontal = 16.dp).animateItem()
                            )
                        }
                        
                        if (isLandGroup) {
                            item(key = "main_lands_logic") {
                                Column(Modifier.animateItem()) {
                                    AnimatedVisibility(
                                        visible = uiState.showLandSuggestions && uiState.landDeltas.isNotEmpty(),
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        MagicLandSuggestionStatic(
                                            deltas = uiState.landDeltas,
                                            onClick = viewModel::applyLandSuggestions,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                        )
                                    }
                                    AddBasicLandsRow(onClick = onAddBasicLands, modifier = Modifier.padding(horizontal = 16.dp))
                                }
                            }
                        }

                        items(cards, key = { "main_" + it.scryfallId + groupLabel }) { entry ->
                            Column(Modifier.animateItem()) {
                                CardRow(
                                    entry = entry,
                                    isInCollection = entry.scryfallId in uiState.collectionIds,
                                    onClick = { onCardClick(entry.scryfallId) },
                                    onRemove = { viewModel.removeCard(entry.scryfallId) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                
                                val qtyInSideboard = uiState.cards.find { it.scryfallId == entry.scryfallId && it.isSideboard }?.quantity ?: 0
                                MovementRow(
                                    labelTo = "1x To Sideboard",
                                    onMoveTo = { viewModel.moveQuantityToSideboard(entry.scryfallId, 1) },
                                    labelFrom = if (qtyInSideboard > 0) "1x From Sideboard" else null,
                                    onMoveFrom = if (qtyInSideboard > 0) {
                                        { viewModel.moveQuantityToMainboard(entry.scryfallId, 1) }
                                    } else null,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                
                                WarningOverlay(entry, uiState, viewModel)
                            }
                        }
                    }
                }

                // ── Sideboard Section ─────────────────────────────────────────
                item {
                    MainSectionHeader(
                        title = stringResource(R.string.deckdetail_tab_sideboard, sideboardCards.sumOf { it.quantity }),
                        expanded = uiState.sideboardExpanded,
                        onToggle = viewModel::toggleSideboard,
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )
                }

                if (uiState.sideboardExpanded) {
                    if (sideboardCards.isEmpty()) {
                        item {
                            Text(
                                text = "No cards in sideboard",
                                style = ty.labelSmall,
                                color = mc.textDisabled,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp).animateItem()
                            )
                        }
                    } else {
                        items(sideboardCards, key = { "side_" + it.scryfallId }) { entry ->
                            Column(Modifier.animateItem()) {
                                CardRow(
                                    entry = entry,
                                    isInCollection = entry.scryfallId in uiState.collectionIds,
                                    onClick = { onCardClick(entry.scryfallId) },
                                    onRemove = { viewModel.removeCard(entry.scryfallId, true) },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                val qtyInMainboard = uiState.cards.find { it.scryfallId == entry.scryfallId && !it.isSideboard }?.quantity ?: 0
                                MovementRow(
                                    labelTo = "1x To Mainboard",
                                    onMoveTo = { viewModel.moveQuantityToMainboard(entry.scryfallId, 1) },
                                    labelFrom = if (qtyInMainboard > 0) "1x From Mainboard" else null,
                                    onMoveFrom = if (qtyInMainboard > 0) {
                                        { viewModel.moveQuantityToSideboard(entry.scryfallId, 1) }
                                    } else null,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { viewModel.saveDeck { /* No-op, we stay on screen */ } },
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
                                    style = ty.titleMedium,
                                    color = if (uiState.totalCards > 0) mc.background else mc.textDisabled,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningOverlay(
    entry: DeckSlotEntry,
    uiState: DeckMagicDetailUiState,
    viewModel: DeckMagicDetailViewModel
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    
    if (entry.scryfallId in uiState.overLimitCards) {
        val isAck = entry.scryfallId in uiState.acknowledgedOverLimitCards
        val limitInt = viewModel.deckFormat?.maxCopies ?: 4
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isAck) mc.surface else mc.lifeNegative.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.deckbuilder_copy_warning, limitInt),
                    style = ty.labelSmall,
                    color = if (isAck) mc.textDisabled else mc.lifeNegative,
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = isAck,
                    onCheckedChange = { if (it) viewModel.acknowledgeOverLimit(entry.scryfallId) else viewModel.unacknowledgeOverLimit(entry.scryfallId) },
                    colors = CheckboxDefaults.colors(checkedColor = mc.primaryAccent, uncheckedColor = mc.lifeNegative)
                )
            }
        }
    }
}

@Composable
private fun MainSectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "HeaderRotation"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = ty.titleMedium, color = mc.primaryAccent, fontWeight = FontWeight.Bold)
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = mc.primaryAccent,
            modifier = Modifier.graphicsLayer { rotationZ = rotation }
        )
    }
}



@Composable
private fun MovementRow(
    labelTo: String,
    onMoveTo: () -> Unit,
    labelFrom: String? = null,
    onMoveFrom: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Action: Move To [Other]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onMoveTo)
                .padding(vertical = 4.dp, horizontal = 4.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.CompareArrows,
                null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(labelTo, style = ty.labelSmall, color = mc.primaryAccent)
        }

        // Right Action: Move From [Other] (if applicable)
        if (labelFrom != null && onMoveFrom != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onMoveFrom)
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            ) {
                Text(labelFrom, style = ty.labelSmall, color = mc.secondaryAccent)
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Filled.CompareArrows,
                    null,
                    tint = mc.secondaryAccent,
                    modifier = Modifier.size(16.dp).graphicsLayer { rotationY = 180f }
                )
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
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
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
                val text = if (isSuggestionEnabled) "Disable suggestions" else "Enable suggestions"
                Text(text = text, style = ty.labelSmall, color = mc.primaryAccent)
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
    deckFormat: DeckFormat?,
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
                        val displayName = card.printedName?.takeIf { it.isNotBlank() } ?: card.name
                        Text(displayName, style = ty.titleMedium, color = mc.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        card.manaCost?.let { ManaCostImages(manaCost = it, symbolSize = 18.dp) }
                    }
                    val typeLine = card.printedTypeLine?.takeIf { it.isNotBlank() } ?: card.typeLine
                    Text(typeLine, style = ty.labelMedium, color = mc.textSecondary)
                    HorizontalDivider(color = mc.surfaceVariant)
                    
                    val oracleDisplayText = card.oracleText?.takeIf { it.isNotBlank() } ?: card.printedText ?: ""
                    OracleText(text = oracleDisplayText, style = MaterialTheme.typography.bodySmall)
                    
                    val preferredCurrency = LocalPreferredCurrency.current
                    val priceText = PriceFormatter.formatFromScryfall(card.priceUsd, card.priceEur, preferredCurrency)
                    if (priceText != "—") {
                        Text(priceText, style = ty.labelMedium, color = mc.goldMtg, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.deckdetail_in_deck_label), style = ty.labelMedium, color = mc.textSecondary, modifier = Modifier.weight(1f))
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
                border = BorderStroke(1.dp, mc.lifeNegative.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = mc.lifeNegative, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_remove_all), color = mc.lifeNegative, style = ty.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCardsSheet(
    uiState: DeckMagicDetailUiState,
    format: DeckFormat?,
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
            Text(stringResource(R.string.deckbuilder_add_cards), style = ty.titleMedium, color = mc.textPrimary, modifier = Modifier.padding(16.dp))

            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.addCardsQuery,
                    onValueChange = { if (selectedTab == 0) onQueryChange(it) else onScryfallSearch(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.deckbuilder_add_cards_search_hint), color = mc.textDisabled) },
                    leadingIcon = {
                        val isSearching = if (selectedTab == 0) uiState.isSearchingCards else uiState.isSearchingScryfall
                        if (isSearching) CircularProgressIndicator(Modifier.size(20.dp), color = mc.primaryAccent, strokeWidth = 2.dp)
                        else Icon(Icons.Default.Search, null, tint = mc.textSecondary)
                    },
                    trailingIcon = if (uiState.addCardsQuery.isNotEmpty()) {
                        { IconButton(onClick = { onQueryChange(""); onScryfallSearch("") }) { Icon(Icons.Default.Clear, null, tint = mc.textSecondary) } }
                    } else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = mc.primaryAccent, unfocusedBorderColor = mc.surfaceVariant, cursorColor = mc.primaryAccent)
                )
                IconButton(onClick = { showAdvancedSearch = true }, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(mc.surface)) {
                    Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.advsearch_title), tint = mc.primaryAccent)
                }
            }

            TabRow(selectedTabIndex = selectedTab, containerColor = mc.backgroundSecondary, contentColor = mc.primaryAccent) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.deckbuilder_tab_collection), style = ty.labelLarge) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.deckdetail_tab_scryfall), style = ty.labelLarge) })
            }

            val results = if (selectedTab == 0) uiState.addCardsResults else uiState.scryfallResults
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Spacer(Modifier.height(8.dp)) }
                
                items(results, key = { it.card.scryfallId }) { row ->
                    AddCardSheetRow(
                        row = row,
                        format = format,
                        onAdd = { onAdd(row.card.scryfallId) },
                        onRemove = { onRemove(row.card.scryfallId) },
                        onClick = { onCardClick(row.card.scryfallId) }
                    )
                }

                // Empty State from DeckDetail
                if (results.isEmpty()) {
                    val isSearching = if (selectedTab == 0) uiState.isSearchingCards else uiState.isSearchingScryfall
                    if (!isSearching) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (selectedTab == 1 && uiState.addCardsQuery.isBlank())
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

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent), shape = RoundedCornerShape(8.dp)) {
                Text(stringResource(R.string.deckbuilder_add_cards_done), style = ty.titleMedium, color = mc.background)
            }
        }
    }
}

@Composable
private fun AddCardSheetRow(
    row: AddCardRow,
    format: DeckFormat?,
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
            Text(stringResource(R.string.deckdetail_basic_lands_sheet_title), style = ty.titleMedium, color = mc.textPrimary, modifier = Modifier.padding(vertical = 8.dp))
            BASIC_LAND_NAMES.forEach { landName ->
                val quantity = uiState.cards.filter { it.card?.name == landName && !it.isSideboard }.sumOf { it.quantity }
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
            Text(stringResource(R.string.deckdetail_add_basic_lands), style = ty.bodyMedium, color = mc.primaryAccent, modifier = Modifier.weight(1f))
            Icon(Icons.Default.Add, null, tint = mc.primaryAccent, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditDeckSheet(
    deck: Deck?,
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
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(bottom = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.deck_edit_title), style = ty.titleMedium, color = mc.textPrimary)
                Button(onClick = { onSave(nameText, selectedCoverId) }, colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent), shape = RoundedCornerShape(8.dp)) {
                    Text(stringResource(R.string.action_save), style = ty.labelLarge, color = mc.background)
                }
            }
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text(stringResource(R.string.deckbuilder_setup_name_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = mc.primaryAccent, cursorColor = mc.primaryAccent)
            )
            
            val coverCards = remember(cards) {
                cards.filter { it.card?.imageArtCrop != null }.distinctBy { it.scryfallId }
            }
            if (coverCards.isNotEmpty()) {
                Text(stringResource(R.string.deck_edit_cover_section), style = ty.labelMedium, modifier = Modifier.padding(16.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(coverCards, key = { "cover_" + it.scryfallId }) { dc ->
                        val isSelected = dc.scryfallId == selectedCoverId
                        Box(
                            modifier = Modifier.aspectRatio(1.37f).clip(RoundedCornerShape(8.dp))
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
            order.mapNotNull { label ->
                val list = groups[label]?.sortedBy { it.card?.name } ?: emptyList()
                if (list.isEmpty() && label != "Lands") null
                else label to list
            }
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
            order.mapNotNull { label ->
                val list = groups[label]?.sortedBy { it.card?.name } ?: emptyList()
                if (list.isEmpty() && label != "Land") null
                else label to list
            }
        }
        GroupingMode.COST -> {
            val nonLands = cards.filter { it.card != null && !BasicLandCalculator.isLand(it.card!!) }
            val groups = nonLands.groupBy { entry ->
                val cmc = entry.card?.cmc?.toInt()?.coerceIn(0, 7) ?: 0
                if (cmc == 7) "7+ Cost" else "$cmc Cost"
            }
            groups.entries.filter { it.value.isNotEmpty() }.sortedBy { it.key }.map { it.key to it.value.sortedBy { it.card?.name } }
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
            tagMap.entries.filter { it.value.isNotEmpty() }.sortedByDescending { it.value.size }.map { it.key to it.value.sortedBy { it.card?.name } }
        }
    }
}
