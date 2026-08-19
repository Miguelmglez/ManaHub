package com.mmg.manahub.feature.decks.presentation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase
import com.mmg.manahub.core.FeatureFlags
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckCard
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.model.GroupingMode
import com.mmg.manahub.core.ui.components.CardSearchSheet
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCardInspectionOverlay
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicFilterChip
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaHubBottomSheetSelector
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategy
import com.mmg.manahub.feature.decks.domain.engine.PillarId
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
import com.mmg.manahub.feature.decks.domain.model.ComboResult
import com.mmg.manahub.feature.decks.domain.orchestrator.DoctorAnalysisStage
import com.mmg.manahub.feature.decks.domain.template.DiscoverySearchFilter
import com.mmg.manahub.feature.decks.domain.template.partitionByAxis
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.presentation.components.AddBasicLandsRow
import com.mmg.manahub.feature.decks.presentation.components.AddSuggestionRow
import com.mmg.manahub.feature.decks.presentation.components.BasicLandsSheet
import com.mmg.manahub.feature.decks.presentation.components.CardDetailSheet
import com.mmg.manahub.feature.decks.presentation.components.CardRow
import com.mmg.manahub.feature.decks.presentation.components.CollapsedFindingsCaption
import com.mmg.manahub.feature.decks.presentation.components.CommanderBanner
import com.mmg.manahub.feature.decks.presentation.components.CommunityAddSuggestionRow
import com.mmg.manahub.feature.decks.presentation.components.CuratedStrategyPickerSheet
import com.mmg.manahub.feature.decks.presentation.components.CutSuggestionRow
import com.mmg.manahub.feature.decks.presentation.components.DeckFormatChipRow
import com.mmg.manahub.feature.decks.presentation.components.DeckImportSheet
import com.mmg.manahub.feature.decks.presentation.components.DeckStatsCard
import com.mmg.manahub.feature.decks.presentation.components.DeckSummaryCard
import com.mmg.manahub.feature.decks.presentation.components.EditDeckSheet
import com.mmg.manahub.feature.decks.presentation.components.ExpandChevron
import com.mmg.manahub.feature.decks.presentation.components.FindingRow
import com.mmg.manahub.feature.decks.presentation.components.GroupHeader
import com.mmg.manahub.feature.decks.presentation.components.HealthScoreRing
import com.mmg.manahub.feature.decks.presentation.components.MagicLandSuggestionStatic
import com.mmg.manahub.feature.decks.presentation.components.MovementRow
import com.mmg.manahub.feature.decks.presentation.components.PillarTile
import com.mmg.manahub.feature.decks.presentation.components.RoleCoverageEntryRow
import com.mmg.manahub.feature.decks.presentation.components.SimilarDeckCard
import com.mmg.manahub.feature.decks.presentation.components.StrategyPlanChip
import com.mmg.manahub.feature.decks.presentation.components.StrategyPlanHint
import com.mmg.manahub.feature.decks.presentation.components.SynergyCardTile
import com.mmg.manahub.feature.decks.presentation.components.TribeOption
import com.mmg.manahub.feature.decks.presentation.components.WarningOverlay
import com.mmg.manahub.feature.decks.presentation.components.groupCards
import com.mmg.manahub.feature.decks.presentation.components.key
import com.mmg.manahub.feature.decks.presentation.components.label
import org.koin.androidx.compose.koinViewModel
import androidx.compose.material.icons.filled.CollectionsBookmark

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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DeckStudioScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    onPlaytest: (deckId: String) -> Unit,
    onReviewSurvey: (sessionId: Long) -> Unit,
    // Deck Doctor Community/Archetype plan, Phase 4 (Motor B): navigates to
    // Screen.CommunityDecksByCard(cardName) / Screen.CommunityDeckDetail(archidektId). Defaulted
    // to a no-op so every OTHER call site of this screen (none exist besides AppNavGraph today,
    // but this keeps the signature source-compatible for tests/previews) keeps compiling.
    onNavigateToCommunityDecksByCard: (String) -> Unit = {},
    onNavigateToCommunityDeckDetail: (Int) -> Unit = {},
    // Deck Builder v2 (plan §3.4/§3.5): navigates to Screen.DeckWizard, optionally pre-filled from
    // a Discoveries v2 "Build this" tap. Deck Engine Unification (D2): args carry the unified
    // taxonomy directly (archetype = a raw ArchetypeId enum name, theme = a raw ThemeId enum name,
    // tribe = a raw tribe:<subtype> key, colors = concatenated ManaColor symbols) -- all null for
    // the plain "Build from seed" entry point. Route construction stays in AppNavGraph (this screen
    // never imports Screen directly, mirroring onNavigateToCommunityDecksByCard's own convention).
    // [seeds] (plan D7, 4.3): a combo's component card names, hands off to the wizard's Flow A --
    // null/empty for every other entry point (Discoveries v2's "Build this" passes null here too).
    onNavigateToWizard: (archetype: String?, theme: String?, tribe: String?, colors: String?, seeds: List<String>?) -> Unit = { _, _, _, _, _ -> },
    // Visual-overhaul pass: non-null only when the hosting nav destination is inside a
    // `SharedTransitionLayout` -- drives the Combos-tab card tiles' shared-element transition
    // into the real CardDetailScreen (mirrors AddCardScreen/CollectionScreen's own optional
    // params). Null degrades to a plain tap with no shared-element animation.
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    viewModel: DeckStudioViewModel = koinViewModel(),
    onNavigateToMassiveAddCards: (List<Card>) -> Unit = {},
    ) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val deckStats by viewModel.deckStatsFlow.collectAsStateWithLifecycle()
    val playerName by viewModel.playerNameFlow.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
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

    // C3: resolve the tapped card to a DeckSlotEntry from the deck list, commander, search results,
    // or (Phase 4) a Motor B community suggestion (quantityInDeck is always 0 there — a community
    // card is by definition not yet in the mainboard).
    val selectedDeckCard = remember(
        selectedCardId,
        uiState.cards,
        uiState.addCardsResults,
        uiState.scryfallResults,
        uiState.commanderCard,
        uiState.communityAdds,
    ) {
        selectedCardId?.let { id ->
            uiState.cards.find { it.scryfallId == id }
                ?: uiState.commanderCard?.takeIf { it.scryfallId == id }
                ?: (uiState.addCardsResults + uiState.scryfallResults)
                    .find { it.card.scryfallId == id }
                    ?.let { row -> DeckSlotEntry(row.card.scryfallId, row.quantityInDeck, false, row.card) }
                ?: uiState.communityAdds.find { it.card.scryfallId == id }
                    ?.let { s -> DeckSlotEntry(s.card.scryfallId, 0, false, s.card) }
        }
    }

    val cardAddedMsg = stringResource(R.string.deck_studio_card_added)
    val cardCutMsg = stringResource(R.string.deck_studio_card_cut)
    val externalFailedMsg = stringResource(R.string.deck_studio_external_pool_failed)
    val archetypePlanUpdatedMsg = stringResource(R.string.deck_studio_archetype_plan_updated)
    val strategyUnlockedMsg = stringResource(R.string.deck_studio_strategy_unlocked)

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

    val inspirationsEnabled = FeatureFlags.Decks.DISCOVERIES_V2_ENABLED
    val seedEnabled = FeatureFlags.Decks.DECK_BUILDER_V2_ENABLED
    val handleBuildFromSeed: () -> Unit = {
        onNavigateToWizard(null, null, null, null, null)
    }

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
                    onBuildFromSeed = handleBuildFromSeed,
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
                    onMassiveAdd = {onNavigateToMassiveAddCards(emptyList())},
                    shareEnabled = !uiState.isEmptyDeck,
                )
            },
            bottomBar = {
                val playtestEnabled = !uiState.isEmptyDeck && uiState.deck?.id != null
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(mc.background)
                        .navigationBarsPadding()
                        .padding(horizontal = spacing.lg, vertical = spacing.md)
                ) {
                    MagicCtaButton(
                        text = stringResource(R.string.deck_studio_playtest),
                        onClick = { uiState.deck?.id?.let(onPlaytest) },
                        enabled = playtestEnabled,
                        modifier = Modifier.fillMaxWidth().padding(bottom = spacing.md).height(56.dp)
                    )
                }
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
                        modifier = Modifier,
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
                    if (FeatureFlags.Decks.DECK_STUDIO_SUGGESTIONS_TAB_ENABLED) {
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
                            onBuildFromSeed = handleBuildFromSeed,
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
                            onApplyCuratedStrategy = { strategy, tribe ->
                                viewModel.onApplyCuratedStrategy(strategy, tribe)
                                toastState.show(archetypePlanUpdatedMsg, MagicToastType.SUCCESS)
                            },
                            onAutoDetectArchetypePlan = {
                                viewModel.onClearArchetypeOverride()
                                toastState.show(archetypePlanUpdatedMsg, MagicToastType.SUCCESS)
                            },
                            onUnlockStrategy = {
                                viewModel.onUnlockStrategy()
                                toastState.show(strategyUnlockedMsg, MagicToastType.SUCCESS)
                            },
                            onAddCommunity = { s ->
                                viewModel.onAddSuggestion(s.card.scryfallId, s.card.name)
                            },
                            onCommunityCardTap = { id -> selectedCardId = id },
                            onViewCommunityDecksForCard = onNavigateToCommunityDecksByCard,
                            onOpenSimilarDeck = onNavigateToCommunityDeckDetail,
                            onToggleIncludeOutsideCollection = viewModel::onToggleIncludeOutsideCollection,
                        )
                    }
                }
            }
        }

        MagicToastHost(
            state = toastState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 80.dp),
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
                displayCard = uiState.detailDisplayCard,
                isLoadingDetail = uiState.isLoadingCardDetail,
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

    // Inspirations (Discoveries) sheet (Phase 4): VM-state driven (uiState.showInspirations).
    if (uiState.showInspirations) {
        val inspirationsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.closeInspirations() },
            sheetState = inspirationsSheetState,
            shape = BottomSheetShape,
            containerColor = mc.background,
        ) {
            // Deck Builder v2 Phase 5 (plan D10/§3.7): Discoveries v2 clusters (identity-only,
            // color-coherent) hand off to the v2 wizard pre-filled (D11). The legacy content
            // (ANY-tag-category clustering, seeding the old seed sheet) was RETIRED in the Deck
            // Wizard & Engine Rework plan, WS7.2 (2026-07-28) -- this is reachable only when
            // `DeckFeatureFlags.DISCOVERIES_V2_ENABLED` is on (see `inspirationsEnabled` below,
            // the entry point's own visibility gate), so there is exactly one content branch left.
            InspirationsSheetContentV2(
                discoveries = uiState.discoveriesV2,
                filteredDiscoveries = uiState.filteredDiscoveriesV2,
                matchingCards = uiState.discoveryMatchingCards,
                isLoading = uiState.isLoadingDiscoveries,
                onBuildThis = { discovery ->
                    viewModel.closeInspirations()
                    onNavigateToWizard(
                        discovery.archetype?.name,
                        discovery.theme?.name,
                        discovery.tribe,
                        discovery.dominantColors.joinToString("") { it.symbol },
                        null,
                    )
                },
                inspirationsTab = uiState.inspirationsTab,
                onSelectTab = viewModel::onSelectInspirationsTab,
                searchQuery = uiState.discoverySearchQuery,
                onSearchQueryChange = viewModel::onDiscoverySearchQueryChange,
                selectedCardNames = uiState.discoverySelectedCardNames,
                onToggleSearchCard = viewModel::onToggleDiscoverySearchCard,
                onClearSearch = viewModel::onClearDiscoverySearch,
                comboResult = uiState.comboResult,
                comboCardsByName = uiState.comboCardsByName,
                isLoadingCombos = uiState.isLoadingCombos,
                onUseComboAsSeed = { cardNames ->
                    viewModel.closeInspirations()
                    onNavigateToWizard(null, null, null, null, cardNames)
                },
                // Combos tab only -- Strategies-tab card taps open the inline zoom overlay
                // (self-contained inside InspirationsSheetContentV2) and never call this.
                // Close the sheet BEFORE navigating (same sequencing as `onBuildThis`/
                // `onUseComboAsSeed` just above, and the coordination pattern in
                // `ScannerScreen.kt`'s `ScanQueueSheet` -- `onOpenCardDetail(fromQueue = true)`
                // flips `showQueueSheet` off in the SAME state update as opening the detail
                // overlay): otherwise the sheet's own show/hide animation is still playing
                // when CardDetailScreen's entry transition starts, and the two race visibly.
                onNavigateToCardDetail = { id ->
                    focusManager.clearFocus()
                    viewModel.closeInspirations()
                    onCardClick(id)
                },
                sharedTransitionScope = null,
                animatedVisibilityScope = null,
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
    onBuildFromSeed: () -> Unit,
    onBrowseInspirations: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    shareEnabled: Boolean,
    onMassiveAdd: ()->Unit
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
                    // Deck Builder v2 (plan D10/§3.7): visible when the v2 wizard is enabled. The
                    // legacy seed-sheet sibling flag was RETIRED in WS7.2 (2026-07-28).
                    if (FeatureFlags.Decks.DECK_BUILDER_V2_ENABLED) {
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
                    // Deck Builder v2 (plan D10/§3.7): visible when Discoveries v2 is enabled. The
                    // legacy discoverSynergies content's sibling flag was RETIRED in WS7.2
                    // (2026-07-28) -- the sheet's content (see the ModalBottomSheet above) has
                    // exactly one branch now.
                    if (FeatureFlags.Decks.DISCOVERIES_V2_ENABLED) {
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

                    if (FeatureFlags.MassiveAdd.MASSIVE_CARDS_ENABLED) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Massive add",
                                    style = ty.bodyMedium,
                                    color = if (shareEnabled) mc.textPrimary else mc.textDisabled,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CollectionsBookmark,
                                    contentDescription = null,
                                    tint = if (shareEnabled) mc.textSecondary else mc.textDisabled,
                                )
                            },
                            enabled = shareEnabled,
                            onClick = {
                                showOverflow = false
                                onMassiveAdd()
                            },
                        )
                    }
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

    // Smooth crossfade between the loading spinner and the loaded content (UI polish, 2026-07-22) —
    // avoids the abrupt jump-cut previously felt right after a Community Deck import navigates
    // into a freshly-created deck whose data is still loading from Room.
    AnimatedContent(
        targetState = uiState.isLoading,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
        },
        label = "DeckStudioBuildTabLoading",
    ) { isLoading ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MagicLoadingSpinner()
            }
        } else if (uiState.isEmptyDeck) {
            val currentFormat = uiState.deck?.format
                ?.let { fmt -> DeckFormat.entries.firstOrNull { it.name.equals(fmt, ignoreCase = true) } }
            EmptyDeckState(
                selectedFormat = currentFormat,
                onFormatChange = onFormatChange,
                onBuildFromSeed = onBuildFromSeed,
                onBrowseInspirations = onBrowseInspirations,
                onImportDeck = onImportDeck,
            )
        } else {
            val mainboardCards = uiState.cards.filter { !it.isSideboard }
            val sideboardCards = uiState.cards.filter { it.isSideboard }.sortedBy { it.card?.name }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(bottom = FabClearance),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
        item(key = "summary") {
            // Derive the deck-size target from the deck's actual DeckFormat.targetDeckSize
            // (DRAFT = 40, COMMANDER = 100, everything else = 60) instead of a Commander-only
            // ternary, which silently mislabeled Draft decks (min 40) as "X/60".
            val targetCount = uiState.deck?.format
                ?.let { fmt -> DeckFormat.entries.firstOrNull { it.name.equals(fmt, ignoreCase = true) } }
                ?.targetDeckSize ?: 60
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
                // F.4: migrated from GroupingFlowSelector (dropdown) to ManaHubBottomSheetSelector,
                // the same modal-sheet picker CollectionScreen already uses for its sort/group pickers.
                ManaHubBottomSheetSelector(
                    icon = Icons.Default.Layers,
                    valueText = stringResource(uiState.groupingMode.displayResId),
                    items = GroupingMode.entries,
                    selectedItem = uiState.groupingMode,
                    onSelect = onSetGroupingMode,
                    itemLabel = { stringResource(it.displayResId) },
                )
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
                            // Mainboard<->sideboard movement is format-agnostic (bug fix,
                            // 2026-07-22): DeckStudioViewModel.moveQuantityToSideboard/
                            // moveQuantityToMainboard and DeckRepository.moveCardQuantity have no
                            // format restriction, and a Commander import can legitimately land
                            // cards in the sideboard (Archidekt Maybeboard/custom-excluded
                            // categories — see CommunityDeckMappers.kt). Previously gated behind
                            // `!isCommanderFormat`, which hid this affordance for Commander decks
                            // with no domain-level backing (parity fix vs. the legacy
                            // DeckBuilderScreen.kt editor, which always showed it).
                            val qtyInSideboard = uiState.cards.find { it.scryfallId == entry.scryfallId && it.isSideboard }?.quantity ?: 0
                            MovementRow(
                                labelTo = stringResource(R.string.deckbuilder_move_to_sideboard),
                                onMoveTo = { onMoveToSideboard(entry.scryfallId) },
                                labelFrom = if (qtyInSideboard > 0) stringResource(R.string.deckbuilder_from_sideboard) else null,
                                onMoveFrom = if (qtyInSideboard > 0) {
                                    { onMoveToMainboard(entry.scryfallId) }
                                } else null,
                            )
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

        // Sideboard is format-agnostic (bug fix, 2026-07-22 — see the mainboard MovementRow
        // comment above for the full rationale): always render it, mirroring the legacy
        // DeckBuilderScreen.kt editor's unconditional behavior. A Commander-format import can
        // legitimately have sideboard-zone cards (Archidekt Maybeboard/custom-excluded
        // categories), which previously had no UI to view or move for Commander decks.
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
}

/** Standard large primary/secondary action button height. */
private val LargeButtonHeight = 52.dp

/**
 * The empty-deck landing panel (visual-overhaul pass): a hero header followed by up to three
 * richly-illustrated option cards -- "Build from seed", "Browse inspirations", "Import deck list"
 * -- each with its own accent color, icon badge, title, and short description, ending in a
 * [MagicCtaButton]. Visuals only: every callback and the [DeckFeatureFlags]-driven
 * primary/secondary promotion logic are unchanged from the previous plain-button layout.
 */
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

    // "Build from seed" and "Browse inspirations" are HIDDEN for release behind their
    // DeckFeatureFlags. When BOTH are disabled, "Import deck" is promoted to the PRIMARY (Filled)
    // CTA so the empty state still has a clear primary action (the + FAB remains the main
    // add-cards affordance). Unchanged from the previous layout. Each flag's legacy sibling
    // (DECK_STUDIO_BUILD_FROM_SEED_ENABLED / DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED) was RETIRED
    // in the Deck Wizard & Engine Rework plan, WS7.2 (2026-07-28).
    val seedEnabled = FeatureFlags.Decks.DECK_BUILDER_V2_ENABLED
    val inspirationsEnabled = FeatureFlags.Decks.DISCOVERIES_V2_ENABLED
    val importIsPrimary = !seedEnabled && !inspirationsEnabled

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        item(key = "empty_format") {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    stringResource(R.string.deck_studio_format_section),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
                DeckFormatChipRow(
                    selectedFormat = selectedFormat,
                    onFormatSelected = onFormatChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        item(key = "empty_hero") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(mc.goldMtg.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = mc.goldMtg,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(Modifier.height(spacing.xs))
                Text(
                    stringResource(R.string.deck_studio_empty_title),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                )
                Text(
                    stringResource(R.string.deck_studio_empty_subtitle),
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.fillMaxWidth(0.9f),
                )
            }
        }

        if (seedEnabled) {
            item(key = "empty_option_seed") {
                EmptyStateOptionCard(
                    icon = Icons.Default.AutoAwesome,
                    accent = mc.goldMtg,
                    title = stringResource(R.string.deck_studio_build_from_seed),
                    description = stringResource(R.string.deck_studio_build_from_seed_desc),
                    ctaLabel = stringResource(R.string.deck_studio_build_from_seed),
                    ctaColor = MagicCtaColor.Gold,
                    isPrimary = true,
                    onClick = onBuildFromSeed,
                )
            }
        }
        if (inspirationsEnabled) {
            item(key = "empty_option_inspirations") {
                EmptyStateOptionCard(
                    icon = Icons.Default.Explore,
                    accent = mc.primaryAccent,
                    title = stringResource(R.string.deck_studio_browse_inspirations),
                    description = stringResource(R.string.deck_studio_browse_inspirations_desc),
                    ctaLabel = stringResource(R.string.deck_studio_browse_inspirations),
                    ctaColor = MagicCtaColor.Primary,
                    isPrimary = !seedEnabled,
                    onClick = onBrowseInspirations,
                )
            }
        }
        item(key = "empty_option_import") {
            EmptyStateOptionCard(
                icon = Icons.Default.UploadFile,
                accent = mc.secondaryAccent,
                title = stringResource(R.string.deck_studio_import_deck),
                description = stringResource(R.string.deck_studio_import_deck_desc),
                ctaLabel = stringResource(R.string.deck_studio_import_deck),
                ctaColor = MagicCtaColor.Accent,
                isPrimary = importIsPrimary,
                onClick = onImportDeck,
            )
        }
    }
}

