package com.mmg.manahub.feature.draft.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.ManaTabItem
import com.mmg.manahub.core.ui.components.ManaTabRow
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.model.DraftCard
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimUiState
import com.mmg.manahub.feature.draft.presentation.viewmodel.DraftSimViewModel
import kotlinx.coroutines.launch

/** Single-letter color → token color for the commitment pips. */
private val COLOR_ORDER = listOf("W", "U", "B", "R", "G")

/** Sort weight for a rarity string: mythic first, common last. */
private fun rarityOrder(rarity: String): Int = when (rarity.lowercase()) {
    "mythic" -> 0
    "rare" -> 1
    "uncommon" -> 2
    "common" -> 3
    else -> 4
}

/** Which pane of the merged draft screen is showing. */
private enum class DraftSimTab { PICKS, DECK }

/**
 * How long the "Draft completed" celebration stays visible in the Picks tab (C.4) before it
 * collapses out of the tab row and the Deck tab is force-selected.
 */
private const val DRAFT_COMPLETED_CELEBRATION_MS = 1600L

/**
 * The merged draft-flow screen (Phase C): a "Picks" tab for the active draft and a "Deck" tab
 * previewing the accumulated pool / final deck, switchable via a top [TabRow] from the moment the
 * draft starts. This replaces the old two-destination Drafting → Result navigation: both panes now
 * observe the same [DraftSimUiState] and there is no cross-screen nav between them.
 *
 * Once the draft leaves DRAFTING (all packs picked), the Picks tab shows a brief "Draft completed"
 * celebration, then collapses out of the tab row, leaving only the Deck tab to review/save the deck.
 */
