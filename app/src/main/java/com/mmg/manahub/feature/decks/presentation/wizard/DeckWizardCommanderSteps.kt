package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-09

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.search.StructuredCardSearch
import com.mmg.manahub.core.model.AddCardRow
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardRow
import com.mmg.manahub.core.ui.components.CardSearchSheet
import com.mmg.manahub.core.ui.components.CardTagChip
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicSelectionItem
import com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet
import com.mmg.manahub.core.ui.components.search.shortLabel
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.CardSection
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategy
import com.mmg.manahub.feature.decks.domain.engine.PillarId
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.SectionQueryContext
import com.mmg.manahub.feature.decks.domain.engine.SectionSearchQuery
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
import com.mmg.manahub.feature.decks.domain.usecase.StrategyRecommendation
import com.mmg.manahub.feature.decks.presentation.components.CardDetailSheet
import com.mmg.manahub.feature.decks.presentation.components.CardSectionRow
import com.mmg.manahub.feature.decks.presentation.components.TribeOption
import com.mmg.manahub.feature.decks.presentation.components.TribePickerSection
import com.mmg.manahub.feature.decks.presentation.components.label
import org.jetbrains.compose.resources.painterResource

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard & Engine Rework plan (docs/plans/deck-wizard-rework-plan.md), Workstream 2 --
//  the new Commander-only step sequence: COMMANDER_PICK (2.1) -> STRATEGY (2.2) -> MANUAL_ADDS
//  (2.3, SHARED with a future Casual caller -- Workstream 3).
// ═══════════════════════════════════════════════════════════════════════════════

// ── 2.1 — COMMANDER_PICK (Deck Wizard Commander v3 plan, Phase 3.2) ────────────

/**
 * Deck Wizard Commander v3 plan (Phase 3.1, F10): the COMMANDER_PICK grid's default (no structured
 * search) candidate list -- every collection-eligible commander, uncapped (F10 removed the old
 * 5-candidate cap), filtered by [DeckWizardUiState.commanderQuery]'s plain name filter. Already
 * sorted by [com.mmg.manahub.feature.decks.domain.template.OwnedCommanderCandidate.supportScore]
 * desc then name asc ([com.mmg.manahub.feature.decks.domain.template.CollectionProfileUseCase]).
 * A plain (non-`@Composable`) function so it is covered by a JVM unit test rather than a Compose
 * UI test.
 */
internal fun commanderPickLocalCandidates(uiState: DeckWizardUiState): List<Card> {
    val query = uiState.commanderQuery.trim()
    return uiState.collectionProfile?.commanderCandidates.orEmpty()
        .map { it.card }
        .filter { card -> query.isEmpty() || card.name.contains(query, ignoreCase = true) }
}

/** Deck Wizard Commander v3 plan (Phase 3.2/3.3): COMMANDER_PICK's Collection-tab results once a
 * structured search ([DeckWizardUiState.commanderStructuredQuery]) is active -- a lenient local
 * filter of the already-loaded [DeckWizardUiState.ownedCards] via the SAME [StructuredCardSearch]
 * helper Deck Studio's own two-tab search uses (3.3), so the two never disagree on what a locked
 * `is:commander` criterion means locally. */
internal fun commanderPickCollectionTabResults(uiState: DeckWizardUiState): List<Card> =
    StructuredCardSearch.collectionMatches(uiState.ownedCards, uiState.commanderStructuredQuery, uiState.commanderQuery)

/**
 * Deck Wizard Commander v3 plan (Phase 3.2/3.4, D14): the criteria COMMANDER_PICK's own
 * [AdvancedSearchSheet] open locks non-removable for [format] -- always
 * [SearchCriterion.CommanderEligible], plus a strict `Format(commander, legal)` lock ONLY for
 * [DeckFormat.COMMANDER] (Commander Casual is the permissive format and gets no legality lock, per
 * the campaign's own product rule R2).
 */
internal fun commanderLockedCriteria(format: DeckFormat?): List<SearchCriterion> = buildList {
    add(SearchCriterion.CommanderEligible)
    if (format == DeckFormat.COMMANDER) {
        add(SearchCriterion.Format(listOf("commander"), legal = true))
    }
}

/**
 * The first Commander step: a [LazyVerticalGrid] of every eligible owned commander
 * ([commanderPickLocalCandidates]), a name-filter search bar, and a trailing Tune icon opening the
 * shared [AdvancedSearchSheet] locked to [commanderLockedCriteria] (D14). Once a structured search
 * is applied ([DeckWizardUiState.commanderStructuredQuery]), the grid switches to two tabs --
 * Collection ([commanderPickCollectionTabResults], local, lenient) and All cards
 * ([DeckWizardUiState.commanderSearchResults], live Scryfall) -- with a chips row summarizing the
 * active filters (the locked ones among them are, by construction, never removable: nothing in
 * this step or the sheet can drop them, see [commanderLockedCriteria]'s KDoc). Deck Wizard
 * Commander v3 plan, Phase 3.2 (D7/R2): the color-identity filter row and the "include cards
 * outside your collection" toggle were DELETED from this step -- it is collection-only by default,
 * and an unowned commander is now reachable ONLY through the All-cards tab.
 *
 * Tapping a candidate opens the SHARED [CardDetailSheet] in its commander-selection context.
 * Once [DeckWizardUiState.selectedCommander] is set, this step shows the picked commander's
 * [CardRow] (commander style) + a "Change commander" affordance instead of the grid.
 */
