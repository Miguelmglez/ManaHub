package com.mmg.manahub.feature.decks.presentation

import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.domain.model.DeckCard
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.domain.model.DeckSlotEntry
import com.mmg.manahub.core.model.GroupingMode
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase
import com.mmg.manahub.core.ui.components.CardSearchSheet
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.GroupingFlowSelector
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckSkeletons
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.presentation.components.AddBasicLandsRow
import com.mmg.manahub.feature.decks.presentation.components.BasicLandsSheet
import com.mmg.manahub.feature.decks.presentation.components.BudgetInputBar
import com.mmg.manahub.feature.decks.presentation.components.CardDetailSheet
import com.mmg.manahub.feature.decks.presentation.components.CardRow
import com.mmg.manahub.feature.decks.presentation.components.CommanderBanner
import com.mmg.manahub.feature.decks.presentation.components.DeckFormatChipRow
import com.mmg.manahub.feature.decks.presentation.components.DeckImportSheet
import com.mmg.manahub.feature.decks.presentation.components.DeckStatsCard
import com.mmg.manahub.feature.decks.presentation.components.DeckSummaryCard
import com.mmg.manahub.feature.decks.presentation.components.DiscoveryRow
import com.mmg.manahub.feature.decks.presentation.components.EditDeckSheet
import com.mmg.manahub.feature.decks.presentation.components.GroupHeader
import com.mmg.manahub.feature.decks.presentation.components.MagicLandSuggestionStatic
import com.mmg.manahub.feature.decks.presentation.components.MovementRow
import com.mmg.manahub.feature.decks.presentation.components.SeedsContent
import com.mmg.manahub.feature.decks.presentation.components.WarningOverlay
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
 * @param onPlaytest opens the playtest setup for the given (non-empty) deck id.
 * @param onReviewSurvey opens the post-game survey in REVIEW mode for a session id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckStudioScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    onPlaytest: (deckId: String) -> Unit,
    onReviewSurvey: (sessionId: Long) -> Unit,
    viewModel: DeckStudioViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deckStats by viewModel.deckStatsFlow.collectAsStateWithLifecycle()
    val playerName by viewModel.playerNameFlow.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val toastState = rememberMagicToastState()

    var showAddCardsSheet by remember { mutableStateOf(false) }
    var showCommanderSearchSheet by remember { mutableStateOf(false) }
    var showBasicLandsSheet by remember { mutableStateOf(false) }
    var showEditDeckSheet by remember { mutableStateOf(false) }
    var showImportSheet by remember { mutableStateOf(false) }
    // C3: the inline CardDetailSheet target (a scryfallId from a deck-list / commander tap).
    var selectedCardId by remember { mutableStateOf<String?>(null) }
    // True when the detail sheet was opened from the commander-selection flow (shows the
    // commander-specific actions instead of the +/- counter).
    var isCardDetailInCommanderContext by remember { mutableStateOf(false) }

    // C3: resolve the tapped card to a DeckSlotEntry from the deck list, commander, or search results.
    val selectedDeckCard = remember(
        selectedCardId,
        uiState.cards,
        uiState.addCardsResults,
        uiState.scryfallResults,
        uiState.commanderCard,
    ) {
        selectedCardId?.let { id ->
            uiState.cards.find { it.scryfallId == id }
                ?: uiState.commanderCard?.takeIf { it.scryfallId == id }
                ?: (uiState.addCardsResults + uiState.scryfallResults)
                    .find { it.card.scryfallId == id }
                    ?.let { row -> DeckSlotEntry(row.card.scryfallId, row.quantityInDeck, false, row.card) }
        }
    }

    val cardAddedMsg = stringResource(R.string.deck_studio_card_added)
    val cardCutMsg = stringResource(R.string.deck_studio_card_cut)
    val externalFailedMsg = stringResource(R.string.deck_studio_external_pool_failed)
    val seedBuiltMsg = stringResource(R.string.deck_studio_seed_built)

    // Screen-entry breadcrumb (no PII).
    LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("screen_viewed: deck_studio")
    }

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
            // C3: the inline detail sheet sits on top of everything (incl. the commander
            // search sheet), so it must close first.
            selectedCardId != null -> { selectedCardId = null; isCardDetailInCommanderContext = false }
            showAddCardsSheet -> { showAddCardsSheet = false; viewModel.clearAddCardsState() }
            showCommanderSearchSheet -> { showCommanderSearchSheet = false; viewModel.clearAddCardsState() }
            showBasicLandsSheet -> showBasicLandsSheet = false
            showEditDeckSheet -> showEditDeckSheet = false
            showImportSheet -> showImportSheet = false
            else -> viewModel.onExitRequested(onBack)
        }
    }
    BackHandler(onBack = handleBack)

    val isCommanderFormat = uiState.deck?.format
        ?.let { fmt -> DeckFormat.entries.firstOrNull { it.name.equals(fmt, ignoreCase = true) } } == DeckFormat.COMMANDER

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.Scaffold(
            containerColor = mc.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                DeckStudioTopBar(
                    title = uiState.deck?.name ?: stringResource(R.string.deck_studio_title),
                    format = uiState.deck?.format,
                    onBack = handleBack,
                    // Playtest is available only for a non-empty, persisted deck.
                    playtestEnabled = !uiState.isEmptyDeck && uiState.deck?.id != null,
                    onPlaytest = { uiState.deck?.id?.let(onPlaytest) },
                    onBuildFromSeed = { viewModel.openSeedSheet() },
                    onBrowseInspirations = { viewModel.openInspirations() },
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
                        shape = CardShape,
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.deck_studio_add_card_fab))
                    }
                }
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // The Suggestions tab is HIDDEN for release behind
                // DeckFeatureFlags.DECK_STUDIO_SUGGESTIONS_TAB_ENABLED (UI-only; the SUGGESTIONS
                // branch + SuggestionsTab composable stay compiled). With it disabled, only the
                // BUILD tab remains — a single-item TabRow looks broken, so it is not rendered.
                val tabs = buildList {
                    add(DeckStudioTab.BUILD to stringResource(R.string.deck_studio_tab_build))
                    if (DeckFeatureFlags.DECK_STUDIO_SUGGESTIONS_TAB_ENABLED) {
                        add(DeckStudioTab.SUGGESTIONS to stringResource(R.string.deck_studio_tab_suggestions))
                    }
                }
                if (tabs.size > 1) {
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
                            deckStats = deckStats,
                            playerName = playerName,
                            // External nav (full CardDetail screen) — used by the stats card.
                            onCardClick = onCardClick,
                            // C3: a tap on a card IN THE DECK LIST opens the inline detail sheet.
                            onDeckCardClick = { id ->
                                focusManager.clearFocus()
                                isCardDetailInCommanderContext = false
                                selectedCardId = id
                            },
                            onReviewSurvey = onReviewSurvey,
                            onReplaceCard = { card ->
                                // Mirror the legacy editor: pre-fill the search with the card
                                // name and open the add-cards sheet so the user can pick a
                                // replacement immediately.
                                viewModel.onAddCardsQueryChange(card.name)
                                showAddCardsSheet = true
                            },
                            onSetGroupingMode = viewModel::setGroupingMode,
                            onToggleMainboard = viewModel::toggleMainboard,
                            onToggleSideboard = viewModel::toggleSideboard,
                            onToggleLandSuggestions = viewModel::toggleLandSuggestions,
                            onApplyLandSuggestions = viewModel::applyLandSuggestions,
                            onRemoveCard = viewModel::removeCard,
                            onMoveToSideboard = { id -> viewModel.moveQuantityToSideboard(id) },
                            onMoveToMainboard = { id -> viewModel.moveQuantityToMainboard(id) },
                            onRemoveCommander = viewModel::removeCommander,
                            onAddBasicLands = { showBasicLandsSheet = true },
                            onChooseCommander = {
                                viewModel.showCollectionCards()
                                showCommanderSearchSheet = true
                            },
                            onBuildFromSeed = {
                                // Phase 3 (P3-T1/P3-T2): opens the seed-build sheet (VM state-driven).
                                viewModel.openSeedSheet()
                            },
                            onBrowseInspirations = { viewModel.openInspirations() },
                            onImportDeck = { showImportSheet = true },
                            onFormatChange = viewModel::changeFormat,
                            onAcknowledgeOverLimit = viewModel::acknowledgeOverLimit,
                            onUnacknowledgeOverLimit = viewModel::unacknowledgeOverLimit,
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

        // C3: the inline card-detail sheet for taps on a card in the deck list / commander
        // banner. Search-result taps (CardSearchSheet) and the stats card still navigate to
        // the full CardDetail screen via onCardClick.
        if (selectedDeckCard != null) {
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

    // ── Sheets ──────────────────────────────────────────────────────────────────

    // Seed-build sheet (Phase 3): VM-state driven (uiState.showSeedSheet), unlike the
    // local-state sheets below.
    if (uiState.showSeedSheet) {
        val seedSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeSeedSheet() },
            sheetState = seedSheetState,
            shape = BottomSheetShape,
            containerColor = mc.background,
        ) {
            Column(Modifier.fillMaxHeight(0.92f)) {
                Text(
                    text = stringResource(R.string.deck_studio_seed_sheet_title),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    modifier = Modifier.padding(
                        horizontal = MaterialTheme.spacing.lg,
                        vertical = MaterialTheme.spacing.md,
                    ),
                )
                val seedFormat = uiState.deck?.format
                    ?.let { fmt -> DeckFormat.entries.firstOrNull { it.name.equals(fmt, ignoreCase = true) } }
                    ?: DeckFormat.CASUAL
                SeedsContent(
                    seedCards = uiState.seedCards,
                    identity = uiState.inferredIdentity,
                    skeleton = DeckSkeletons.forFormat(seedFormat),
                    budget = uiState.budgetConstraints,
                    query = uiState.seedQuery,
                    searchResults = uiState.seedSearchResults,
                    isSearching = uiState.isSearchingSeeds,
                    canGenerate = uiState.seedCards.isNotEmpty() && !uiState.isGenerating,
                    isGenerating = uiState.isGenerating,
                    onQueryChange = viewModel::onSeedQueryChange,
                    onAddSeed = viewModel::addSeed,
                    onRemoveSeed = viewModel::removeSeed,
                    // Unused: the budget is driven by the custom budgetSlot below (free-text input).
                    onBudgetChanged = { },
                    onGenerate = {
                        focusManager.clearFocus()
                        viewModel.generateFromSeeds { count ->
                            toastState.show(
                                String.format(seedBuiltMsg, count),
                                MagicToastType.SUCCESS,
                            )
                        }
                    },
                    budgetSlot = {
                        BudgetInputBar(
                            perCardText = uiState.rawPerCardText,
                            totalText = uiState.rawTotalText,
                            ownedCardsAreFree = uiState.ownedCardsAreFree,
                            hasError = uiState.budgetError,
                            onPerCardChange = viewModel::onPerCardBudgetChange,
                            onTotalChange = viewModel::onTotalBudgetChange,
                            onOwnedFreeChange = viewModel::onOwnedCardsFreeChange,
                            onClear = viewModel::onClearBudget,
                        )
                    },
                )
            }
        }
    }

    // Inspirations (Discoveries) sheet (Phase 4): VM-state driven (uiState.showInspirations).
    if (uiState.showInspirations) {
        val inspirationsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeInspirations() },
            sheetState = inspirationsSheetState,
            shape = BottomSheetShape,
            containerColor = mc.background,
        ) {
            InspirationsSheetContent(
                discoveries = uiState.discoveries,
                isLoading = uiState.isLoadingDiscoveries,
                onCardClick = { id ->
                    focusManager.clearFocus()
                    onCardClick(id)
                },
                onSeedStudio = { discovery -> viewModel.startFromDiscovery(discovery) },
            )
        }
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
            onFormatChange = viewModel::changeFormat,
        )
    }

    if (showImportSheet) {
        DeckImportSheet(
            isLoading = uiState.isImporting,
            error = null,
            onImport = { text ->
                focusManager.clearFocus()
                viewModel.importDeck(text)
                showImportSheet = false
            },
            onDismiss = {
                focusManager.clearFocus()
                showImportSheet = false
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
                // C3: open the inline detail sheet in commander-selection context so the
                // golden "Choose as commander" CTA is shown instead of navigating away.
                isCardDetailInCommanderContext = true
                selectedCardId = id
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
    playtestEnabled: Boolean,
    onPlaytest: () -> Unit,
    onBuildFromSeed: () -> Unit,
    onBrowseInspirations: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    shareEnabled: Boolean,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    var showOverflow by remember { mutableStateOf(false) }
    Surface(color = mc.backgroundSecondary) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = spacing.xs, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = mc.textSecondary)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = spacing.sm)) {
                Text(
                    text = title,
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                format?.let { fmt ->
                    Surface(shape = ChipShape, color = mc.goldMtg.copy(alpha = 0.15f)) {
                        Text(
                            text = fmt.uppercase(),
                            style = ty.labelSmall,
                            color = mc.goldMtg,
                            modifier = Modifier.padding(horizontal = spacing.xs, vertical = spacing.xxs),
                        )
                    }
                }
            }
            // Playtest (Group C / C1): launches the playtest setup for the current deck.
            // Enabled only when the deck is non-empty and persisted.
            // HIDDEN for release behind DeckFeatureFlags.PLAYTEST_ENABLED (UI-only; params/plumbing stay).
            if (DeckFeatureFlags.PLAYTEST_ENABLED) {
                IconButton(onClick = onPlaytest, enabled = playtestEnabled) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.deck_studio_playtest),
                        tint = mc.primaryAccent,
                    )
                }
            }
            // Overflow menu (Phase 3 + Group D): "Build from seed" (seed sheet),
            // "Browse inspirations" (Discoveries sheet), and "Share" — relocated here
            // from standalone icon buttons to keep ≤4 primary actions in the bar.
            Box {
                IconButton(onClick = { showOverflow = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.deck_studio_more_options),
                        tint = mc.textSecondary,
                    )
                }
                DropdownMenu(
                    expanded = showOverflow,
                    onDismissRequest = { showOverflow = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.deck_studio_edit_deck),
                                style = ty.bodyMedium,
                                color = mc.textPrimary,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = mc.textSecondary,
                            )
                        },
                        onClick = {
                            showOverflow = false
                            onEdit()
                        },
                    )
                    // HIDDEN for release behind DeckFeatureFlags.DECK_STUDIO_BUILD_FROM_SEED_ENABLED
                    // (UI-only; openSeedSheet/seed-sheet stay compiled).
                    if (DeckFeatureFlags.DECK_STUDIO_BUILD_FROM_SEED_ENABLED) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.deck_studio_build_from_seed),
                                    style = ty.bodyMedium,
                                    color = mc.textPrimary,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = mc.textSecondary,
                                )
                            },
                            onClick = {
                                showOverflow = false
                                onBuildFromSeed()
                            },
                        )
                    }
                    // HIDDEN for release behind DeckFeatureFlags.DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED
                    // (UI-only; openInspirations/inspirations-sheet stay compiled).
                    if (DeckFeatureFlags.DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = stringResource(R.string.deck_studio_inspirations),
                                    style = ty.bodyMedium,
                                    color = mc.textPrimary,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = mc.textSecondary,
                                )
                            },
                            onClick = {
                                showOverflow = false
                                onBrowseInspirations()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.deck_studio_share_deck),
                                style = ty.bodyMedium,
                                color = if (shareEnabled) mc.textPrimary else mc.textDisabled,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = if (shareEnabled) mc.textSecondary else mc.textDisabled,
                            )
                        },
                        enabled = shareEnabled,
                        onClick = {
                            showOverflow = false
                            onShare()
                        },
                    )
                }
            }
        }
    }
}

