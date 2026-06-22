package com.mmg.manahub.feature.decks.presentation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.model.BASIC_LAND_NAMES
import com.mmg.manahub.core.domain.model.DeckCard
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.domain.model.DeckSlotEntry
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.CardSearchSheet
import com.mmg.manahub.core.ui.components.GroupingFlowSelector
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.decks.presentation.components.AddBasicLandsRow
import com.mmg.manahub.feature.decks.presentation.components.BasicLandsSheet
import com.mmg.manahub.feature.decks.presentation.components.CardRow
import com.mmg.manahub.feature.decks.presentation.components.CommanderBanner
import com.mmg.manahub.feature.decks.presentation.components.DeckStatsCard
import com.mmg.manahub.feature.decks.presentation.components.DeckSummaryCard
import com.mmg.manahub.feature.decks.presentation.components.EditDeckSheet
import com.mmg.manahub.feature.decks.presentation.components.GroupHeader
import com.mmg.manahub.feature.decks.presentation.components.MagicLandSuggestionStatic
import com.mmg.manahub.feature.decks.presentation.components.MovementRow
import com.mmg.manahub.feature.decks.presentation.components.groupCards

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckMagicDetailScreen(
    onBack: () -> Unit,
    onImproveDeck: (String) -> Unit,
    onReviewSurvey: (Long) -> Unit = {},
    onPlaytest: (deckId: String) -> Unit = {},
    viewModel: DeckMagicDetailViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deckStats by viewModel.deckStatsFlow.collectAsStateWithLifecycle()
    val playerName by viewModel.playerNameFlow.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var showAddCardsSheet by remember { mutableStateOf(false) }
    var showBasicLandsSheet by remember { mutableStateOf(false) }
    var showEditDeckSheet by remember { mutableStateOf(false) }
    var showCommanderSearchSheet by remember { mutableStateOf(false) }
    var selectedCardId by remember { mutableStateOf<String?>(null) }
    // Tracks whether the CardDetailSheet was opened from the commander-selection flow,
    // so it can display the commander-specific action buttons instead of the +/- counter.
    var isCardDetailInCommanderContext by remember { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        focusManager.clearFocus()
        if (viewModel.onNavigatingBack()) onBack()
    }
    BackHandler(onBack = handleBack)

    val selectedDeckCard = remember(selectedCardId, uiState.cards, uiState.addCardsResults, uiState.scryfallResults, uiState.commanderCard) {
        selectedCardId?.let { id ->
            uiState.cards.find { it.scryfallId == id }
                ?: (if (uiState.commanderCard?.scryfallId == id) uiState.commanderCard else null)
                ?: (uiState.addCardsResults + uiState.scryfallResults).find { it.card.scryfallId == id }?.let { row ->
                    DeckSlotEntry(row.card.scryfallId, row.quantityInDeck, false, row.card)
                }
        }
    }

    Scaffold(
        containerColor      = mc.background,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(color = mc.backgroundSecondary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = mc.textSecondary)
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            text = uiState.deck?.name ?: stringResource(R.string.deckbuilder_title),
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

                    // Playtest button — launches setup screen for this deck.
                    uiState.deck?.id?.let { deckId ->
                        IconButton(onClick = { onPlaytest(deckId) }) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Playtest deck",
                                tint = mc.primaryAccent,
                            )
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
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.deckbuilder_share_chooser)))
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
            onCardClick = { id ->
                // Cards tapped from the main deck list are never in commander-selection context.
                isCardDetailInCommanderContext = false
                selectedCardId = id
            },
            onAddBasicLands = { showBasicLandsSheet = true },
            onChooseCommander = {
                viewModel.showCollectionCards()
                showCommanderSearchSheet = true
            },
            onReviewSurvey = onReviewSurvey,
            onReplaceCard = { card ->
                viewModel.onAddCardsQueryChange(card.name)
                showAddCardsSheet = true
            },
            deckStats = deckStats,
            playerName = playerName,
            modifier = Modifier.padding(padding)
        )
    }

    if (showEditDeckSheet) {
        EditDeckSheet(
            deck = uiState.deck,
            cards = uiState.cards,
            onSave = { newName, newCoverId ->
                focusManager.clearFocus()
                if (newName != null) viewModel.updateDeckName(newName)
                if (newCoverId != null) viewModel.setCoverCard(newCoverId)
                showEditDeckSheet = false
            },
            onDismiss = {
                focusManager.clearFocus()
                showEditDeckSheet = false
            },
        )
    }

    if (showBasicLandsSheet) {
        val basicLandCounts = remember(uiState.cards) {
            BASIC_LAND_NAMES.associateWith { landName ->
                uiState.cards.filter { it.card?.name == landName && !it.isSideboard }.sumOf { it.quantity }
            }
        }
        BasicLandsSheet(
            basicLandCounts = basicLandCounts,
            onAddBasicLand = viewModel::addBasicLandByName,
            onRemoveBasicLand = viewModel::removeBasicLandByName,
            manaCodeFor = viewModel::getManaCode,
            onDismiss = { showBasicLandsSheet = false },
        )
    }

    if (showAddCardsSheet) {
        CardSearchSheet(
            query = uiState.addCardsQuery,
            offerResults = emptyList(),
            addCardsResults = uiState.addCardsResults,
            scryfallResults = uiState.scryfallResults,
            isSearchingCards = uiState.isSearchingCards,
            isSearchingScryfall = uiState.isSearchingScryfall,
            isCommanderMode = false,
            isCurrentCommander = { it == uiState.commanderCard?.scryfallId },
            offerTabLabel = stringResource(R.string.stats_tab_collection),
            allCardsTabLabel = stringResource(R.string.deckdetail_tab_scryfall),
            onQueryChange = viewModel::onAddCardsQueryChange,
            onScryfallSearch = viewModel::searchScryfallDirect,
            onAdd = { row -> viewModel.addCardToDeck(row.card.scryfallId) },
            onRemove = { row -> viewModel.removeCardFromDeck(row.card.scryfallId) },
            onCardClick = { id ->
                focusManager.clearFocus()
                // Regular add-cards flow: no commander context.
                isCardDetailInCommanderContext = false
                selectedCardId = id
            },
            onDismiss = {
                focusManager.clearFocus()
                showAddCardsSheet = false
                viewModel.clearAddCardsState()
            },
        )
    }

    if (showCommanderSearchSheet) {
        CardSearchSheet(
            query = uiState.addCardsQuery,
            offerResults = emptyList(),
            addCardsResults = uiState.addCardsResults,
            scryfallResults = uiState.scryfallResults,
            isSearchingCards = uiState.isSearchingCards,
            isSearchingScryfall = uiState.isSearchingScryfall,
            isCommanderMode = true,
            offerTabLabel = stringResource(R.string.stats_tab_collection),
            allCardsTabLabel = stringResource(R.string.deckdetail_tab_scryfall),
            isCurrentCommander = { it == uiState.commanderCard?.scryfallId },
            onQueryChange = viewModel::searchCommander,
            onScryfallSearch = viewModel::searchCommander,
            onAdd = { row ->
                focusManager.clearFocus()
                // Direct add from the list row: resolve card and assign as commander.
                viewModel.setCommander(row.card)
                showCommanderSearchSheet = false
            },
            onRemove = { /* No-op in commander selection mode */ },
            onCardClick = { id ->
                focusManager.clearFocus()
                // Open detail sheet with the commander-selection context flag active.
                isCardDetailInCommanderContext = true
                selectedCardId = id
            },
            onDismiss = {
                focusManager.clearFocus()
                showCommanderSearchSheet = false
                viewModel.clearCommanderSearch()
            }
        )
    }

    if (selectedDeckCard != null) {
        // Load tags and other details
        LaunchedEffect(selectedDeckCard.scryfallId) {
            focusManager.clearFocus()
            viewModel.loadCardDetails(selectedDeckCard.scryfallId)
        }

        val isAlreadyCommander = uiState.commanderCard?.scryfallId == selectedDeckCard.scryfallId

        CardDetailSheet(
            deckCard = selectedDeckCard,
            isCommander = isAlreadyCommander,
            isCommanderSelectionContext = isCardDetailInCommanderContext,
            tags = uiState.detailTags,
            onAdd = {
                // In commander-selection context, "add" means "set as commander", not add to mainboard.
                if (isCardDetailInCommanderContext) {
                    selectedDeckCard.card?.let { card -> viewModel.setCommander(card) }
                    selectedCardId = null
                    isCardDetailInCommanderContext = false
                    showCommanderSearchSheet = false
                } else {
                    viewModel.addCardToDeck(selectedDeckCard.scryfallId, selectedDeckCard.isSideboard)
                }
            },
            onRemove = { viewModel.removeCardFromDeck(selectedDeckCard.scryfallId, selectedDeckCard.isSideboard) },
            onDelete = {
                viewModel.removeCard(selectedDeckCard.scryfallId, selectedDeckCard.isSideboard)
                selectedCardId = null
                isCardDetailInCommanderContext = false
            },
            onChooseAsCommander = { card ->
                viewModel.setCommander(card)
                selectedCardId = null
                isCardDetailInCommanderContext = false
                showCommanderSearchSheet = false
            },
            onRemoveCommander = {
                viewModel.removeCommander()
                selectedCardId = null
                isCardDetailInCommanderContext = false
            },
            onDismiss = {
                focusManager.clearFocus()
                selectedCardId = null
                isCardDetailInCommanderContext = false
            },
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
    onChooseCommander: () -> Unit,
    onReviewSurvey: (Long) -> Unit,
    onReplaceCard: (com.mmg.manahub.core.domain.model.Card) -> Unit,
    deckStats: com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase.Result?,
    playerName: String,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    AnimatedContent(
        targetState = uiState.isLoading,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "LoadingTransition"
    ) { isLoading ->
        if (isLoading) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }
        } else {
            Box(modifier.fillMaxSize()) {
                val mainboardCards = uiState.cards.filter { !it.isSideboard }
                val sideboardCards = uiState.cards.filter { it.isSideboard }.sortedBy { it.card?.name }
                val isCommanderFormat = viewModel.deckFormat == DeckFormat.COMMANDER

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "summary") {
                        val targetCount = when (viewModel.deckFormat) {
                            DeckFormat.COMMANDER -> 100
                            else -> 60
                        }
                        val maxInCurve = uiState.manaCurve.values.maxOrNull() ?: 0
                        val deckCards = (uiState.cards + listOfNotNull(uiState.commanderCard))
                            .filter { it.card != null && !it.isSideboard && !BasicLandCalculator.isLand(it.card!!) }
                            .map { DeckCard(it.card!!, it.quantity, it.scryfallId in uiState.collectionIds) }

                        DeckSummaryCard(
                            totalCards = uiState.totalCards,
                            targetCount = targetCount,
                            manaCurve = uiState.manaCurve,
                            maxInCurve = maxInCurve,
                            deckCards = deckCards,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).animateItem()
                        )
                    }

                    item(key = "deck_stats") {
                        DeckStatsCard(
                            stats = deckStats,
                            playerName = playerName,
                            onCardClick = onCardClick,
                            onReviewSurvey = onReviewSurvey,
                            onReplaceCard = onReplaceCard,
                            modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                        )
                    }

                    item(key = "grouping_selector") {
                        Column(Modifier.padding(horizontal = 16.dp).animateItem()) {
                            GroupingFlowSelector(
                                selected = uiState.groupingMode,
                                onSelect = viewModel::setGroupingMode
                            )
                        }
                    }

                    // ── Commander Section ─────────────────────────────────────────
                    if (isCommanderFormat) {
                        item(key = "commander_section") {
                            Column(modifier = Modifier.padding(horizontal = 16.dp).animateItem()) {
                                Text(
                                    text = stringResource(R.string.deckbuilder_commander_label),
                                    style = ty.titleMedium,
                                    color = mc.goldMtg,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                if (uiState.commanderCard != null) {
                                    uiState.commanderCard.card?.let { card ->
                                        CommanderBanner(
                                            commander = card,
                                            modifier = Modifier.clickable { onCardClick(uiState.commanderCard.scryfallId) }
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))

                                    WarningOverlay(uiState.commanderCard, uiState, viewModel, isCommander = true)
                                    TextButton(
                                        onClick = viewModel::removeCommander,
                                        modifier = Modifier.align(Alignment.CenterHorizontally),
                                    ) {
                                        Text(stringResource(R.string.deckbuilder_remove_commander), style = ty.labelLarge, color = mc.lifeNegative)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = onChooseCommander,
                                        modifier = Modifier.fillMaxWidth(),
                                        border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(stringResource(R.string.deckbuilder_setup_commander_label), style = ty.labelLarge)
                                    }
                                }
                            }
                        }
                    }

                    // ── Mainboard Section ─────────────────────────────────────────
                    item(key = "mainboard_header") {
                        MainSectionHeader(
                            title = stringResource(R.string.deckdetail_tab_mainboard, mainboardCards.sumOf { it.quantity }),
                            expanded = uiState.mainboardExpanded,
                            onToggle = viewModel::toggleMainboard,
                            modifier = Modifier.padding(horizontal = 0.dp).animateItem()
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
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = mc.backgroundSecondary,
                                    border = BorderStroke(0.5.dp, mc.surfaceVariant),
                                    modifier = Modifier.padding(horizontal = 16.dp).animateItem()
                                ) {
                                    Column {
                                        CardRow(
                                            entry = entry,
                                            isInCollection = entry.scryfallId in uiState.collectionIds,
                                            onClick = { onCardClick(entry.scryfallId) },
                                            onRemove = { viewModel.removeCard(entry.scryfallId) },
                                            modifier = Modifier
                                        )

                                        val qtyInSideboard = uiState.cards.find { it.scryfallId == entry.scryfallId && it.isSideboard }?.quantity ?: 0
                                        MovementRow(
                                            labelTo = stringResource(R.string.deckbuilder_move_to_sideboard),
                                            onMoveTo = { viewModel.moveQuantityToSideboard(entry.scryfallId, 1) },
                                            labelFrom = if (qtyInSideboard > 0) stringResource(R.string.deckbuilder_from_sideboard) else null,
                                            onMoveFrom = if (qtyInSideboard > 0) {
                                                { viewModel.moveQuantityToMainboard(entry.scryfallId, 1) }
                                            } else null,
                                            modifier = Modifier
                                        )

                                        WarningOverlay(entry, uiState, viewModel)
                                    }
                                }
                            }
                        }
                    }

                    // ── Sideboard Section ─────────────────────────────────────────
                    item(key = "sideboard_header") {
                        MainSectionHeader(
                            title = stringResource(R.string.deckdetail_tab_sideboard, sideboardCards.sumOf { it.quantity }),
                            expanded = uiState.sideboardExpanded,
                            onToggle = viewModel::toggleSideboard,
                            modifier = Modifier.padding(horizontal = 0.dp).animateItem()
                        )
                    }

                    if (uiState.sideboardExpanded) {
                        if (sideboardCards.isEmpty()) {
                            item(key = "sideboard_empty") {
                                Text(
                                    text = stringResource(R.string.deckbuilder_sideboard_empty),
                                    style = ty.labelSmall,
                                    color = mc.textDisabled,
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp).animateItem()
                                )
                            }
                        } else {
                            items(sideboardCards, key = { "side_" + it.scryfallId }) { entry ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = mc.backgroundSecondary,
                                    border = BorderStroke(0.5.dp, mc.surfaceVariant),
                                    modifier = Modifier.padding(horizontal = 16.dp).animateItem()
                                ) {
                                    Column {
                                        CardRow(
                                            entry = entry,
                                            isInCollection = entry.scryfallId in uiState.collectionIds,
                                            onClick = { onCardClick(entry.scryfallId) },
                                            onRemove = { viewModel.removeCard(entry.scryfallId, true) },
                                            modifier = Modifier
                                        )
                                        val qtyInMainboard = uiState.cards.find { it.scryfallId == entry.scryfallId && !it.isSideboard }?.quantity ?: 0
                                        MovementRow(
                                            labelTo = stringResource(R.string.deckbuilder_move_to_mainboard),
                                            onMoveTo = { viewModel.moveQuantityToMainboard(entry.scryfallId, 1) },
                                            labelFrom = if (qtyInMainboard > 0) stringResource(R.string.deckbuilder_from_sideboard) else null,
                                            onMoveFrom = if (qtyInMainboard > 0) {
                                                { viewModel.moveQuantityToSideboard(entry.scryfallId, 1) }
                                            } else null,
                                            modifier = Modifier
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

@Composable
private fun WarningOverlay(
    entry: DeckSlotEntry,
    uiState: DeckMagicDetailUiState,
    viewModel: DeckMagicDetailViewModel,
    isCommander: Boolean = false
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val isOverLimit = entry.scryfallId in uiState.overLimitCards
    val isInvalidIdentity = entry.scryfallId in uiState.invalidColorIdentityCards
    val isNonLegendaryCommander = isCommander && uiState.isCommanderInvalid

    AnimatedVisibility(
        visible = isOverLimit || isInvalidIdentity || isNonLegendaryCommander,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        val isAck = entry.scryfallId in uiState.acknowledgedOverLimitCards
        val limitInt = viewModel.deckFormat?.maxCopies ?: 4
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (isAck) mc.surface else mc.lifeNegative.copy(alpha = 0.1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val warningText = when {
                    isNonLegendaryCommander -> stringResource(R.string.deckbuilder_invalid_commander_legendary)
                    isOverLimit && isInvalidIdentity -> stringResource(R.string.deckbuilder_error_limit_and_identity, limitInt)
                    isOverLimit -> stringResource(R.string.deckbuilder_copy_warning, limitInt)
                    else -> stringResource(R.string.deckbuilder_error_invalid_identity)
                }
                Text(
                    warningText,
                    style = ty.labelSmall,
                    color = if (isAck) mc.textSecondary else mc.lifeNegative,
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
    ) {
        Text(title, style = ty.titleMedium, color = mc.primaryAccent, fontWeight = FontWeight.Bold)

        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = mc.primaryAccent,
            modifier = Modifier.graphicsLayer { rotationZ = rotation }
        )
    }
}



@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CardDetailSheet(
    deckCard: DeckSlotEntry,
    /** True when this card IS the current deck commander. */
    isCommander: Boolean,
    /**
     * True when the sheet was opened from the "Choose Commander" search flow.
     * In this context the +/- counter is hidden and commander-specific action
     * buttons are shown instead.
     */
    isCommanderSelectionContext: Boolean,
    tags: List<com.mmg.manahub.core.domain.model.CardTag>,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
    /** Called with the resolved Card when the user taps "Choose as commander". */
    onChooseAsCommander: (com.mmg.manahub.core.domain.model.Card) -> Unit,
    /** Called when the user taps "Remove commander" inside the commander-selection context. */
    onRemoveCommander: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val card = deckCard.card
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    var showBackFace by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = mc.textSecondary)
                }
            }
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
                        CardName(
                            name          = displayName,
                            showFrontOnly = true,
                            style         = ty.titleMedium,
                            color         = mc.textPrimary,
                            fontWeight    = FontWeight.Bold,
                            modifier      = Modifier.weight(1f)
                        )
                        card.manaCost?.let { ManaCostImages(manaCost = it, symbolSize = 18.dp) }
                    }
                    val typeLine = card.printedTypeLine?.takeIf { it.isNotBlank() } ?: card.typeLine
                    Text(typeLine, style = ty.labelMedium, color = mc.textSecondary)
                    HorizontalDivider(color = mc.surfaceVariant)

                    val oracleDisplayText = card.oracleText?.takeIf { it.isNotBlank() } ?: card.printedText ?: ""
                    OracleText(text = oracleDisplayText, style = MaterialTheme.magicTypography.bodySmall)

                    // Tag section
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tags.forEach { tag ->
                                Surface(
                                    color = mc.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp),
                                    border = BorderStroke(0.5.dp, mc.primaryAccent.copy(alpha = 0.2f))
                                ) {
                                    Text(
                                        text = tag.label(),
                                        style = ty.labelSmall,
                                        color = mc.textSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Action area ───────────────────────────────────────────────────
            when {
                // Case 1: This sheet is in the "Choose Commander" search flow.
                isCommanderSelectionContext -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isCommander) {
                            // Card is already the commander — show current status badge.
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = mc.goldMtg.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(Icons.Default.Star, null, tint = mc.goldMtg, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.deckbuilder_commander_label).uppercase(),
                                        style = ty.labelLarge,
                                        color = mc.goldMtg,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            // Remove commander option below the status badge.
                            OutlinedButton(
                                onClick = onRemoveCommander,
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                border = BorderStroke(1.dp, mc.lifeNegative.copy(alpha = 0.6f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = mc.lifeNegative, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.deckbuilder_remove_commander),
                                    color = mc.lifeNegative,
                                    style = ty.bodyMedium
                                )
                            }
                        } else {
                            // Card is not yet the commander — golden "Choose as commander" CTA.
                            if (card != null) {
                                Button(
                                    onClick = { onChooseAsCommander(card) },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = mc.goldMtg),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Star, null, tint = mc.background, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.deckbuilder_choose_as_commander),
                                        style = ty.titleMedium,
                                        color = mc.background,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Case 2: Card is the current commander, viewed from the normal deck list.
                isCommander -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = mc.goldMtg.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Star, null, tint = mc.goldMtg, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.deckbuilder_commander_label).uppercase(),
                                style = ty.labelLarge,
                                color = mc.goldMtg,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Case 3: Regular card in the deck — show the +/- quantity counter.
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.deckdetail_in_deck_label),
                            style = ty.labelMedium,
                            color = mc.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onRemove, enabled = deckCard.quantity > 0) {
                            Icon(
                                Icons.Default.Remove,
                                contentDescription = null,
                                tint = if (deckCard.quantity > 0) mc.primaryAccent else mc.textDisabled
                            )
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = mc.primaryAccent.copy(alpha = 0.15f)) {
                            Text(
                                text = "${deckCard.quantity}",
                                style = ty.titleMedium,
                                color = mc.primaryAccent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        IconButton(onClick = onAdd) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = mc.primaryAccent)
                        }
                    }
                }
            }

            // Delete / remove-all button — always visible except in pure commander-selection context.
            if (!isCommanderSelectionContext) {
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
}