@Composable
internal fun CommanderPickStepContent(
    uiState: DeckWizardUiState,
    onQueryChange: (String) -> Unit,
    onSelectCommander: (Card) -> Unit,
    onClearCommander: () -> Unit,
    onApplyStructuredSearch: (AdvancedSearchQuery) -> Unit,
    onSelectResultTab: (CommanderResultTab) -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    var inspectionCard by remember { mutableStateOf<Card?>(null) }
    var showAdvancedSearch by remember { mutableStateOf(false) }

    val commander = uiState.selectedCommander
    val lockedCriteria = remember(uiState.selectedFormat) { commanderLockedCriteria(uiState.selectedFormat) }
    val hasStructuredSearch = uiState.commanderStructuredQuery != null
    val localCandidates = remember(uiState.collectionProfile, uiState.commanderQuery) { commanderPickLocalCandidates(uiState) }
    val collectionTabResults = remember(uiState.ownedCards, uiState.commanderStructuredQuery, uiState.commanderQuery) {
        commanderPickCollectionTabResults(uiState)
    }
    val candidatesToShow = when {
        !hasStructuredSearch -> localCandidates
        uiState.commanderResultTab == CommanderResultTab.COLLECTION -> collectionTabResults
        else -> uiState.commanderSearchResults
    }
    val isLoadingAllCardsTab = hasStructuredSearch &&
        uiState.commanderResultTab == CommanderResultTab.ALL_CARDS &&
        uiState.isSearchingCommander

    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(stringResource(R.string.deck_wizard_commander_pick_title), style = ty.titleLarge, color = mc.textPrimary)
                    Text(
                        stringResource(R.string.deck_wizard_commander_pick_subtitle),
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }

            if (commander != null) {
                item(key = "commander_banner", span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        CardRow(
                            card = commander,
                            isInCollection = true,
                            onClick = {},
                            onRemove = null,
                            isCommander = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClearCommander),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = mc.primaryAccent, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.deck_wizard_change_commander), style = ty.labelMedium, color = mc.primaryAccent)
                        }
                    }
                }
            } else {
                item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        OutlinedTextField(
                            value = uiState.commanderQuery,
                            onValueChange = onQueryChange,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text(stringResource(R.string.deck_wizard_commander_search_hint), style = ty.bodyMedium, color = mc.textDisabled) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary) },
                            shape = CardShape,
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
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = stringResource(R.string.deck_wizard_commander_advanced_search),
                                tint = if (hasStructuredSearch) mc.goldMtg else mc.textSecondary,
                            )
                        }
                    }
                }

                if (hasStructuredSearch) {
                    val activeCriteria = uiState.commanderStructuredQuery?.criteria.orEmpty()
                    item(key = "active_filters", span = { GridItemSpan(maxLineSpan) }) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            activeCriteria.forEach { criterion ->
                                val isLocked = lockedCriteria.any { it::class == criterion::class }
                                InputChip(
                                    selected = true,
                                    onClick = {},
                                    label = { Text(criterion.shortLabel()) },
                                    leadingIcon = if (isLocked) {
                                        { Icon(Icons.Default.Lock, contentDescription = null, tint = mc.goldMtg, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                )
                            }
                        }
                    }
                    item(key = "tabs", span = { GridItemSpan(maxLineSpan) }) {
                        TabRow(selectedTabIndex = uiState.commanderResultTab.ordinal, containerColor = mc.surface, contentColor = mc.primaryAccent) {
                            Tab(
                                selected = uiState.commanderResultTab == CommanderResultTab.COLLECTION,
                                onClick = { onSelectResultTab(CommanderResultTab.COLLECTION) },
                                text = { Text(stringResource(R.string.deck_wizard_commander_tab_collection)) },
                            )
                            Tab(
                                selected = uiState.commanderResultTab == CommanderResultTab.ALL_CARDS,
                                onClick = { onSelectResultTab(CommanderResultTab.ALL_CARDS) },
                                text = { Text(stringResource(R.string.deck_wizard_commander_tab_all_cards)) },
                            )
                        }
                    }
                } else {
                    item(key = "collection_header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.deck_wizard_commanders_in_collection), style = ty.titleMedium, color = mc.textPrimary)
                    }
                }

                when {
                    isLoadingAllCardsTab -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = spacing.lg), contentAlignment = Alignment.Center) {
                            MagicLoadingSpinner(size = MagicLoadingSize.Small)
                        }
                    }
                    candidatesToShow.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            title = stringResource(R.string.deck_wizard_commander_pick_empty),
                            icon = Icons.Default.Search,
                            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xl),
                        )
                    }
                    else -> items(candidatesToShow, key = { "cmdcand_${it.scryfallId}" }) { card ->
                        CommanderCandidateTile(card = card, onClick = { inspectionCard = card })
                    }
                }
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = uiState.selectedCommander != null,
            onClick = onNext,
        )
    }

    if (showAdvancedSearch) {
        AdvancedSearchSheet(
            onDismiss = { showAdvancedSearch = false },
            onSearch = { query, _ ->
                onApplyStructuredSearch(query)
                showAdvancedSearch = false
            },
            appliedQuery = uiState.commanderStructuredQuery,
            lockedCriteria = lockedCriteria.toSet(),
        )
    }

    inspectionCard?.let { card ->
        CardDetailSheet(
            deckCard = DeckSlotEntry(scryfallId = card.scryfallId, quantity = 0, isSideboard = false, card = card, source = DeckCardSource.USER),
            displayCard = card,
            isLoadingDetail = false,
            isCommander = false,
            isCommanderSelectionContext = true,
            tags = card.tags + card.userTags,
            onAdd = {},
            onRemove = {},
            onDelete = {},
            onChooseAsCommander = { chosen ->
                onSelectCommander(chosen)
                inspectionCard = null
            },
            onRemoveCommander = { inspectionCard = null },
            onDismiss = { inspectionCard = null },
        )
    }
}