@Composable
fun DraftSimulatorScreen(
    onDeckSaved: (deckId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: DraftSimViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val inactivePoolIndices by viewModel.inactivePoolIndices.collectAsStateWithLifecycle()
    val basicLandCounts by viewModel.basicLandCounts.collectAsStateWithLifecycle()
    val targetLandCount by viewModel.targetLandCount.collectAsStateWithLifecycle()
    // G.5/G.6: the Pick 2+ in-progress selection now lives in the ViewModel (see
    // DraftSimViewModel.selectedCardIds' KDoc) instead of DraftingContent's local `remember` state.
    val selectedCardIds by viewModel.selectedCardIds.collectAsStateWithLifecycle()
    // G.2: drives the Deck tab's Save button loading/disabled state.
    val isCompletingDraft by viewModel.isCompletingDraft.collectAsStateWithLifecycle()
    val toastState = rememberMagicToastState()
    val coroutineScope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.draft_sim_deck_saved)

    // Pause/resume the pick timer with the screen lifecycle.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onScreenPaused()
                Lifecycle.Event.ON_RESUME -> viewModel.onScreenResumed()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Deck saved: toast + hand the new deck id to the caller (opens Deck Studio). Replaces the old
    // DraftResultScreen's own LaunchedEffect — same behavior, just relocated into the merged screen.
    LaunchedEffect(state) {
        val s = state
        if (s is DraftSimUiState.Complete) {
            toastState.show(savedMessage, MagicToastType.SUCCESS)
            onDeckSaved(s.deckId)
        }
    }

    var selectedTab by remember { mutableStateOf(DraftSimTab.PICKS) }

    // C.4: once the draft leaves DRAFTING (Building/Complete), keep the Picks tab around just long
    // enough to show a "Draft completed" celebration, then collapse it and force-select the Deck tab.
    val draftFinished = state is DraftSimUiState.Building || state is DraftSimUiState.Complete
    var picksTabCollapsed by remember { mutableStateOf(false) }
    LaunchedEffect(draftFinished) {
        if (draftFinished && !picksTabCollapsed) {
            kotlinx.coroutines.delay(DRAFT_COMPLETED_CELEBRATION_MS)
            picksTabCollapsed = true
            selectedTab = DraftSimTab.DECK
        }
    }

    val tabs = if (picksTabCollapsed) listOf(DraftSimTab.DECK) else listOf(DraftSimTab.PICKS, DraftSimTab.DECK)

    // E.10b/E.10c: exit confirmation. Drafting → cancelling loses the in-progress session entirely;
    // Building (finished, not yet saved) → leaving discards the not-yet-saved curation. Complete is
    // excluded — the LaunchedEffect above already navigates away via onDeckSaved the moment that
    // state is reached, so there is nothing left on screen to confirm exiting from.
    var showExitDialog by remember { mutableStateOf(false) }
    val handleBackRequest: () -> Unit = {
        when (state) {
            is DraftSimUiState.Drafting, is DraftSimUiState.Building -> showExitDialog = true
            else -> onBack()
        }
    }
    BackHandler(onBack = handleBackRequest)

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // E.9: the round/pick header sits ABOVE the tab strip, and only while a live pick is in
            // front of the human (Drafting) — once the draft finishes, ResultContent (Deck tab) owns
            // the header instead, avoiding a duplicate/jarring second header once Picks collapses.
            (state as? DraftSimUiState.Drafting)?.let { drafting ->
                DraftingHeaderRow(
                    state = drafting,
                    onBack = handleBackRequest,
                    onOpenPool = { selectedTab = DraftSimTab.DECK },
                )
            }

            // Tab row collapses (animated) once the Picks tab is gone — mirrors DeckStudioScreen's
            // "a single-item TabRow looks broken" convention, just animated instead of instant.
            // E.8: shared ManaTabRow (same visual system as CollectionScreen's Cards/Decks/Trades row).
            AnimatedVisibility(visible = tabs.size > 1) {
                ManaTabRow(
                    items = tabs.map { tab ->
                        ManaTabItem(
                            label = stringResource(
                                if (tab == DraftSimTab.PICKS) R.string.draft_sim_tab_picks
                                else R.string.draft_sim_tab_deck,
                            ).uppercase(),
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                        )
                    },
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    DraftSimTab.PICKS -> when (val s = state) {
                        is DraftSimUiState.Drafting -> DraftingContent(
                            state = s,
                            selectedCardIds = selectedCardIds,
                            onToggleCardSelection = viewModel::toggleCardSelection,
                            onConfirmPicks = viewModel::onConfirmPicks,
                            onAutoPick = viewModel::onAutoPick,
                        )

                        is DraftSimUiState.Error -> FullErrorState(
                            message = stringResource(R.string.draft_sim_error_generic),
                            retryLabel = stringResource(R.string.action_back),
                            onRetry = onBack,
                        )

                        else -> if (draftFinished) {
                            DraftCompletedCelebration()
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                MagicLoadingSpinner()
                            }
                        }
                    }

                    DraftSimTab.DECK -> DeckTabContent(
                        uiState = state,
                        inactivePoolIndices = inactivePoolIndices,
                        onToggleCardActive = viewModel::toggleCardActive,
                        basicLandCounts = basicLandCounts,
                        targetLandCount = targetLandCount,
                        onSetBasicLandCount = viewModel::setBasicLandCount,
                        onSetTargetLandCount = viewModel::setTargetLandCount,
                        onApplyLandSuggestions = viewModel::applyLandSuggestionAutofill,
                        onSave = viewModel::onCompleteDraft,
                        isSaving = isCompletingDraft,
                        onBack = handleBackRequest,
                    )
                }
            }
        }

        MagicToastHost(toastState)
    }

    // E.10b/E.10c: both flows share "cancel the session + leave" as their confirm action — only the
    // dialog copy differs by the draft's current status.
    if (showExitDialog) {
        val isDrafting = state is DraftSimUiState.Drafting
        MagicAlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = stringResource(
                if (isDrafting) R.string.draft_sim_cancel_draft_title else R.string.draft_sim_exit_without_saving_title,
            ),
            text = stringResource(
                if (isDrafting) R.string.draft_sim_cancel_draft_message else R.string.draft_sim_exit_without_saving_message,
            ),
            confirmLabel = stringResource(
                if (isDrafting) R.string.draft_sim_cancel_draft_confirm else R.string.draft_sim_exit_without_saving_confirm,
            ),
            confirmColor = MagicCtaColor.Error,
            onConfirm = {
                showExitDialog = false
                // F.6: onCancelDraft is now a suspend fun, awaited HERE before onBack() fires, so the
                // persisted DraftSessionEntity delete is guaranteed to have at least been attempted
                // before navigation moves the user to a screen that could otherwise observe the
                // stale, not-yet-cancelled session (see DraftSimViewModel.onCancelDraft's KDoc).
                coroutineScope.launch {
                    viewModel.onCancelDraft()
                    onBack()
                }
            },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { showExitDialog = false },
        )
    }
}

/**
 * Brief celebratory placeholder shown in the Picks tab between the draft ending (Building/Complete)
 * and the tab collapsing out (C.4). The Deck tab underneath already renders the final pool, so this
 * is purely a transitional moment — it never blocks the Deck tab's own content.
 */
