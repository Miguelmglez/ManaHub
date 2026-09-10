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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.AddCardRow
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CardRow
import com.mmg.manahub.core.ui.components.CardSearchSheet
import com.mmg.manahub.core.ui.components.CardTagChip
import com.mmg.manahub.core.ui.components.CardTagGroup
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicSelectionItem
import com.mmg.manahub.core.ui.components.search.AdvancedSearchSheet
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
import com.mmg.manahub.feature.decks.domain.usecase.RecommendCommanderStrategiesUseCase
import com.mmg.manahub.feature.decks.domain.usecase.StrategyRecommendation
import com.mmg.manahub.feature.decks.presentation.components.CardDetailSheet
import com.mmg.manahub.feature.decks.presentation.components.CardFlipPortrait
import com.mmg.manahub.feature.decks.presentation.components.CardSectionRow
import com.mmg.manahub.feature.decks.presentation.components.PillarTile
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

/** Deck Wizard Commander v4 plan (W2.1/W2.2, G2/R2): the number of criteria the user has added on
 * top of [lockedCriteria] -- the shared definition of "active filter" for BOTH the idle/search
 * branch ([commanderPickIsIdle]) and the filter-count badge/row (W2.2), so the two never disagree
 * on what counts as a real filter. A [structuredQuery] whose criteria are ALL locked (e.g. the
 * sheet was opened and Search tapped with nothing else picked) counts as zero active filters. */
internal fun commanderActiveFilterCount(structuredQuery: AdvancedSearchQuery?, lockedCriteria: List<SearchCriterion>): Int =
    structuredQuery?.criteria.orEmpty().count { criterion -> lockedCriteria.none { it::class == criterion::class } }

/** Deck Wizard Commander v4 plan (W2.1, G2/R2): true while COMMANDER_PICK shows the local
 * owned-eligible grid instead of live Scryfall results -- no query text AND no user-added filter
 * beyond [lockedCriteria] (per [commanderActiveFilterCount]). Any non-blank query text, or any
 * active filter, ends idle. */
internal fun commanderPickIsIdle(uiState: DeckWizardUiState, lockedCriteria: List<SearchCriterion>): Boolean =
    uiState.commanderQuery.isBlank() && commanderActiveFilterCount(uiState.commanderStructuredQuery, lockedCriteria) == 0

/** Deck Wizard Commander v4 plan (W2.1, G2/R2): the ONE query COMMANDER_PICK sends to Scryfall once
 * it is not idle ([commanderPickIsIdle]) -- combines [lockedCriteria] (always), any user-added
 * structured criteria already folded into [DeckWizardUiState.commanderStructuredQuery] (the sheet
 * force-merges the locks in, D14), and the plain search text as a [SearchCriterion.Name]. Returns
 * `null` while idle -- callers must check that first rather than relying on an empty criteria list,
 * since [lockedCriteria] alone is never empty. */
internal fun commanderOwnedIds(ownedCards: List<Card>): Set<String> = ownedCards.map { it.scryfallId }.toSet()

internal fun buildCommanderSearchQuery(uiState: DeckWizardUiState, lockedCriteria: List<SearchCriterion>): AdvancedSearchQuery? {
    if (commanderPickIsIdle(uiState, lockedCriteria)) return null
    val base = uiState.commanderStructuredQuery ?: AdvancedSearchQuery(criteria = lockedCriteria)
    val nameText = uiState.commanderQuery.trim()
    val criteria = base.criteria.filterNot { it is SearchCriterion.Name } +
        if (nameText.isNotEmpty()) listOf(SearchCriterion.Name(nameText)) else emptyList()
    return base.copy(criteria = criteria)
}

