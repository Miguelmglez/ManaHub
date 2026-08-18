package com.mmg.manahub.feature.draft.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.BASIC_LAND_NAMES
import com.mmg.manahub.core.model.GroupingMode
import com.mmg.manahub.core.tagging.label
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.ManaCurveChart
import com.mmg.manahub.core.ui.components.ManaHubBottomSheetSelector
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.MagicCardInspectionOverlay
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.coloredShadow
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.model.DraftCard
import com.mmg.manahub.core.model.DraftState

/**
 * Sparkle overlay + deck-preview content for the Draft Simulator's "Deck" tab (Phase C merge —
 * see [com.mmg.manahub.feature.draft.presentation.ui.DraftSimulatorScreen]). These composables used
 * to back a standalone `DraftResultScreen` nav destination; that destination was removed when the
 * Drafting/Result screens were collapsed into one, but the content itself is unchanged and is now
 * called directly from `DraftSimulatorScreen`'s Deck tab.
 *
 * Phase D reworked [ResultContent] into a real mainboard/sideboard curation UI: grouping
 * (Type/Color/CMC/Tag), a per-card active/inactive (mainboard/sideboard) toggle + tap-to-inspect
 * overlay, corrected pool/non-land/land stats, and an editable basic-land count with a "Magic Land
 * Suggestions" autofill — all gated behind the draft actually being finished (see
 * `DraftSimViewModel`'s `inactivePoolIndices`/`basicLandCounts`/`targetLandCount` state, which the Deck
 * tab reads/writes through instead of local Composable state, because `onCompleteDraft` needs to
 * read the curation at save time).
 */
@Composable
internal fun CelebrationSparkles() {
    val mc = MaterialTheme.magicColors
    val infiniteTransition = rememberInfiniteTransition(label = "sparkles")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sparkle_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        mc.primaryAccent.copy(alpha = alpha * 0.5f),
                        Color.Transparent
                    ),
                    center = androidx.compose.ui.geometry.Offset(200f, 200f),
                    radius = 400f
                )
            )
            .graphicsLayer { this.alpha = alpha }
    )
}

/** Image alpha applied to a pool card the user has toggled to the sideboard (D.4). */
private const val INACTIVE_CARD_ALPHA = 0.35f