/** A simple, tap-to-open card image grid tile for the COMMANDER_PICK candidate grid -- unlike
 * [WizardCardImageTile] (Direction step's seed/commander tiles), this has ONE action only (tap
 * opens the shared [CardDetailSheet]), so it does not need that tile's separate magnifier badge. */
@Composable
private fun CommanderCandidateTile(card: Card, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(63f / 88f)
            .clip(CardShape)
            .background(mc.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = card.imageNormal,
            contentDescription = card.name,
            placeholder = painterResource(Res.drawable.mtg_card_back),
            error = painterResource(Res.drawable.mtg_card_back),
            fallback = painterResource(Res.drawable.mtg_card_back),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** The D-A "include outside your collection" toggle row -- shared visual shape between
 * COMMANDER_PICK (2.1), MANUAL_ADDS (2.3), and (Workstream 3) Flow A's own seed search. Internal
 * (not `private`) so [CardsFlowDirectionContent] in `DeckWizardDirectionIdentity.kt` can reuse it
 * too, per the plan's own "reuse WS2.1's D-A toggle/pattern, don't reinvent it" instruction. */
@Composable
internal fun OutsideCollectionToggleRow(checked: Boolean, onToggle: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(onClick = onToggle, shape = CardShape, color = mc.surface, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(spacing.md).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.deck_wizard_include_outside_collection_title), style = ty.bodyMedium, color = mc.textPrimary)
                Text(stringResource(R.string.deck_wizard_include_outside_collection_subtitle), style = ty.labelSmall, color = mc.textSecondary)
            }
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = mc.onAccent,
                    checkedTrackColor = mc.primaryAccent,
                    uncheckedThumbColor = mc.textDisabled,
                    uncheckedTrackColor = mc.surfaceVariant,
                ),
            )
        }
    }
}

// ── 2.2 — STRATEGY (Deck Wizard Commander v3 plan, Phase 4.2) ──────────────────

/** Sentinel id for the UI-level "Custom" row -- never a [CuratedStrategy.id] (the catalog never
 * mints an id equal to this), so it can share a single-select `selectedId` slot with real catalog
 * ids without ambiguity. */
internal const val CUSTOM_STRATEGY_ID = "__custom__"

/** Deck Wizard Commander v3 plan (Phase 4, D4): the STRATEGY step's own top-N split of
 * [DeckWizardUiState.commanderStrategyRecommendations] -- the first [STRATEGY_RECOMMENDED_COUNT]
 * are "Recommended", the rest "Other plans" (collapsed by default, plan §8's "yes" default). A
 * plain (non-`@Composable`) function so it is unit-testable without a Compose UI test. */
internal fun strategyRecommendedSplit(recommendations: List<StrategyRecommendation>): Pair<List<StrategyRecommendation>, List<StrategyRecommendation>> =
    recommendations.take(STRATEGY_RECOMMENDED_COUNT) to recommendations.drop(STRATEGY_RECOMMENDED_COUNT)

private const val STRATEGY_RECOMMENDED_COUNT = 6

/**
 * The single-select STRATEGY list (D4): [DeckWizardUiState.commanderStrategyRecommendations] split
 * into "Recommended" (top 6) and collapsed "Other plans", plus "Custom" always offered last. The
 * #1 recommendation is preselected by the ViewModel the moment the list loads
 * ([DeckWizardViewModel.recommendCommanderStrategies]) and Next is ALWAYS enabled (plan §4/§8
 * defaults) -- this step never blocks progress the way the old picker's tribe-required gate could.
 *
 * A [CuratedStrategy.requiresTribe] entry the recommender could not resolve a concrete tribe for
 * opens the shared [TribePickerSection] tribe sub-picker (same component Deck Studio's own
 * [CuratedStrategyPickerSheet] uses) instead of applying with no tribe.
 */