/**
 * The first Commander step: a [LazyVerticalGrid] of every eligible owned commander
 * ([commanderPickLocalCandidates]) while idle ([commanderPickIsIdle]), or live Scryfall results
 * ([DeckWizardUiState.commanderSearchResults]) the moment there is search text or an active filter
 * -- no more Collection/All-cards tabs (Deck Wizard Commander v4 plan, W2.1, G2/R2). The trailing
 * Tune icon opens the shared [AdvancedSearchSheet] locked to [commanderLockedCriteria] (D14); the
 * active-filter count row + "clear filters" mirror `CollectionScreen.kt`'s own pattern (W2.2).
 *
 * Tapping a candidate opens the SHARED [CardDetailSheet] in its commander-selection context.
 * Once [DeckWizardUiState.selectedCommander] is set, this step shows the picked commander's large
 * flip portrait + tags + a "Change commander" CTA (W2.3, G3) instead of the grid -- "Next" is owned
 * solely by the sticky [WizardStickyButton] below, same as every sibling step (Deck Wizard Commander
 * v4 plan, Run 2 W2 review, P0: a duplicate inline "Next" here used to render alongside it).
 */
@Composable
internal fun CommanderPickStepContent(
    uiState: DeckWizardUiState,
    onQueryChange: (String) -> Unit,
    onSelectCommander: (Card) -> Unit,
    onClearCommander: () -> Unit,
    onApplyStructuredSearch: (AdvancedSearchQuery) -> Unit,
    onClearFilters: () -> Unit,
    onClearSearchAndFilters: () -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    var inspectionCard by remember { mutableStateOf<Card?>(null) }
    var showAdvancedSearch by remember { mutableStateOf(false) }

    val commander = uiState.selectedCommander
    val lockedCriteria = remember(uiState.selectedFormat) { commanderLockedCriteria(uiState.selectedFormat) }
    val filterCount = remember(uiState.commanderStructuredQuery, lockedCriteria) {
        commanderActiveFilterCount(uiState.commanderStructuredQuery, lockedCriteria)
    }
    val isIdle = remember(uiState.commanderQuery, uiState.commanderStructuredQuery, lockedCriteria) {
        commanderPickIsIdle(uiState, lockedCriteria)
    }
    val localCandidates = remember(uiState.collectionProfile, uiState.commanderQuery) { commanderPickLocalCandidates(uiState) }
    val candidatesToShow = if (isIdle) localCandidates else uiState.commanderSearchResults
    val ownedIds = remember(uiState.ownedCards) { commanderOwnedIds(uiState.ownedCards) }

    Column(Modifier.fillMaxSize()) {
        if (commander != null) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = spacing.lg, vertical = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(stringResource(R.string.deck_wizard_commander_pick_title), style = ty.titleLarge, color = mc.textPrimary)
                CardFlipPortrait(card = commander)
                val commanderTags = remember(commander) { commander.tags + commander.userTags }
                if (commanderTags.isNotEmpty()) {
                    CardTagGroup(tags = commanderTags, tagLabel = { it.label() })
                }
                MagicCtaButton(
                    onClick = onClearCommander,
                    text = stringResource(R.string.deck_wizard_change_commander),
                    style = MagicCtaStyle.Outlined,
                    color = MagicCtaColor.Neutral,
                    icon = { Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
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
                        BadgedBox(
                            badge = {
                                if (filterCount > 0) {
                                    Badge(containerColor = mc.primaryAccent, contentColor = mc.background) { Text("$filterCount") }
                                }
                            },
                        ) {
                            IconButton(
                                onClick = { showAdvancedSearch = true },
                                modifier = Modifier.size(48.dp),
                            ) {
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
                    item(key = "collection_header", span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.deck_wizard_commanders_in_collection), style = ty.titleMedium, color = mc.textPrimary)
                    }
                }

                when {
                    // Never render an empty state while a search is in flight -- that reads as
                    // "no results", which may be a lie (campaign G7). Error takes priority over
                    // empty (P1.2) but loading still wins over both.
                    !isIdle && uiState.isSearchingCommander -> item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = spacing.lg), contentAlignment = Alignment.Center) {
                            MagicLoadingSpinner(size = MagicLoadingSize.Small)
                        }
                    }
                    !isIdle && uiState.commanderSearchError -> item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                        InlineErrorState(
                            message = stringResource(R.string.deck_wizard_commander_search_error),
                            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.lg),
                        )
                    }
                    candidatesToShow.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(
                            title = stringResource(if (isIdle) R.string.deck_wizard_commander_pick_idle_empty else R.string.deck_wizard_commander_pick_empty),
                            icon = Icons.Default.Search,
                            actionLabel = if (!isIdle) stringResource(R.string.deck_wizard_commander_clear_filters) else null,
                            onAction = if (!isIdle) onClearSearchAndFilters else null,
                            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xl),
                        )
                    }
                    else -> items(candidatesToShow, key = { "cmdcand_${it.scryfallId}" }) { card ->
                        CommanderCandidateTile(card = card, isOwned = card.scryfallId in ownedIds, onClick = { inspectionCard = card })
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
 * opens the shared [CardDetailSheet]), so it does not need that tile's separate magnifier badge.
 * [isOwned] renders the same [Icons.Rounded.CollectionsBookmark] badge language [CardRow] uses for
 * an owned card (Deck Wizard Commander v4 plan, W2.1, G2) -- Scryfall results otherwise carry no
 * visual signal of what the user already has. */
