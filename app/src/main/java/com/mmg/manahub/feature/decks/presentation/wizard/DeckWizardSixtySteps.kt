package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-21

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.ui.components.CardRow
import com.mmg.manahub.core.ui.components.DeckCardQueueItem
import com.mmg.manahub.core.ui.components.DeckCardQueueSheet
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicSelectionItem
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ColorCombinationNames
import com.mmg.manahub.feature.decks.domain.engine.CopyPolicy
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategy
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.SectionSearchQuery
import com.mmg.manahub.feature.decks.domain.engine.isLegalForFormat
import com.mmg.manahub.feature.decks.presentation.components.TribeOption
import com.mmg.manahub.feature.decks.presentation.components.TribePickerSection

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
 * The "Start from cards" flow's first step (S3): a [LazyColumn] of [CardRow]s over every owned,
 * format-legal card ([seedPickLocalCandidates]) while idle ([seedPickIsIdle]), or live Scryfall
 * results ([DeckWizardUiState.seedPickResults]) locked to [seedLockedCriteria] the moment there is
 * search text or an active filter — the SAME idle/search dispatch [CommanderPickStepContent] uses.
 *
 * Each row carries the -/+ seed stepper (capped by [CopyPolicy.maxSeedCopies]); tapping the row or
 * its image opens the single-card inspection overlay with "Add to deck"/"Cancel". A sticky "N cards
 * added" pill (hidden at 0 copies) opens the seed queue sheet ([DeckCardQueueSheet]). "Next"
 * requires at least one seed (S3/S4).
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
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    var showAdvancedSearch by remember { mutableStateOf(false) }
    val inspection = rememberWizardCardInspectionState()

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
    fun maxCopiesOf(card: Card): Int = format?.let { CopyPolicy.maxSeedCopies(card, it) } ?: 1

    WizardCardInspectionHost(
        state = inspection,
        actions = { card ->
            val quantity = seedQuantityByCardId[card.scryfallId] ?: 0
            val atCap = quantity >= maxCopiesOf(card)
            MagicCtaButton(
                onClick = {
                    onAddSeed(card)
                    inspection.requestDismiss()
                },
                text = stringResource(R.string.deck_wizard_add_to_deck),
                enabled = !atCap,
                style = MagicCtaStyle.Filled,
                color = MagicCtaColor.Primary,
                icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (atCap) {
                Text(
                    text = stringResource(R.string.deck_wizard_seed_in_deck, quantity),
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            MagicCtaButton(
                onClick = inspection::requestDismiss,
                text = stringResource(R.string.action_cancel),
                style = MagicCtaStyle.Ghost,
                color = MagicCtaColor.Neutral,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                item(key = "header") {
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

                item(key = "search") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    ) {
                        val seedSearchPlaceholder = stringResource(R.string.card_queue_search_placeholder)
                        OutlinedTextField(
                            value = uiState.seedPickQuery,
                            onValueChange = onQueryChange,
                            // Placeholder text alone is not exposed as a field name to TalkBack.
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
                    item(key = "active_filters") {
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
                    item(key = "idle_header") {
                        Text(stringResource(R.string.deck_wizard_seed_pick_idle_header), style = ty.titleMedium, color = mc.textPrimary)
                    }
                }

                when {
                    // Loading wins over error and empty: an empty state mid-search reads as "no results".
                    !isIdle && uiState.isSearchingSeedPick -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = spacing.lg), contentAlignment = Alignment.Center) {
                            MagicLoadingSpinner(size = MagicLoadingSize.Small)
                        }
                    }
                    !isIdle && uiState.seedPickSearchError -> item(key = "error") {
                        InlineErrorState(
                            message = stringResource(R.string.deck_wizard_commander_search_error),
                            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.lg),
                        )
                    }
                    candidatesToShow.isEmpty() -> item(key = "empty") {
                        EmptyState(
                            title = stringResource(R.string.deck_wizard_seed_pick_empty),
                            icon = Icons.Default.Search,
                            actionLabel = if (!isIdle) stringResource(R.string.deck_wizard_commander_clear_filters) else null,
                            onAction = if (!isIdle) onClearSearchAndFilters else null,
                            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xl),
                        )
                    }
                    else -> items(candidatesToShow, key = { "seedcand_${it.scryfallId}" }) { card ->
                        var thumbnailRect by remember { mutableStateOf(Rect.Zero) }
                        val quantity = seedQuantityByCardId[card.scryfallId] ?: 0
                        val maxCopies = remember(card, format) { maxCopiesOf(card) }
                        CardRow(
                            card = card,
                            isInCollection = card.scryfallId in ownedIds,
                            quantity = quantity,
                            onClick = { inspection.open(card, thumbnailRect) },
                            onImageClick = { inspection.open(card, thumbnailRect) },
                            onAdd = { onAddSeed(card) },
                            onRemove = { onRemoveSeedCopy(card) },
                            addEnabled = quantity < maxCopies,
                            modifier = Modifier
                                .animateItem()
                                .onGloballyPositioned { thumbnailRect = inspection.thumbnailRectOf(it, imageIsInteractive = true) },
                        )
                    }
                }
            }

            SeedsAddedPill(seedCopies = uiState.seedCopies, onClick = onToggleSeedQueue)

            WizardStickyButton(
                label = stringResource(R.string.deck_wizard_next),
                enabled = uiState.seeds.isNotEmpty(),
                onClick = onNext,
            )
        }
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
            // The queue is a window-level sheet, so the step's overlay only becomes visible once it closes.
            onImageClick = { item ->
                onToggleSeedQueue()
                inspection.openFromCenter(item.card)
            },
            emptyTitle = stringResource(R.string.deck_wizard_seed_queue_empty_title),
            emptySubtitle = stringResource(R.string.deck_wizard_seed_queue_empty_subtitle),
        )
    }
}