@Composable
internal fun StrategyStepContent(
    uiState: DeckWizardUiState,
    onSelectStrategy: (CuratedStrategy) -> Unit,
    onSelectCustom: () -> Unit,
    onRequestTribe: (CuratedStrategy) -> Unit,
    onPickTribe: (String) -> Unit,
    onCancelTribePick: () -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    var otherPlansExpanded by remember { mutableStateOf(false) }
    val (recommended, other) = remember(uiState.commanderStrategyRecommendations) {
        strategyRecommendedSplit(uiState.commanderStrategyRecommendations)
    }
    val commanderName = uiState.selectedCommander?.name.orEmpty()
    val pendingTribeStrategy = uiState.pendingTribeStrategy
    val tribeOptions = remember(uiState.commanderTribePickerCandidates) {
        uiState.commanderTribePickerCandidates.map { TribeOption(key = it.tribeKey, label = it.displayLabel) }
    }

    Column(Modifier.fillMaxSize()) {
        if (pendingTribeStrategy != null) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = spacing.lg, vertical = spacing.md),
            ) {
                TribePickerSection(
                    strategy = pendingTribeStrategy,
                    availableTribes = tribeOptions,
                    onSelectTribe = onPickTribe,
                    onCancel = onCancelTribePick,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                item(key = "header") {
                    Column {
                        Text(
                            stringResource(R.string.deck_wizard_strategy_step_title_for, commanderName),
                            style = ty.titleLarge,
                            color = mc.textPrimary,
                        )
                        Text(
                            stringResource(R.string.deck_wizard_strategy_step_subtitle),
                            style = ty.bodyMedium,
                            color = mc.textSecondary,
                            modifier = Modifier.padding(top = spacing.xxs),
                        )
                    }
                }

                if (uiState.isLoadingCommanderStrategies) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = spacing.xl), contentAlignment = Alignment.Center) {
                            MagicLoadingSpinner()
                        }
                    }
                } else {
                    if (recommended.isNotEmpty()) {
                        item(key = "recommended_header") {
                            StrategySectionHeader(stringResource(R.string.deck_wizard_strategy_recommended_header, commanderName))
                        }
                        items(recommended, key = { "rec_${it.strategy.id}" }) { recommendation ->
                            StrategyRecommendationRow(
                                recommendation = recommendation,
                                isSelected = uiState.selectedCuratedStrategyId == recommendation.strategy.id,
                                onClick = {
                                    if (recommendation.strategy.requiresTribe && recommendation.tribe == null) {
                                        onRequestTribe(recommendation.strategy)
                                    } else {
                                        onSelectStrategy(recommendation.strategy)
                                    }
                                },
                            )
                        }
                    }
                    if (other.isNotEmpty()) {
                        item(key = "other_plans_toggle") {
                            OtherPlansToggle(
                                expanded = otherPlansExpanded,
                                onToggle = { otherPlansExpanded = !otherPlansExpanded },
                            )
                        }
                        if (otherPlansExpanded) {
                            items(other, key = { "other_${it.strategy.id}" }) { recommendation ->
                                StrategyRecommendationRow(
                                    recommendation = recommendation,
                                    isSelected = uiState.selectedCuratedStrategyId == recommendation.strategy.id,
                                    onClick = {
                                        if (recommendation.strategy.requiresTribe && recommendation.tribe == null) {
                                            onRequestTribe(recommendation.strategy)
                                        } else {
                                            onSelectStrategy(recommendation.strategy)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    item(key = "custom_header") {
                        StrategySectionHeader(stringResource(R.string.deck_wizard_strategy_custom_header))
                    }
                    item(key = "custom") {
                        MagicSelectionItem(
                            title = stringResource(R.string.deck_wizard_strategy_custom_title),
                            description = stringResource(R.string.deck_wizard_strategy_custom_description),
                            isSelected = uiState.selectedCuratedStrategyId == null,
                            accentColor = mc.goldMtg,
                            icon = {
                                Box(
                                    modifier = Modifier.size(40.dp).clip(CircleShape).background(mc.goldMtg.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = mc.goldMtg)
                                }
                            },
                            onClick = onSelectCustom,
                        )
                    }
                }
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = true,
            onClick = onNext,
        )
    }
}

@Composable
private fun StrategySectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.magicTypography.labelMedium,
        color = MaterialTheme.magicColors.primaryAccent,
        modifier = Modifier.padding(top = MaterialTheme.spacing.sm, bottom = MaterialTheme.spacing.xxs),
    )
}

@Composable
private fun OtherPlansToggle(expanded: Boolean, onToggle: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = mc.textSecondary,
        )
        Text(
            text = stringResource(if (expanded) R.string.deck_wizard_strategy_other_plans_collapse else R.string.deck_wizard_strategy_other_plans_expand),
            style = ty.labelLarge,
            color = mc.textSecondary,
        )
    }
}