@Composable
private fun DraftCompletedCelebration() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CelebrationSparkles()
        EmptyState(
            title = stringResource(R.string.draft_sim_draft_completed),
            icon = Icons.Default.CheckCircle,
        )
    }
}

/**
 * Deck tab content: previews the human's accumulated pool as it grows (Picks tab still active,
 * [DraftSimUiState.Drafting]) and hands off to the real mainboard/sideboard curation UI in
 * [ResultContent] once the draft reaches [DraftSimUiState.Building]/[DraftSimUiState.Complete]
 * (Phase D). [isDraftComplete] gates [ResultContent]'s Save button + basic-lands editor — while
 * still [DraftSimUiState.Drafting], only the live analysis + grouped card list is shown.
 */
@Composable
private fun DeckTabContent(
    uiState: DraftSimUiState,
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
) {
    val isDraftComplete = uiState is DraftSimUiState.Building || uiState is DraftSimUiState.Complete

    when (uiState) {
        is DraftSimUiState.Drafting -> ResultContent(
            state = uiState.state,
            isDraftComplete = false,
            inactivePoolIndices = inactivePoolIndices,
            onToggleCardActive = onToggleCardActive,
            basicLandCounts = basicLandCounts,
            targetLandCount = targetLandCount,
            onSetBasicLandCount = onSetBasicLandCount,
            onSetTargetLandCount = onSetTargetLandCount,
            onApplyLandSuggestions = onApplyLandSuggestions,
            onSave = onSave,
            isSaving = isSaving,
            onBack = onBack,
            errorMessage = null,
        )

        is DraftSimUiState.Building -> ResultContent(
            state = uiState.state,
            isDraftComplete = isDraftComplete,
            inactivePoolIndices = inactivePoolIndices,
            onToggleCardActive = onToggleCardActive,
            basicLandCounts = basicLandCounts,
            targetLandCount = targetLandCount,
            onSetBasicLandCount = onSetBasicLandCount,
            onSetTargetLandCount = onSetTargetLandCount,
            onApplyLandSuggestions = onApplyLandSuggestions,
            onSave = onSave,
            isSaving = isSaving,
            onBack = onBack,
            errorMessage = null,
        )

        is DraftSimUiState.Error -> ResultContent(
            state = null,
            isDraftComplete = false,
            inactivePoolIndices = inactivePoolIndices,
            onToggleCardActive = onToggleCardActive,
            basicLandCounts = basicLandCounts,
            targetLandCount = targetLandCount,
            onSetBasicLandCount = onSetBasicLandCount,
            onSetTargetLandCount = onSetTargetLandCount,
            onApplyLandSuggestions = onApplyLandSuggestions,
            onSave = onSave,
            isSaving = isSaving,
            onBack = onBack,
            errorMessage = stringResource(R.string.draft_sim_error_generic),
        )

        else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            MagicLoadingSpinner()
        }
    }
}

/**
 * The round/pick header — back button, circular timer + round/pick counter, and the pool-size badge
 * (tap switches to the Deck tab). E.9: hoisted OUT of [DraftingContent] so [DraftSimulatorScreen] can
 * render it ABOVE the tab strip instead of buried inside the Picks tab's own content area; only
 * rendered while a live pick is in front of the human ([DraftSimUiState.Drafting]) — once the draft
 * finishes, `ResultContent` (Deck tab) renders its own header instead, so the two are never shown
 * stacked/duplicated.
 */
