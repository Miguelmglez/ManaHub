package com.mmg.manahub.feature.decks.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.DeckCard
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.ui.components.CardSearchSheet
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.GroupingFlowSelector
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.presentation.components.AddBasicLandsRow
import com.mmg.manahub.feature.decks.presentation.components.BasicLandsSheet
import com.mmg.manahub.feature.decks.presentation.components.BudgetInputBar
import com.mmg.manahub.feature.decks.presentation.components.CardRow
import com.mmg.manahub.feature.decks.presentation.components.CommanderBanner
import com.mmg.manahub.feature.decks.presentation.components.DeckSummaryCard
import com.mmg.manahub.feature.decks.presentation.components.EditDeckSheet
import com.mmg.manahub.feature.decks.presentation.components.GroupHeader
import com.mmg.manahub.feature.decks.presentation.components.MovementRow
import com.mmg.manahub.feature.decks.presentation.components.groupCards
import com.mmg.manahub.feature.decks.presentation.improvement.components.AddSuggestionRow
import com.mmg.manahub.feature.decks.presentation.improvement.components.CutSuggestionRow
import com.mmg.manahub.feature.decks.presentation.improvement.components.HealthScoreRing
import com.mmg.manahub.feature.decks.presentation.improvement.components.RoleCoverageRow
import com.mmg.manahub.feature.decks.presentation.improvement.components.WarningChip
import com.mmg.manahub.feature.decks.presentation.improvement.components.key
import com.mmg.manahub.feature.decks.presentation.improvement.components.label