/**
 * One STRATEGY row (D4): mirrors [MagicSelectionItem]'s Surface/border/selected treatment (that
 * shared component has no slot for a reason-chip row, so this is a sibling built on the same
 * tokens/shape rather than a literal clone of its markup -- see the Phase 4 report for this
 * judgment call) plus up to 2 [RecommendationReason] chips via [CardTagChip] (never a raw
 * `Surface`/`InputChip`, per CLAUDE.md's tag-chip rule).
 */
@Composable
private fun StrategyRecommendationRow(recommendation: StrategyRecommendation, isSelected: Boolean, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val accent = mc.primaryAccent

    Surface(
        onClick = onClick,
        shape = CardShape,
        color = mc.backgroundSecondary,
        border = BorderStroke(width = if (isSelected) 1.5.dp else 1.dp, color = if (isSelected) accent else mc.surfaceVariant),
        modifier = Modifier.fillMaxWidth().semantics { selected = isSelected },
    ) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    recommendation.strategy.displayName,
                    style = ty.titleMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold),
                    color = if (isSelected) accent else mc.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                }
            }
            Text(recommendation.strategy.description, style = ty.bodySmall, color = mc.textSecondary)
            if (recommendation.reasons.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    recommendation.reasons.forEach { reason ->
                        CardTagChip(label = reason.label, category = TagCategory.STRATEGY)
                    }
                }
            }
        }
    }
}

// ── PLAN_SECTIONS (Deck Wizard Commander v3 plan, Phase 5, R4) — Commander-only replacement for
//    MANUAL_ADDS on the SAME WizardPhase.MANUAL_ADDS phase; Casual keeps ManualAddsStepContent
//    below untouched. ────────────────────────────────────────────────────────────────────────────

/** Pillar display order for the PLAN_SECTIONS step (plan 5.2) -- LEGALITY is never shown here (it
 * carries no section a manual add can meaningfully target: `legal`/`illegal` have no Browse action
 * per [SectionSearchQuery.fragmentFor]'s own "no sensible add-more action" rule). */
private val PLAN_SECTIONS_PILLAR_ORDER = listOf(PillarId.PLAN_ROLES, PillarId.MANA_BASE, PillarId.CURVE, PillarId.SYNERGY)

/** The SYNERGY residual buckets (Deck Analysis Engine v3, Phase 4, spec §7) -- the manual adds the
 * engine could not attribute to the plan. Relabeled "Kept — outside the plan" here (R5/D7: the
 * wizard never auto-drops a manual add for being off-plan) rather than their normal
 * [CardSection.label]. */
private val KEPT_OUTSIDE_PLAN_IDS = setOf("interaction", "standalone", "offplan")

/** Same [SnapshotStateMap] + flattened-`List<String>` [Saver] shape as `DeckStudioScreen.kt`'s
 * `CollapsedCategorySectionsSaver` -- duplicated locally (not promoted to a shared file) per this
 * codebase's own "small per-file duplicate over cross-file coupling for one extra call site"
 * convention (see [FormatCard]'s KDoc for the same judgment call made elsewhere in this file). */
private const val PLAN_SECTIONS_SAVER_DELIMITER = "|||"

private val CollapsedPlanSectionsSaver: Saver<SnapshotStateMap<String, Boolean>, List<String>> = Saver(
    save = { map -> map.map { (key, value) -> "$key$PLAN_SECTIONS_SAVER_DELIMITER$value" } },
    restore = { encoded ->
        mutableStateMapOf<String, Boolean>().apply {
            encoded.forEach { entry ->
                val separatorIndex = entry.lastIndexOf(PLAN_SECTIONS_SAVER_DELIMITER)
                if (separatorIndex >= 0) {
                    val keyPart = entry.substring(0, separatorIndex)
                    val valuePart = entry.substring(separatorIndex + PLAN_SECTIONS_SAVER_DELIMITER.length)
                    this[keyPart] = valuePart.toBoolean()
                }
            }
        }
    },
)

/**
 * Deck Wizard Commander v3 plan, Phase 5 (R4) — replaces [ManualAddsStepContent] for Commander
 * formats only. Renders [DeckWizardUiState.planAnalysis]'s pillars/sections via the SAME
 * [CardSectionRow] the Deck Studio Analysis tab uses -- card→section attribution comes ONLY from
 * [com.mmg.manahub.feature.decks.domain.usecase.DeckAnalysisPipeline.analyze] (wired by
 * [DeckWizardViewModel.recomputePlanAnalysis]); this composable has ZERO scoring/classification/
 * query logic of its own (the campaign's own D2 rule -- see `docs/deck-wizard-state.md` §2).
 *
 * Fully skippable, same as the old MANUAL_ADDS step -- Next is always enabled, even while
 * [DeckWizardUiState.planAnalysis] is still loading or degraded to `null`.
 */