/** The sticky "N cards added" pill above SEED_PICK's Next button -- animates in/out at the 0 boundary
 * and slides its count on every change. */
@Composable
private fun SeedsAddedPill(seedCopies: Int, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    AnimatedVisibility(
        visible = seedCopies > 0,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        // The visible text alone does not tell TalkBack that tapping opens the seed queue list.
        val pillDescription = stringResource(R.string.deck_wizard_seeds_added_pill_a11y, seedCopies)
        Surface(
            onClick = onClick,
            shape = ChipShape,
            color = mc.primaryAccent.copy(alpha = 0.14f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.xs).heightIn(min = 48.dp)
                .semantics { contentDescription = pillDescription },
        ) {
            Box(Modifier.fillMaxWidth().padding(spacing.sm), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = seedCopies,
                    transitionSpec = {
                        val up = targetState >= initialState
                        (slideInVertically(tween(200)) { if (up) it else -it } + fadeIn(tween(200))) togetherWith
                            (slideOutVertically(tween(200)) { if (up) -it else it } + fadeOut(tween(150)))
                    },
                    label = "SeedsAddedCount",
                ) { count ->
                    Text(
                        text = stringResource(R.string.deck_wizard_seeds_added_pill, count),
                        style = ty.labelLarge,
                        color = mc.primaryAccent,
                    )
                }
            }
        }
    }
}

/**
 * "Start from colors" (S9): a WUBRG chip row + the exclusive 6th Colorless chip, then
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
                    // 6 x 48dp chips + 5 gaps fit a 360dp device with zero slack; scroll instead of clipping the last chip.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    ) {
                        ManaColor.entries.forEach { color ->
                            ColorToggleChip(
                                color = color,
                                selected = color in uiState.colorIdentity,
                                readOnly = false,
                                onClick = { onToggleColor(color) },
                            )
                        }
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
                        selectedDisplayLabel = uiState.strategyDisplayLabel,
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
 * strategy-first pick already names a real catalog entry). Selecting a row opens its color-combo
 * [ModalBottomSheet] ([StrategyPickColorSheet]); picking a combo commits the identity and finalizes
 * the pick (or opens the tribe sub-picker first). The committed row shows its combo as mana symbols.
 * Next requires a FULLY committed pick (strategy + combo).
 */
@Composable
internal fun StrategyPickStepContent(
    uiState: DeckWizardUiState,
    onQueryChange: (String) -> Unit,
    onSelectEntry: (CuratedStrategy) -> Unit,
    onSelectCombo: (CuratedStrategy, ColorComboSuggestion) -> Unit,
    onDismissColorSheet: () -> Unit,
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
    val expandedStrategy = uiState.expandedStrategyPickId?.let { id -> uiState.strategyRecommendations.firstOrNull { it.strategy.id == id }?.strategy }

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
                        // Placeholder text alone is not exposed as a field name to TalkBack.
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
                        val isCommitted = uiState.selectedCuratedStrategyId == strategy.id
                        StrategyRecommendationRow(
                            recommendation = recommendation,
                            isSelected = uiState.strategyPickSelectedId == strategy.id,
                            onClick = { onSelectEntry(strategy) },
                            titleOverride = if (isCommitted) uiState.strategyDisplayLabel else null,
                            icon = if (isCommitted) ({ ColorIdentitySymbols(colors = uiState.engineIdentity) }) else null,
                        )
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

    if (uiState.showStrategyPickColorSheet && expandedStrategy != null) {
        StrategyPickColorSheet(
            strategy = expandedStrategy,
            combos = uiState.strategyPickCombos,
            selectedColors = uiState.colorIdentity,
            onSelectCombo = { combo -> onSelectCombo(expandedStrategy, combo) },
            onDismiss = onDismissColorSheet,
        )
    }
}

/** STRATEGY_PICK's color-combo picker: one [MagicSelectionItem] per curated combo, named via
 * [ColorCombinationNames] with its mana symbols as the icon (colorless = the `{C}` symbol). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrategyPickColorSheet(
    strategy: CuratedStrategy,
    combos: List<ColorComboSuggestion>,
    selectedColors: Set<ManaColor>,
    onSelectCombo: (ColorComboSuggestion) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = mc.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(strategy.displayName, style = ty.titleLarge, color = mc.textPrimary)
            Text(
                stringResource(R.string.deck_wizard_color_combos_title),
                style = ty.bodyMedium,
                color = mc.textSecondary,
                modifier = Modifier.padding(bottom = spacing.xs),
            )
            if (combos.isEmpty()) {
                Text(stringResource(R.string.deck_wizard_color_combos_empty), style = ty.bodySmall, color = mc.textSecondary)
            } else {
                combos.forEach { combo ->
                    MagicSelectionItem(
                        title = ColorCombinationNames.nameFor(combo.colors),
                        isSelected = combo.colors == selectedColors,
                        onClick = { onSelectCombo(combo) },
                        icon = { ColorIdentitySymbols(colors = combo.colors) },
                    )
                }
            }
        }
    }
}

/** A color identity as real mana symbols; an empty set renders the `{C}` colorless symbol. */
@Composable
internal fun ColorIdentitySymbols(colors: Set<ManaColor>, symbolSize: Dp = 20.dp) {
    val engineColors = colors.filter { it != ManaColor.C }
    if (engineColors.isEmpty()) {
        ManaSymbolImage(token = ManaColor.C.symbol, size = symbolSize)
    } else {
        ManaCostImages(
            manaCost = engineColors.sortedBy { it.ordinal }.joinToString(separator = "") { "{${it.symbol}}" },
            symbolSize = symbolSize,
            spacing = MaterialTheme.spacing.xxs,
        )
    }
}