/**
 * One illustrated option card in [EmptyDeckState]: an accent-tinted icon badge, title,
 * description, and a full-width [MagicCtaButton]. [isPrimary] renders the button
 * [MagicCtaStyle.Filled] (solid accent, high emphasis); otherwise [MagicCtaStyle.Outlined].
 */
@Composable
private fun EmptyStateOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    title: String,
    description: String,
    ctaLabel: String,
    ctaColor: MagicCtaColor,
    isPrimary: Boolean,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        shape = CardShape,
        color = mc.backgroundSecondary,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = ty.titleMedium, color = mc.textPrimary)
                    Text(description, style = ty.bodySmall, color = mc.textSecondary)
                }
            }
            MagicCtaButton(
                onClick = onClick,
                text = ctaLabel,
                style = if (isPrimary) MagicCtaStyle.Filled else MagicCtaStyle.Outlined,
                color = ctaColor,
                modifier = Modifier.fillMaxWidth().heightIn(min = LargeButtonHeight),
            )
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
 * Deck Builder v2 Phase 5 (plan §3.5) sheet content over the [DeckDiscoveryV2] cluster model,
 * shown when `DeckFeatureFlags.DISCOVERIES_V2_ENABLED` is on (D10/§3.7). "Build this" hands off
 * to the v2 wizard pre-filled (D11). The legacy `InspirationsSheetContent` (ANY-tag-category
 * clustering, seed-sheet handoff) was RETIRED in the Deck Wizard & Engine Rework plan, WS7.2
 * (2026-07-28) -- this is the sheet's only content now.
 *
 * Deck Engine Unification plan D7 (Phase 4) redesign: this is now a two-tab synergy browser
 * (Strategies / Combos) with free-text + search-by-card filtering on the Strategies tab (4.1/4.2)
 * and a Commander Spellbook combos tab (4.3). Stateless per the file's own convention -- every
 * input is a param, every mutation a callback to [DeckStudioViewModel]; the one exception is the
 * Strategies tab's inline card-inspection overlay (visual-overhaul pass), which is ephemeral UI
 * state, not business state, and is hosted here (not lifted to the VM) for the same reason every
 * other `MagicCardInspectionOverlay` trigger in this codebase keeps its session state local.
 *
 * @param discoveries the FULL unfiltered cluster list -- only used to derive the search-by-card
 *   pickable pool ([DiscoverySearchFilter.pickableCardNames]); the Strategies tab itself renders
 *   [filteredDiscoveries].
 * @param filteredDiscoveries [discoveries] narrowed by [searchQuery]/[selectedCardNames]
 *   ([DeckStudioViewModel.filteredDiscoveriesV2] -- the VM is the single source of truth for the
 *   filter, this Composable stays a dumb reader).
 * @param matchingCards the flat "matching cards" preview -- [discoveries]' members narrowed by
 *   the SAME [searchQuery]/[selectedCardNames] filter ([DeckStudioViewModel.discoveryMatchingCards]).
 * @param comboCardsByName every Combos-tab card name resolved to a full [Card]
 *   ([DeckStudioViewModel.comboCardsByName]) so the Combos tab can render image tiles.
 * @param onNavigateToCardDetail Combos-tab ONLY -- opens the real card detail screen for a
 *   resolved scryfallId. The Strategies tab never calls this; its taps open the inline overlay.
 * @param sharedTransitionScope / @param animatedVisibilityScope threaded straight through to the
 *   Combos tab's card tiles for the shared-element transition into card detail.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun InspirationsSheetContentV2(
    discoveries: List<com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2>,
    filteredDiscoveries: List<com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2>,
    matchingCards: List<Card>,
    isLoading: Boolean,
    onBuildThis: (com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2) -> Unit,
    inspirationsTab: InspirationsTab,
    onSelectTab: (InspirationsTab) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCardNames: Set<String>,
    onToggleSearchCard: (String) -> Unit,
    onClearSearch: () -> Unit,
    comboResult: ComboResult?,
    comboCardsByName: Map<String, Card>,
    isLoadingCombos: Boolean,
    onUseComboAsSeed: (List<String>) -> Unit,
    onNavigateToCardDetail: (String) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    // Strategies-tab inline card inspection (visual-overhaul pass): a tap on any card tile --
    // the search-matches preview or a per-category DiscoveryRowV2 row -- flies the card to a
    // zoomed overlay in place, mirroring `PlaytestSetupScreen`'s single-card
    // MagicCardInspectionOverlay pattern. rootCoordinates anchors every tile's captured Rect to
    // THIS Box so the overlay's flight animation lines up regardless of scroll position.
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var inspectionCard by remember { mutableStateOf<Card?>(null) }
    var inspectionRect by remember { mutableStateOf(Rect.Zero) }
    var isDismissingInspection by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxHeight(0.92f)
            .onGloballyPositioned { rootCoordinates = it },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = spacing.lg)) {
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

            TabRow(
                selectedTabIndex = inspirationsTab.ordinal,
                containerColor = mc.backgroundSecondary,
                contentColor = mc.primaryAccent,
            ) {
                Tab(
                    selected = inspirationsTab == InspirationsTab.STRATEGIES,
                    onClick = { onSelectTab(InspirationsTab.STRATEGIES) },
                    text = {
                        Text(
                            stringResource(R.string.deck_studio_inspirations_tab_strategies),
                            style = ty.labelLarge,
                        )
                    },
                )
                Tab(
                    selected = inspirationsTab == InspirationsTab.COMBOS,
                    onClick = { onSelectTab(InspirationsTab.COMBOS) },
                    text = {
                        Text(
                            stringResource(R.string.deck_studio_inspirations_tab_combos),
                            style = ty.labelLarge,
                        )
                    },
                )
            }
            Spacer(Modifier.height(spacing.sm))

            when (inspirationsTab) {
                InspirationsTab.STRATEGIES -> StrategiesTabContent(
                    discoveries = discoveries,
                    filteredDiscoveries = filteredDiscoveries,
                    matchingCards = matchingCards,
                    isLoading = isLoading,
                    rootCoordinates = rootCoordinates,
                    onCardTap = { card, rect ->
                        isDismissingInspection = false
                        inspectionCard = card
                        inspectionRect = rect
                    },
                    onBuildThis = onBuildThis,
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    selectedCardNames = selectedCardNames,
                    onToggleSearchCard = onToggleSearchCard,
                    onClearSearch = onClearSearch,
                )
                InspirationsTab.COMBOS -> CombosTabContent(
                    comboResult = comboResult,
                    cardsByName = comboCardsByName,
                    isLoading = isLoadingCombos,
                    onCardClick = onNavigateToCardDetail,
                    onUseComboAsSeed = onUseComboAsSeed,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }

        inspectionCard?.let { card ->
            MagicCardInspectionOverlay(
                card = card,
                initialRect = inspectionRect,
                isVisible = true,
                isDismissing = isDismissingInspection,
                onDismissRequest = { isDismissingInspection = true },
                onDismiss = {
                    inspectionCard = null
                    isDismissingInspection = false
                },
            )
        }
    }
}

