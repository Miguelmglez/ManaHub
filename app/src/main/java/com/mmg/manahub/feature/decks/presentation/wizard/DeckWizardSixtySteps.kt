package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-17

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.mmg.manahub.feature.decks.domain.engine.SectionSearchQuery
import com.mmg.manahub.feature.decks.domain.engine.isLegalForFormat
import com.mmg.manahub.feature.decks.presentation.components.CardDetailSheet
import com.mmg.manahub.feature.decks.presentation.components.SeedSelectionUi

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
                    OutlinedTextField(
                        value = uiState.seedPickQuery,
                        onValueChange = onQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.card_queue_search_placeholder), style = ty.bodyMedium, color = mc.textDisabled) },
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
            Surface(
                onClick = onToggleSeedQueue,
                shape = ChipShape,
                color = mc.primaryAccent.copy(alpha = 0.14f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.xs).heightIn(min = 48.dp),
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
//  COLOR_PICK / STRATEGY_PICK -- minimal placeholders (run B builds the real content, plan §5
//  Phase 5.3). Exist ONLY so DeckWizardScreen.kt's `when (phase)` compiles against the WizardPhase
//  entries this run introduces; Next is intentionally disabled (there is nothing to advance with
//  yet) -- back navigation (already wired in DeckWizardViewModel.onBackPressed) is the only way
//  forward until run B lands.
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ColorPickStepContent(uiState: DeckWizardUiState, onNext: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(spacing.lg)) {
            Text(stringResource(R.string.deck_wizard_entry_colors_title), style = ty.titleLarge, color = mc.textPrimary)
            Text(
                stringResource(R.string.deck_wizard_entry_colors_subtitle),
                style = ty.bodyMedium,
                color = mc.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        WizardStickyButton(label = stringResource(R.string.deck_wizard_next), enabled = false, onClick = onNext)
    }
}

@Composable
internal fun StrategyPickStepContent(uiState: DeckWizardUiState, onNext: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(spacing.lg)) {
            Text(stringResource(R.string.deck_wizard_entry_strategy_title), style = ty.titleLarge, color = mc.textPrimary)
            Text(
                stringResource(R.string.deck_wizard_entry_strategy_subtitle),
                style = ty.bodyMedium,
                color = mc.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }
        WizardStickyButton(label = stringResource(R.string.deck_wizard_next), enabled = false, onClick = onNext)
    }
}