@Composable
private fun CommanderCandidateTile(card: Card, isOwned: Boolean, onClick: () -> Unit) {
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
        if (isOwned) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                color = mc.background.copy(alpha = 0.85f),
                shape = CircleShape,
            ) {
                Icon(
                    Icons.Rounded.CollectionsBookmark,
                    contentDescription = stringResource(R.string.deck_wizard_commander_owned_badge),
                    tint = mc.primaryAccent,
                    modifier = Modifier.padding(4.dp).size(14.dp),
                )
            }
        }
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

/** Deck Wizard Commander v4 plan (W3, E3): the STRATEGY step's own split of
 * [DeckWizardUiState.commanderStrategyRecommendations] into "Recommended" (score-threshold + hard
 * cap of 5) and collapsed "Partial fit" (the rest of the format's catalog, collapsed by default) --
 * delegates entirely to [RecommendCommanderStrategiesUseCase.splitRecommended], the use case's own
 * threshold/cap logic (never a UI-local `take(N)`). A plain (non-`@Composable`) function so it stays
 * unit-testable without a Compose UI test. */
internal fun strategyRecommendedSplit(recommendations: List<StrategyRecommendation>): Pair<List<StrategyRecommendation>, List<StrategyRecommendation>> =
    RecommendCommanderStrategiesUseCase().splitRecommended(recommendations)

/**
 * The single-select STRATEGY list (D4): [DeckWizardUiState.commanderStrategyRecommendations] split
 * into "Recommended" (score-threshold + hard cap of 5, E3) and collapsed "Partial fit", plus
 * "Custom" always offered last. The #1 recommendation is preselected by the ViewModel the moment
 * the list loads ([DeckWizardViewModel.recommendCommanderStrategies]) and Next is ALWAYS enabled
 * (plan §4/§8 defaults) -- this step never blocks progress the way the old picker's tribe-required
 * gate could.
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
                    // compose-design-reviewer P2 findings: the header now renders unconditionally
                    // (previously it was skipped along with the whole branch when Recommended was
                    // empty, so the "why empty" text dropped in with none of the section chrome
                    // every other block in this list uses); the empty-explanation text no longer
                    // requires `other` to be non-empty either (a theoretical fully-empty catalog
                    // would otherwise explain nothing).
                    item(key = "recommended_header") {
                        StrategySectionHeader(stringResource(R.string.deck_wizard_strategy_recommended_header, commanderName))
                    }
                    if (recommended.isNotEmpty()) {
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
                    } else {
                        item(key = "recommended_empty") {
                            Text(
                                text = stringResource(R.string.deck_wizard_strategy_recommended_empty, commanderName),
                                style = ty.bodyMedium,
                                color = mc.textSecondary,
                                modifier = Modifier.padding(vertical = spacing.sm),
                            )
                        }
                    }
                    if (other.isNotEmpty()) {
                        item(key = "partial_fit_toggle") {
                            PartialFitToggle(
                                expanded = otherPlansExpanded,
                                subtitle = if (!otherPlansExpanded) stringResource(R.string.deck_wizard_strategy_partial_fit_subtitle, commanderName) else null,
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

/** Deck Wizard Commander v4 plan, W3/E3: renamed from "Other plans" -- users found the old label
 * and its equal visual weight with Recommended read as false confidence. [subtitle] (shown only
 * while collapsed) frames the group honestly: these can work, the commander just doesn't push
 * toward them. */