@Composable
internal fun PlanSectionsStepContent(
    uiState: DeckWizardUiState,
    onQueryChange: (String) -> Unit,
    onApplyStructuredSearch: (AdvancedSearchQuery) -> Unit,
    onFilterByTags: (Set<String>) -> Unit,
    onScryfallSearch: (String) -> Unit,
    onAddCard: (Card) -> Unit,
    onRemoveCard: (Card) -> Unit,
    onClearSearchState: () -> Unit,
    onCardClick: (String) -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val expandedSections = rememberSaveable(saver = CollapsedPlanSectionsSaver) { mutableStateMapOf() }
    var browseSectionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSearchSheet by rememberSaveable { mutableStateOf(false) }
    // Same RESUMED gate as Deck Studio's own add-cards sheet (feedback_modal_sheet_blocks_nav_
    // transition): a card tap inside CardSearchSheet forwards to the full CardDetailScreen via
    // [onCardClick], and the sheet must unmount the instant that navigation starts.
    val isDestinationResumed = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.RESUMED)

    val format = uiState.selectedFormat ?: DeckFormat.COMMANDER
    val dominantTribe = uiState.selectedTribeKey?.removePrefix(TribeDeriver.TRIBE_PREFIX)
    val queryContext = remember(uiState.colorIdentity, format, dominantTribe) {
        SectionQueryContext(colorIdentity = uiState.colorIdentity, format = format, dominantTribe = dominantTribe)
    }
    val browseQuery = remember(browseSectionId, queryContext) {
        browseSectionId?.let { SectionSearchQuery.toAdvancedQuery(it, queryContext) }
    }
    val browseTagKeys = remember(browseSectionId) {
        browseSectionId?.let { SectionSearchQuery.collectionTagKeysFor(it) }.orEmpty()
    }

    fun resolveCard(scryfallId: String): Card? =
        uiState.selectedCommander?.takeIf { it.scryfallId == scryfallId }
            ?: uiState.seedCards.firstOrNull { it.scryfallId == scryfallId }

    val analysis = uiState.planAnalysis
    val pillars = remember(analysis) {
        PLAN_SECTIONS_PILLAR_ORDER.mapNotNull { id -> analysis?.pillars?.firstOrNull { it.id == id } }
    }

    Column(Modifier.fillMaxSize()) {
        when {
            analysis == null && uiState.isAnalyzingPlan -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                MagicLoadingSpinner()
            }
            analysis == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                InlineErrorState(message = stringResource(R.string.deck_wizard_plan_sections_error))
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                item(key = "header") {
                    Column {
                        Text(stringResource(R.string.deck_wizard_plan_sections_title), style = ty.titleLarge, color = mc.textPrimary)
                        Text(
                            stringResource(R.string.deck_wizard_plan_sections_subtitle),
                            style = ty.bodyMedium,
                            color = mc.textSecondary,
                            modifier = Modifier.padding(top = spacing.xxs),
                        )
                    }
                }
                pillars.forEach { pillar ->
                    item(key = "pillar_${pillar.id.name}") {
                        Text(
                            text = pillar.id.label().uppercase(),
                            style = ty.labelMedium,
                            color = mc.primaryAccent,
                            modifier = Modifier.padding(top = spacing.sm, bottom = spacing.xxs),
                        )
                    }
                    items(pillar.sections, key = { "${pillar.id.name}:${it.id}" }) { section ->
                        val sectionKey = "${pillar.id.name}:${section.id}"
                        // Defaults to EXPANDED (matches DeckStudioScreen's Analysis tab convention,
                        // where collapsedCategorySections gates on `!= true`) -- this step's whole
                        // purpose is to surface each category's Browse action, so hiding everything
                        // behind a tap on first load would bury the primary action of the screen.
                        val expanded = expandedSections[sectionKey] ?: true
                        val renderedSection = if (section.id in KEPT_OUTSIDE_PLAN_IDS) {
                            section.copy(label = stringResource(R.string.deck_wizard_plan_sections_kept_outside_plan))
                        } else {
                            section
                        }
                        val browseFragment = SectionSearchQuery.fragmentFor(section.id, queryContext)
                        CardSectionRow(
                            section = renderedSection,
                            resolveCard = ::resolveCard,
                            onCardClick = onCardClick,
                            onBrowse = if (browseFragment == null) null else {
                                {
                                    browseSectionId = section.id
                                    showSearchSheet = true
                                }
                            },
                            expanded = expanded,
                            onToggleExpanded = { expandedSections[sectionKey] = !expanded },
                            ownedAvailabilityHint = uiState.ownedAvailabilityBySection[section.id],
                        )
                    }
                }
                if (uiState.seedCards.isNotEmpty()) {
                    item(key = "manual_adds_header") {
                        // Same labelMedium/primaryAccent/uppercase treatment as the pillar headers
                        // above (see StrategySectionHeader for the shared convention this file uses
                        // for every such sub-header) -- this is one more section in the same list,
                        // not a bigger heading, so it must not outrank the pillar headers it sits
                        // below.
                        Text(
                            text = stringResource(R.string.deck_wizard_plan_sections_added_title).uppercase(),
                            style = ty.labelMedium,
                            color = mc.primaryAccent,
                            modifier = Modifier.padding(top = spacing.sm, bottom = spacing.xxs),
                        )
                    }
                    items(uiState.seedCards, key = { "planadded_${it.scryfallId}" }) { card ->
                        // Tappable, unlike the dead onClick={} elsewhere in this file's legacy
                        // ManualAddsStepContent -- this step already threads onCardClick through for
                        // the search sheet, so wire the same navigation here instead of rendering a
                        // clickable Surface (CardRow uses Surface(onClick=...), real ripple) that does
                        // nothing on tap.
                        CardRow(
                            card = card,
                            isInCollection = true,
                            onClick = { onCardClick(card.scryfallId) },
                            onRemove = { onRemoveCard(card) },
                        )
                    }
                }
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = true,
            onClick = onNext,
        )
    }

    if (showSearchSheet && isDestinationResumed) {
        val existingIds = remember(uiState.selectedCommander, uiState.seedCards) {
            (listOfNotNull(uiState.selectedCommander?.scryfallId) + uiState.seedCards.map { it.scryfallId }).toSet()
        }
        val ownedIds = remember(uiState.ownedCards) { uiState.ownedCards.map { it.scryfallId }.toSet() }
        fun toRow(card: Card) = AddCardRow(
            card = card,
            quantityInDeck = if (card.scryfallId in existingIds) 1 else 0,
            isOwned = card.scryfallId in ownedIds,
        )
        CardSearchSheet(
            query = uiState.planSectionsQuery,
            offerResults = emptyList(),
            addCardsResults = uiState.planSectionsCollectionResults.map(::toRow),
            scryfallResults = uiState.planSectionsScryfallResults.map(::toRow),
            isSearchingCards = false,
            isSearchingScryfall = uiState.isSearchingPlanSectionsScryfall,
            offerTabLabel = stringResource(R.string.stats_tab_collection),
            allCardsTabLabel = stringResource(R.string.deckdetail_tab_scryfall),
            onQueryChange = onQueryChange,
            onScryfallSearch = onScryfallSearch,
            onAdd = { row -> onAddCard(row.card) },
            onRemove = { row -> onRemoveCard(row.card) },
            onCardClick = onCardClick,
            initialAdvancedQuery = browseQuery,
            appliedAdvancedQuery = uiState.planSectionsStructuredQuery,
            initialCollectionTagKeys = browseTagKeys,
            onAdvancedSearch = onApplyStructuredSearch,
            onFilterCollectionByTags = onFilterByTags,
            onDismiss = {
                showSearchSheet = false
                browseSectionId = null
                onClearSearchState()
            },
        )
    }
}

