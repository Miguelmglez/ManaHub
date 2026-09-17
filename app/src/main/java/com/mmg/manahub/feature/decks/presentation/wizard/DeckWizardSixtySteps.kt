package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-17

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.ui.components.DeckCardQueueItem
import com.mmg.manahub.core.ui.components.DeckCardQueueSheet
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.CopyPolicy
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategy
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.SectionSearchQuery
import com.mmg.manahub.feature.decks.domain.engine.isLegalForFormat
import com.mmg.manahub.feature.decks.domain.usecase.StrategyRecommendation
import com.mmg.manahub.feature.decks.presentation.components.CardDetailSheet
import com.mmg.manahub.feature.decks.presentation.components.SeedSelectionUi
import com.mmg.manahub.feature.decks.presentation.components.TribeOption
import com.mmg.manahub.feature.decks.presentation.components.TribePickerSection

// ═══════════════════════════════════════════════════════════════════════════════
//  Deck Wizard 60-card wave (v6), plan §5 Phase 5.2 -- SEED_PICK ("Start from cards"), the
//  60-card counterpart of DeckWizardCommanderSteps.kt's COMMANDER_PICK: a structural clone of
//  CommanderPickStepContent, reusing WizardCandidateTile (renamed CommanderCandidateTile) and the
//  SAME idle/search/AdvancedSearchSheet pattern, generalized to owned+legal candidates instead of
//  owned+commander-eligible ones and to seed COPIES instead of a single commander pick.
//
//  COLOR_PICK/STRATEGY_PICK's real content lands in run B (plan §5 Phase 5.3) -- the two minimal
//  placeholders at the bottom of this file exist ONLY so DeckWizardScreen.kt's `when (phase)`
//  compiles against the new WizardPhase entries this run introduces.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * The criteria SEED_PICK's own [AdvancedSearchSheet] open locks non-removable: [format]'s legality
 * ([SectionSearchQuery.legalityCriterion], the ONE format→Scryfall legality mapping every wizard
 * search uses now — S3). `null` [format] (unreachable in practice, defensive only) locks nothing.
 */
internal fun seedLockedCriteria(format: DeckFormat?): List<SearchCriterion> =
    format?.let { listOfNotNull(SectionSearchQuery.legalityCriterion(it)) }.orEmpty()

/** Mirrors [commanderActiveFilterCount]'s exact contract for SEED_PICK's own structured query. */
internal fun seedPickActiveFilterCount(structuredQuery: AdvancedSearchQuery?, lockedCriteria: List<SearchCriterion>): Int =
    structuredQuery?.criteria.orEmpty().count { criterion -> lockedCriteria.none { it::class == criterion::class } }

/** Mirrors [commanderPickIsIdle]'s exact contract for SEED_PICK. */
internal fun seedPickIsIdle(uiState: DeckWizardUiState, lockedCriteria: List<SearchCriterion>): Boolean =
    uiState.seedPickQuery.isBlank() && seedPickActiveFilterCount(uiState.seedPickStructuredQuery, lockedCriteria) == 0

/** Mirrors [buildCommanderSearchQuery]'s exact contract for SEED_PICK. */
internal fun buildSeedPickSearchQuery(uiState: DeckWizardUiState, lockedCriteria: List<SearchCriterion>): AdvancedSearchQuery? {
    if (seedPickIsIdle(uiState, lockedCriteria)) return null
    val base = uiState.seedPickStructuredQuery ?: AdvancedSearchQuery(criteria = lockedCriteria)
    val nameText = uiState.seedPickQuery.trim()
    val criteria = base.criteria.filterNot { it is SearchCriterion.Name } +
        if (nameText.isNotEmpty()) listOf(SearchCriterion.Name(nameText)) else emptyList()
    return base.copy(criteria = criteria)
}

/**
 * SEED_PICK's default (no structured search) candidate list -- every collection-owned, format-legal
 * card (S3: "idle state shows owned + legal cards"), one PRINTING per name, name-sorted, filtered by
 * [DeckWizardUiState.seedPickQuery]'s plain name filter. A plain (non-`@Composable`) function so it
 * is covered by a JVM unit test rather than a Compose UI test (mirrors [commanderPickLocalCandidates]).
 */