/** Bottom clearance so the last list item clears the FAB. */
private val FabClearance = 120.dp

@Composable
private fun BuildTab(
    uiState: DeckStudioUiState,
    isCommanderFormat: Boolean,
    deckStats: GetDeckGameStatsUseCase.Result?,
    playerName: String,
    onCardClick: (String) -> Unit,
    onDeckCardClick: (String) -> Unit,
    onReviewSurvey: (sessionId: Long) -> Unit,
    onReplaceCard: (Card) -> Unit,
    onSetGroupingMode: (GroupingMode) -> Unit,
    onToggleMainboard: () -> Unit,
    onToggleSideboard: () -> Unit,
    onToggleLandSuggestions: () -> Unit,
    onApplyLandSuggestions: () -> Unit,
    onRemoveCard: (String, Boolean) -> Unit,
    onMoveToSideboard: (String) -> Unit,
    onMoveToMainboard: (String) -> Unit,
    onRemoveCommander: () -> Unit,
    onAddBasicLands: () -> Unit,
    onChooseCommander: () -> Unit,
    onBuildFromSeed: () -> Unit,
    onBrowseInspirations: () -> Unit,
    onImportDeck: () -> Unit,
    onFormatChange: (DeckFormat) -> Unit,
    onAcknowledgeOverLimit: (String) -> Unit,
    onUnacknowledgeOverLimit: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    // C5: the format copy limit used by the per-card WarningOverlay banners.
    val maxCopies = uiState.deck?.format
        ?.let { fmt -> DeckFormat.entries.firstOrNull { it.name.equals(fmt, ignoreCase = true) } }
        ?.maxCopies ?: 4

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = mc.primaryAccent)
        }
        return
    }

    if (uiState.isEmptyDeck) {
        val currentFormat = uiState.deck?.format
            ?.let { fmt -> DeckFormat.entries.firstOrNull { it.name.equals(fmt, ignoreCase = true) } }
        EmptyDeckState(
            selectedFormat = currentFormat,
            onFormatChange = onFormatChange,
            onBuildFromSeed = onBuildFromSeed,
            onBrowseInspirations = onBrowseInspirations,
            onImportDeck = onImportDeck,
        )
        return
    }

    val mainboardCards = uiState.cards.filter { !it.isSideboard }
    val sideboardCards = uiState.cards.filter { it.isSideboard }.sortedBy { it.card?.name }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = FabClearance),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
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
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md).animateItem(),
            )
        }

        // Per-deck game stats (Group C / C2): only present once the deck has recorded
        // games. DeckStatsCard itself no-ops on null / zero games, but gating the item
        // keeps an empty padded slot out of the list.
        if (deckStats != null) {
            item(key = "deck_stats") {
                DeckStatsCard(
                    stats = deckStats,
                    playerName = playerName,
                    onCardClick = onCardClick,
                    onReviewSurvey = onReviewSurvey,
                    onReplaceCard = onReplaceCard,
                    modifier = Modifier.padding(horizontal = spacing.lg).animateItem(),
                )
            }
        }

        item(key = "grouping_selector") {
            Column(Modifier.padding(horizontal = spacing.lg).animateItem()) {
                GroupingFlowSelector(selected = uiState.groupingMode, onSelect = onSetGroupingMode)
            }
        }

        if (isCommanderFormat) {
            item(key = "commander_section") {
                Column(modifier = Modifier.padding(horizontal = spacing.lg).animateItem()) {
                    Text(
                        text = stringResource(R.string.deckbuilder_commander_label),
                        style = ty.titleMedium,
                        color = mc.goldMtg,
                        modifier = Modifier.padding(vertical = spacing.sm),
                    )
                    val commander = uiState.commanderCard
                    if (commander?.card != null) {
                        CommanderBanner(
                            commander = commander.card!!,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clickable { onDeckCardClick(commander.scryfallId) },
                        )
                        Spacer(Modifier.height(spacing.xs))
                        // C5: the commander's own validity warning (non-legendary, etc.).
                        WarningOverlay(
                            entry = commander,
                            isOverLimit = false,
                            isInvalidIdentity = false,
                            isNonLegendaryCommander = uiState.isCommanderInvalid,
                            isAcknowledged = commander.scryfallId in uiState.acknowledgedOverLimitCards,
                            maxCopies = maxCopies,
                            onAcknowledge = onAcknowledgeOverLimit,
                            onUnacknowledge = onUnacknowledgeOverLimit,
                            isCommander = true,
                        )
                        TextButton(
                            onClick = onRemoveCommander,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(stringResource(R.string.deckbuilder_remove_commander), style = ty.labelLarge, color = mc.lifeNegative)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onChooseCommander,
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.5f)),
                            shape = ChipShape,
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(spacing.sm))
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
                onToggle = onToggleMainboard,
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
                        showSuggestionToggle = isLandGroup,
                        isSuggestionEnabled = uiState.showLandSuggestions,
                        onToggleSuggestion = onToggleLandSuggestions,
                        modifier = Modifier.padding(horizontal = spacing.lg).animateItem(),
                    )
                }
                if (isLandGroup) {
                    item(key = "main_lands_logic") {
                        Column(Modifier.animateItem()) {
                            // C4: the basic-land suggestion strip; tapping it applies all deltas.
                            AnimatedVisibility(
                                visible = uiState.showLandSuggestions && uiState.landDeltas.isNotEmpty(),
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut(),
                            ) {
                                MagicLandSuggestionStatic(
                                    deltas = uiState.landDeltas,
                                    onClick = onApplyLandSuggestions,
                                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                                )
                            }
                            AddBasicLandsRow(onClick = onAddBasicLands, modifier = Modifier.padding(horizontal = spacing.lg))
                        }
                    }
                }
                items(cards, key = { "main_${it.scryfallId}_$groupLabel" }) { entry ->
                    Surface(
                        shape = CardShape,
                        color = mc.backgroundSecondary,
                        border = BorderStroke(0.5.dp, mc.surfaceVariant),
                        modifier = Modifier.padding(horizontal = spacing.lg).animateItem(),
                    ) {
                        Column {
                            CardRow(
                                entry = entry,
                                isInCollection = entry.scryfallId in uiState.collectionIds,
                                onClick = { onDeckCardClick(entry.scryfallId) },
                                onRemove = { onRemoveCard(entry.scryfallId, false) },
                            )
                            if (!isCommanderFormat) {
                                val qtyInSideboard = uiState.cards.find { it.scryfallId == entry.scryfallId && it.isSideboard }?.quantity ?: 0
                                MovementRow(
                                    labelTo = stringResource(R.string.deckbuilder_move_to_sideboard),
                                    onMoveTo = { onMoveToSideboard(entry.scryfallId) },
                                    labelFrom = if (qtyInSideboard > 0) stringResource(R.string.deckbuilder_from_sideboard) else null,
                                    onMoveFrom = if (qtyInSideboard > 0) {
                                        { onMoveToMainboard(entry.scryfallId) }
                                    } else null,
                                )
                            }
                            // C5: per-card over-limit / off-identity construction warning.
                            WarningOverlay(
                                entry = entry,
                                isOverLimit = entry.scryfallId in uiState.overLimitCards,
                                isInvalidIdentity = entry.scryfallId in uiState.invalidColorIdentityCards,
                                isNonLegendaryCommander = false,
                                isAcknowledged = entry.scryfallId in uiState.acknowledgedOverLimitCards,
                                maxCopies = maxCopies,
                                onAcknowledge = onAcknowledgeOverLimit,
                                onUnacknowledge = onUnacknowledgeOverLimit,
                            )
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
                    onToggle = onToggleSideboard,
                    modifier = Modifier.animateItem(),
                )
            }
            if (uiState.sideboardExpanded) {
                if (sideboardCards.isEmpty()) {
                    item(key = "sideboard_empty") {
                        Text(
                            text = stringResource(R.string.deckbuilder_sideboard_empty),
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                            modifier = Modifier.padding(horizontal = spacing.xxl, vertical = spacing.sm).animateItem(),
                        )
                    }
                } else {
                    items(sideboardCards, key = { "side_${it.scryfallId}" }) { entry ->
                        Surface(
                            shape = CardShape,
                            color = mc.backgroundSecondary,
                            border = BorderStroke(0.5.dp, mc.surfaceVariant),
                            modifier = Modifier.padding(horizontal = spacing.lg).animateItem(),
                        ) {
                            Column {
                                CardRow(
                                    entry = entry,
                                    isInCollection = entry.scryfallId in uiState.collectionIds,
                                    onClick = { onDeckCardClick(entry.scryfallId) },
                                    onRemove = { onRemoveCard(entry.scryfallId, true) },
                                )
                                MovementRow(
                                    labelTo = stringResource(R.string.deckbuilder_move_to_mainboard),
                                    onMoveTo = { onMoveToMainboard(entry.scryfallId) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Standard large primary/secondary action button height. */
private val LargeButtonHeight = 52.dp

@Composable
private fun EmptyDeckState(
    selectedFormat: DeckFormat?,
    onFormatChange: (DeckFormat) -> Unit,
    onBuildFromSeed: () -> Unit,
    onBrowseInspirations: () -> Unit,
    onImportDeck: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    // Outer Column fills the space; Top section for format selection,
    // Center section (Box with weight 1f) for the rest of the content.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
    ) {
        Text(
            stringResource(R.string.deck_studio_format_section),
            style = ty.labelSmall,
            color = mc.textSecondary,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(spacing.xs))

        DeckFormatChipRow(
            selectedFormat = selectedFormat,
            onFormatSelected = onFormatChange,
            modifier = Modifier.fillMaxWidth(),
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                // Title block first so the panel reads title → format → actions.
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = mc.goldMtg,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    stringResource(R.string.deck_studio_empty_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary
                )
                Text(
                    stringResource(R.string.deck_studio_empty_subtitle),
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                )

                // "Build from seed" and "Browse inspirations" are HIDDEN for release behind their
                // DeckFeatureFlags. When BOTH are disabled, "Import deck" is promoted from the
                // secondary OutlinedButton to the PRIMARY filled Button so the empty state still has
                // a clear primary action (the + FAB remains the main add-cards affordance).
                val seedEnabled = DeckFeatureFlags.DECK_STUDIO_BUILD_FROM_SEED_ENABLED
                val inspirationsEnabled = DeckFeatureFlags.DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED
                val importIsPrimary = !seedEnabled && !inspirationsEnabled

                // Primary action.
                if (seedEnabled) {
                    androidx.compose.material3.Button(
                        onClick = onBuildFromSeed,
                        modifier = Modifier.fillMaxWidth().height(LargeButtonHeight),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                        shape = ButtonShape,
                    ) {
                        Text(
                            stringResource(R.string.deck_studio_build_from_seed),
                            style = ty.labelLarge,
                            color = mc.background
                        )
                    }
                }
                // Secondary actions.
                if (inspirationsEnabled) {
                    OutlinedButton(
                        onClick = onBrowseInspirations,
                        modifier = Modifier.fillMaxWidth().height(LargeButtonHeight),
                        border = BorderStroke(1.dp, mc.primaryAccent),
                        shape = ButtonShape,
                    ) {
                        Text(
                            stringResource(R.string.deck_studio_browse_inspirations),
                            style = ty.labelLarge,
                            color = mc.primaryAccent
                        )
                    }
                }
                if (importIsPrimary) {
                    androidx.compose.material3.Button(
                        onClick = onImportDeck,
                        modifier = Modifier.fillMaxWidth().height(LargeButtonHeight),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                        shape = ButtonShape,
                    ) {
                        Text(
                            stringResource(R.string.deck_studio_import_deck),
                            style = ty.labelLarge,
                            color = mc.background
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onImportDeck,
                        modifier = Modifier.fillMaxWidth().height(LargeButtonHeight),
                        border = BorderStroke(1.dp, mc.primaryAccent),
                        shape = ButtonShape,
                    ) {
                        Text(
                            stringResource(R.string.deck_studio_import_deck),
                            style = ty.labelLarge,
                            color = mc.primaryAccent
                        )
                    }
                }
            }
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
    val spacing = MaterialTheme.spacing
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "SectionHeaderRotation",
    )
    val toggleDescription = if (expanded) {
        stringResource(R.string.deck_studio_collapse_section)
    } else {
        stringResource(R.string.deck_studio_expand_section)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = ty.titleLarge, color = mc.primaryAccent)
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = toggleDescription,
            tint = mc.primaryAccent,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

/**
 * The Inspirations (Discoveries) sheet content (Phase 4, P4-T1): lists collection-synergy
 * discoveries. Tapping "Seed Studio" pre-seeds the seed sheet (the user still taps Generate).
 *
 * Stateless: discoveries + loading flag come from the VM; every tap is a callback.
 */
@Composable
private fun InspirationsSheetContent(
    discoveries: List<com.mmg.manahub.feature.decks.domain.engine.MagicDiscovery>,
    isLoading: Boolean,
    onCardClick: (String) -> Unit,
    onSeedStudio: (com.mmg.manahub.feature.decks.domain.engine.MagicDiscovery) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxHeight(0.92f)
            .padding(horizontal = spacing.lg),
    ) {
        Text(
            text = stringResource(R.string.deck_studio_inspirations_title),
            style = ty.titleLarge,
            color = mc.textPrimary,
            modifier = Modifier.padding(top = spacing.md),
        )
        Text(
            text = stringResource(R.string.deck_studio_inspirations_subtitle),
            style = ty.bodySmall,
            color = mc.textSecondary,
            modifier = Modifier.padding(top = spacing.xxs, bottom = spacing.sm),
        )

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = mc.primaryAccent)
            }
            discoveries.isEmpty() -> EmptyState(
                title = stringResource(R.string.deck_studio_inspirations_empty_title),
                subtitle = stringResource(R.string.deck_studio_inspirations_empty_subtitle),
                icon = Icons.Default.AutoAwesome,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(vertical = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                // C3: key by primaryTag.key + label — two discoveries can share the same
                // CardTag, and a bare primaryTag.key would crash the LazyColumn on duplicate keys.
                items(discoveries.take(20), key = { "${it.primaryTag.key}_${it.label}" }) { discovery ->
                    DiscoveryRow(
                        discovery = discovery,
                        onCardClick = onCardClick,
                        onSeedStudio = { onSeedStudio(discovery) },
                    )
                }
            }
        }
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
    val spacing = MaterialTheme.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
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
                    Modifier.fillMaxWidth().padding(vertical = spacing.xl),
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
        style = MaterialTheme.magicTypography.labelLarge,
        color = color,
    )
}