@Composable
private fun DraftingHeaderRow(
    state: DraftSimUiState.Drafting,
    onBack: () -> Unit,
    onOpenPool: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

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

        // Integrated circular timer + Pick counter
        val secondsLeft = state.timerSecondsLeft
        val isUrgent = secondsLeft != null && secondsLeft <= 5

        val infiniteTransition = rememberInfiniteTransition(label = "timer_pulse")
        val pulseScale by if (isUrgent) {
            infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulse_scale",
            )
        } else {
            remember { mutableStateOf(1f) }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                },
        ) {
            if (secondsLeft != null) {
                MagicLoadingSpinner(
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MagicLoadingSpinner(
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = state.state.round.toString(),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                    fontSize = 10.sp,
                )
                Text(
                    text = state.state.pickNumber.toString(),
                    style = ty.titleMedium,
                    color = if (isUrgent) mc.lifeNegative else mc.textPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.width(sp.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.draft_sim_pick_counter,
                    state.state.round,
                    state.state.pickNumber,
                ).uppercase(),
                style = ty.labelMedium,
                color = mc.textSecondary,
                fontWeight = FontWeight.Bold,
            )
        }

        // Pool size badge — tapping it now switches to the Deck tab (replaces PoolBottomSheet).
        Surface(
            onClick = onOpenPool,
            shape = ChipShape,
            color = mc.primaryAccent.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.3f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = sp.md, vertical = sp.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Default.Style,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = state.poolSize.toString(),
                    style = ty.labelMedium,
                    color = mc.primaryAccent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun DraftingContent(
    state: DraftSimUiState.Drafting,
    // G.5/G.6: the in-progress Pick 2+ selection is now owned by DraftSimViewModel (see its
    // selectedCardIds KDoc) instead of local `remember` state, so the ViewModel's own timer-expiry
    // handler can see and complete a partial selection instead of silently discarding it.
    selectedCardIds: List<String>,
    onToggleCardSelection: (String) -> Unit,
    onConfirmPicks: (List<String>) -> Unit,
    onAutoPick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    val navBarBottom = WindowInsetsBottom()

    // The currently zoomed card (Pick-1 mode only; null = no zoom sheet).
    var selectedCard by remember { mutableStateOf<DraftCard?>(null) }
    var isSuggestionVisible by remember(state.state.pickNumber, state.state.round) { mutableStateOf(false) }

    val picksPerTurn = state.state.config.picksPerTurn

    // Rarity-sorted pack (mythic → common, then alphabetical) for predictable scanning.
    val sortedPack = remember(state.currentPack) {
        state.currentPack.sortedWith(
            compareBy({ rarityOrder(it.card.rarity) }, { it.card.name }),
        )
    }

    // Target number of picks for THIS turn: normally picksPerTurn, but the last turn of a round may
    // have fewer cards left in the pack than picksPerTurn (odd pack size) — see DraftState.picksTakenInTurn.
    val confirmTarget = minOf(picksPerTurn, sortedPack.size)

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Pack grid ────────────────────────────────────────────────────────────
        if (sortedPack.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.draft_sim_empty_pack),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        } else {
            AnimatedContent(
                targetState = sortedPack,
                transitionSpec = {
                    (slideInHorizontally { it } + fadeIn())
                        .togetherWith(slideOutHorizontally { -it } + fadeOut())
                },
                label = "pack_transition",
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { pack ->
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(sp.md),
                    horizontalArrangement = Arrangement.spacedBy(sp.sm),
                    verticalArrangement = Arrangement.spacedBy(sp.sm),
                ) {
                    // E.1: no per-card entrance animation — cards render directly. The old
                    // per-item AnimatedVisibility + staggered LaunchedEffect delay made the pack
                    // grid's scroll heavy/laggy, exactly the complaint D.2/D.3 already fixed for the
                    // Deck tab's grid; this brings the Picks tab's pack grid in line with it.
                    itemsIndexed(
                        items = pack,
                        key = { index, it -> "${it.card.scryfallId}:${it.isFoil}:$index" },
                    ) { _, draftCard ->
                        DraftPackCard(
                            draftCard = draftCard,
                            isSuggested = isSuggestionVisible && draftCard.card.scryfallId == state.suggestedPickId,
                            isSelected = picksPerTurn > 1 && draftCard.card.scryfallId in selectedCardIds,
                            onTap = {
                                if (picksPerTurn <= 1) {
                                    selectedCard = draftCard
                                } else {
                                    onToggleCardSelection(draftCard.card.scryfallId)
                                }
                            },
                        )
                    }
                }
            }
        }

        // ── Bottom action bar ─────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = sp.lg, vertical = sp.sm)
                .padding(bottom = navBarBottom),
            verticalArrangement = Arrangement.spacedBy(sp.xs)
        ) {

            // Pick 2+ mode: primary CTA to commit the turn's selection. Disabled until the human has
            // selected exactly confirmTarget cards (picksPerTurn, or fewer on an odd-final-turn pack).
            if (picksPerTurn > 1) {
                val confirmEnabled = confirmTarget > 0 && selectedCardIds.size == confirmTarget
                Button(
                    // G.6: no longer clears selectedCardIds here — DraftSimViewModel.onConfirmPicks
                    // now only clears it once the pick is actually confirmed (DataResult.Success),
                    // so a rejected/no-op confirm (e.g. isPickInFlight racing a near-simultaneous
                    // auto-pick) never silently drops the user's selection.
                    onClick = { onConfirmPicks(selectedCardIds) },
                    enabled = confirmEnabled,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mc.primaryAccent,
                        disabledContainerColor = mc.surfaceVariant,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.draft_sim_confirm_n_picks, confirmTarget),
                        style = ty.labelLarge,
                        color = if (confirmEnabled) mc.background else mc.textDisabled,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Suggested pick shortcut — toggles the yellow border on the engine's recommended card.
            val suggestedCard = state.suggestedPickId?.let { id ->
                sortedPack.firstOrNull { it.card.scryfallId == id }
            }
            if (suggestedCard != null) {
                Button(
                    onClick = { isSuggestionVisible = !isSuggestionVisible },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = ButtonShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mc.surface,
                        contentColor = mc.textPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = mc.goldMtg,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(sp.sm))
                        Text(
                            text = stringResource(R.string.draft_sim_suggested_pick),
                            style = ty.labelLarge,
                        )
                    }
                }
            }

            // Auto-pick button.
            Button(
                onClick = onAutoPick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.surface,
                    contentColor = mc.textPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Bolt,
                        contentDescription = null,
                        tint = mc.goldMtg,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(sp.sm))
                    Text(
                        text = stringResource(R.string.draft_sim_auto_pick),
                        style = ty.labelLarge,
                    )
                }
            }
        }
    }

    // ── Modals ────────────────────────────────────────────────────────────────
    // Pick-1 mode only: tapping a card opens the zoom sheet instead of toggling selection.
    selectedCard?.let { card ->
        CardZoomSheet(
            draftCard = card,
            onConfirmPick = {
                val id = card.card.scryfallId
                selectedCard = null
                onConfirmPicks(listOf(id))
            },
            onDismiss = { selectedCard = null },
        )
    }
}

