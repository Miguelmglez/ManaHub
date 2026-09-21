package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-21

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.mmg.manahub.R
import com.mmg.manahub.core.model.AddCardRow
import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckCardSource
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.ui.components.CardRow
import com.mmg.manahub.core.ui.components.CardSearchSheet
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
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.CardSection
import com.mmg.manahub.feature.decks.domain.engine.CopyPolicy
import com.mmg.manahub.feature.decks.domain.engine.CuratedStrategy
import com.mmg.manahub.feature.decks.domain.engine.PillarId
import com.mmg.manahub.feature.decks.domain.engine.SectionQueryContext
import com.mmg.manahub.feature.decks.domain.engine.SectionSearchQuery
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
import com.mmg.manahub.feature.decks.domain.usecase.RecommendWizardStrategiesUseCase
import com.mmg.manahub.feature.decks.domain.usecase.StrategyRecommendation
import com.mmg.manahub.feature.decks.presentation.components.CardDetailSheet
import com.mmg.manahub.feature.decks.presentation.components.CardFlipPortrait
import com.mmg.manahub.feature.decks.presentation.components.CardSectionRow
import com.mmg.manahub.feature.decks.presentation.components.PillarTile
import com.mmg.manahub.feature.decks.presentation.components.SeedSelectionUi
import com.mmg.manahub.feature.decks.presentation.components.TribeOption
import com.mmg.manahub.feature.decks.presentation.components.TribePickerSection
import com.mmg.manahub.feature.decks.presentation.components.label