@Composable
internal fun ResultContent(
    state: DraftState?,
    isDraftComplete: Boolean,
    inactivePoolIndices: Set<Int>,
    onToggleCardActive: (Int) -> Unit,
    basicLandCounts: Map<String, Int>,
    targetLandCount: Int,
    onSetBasicLandCount: (String, Int) -> Unit,
    onSetTargetLandCount: (Int) -> Unit,
    onApplyLandSuggestions: () -> Unit,
    onSave: () -> Unit,
    /** G.2: true while [onSave] is in flight — disables/shows a spinner on the Save button. */
    isSaving: Boolean,
    onBack: () -> Unit,
    errorMessage: String?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    if (state == null && errorMessage != null) {
        // G.3 fix: this is the branch `DeckTabContent`'s `is DraftSimUiState.Error ->` call site
        // actually reaches (it always passes `state = null`, `isDraftComplete = false`) — the
        // isDraftComplete-gated back button in the header Row below is never reached from here since
        // this early `return` skips it entirely. `FullErrorState` itself has no back/secondary-action
        // slot, so a dedicated back button is rendered here: an error state must always show a
        // visible way back regardless of draft-completion status, not just rely on the system back
        // gesture (F.6's BackHandler still routes it correctly, but there was no on-screen affordance).
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sp.sm, vertical = sp.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = mc.textPrimary,
                    )
                }
            }
            FullErrorState(
                message = errorMessage,
                retryLabel = stringResource(R.string.draft_retry),
                onRetry = onSave,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    val humanSeat = state?.seats?.firstOrNull { it.isHuman }
    val pool = humanSeat?.pool.orEmpty()

    // D.5: stats now respect the active/inactive (mainboard/sideboard) split instead of always
    // reporting the whole pool. POOL stays the total drafted count; NON-LAND/LANDS count only
    // ACTIVE (mainboard) cards, plus the user's own basic-land curation for LANDS.
    // E.2: filtered by POOL POSITION now, not scryfallId (see DraftSimViewModel.inactivePoolIndices).
    val activePool = remember(pool, inactivePoolIndices) {
        pool.filterIndexed { index, _ -> index !in inactivePoolIndices }
    }
    val activeNonLands = remember(activePool) { activePool.filterNot { BasicLandCalculator.isLand(it.card) } }
    // E.5: a drafted BASIC land is excluded from the "Lands" accounting entirely — the user adds
    // basics exclusively via DraftBasicLandsEditor's stepper, never via the pool.
    val activeLands = remember(activePool) {
        activePool.filter { BasicLandCalculator.isLand(it.card) && !BasicLandCalculator.isBasicLand(it.card) }
    }
    val basicsTotal = basicLandCounts.values.sum()
    val cmcDistribution = remember(activeNonLands) {
        activeNonLands.groupBy { it.card.cmc.toInt() }.mapValues { it.value.size }
    }

    var groupingMode by remember { mutableStateOf(GroupingMode.TYPE) }
    // E.5/F.2: drafted basics are dropped from grouping AND from the tap-to-inspect overlay entirely
    // (indices are preserved via IndexedValue even though some entries get filtered out, so E.2's
    // pool-position toggling stays correct). Computed ONCE and shared by both the grouped list and
    // the overlay so paging through the overlay can never surface a drafted basic land either.
    val inspectablePool = remember(pool) {
        pool.withIndex().filterNot { BasicLandCalculator.isBasicLand(it.value.card) }
    }
    val grouped = remember(inspectablePool, groupingMode) {
        groupDraftCards(inspectablePool, groupingMode)
    }
    // E.3: per-group expand/collapse state, mirroring DeckStudioScreen's SectionHeader — default
    // every group EXPANDED (a freshly-completed draft should show everything at a glance).
    var collapsedGroups by remember { mutableStateOf(emptySet<String>()) }
    // F.1: the basic-lands editor section gets the same expand/collapse affordance as the card
    // groups above — default EXPANDED, matching every other group's default.
    var basicsExpanded by remember { mutableStateOf(true) }

    // MagicCardInspectionOverlay pattern (mirrors DeckStudioScreen): rootCoordinates anchors every
    // tile's captured Rect so the fly-in/out animation starts/ends at the tapped tile's exact
    // on-screen position.
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var inspectingIndex by remember { mutableStateOf<Int?>(null) }
    var inspectionRect by remember { mutableStateOf(Rect.Zero) }
    var isDismissingInspection by remember { mutableStateOf(false) }

    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // No statusBarsPadding here: DraftSimulatorScreen (the sole caller now) applies it once at the
    // outer Column, above the TabRow — applying it again here would double the top inset.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootCoordinates = it },
    ) {
        // --- Header ---
        // F.3: while the draft is still in progress (!isDraftComplete), DraftSimulatorScreen's OWN
        // DraftingHeaderRow already renders a back button ABOVE the tab strip (gated on
        // DraftSimUiState.Drafting) that's wired to the SAME exit-confirmation-gated callback this
        // composable receives as `onBack` — rendering a second IconButton here was pure visual
        // duplication (not a behavior bypass: both call the identical handleBackRequest), so it's
        // omitted here once isDraftComplete, leaving DraftingHeaderRow as the sole back affordance
        // while drafting. The title also no longer claims "Drafting complete" while still drafting.
        //
        // G.3 fix: mirrors the same `isDraftComplete || errorMessage != null` rule the early-return
        // FullErrorState branch above now applies (the branch DeckTabContent's Error state actually
        // reaches — see its comment). Kept here too as a defensive fallback: `errorMessage != null`
        // together with a non-null `state` is not emitted by any CURRENT caller, but is a valid
        // combination per this function's own signature, and should show the same "always show a
        // way out on error" back button if a future caller ever produces it.
        val showBackButton = isDraftComplete || errorMessage != null
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sp.sm, vertical = sp.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = mc.textPrimary,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (isDraftComplete) R.string.draft_sim_building_title else R.string.draft_sim_deck_preview_title,
                    ),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                )
                if (state != null) {
                    Text(
                        text = state.config.setCode.uppercase(),
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            if (state != null) {
                SetSymbol(
                    setCode = state.config.setCode,
                    rarity = CardRarity.MYTHIC,
                    size = 32.dp,
                    modifier = Modifier.padding(end = sp.md)
                )
            }
        }

        if (errorMessage != null) {
            InlineErrorState(
                message = errorMessage,
                retryLabel = stringResource(R.string.draft_retry),
                onRetry = onSave,
                modifier = Modifier.padding(horizontal = sp.lg, vertical = sp.xs),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(sp.md),
            horizontalArrangement = Arrangement.spacedBy(sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.sm),
        ) {
            // --- Analysis Section ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }

                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(800)) + slideInVertically(tween(800)) { -20 }
                ) {
                    Surface(
                        shape = CardShape,
                        color = mc.surface,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = sp.md)
                            .coloredShadow(
                                color = mc.primaryAccent.copy(alpha = 0.1f),
                                borderRadius = 12.dp,
                                blurRadius = 12.dp
                            )
                    ) {
                        Column(modifier = Modifier.padding(sp.md)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(sp.sm)
                            ) {
                                Icon(
                                    Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = mc.primaryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "DECK ANALYSIS",
                                    style = ty.labelMedium,
                                    color = mc.textPrimary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.height(sp.md))
                            ManaCurveChart(
                                cmcDistribution = cmcDistribution,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(sp.md))
                            HorizontalDivider(color = mc.surfaceVariant, thickness = 0.5.dp)
                            Spacer(Modifier.height(sp.md))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem(label = "POOL", value = pool.size.toString())
                                StatItem(label = "NON-LAND", value = activeNonLands.size.toString())
                                StatItem(label = "LANDS", value = (activeLands.size + basicsTotal).toString())
                            }
                        }
                    }
                }
            }

            // --- Grouping selector: always available, even mid-draft (D.1/D.2). F.4: migrated from
            // GroupingFlowSelector (dropdown) to ManaHubBottomSheetSelector, the same modal-sheet
            // picker CollectionScreen already uses for its sort/group pickers. ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(bottom = sp.sm)) {
                    ManaHubBottomSheetSelector(
                        icon = Icons.Default.Layers,
                        valueText = stringResource(groupingMode.displayResId),
                        items = GroupingMode.entries,
                        selectedItem = groupingMode,
                        onSelect = { groupingMode = it },
                        itemLabel = { stringResource(it.displayResId) },
                    )
                }
            }

            // --- Basic-land editor: gated behind the draft actually being finished (D.6/D.7). F.1:
            // now expandable/collapsible via the same DraftGroupSectionHeader the card groups use. ---
            if (isDraftComplete) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DraftGroupSectionHeader(
                        title = stringResource(R.string.draft_sim_basics_group_title),
                        cardCount = basicsTotal,
                        expanded = basicsExpanded,
                        onToggle = { basicsExpanded = !basicsExpanded },
                    )
                }
                if (basicsExpanded) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        DraftBasicLandsEditor(
                            targetLandCount = targetLandCount,
                            basicLandCounts = basicLandCounts,
                            onSetTargetLandCount = onSetTargetLandCount,
                            onSetBasicLandCount = onSetBasicLandCount,
                            onApplyLandSuggestions = onApplyLandSuggestions,
                            modifier = Modifier.padding(bottom = sp.md),
                        )
                    }
                }
            } else {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(R.string.draft_sim_grouping_gated_hint),
                        style = ty.labelMedium,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(bottom = sp.md),
                    )
                }
            }

            // --- Grouped pool grid (D.2/D.3/D.4): no per-card entrance animation — cards render
            // directly, which is the whole point of this phase (the old per-item AnimatedVisibility
            // + staggered LaunchedEffect delay made scrolling heavy). Group headers render directly
            // too, for the same reason. E.3: headers now match DeckStudioScreen's SectionHeader style
            // and are individually expandable/collapsible. ---
            grouped.forEach { (groupLabel, cards) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val isExpanded = groupLabel !in collapsedGroups
                    DraftGroupSectionHeader(
                        title = groupLabel,
                        cardCount = cards.size,
                        expanded = isExpanded,
                        onToggle = {
                            collapsedGroups = if (isExpanded) {
                                collapsedGroups + groupLabel
                            } else {
                                collapsedGroups - groupLabel
                            }
                        },
                    )
                }

                if (groupLabel !in collapsedGroups) {
                    itemsIndexed(
                        items = cards,
                        // E.2: keyed by the card's REAL pool position — unique per copy (fixes the
                        // old "${groupLabel}_${scryfallId}_${isFoil}_$index" key, which conflated
                        // duplicate copies since scryfallId/isFoil are identical across them).
                        key = { _, indexed -> "${groupLabel}_${indexed.index}" },
                    ) { _, indexed ->
                        val poolIndex = indexed.index
                        val draftCard = indexed.value
                        val isActive = poolIndex !in inactivePoolIndices
                        DeckCardTile(
                            draftCard = draftCard,
                            isActive = isActive,
                            rootCoordinates = rootCoordinates,
                            onToggleActive = { onToggleCardActive(poolIndex) },
                            onTap = { rect ->
                                inspectingIndex = poolIndex
                                inspectionRect = rect
                            },
                        )
                    }
                }
            }
        }

        if (isDraftComplete) {
            // E.7 fix: padding MUST come BEFORE the fixed height, matching DeckStudioScreen's own
            // primary-CTA convention (`Modifier.fillMaxWidth().padding(bottom = spacing.md).height(56.dp)`).
            // The previous order (`.height(64.dp).padding(horizontal = sp.lg).padding(bottom = sp.lg +
            // navBarBottom)`) applied BOTH paddings INSIDE the fixed 64dp box instead of as outer
            // margin — on a gesture-nav device `sp.lg + navBarBottom` alone can approach or exceed
            // 64dp, squeezing MagicCtaButton's inner content region to near-zero height and making
            // the label unreadable/clipped. Also matches the convention of every other MagicCtaButton
            // call site in the codebase (48-56dp, padding-then-height).
            MagicCtaButton(
                onClick = onSave,
                text = stringResource(R.string.draft_sim_save_deck).uppercase(),
                // G.2: reflects DraftSimViewModel.isCompletingDraft — disables the button and shows
                // a spinner while a save is in flight, instead of a rapid double-tap silently firing
                // two concurrent CompleteDraftUseCase calls (which produced duplicate decks).
                enabled = !isSaving,
                isLoading = isSaving,
                style = MagicCtaStyle.Filled,
                color = MagicCtaColor.Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sp.lg)
                    .padding(bottom = sp.lg + navBarBottom)
                    .height(56.dp),
            )
        }
    }

    // --- Tap-to-inspect overlay (D.4, fixed F.2): pages through inspectablePool (pool minus drafted
    // basics — see E.5/F.2 above) so a drafted basic land can never be paged into either. `onTap`
    // stores the RAW pool index in `inspectingIndex` (needed by `onToggleCardActive` elsewhere), so
    // the overlay's `initialIndex`/paging position must be resolved to inspectablePool's OWN index
    // space here. A drafted basic is never tappable in the first place (E.5), so this lookup should
    // always succeed for anything the user could have tapped — guarded defensively regardless.
    val inspectingPagerIndex = inspectingIndex?.let { raw -> inspectablePool.indexOfFirst { it.index == raw } }
    if (inspectingPagerIndex != null && inspectingPagerIndex >= 0) {
        MagicCardInspectionOverlay(
            items = inspectablePool,
            initialIndex = inspectingPagerIndex,
            initialRect = inspectionRect,
            isVisible = true,
            isDismissing = isDismissingInspection,
            cardExtractor = { it.value.card },
            onDismissRequest = { isDismissingInspection = true },
            onDismiss = {
                inspectingIndex = null
                isDismissingInspection = false
            },
            // E.2/F.2: actionsIndexed (not actions) — but unlike E.2's original approach, the REAL
            // pool position now comes straight from the IndexedValue item itself (`indexedItem.index`,
            // preserved through the E.5/F.2 basics filter), not from the overlay's own pager position
            // (the `_` second param) — simpler and robust regardless of how the pager's index lines up
            // with the real pool, since `items` is no longer the raw pool.
            actionsIndexed = { indexedItem, _ ->
                val realPoolIndex = indexedItem.index
                val isActive = realPoolIndex !in inactivePoolIndices
                MagicCtaButton(
                    onClick = {
                        if (!isActive) onToggleCardActive(realPoolIndex)
                        isDismissingInspection = true
                    },
                    text = stringResource(
                        if (isActive) R.string.draft_sim_keep_mainboard else R.string.draft_sim_move_mainboard,
                    ),
                    style = MagicCtaStyle.Filled,
                    color = MagicCtaColor.Primary,
                    modifier = Modifier.fillMaxWidth(),
                )
                MagicCtaButton(
                    onClick = {
                        if (isActive) onToggleCardActive(realPoolIndex)
                        isDismissingInspection = true
                    },
                    text = stringResource(
                        if (!isActive) R.string.draft_sim_keep_sideboard else R.string.draft_sim_move_sideboard,
                    ),
                    style = MagicCtaStyle.Outlined,
                    color = MagicCtaColor.Neutral,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        )
    }
}

/**
 * Localized label for [GroupingMode] (F.4 — migrated from [GroupingFlowSelector]'s hardcoded
 * English literals to [ManaHubBottomSheetSelector], which needs a [stringResource] per item like
 * `CollectionGroupingMode.displayResId` in `CollectionScreen.kt`). Kept PRIVATE and duplicated
 * rather than shared with `DeckStudioScreen`'s equivalent — same judgment call as
 * [DraftGroupSectionHeader]'s KDoc: a ~10-line enum-to-string mapping is not worth a cross-feature
 * (decks <-> draft) dependency.
 */
private val GroupingMode.displayResId: Int
    get() = when (this) {
        GroupingMode.TYPE -> R.string.deckbuilder_group_type
        GroupingMode.COLOR -> R.string.deckbuilder_group_color
        GroupingMode.COST -> R.string.deckbuilder_group_cmc
        GroupingMode.TAG -> R.string.deckbuilder_group_tag
    }

/**
 * Expandable/collapsible group header for the Deck tab's grouped card list (E.3) — visually mirrors
 * `DeckStudioScreen`'s private `SectionHeader` (title in `titleLarge`/`primaryAccent`, trailing
 * `ExpandMore` chevron rotated 180° when expanded, ≥48dp touch target, whole row clickable) plus the
 * pre-existing trailing card-count text. Duplicated locally rather than sharing the literal
 * `DeckStudioScreen` composable — it is `private fun` there and extraction into `core-ui` was judged
 * riskier than a ~20-line visual-parity duplicate for this pass (see E.9's `ManaTabRow` for where
 * extraction WAS the right call, i.e. an already-identical, already-public-surface pattern).
 */
@Composable
private fun DraftGroupSectionHeader(
    title: String,
    cardCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val rotation by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "DraftGroupSectionHeaderRotation",
    )
    val toggleDescription = if (expanded) {
        stringResource(R.string.draft_sim_collapse_group)
    } else {
        stringResource(R.string.draft_sim_expand_group)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle)
            .padding(vertical = sp.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sm),
    ) {
        Text(text = title, style = ty.titleLarge, color = mc.primaryAccent, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.weight(1f))
        Text(text = "$cardCount cards", style = ty.labelMedium, color = mc.textSecondary)
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = toggleDescription,
            tint = mc.primaryAccent,
            modifier = Modifier.graphicsLayer { rotationZ = rotation },
        )
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = ty.titleMedium, color = mc.textPrimary, fontWeight = FontWeight.Bold)
        Text(text = label, style = ty.labelSmall, color = mc.textDisabled)
    }
}

