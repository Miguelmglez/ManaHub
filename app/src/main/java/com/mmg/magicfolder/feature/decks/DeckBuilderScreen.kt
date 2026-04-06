package com.mmg.magicfolder.feature.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.domain.model.BuilderStep
import com.mmg.magicfolder.core.domain.model.BuilderTab
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.DeckBuilderState
import com.mmg.magicfolder.core.domain.model.DeckCard
import com.mmg.magicfolder.core.domain.model.DeckFormat
import com.mmg.magicfolder.core.domain.model.ReviewGroupBy
import com.mmg.magicfolder.core.ui.theme.MagicTheme
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.decks.components.BuildingFilters
import com.mmg.magicfolder.feature.decks.components.CommanderBanner
import com.mmg.magicfolder.feature.decks.components.CommanderSearchSheet
import com.mmg.magicfolder.feature.decks.components.DeckCardRow
import com.mmg.magicfolder.feature.decks.components.LandsSection
import com.mmg.magicfolder.feature.decks.components.ManaCurveChart

@Composable
fun DeckBuilderScreen(
    onNavigateBack: () -> Unit,
    onDeckSaved:    () -> Unit,
    viewModel:      DeckBuilderViewModel = hiltViewModel(),
) {
    val state                by viewModel.state.collectAsStateWithLifecycle()
    val commanderResults     by viewModel.commanderResults.collectAsStateWithLifecycle()
    val isSearchingCommander by viewModel.isSearchingCommander.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Scaffold(
        containerColor = mc.background,
        topBar = {
            Surface(color = mc.backgroundSecondary) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = {
                        when (state.step) {
                            BuilderStep.SETUP    -> onNavigateBack()
                            BuilderStep.BUILDING -> onNavigateBack()
                            BuilderStep.REVIEW   -> viewModel.goBackToBuilding()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint               = mc.textSecondary,
                        )
                    }
                    Text(
                        text = when (state.step) {
                            BuilderStep.SETUP    -> stringResource(R.string.deckbuilder_title)
                            BuilderStep.BUILDING -> "${state.totalCardCount} / ${state.format.targetDeckSize}"
                            BuilderStep.REVIEW   -> stringResource(R.string.deckbuilder_step_review)
                        },
                        style    = ty.titleLarge,
                        color    = mc.textPrimary,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.step == BuilderStep.BUILDING) {
                        TextButton(onClick = viewModel::goToReview) {
                            Text(
                                text  = stringResource(R.string.deckbuilder_step_review),
                                color = mc.primaryAccent,
                                style = ty.labelLarge,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        when (state.step) {
            BuilderStep.SETUP -> SetupStep(
                state                = state,
                commanderResults     = commanderResults,
                isSearchingCommander = isSearchingCommander,
                onCommanderSearch    = viewModel::searchCommander,
                onCommanderClear     = viewModel::clearCommanderSearch,
                onBuild              = { name, format, commander ->
                    viewModel.setupDeck(name, format, commander)
                },
                modifier             = Modifier.padding(padding),
            )
            BuilderStep.BUILDING -> BuildingStep(
                state                 = state,
                onAddToMainboard      = viewModel::addToMainboard,
                onAddToSideboard      = viewModel::addToSideboard,
                onRemoveFromMainboard = viewModel::removeFromMainboard,
                onRemoveNonBasicLand  = viewModel::removeNonBasicLand,
                onSetTab              = viewModel::setActiveTab,
                onToggleColorFilter   = viewModel::toggleColorFilter,
                onClearFilters        = viewModel::clearFilters,
                onGoToReview          = viewModel::goToReview,
                getFilteredCards      = viewModel::getFilteredCards,
                modifier              = Modifier.padding(padding),
            )
            BuilderStep.REVIEW -> ReviewStep(
                state                    = state,
                onRemoveFromMainboard    = viewModel::removeFromMainboard,
                onRemoveFromSideboard    = viewModel::removeFromSideboard,
                onMoveToSideboard        = viewModel::moveToSideboard,
                onMoveToMainboard        = viewModel::moveToMainboard,
                onRemoveNonBasicLand     = viewModel::removeNonBasicLand,
                onSetGroupBy             = viewModel::setReviewGroupBy,
                onAcknowledgeOverLimit   = viewModel::acknowledgeOverLimit,
                onUnacknowledgeOverLimit = viewModel::unacknowledgeOverLimit,
                onSave                   = { viewModel.saveDeck(onDeckSaved) },
                modifier                 = Modifier.padding(padding),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 1 — Setup
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SetupStep(
    state:                DeckBuilderState,
    commanderResults:     List<Card>,
    isSearchingCommander: Boolean,
    onCommanderSearch:    (String) -> Unit,
    onCommanderClear:     () -> Unit,
    onBuild:              (String, DeckFormat, Card?) -> Unit,
    modifier:             Modifier,
) {
    val mc = MaterialTheme.magicColors
    var deckName          by remember { mutableStateOf("") }
    var selectedFormat    by remember { mutableStateOf(DeckFormat.STANDARD) }
    var selectedCommander by remember { mutableStateOf<Card?>(null) }
    var showCommanderSheet by remember { mutableStateOf(false) }

    val canBuild = deckName.isNotBlank() &&
        (!selectedFormat.requiresCommander || selectedCommander != null)

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Deck name
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.deckbuilder_setup_name_label))
                OutlinedTextField(
                    value         = deckName,
                    onValueChange = { deckName = it },
                    placeholder   = { Text(stringResource(R.string.deckbuilder_name_hint), color = mc.textDisabled) },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                        focusedTextColor     = mc.textPrimary,
                        unfocusedTextColor   = mc.textPrimary,
                        cursorColor          = mc.primaryAccent,
                    ),
                    textStyle = MaterialTheme.magicTypography.bodyLarge,
                )
            }

            // Format selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(stringResource(R.string.deckbuilder_setup_format_label))
                FormatGrid(
                    selected = selectedFormat,
                    onSelect = { fmt ->
                        selectedFormat    = fmt
                        selectedCommander = null
                        onCommanderClear()
                    },
                )
            }

            // Commander search (only for Commander format)
            if (selectedFormat.requiresCommander) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(stringResource(R.string.deckbuilder_setup_commander_label))
                    if (selectedCommander != null) {
                        CommanderBanner(
                            commander = selectedCommander!!,
                            modifier  = Modifier.clickable { showCommanderSheet = true },
                        )
                    } else {
                        OutlinedButton(
                            onClick  = { showCommanderSheet = true },
                            modifier = Modifier.fillMaxWidth(),
                            border   = androidx.compose.foundation.BorderStroke(
                                1.dp, mc.primaryAccent.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text  = stringResource(R.string.deckbuilder_search_commander_hint),
                                style = MaterialTheme.magicTypography.labelLarge,
                                color = mc.primaryAccent,
                            )
                        }
                    }
                }
            }
        }

        // Build button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(mc.background)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Button(
                onClick  = { onBuild(deckName, selectedFormat, selectedCommander) },
                enabled  = canBuild,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = mc.primaryAccent,
                    disabledContainerColor = mc.surfaceVariant,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text  = stringResource(R.string.deckbuilder_setup_build_button),
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = if (canBuild) mc.background else mc.textDisabled,
                )
            }
        }
    }

    if (showCommanderSheet) {
        CommanderSearchSheet(
            results       = commanderResults,
            isSearching   = isSearchingCommander,
            onQueryChange = onCommanderSearch,
            onSelectCard  = { card ->
                selectedCommander  = card
                showCommanderSheet = false
                onCommanderClear()
            },
            onDismiss = {
                showCommanderSheet = false
                onCommanderClear()
            },
        )
    }
}

