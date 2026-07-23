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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.mmg.manahub.core.model.DeckCard
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.model.GroupingMode
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.domain.usecase.decks.GetDeckGameStatsUseCase
import com.mmg.manahub.core.ui.components.CardSearchSheet
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.GroupingFlowSelector
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
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
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckSkeletons
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.model.AlmostCombo
import com.mmg.manahub.feature.decks.domain.model.Combo
import com.mmg.manahub.feature.decks.domain.model.ComboResult
import com.mmg.manahub.feature.decks.domain.template.DiscoverySearchFilter
import com.mmg.manahub.feature.decks.domain.usecase.AddSuggestion
import com.mmg.manahub.feature.decks.presentation.components.AddBasicLandsRow
import com.mmg.manahub.feature.decks.presentation.components.ArchetypePlanChip
import com.mmg.manahub.feature.decks.presentation.components.ArchetypePlanHint
import com.mmg.manahub.feature.decks.presentation.components.ArchetypePlanSheetContent
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
import com.mmg.manahub.feature.decks.presentation.components.AddSuggestionRow
import com.mmg.manahub.feature.decks.presentation.components.CommunityAddSuggestionRow
import com.mmg.manahub.feature.decks.presentation.components.SimilarDeckCard
import com.mmg.manahub.feature.decks.presentation.components.CutSuggestionRow
import com.mmg.manahub.feature.decks.presentation.components.HealthScoreRing
import com.mmg.manahub.feature.decks.presentation.components.RoleCoverageRow
import com.mmg.manahub.feature.decks.presentation.components.SeedsContent
import com.mmg.manahub.feature.decks.presentation.components.WarningChip
import com.mmg.manahub.feature.decks.presentation.components.WarningOverlay
import com.mmg.manahub.feature.decks.presentation.components.groupCards
import com.mmg.manahub.feature.decks.presentation.components.key
import com.mmg.manahub.feature.decks.presentation.components.label
import com.mmg.manahub.core.ui.components.InlineErrorState
import androidx.compose.foundation.lazy.LazyRow

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
    viewModel: DeckStudioViewModel = koinViewModel(),
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
    val seedBuiltMsg = stringResource(R.string.deck_studio_seed_built)
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

    // Deck Builder v2 (plan D10/§3.7): a single "Build from seed" entry point routes to the v2
    // wizard when DECK_BUILDER_V2_ENABLED, else opens the legacy seed sheet -- shared by BOTH the
    // top-bar overflow item and the empty-state primary button so the two never drift.
    val handleBuildFromSeed: () -> Unit = {
        if (DeckFeatureFlags.DECK_BUILDER_V2_ENABLED) {
            onNavigateToWizard(null, null, null, null, null)
        } else {
            viewModel.openSeedSheet()
        }
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
                            onApplyArchetypePlan = { macro, themes ->
                                viewModel.onSetArchetypeOverride(macro, themes)
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
                    // Phase 5: the toggle row is only shown when the master flag is on.
                    useCommunityData = uiState.useCommunityDataForSeed,
                    onToggleUseCommunityData = if (uiState.communityEngineEnabled) {
                        viewModel::toggleUseCommunityDataForSeed
                    } else {
                        null
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
            // Deck Builder v2 Phase 5 (plan D10/§3.7): the SAME entry point, content swaps on the
            // flag -- Discoveries v2 clusters (identity-only, color-coherent) hand off to the v2
            // wizard pre-filled (D11); the legacy content still seeds the old seed sheet.
            if (DeckFeatureFlags.DISCOVERIES_V2_ENABLED) {
                InspirationsSheetContentV2(
                    discoveries = uiState.discoveriesV2,
                    filteredDiscoveries = uiState.filteredDiscoveriesV2,
                    isLoading = uiState.isLoadingDiscoveries,
                    onCardClick = { id ->
                        focusManager.clearFocus()
                        onCardClick(id)
                    },
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
                    isLoadingCombos = uiState.isLoadingCombos,
                    onUseComboAsSeed = { cardNames ->
                        viewModel.closeInspirations()
                        onNavigateToWizard(null, null, null, null, cardNames)
                    },
                )
            } else {
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
                    // Deck Builder v2 (plan D10/§3.7): visible whenever EITHER the legacy seed
                    // sheet OR the v2 wizard is enabled -- handleBuildFromSeed picks the destination.
                    if (DeckFeatureFlags.DECK_STUDIO_BUILD_FROM_SEED_ENABLED || DeckFeatureFlags.DECK_BUILDER_V2_ENABLED) {
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
                    // Deck Builder v2 (plan D10/§3.7): visible whenever EITHER the legacy
                    // discoverSynergies content OR Discoveries v2 is enabled -- the sheet content
                    // itself branches on DISCOVERIES_V2_ENABLED (see the ModalBottomSheet below).
                    if (DeckFeatureFlags.DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED || DeckFeatureFlags.DISCOVERIES_V2_ENABLED) {
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

    // Smooth crossfade between the loading spinner and the loaded content (UI polish, 2026-07-22),
    // matching the sibling (dead) DeckBuilderScreen.kt's ViewStepContent AnimatedContent pattern —
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
                androidx.compose.material3.CircularProgressIndicator(color = mc.primaryAccent)
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
                val seedEnabled = DeckFeatureFlags.DECK_STUDIO_BUILD_FROM_SEED_ENABLED || DeckFeatureFlags.DECK_BUILDER_V2_ENABLED
                val inspirationsEnabled = DeckFeatureFlags.DECK_STUDIO_BROWSE_INSPIRATIONS_ENABLED || DeckFeatureFlags.DISCOVERIES_V2_ENABLED
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
 * Deck Builder v2 Phase 5 (plan §3.5) sheet content -- the [DeckDiscoveryV2] sibling of
 * [InspirationsSheetContent], rendered instead of it when `DeckFeatureFlags
 * .DISCOVERIES_V2_ENABLED` is on (same entry point, D10/§3.7). "Build this" hands off to the v2
 * wizard pre-filled (D11) instead of the old seed-sheet handoff.
 *
 * Deck Engine Unification plan D7 (Phase 4) redesign: this is now a two-tab synergy browser
 * (Strategies / Combos) with free-text + search-by-card filtering on the Strategies tab (4.1/4.2)
 * and a Commander Spellbook combos tab (4.3). Stateless per the file's own convention -- every
 * input is a param, every mutation a callback to [DeckStudioViewModel].
 *
 * @param discoveries the FULL unfiltered cluster list -- only used to derive the search-by-card
 *   pickable pool ([DiscoverySearchFilter.pickableCardNames]); the Strategies tab itself renders
 *   [filteredDiscoveries].
 * @param filteredDiscoveries [discoveries] narrowed by [searchQuery]/[selectedCardNames]
 *   ([DeckStudioViewModel.filteredDiscoveriesV2] -- the VM is the single source of truth for the
 *   filter, this Composable stays a dumb reader).
 */
@Composable
private fun InspirationsSheetContentV2(
    discoveries: List<com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2>,
    filteredDiscoveries: List<com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2>,
    isLoading: Boolean,
    onCardClick: (String) -> Unit,
    onBuildThis: (com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2) -> Unit,
    inspirationsTab: InspirationsTab,
    onSelectTab: (InspirationsTab) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCardNames: Set<String>,
    onToggleSearchCard: (String) -> Unit,
    onClearSearch: () -> Unit,
    comboResult: ComboResult?,
    isLoadingCombos: Boolean,
    onUseComboAsSeed: (List<String>) -> Unit,
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
                isLoading = isLoading,
                onCardClick = onCardClick,
                onBuildThis = onBuildThis,
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                selectedCardNames = selectedCardNames,
                onToggleSearchCard = onToggleSearchCard,
                onClearSearch = onClearSearch,
            )
            InspirationsTab.COMBOS -> CombosTabContent(
                comboResult = comboResult,
                isLoading = isLoadingCombos,
                onCardClick = onCardClick,
                onUseComboAsSeed = onUseComboAsSeed,
            )
        }
    }
}

/**
 * The Strategies tab (4.1 strictness lives entirely in [com.mmg.manahub.feature.decks.domain
 * .template.DiscoverSynergiesV2UseCase]; this composable is 4.2's search UI only).
 */
@Composable
private fun StrategiesTabContent(
    discoveries: List<com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2>,
    filteredDiscoveries: List<com.mmg.manahub.feature.decks.domain.template.DeckDiscoveryV2>,
    isLoading: Boolean,
    onCardClick: (String) -> Unit,
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
                        FilterChip(
                            selected = name in selectedCardNames,
                            onClick = { onToggleSearchCard(name) },
                            label = { Text(name, style = ty.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = mc.primaryAccent.copy(alpha = 0.2f),
                                selectedLabelColor = mc.primaryAccent,
                            ),
                        )
                    }
                }
            }
        }

        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = mc.primaryAccent)
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
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(vertical = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                // Keyed by the cluster's own stable identity (tag key or tribe key) -- distinct
                // from the legacy MagicDiscovery key shape (no primaryTag on this model).
                items(filteredDiscoveries.take(20), key = { it.key.stableKey() }) { discovery ->
                    com.mmg.manahub.feature.decks.presentation.components.DiscoveryRowV2(
                        discovery = discovery,
                        onCardClick = onCardClick,
                        onBuildThis = { onBuildThis(discovery) },
                    )
                }
            }
        }
    }
}