/**
 * A single card in the draft pack. Pick-1 mode: tap opens the zoom sheet (no direct pick). Pick 2+
 * mode: tap toggles [isSelected] (handled by the caller). Long-press flips a double-faced card to
 * its back image while held. A suggested card is outlined in gold; a selected card gets an inset
 * accent ring + checkmark badge so both indicators can show at once.
 */
@Composable
private fun DraftPackCard(
    draftCard: DraftCard,
    isSuggested: Boolean,
    isSelected: Boolean,
    onTap: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val card = draftCard.card
    val hasBack = card.imageBackNormal != null
    var showingBack by remember(card.scryfallId) { mutableStateOf(false) }

    // Scale animation on press
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        label = "card_scale",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shadowElevation = 4.dp.toPx()
                shape = CardShape
                clip = true
            }
            .clip(CardShape)
            .then(
                if (isSuggested) Modifier.border(2.dp, mc.goldMtg, CardShape) else Modifier,
            )
            .then(
                // Inset from the suggested-pick ring so both can render simultaneously (the engine's
                // suggested card can also be one of the human's Pick-2 selections).
                if (isSelected) Modifier.padding(3.dp).border(3.dp, mc.primaryAccent, CardShape) else Modifier,
            )
            .pointerInput(card.scryfallId, hasBack) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { if (hasBack) showingBack = true },
                    onPress = {
                        isPressed = true
                        try {
                            if (hasBack) {
                                tryAwaitRelease()
                                showingBack = false
                            } else {
                                awaitRelease()
                            }
                        } finally {
                            isPressed = false
                        }
                    },
                )
            },
    ) {
        AsyncImage(
            model = if (showingBack) card.imageBackNormal else card.imageNormal,
            contentDescription = card.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        // Foil shimmer overlay - animated
        if (draftCard.isFoil) {
            val shimmerTransition = rememberInfiniteTransition(label = "foil_shimmer")
            val offset by shimmerTransition.animateFloat(
                initialValue = -500f,
                targetValue = 500f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2500),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "shimmer_offset",
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0x11FFFFFF),
                                Color(0x33FF00FF),
                                Color(0x3300FFFF),
                                Color(0x11FFFFFF),
                                Color.Transparent,
                            ),
                            start = androidx.compose.ui.geometry.Offset(offset, offset),
                            end = androidx.compose.ui.geometry.Offset(offset + 300f, offset + 300f),
                        ),
                    ),
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(20.dp)
                    .background(mc.background, CircleShape),
            )
        }
    }
}