/**
 * The unified "Deck Studio" editor surface (Phase 1).
 *
 * A 2-tab screen — Build (the live manual editor) and Suggestions (a Phase-2 Deck
 * Doctor stub) — driven entirely by [DeckStudioViewModel] against a single live
 * draft deck. Manual add/remove/move/basic-lands/commander/metadata/export all
 * write straight through the repository.
 *
 * Exit contract (U1/U2): both the top-bar back arrow and the system [BackHandler]
 * call [DeckStudioViewModel.onExitRequested] (NOT [onBack] directly) so an empty,
 * untouched draft is discarded before navigating away. An open sheet closes first.
 *
 * @param onBack pops the back stack (invoked by the VM after discard cleanup).
 * @param onCardClick opens a card detail screen for the given scryfallId.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckStudioScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    viewModel: DeckStudioViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val toastState = rememberMagicToastState()

    var showAddCardsSheet by remember { mutableStateOf(false) }
    var showCommanderSearchSheet by remember { mutableStateOf(false) }
    var showBasicLandsSheet by remember { mutableStateOf(false) }
    var showEditDeckSheet by remember { mutableStateOf(false) }

    val cardAddedMsg = stringResource(R.string.deck_studio_card_added)
    val cardCutMsg = stringResource(R.string.deck_studio_card_cut)
    val externalFailedMsg = stringResource(R.string.deck_studio_external_pool_failed)

    // One-shot events (buffered Channel; collected once, never via state).
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                DeckStudioEvent.NavigateBack -> Unit // navigation is invoked in the VM callback
                is DeckStudioEvent.ShowToast -> toastState.show(event.message, MagicToastType.INFO)
                is DeckStudioEvent.CardAdded ->
                    toastState.show(String.format(cardAddedMsg, event.cardName), MagicToastType.SUCCESS)
                is DeckStudioEvent.CardCut ->
                    toastState.show(String.format(cardCutMsg, event.cardName), MagicToastType.SUCCESS)
                DeckStudioEvent.ExternalPoolFailed ->
                    toastState.show(externalFailedMsg, MagicToastType.ERROR)
            }
        }
    }

    // Back behavior: an open sheet closes first; otherwise the VM handles the
    // discard-if-empty contract and then navigates back.
    val handleBack: () -> Unit = {
        focusManager.clearFocus()
        when {
            showAddCardsSheet -> { showAddCardsSheet = false; viewModel.clearAddCardsState() }
            showCommanderSearchSheet -> { showCommanderSearchSheet = false; viewModel.clearAddCardsState() }
            showBasicLandsSheet -> showBasicLandsSheet = false
            showEditDeckSheet -> showEditDeckSheet = false
            else -> viewModel.onExitRequested(onBack)
        }
    }
    BackHandler(onBack = handleBack)

    val isCommanderFormat = uiState.deck?.format
        ?.let { fmt -> DeckFormat.entries.firstOrNull { it.name.equals(fmt, ignoreCase = true) } } == DeckFormat.COMMANDER

    // Pre-resolved "coming soon" messages for the Phase 3/4 stub CTAs (cannot call
    // stringResource inside non-composable click lambdas).
    val seedComingSoonMsg = stringResource(R.string.deck_studio_seed_coming_soon)
    val inspirationsComingSoonMsg = stringResource(R.string.deck_studio_inspirations_coming_soon)

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.Scaffold(
            containerColor = mc.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                DeckStudioTopBar(
                    title = uiState.deck?.name ?: stringResource(R.string.deck_studio_title),
                    format = uiState.deck?.format,
                    onBack = handleBack,
                    onEdit = { showEditDeckSheet = true },
                    onShare = {
                        val text = viewModel.exportDeckToText()
                        if (text != null) {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.deckbuilder_share_chooser)))
                        }
                    },
                    shareEnabled = !uiState.isEmptyDeck,
                )
            },
            floatingActionButton = {
                // FAB only on the Build tab.
                AnimatedVisibility(
                    visible = uiState.selectedTab == DeckStudioTab.BUILD,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    FloatingActionButton(
                        onClick = {
                            viewModel.showCollectionCards()
                            showAddCardsSheet = true
                        },
                        containerColor = mc.primaryAccent,
                        contentColor = mc.background,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.deckdetail_add_basic_lands))
                    }
                }
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                val tabs = listOf(
                    DeckStudioTab.BUILD to stringResource(R.string.deck_studio_tab_build),
                    DeckStudioTab.SUGGESTIONS to stringResource(R.string.deck_studio_tab_suggestions),
                )
                TabRow(
                    selectedTabIndex = tabs.indexOfFirst { it.first == uiState.selectedTab }.coerceAtLeast(0),
                    containerColor = mc.backgroundSecondary,
                    contentColor = mc.primaryAccent,
                ) {
                    tabs.forEach { (tab, label) ->
                        Tab(
                            selected = uiState.selectedTab == tab,
                            onClick = { viewModel.onSelectTab(tab) },
                            text = {
                                Text(
                                    label,
                                    style = ty.labelLarge,
                                    color = if (uiState.selectedTab == tab) mc.primaryAccent else mc.textSecondary,
                                )
                            },
                        )
                    }
                }

                AnimatedContent(
                    targetState = uiState.selectedTab,
                    transitionSpec = {
                        fadeIn(tween(300)) togetherWith fadeOut(tween(150))
                    },
                    label = "DeckStudioTab",
                ) { tab ->
                    when (tab) {
                        DeckStudioTab.BUILD -> BuildTab(
                            uiState = uiState,
                            isCommanderFormat = isCommanderFormat,
                            viewModel = viewModel,
                            onCardClick = onCardClick,
                            onAddBasicLands = { showBasicLandsSheet = true },
                            onChooseCommander = {
                                viewModel.showCollectionCards()
                                showCommanderSearchSheet = true
                            },
                            onBuildFromSeed = {
                                // Seed flow lands in Phase 3 (P3-T1/P3-T2).
                                toastState.show(seedComingSoonMsg, MagicToastType.INFO)
                            },
                            onBrowseInspirations = {
                                // Discoveries land in Phase 4 (P4-T1).
                                toastState.show(inspirationsComingSoonMsg, MagicToastType.INFO)
                            },
                        )
                        DeckStudioTab.SUGGESTIONS -> SuggestionsTab(
                            uiState = uiState,
                            onPerCardBudgetChange = viewModel::onPerCardBudgetChange,
                            onTotalBudgetChange = viewModel::onTotalBudgetChange,
                            onOwnedFreeChange = viewModel::onOwnedCardsFreeChange,
                            onClearBudget = viewModel::onClearBudget,
                            onAdd = { s -> viewModel.onAddSuggestion(s.fit.card.scryfallId, s.fit.card.name) },
                            onCut = { fit -> viewModel.onCutSuggestion(fit.card.scryfallId, fit.card.name) },
                        )
                    }
                }
            }
        }

        MagicToastHost(
            state = toastState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
        )
    }

    // ── Sheets ──────────────────────────────────────────────────────────────────

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
        BasicLandsSheet(
            basicLandCounts = viewModel.basicLandCounts(),
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
                onCardClick(id)
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
            isCurrentCommander = { it == uiState.commanderCard?.scryfallId },
            offerTabLabel = stringResource(R.string.stats_tab_collection),
            allCardsTabLabel = stringResource(R.string.deckdetail_tab_scryfall),
            onQueryChange = viewModel::searchCommander,
            onScryfallSearch = viewModel::searchCommander,
            onAdd = { row ->
                focusManager.clearFocus()
                viewModel.setCommander(row.card)
                showCommanderSearchSheet = false
            },
            onRemove = { /* No-op in commander selection mode */ },
            onCardClick = { id ->
                focusManager.clearFocus()
                onCardClick(id)
            },
            onDismiss = {
                focusManager.clearFocus()
                showCommanderSearchSheet = false
                viewModel.clearAddCardsState()
            },
        )
    }
}