/** The Combos tab (Deck Engine Unification plan D7, 4.3). */
@Composable
private fun CombosTabContent(
    comboResult: ComboResult?,
    isLoading: Boolean,
    onCardClick: (String) -> Unit,
    onUseComboAsSeed: (List<String>) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing

    when {
        isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = mc.primaryAccent)
        }
        comboResult == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.CircularProgressIndicator(color = mc.primaryAccent)
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
                        onCardClick = onCardClick,
                        onUseAsSeed = { onUseComboAsSeed(combo.cardNames) },
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
                        onCardClick = onCardClick,
                        onUseAsSeed = { onUseComboAsSeed(almost.ownedCardNames + almost.missingCardName) },
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
 * by the live deck via [DeckStudioViewModel]. [BudgetInputBar] is NOT shown here (D5 — Motor A
 * suggestions are already owned, so budget is moot); it stays in the codebase for a different
 * editing surface. The row composables and string helpers live in
 * [com.mmg.manahub.feature.decks.presentation.components] — this is now the SOLE Deck Doctor UI
 * surface; the standalone Deck Improvement screen those composables were originally copied from
 * was retired in Phase 0.5 (D10).
 *
 * Stateless: all state comes from [uiState]; every mutation is a callback to the VM.
 */
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
    onApplyArchetypePlan: (ArchetypeId, List<ThemeId>) -> Unit,
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
    var showArchetypeSheet by remember { mutableStateOf(false) }
    var showUnlockConfirmDialog by remember { mutableStateOf(false) }
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

    // Deck Engine Unification (D4): the "Deck plan" editor sheet is only reachable while unlocked
    // -- showArchetypeSheet can only ever flip true from the (now-hidden) chip's onClick below, but
    // this guard is defensive against a stray state carried across a locked->unlocked transition.
    if (showArchetypeSheet && !strategyLocked) {
        val archetypeSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showArchetypeSheet = false },
            sheetState = archetypeSheetState,
            shape = BottomSheetShape,
            containerColor = mc.background,
        ) {
            ArchetypePlanSheetContent(
                initialMacro = health.archetypeResolution.macro,
                initialThemes = health.archetypeResolution.themes,
                onApply = onApplyArchetypePlan,
                onAutoDetect = onAutoDetectArchetypePlan,
                onDismiss = { showArchetypeSheet = false },
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
        // ── Archetype plan chip (Phase 1.7) — hidden while strategyLocked (D4): editing the deck
        // plan on a wizard-built deck would contradict the strategy it was built for. A dedicated
        // banner (below) explains why and offers the explicit unlock action instead. ───────────
        if (strategyLocked) {
            item(key = "strategy_locked_banner") {
                StrategyLockedBanner(onUnlockClick = {
                    FirebaseCrashlytics.getInstance().log("deck_studio_unlock_strategy_dialog_shown")
                    showUnlockConfirmDialog = true
                })
            }
        } else {
        item(key = "archetype_plan_chip") {
            ArchetypePlanChip(
                macro = health.archetypeResolution.macro,
                themes = health.archetypeResolution.themes,
                isManualOverride = health.archetypeResolution.isManualOverride,
                onClick = { showArchetypeSheet = true },
            )
        }
        if (!health.archetypeResolution.isManualOverride &&
            health.archetypeResolution.macro == ArchetypeId.GENERIC &&
            health.archetypeResolution.themes.isEmpty()
        ) {
            item(key = "archetype_plan_hint") {
                ArchetypePlanHint(onClick = { showArchetypeSheet = true })
            }
        }
        }

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
            items(evaluation.warnings.distinctBy { it.key }, key = { "warn_${it.key}" }) { warning ->
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

        // ── Adds — Motor A (Deck Doctor Community/Archetype plan Phase 2): collection-only,
        //    offline, always available. `BudgetInputBar` stays in the codebase (D5) but is not
        //    surfaced here — every Motor A suggestion is already owned, so budget is moot for it.
        //    Deck Builder v2 (plan §3.8): grouped by SuggestionCategoryResolver with a header chip
        //    per category (Removal/Ramp/Tokens/...) — presentation-side only, DeckDoctorOrchestrator's
        //    state shape is unchanged.
        item(key = "adds_header") {
            SuggestionsSectionHeader(
                stringResource(R.string.deck_studio_suggestions_from_collection),
                mc.lifePositive,
            )
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
                        androidx.compose.material3.CircularProgressIndicator(color = mc.secondaryAccent)
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