// ── 2.3 — MANUAL_ADDS (SHARED, WS3 mounts this same composable for the Casual flows) ─────────

private data class ManualAddsRoleSection(val key: RoleKey, val label: String, val filled: Int, val target: Int)

/** Groups [seedCards] by their best-matching role (per [ArchetypeRoleClassifier.classify], among
 * only the keys [skeleton] actually assigns a non-zero ideal band to) and returns one section per
 * such role, target-descending -- "seeds-only counts" per plan 2.3 (a card whose best role isn't in
 * [skeleton] at all contributes to no section, but is still a real seed; this is a display grouping,
 * never an enforcement gate). */
private fun buildRoleSections(skeleton: ResolvedArchetypeSkeleton, seedCards: List<Card>): List<ManualAddsRoleSection> {
    val wantedKeys = skeleton.roleTargets.filterValues { it.ideal > 0 }.keys
    val filledByRole = mutableMapOf<RoleKey, Int>()
    seedCards.forEach { card ->
        val scores = ArchetypeRoleClassifier.classify(card).filterKeys { it in wantedKeys }
        val bestKey = scores.maxByOrNull { it.value }?.key ?: return@forEach
        filledByRole[bestKey] = (filledByRole[bestKey] ?: 0) + 1
    }
    return skeleton.roleTargets.entries
        .filter { it.value.ideal > 0 }
        .sortedByDescending { it.value.ideal }
        .map { (key, target) -> ManualAddsRoleSection(key, ArchetypeRoleClassifier.label(key), filledByRole[key] ?: 0, target.ideal) }
}