@Composable
private fun PartialFitToggle(expanded: Boolean, subtitle: String?, onToggle: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle, role = Role.Button)
            .padding(vertical = spacing.xxs),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = mc.textSecondary,
            )
            Text(
                text = stringResource(if (expanded) R.string.deck_wizard_strategy_partial_fit_collapse else R.string.deck_wizard_strategy_partial_fit_expand),
                style = ty.labelLarge,
                color = mc.textSecondary,
            )
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = ty.bodySmall,
                color = mc.textDisabled,
                modifier = Modifier.padding(start = spacing.xxl, top = spacing.xxs),
            )
        }
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
 * **Category navigation (v4 plan, W4.1/G5/R5):** reuses the Analysis tab's own single-select
 * [PillarTile] row verbatim (`DeckStudioScreen.kt`'s `expandedPillar`/`PillarTile` pattern) --
 * every pillar's icon/subscore/label renders at once (the overview), and tapping a tile switches
 * which pillar's [CardSectionRow] list renders below, replacing the old always-all-expanded,
 * every-pillar-stacked-into-one-list layout this step used to render.
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
    // Deck Wizard v4 plan, W4.1/G5/R5 -- category navigation reused verbatim from the Analysis
    // tab's own PillarTile row (DeckStudioScreen.kt's `expandedPillar`): a single-select tile per
    // pillar gives the at-a-glance overview (icon/subscore/label per category, all visible at once)
    // and the step now shows only the SELECTED pillar's sections below, instead of every pillar's
    // sections stacked into one long undifferentiated list.
    var selectedPillarId by rememberSaveable { mutableStateOf<PillarId?>(PLAN_SECTIONS_PILLAR_ORDER.firstOrNull()) }
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
                item(key = "pillar_row") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        pillars.forEach { pillar ->
                            val isSelected = selectedPillarId == pillar.id
                            PillarTile(
                                pillar = pillar,
                                expanded = isSelected,
                                onClick = { selectedPillarId = pillar.id },
                                // compose-design-reviewer P1 finding: PillarTile has no built-in
                                // `selected` semantics (it's a repurposed "expanded" boolean, color
                                // only) -- wrap it here since this call site promotes it to a
                                // single-select TAB row, this campaign's primary step navigation.
                                modifier = Modifier.weight(1f).semantics { selected = isSelected; role = Role.Tab },
                            )
                        }
                    }
                }
                val selectedPillar = pillars.firstOrNull { it.id == selectedPillarId } ?: pillars.firstOrNull()
                if (selectedPillar != null) {
                    item(key = "pillar_${selectedPillar.id.name}_header") {
                        Text(
                            text = selectedPillar.id.label().uppercase(),
                            style = ty.labelMedium,
                            color = mc.primaryAccent,
                            modifier = Modifier.padding(top = spacing.sm, bottom = spacing.xxs),
                        )
                    }
                    items(selectedPillar.sections, key = { "${selectedPillar.id.name}:${it.id}" }) { section ->
                        val sectionKey = "${selectedPillar.id.name}:${section.id}"
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