@Composable
private fun DeckStudioTopBar(
    title: String,
    format: String?,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    shareEnabled: Boolean,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(color = mc.backgroundSecondary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = mc.textSecondary)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = title,
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                format?.let { fmt ->
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
            // Inspirations (Discoveries) — wired in Phase 4; present-but-inert for now.
            IconButton(onClick = { /* Phase 4: Inspirations sheet */ }, enabled = false) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = mc.goldMtg.copy(alpha = 0.4f))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = mc.textSecondary)
            }
            IconButton(onClick = onShare, enabled = shareEnabled) {
                Icon(Icons.Default.Share, contentDescription = null, tint = mc.textSecondary)
            }
        }
    }
}

@Composable
private fun BuildTab(
    uiState: DeckStudioUiState,
    isCommanderFormat: Boolean,
    viewModel: DeckStudioViewModel,
    onCardClick: (String) -> Unit,
    onAddBasicLands: () -> Unit,
    onChooseCommander: () -> Unit,
    onBuildFromSeed: () -> Unit,
    onBrowseInspirations: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = mc.primaryAccent)
        }
        return
    }

    if (uiState.isEmptyDeck) {
        EmptyDeckState(onBuildFromSeed = onBuildFromSeed, onBrowseInspirations = onBrowseInspirations)
        return
    }

    val mainboardCards = uiState.cards.filter { !it.isSideboard }
    val sideboardCards = uiState.cards.filter { it.isSideboard }.sortedBy { it.card?.name }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") {
            val targetCount = if (isCommanderFormat) 100 else 60
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
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).animateItem(),
            )
        }

        item(key = "grouping_selector") {
            Column(Modifier.padding(horizontal = 16.dp).animateItem()) {
                GroupingFlowSelector(selected = uiState.groupingMode, onSelect = viewModel::setGroupingMode)
            }
        }

        if (isCommanderFormat) {
            item(key = "commander_section") {
                Column(modifier = Modifier.padding(horizontal = 16.dp).animateItem()) {
                    Text(
                        text = stringResource(R.string.deckbuilder_commander_label),
                        style = ty.titleMedium,
                        color = mc.goldMtg,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    val commander = uiState.commanderCard
                    if (commander?.card != null) {
                        CommanderBanner(
                            commander = commander.card!!,
                            modifier = Modifier.clickable { onCardClick(commander.scryfallId) },
                        )
                        Spacer(Modifier.height(4.dp))
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
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.deckbuilder_setup_commander_label), style = ty.labelLarge)
                        }
                    }
                }
            }
        }

        item(key = "mainboard_header") {
            SectionHeader(
                title = stringResource(R.string.deckdetail_tab_mainboard, mainboardCards.sumOf { it.quantity }),
                expanded = uiState.mainboardExpanded,
                onToggle = viewModel::toggleMainboard,
                modifier = Modifier.animateItem(),
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
                        modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                    )
                }
                if (isLandGroup) {
                    item(key = "main_lands_logic") {
                        AddBasicLandsRow(onClick = onAddBasicLands, modifier = Modifier.padding(horizontal = 16.dp).animateItem())
                    }
                }
                items(cards, key = { "main_${it.scryfallId}_$groupLabel" }) { entry ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = mc.backgroundSecondary,
                        border = BorderStroke(0.5.dp, mc.surfaceVariant),
                        modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                    ) {
                        Column {
                            CardRow(
                                entry = entry,
                                isInCollection = entry.scryfallId in uiState.collectionIds,
                                onClick = { onCardClick(entry.scryfallId) },
                                onRemove = { viewModel.removeCard(entry.scryfallId) },
                            )
                            if (!isCommanderFormat) {
                                val qtyInSideboard = uiState.cards.find { it.scryfallId == entry.scryfallId && it.isSideboard }?.quantity ?: 0
                                MovementRow(
                                    labelTo = stringResource(R.string.deckbuilder_move_to_sideboard),
                                    onMoveTo = { viewModel.moveQuantityToSideboard(entry.scryfallId) },
                                    labelFrom = if (qtyInSideboard > 0) stringResource(R.string.deckbuilder_from_sideboard) else null,
                                    onMoveFrom = if (qtyInSideboard > 0) {
                                        { viewModel.moveQuantityToMainboard(entry.scryfallId) }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isCommanderFormat) {
            item(key = "sideboard_header") {
                SectionHeader(
                    title = stringResource(R.string.deckdetail_tab_sideboard, sideboardCards.sumOf { it.quantity }),
                    expanded = uiState.sideboardExpanded,
                    onToggle = viewModel::toggleSideboard,
                    modifier = Modifier.animateItem(),
                )
            }
            if (uiState.sideboardExpanded) {
                if (sideboardCards.isEmpty()) {
                    item(key = "sideboard_empty") {
                        Text(
                            text = stringResource(R.string.deckbuilder_sideboard_empty),
                            style = ty.labelSmall,
                            color = mc.textDisabled,
                            modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp).animateItem(),
                        )
                    }
                } else {
                    items(sideboardCards, key = { "side_${it.scryfallId}" }) { entry ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = mc.backgroundSecondary,
                            border = BorderStroke(0.5.dp, mc.surfaceVariant),
                            modifier = Modifier.padding(horizontal = 16.dp).animateItem(),
                        ) {
                            Column {
                                CardRow(
                                    entry = entry,
                                    isInCollection = entry.scryfallId in uiState.collectionIds,
                                    onClick = { onCardClick(entry.scryfallId) },
                                    onRemove = { viewModel.removeCard(entry.scryfallId, true) },
                                )
                                MovementRow(
                                    labelTo = stringResource(R.string.deckbuilder_move_to_mainboard),
                                    onMoveTo = { viewModel.moveQuantityToMainboard(entry.scryfallId) },
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
private fun EmptyDeckState(
    onBuildFromSeed: () -> Unit,
    onBrowseInspirations: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = mc.goldMtg, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.deck_studio_empty_title), style = ty.titleMedium, color = mc.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.deck_studio_empty_subtitle),
            style = ty.bodyMedium,
            color = mc.textSecondary,
        )
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Button(
            onClick = onBuildFromSeed,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.deck_studio_build_from_seed), style = ty.labelLarge, color = mc.background)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onBrowseInspirations,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.deck_studio_browse_inspirations), style = ty.labelLarge, color = mc.primaryAccent)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "SectionHeaderRotation",
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
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

/**
 * The Suggestions surface (Deck Doctor inline, Phase 2): a Health summary, the Cut
 * list, the Add list, and the free-text [BudgetInputBar], all driven by the live
 * deck via [DeckStudioViewModel]. The row composables and string helpers are reused
 * verbatim from the standalone Deck Doctor screen
 * ([com.mmg.manahub.feature.decks.presentation.improvement.components]).
 *
 * Stateless: all state comes from [uiState]; every mutation is a callback to the VM.
 */
@Composable
private fun SuggestionsTab(
    uiState: DeckStudioUiState,
    onPerCardBudgetChange: (String) -> Unit,
    onTotalBudgetChange: (String) -> Unit,
    onOwnedFreeChange: (Boolean) -> Unit,
    onClearBudget: () -> Unit,
    onAdd: (AddSuggestion) -> Unit,
    onCut: (CardFit) -> Unit,
) {
    val mc = MaterialTheme.magicColors

    // First-open loading (the lazy full analysis kicked off by onSelectTab).
    if (uiState.isSuggestionsLoading && uiState.health == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = mc.primaryAccent)
        }
        return
    }

    val health = uiState.health
    if (health == null) {
        EmptyState(
            title = stringResource(R.string.deck_studio_suggestions_coming_soon_title),
            subtitle = stringResource(R.string.deck_studio_suggestions_coming_soon_subtitle),
            icon = Icons.Default.AutoAwesome,
        )
        return
    }

    val evaluation = health.evaluation

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Health summary ────────────────────────────────────────────────────
        item(key = "health_ring") {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                HealthScoreRing(score = evaluation.healthScore)
            }
        }
        item(key = "health_roles_header") {
            SuggestionsSectionHeader(stringResource(R.string.deck_health_section_roles), mc.primaryAccent)
        }
        items(evaluation.roleCoverage, key = { "role_${it.role.name}" }) { coverage ->
            RoleCoverageRow(coverage = coverage)
        }
        if (evaluation.warnings.isNotEmpty()) {
            item(key = "health_warnings_header") {
                SuggestionsSectionHeader(stringResource(R.string.deck_health_section_warnings), mc.lifeNegative)
            }
            items(evaluation.warnings, key = { "warn_${it.key}" }) { warning ->
                WarningChip(text = warning.label())
            }
        }

        // ── Cuts ────────────────────────────────────────────────────────────────
        item(key = "cuts_header") {
            SuggestionsSectionHeader(stringResource(R.string.deck_studio_suggestions_tab_cuts), mc.lifeNegative)
        }
        if (uiState.cuts.isEmpty()) {
            item(key = "cuts_empty") {
                Text(
                    text = stringResource(R.string.deck_doctor_cut_empty_title),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                )
            }
        } else {
            items(uiState.cuts, key = { "cut_${it.card.scryfallId}" }) { fit ->
                CutSuggestionRow(fit = fit, onCut = { onCut(fit) })
            }
        }

        // ── Budget + Adds ────────────────────────────────────────────────────────
        item(key = "adds_header") {
            SuggestionsSectionHeader(stringResource(R.string.deck_studio_suggestions_tab_adds), mc.lifePositive)
        }
        item(key = "budget_bar") {
            BudgetInputBar(
                perCardText = uiState.rawPerCardText,
                totalText = uiState.rawTotalText,
                ownedCardsAreFree = uiState.ownedCardsAreFree,
                hasError = uiState.budgetError,
                onPerCardChange = onPerCardBudgetChange,
                onTotalChange = onTotalBudgetChange,
                onOwnedFreeChange = onOwnedFreeChange,
                onClear = onClearBudget,
            )
        }
        item(key = "budget_summary") {
            val summaryText = if (uiState.addsCardsToBuy == 0) {
                stringResource(R.string.deck_doctor_budget_all_owned)
            } else {
                stringResource(
                    R.string.deck_doctor_budget_to_buy,
                    String.format(java.util.Locale.US, "%.2f", uiState.addsTotalCostEur),
                    uiState.addsCardsToBuy,
                )
            }
            Text(text = summaryText, style = MaterialTheme.magicTypography.bodySmall, color = mc.textSecondary)
        }
        when {
            uiState.isAddsLoading -> item(key = "adds_loading") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.CircularProgressIndicator(color = mc.primaryAccent)
                }
            }
            uiState.adds.isEmpty() -> item(key = "adds_empty") {
                Text(
                    text = stringResource(R.string.deck_doctor_add_empty_title),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                )
            }
            else -> items(uiState.adds, key = { "add_${it.fit.card.scryfallId}" }) { suggestion ->
                AddSuggestionRow(suggestion = suggestion, onAdd = { onAdd(suggestion) })
            }
        }
    }
}

@Composable
private fun SuggestionsSectionHeader(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.magicTypography.labelMedium,
        color = color,
        fontWeight = FontWeight.Bold,
    )
}