/**
 * Full-screen zoom sheet for a single draft card. Shows the full card image (tap to flip a DFC),
 * tier/foil badges, name, mana cost, type line, oracle text, and a primary "Pick this card" action.
 * Only mounted in Pick-1 mode (see [DraftingContent]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardZoomSheet(
    draftCard: DraftCard,
    onConfirmPick: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val card = draftCard.card
    val hasBack = !card.imageBackNormal.isNullOrBlank()
    var showingBack by remember(card.scryfallId) { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (showingBack) -180f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "CardFlip",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = mc.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Close button row
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = sp.sm, vertical = sp.xs),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = mc.textSecondary)
                }
            }

            // Card image with flip
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(0.716f)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .clip(CardShape)
                    .then(
                        if (hasBack) Modifier.clickable { showingBack = !showingBack } else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = card.imageNormal,
                    contentDescription = card.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (rotation >= -90f) 1f else 0f },
                )
                if (hasBack) {
                    AsyncImage(
                        model = card.imageBackNormal,
                        contentDescription = card.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                rotationY = 180f
                                alpha = if (rotation < -90f) 1f else 0f
                            },
                    )
                }
            }

            Spacer(Modifier.height(sp.md))

            // Badges row (tier + foil)
            if (draftCard.tierRating != null || draftCard.isFoil) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(sp.xs),
                    modifier = Modifier.padding(horizontal = sp.lg),
                ) {
                    draftCard.tierRating?.let { rating ->
                        Surface(shape = ChipShape, color = mc.goldMtg.copy(alpha = 0.18f)) {
                            Text(
                                text = rating,
                                style = ty.labelMedium,
                                color = mc.goldMtg,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = sp.sm, vertical = sp.xxs),
                            )
                        }
                    }
                    if (draftCard.isFoil) {
                        Surface(shape = ChipShape, color = mc.primaryAccent.copy(alpha = 0.18f)) {
                            Text(
                                text = "Foil",
                                style = ty.labelMedium,
                                color = mc.primaryAccent,
                                modifier = Modifier.padding(horizontal = sp.sm, vertical = sp.xxs),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(sp.sm))
            }

            // Card name + mana cost
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = sp.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayName = card.printedName?.takeIf { it.isNotBlank() } ?: card.name
                    CardName(
                        name = displayName,
                        showFrontOnly = true,
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    card.manaCost?.let { ManaCostImages(manaCost = it, symbolSize = 18.dp) }
                }
                val typeLine = card.printedTypeLine?.takeIf { it.isNotBlank() } ?: card.typeLine
                Text(typeLine, style = ty.labelMedium, color = mc.textSecondary)
                Spacer(Modifier.height(sp.xs))
                HorizontalDivider(color = mc.surfaceVariant)
                Spacer(Modifier.height(sp.xs))
                val oracleText = card.oracleText?.takeIf { it.isNotBlank() } ?: card.printedText ?: ""
                if (oracleText.isNotBlank()) {
                    OracleText(text = oracleText, style = ty.bodySmall)
                }
            }

            Spacer(Modifier.height(sp.lg))

            // Pick button
            Button(
                onClick = onConfirmPick,
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(52.dp),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
            ) {
                Text(
                    text = stringResource(R.string.draft_sim_confirm_pick),
                    style = ty.labelLarge,
                    color = mc.background,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(sp.lg))
        }
    }
}

/** Color commitment pips, sized proportionally to the seat's accumulated weights. */
@Composable
private fun ColorPips(commitment: Map<String, Float>) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing

    val colorFor: (String) -> Color = {
        when (it) {
            "W" -> mc.manaW; "U" -> mc.manaU; "B" -> mc.manaB
            "R" -> mc.manaR; "G" -> mc.manaG; else -> mc.manaC
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(sp.xxs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        COLOR_ORDER.forEach { letter ->
            val weight = commitment[letter] ?: 0f
            val alpha = if (weight <= 0f) 0.15f else 1f
            val size = if (weight > 10f) 14.dp else if (weight > 0f) 12.dp else 10.dp

            Box(
                modifier = Modifier
                    .size(size)
                    .clip(ChipShape)
                    .background(colorFor(letter).copy(alpha = alpha))
                    .then(
                        if (weight > 0f) Modifier.border(0.5.dp, Color.White.copy(alpha = 0.3f), ChipShape)
                        else Modifier,
                    ),
            )
        }
    }
}

@Composable
private fun WindowInsetsBottom() =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