internal fun seedPickLocalCandidates(uiState: DeckWizardUiState): List<Card> {
    val format = uiState.selectedFormat ?: return emptyList()
    val query = uiState.seedPickQuery.trim()
    return uiState.ownedCards
        .filter { card -> isLegalForFormat(card, format) }
        .distinctBy { it.name }
        .sortedBy { it.name }
        .filter { card -> query.isEmpty() || card.name.contains(query, ignoreCase = true) }
}

/**
 * The "Start from cards" flow's first step (S3): a [LazyVerticalGrid] of every owned, format-legal
 * card ([seedPickLocalCandidates]) while idle ([seedPickIsIdle]), or live Scryfall results
 * ([DeckWizardUiState.seedPickResults]) locked to [seedLockedCriteria] the moment there is search
 * text or an active filter — the SAME idle/search dispatch [CommanderPickStepContent] uses (D14).
 *
 * Tapping a tile opens the shared [CardDetailSheet] in seed-selection mode ([SeedSelectionUi]).
 * A sticky "N cards added" pill (hidden at 0 copies) opens the seed queue sheet
 * ([DeckCardQueueSheet]) for reviewing/adjusting every picked seed at once. "Next" requires at
 * least one seed (S3/S4).
 */
@Composable
internal fun SeedPickStepContent(
    uiState: DeckWizardUiState,
    onQueryChange: (String) -> Unit,
    onApplyStructuredSearch: (AdvancedSearchQuery) -> Unit,
    onClearFilters: () -> Unit,
    onClearSearchAndFilters: () -> Unit,
    onAddSeed: (Card) -> Unit,
    onRemoveSeedCopy: (Card) -> Unit,
    onRemoveSeed: (Card) -> Unit,
    onToggleSeedQueue: () -> Unit,
    onShowSeedDetail: (Card?) -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    var showAdvancedSearch by remember { mutableStateOf(false) }

    val format = uiState.selectedFormat
    val lockedCriteria = remember(format) { seedLockedCriteria(format) }
    val filterCount = remember(uiState.seedPickStructuredQuery, lockedCriteria) {
        seedPickActiveFilterCount(uiState.seedPickStructuredQuery, lockedCriteria)
    }
    val isIdle = remember(uiState.seedPickQuery, uiState.seedPickStructuredQuery, lockedCriteria) {
        seedPickIsIdle(uiState, lockedCriteria)
    }
    val localCandidates = remember(uiState.ownedCards, uiState.seedPickQuery, format) { seedPickLocalCandidates(uiState) }
    val candidatesToShow = if (isIdle) localCandidates else uiState.seedPickResults
    val ownedIds = remember(uiState.ownedCards) { uiState.ownedCards.map { it.scryfallId }.toSet() }
    val seedQuantityByCardId = remember(uiState.seeds) { uiState.seeds.associate { it.card.scryfallId to it.quantity } }

    Column(Modifier.fillMaxWidth()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(stringResource(R.string.deck_wizard_seed_pick_title), style = ty.titleLarge, color = mc.textPrimary)
                    Text(
                        stringResource(R.string.deck_wizard_seed_pick_subtitle),
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }

            item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    val seedSearchPlaceholder = stringResource(R.string.card_queue_search_placeholder)
                    OutlinedTextField(
                        value = uiState.seedPickQuery,
                        onValueChange = onQueryChange,
                        // Gate 5 audit (design P2a): placeholder text alone isn't exposed as a field
                        // name to TalkBack -- give it one directly, mirroring DeckCardQueueSheet.kt.
                        modifier = Modifier.weight(1f).semantics { contentDescription = seedSearchPlaceholder },
                        singleLine = true,
                        placeholder = { Text(seedSearchPlaceholder, style = ty.bodyMedium, color = mc.textDisabled) },
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
                    BadgedBox(
                        badge = {
                            if (filterCount > 0) {
                                Badge(containerColor = mc.primaryAccent, contentColor = mc.background) { Text("$filterCount") }
                            }
                        },
                    ) {
                        IconButton(onClick = { showAdvancedSearch = true }, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = stringResource(R.string.deck_wizard_commander_advanced_search),
                                tint = if (filterCount > 0) mc.primaryAccent else mc.textSecondary,
                            )
                        }
                    }
                }
            }

            if (filterCount > 0) {
                item(key = "active_filters", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.deck_wizard_commander_active_filters, filterCount),
                            style = ty.bodySmall,
                            color = mc.primaryAccent,
                        )
                        MagicCtaButton(
                            onClick = onClearFilters,
                            text = stringResource(R.string.deck_wizard_commander_clear_filters),
                            style = MagicCtaStyle.Ghost,
                            color = MagicCtaColor.Error,
                        )
                    }
                }
            } else if (isIdle) {
                item(key = "idle_header", span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(R.string.deck_wizard_seed_pick_idle_header), style = ty.titleMedium, color = mc.textPrimary)
                }
            }

            when {
                !isIdle && uiState.isSearchingSeedPick -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(vertical = spacing.lg), contentAlignment = Alignment.Center) {
                        MagicLoadingSpinner(size = MagicLoadingSize.Small)
                    }
                }
                !isIdle && uiState.seedPickSearchError -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                    InlineErrorState(
                        message = stringResource(R.string.deck_wizard_commander_search_error),
                        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.lg),
                    )
                }
                candidatesToShow.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = stringResource(R.string.deck_wizard_seed_pick_empty),
                        icon = Icons.Default.Search,
                        actionLabel = if (!isIdle) stringResource(R.string.deck_wizard_commander_clear_filters) else null,
                        onAction = if (!isIdle) onClearSearchAndFilters else null,
                        modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xl),
                    )
                }
                else -> items(candidatesToShow, key = { "seedcand_${it.scryfallId}" }) { card ->
                    WizardCandidateTile(
                        card = card,
                        isOwned = card.scryfallId in ownedIds,
                        onClick = { onShowSeedDetail(card) },
                        seedQuantity = seedQuantityByCardId[card.scryfallId],
                    )
                }
            }
        }

        if (uiState.seedCopies > 0) {
            // Gate 5 audit (design P1): the visible "N cards added" text alone doesn't tell TalkBack
            // that tapping this pill opens the seed queue list.
            val pillDescription = stringResource(R.string.deck_wizard_seeds_added_pill_a11y, uiState.seedCopies)
            Surface(
                onClick = onToggleSeedQueue,
                shape = ChipShape,
                color = mc.primaryAccent.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.xs).heightIn(min = 48.dp)
                    .semantics { contentDescription = pillDescription },
            ) {
                Box(Modifier.fillMaxWidth().padding(spacing.sm), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.deck_wizard_seeds_added_pill, uiState.seedCopies),
                        style = ty.labelLarge,
                        color = mc.primaryAccent,
                    )
                }
            }
        }

        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = uiState.seeds.isNotEmpty(),
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
            appliedQuery = uiState.seedPickStructuredQuery,
            lockedCriteria = lockedCriteria.toSet(),
        )
    }

    if (uiState.showSeedQueue) {
        val currency = LocalPreferredCurrency.current
        DeckCardQueueSheet(
            title = stringResource(R.string.deck_wizard_seed_queue_title, uiState.seedCopies),
            items = uiState.seeds.map { seed ->
                DeckCardQueueItem(
                    id = seed.card.scryfallId,
                    card = seed.card,
                    quantity = seed.quantity,
                    maxQuantity = format?.let { CopyPolicy.maxSeedCopies(seed.card, it) },
                    isInCollection = seed.card.scryfallId in ownedIds,
                )
            },
            preferredCurrency = currency,
            isBusy = false,
            onDismiss = onToggleSeedQueue,
            onIncrement = { item -> onAddSeed(item.card) },
            onDecrement = { item -> onRemoveSeedCopy(item.card) },
            onRemove = { item -> onRemoveSeed(item.card) },
            onImageClick = { item -> onShowSeedDetail(item.card) },
            emptyTitle = stringResource(R.string.deck_wizard_seed_queue_empty_title),
            emptySubtitle = stringResource(R.string.deck_wizard_seed_queue_empty_subtitle),
        )
    }

    uiState.seedDetailCard?.let { card ->
        val existingQuantity = uiState.seeds.firstOrNull { it.card.scryfallId == card.scryfallId }?.quantity ?: 0
        val maxQuantity = format?.let { CopyPolicy.maxSeedCopies(card, it) } ?: 1
        CardDetailSheet(
            deckCard = DeckSlotEntry(
                scryfallId = card.scryfallId,
                quantity = existingQuantity,
                isSideboard = false,
                card = card,
                source = DeckCardSource.USER,
            ),
            displayCard = card,
            isLoadingDetail = false,
            isCommander = false,
            isCommanderSelectionContext = false,
            tags = card.tags + card.userTags,
            onAdd = {},
            onRemove = {},
            onDelete = {},
            onChooseAsCommander = {},
            onRemoveCommander = {},
            onDismiss = { onShowSeedDetail(null) },
            seedSelection = SeedSelectionUi(
                quantity = existingQuantity,
                maxQuantity = maxQuantity,
                isOwned = card.scryfallId in ownedIds,
                ownedQuantity = uiState.ownedQuantityByName[card.name] ?: 0,
                onAdd = { onAddSeed(card) },
                onRemove = { onRemoveSeedCopy(card) },
            ),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  COLOR_PICK / STRATEGY_PICK (Deck Wizard 60-card wave v6, plan §5 Phase 5.3). Both reuse
//  StrategyRecommendationList/StrategyRecommendationRow/InlineColorComboSection from
//  DeckWizardCommanderSteps.kt and TribePickerSection from feature/decks/presentation/components --
//  same package, no wizard-only duplication of the strategy-picking chrome.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * "Start from colors" (S9): a WUBRG chip row + the exclusive 6th "Colorless" chip, then
 * [StrategyRecommendationList] fed by [DeckWizardUiState.strategyRecommendations] (empty until at
 * least one chip is picked -- [DeckWizardViewModel.recomputeStrategyRecommendations] returns early
 * in that case rather than scoring an empty-identity/empty-seeds anchor). Next requires a color AND
 * a strategy pick (custom included).
 */
@Composable
internal fun ColorPickStepContent(
    uiState: DeckWizardUiState,
    onToggleColor: (ManaColor) -> Unit,
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
    val pendingTribeStrategy = uiState.pendingTribeStrategy
    val tribeOptions = remember(uiState.commanderTribePickerCandidates) {
        uiState.commanderTribePickerCandidates.map { TribeOption(key = it.tribeKey, label = it.displayLabel) }
    }

    Column(Modifier.fillMaxSize()) {
        if (pendingTribeStrategy != null) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.lg, vertical = spacing.md),
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
                        Text(stringResource(R.string.deck_wizard_colors_flow_title), style = ty.titleLarge, color = mc.textPrimary)
                        Text(
                            stringResource(R.string.deck_wizard_colors_flow_subtitle),
                            style = ty.bodyMedium,
                            color = mc.textSecondary,
                            modifier = Modifier.padding(top = spacing.xxs),
                        )
                    }
                }
                item(key = "color_picker") {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        ManaColor.entries.filter { it != ManaColor.C }.forEach { color ->
                            ColorToggleChip(
                                color = color,
                                selected = color in uiState.colorIdentity,
                                readOnly = false,
                                onClick = { onToggleColor(color) },
                            )
                        }
                        ColorToggleChip(
                            color = ManaColor.C,
                            selected = ManaColor.C in uiState.colorIdentity,
                            readOnly = false,
                            onClick = { onToggleColor(ManaColor.C) },
                            label = stringResource(R.string.deck_wizard_colorless_chip),
                        )
                    }
                }
                when {
                    uiState.colorIdentity.isEmpty() -> item(key = "pick_hint") {
                        Text(stringResource(R.string.deck_wizard_colors_flow_pick_hint), style = ty.bodySmall, color = mc.textSecondary)
                    }
                    uiState.isLoadingCommanderStrategies -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = spacing.xl), contentAlignment = Alignment.Center) {
                            MagicLoadingSpinner()
                        }
                    }
                    else -> StrategyRecommendationList(
                        recommendations = uiState.strategyRecommendations,
                        selectedId = uiState.selectedCuratedStrategyId,
                        onSelectStrategy = onSelectStrategy,
                        onSelectCustom = onSelectCustom,
                        onRequestTribe = onRequestTribe,
                        contextLabel = "",
                        otherPlansExpanded = otherPlansExpanded,
                        onToggleOtherPlansExpanded = { otherPlansExpanded = !otherPlansExpanded },
                        showCustom = true,
                    )
                }
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = uiState.colorIdentity.isNotEmpty() && (uiState.selectedCuratedStrategyId != null || uiState.isCustomStrategyChosen),
            onClick = onNext,
        )
    }
}