/**
 * Skeleton-guided, fully skippable card search + add step (plan 2.3). Built SHARED for reuse: every
 * parameter this composable reads off [uiState] is format-agnostic (skeleton/seeds/color identity/
 * search state), so a future Casual caller (Workstream 3) can mount it unchanged once its own VM
 * paths populate the same [DeckWizardUiState] fields.
 *
 * Hard-filters candidates by [DeckWizardUiState.colorIdentity] subset (`cardIdentity ⊆
 * commanderIdentity`, colorless always included per the empty-list-vacuous-`all{}` rule) --
 * Workstream 9 note: this reads whatever is CURRENTLY in [DeckWizardUiState.colorIdentity] at call
 * time; if a future workstream makes color identity multi-sourced/derived differently, this filter
 * point is the one to revisit.
 */
@Composable
internal fun ManualAddsStepContent(
    uiState: DeckWizardUiState,
    onQueryChange: (String) -> Unit,
    onToggleIncludeOutsideCollection: () -> Unit,
    onSelectRoleFilter: (String?) -> Unit,
    onAddSeed: (Card) -> Unit,
    onRemoveSeed: (Card) -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val skeleton = uiState.manualAddsSkeleton

    val roleSections = remember(skeleton, uiState.seedCards) {
        skeleton?.let { buildRoleSections(it, uiState.seedCards) }.orEmpty()
    }

    val identitySymbols = remember(uiState.colorIdentity) { uiState.colorIdentity.map { it.symbol }.toSet() }
    val query = uiState.manualAddsQuery.trim()
    val seededIds = remember(uiState.seedCards) { uiState.seedCards.map { it.scryfallId }.toSet() }

    val localCandidates = remember(uiState.ownedCards, seededIds, identitySymbols, query, uiState.manualAddsRoleFilter) {
        uiState.ownedCards
            .filterNot { it.scryfallId in seededIds }
            .filter { card -> card.colorIdentity.all { it in identitySymbols } }
            .filter { card -> query.isEmpty() || card.name.contains(query, ignoreCase = true) }
            .filter { card ->
                val roleFilter = uiState.manualAddsRoleFilter ?: return@filter true
                (ArchetypeRoleClassifier.classify(card)[roleFilter] ?: 0f) > 0f
            }
            .take(MANUAL_ADDS_RESULT_CAP)
    }
    val showOutsideResults = uiState.includeOutsideCollection && query.length >= 2
    val candidatesToShow = if (showOutsideResults) uiState.manualAddsSearchResults else localCandidates

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item(key = "header") {
                Column {
                    Text(stringResource(R.string.deck_wizard_manual_adds_title), style = ty.titleLarge, color = mc.textPrimary)
                    Text(
                        stringResource(R.string.deck_wizard_manual_adds_subtitle),
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }

            if (roleSections.isNotEmpty()) {
                item(key = "role_sections") {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        roleSections.forEach { section ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(section.label, style = ty.bodyMedium, color = mc.textSecondary)
                                Text(
                                    stringResource(R.string.deck_wizard_manual_adds_role_progress, section.filled, section.target),
                                    style = ty.labelMedium,
                                    color = mc.textPrimary,
                                )
                            }
                        }
                    }
                }
                item(key = "role_filters") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        roleSections.forEach { section ->
                            DirectionChip(
                                label = section.label,
                                selected = uiState.manualAddsRoleFilter == section.key,
                                onClick = { onSelectRoleFilter(section.key) },
                            )
                        }
                    }
                }
            }

            item(key = "outside_toggle") {
                OutsideCollectionToggleRow(checked = uiState.includeOutsideCollection, onToggle = onToggleIncludeOutsideCollection)
            }
            item(key = "search") {
                OutlinedTextField(
                    value = uiState.manualAddsQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.deck_wizard_manual_adds_search_hint), style = ty.bodyMedium, color = mc.textDisabled) },
                    leadingIcon = {
                        if (uiState.isSearchingManualAdds) {
                            MagicLoadingSpinner(size = MagicLoadingSize.XSmall)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary)
                        }
                    },
                    shape = CardShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                        focusedTextColor = mc.textPrimary,
                        unfocusedTextColor = mc.textPrimary,
                        cursorColor = mc.primaryAccent,
                    ),
                )
            }

            if (candidatesToShow.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.deck_wizard_manual_adds_empty), style = ty.bodySmall, color = mc.textSecondary)
                }
            } else {
                items(candidatesToShow, key = { "manualcand_${it.scryfallId}" }) { card ->
                    CardRow(card = card, isInCollection = !showOutsideResults, onClick = { onAddSeed(card) }, onRemove = null)
                }
            }

            if (uiState.seedCards.isNotEmpty()) {
                item(key = "added_header") {
                    Text(
                        stringResource(R.string.deck_wizard_manual_adds_added_title),
                        style = ty.labelLarge,
                        color = mc.primaryAccent,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                }
                items(uiState.seedCards, key = { "manualadded_${it.scryfallId}" }) { card ->
                    CardRow(card = card, isInCollection = true, onClick = {}, onRemove = { onRemoveSeed(card) })
                }
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = true,
            onClick = onNext,
        )
    }
}

private const val MANUAL_ADDS_RESULT_CAP = 40