/**
 * One pool card in the Deck tab's grid (D.4): tapping the card image opens the full-pool inspection
 * overlay; the hide/show icon BELOW the image instantly toggles mainboard/sideboard, no dialog.
 * `onToggleActive`'s [IconButton] relies on Material3's default 48dp minimum touch target (CLAUDE.md
 * — every interactive element must clear 48dp) even though the visible glyph is small.
 */
@Composable
private fun DeckCardTile(
    draftCard: DraftCard,
    isActive: Boolean,
    rootCoordinates: LayoutCoordinates?,
    onToggleActive: () -> Unit,
    onTap: (Rect) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var tileRect by remember { mutableStateOf(Rect.Zero) }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .onGloballyPositioned { coords ->
                    val root = rootCoordinates
                    if (root != null && root.isAttached && coords.isAttached) {
                        tileRect = root.localBoundingBoxOf(coords)
                    }
                }
                .clip(CardShape)
                .clickable { onTap(tileRect) },
        ) {
            AsyncImage(
                model = draftCard.card.imageNormal,
                contentDescription = draftCard.card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (isActive) 1f else INACTIVE_CARD_ALPHA },
            )
        }
        IconButton(onClick = onToggleActive, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (isActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = stringResource(
                    if (isActive) R.string.draft_sim_move_to_sideboard else R.string.draft_sim_move_to_mainboard,
                ),
                tint = if (isActive) mc.textSecondary else mc.primaryAccent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Editable basic-land count for the curated deck (D.7): a stepper on the overall target, a
 * "Magic Land Suggestions" autofill (per-color-pip-weighted split via `DraftSimViewModel
 * .applyLandSuggestionAutofill`, using [com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator]
 * — E.4 unified this with `DeckStudioViewModel`'s land-suggestion engine instead of the old
 * simplistic [com.mmg.manahub.feature.draft.data.engine.ScoringDraftDeckBuilder] proportion split),
 * and a per-basic stepper. Rendered as its OWN
 * always-visible section (once the draft is complete) rather than nested inside the TYPE-grouping
 * "Lands" group — the land count must stay reachable regardless of which [GroupingMode] the user has
 * selected, since switching grouping modes must never hide save-critical curation.
 */
@Composable
private fun DraftBasicLandsEditor(
    targetLandCount: Int,
    basicLandCounts: Map<String, Int>,
    onSetTargetLandCount: (Int) -> Unit,
    onSetBasicLandCount: (String, Int) -> Unit,
    onApplyLandSuggestions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val totalBasics = basicLandCounts.values.sum()

    Surface(shape = CardShape, color = mc.surface, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(sp.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = mc.lifePositive,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(sp.sm))
                Text(
                    text = stringResource(R.string.draft_sim_basics_header, totalBasics),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(sp.sm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.draft_sim_target_land_count),
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onSetTargetLandCount(targetLandCount - 1) }) {
                    Icon(Icons.Default.Remove, null, tint = mc.textSecondary, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = "$targetLandCount",
                    style = ty.labelLarge,
                    color = mc.primaryAccent,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { onSetTargetLandCount(targetLandCount + 1) }) {
                    Icon(Icons.Default.Add, null, tint = mc.primaryAccent, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.height(sp.sm))

            MagicCtaButton(
                onClick = onApplyLandSuggestions,
                text = stringResource(R.string.draft_sim_land_suggestions),
                style = MagicCtaStyle.Outlined,
                color = MagicCtaColor.Gold,
                icon = { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(sp.sm))
            HorizontalDivider(color = mc.surfaceVariant, thickness = 0.5.dp)
            Spacer(Modifier.height(sp.sm))

            BASIC_LAND_NAMES.forEach { name ->
                val count = basicLandCounts[name] ?: 0
                DraftBasicLandRow(
                    name = name,
                    count = count,
                    onIncrement = { onSetBasicLandCount(name, count + 1) },
                    onDecrement = { onSetBasicLandCount(name, (count - 1).coerceAtLeast(0)) },
                )
            }
        }
    }
}

@Composable
private fun DraftBasicLandRow(name: String, count: Int, onIncrement: () -> Unit, onDecrement: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val manaToken = BASIC_LAND_MANA_TOKEN[name]

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.sm),
    ) {
        if (manaToken != null) ManaSymbolImage(token = manaToken, size = 20.dp)
        Text(name, style = ty.bodyMedium, color = mc.textPrimary, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrement, enabled = count > 0) {
            Icon(
                Icons.Default.Remove,
                null,
                tint = if (count > 0) mc.textSecondary else mc.textDisabled,
                modifier = Modifier.size(16.dp),
            )
        }
        Text("$count", style = ty.labelMedium, color = mc.primaryAccent, modifier = Modifier.width(20.dp))
        IconButton(onClick = onIncrement) {
            Icon(Icons.Default.Add, null, tint = mc.primaryAccent, modifier = Modifier.size(16.dp))
        }
    }
}

/** Basic-land display name → mana-symbol token, the reverse of [BasicLandCalculator.LAND_FOR_COLOR]. */
private val BASIC_LAND_MANA_TOKEN: Map<String, String> =
    BasicLandCalculator.LAND_FOR_COLOR.entries.associate { (letter, name) -> name to letter }

/**
 * Groups a draft pool by [GroupingMode], mirroring `DeckEditorComponents.groupCards`'s TYPE/COLOR/
 * COST/TAG semantics (`feature/decks/presentation/components/DeckEditorComponents.kt`), adapted to
 * [DraftCard] (whose [DraftCard.card] is always non-null, unlike
 * [com.mmg.manahub.core.model.DeckSlotEntry]). Re-added here after Phase C deleted the original
 * `PoolBottomSheet`-era grouping helper together with the sheet itself.
 *
 * E.2: operates on [IndexedValue] (the caller passes `pool.withIndex()`, optionally pre-filtered)
 * so each entry carries its ORIGINAL pool position through grouping/sorting — the stable identifier
 * [DraftSimViewModel.inactivePoolIndices] toggles by, which survives duplicate-card copies that
 * `scryfallId`-keying could not (two copies of the same card are `equals()`-identical otherwise).
 */
private fun groupDraftCards(
    cards: List<IndexedValue<DraftCard>>,
    mode: GroupingMode,
): List<Pair<String, List<IndexedValue<DraftCard>>>> {
    return when (mode) {
        GroupingMode.TYPE -> {
            val order = listOf("Creatures", "Instants", "Sorceries", "Enchantments", "Artifacts", "Planeswalkers", "Lands", "Other")
            val groups = cards.groupBy { entry ->
                val type = entry.value.card.typeLine
                when {
                    type.contains("Creature") -> "Creatures"
                    type.contains("Instant") -> "Instants"
                    type.contains("Sorcery") -> "Sorceries"
                    type.contains("Enchantment") -> "Enchantments"
                    type.contains("Artifact") -> "Artifacts"
                    type.contains("Planeswalker") -> "Planeswalkers"
                    type.contains("Land") -> "Lands"
                    else -> "Other"
                }
            }
            order.mapNotNull { label ->
                val list = groups[label]?.sortedBy { it.value.card.name } ?: emptyList()
                if (list.isEmpty()) null else label to list
            }
        }
        GroupingMode.COLOR -> {
            val order = listOf("W", "U", "B", "R", "G", "Multicolor", "Colorless", "Land")
            val groups = cards.groupBy { entry ->
                val card = entry.value.card
                if (card.typeLine.contains("Land")) "Land"
                else when (card.colors.size) {
                    0 -> "Colorless"
                    1 -> card.colors.first()
                    else -> "Multicolor"
                }
            }
            order.mapNotNull { label ->
                val list = groups[label]?.sortedBy { it.value.card.name } ?: emptyList()
                if (list.isEmpty()) null else label to list
            }
        }
        GroupingMode.COST -> {
            val nonLands = cards.filter { !BasicLandCalculator.isLand(it.value.card) }
            val groups = nonLands.groupBy { (it.value.card.cmc.toInt().coerceIn(0, 7)).toString() }
            groups.entries
                .filter { it.value.isNotEmpty() }
                .sortedBy { it.key.toInt() }
                .map { it.key to it.value.sortedBy { c -> c.value.card.name } }
        }
        GroupingMode.TAG -> {
            val tagMap = mutableMapOf<String, MutableList<IndexedValue<DraftCard>>>()
            cards.forEach { entry ->
                val tags = entry.value.card.tags + entry.value.card.userTags
                if (tags.isEmpty()) {
                    tagMap.getOrPut("Untagged") { mutableListOf() }.add(entry)
                } else {
                    tags.forEach { tag -> tagMap.getOrPut(tag.label()) { mutableListOf() }.add(entry) }
                }
            }
            tagMap.entries
                .filter { it.value.isNotEmpty() }
                .sortedByDescending { it.value.size }
                .map { it.key to it.value.sortedBy { c -> c.value.card.name } }
        }
    }
}