@Composable
private fun FormatGrid(
    selected: DeckFormat,
    onSelect: (DeckFormat) -> Unit,
) {
    val mc   = MaterialTheme.magicColors
    val rows = DeckFormat.entries.chunked(4)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { fmt ->
                    val sel = fmt == selected
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface)
                            .border(
                                width = if (sel) 1.5.dp else 0.5.dp,
                                color = if (sel) mc.primaryAccent else mc.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                            )
                            .clickable { onSelect(fmt) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text      = stringResource(fmt.displayNameRes),
                            style     = MaterialTheme.magicTypography.labelMedium,
                            color     = if (sel) mc.primaryAccent else mc.textSecondary,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                // Pad last row if uneven
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 2 — Building
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildingStep(
    state:                DeckBuilderState,
    onAddToMainboard:     (DeckCard) -> Unit,
    onAddToSideboard:     (DeckCard) -> Unit,
    onRemoveFromMainboard:(String) -> Unit,
    onRemoveNonBasicLand: (String) -> Unit,
    onSetTab:             (BuilderTab) -> Unit,
    onToggleColorFilter:  (String) -> Unit,
    onClearFilters:       () -> Unit,
    onGoToReview:         () -> Unit,
    getFilteredCards:     (List<DeckCard>) -> List<DeckCard>,
    modifier:             Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val sourceCards = when (state.activeTab) {
        BuilderTab.MY_COLLECTION       -> state.collectionCards
        BuilderTab.SCRYFALL_SUGGESTIONS -> state.suggestions
    }
    val visibleCards = remember(sourceCards, state.filterColors, state.filterType, state.filterMaxCmc) {
        getFilteredCards(sourceCards)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Progress bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(mc.backgroundSecondary)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${state.totalCardCount} / ${state.format.targetDeckSize}",
                    style = ty.labelLarge,
                    color = mc.textSecondary,
                )
                Text(
                    "${(state.completionPercent * 100).toInt()}%",
                    style = ty.labelLarge,
                    color = mc.primaryAccent,
                )
            }
            LinearProgressIndicator(
                progress   = { state.completionPercent },
                modifier   = Modifier.fillMaxWidth().height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color      = mc.primaryAccent,
                trackColor = mc.surfaceVariant,
            )
        }

        // Main scrollable content
        LazyColumn(
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            // Commander banner
            state.commander?.let { cmdr ->
                item {
                    CommanderBanner(
                        commander = cmdr,
                        modifier  = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            // Mainboard quick-view
            if (state.mainboard.isNotEmpty()) {
                item {
                    SectionLabel(
                        text     = "${stringResource(R.string.deckbuilder_step_building)} · ${state.nonLandCardCount}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                items(state.mainboard, key = { "main_${it.card.scryfallId}" }) { dc ->
                    DeckCardRow(
                        deckCard = dc,
                        onRemove = { onRemoveFromMainboard(dc.card.scryfallId) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
            }

            // Lands
            if (state.nonBasicLands.isNotEmpty() || state.basicLands.total > 0) {
                item {
                    LandsSection(
                        nonBasicLands    = state.nonBasicLands,
                        basicLands       = state.basicLands,
                        onRemoveNonBasic = onRemoveNonBasicLand,
                        modifier         = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            // Divider & tab row
            item {
                Spacer(Modifier.height(8.dp))
                TabRow(
                    selectedTabIndex = state.activeTab.ordinal,
                    containerColor   = mc.backgroundSecondary,
                    contentColor     = mc.primaryAccent,
                    modifier         = Modifier.fillMaxWidth(),
                ) {
                    BuilderTab.entries.forEach { tab ->
                        Tab(
                            selected = state.activeTab == tab,
                            onClick  = { onSetTab(tab) },
                            text     = {
                                Text(
                                    text  = when (tab) {
                                        BuilderTab.MY_COLLECTION        -> stringResource(R.string.deckbuilder_tab_collection)
                                        BuilderTab.SCRYFALL_SUGGESTIONS -> stringResource(R.string.deckbuilder_tab_suggestions)
                                    },
                                    style = MaterialTheme.magicTypography.labelMedium,
                                )
                            },
                        )
                    }
                }
            }

            // Filters
            item {
                BuildingFilters(
                    selectedColors = state.filterColors,
                    onToggleColor  = onToggleColorFilter,
                    onClearFilters = onClearFilters,
                    modifier       = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // Card list
            when {
                (state.activeTab == BuilderTab.MY_COLLECTION && state.isLoadingCollection) ||
                (state.activeTab == BuilderTab.SCRYFALL_SUGGESTIONS && state.isLoadingSuggestions) -> {
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = mc.primaryAccent)
                        }
                    }
                }
                visibleCards.isEmpty() -> {
                    item {
                        Text(
                            text     = stringResource(R.string.deckbuilder_no_cards),
                            style    = MaterialTheme.magicTypography.bodyMedium,
                            color    = mc.textDisabled,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        )
                    }
                }
                else -> {
                    items(visibleCards, key = { "${state.activeTab}_${it.card.scryfallId}" }) { dc ->
                        DeckCardRow(
                            deckCard = dc,
                            onAdd    = { onAddToMainboard(dc) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        // Bottom summary bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(mc.backgroundSecondary)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text  = stringResource(
                            R.string.deckbuilder_format_total_cards,
                            state.totalCardCount
                        ),
                        style = ty.labelLarge,
                        color = mc.textPrimary,
                    )
                    Text(
                        text  = stringResource(
                            R.string.deckbuilder_lands,
                        ) + ": ${state.totalLandCount}",
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                    )
                }
                Button(
                    onClick  = onGoToReview,
                    colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                    shape    = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(40.dp),
                ) {
                    Text(
                        text  = stringResource(R.string.deckbuilder_step_review),
                        style = ty.labelLarge,
                        color = mc.background,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 3 — Review
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ReviewStep(
    state:                    DeckBuilderState,
    onRemoveFromMainboard:    (String) -> Unit,
    onRemoveFromSideboard:    (String) -> Unit,
    onMoveToSideboard:        (String) -> Unit,
    onMoveToMainboard:        (String) -> Unit,
    onRemoveNonBasicLand:     (String) -> Unit,
    onSetGroupBy:             (ReviewGroupBy) -> Unit,
    onAcknowledgeOverLimit:   (String) -> Unit,
    onUnacknowledgeOverLimit: (String) -> Unit,
    onSave:                   () -> Unit,
    modifier:                 Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    // Group mainboard
    val grouped = remember(state.mainboard, state.reviewGroupBy) {
        when (state.reviewGroupBy) {
            ReviewGroupBy.TYPE   -> state.mainboard.groupBy { cardTypeLabel(it.card.typeLine) }
            ReviewGroupBy.COLOR  -> state.mainboard.groupBy { mainColorLabel(it.card.colors) }
            ReviewGroupBy.CMC    -> state.mainboard.groupBy { cmcLabel(it.card.cmc) }
            ReviewGroupBy.RARITY -> state.mainboard.groupBy { it.card.rarity.replaceFirstChar { c -> c.uppercase() } }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Mana curve chart
            item {
                ManaCurveChart(
                    cards    = state.mainboard,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }

            // Group-by selector
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = "Group:",
                        style = ty.labelSmall,
                        color = mc.textDisabled,
                    )
                    ReviewGroupBy.entries.forEach { g ->
                        val sel = g == state.reviewGroupBy
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (sel) mc.primaryAccent.copy(0.15f) else mc.surface)
                                .border(
                                    width = if (sel) 1.dp else 0.dp,
                                    color = if (sel) mc.primaryAccent else mc.surface,
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .clickable { onSetGroupBy(g) }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text  = when (g) {
                                    ReviewGroupBy.TYPE   -> stringResource(R.string.deckbuilder_group_type)
                                    ReviewGroupBy.COLOR  -> stringResource(R.string.deckbuilder_group_color)
                                    ReviewGroupBy.CMC    -> stringResource(R.string.deckbuilder_group_cmc)
                                    ReviewGroupBy.RARITY -> stringResource(R.string.deckbuilder_group_rarity)
                                },
                                style = ty.labelSmall,
                                color = if (sel) mc.primaryAccent else mc.textSecondary,
                            )
                        }
                    }
                }
            }

            // Grouped mainboard sections
            grouped.entries.sortedBy { it.key }.forEach { (groupLabel, cards) ->
                item(key = "group_$groupLabel") {
                    SectionLabel(
                        text     = "$groupLabel (${cards.sumOf { it.quantity }})",
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                items(cards, key = { "main_${it.card.scryfallId}" }) { dc ->
                    Column {
                        Row(
                            modifier              = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            DeckCardRow(
                                deckCard = dc,
                                onRemove = { onRemoveFromMainboard(dc.card.scryfallId) },
                                modifier = Modifier.weight(1f),
                            )
                            // Move to sideboard button
                            TextButton(
                                onClick  = { onMoveToSideboard(dc.card.scryfallId) },
                                modifier = Modifier.width(48.dp),
                            ) {
                                Text("SB", style = ty.labelSmall, color = mc.secondaryAccent)
                            }
                        }
                        // Copy limit warning for standard-like formats
                        if (dc.card.scryfallId in state.overLimitCards) {
                            val isAcknowledged = dc.card.scryfallId in state.acknowledgedOverLimitCards
                            Surface(
                                shape    = RoundedCornerShape(6.dp),
                                color    = if (isAcknowledged) mc.surface else mc.lifeNegative.copy(alpha = 0.1f),
                                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, top = 2.dp, bottom = 4.dp),
                            ) {
                                Row(
                                    modifier          = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text  = stringResource(R.string.deckbuilder_copy_warning, state.format.maxCopies),
                                        style = ty.labelSmall,
                                        color = if (isAcknowledged) mc.textDisabled else mc.lifeNegative,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Checkbox(
                                        checked         = isAcknowledged,
                                        onCheckedChange = { checked ->
                                            if (checked) onAcknowledgeOverLimit(dc.card.scryfallId)
                                            else onUnacknowledgeOverLimit(dc.card.scryfallId)
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor   = mc.primaryAccent,
                                            uncheckedColor = mc.lifeNegative,
                                            checkmarkColor = mc.background,
                                        ),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Lands section
            if (state.nonBasicLands.isNotEmpty() || state.basicLands.total > 0) {
                item {
                    LandsSection(
                        nonBasicLands    = state.nonBasicLands,
                        basicLands       = state.basicLands,
                        onRemoveNonBasic = onRemoveNonBasicLand,
                        modifier         = Modifier.padding(top = 8.dp),
                    )
                }
            }

            // Sideboard
            if (state.sideboard.isNotEmpty()) {
                item {
                    SectionLabel(
                        text     = "Sideboard (${state.sideboard.sumOf { it.quantity }})",
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(state.sideboard, key = { "side_${it.card.scryfallId}" }) { dc ->
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        DeckCardRow(
                            deckCard = dc,
                            onRemove = { onRemoveFromSideboard(dc.card.scryfallId) },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick  = { onMoveToMainboard(dc.card.scryfallId) },
                            modifier = Modifier.width(56.dp),
                        ) {
                            Text("→MB", style = ty.labelSmall, color = mc.primaryAccent)
                        }
                    }
                }
            }
        }

        // Save button
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(mc.background)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick  = onSave,
                enabled  = state.totalCardCount > 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = mc.primaryAccent,
                    disabledContainerColor = mc.surfaceVariant,
                ),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text  = stringResource(R.string.deckbuilder_save_button),
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = if (state.totalCardCount > 0) mc.background else mc.textDisabled,
                )
            }
        }

        // Error
        state.error?.let { msg ->
            Snackbar(
                modifier         = Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp, start = 16.dp, end = 16.dp),
                containerColor   = mc.lifeNegative.copy(alpha = 0.9f),
                contentColor     = mc.textPrimary,
            ) {
                Text(msg, style = MaterialTheme.magicTypography.bodySmall)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Helpers
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text     = text,
        style    = MaterialTheme.magicTypography.labelLarge,
        color    = MaterialTheme.magicColors.textSecondary,
        modifier = modifier,
    )
}

private fun cardTypeLabel(typeLine: String): String = when {
    typeLine.contains("Creature",    ignoreCase = true) -> "Creature"
    typeLine.contains("Planeswalker",ignoreCase = true) -> "Planeswalker"
    typeLine.contains("Instant",     ignoreCase = true) -> "Instant"
    typeLine.contains("Sorcery",     ignoreCase = true) -> "Sorcery"
    typeLine.contains("Enchantment", ignoreCase = true) -> "Enchantment"
    typeLine.contains("Artifact",    ignoreCase = true) -> "Artifact"
    typeLine.contains("Land",        ignoreCase = true) -> "Land"
    else                                                -> "Other"
}

private fun mainColorLabel(colors: List<String>): String = when {
    colors.isEmpty()  -> "Colorless"
    colors.size > 1   -> "Multicolor"
    else              -> when (colors.first().uppercase()) {
        "W" -> "White"
        "U" -> "Blue"
        "B" -> "Black"
        "R" -> "Red"
        "G" -> "Green"
        else -> colors.first()
    }
}

private fun cmcLabel(cmc: Double): String = when (cmc.toInt()) {
    0, 1 -> "0–1"
    2    -> "2"
    3    -> "3"
    4    -> "4"
    else -> "5+"
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Previews
// ═══════════════════════════════════════════════════════════════════════════════

@Preview(showBackground = true, name = "Setup Step")
@Composable
private fun SetupStepPreview() {
    MagicTheme {
        SetupStep(
            state = DeckBuilderState(),
            commanderResults = emptyList(),
            isSearchingCommander = false,
            onCommanderSearch = {},
            onCommanderClear = {},
            onBuild = { _, _, _ -> },
            modifier = Modifier
        )
    }
}


@Preview(showBackground = true, name = "Review Step")
@Composable
private fun ReviewStepPreview() {
    val mockCard = createMockCard()
    val state = DeckBuilderState(
        step = BuilderStep.REVIEW,
        deckName = "Preview Deck",
        format = DeckFormat.STANDARD,
        mainboard = listOf(
            DeckCard(mockCard, quantity = 4),
            DeckCard(createMockCard("2", "Lightning Bolt", "{R}", 1.0, "Instant", "common"), quantity = 4)
        )
    )
    MagicTheme {
        ReviewStep(
            state = state,
            onRemoveFromMainboard = {},
            onRemoveFromSideboard = {},
            onMoveToSideboard = {},
            onMoveToMainboard = {},
            onRemoveNonBasicLand = {},
            onSetGroupBy = {},
            onSave = {},
            modifier = Modifier
        )
    }
}

private fun createMockCard(
    id: String = "1",
    name: String = "Black Lotus",
    manaCost: String? = "{0}",
    cmc: Double = 0.0,
    typeLine: String = "Artifact",
    rarity: String = "mythic"
) = Card(
    scryfallId = id,
    name = name,
    printedName = name,
    manaCost = manaCost,
    cmc = cmc,
    colors = if (manaCost?.contains("R") == true) listOf("R") else emptyList(),
    colorIdentity = if (manaCost?.contains("R") == true) listOf("R") else emptyList(),
    typeLine = typeLine,
    printedTypeLine = typeLine,
    oracleText = "T, Sacrifice Black Lotus: Add three mana of any one color.",
    printedText = "T, Sacrifice Black Lotus: Add three mana of any one color.",
    keywords = emptyList(),
    power = null,
    toughness = null,
    loyalty = null,
    setCode = "lea",
    setName = "Limited Edition Alpha",
    collectorNumber = "232",
    rarity = rarity,
    releasedAt = "1993-08-05",
    frameEffects = emptyList(),
    promoTypes = emptyList(),
    lang = "en",
    imageNormal = null,
    imageArtCrop = null,
    imageBackNormal = null,
    priceUsd = 100.0,
    priceUsdFoil = null,
    priceEur = 80.0,
    priceEurFoil = null,
    legalityStandard = "legal",
    legalityPioneer = "legal",
    legalityModern = "legal",
    legalityCommander = "legal",
    flavorText = null,
    artist = "Artist",
    scryfallUri = ""
)