// ── COMMANDER_PICK ────────────────────────────────────────────────────────────

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
 * The first Commander step: a [LazyColumn] of [CardRow]s over every eligible owned commander
 * ([commanderPickLocalCandidates]) while idle ([commanderPickIsIdle]), or live Scryfall results
 * ([DeckWizardUiState.commanderSearchResults]) the moment there is search text or an active filter.
 * The trailing Tune icon opens the shared [AdvancedSearchSheet] locked to [commanderLockedCriteria]
 * (D14); the active-filter count row + "clear filters" mirror `CollectionScreen.kt`'s own pattern.
 *
 * Tapping a row (or its image) opens the single-card inspection overlay with "Choose as commander"
 * / "Cancel". Once [DeckWizardUiState.selectedCommander] is set, the list cross-fades into the picked
 * commander's flip portrait + tags + a "Change commander" CTA -- "Next" is owned solely by the sticky
 * [WizardStickyButton] below, same as every sibling step.
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
    var showAdvancedSearch by remember { mutableStateOf(false) }
    val inspection = rememberWizardCardInspectionState()

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

    WizardCardInspectionHost(
        state = inspection,
        actions = { card ->
            MagicCtaButton(
                onClick = {
                    onSelectCommander(card)
                    inspection.requestDismiss()
                },
                text = stringResource(R.string.deckbuilder_choose_as_commander),
                color = MagicCtaColor.Gold,
                icon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
            )
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
            AnimatedContent(
                targetState = commander,
                transitionSpec = {
                    (fadeIn(tween(250)) + scaleIn(initialScale = 0.94f, animationSpec = tween(250))) togetherWith fadeOut(tween(250))
                },
                contentKey = { it != null },
                label = "CommanderPickListOrSelected",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { selected ->
                if (selected != null) {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = spacing.lg, vertical = spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.md),
                    ) {
                        Text(stringResource(R.string.deck_wizard_commander_pick_title), style = ty.titleLarge, color = mc.textPrimary)
                        CardFlipPortrait(card = selected, modifier = Modifier.animateEnterExit(enter = fadeIn() + scaleIn(initialScale = 0.94f), exit = fadeOut()))
                        val commanderTags = remember(selected) { selected.tags + selected.userTags }
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        item(key = "header") {
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

                        item(key = "search") {
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
                            item(key = "collection_header") {
                                Text(stringResource(R.string.deck_wizard_commanders_in_collection), style = ty.titleMedium, color = mc.textPrimary)
                            }
                        }

                        when {
                            // Loading wins over error and empty: an empty state mid-search reads as "no results".
                            !isIdle && uiState.isSearchingCommander -> item(key = "loading") {
                                Box(Modifier.fillMaxWidth().padding(vertical = spacing.lg), contentAlignment = Alignment.Center) {
                                    MagicLoadingSpinner(size = MagicLoadingSize.Small)
                                }
                            }
                            !isIdle && uiState.commanderSearchError -> item(key = "error") {
                                InlineErrorState(
                                    message = stringResource(R.string.deck_wizard_commander_search_error),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.lg),
                                )
                            }
                            candidatesToShow.isEmpty() -> item(key = "empty") {
                                EmptyState(
                                    title = stringResource(if (isIdle) R.string.deck_wizard_commander_pick_idle_empty else R.string.deck_wizard_commander_pick_empty),
                                    icon = Icons.Default.Search,
                                    actionLabel = if (!isIdle) stringResource(R.string.deck_wizard_commander_clear_filters) else null,
                                    onAction = if (!isIdle) onClearSearchAndFilters else null,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xl),
                                )
                            }
                            else -> items(candidatesToShow, key = { "cmdcand_${it.scryfallId}" }) { card ->
                                var thumbnailRect by remember { mutableStateOf(Rect.Zero) }
                                CardRow(
                                    card = card,
                                    isInCollection = card.scryfallId in ownedIds,
                                    onClick = { inspection.open(card, thumbnailRect) },
                                    onImageClick = { inspection.open(card, thumbnailRect) },
                                    onRemove = null,
                                    onAdd = null,
                                    selected = false,
                                    modifier = Modifier
                                        .animateItem()
                                        .onGloballyPositioned { thumbnailRect = inspection.thumbnailRectOf(it, imageIsInteractive = true) },
                                )
                            }
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
}

// ── STRATEGY ──────────────────────────────────────────────────────────────────

/** Sentinel id for the UI-level "Custom" row -- never a [CuratedStrategy.id] (the catalog never
 * mints an id equal to this), so it can share a single-select `selectedId` slot with real catalog
 * ids without ambiguity. */
internal const val CUSTOM_STRATEGY_ID = "__custom__"

/** Deck Wizard Commander v4 plan (W3, E3): the STRATEGY step's own split of
 * [DeckWizardUiState.strategyRecommendations] into "Recommended" (score-threshold + hard
 * cap of 5) and collapsed "Partial fit" (the rest of the format's catalog, collapsed by default) --
 * delegates entirely to [RecommendWizardStrategiesUseCase.splitRecommended], the use case's own
 * threshold/cap logic (never a UI-local `take(N)`). A plain (non-`@Composable`) function so it stays
 * unit-testable without a Compose UI test. */
internal fun strategyRecommendedSplit(recommendations: List<StrategyRecommendation>): Pair<List<StrategyRecommendation>, List<StrategyRecommendation>> =
    RecommendWizardStrategiesUseCase().splitRecommended(recommendations)

/**
 * Deck Wizard 60-card wave (v6), plan §5 Phase 5.3: the single-select strategy list (D4) --
 * [recommendations] split into "Recommended" (score-threshold + hard cap of 5, E3) and collapsed
 * "Partial fit", plus "Custom" (only when [showCustom]) always offered last. Extracted from
 * [StrategyStepContent] (list items only -- header/subtitle/sticky button stay in each step) so
 * [ColorPickStepContent] (`DeckWizardSixtySteps.kt`) can reuse it verbatim.
 *
 * [contextLabel] is the already-resolved "for %1$s" substitution (a commander's name, "your N
 * cards", or blank) -- blank falls back to the `_generic` string variants (COLOR_PICK has no
 * natural "for X" phrase). [otherPlansExpanded]/[onToggleOtherPlansExpanded] are hoisted (not
 * `remember`ed here) so each calling step owns its own toggle state.
 *
 * A [CuratedStrategy.requiresTribe] entry the recommender could not resolve a concrete tribe for
 * opens the shared [TribePickerSection] tribe sub-picker (same component Deck Studio's own
 * [CuratedStrategyPickerSheet] uses) instead of applying with no tribe. [selectedDisplayLabel]
 * ([DeckWizardUiState.strategyDisplayLabel]) titles the selected row so a tribal pick reads
 * "Tribal — Elves".
 */
internal fun LazyListScope.StrategyRecommendationList(
    recommendations: List<StrategyRecommendation>,
    selectedId: String?,
    selectedDisplayLabel: String?,
    onSelectStrategy: (CuratedStrategy) -> Unit,
    onSelectCustom: () -> Unit,
    onRequestTribe: (CuratedStrategy) -> Unit,
    contextLabel: String,
    otherPlansExpanded: Boolean,
    onToggleOtherPlansExpanded: () -> Unit,
    showCustom: Boolean = true,
) {
    val (recommended, other) = strategyRecommendedSplit(recommendations)
    fun onRowClick(recommendation: StrategyRecommendation) {
        if (recommendation.strategy.requiresTribe && recommendation.tribe == null) {
            onRequestTribe(recommendation.strategy)
        } else {
            onSelectStrategy(recommendation.strategy)
        }
    }

    // compose-design-reviewer P2 findings (Commander v4): the header now renders unconditionally
    // (previously it was skipped along with the whole branch when Recommended was empty, so the
    // "why empty" text dropped in with none of the section chrome every other block uses); the
    // empty-explanation text no longer requires `other` to be non-empty either.
    item(key = "recommended_header") {
        StrategySectionHeader(
            if (contextLabel.isNotEmpty()) stringResource(R.string.deck_wizard_strategy_recommended_header, contextLabel)
            else stringResource(R.string.deck_wizard_strategy_recommended_header_generic)
        )
    }
    if (recommended.isNotEmpty()) {
        items(recommended, key = { "rec_${it.strategy.id}" }) { recommendation ->
            val isSelected = selectedId == recommendation.strategy.id
            StrategyRecommendationRow(
                recommendation = recommendation,
                isSelected = isSelected,
                onClick = { onRowClick(recommendation) },
                titleOverride = if (isSelected) selectedDisplayLabel else null,
            )
        }
    } else {
        item(key = "recommended_empty") {
            Text(
                text = if (contextLabel.isNotEmpty()) stringResource(R.string.deck_wizard_strategy_recommended_empty, contextLabel)
                    else stringResource(R.string.deck_wizard_strategy_recommended_empty_generic),
                style = MaterialTheme.magicTypography.bodyMedium,
                color = MaterialTheme.magicColors.textSecondary,
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.sm),
            )
        }
    }
    if (other.isNotEmpty()) {
        item(key = "partial_fit_toggle") {
            PartialFitToggle(
                expanded = otherPlansExpanded,
                subtitle = if (!otherPlansExpanded) {
                    if (contextLabel.isNotEmpty()) stringResource(R.string.deck_wizard_strategy_partial_fit_subtitle, contextLabel)
                    else stringResource(R.string.deck_wizard_strategy_partial_fit_subtitle_generic)
                } else null,
                onToggle = onToggleOtherPlansExpanded,
            )
        }
        if (otherPlansExpanded) {
            items(other, key = { "other_${it.strategy.id}" }) { recommendation ->
                val isSelected = selectedId == recommendation.strategy.id
                StrategyRecommendationRow(
                    recommendation = recommendation,
                    isSelected = isSelected,
                    onClick = { onRowClick(recommendation) },
                    titleOverride = if (isSelected) selectedDisplayLabel else null,
                )
            }
        }
    }
    if (showCustom) {
        item(key = "custom_header") {
            StrategySectionHeader(stringResource(R.string.deck_wizard_strategy_custom_header))
        }
        item(key = "custom") {
            val mc = MaterialTheme.magicColors
            MagicSelectionItem(
                title = stringResource(R.string.deck_wizard_strategy_custom_title),
                description = stringResource(R.string.deck_wizard_strategy_custom_description),
                isSelected = selectedId == null,
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

/**
 * The single-select STRATEGY step, shared by Commander AND the Cards flow (S1/S7) -- header +
 * subtitle here, the list itself is [StrategyRecommendationList]. Subtitle: Commander "for
 * <commander name>" (unchanged); Cards "for your N cards" (`deck_wizard_strategy_subtitle_seeds`,
 * N = [DeckWizardUiState.seedCopies]). The #1 recommendation is preselected by the ViewModel the
 * moment the list loads and Next is ALWAYS enabled (plan §4/§8 defaults) -- this step never blocks
 * progress the way the old picker's tribe-required gate could.
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
    val isCommander = uiState.selectedFormat?.isCommanderFormat == true
    val commanderName = uiState.selectedCommander?.name.orEmpty()
    val pendingTribeStrategy = uiState.pendingTribeStrategy
    val tribeOptions = remember(uiState.commanderTribePickerCandidates) {
        uiState.commanderTribePickerCandidates.map { TribeOption(key = it.tribeKey, label = it.displayLabel) }
    }
    // Deck Wizard 60-card wave (v6): STRATEGY is now shared by every anchor, not just Commander
    // (S1/S7) -- the Cards flow's own "for your N cards" context replaces the commander's name for
    // BOTH the title and the Recommended-list's "for X" phrasing.
    val contextLabel = if (isCommander) commanderName else stringResource(R.string.deck_wizard_strategy_context_seeds, uiState.seedCopies)

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
                            text = if (isCommander) {
                                stringResource(R.string.deck_wizard_strategy_step_title_for, commanderName)
                            } else {
                                stringResource(R.string.deck_wizard_strategy_step_title)
                            },
                            style = ty.titleLarge,
                            color = mc.textPrimary,
                        )
                        Text(
                            text = if (isCommander) {
                                stringResource(R.string.deck_wizard_strategy_step_subtitle)
                            } else {
                                stringResource(R.string.deck_wizard_strategy_subtitle_seeds, uiState.seedCopies)
                            },
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
                    StrategyRecommendationList(
                        recommendations = uiState.strategyRecommendations,
                        selectedId = uiState.selectedCuratedStrategyId,
                        selectedDisplayLabel = uiState.strategyDisplayLabel,
                        onSelectStrategy = onSelectStrategy,
                        onSelectCustom = onSelectCustom,
                        onRequestTribe = onRequestTribe,
                        contextLabel = contextLabel,
                        otherPlansExpanded = otherPlansExpanded,
                        onToggleOtherPlansExpanded = { otherPlansExpanded = !otherPlansExpanded },
                        showCustom = true,
                    )
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
    // Task 0 / P2 (design review): relying on the label text alone ("Partial fit" <-> "Hide
    // partial fit") to convey expand/collapse state gives TalkBack no distinct state signal --
    // stateDescription makes the expanded/collapsed state an explicit, announced fact.
    val stateDescription = stringResource(
        if (expanded) R.string.deck_wizard_strategy_partial_fit_state_expanded
        else R.string.deck_wizard_strategy_partial_fit_state_collapsed,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle, role = Role.Button)
            .semantics { this.stateDescription = stateDescription }
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

/** The display label for a raw `tribe:<subtype>` key -- mirrors [DeckWizardViewModel]'s own
 * `selectedTribeLabel` derivation so an unselected tribal row reads the same as the selected one. */
internal fun tribeDisplayLabel(tribeKey: String): String =
    tribeKey.removePrefix(TribeDeriver.TRIBE_PREFIX).replaceFirstChar(Char::uppercase)

/**
 * One STRATEGY/COLOR_PICK/STRATEGY_PICK row: a [MagicSelectionItem] titled with the strategy name
 * (plus " — <Tribe>" when the recommender resolved a tribe, or [titleOverride] for the selected
 * row), described by the strategy's own explanation, with the owned fitting-card count as a
 * compact trailing badge ([OwnedCountBadge], hidden at 0). [icon] is STRATEGY_PICK's committed
 * color-combo symbols; every other caller leaves it null.
 */
@Composable
internal fun StrategyRecommendationRow(
    recommendation: StrategyRecommendation,
    isSelected: Boolean,
    onClick: () -> Unit,
    titleOverride: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    val title = titleOverride
        ?: recommendation.tribe?.let { "${recommendation.strategy.displayName} — ${tribeDisplayLabel(it)}" }
        ?: recommendation.strategy.displayName
    val ownedCount = recommendation.ownedFittingCount
    MagicSelectionItem(
        title = title,
        description = recommendation.strategy.description,
        isSelected = isSelected,
        accentColor = MaterialTheme.magicColors.primaryAccent,
        icon = icon,
        trailing = if (ownedCount > 0) ({ OwnedCountBadge(count = ownedCount) }) else null,
        onClick = onClick,
    )
}

/** "You own N cards" as a chip: the same owned-icon language [CardRow] uses, tonal accent fill with
 * full-opacity accent text (the [com.mmg.manahub.core.ui.components.CardTagChip] recipe) so it clears
 * contrast on HallowedPrint as well as the dark palettes. */
@Composable
private fun OwnedCountBadge(count: Int) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val ownedDescription = stringResource(R.string.deck_wizard_strategy_owned_count, count)
    Surface(
        shape = ChipShape,
        color = mc.primaryAccent.copy(alpha = 0.12f),
        modifier = Modifier.semantics { contentDescription = ownedDescription },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
        ) {
            Icon(
                imageVector = Icons.Rounded.CollectionsBookmark,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(14.dp),
            )
            Text(text = count.toString(), style = ty.labelMedium, color = mc.primaryAccent)
        }
    }
}

// ── PLAN_SECTIONS (Deck Wizard Commander v3 plan, Phase 5, R4; generalized to every anchor by the
//    60-card wave v6, plan §5 Phase 5.1, S1) — the ONE engine-attributed browse/add step now; the
//    pre-v6 Casual-only ManualAddsStepContent was deleted along with the DIRECTION/IDENTITY step
//    machinery it depended on. ─────────────────────────────────────────────────────────────────

/** Pillar display order for the PLAN_SECTIONS step (plan 5.2) -- LEGALITY is never shown here (it
 * carries no section a manual add can meaningfully target: `legal`/`illegal` have no Browse action
 * per [SectionSearchQuery.fragmentFor]'s own "no sensible add-more action" rule). */
private val PLAN_SECTIONS_PILLAR_ORDER = listOf(PillarId.PLAN_ROLES, PillarId.MANA_BASE, PillarId.CURVE, PillarId.SYNERGY)

/** The SYNERGY residual buckets (Deck Analysis Engine v3, Phase 4, spec §7) -- the manual adds the
 * engine could not attribute to the plan. Relabeled "Kept — outside the plan" here (R5/D7: the
 * wizard never auto-drops a manual add for being off-plan) rather than their normal
 * [CardSection.label]. */
private val KEPT_OUTSIDE_PLAN_IDS = setOf("interaction", "standalone", "offplan")

/** The merged residual section's id (never an engine id; `SectionSearchQuery` has no Browse for it). */
internal const val KEPT_OUTSIDE_PLAN_SECTION_ID = "kept_outside_plan"

/** Folds the SYNERGY residual buckets ([KEPT_OUTSIDE_PLAN_IDS]) into ONE section labelled [label],
 * placed where the first of them appeared -- three back-to-back rows with the same header read as
 * a rendering bug. Sections with no residual bucket come back untouched. */
internal fun mergeKeptOutsidePlanSections(sections: List<CardSection>, label: String): List<CardSection> {
    val residual = sections.filter { it.id in KEPT_OUTSIDE_PLAN_IDS }
    if (residual.isEmpty()) return sections
    val merged = CardSection(
        id = KEPT_OUTSIDE_PLAN_SECTION_ID,
        label = label,
        current = residual.sumOf { it.current },
        contributions = residual.flatMap { it.contributions },
    )
    val firstIndex = sections.indexOfFirst { it.id in KEPT_OUTSIDE_PLAN_IDS }
    return sections.filterNot { it.id in KEPT_OUTSIDE_PLAN_IDS }.toMutableList().apply { add(firstIndex, merged) }
}

/** Same [SnapshotStateMap] + flattened-`List<String>` [Saver] shape as `DeckStudioScreen.kt`'s
 * `CollapsedCategorySectionsSaver` -- duplicated locally (not promoted to a shared file) per this
 * codebase's own "small per-file duplicate over cross-file coupling for one extra call site"
 * convention (the same judgment call this file makes elsewhere for its own single-use rows). */
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
    // Deck Wizard 60-card wave (v6), plan §5 Phase 5.4: opens/closes the "Your cards" section's own
    // inline CardDetailSheet for a 60-card seed (Commander rows keep navigating via onCardClick).
    onShowSeedDetail: (Card?) -> Unit,
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
    // Deck Wizard v4, W4.3 (G7): "Browse for X" must ALWAYS open on the Collection tab. Hoisted
    // (rather than left to CardSearchSheet's own un-hoisted default) because that default switches
    // to All Cards whenever a non-blank structured query exists -- true for almost every section
    // since W4.2's fragment-coverage fix -- regardless of whether a collection-tag preset also
    // fires; hoisting lets this call site pin the opening tab unconditionally.
    var browseSheetTab by rememberSaveable { mutableIntStateOf(0) }
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
    // Deck Wizard UX polish plan, Run 1 §1.2: carries the raw section id itself (never real
    // CardTag keys) through CardSearchSheet's onFilterCollectionByTags(Set<String>) -> Unit param
    // shape -- the VM resolves the real SectionMembership.predicate from it directly.
    val browseTagKeys = remember(browseSectionId) { setOfNotNull(browseSectionId) }

    fun resolveCard(scryfallId: String): Card? =
        uiState.selectedCommander?.takeIf { it.scryfallId == scryfallId }
            ?: uiState.seeds.firstOrNull { it.card.scryfallId == scryfallId }?.card

    val analysis = uiState.planAnalysis
    val pillars = remember(analysis) {
        PLAN_SECTIONS_PILLAR_ORDER.mapNotNull { id -> analysis?.pillars?.firstOrNull { it.id == id } }
    }
    val keptOutsidePlanLabel = stringResource(R.string.deck_wizard_plan_sections_kept_outside_plan)

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
                        modifier = Modifier.fillMaxWidth().selectableGroup(),
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
                    items(mergeKeptOutsidePlanSections(selectedPillar.sections, keptOutsidePlanLabel), key = { "${selectedPillar.id.name}:${it.id}" }) { section ->
                        val sectionKey = "${selectedPillar.id.name}:${section.id}"
                        // Defaults to EXPANDED (matches DeckStudioScreen's Analysis tab convention,
                        // where collapsedCategorySections gates on `!= true`) -- this step's whole
                        // purpose is to surface each category's Browse action, so hiding everything
                        // behind a tap on first load would bury the primary action of the screen.
                        val expanded = expandedSections[sectionKey] ?: true
                        val browseFragment = if (section.id == KEPT_OUTSIDE_PLAN_SECTION_ID) null else SectionSearchQuery.fragmentFor(section.id, queryContext)
                        CardSectionRow(
                            section = section,
                            resolveCard = ::resolveCard,
                            onCardClick = onCardClick,
                            onBrowse = if (browseFragment == null) null else {
                                {
                                    browseSectionId = section.id
                                    browseSheetTab = 0 // Collection tab, always (G7).
                                    showSearchSheet = true
                                }
                            },
                            expanded = expanded,
                            onToggleExpanded = { expandedSections[sectionKey] = !expanded },
                            ownedAvailabilityHint = uiState.ownedAvailabilityBySection[section.id],
                        )
                    }
                }
                if (uiState.seeds.isNotEmpty()) {
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
                    // One gesture contract everywhere: row/image tap = inspect, +/- = copies.
                    // Commander seeds navigate to the full detail screen (quantity is always 1
                    // there); a 60-card row gets the +/- stepper capped at CopyPolicy.maxSeedCopies
                    // and its own inline detail sheet.
                    items(uiState.seeds, key = { "planadded_${it.card.scryfallId}" }) { seed ->
                        if (format.isCommanderFormat) {
                            CardRow(
                                card = seed.card,
                                isInCollection = true,
                                onClick = { onCardClick(seed.card.scryfallId) },
                                onRemove = { onRemoveCard(seed.card) },
                                onImageClick = { onCardClick(seed.card.scryfallId) },
                            )
                        } else {
                            val maxCopies = remember(seed.card, format) { CopyPolicy.maxSeedCopies(seed.card, format) }
                            CardRow(
                                card = seed.card,
                                isInCollection = (uiState.ownedQuantityByName[seed.card.name] ?: 0) > 0,
                                quantity = seed.quantity,
                                onClick = { onShowSeedDetail(seed.card) },
                                onAdd = { onAddCard(seed.card) },
                                onRemove = { onRemoveCard(seed.card) },
                                addEnabled = seed.quantity < maxCopies,
                                onImageClick = { onShowSeedDetail(seed.card) },
                            )
                        }
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
        val existingIds = remember(uiState.selectedCommander, uiState.seeds) {
            (listOfNotNull(uiState.selectedCommander?.scryfallId) + uiState.seeds.map { it.card.scryfallId }).toSet()
        }
        val ownedIds = remember(uiState.ownedCards) { uiState.ownedCards.map { it.scryfallId }.toSet() }
        val seedQuantityById = remember(uiState.seeds) { uiState.seeds.associate { it.card.scryfallId to it.quantity } }
        // Deck Wizard 60-card wave (v6), plan §5 Phase 5.4: a 60-card seed's real quantity (not
        // just a 0/1 "is it already added" flag) -- Commander seeds are always quantity 1, so this
        // is byte-identical there.
        fun toRow(card: Card) = AddCardRow(
            card = card,
            quantityInDeck = seedQuantityById[card.scryfallId] ?: (if (card.scryfallId in existingIds) 1 else 0),
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
            selectedTabIndex = browseSheetTab,
            onSelectedTabChange = { browseSheetTab = it },
            onDismiss = {
                showSearchSheet = false
                browseSectionId = null
                onClearSearchState()
            },
        )
    }

    // Deck Wizard 60-card wave (v6), plan §5 Phase 5.4: "Your cards"' own inline detail sheet for a
    // 60-card seed -- mirrors SeedPickStepContent's own [uiState.seedDetailCard] block verbatim
    // (same field, opened from a different step).
    if (!format.isCommanderFormat) {
        uiState.seedDetailCard?.let { card ->
            val existingQuantity = uiState.seeds.firstOrNull { it.card.scryfallId == card.scryfallId }?.quantity ?: 0
            val maxQuantity = CopyPolicy.maxSeedCopies(card, format)
            val ownedIds = remember(uiState.ownedCards) { uiState.ownedCards.map { it.scryfallId }.toSet() }
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
                    onAdd = { onAddCard(card) },
                    onRemove = { onRemoveCard(card) },
                ),
            )
        }
    }
}