/**
 * The Strategies tab (4.1 strictness lives entirely in [com.mmg.manahub.feature.decks.domain
 * .template.DiscoverSynergiesV2UseCase]; this composable is 4.2's search UI only).
 *
 * @param matchingCards the search-driven flat card preview shown directly under the search bar
 *   (visual-overhaul pass) -- populated only while a query/card-pick is active, so the SAME
 *   search input narrows both which clusters show ([filteredDiscoveries]) and which individual
 *   cards show here, instead of the query only ever affecting the cluster list.
 * @param rootCoordinates / @param onCardTap forwarded to every [SynergyCardTile] (both this tab's
 *   own preview row and each [DiscoveryRowV2]'s member row) so a tap opens the inline inspection
 *   overlay hosted by [InspirationsSheetContentV2].
 */
@Composable
private fun StrategiesTabContent(
    discoveries: List<com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2>,
    filteredDiscoveries: List<com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2>,
    matchingCards: List<Card>,
    isLoading: Boolean,
    rootCoordinates: LayoutCoordinates?,
    onCardTap: (Card, Rect) -> Unit,
    onBuildThis: (com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCardNames: Set<String>,
    onToggleSearchCard: (String) -> Unit,
    onClearSearch: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    // Pure derivation of the pickable pool from the (small, already-loaded) cluster list -- not
    // worth VM state, mirrors this screen's own `tabs = buildList { ... }` local-derivation
    // precedent above.
    val pickableCardNames = remember(discoveries) { DiscoverySearchFilter.pickableCardNames(discoveries) }
    // Every discovery member is already a resolved Card (DeckDiscoveryV2.members) -- these are
    // owned collection cards, unlike the Combos tab's comboCardsByName which may miss unowned
    // names. Same remember(discoveries) key as pickableCardNames above; last-write-wins on a
    // duplicate name across clusters is fine, they're the same printing.
    val cardsByPickableName = remember(discoveries) {
        discoveries.flatMap { it.members }.associateBy { it.name }
    }

    Column(Modifier.fillMaxSize()) {
        if (!isLoading && discoveries.isNotEmpty()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.xs),
                placeholder = { Text(stringResource(R.string.deck_studio_inspirations_search_hint), style = ty.bodyMedium) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary) },
                trailingIcon = if (searchQuery.isNotEmpty() || selectedCardNames.isNotEmpty()) {
                    {
                        IconButton(onClick = onClearSearch, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.deck_studio_inspirations_search_clear),
                                tint = mc.textSecondary,
                            )
                        }
                    }
                } else null,
                singleLine = true,
                shape = ChipShape,
            )

            if (pickableCardNames.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    items(pickableCardNames, key = { it }) { name ->
                        val card = cardsByPickableName[name]
                        if (card != null) {
                            com.mmg.manahub.feature.decks.presentation.components.PickableSynergyCardTile(
                                card = card,
                                isSelected = name in selectedCardNames,
                                rootCoordinates = rootCoordinates,
                                onToggleSelect = { onToggleSearchCard(name) },
                                onZoom = onCardTap,
                            )
                        } else {
                            // Defensive fallback -- every name in pickableCardNames comes from a
                            // DeckDiscoveryV2 member, which always carries a resolved Card, but
                            // keep a text chip so an unresolved name never silently vanishes.
                            MagicFilterChip(
                                selected = name in selectedCardNames,
                                onClick = { onToggleSearchCard(name) },
                                label = name,
                            )
                        }
                    }
                }
            }

            // Matching-cards preview (visual-overhaul pass): only while a search/pick is active,
            // so it never duplicates the full unfiltered pool.
            if (matchingCards.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.deck_studio_inspirations_matching_cards, matchingCards.size),
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(bottom = spacing.xs),
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    items(matchingCards.take(20), key = { "match_${it.scryfallId}" }) { card ->
                        SynergyCardTile(
                            card = card,
                            rootCoordinates = rootCoordinates,
                            onTap = onCardTap,
                        )
                    }
                }
            }
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MagicLoadingSpinner()
            }
            discoveries.isEmpty() -> EmptyState(
                title = stringResource(R.string.deck_studio_inspirations_empty_title),
                subtitle = stringResource(R.string.deck_studio_inspirations_empty_subtitle),
                icon = Icons.Default.AutoAwesome,
            )
            filteredDiscoveries.isEmpty() -> EmptyState(
                title = stringResource(R.string.deck_studio_inspirations_search_empty_title),
                subtitle = stringResource(R.string.deck_studio_inspirations_search_empty_subtitle),
                icon = Icons.Default.Search,
            )
            else -> {
                // Deck Wizard & Engine Rework plan (WS 1.3, plan §0 F1): Strategy and Tribe are
                // disjoint taxonomy axes -- they used to render as ONE mixed labeled list here
                // ("Spirits"/"Humans" read as strategies), which is exactly the "descompensado"
                // asymmetry the plan calls out. Split into two headed sections, each keeping the
                // use case's own best-fit-first ordering within its half.
                val (strategyDiscoveries, tribeDiscoveries) = filteredDiscoveries.partitionByAxis()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = rememberLazyListState(),
                    contentPadding = PaddingValues(vertical = spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    if (strategyDiscoveries.isNotEmpty()) {
                        item(key = "section_strategies") {
                            Text(
                                text = stringResource(R.string.deck_studio_inspirations_section_strategies),
                                style = ty.labelLarge,
                                color = mc.primaryAccent,
                            )
                        }
                        // Keyed by the cluster's own stable identity (tag key) -- distinct from the
                        // legacy MagicDiscovery key shape (no primaryTag on this model).
                        items(strategyDiscoveries.take(20), key = { it.key.stableKey() }) { discovery ->
                            com.mmg.manahub.feature.decks.presentation.components.DiscoveryRowV2(
                                discovery = discovery,
                                rootCoordinates = rootCoordinates,
                                onCardTap = onCardTap,
                                onBuildThis = { onBuildThis(discovery) },
                            )
                        }
                    }
                    if (tribeDiscoveries.isNotEmpty()) {
                        item(key = "section_tribes") {
                            Text(
                                text = stringResource(R.string.deck_studio_inspirations_section_tribes),
                                style = ty.labelLarge,
                                color = mc.primaryAccent,
                                modifier = Modifier.padding(top = if (strategyDiscoveries.isNotEmpty()) spacing.sm else 0.dp),
                            )
                        }
                        items(tribeDiscoveries.take(20), key = { it.key.stableKey() }) { discovery ->
                            com.mmg.manahub.feature.decks.presentation.components.DiscoveryRowV2(
                                discovery = discovery,
                                rootCoordinates = rootCoordinates,
                                onCardTap = onCardTap,
                                onBuildThis = { onBuildThis(discovery) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Combos tab (Deck Engine Unification plan D7, 4.3). Unlike the Strategies tab, card taps
 * here navigate to the real card detail screen (with a shared-element transition when
 * [sharedTransitionScope]/[animatedVisibilityScope] are non-null) rather than opening an inline
 * overlay -- combo cards may include cards the user doesn't own yet (the missing card in an
 * "almost there" combo), so jumping to the full detail screen (buy/wishlist actions) is more
 * useful here than a bare zoom.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CombosTabContent(
    comboResult: ComboResult?,
    cardsByName: Map<String, Card>,
    isLoading: Boolean,
    onCardClick: (String) -> Unit,
    onUseComboAsSeed: (List<String>) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MagicLoadingSpinner()
        }
        comboResult == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MagicLoadingSpinner()
        }
        comboResult.complete.isEmpty() && comboResult.almostThere.isEmpty() -> EmptyState(
            title = stringResource(R.string.deck_studio_combos_empty_title),
            subtitle = stringResource(R.string.deck_studio_combos_empty_subtitle),
            icon = Icons.Default.AutoAwesome,
        )
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = rememberLazyListState(),
            contentPadding = PaddingValues(vertical = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            if (comboResult.complete.isNotEmpty()) {
                item(key = "combos_complete_header") {
                    Text(
                        text = stringResource(R.string.deck_studio_combos_complete_header, comboResult.complete.size),
                        style = MaterialTheme.magicTypography.labelLarge,
                        color = mc.textSecondary,
                    )
                }
                items(comboResult.complete, key = { "combo_${it.id}" }) { combo ->
                    com.mmg.manahub.feature.decks.presentation.components.ComboRow(
                        combo = combo,
                        cardsByName = cardsByName,
                        onCardClick = onCardClick,
                        onUseAsSeed = { onUseComboAsSeed(combo.cardNames) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
            if (comboResult.almostThere.isNotEmpty()) {
                item(key = "combos_almost_header") {
                    Text(
                        text = stringResource(R.string.deck_studio_combos_almost_header, comboResult.almostThere.size),
                        style = MaterialTheme.magicTypography.labelLarge,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(top = spacing.sm),
                    )
                }
                items(comboResult.almostThere, key = { "almost_${it.id}" }) { almost ->
                    com.mmg.manahub.feature.decks.presentation.components.AlmostComboRow(
                        almostCombo = almost,
                        cardsByName = cardsByName,
                        onCardClick = onCardClick,
                        onUseAsSeed = { onUseComboAsSeed(almost.ownedCardNames + almost.missingCardName) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
        }
    }
}

private fun com.mmg.manahub.feature.decks.domain.template.DiscoveryClusterKey.stableKey(): String = when (this) {
    is com.mmg.manahub.feature.decks.domain.template.DiscoveryClusterKey.Strategy -> "strategy_${tag.key}"
    is com.mmg.manahub.feature.decks.domain.template.DiscoveryClusterKey.Tribe -> "tribe_$tribeKey"
}

/**
 * The Suggestions surface (Deck Doctor inline, Phase 1/2): a Health summary, the Cut list, and
 * the "From your collection" Add list (Motor A, Phase 2 — offline, always available), all driven
 * by the live deck via [DeckStudioViewModel]. No budget UI is shown here (D5 — Motor A suggestions
 * are already owned, so budget is moot); the whole budget-suggestions pipeline
 * (`SuggestAddsWithBudgetUseCase`/`BudgetOptimizer`/`BudgetFilterBar`/`BudgetInputBar`) was DELETED
 * in the Deck Wizard & Engine Rework plan, WS7.3 (2026-07-28, D-H). The row composables and string
 * helpers live in [com.mmg.manahub.feature.decks.presentation.components] — this is now the SOLE
 * Deck Doctor UI surface; the standalone Deck Improvement screen those composables were originally
 * copied from was retired in Phase 0.5 (D10).
 *
 * Stateless: all state comes from [uiState]; every mutation is a callback to the VM.
 */
/**
 * Deck Wizard & Engine Rework plan, Workstream 8.4: the Suggestions tab's staged progress screen
 * for the full [DeckDoctorOrchestrator][com.mmg.manahub.feature.decks.domain.orchestrator.DeckDoctorOrchestrator]
 * analysis pass -- mirrors [com.mmg.manahub.feature.decks.presentation.wizard.GeneratingContent]'s
 * visual language exactly (spinner + current-stage label + a checkmarked list of already-finished
 * stages) rather than a second, divergent design.
 */
@Composable
private fun DoctorStagedProgressContent(
    stage: DoctorAnalysisStage,
    completedStages: List<DoctorAnalysisStage>,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            MagicLoadingSpinner(
                modifier = Modifier.size(48.dp),
            )
            Text(text = stage.label(), style = ty.titleMedium, color = mc.textPrimary)
            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                completedStages.forEach { done ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = mc.lifePositive,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(done.label(), style = ty.labelMedium, color = mc.textSecondary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionsTab(
    uiState: DeckStudioUiState,
    onPerCardBudgetChange: (String) -> Unit,
    onTotalBudgetChange: (String) -> Unit,
    onOwnedFreeChange: (Boolean) -> Unit,
    onClearBudget: () -> Unit,
    onAdd: (AddSuggestion) -> Unit,
    onCut: (CardFit) -> Unit,
    onApplyCuratedStrategy: (CuratedStrategy, String?) -> Unit,
    onAutoDetectArchetypePlan: () -> Unit,
    // Deck Engine Unification (D4) — explicit "Unlock strategy" action, gated behind a confirmation
    // dialog owned by this composable (see the `strategyLocked` banner below).
    onUnlockStrategy: () -> Unit = {},
    // Motor B (Phase 4) — no-op defaults so this composable never has to be re-plumbed in every
    // call site if a preview/test constructs it without these.
    onAddCommunity: (com.mmg.manahub.feature.decks.domain.usecase.CommunityAddSuggestion) -> Unit = {},
    onCommunityCardTap: (String) -> Unit = {},
    onViewCommunityDecksForCard: (String) -> Unit = {},
    onOpenSimilarDeck: (Int) -> Unit = {},
    // Deck Wizard & Engine Rework plan WS8.2 -- no-op default, same "never re-plumb every call
    // site" precedent as the Motor B params above.
    onToggleIncludeOutsideCollection: (Boolean) -> Unit = {},
) {
    val mc = MaterialTheme.magicColors

    // Deck Wizard & Engine Rework plan, Workstream 8.4: the FULL analysis pass (loadAnalysis) is
    // staged -- non-null uiState.doctorStage covers the whole window from the first snapshot
    // through Motor A + the Scryfall backstop finishing (see DeckDoctorOrchestrator.loadAnalysis's
    // KDoc), replacing the old bare spinner with the same "current stage + completed checklist"
    // visual language as the wizard's own GeneratingContent. Motor B (community) is intentionally
    // NOT part of this gate -- it stays fire-and-forget with its own section-level isCommunityLoading
    // spinner further down, so a slow community fetch never delays revealing the rest of the tab.
    if (uiState.doctorStage != null) {
        DoctorStagedProgressContent(stage = uiState.doctorStage, completedStages = uiState.doctorCompletedStages)
        return
    }
    // Defensive fallback for the (should-be-impossible-in-practice) window before the FIRST
    // loadAnalysis has ever set a stage.
    if (uiState.isSuggestionsLoading && uiState.health == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MagicLoadingSpinner()
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

    // Deck Analysis Engine v2 Phase 3: the v2 unified result (score/pillars/strategy) that this
    // whole tab now reads from -- see this phase's report for why `health.evaluation` (the legacy
    // score/roleCoverage/warnings) is no longer read anywhere in this composable.
    val analysis = health.analysis
    val spacing = MaterialTheme.spacing
    var showStrategySheet by remember { mutableStateOf(false) }
    // Deck Analysis Engine v2 Phase 4 (telemetry): disambiguates a genuine picker abandon (swipe/
    // backdrop tap with no pick) from the sheet's own internal dismiss that immediately follows a
    // successful apply/auto-detect (same onDismissRequest/onDismiss callback fires for both) --
    // reset to false every time the sheet is (re)opened, flipped true inside the onApply/onAutoDetect
    // wrappers below BEFORE delegating to the real handlers.
    var strategyPicked by remember { mutableStateOf(false) }
    var showUnlockConfirmDialog by remember { mutableStateOf(false) }
    // Which single pillar tile is expanded (plan §3.4 item 3) -- Plan roles starts expanded by
    // default (plan §3.4 item 4: "always expanded by default"), tapping any tile (including the
    // already-expanded one, to collapse it) reassigns this.
    var expandedPillar by remember { mutableStateOf<PillarId?>(PillarId.PLAN_ROLES) }
    // Deck Engine Unification (D4): the deck's own persisted flag (Deck.strategyLocked), not the
    // orchestrator's own async-loaded DeckDoctorState.strategyLocked -- uiState.deck is always
    // current (observed live), so the "Deck plan" editor gate can never lag one analysis cycle
    // behind a fresh wizard build or a just-completed unlock.
    val strategyLocked = uiState.deck?.strategyLocked == true
    // Hoisted OUT of the LazyColumn content lambda: LazyListScope's content is a plain (non-
    // @Composable) lambda, so stringResource() may only be called inside an item{}/items{} block,
    // never directly in a `when { }`/`val` sitting between them (feedback_lazylistscope_content_not_composable).
    val communitySourceLabel = uiState.deck?.name.orEmpty()
        .ifBlank { stringResource(R.string.deck_studio_suggestions_community_header) }

    // Deck Analysis Engine v2 Phase 3: tribe candidates for CuratedStrategyPickerSheet's
    // requiresTribe sub-step -- derived from the LIVE deck's own tag fingerprint (already computed
    // by DeckScorer.profile, zero extra classification/network work), never a fresh commander-only
    // derivation (that pipeline, DeriveCommanderStrategiesUseCase, is wizard-only and needs an
    // EDHREC fetch this analysis-only picker has no reason to pull in). Capitalized-only labels
    // ("Elf", not "Elves") mirror that same use case's own `displayLabel` precedent.
    val availableTribes = remember(health.profile) {
        health.profile.tagFingerprint.keys
            .filter { it.startsWith(TribeDeriver.TRIBE_PREFIX) }
            .sortedByDescending { health.profile.tagFingerprint[it] ?: 0f }
            .map { fingerprintKey ->
                TribeOption(
                    key = fingerprintKey,
                    label = fingerprintKey.removePrefix(TribeDeriver.TRIBE_PREFIX).replaceFirstChar { it.uppercase() },
                )
            }
    }

    // Deck Engine Unification (D4): the "Deck plan" editor sheet is only reachable while unlocked
    // -- showStrategySheet can only ever flip true from the (now-hidden) chip's onClick below, but
    // this guard is defensive against a stray state carried across a locked->unlocked transition.
    if (showStrategySheet && !strategyLocked) {
        val strategySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // Deck Analysis Engine v2 Phase 4 (telemetry): a genuine abandon (swipe/backdrop tap without
        // picking anything) -- see `strategyPicked`'s own KDoc above for why this needs the flag.
        val onStrategySheetDismiss = {
            if (!strategyPicked) FirebaseCrashlytics.getInstance().log("deck_analysis_picker_abandoned")
            showStrategySheet = false
        }
        ModalBottomSheet(
            onDismissRequest = onStrategySheetDismiss,
            sheetState = strategySheetState,
            shape = BottomSheetShape,
            containerColor = mc.background,
        ) {
            CuratedStrategyPickerSheet(
                currentFormat = uiState.deck?.format
                    ?.let { fmt -> DeckFormat.entries.firstOrNull { it.name.equals(fmt, ignoreCase = true) } }
                    ?: DeckFormat.COMMANDER,
                selectedStrategyId = analysis?.strategy?.curatedStrategyId,
                availableTribes = availableTribes,
                onApply = { strategy, tribe -> strategyPicked = true; onApplyCuratedStrategy(strategy, tribe) },
                onAutoDetect = { strategyPicked = true; onAutoDetectArchetypePlan() },
                onDismiss = onStrategySheetDismiss,
                currentStrategyName = analysis?.strategy?.displayName,
            )
        }
    }

    if (showUnlockConfirmDialog) {
        MagicAlertDialog(
            onDismissRequest = { showUnlockConfirmDialog = false },
            title = stringResource(R.string.deck_studio_unlock_strategy_confirm_title),
            text = stringResource(R.string.deck_studio_unlock_strategy_confirm_body),
            confirmLabel = stringResource(R.string.deck_studio_unlock_strategy_confirm_action),
            onConfirm = {
                showUnlockConfirmDialog = false
                onUnlockStrategy()
            },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = {
                FirebaseCrashlytics.getInstance().log("deck_studio_unlock_strategy_declined")
                showUnlockConfirmDialog = false
            },
            confirmColor = MagicCtaColor.Error
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        // ── Strategy plan chip (plan §3.4 item 1-2) — hidden while strategyLocked (D4): editing
        // the deck plan on a wizard-built deck would contradict the strategy it was built for. A
        // dedicated banner (below) explains why and offers the explicit unlock action instead. ──
        if (strategyLocked) {
            item(key = "strategy_locked_banner") {
                StrategyLockedBanner(onUnlockClick = {
                    FirebaseCrashlytics.getInstance().log("deck_studio_unlock_strategy_dialog_shown")
                    showUnlockConfirmDialog = true
                })
            }
        } else if (analysis != null) {
            item(key = "strategy_plan_chip") {
                StrategyPlanChip(strategy = analysis.strategy, onClick = { showStrategySheet = true })
            }
            // No confident curated match AND still auto-detected (v2's "Custom"/no-match sentinel,
            // CuratedStrategyCatalog.nearestFor) — mirrors the retired ArchetypePlanHint's own
            // "GENERIC + no themes + not manual" gate, translated onto the v2 strategy shape.
            if (!analysis.strategy.isManualOverride && analysis.strategy.curatedStrategyId == null) {
                item(key = "strategy_plan_hint") {
                    StrategyPlanHint(onClick = { showStrategySheet = true })
                }
            }
        }

        // ── Score ring + pillar row + expanded pillar detail (plan §3.4 items 2-4) ─────────────
        if (analysis != null) {
            item(key = "analysis_score_ring") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    HealthScoreRing(score = analysis.totalScore)
                }
            }
            item(key = "analysis_pillar_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    analysis.pillars.forEach { pillar ->
                        PillarTile(
                            pillar = pillar,
                            expanded = expandedPillar == pillar.id,
                            onClick = { expandedPillar = if (expandedPillar == pillar.id) null else pillar.id },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            val expanded = analysis.pillars.firstOrNull { it.id == expandedPillar }
            if (expanded != null) {
                item(key = "analysis_pillar_detail_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SuggestionsSectionHeader(expanded.id.label(), mc.primaryAccent)
                        ExpandChevron(expanded = true)
                    }
                }
                // Plan-role table (plan §3.4 item 4) — PLAN_ROLES only; every other pillar has an
                // empty PillarResult.roleCoverage by construction (AnalysisEngine).
                if (expanded.roleCoverage.isNotEmpty()) {
                    items(expanded.roleCoverage, key = { "pillar_role_${it.roleKey}" }) { entry ->
                        RoleCoverageEntryRow(entry = entry)
                    }
                }
                // Severity-tinted findings for this pillar (plan §3.4 item 5) — already budgeted
                // to ≤3 (BLOCKERs uncapped) by AnalysisEngine; collapsedFindingsCount below is the
                // "N more" caption for anything past the budget.
                if (expanded.findings.isNotEmpty()) {
                    items(expanded.findings, key = { "pillar_finding_${expanded.id}_${it.key}" }) { finding ->
                        FindingRow(finding = finding)
                    }
                }
                if (expanded.collapsedFindingsCount > 0) {
                    item(key = "pillar_collapsed_${expanded.id}") {
                        CollapsedFindingsCaption(count = expanded.collapsedFindingsCount)
                    }
                }
            }
        }

        // Deck Analysis Engine v2 Phase 0 carve-out: Cuts/Adds/Motor B/Similar-decks are hidden
        // while DECK_STUDIO_SUGGESTIONS_ENGINE_ENABLED is off (the v2 rewrite is in progress) — the
        // plan chip and Health section above stay visible. See FeatureFlags.Decks KDoc.
        if (FeatureFlags.Decks.DECK_STUDIO_SUGGESTIONS_ENGINE_ENABLED) {
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

        // ── Adds — Motor A (Deck Doctor Community/Archetype plan Phase 2): collection-only,
        //    offline, always available. No budget UI is surfaced here (D5) -- every Motor A
        //    suggestion is already owned, so budget is moot for it; the budget-suggestions
        //    pipeline itself was DELETED in WS7.3 (D-H).
        //    Deck Builder v2 (plan §3.8): grouped by SuggestionCategoryResolver with a header chip
        //    per category (Removal/Ramp/Tokens/...) — presentation-side only, DeckDoctorOrchestrator's
        //    state shape is unchanged.
        item(key = "adds_header") {
            SuggestionsSectionHeader(
                stringResource(R.string.deck_studio_suggestions_from_collection),
                mc.lifePositive,
            )
        }
        // Deck Wizard & Engine Rework plan WS8.2 -- the Scryfall backstop toggle (3rd adds source,
        // a SEPARATE per-session choice from the wizard's own build-time toggle). Mirrors the
        // owned-cards-are-free Switch styling the retired `BudgetInputBar` used (WS7.3).
        item(key = "adds_outside_collection_toggle") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.deck_studio_include_outside_collection_toggle),
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.textPrimary,
                    )
                    androidx.compose.material3.Switch(
                        checked = uiState.includeOutsideCollection,
                        onCheckedChange = onToggleIncludeOutsideCollection,
                        colors = androidx.compose.material3.SwitchDefaults.colors(
                            checkedThumbColor = mc.background,
                            checkedTrackColor = mc.primaryAccent,
                            uncheckedThumbColor = mc.textSecondary,
                            uncheckedTrackColor = mc.surfaceVariant,
                        ),
                        modifier = Modifier.size(width = 52.dp, height = 48.dp),
                    )
                }
                if (uiState.outsideCollectionUnavailable) {
                    Text(
                        text = stringResource(R.string.deck_studio_outside_collection_unavailable),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.lifeNegative,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
            }
        }
        when {
            uiState.isAddsLoading -> item(key = "adds_loading") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    MagicLoadingSpinner()
                }
            }
            uiState.adds.isEmpty() -> item(key = "adds_empty") {
                EmptyState(
                    title = stringResource(R.string.deck_doctor_add_empty_title),
                    subtitle = stringResource(R.string.deck_studio_suggestions_from_collection_empty_subtitle),
                    icon = Icons.Default.AutoAwesome,
                )
            }
            else -> {
                val groupedAdds = com.mmg.manahub.feature.decks.presentation.components.SuggestionGrouping
                    .groupAddSuggestions(uiState.adds, uiState.health?.profile)
                groupedAdds.forEach { (category, suggestions) ->
                    item(key = "adds_cat_${category.id}") {
                        SuggestionCategoryHeaderChip(label = category.displayLabel, count = suggestions.size, tint = mc.lifePositive)
                    }
                    items(suggestions, key = { "add_${it.fit.card.scryfallId}" }) { suggestion ->
                        AddSuggestionRow(suggestion = suggestion, onAdd = { onAdd(suggestion) })
                    }
                }
            }
        }

        // ── Motor B (Deck Doctor Community/Archetype plan, Phase 4): community suggestions +
        //    "Decks like yours". Entirely additive — when the flag is off (or nothing loaded yet),
        //    `communityAdds`/`similarDecks` are simply empty and NOTHING below renders (no header,
        //    no empty state — the plan's "flag off = nothing community-related" requirement). A
        //    Worker/aggregate failure (`communityUnavailable`) shows ONE InlineErrorState for this
        //    section only; Motor A above is never affected. Deck Builder v2 (plan §3.8): grouped
        //    the SAME way as Motor A, but ALWAYS its own separate section — never merged with Motor
        //    A even within a shared category (D3).
        if (uiState.communityEngineEnabled) {
            item(key = "community_adds_header") {
                SuggestionsSectionHeader(
                    stringResource(R.string.deck_studio_suggestions_community_header),
                    mc.secondaryAccent,
                )
            }
            when {
                uiState.isCommunityLoading && uiState.communityAdds.isEmpty() -> item(key = "community_adds_loading") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        MagicLoadingSpinner()
                    }
                }
                uiState.communityUnavailable -> item(key = "community_unavailable") {
                    InlineErrorState(message = stringResource(R.string.deck_doctor_community_unavailable))
                }
                uiState.communityAdds.isEmpty() -> item(key = "community_adds_empty") {
                    Text(
                        text = stringResource(R.string.deck_doctor_community_empty),
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.textSecondary,
                    )
                }
                else -> {
                    val groupedCommunityAdds = com.mmg.manahub.feature.decks.presentation.components.SuggestionGrouping
                        .groupCommunityAddSuggestions(uiState.communityAdds, uiState.health?.profile)
                    groupedCommunityAdds.forEach { (category, suggestions) ->
                        item(key = "community_cat_${category.id}") {
                            SuggestionCategoryHeaderChip(label = category.displayLabel, count = suggestions.size, tint = mc.secondaryAccent)
                        }
                        items(suggestions, key = { "community_add_${it.card.scryfallId}" }) { suggestion ->
                            CommunityAddSuggestionRow(
                                suggestion = suggestion,
                                sourceLabel = communitySourceLabel,
                                onAdd = { onAddCommunity(suggestion) },
                                onViewDecks = { onViewCommunityDecksForCard(suggestion.card.name) },
                                onCardTap = { onCommunityCardTap(suggestion.card.scryfallId) },
                            )
                        }
                    }
                }
            }

            // "Decks like yours" navigates straight into Screen.CommunityDeckDetail.
            if (uiState.similarDecks.isNotEmpty()) {
                item(key = "similar_decks_header") {
                    SuggestionsSectionHeader(
                        stringResource(R.string.deck_studio_suggestions_similar_decks_header),
                        mc.secondaryAccent,
                    )
                }
                item(key = "similar_decks_carousel") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        items(uiState.similarDecks, key = { "similar_${it.archidektId}" }) { result ->
                            SimilarDeckCard(
                                result = result,
                                onClick = { onOpenSimilarDeck(result.archidektId) },
                            )
                        }
                    }
                }
            }
        }
        } // FeatureFlags.Decks.DECK_STUDIO_SUGGESTIONS_ENGINE_ENABLED
    }
}

/**
 * Deck Builder v2 (plan §3.8) category sub-header for a grouped suggestions list ("Removal (5)") —
 * a lighter-weight chip than [SuggestionsSectionHeader], since it sits ONE level below it (section
 * = Motor A/Motor B, category = Removal/Ramp/... within that section).
 */
@Composable
private fun SuggestionCategoryHeaderChip(label: String, count: Int, tint: androidx.compose.ui.graphics.Color) {
    Surface(shape = ChipShape, color = tint.copy(alpha = 0.14f)) {
        Text(
            text = stringResource(R.string.deck_studio_suggestion_category_chip, label, count),
            style = MaterialTheme.magicTypography.labelMedium,
            color = tint,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xs),
        )
    }
}

/**
 * Deck Engine Unification (D4): shown INSTEAD of the "Deck plan" chip while `Deck.strategyLocked`
 * -- explains why editing is unavailable and offers the explicit unlock action (behind a
 * confirmation dialog, owned by the caller). Never a silent hide with no explanation.
 */
@Composable
private fun StrategyLockedBanner(onUnlockClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(shape = CardShape, color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = stringResource(R.string.deck_studio_strategy_locked_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
            )
            Text(
                text = stringResource(R.string.deck_studio_strategy_locked_body),
                style = ty.bodySmall,
                color = mc.textSecondary,
            )
            OutlinedButton(
                onClick = onUnlockClick,
                shape = ChipShape,
                border = BorderStroke(1.dp, mc.textSecondary),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.deck_studio_strategy_locked_unlock_action), color = mc.textPrimary)
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

/**
 * Localized label for [GroupingMode] (F.4 — migrated from [GroupingFlowSelector]'s hardcoded
 * English literals to [ManaHubBottomSheetSelector], which needs a [stringResource] per item, like
 * `CollectionGroupingMode.displayResId` in `CollectionScreen.kt`). Kept PRIVATE and duplicated
 * rather than shared with `DraftResultScreen`'s equivalent — a ~10-line enum-to-string mapping is
 * not worth a cross-feature (decks <-> draft) dependency.
 */
private val GroupingMode.displayResId: Int
    get() = when (this) {
        GroupingMode.TYPE -> R.string.deckbuilder_group_type
        GroupingMode.COLOR -> R.string.deckbuilder_group_color
        GroupingMode.COST -> R.string.deckbuilder_group_cmc
        GroupingMode.TAG -> R.string.deckbuilder_group_tag
    }