/**
 * "Start from strategy": the WHOLE curated catalog (ranked by owned support alone, computed once on
 * entry via an empty-identity/empty-seeds anchor -- see [DeckWizardViewModel
 * .recomputeStrategyRecommendations]'s own KDoc), filtered by [DeckWizardUiState.strategyPickQuery].
 * No Custom row (S7 -- Custom only makes sense once seeds/a commander exist to infer FROM; a
 * strategy-first pick already names a real catalog entry). Selecting a row expands its
 * [InlineColorComboSection] below it; picking a combo commits the identity and finalizes the pick
 * (or opens the tribe sub-picker first). Next requires a FULLY committed pick (strategy + combo).
 */
@Composable
internal fun StrategyPickStepContent(
    uiState: DeckWizardUiState,
    onQueryChange: (String) -> Unit,
    onSelectEntry: (CuratedStrategy) -> Unit,
    onSelectCombo: (CuratedStrategy, ColorComboSuggestion) -> Unit,
    onRequestTribe: (CuratedStrategy) -> Unit,
    onPickTribe: (String) -> Unit,
    onCancelTribePick: () -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val pendingTribeStrategy = uiState.pendingTribeStrategy
    val tribeOptions = remember(uiState.commanderTribePickerCandidates) {
        uiState.commanderTribePickerCandidates.map { TribeOption(key = it.tribeKey, label = it.displayLabel) }
    }
    val query = uiState.strategyPickQuery.trim()
    val filtered = remember(uiState.strategyRecommendations, query) {
        if (query.isEmpty()) {
            uiState.strategyRecommendations
        } else {
            uiState.strategyRecommendations.filter { it.strategy.displayName.contains(query, ignoreCase = true) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (pendingTribeStrategy != null) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.lg, vertical = spacing.md),
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
                        Text(stringResource(R.string.deck_wizard_strategy_step_title), style = ty.titleLarge, color = mc.textPrimary)
                        Text(
                            stringResource(R.string.deck_wizard_strategy_flow_subtitle),
                            style = ty.bodyMedium,
                            color = mc.textSecondary,
                            modifier = Modifier.padding(top = spacing.xxs),
                        )
                    }
                }
                item(key = "search") {
                    val strategySearchPlaceholder = stringResource(R.string.deck_wizard_strategy_search_hint)
                    OutlinedTextField(
                        value = uiState.strategyPickQuery,
                        onValueChange = onQueryChange,
                        // Gate 5 audit (design P2a): placeholder text alone isn't exposed as a field
                        // name to TalkBack -- give it one directly, mirroring DeckCardQueueSheet.kt.
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = strategySearchPlaceholder },
                        singleLine = true,
                        placeholder = { Text(strategySearchPlaceholder, style = ty.bodyMedium, color = mc.textDisabled) },
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
                }
                when {
                    uiState.isLoadingCommanderStrategies -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = spacing.xl), contentAlignment = Alignment.Center) {
                            MagicLoadingSpinner()
                        }
                    }
                    filtered.isEmpty() -> item(key = "empty") {
                        Text(stringResource(R.string.deck_wizard_strategy_search_empty), style = ty.bodySmall, color = mc.textSecondary)
                    }
                    else -> items(filtered, key = { "stratpick_${it.strategy.id}" }) { recommendation ->
                        val strategy = recommendation.strategy
                        Column {
                            StrategyRecommendationRow(
                                recommendation = recommendation,
                                isSelected = uiState.expandedStrategyPickId == strategy.id || uiState.selectedCuratedStrategyId == strategy.id,
                                onClick = { onSelectEntry(strategy) },
                            )
                            if (uiState.expandedStrategyPickId == strategy.id) {
                                InlineColorComboSection(
                                    suggestions = uiState.strategyPickCombos,
                                    selectedColors = uiState.colorIdentity,
                                    onSelectCombo = { combo -> onSelectCombo(strategy, combo) },
                                )
                            }
                        }
                    }
                }
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = uiState.selectedCuratedStrategyId != null,
            onClick = onNext,
        )
    }
}
