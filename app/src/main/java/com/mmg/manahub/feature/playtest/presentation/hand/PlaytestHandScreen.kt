package com.mmg.manahub.feature.playtest.presentation.hand

import android.R.attr.contentDescription
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCardInspectionOverlay
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.model.HandSnapshot
import com.mmg.manahub.core.model.PlaytestPhase
import com.mmg.manahub.core.model.PlaytestSetup
import com.mmg.manahub.core.model.computeProtectedIndices
import com.mmg.manahub.core.model.computeRequiredBottomCount
import com.mmg.manahub.feature.playtest.presentation.battle.BattlefieldContent
import com.mmg.manahub.feature.playtest.presentation.components.BottomNSelector
import com.mmg.manahub.feature.playtest.presentation.components.CommandZoneArea
import com.mmg.manahub.feature.playtest.presentation.components.CustomHandSheet
import com.mmg.manahub.feature.playtest.presentation.components.PlaytestHandCard
import com.mmg.manahub.feature.playtest.presentation.components.PlaytestSaveSheet
import com.mmg.manahub.feature.playtest.presentation.components.PlaytestSurveySheet

private data class InspectionSession(
    val card: Card,
    val initialRect: Rect,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaytestHandScreen(
    setup: PlaytestSetup,
    onBack: () -> Unit,
    viewModel: PlaytestHandViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val toastState = rememberMagicToastState()

    // Pre-resolved strings used inside the (non-composable) event collector.
    val infoLibraryEmpty = stringResource(R.string.playtest_battle_library_empty)
    val saveSuccessMessage = stringResource(R.string.playtest_save_success)

    // Initialise the ViewModel with the setup once.
    LaunchedEffect(setup) {
        viewModel.initWithSetup(setup)
    }

    // Consume one-shot events from the buffered Channel. Each event is delivered exactly
    // once; collecting the Flow directly (not via collectAsStateWithLifecycle) avoids the
    // equality-collapse / lost-event problems a StateFlow would have.
    LaunchedEffect(Unit) {
        viewModel.events.collect { e ->
            when (e) {
                is PlaytestHandEvent.SaveSuccess -> {
                    toastState.show(saveSuccessMessage, MagicToastType.SUCCESS)
                    onBack()
                }

                is PlaytestHandEvent.NavigateBack -> {
                    onBack()
                }

                is PlaytestHandEvent.ShowError -> {
                    toastState.show(e.message, MagicToastType.ERROR)
                }

                is PlaytestHandEvent.ShowInfo -> {
                    // Resolve the carried string-resource name on the UI layer so the
                    // ViewModel stays free of Android resource references. Unknown keys
                    // fall back to a generic message — never show a raw resource key.
                    val message = when (e.stringResName) {
                        "playtest_battle_library_empty" -> infoLibraryEmpty
                        else -> infoLibraryEmpty
                    }
                    toastState.show(message, MagicToastType.INFO)
                }
            }
        }
    }

    var inspectionSession by remember { mutableStateOf<InspectionSession?>(null) }
    var isDismissingInspection by remember { mutableStateOf(false) }
    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val isPlayPhase = uiState.phase == PlaytestPhase.PLAY

    // ── BackHandler mutual-exclusion invariant ────────────────────────────────
    // There are two BackHandlers on this screen and they must NEVER be enabled at the
    // same time (the topmost-registered enabled handler wins, silently swallowing the
    // other's intent):
    //   1) PLAY-phase handler → opens the End-Test confirmation.
    //   2) BottomNSelector handler → dismisses the selector overlay.
    // These are naturally exclusive by state: the BottomNSelector lives ONLY in the
    // MULLIGAN phase (it is the bridge into PLAY — see onConfirmBottomN → enterPlayPhase),
    // so once `phase == PLAY` the selector can no longer be shown. We still pass explicit
    // `enabled` flags so that even if a future refactor let the states overlap, at most one
    // handler is ever active.
    val isBottomNSelectorVisible = uiState.showBottomNSelector && uiState.snapshot != null

    // In PLAY phase, system Back must ask for confirmation instead of popping.
    // Disabled while the BottomNSelector overlay is up so the two never compete.
    BackHandler(enabled = isPlayPhase && !isBottomNSelectorVisible) {
        viewModel.requestEndTest()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxCoords = it }
    ) {
        Scaffold(
            containerColor = mc.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                        ) {
                            Text(
                                text = if (isPlayPhase) {
                                    stringResource(R.string.playtest_battle_title)
                                } else {
                                    stringResource(
                                        R.string.playtest_hand_title,
                                        uiState.snapshot?.hand?.size ?: 0,
                                    )
                                },
                                style = ty.titleMedium,
                                color = mc.textPrimary,
                            )
                            val mulligans = uiState.snapshot?.mulligansUsed ?: 0
                            if (!isPlayPhase && mulligans > 0) {
                                Surface(
                                    color = mc.primaryAccent.copy(alpha = 0.15f),
                                    shape = ChipShape,
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.playtest_mulligan_chip,
                                            mulligans
                                        ),
                                        style = ty.labelSmall,
                                        color = mc.primaryAccent,
                                        modifier = Modifier.padding(
                                            horizontal = MaterialTheme.spacing.sm,
                                            vertical = MaterialTheme.spacing.xxs,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { if (isPlayPhase) viewModel.requestEndTest() else onBack() },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = mc.textPrimary,
                            )
                        }
                    },
                    actions = {
                        // "Custom your hand" — MULLIGAN phase only. Once the battlefield exists
                        // (PLAY phase) there is no drawn hand left to force cards into, so the
                        // action is hidden entirely rather than shown-disabled.
                        if (!isPlayPhase) {
                            IconButton(onClick = viewModel::onOpenCustomHandSheet) {
                                Icon(
                                    Icons.Default.Style,
                                    contentDescription = stringResource(R.string.playtest_custom_hand_action),
                                    tint = mc.textPrimary,
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.background),
                )
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                when {
                    uiState.isLoading -> MagicLoadingSpinner(
                        modifier = Modifier.align(Alignment.Center),
                    )

                    uiState.errorMessage != null -> FullErrorState(
                        message = uiState.errorMessage!!,
                        retryLabel = stringResource(R.string.action_back),
                        onRetry = onBack,
                    )

                    isPlayPhase && uiState.battlefield != null -> BattlefieldContent(
                        setup = setup,
                        battlefield = uiState.battlefield!!,
                        onDrawCard = viewModel::drawCard,
                        onMoveCard = viewModel::moveCard,
                        onReorderHand = viewModel::onReorderHand,
                        onToggleTap = viewModel::toggleTap,
                        onUpdateCardOffset = viewModel::updateCardOffset,
                        onInspectCommander = { card, rect ->
                            inspectionSession = InspectionSession(card, rect)
                        },
                    )

                    uiState.snapshot != null -> HandContent(
                        setup = setup,
                        snapshot = uiState.snapshot!!,
                        onCardClick = { card, rect ->
                            inspectionSession = InspectionSession(card, rect)
                        },
                        onInspectCommander = { card, rect ->
                            inspectionSession = InspectionSession(card, rect)
                        },
                        onReorder = viewModel::onReorderHand,
                        onRedraw = viewModel::onRedraw,
                        onKeep = viewModel::onKeep,
                        onMulligan = viewModel::onMulligan,
                        boxCoords = boxCoords,
                        // Mulligan is disabled once the next mulligan's required bottom-N count
                        // would reach/exceed drawCount (the ≥1-card kept-hand floor). This mirrors
                        // PlaytestHandViewModel.onMulligan's own guard exactly, via the same
                        // shared computeRequiredBottomCount() — the two must never drift, or the
                        // button could show enabled while the tap silently no-ops (or vice versa).
                        canMulligan = uiState.snapshot!!.hand.size > 1 &&
                                computeRequiredBottomCount(
                                    setup.drawCount,
                                    setup.startCount,
                                    uiState.snapshot!!.mulligansUsed + 1,
                                ) < setup.drawCount,
                    )
                }
            }
        }

        // Bottom-N overlay (rendered on top of everything).
        if (isBottomNSelectorVisible) {
            // Intercept system Back so it dismisses the selector instead of popping the screen.
            // Explicitly enabled only while the overlay is visible (see the mutual-exclusion
            // invariant above) so it never competes with the PLAY-phase BackHandler.
            BackHandler(enabled = true) { viewModel.onDismissBottomN() }
            val bottomNSnapshot = uiState.snapshot!!
            BottomNSelector(
                hand = bottomNSnapshot.hand,
                requiredCount = computeRequiredBottomCount(
                    setup.drawCount, setup.startCount, bottomNSnapshot.mulligansUsed,
                ),
                selectedIndices = uiState.selectedBottomIndices,
                disabledIndices = computeProtectedIndices(
                    bottomNSnapshot.hand,
                    uiState.customHandSelection
                ),
                onToggle = viewModel::toggleBottomSelection,
                onConfirm = viewModel::onConfirmBottomN,
            )
        }

        MagicToastHost(
            state = toastState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Inspection overlay
        inspectionSession?.let { session ->
            MagicCardInspectionOverlay(
                card = session.card,
                initialRect = session.initialRect,
                isVisible = true,
                isDismissing = isDismissingInspection,
                onDismissRequest = { isDismissingInspection = true },
                onDismiss = {
                    inspectionSession = null
                    isDismissingInspection = false
                },
            )
        }
    }

    // "Custom your hand" sheet (MULLIGAN phase only — see the topbar action gating above).
    if (uiState.showCustomHandSheet) {
        CustomHandSheet(
            availableCards = uiState.availableCards,
            selection = uiState.customHandSelection,
            drawCount = setup.drawCount,
            onCountChange = viewModel::onSetCustomHandCount,
            onDismiss = viewModel::onDismissCustomHandSheet,
        )
    }

    // Save sheet.
    if (uiState.showSaveSheet && uiState.setup != null && uiState.snapshot != null) {
        PlaytestSaveSheet(
            setup = uiState.setup!!,
            snapshot = uiState.snapshot!!,
            isSaving = uiState.isSaving,
            onSaveWithoutSurvey = viewModel::onSaveWithoutSurvey,
            onSaveAndSurvey = viewModel::onSaveAndOpenSurvey,
            onDiscard = viewModel::onDiscard,
            onDismiss = viewModel::onDismissSaveSheet,
        )
    }

    // Survey sheet.
    if (uiState.showSurveySheet && uiState.snapshot != null) {
        PlaytestSurveySheet(
            handCards = uiState.snapshot!!.hand,
            onFinish = { answers, types, refs ->
                viewModel.onSurveyFinished(answers, types, refs)
            },
            onDismiss = viewModel::onDismissSurveySheet,
        )
    }

    // End Test confirmation (PLAY phase). Confirming navigates back WITHOUT saving —
    // the battlefield is purely ephemeral, so there is nothing to persist.
    if (uiState.showEndTestConfirm) {
        MagicAlertDialog(
            onDismissRequest = viewModel::dismissEndTest,
            title = stringResource(R.string.playtest_battle_confirm_end_title),
            text = stringResource(R.string.playtest_battle_confirm_end_body),
            confirmLabel = stringResource(R.string.playtest_battle_confirm_end_confirm),
            onConfirm = viewModel::confirmEndTest,
            dismissLabel = stringResource(R.string.playtest_battle_confirm_end_cancel),
            onDismiss = viewModel::dismissEndTest,
            confirmColor = MagicCtaColor.Error,
        )
    }
}

// ── Hand content — orientation-aware ─────────────────────────────────────────

@Composable
private fun HandContent(
    setup: PlaytestSetup,
    snapshot: HandSnapshot,
    onCardClick: (Card, Rect) -> Unit,
    onInspectCommander: (Card, Rect) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onRedraw: () -> Unit,
    onKeep: () -> Unit,
    onMulligan: () -> Unit,
    canMulligan: Boolean,
    boxCoords: LayoutCoordinates?,
) {
    val orientation = LocalConfiguration.current.orientation
    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        LandscapeHandContent(
            setup = setup,
            snapshot = snapshot,
            onCardClick = onCardClick,
            onInspectCommander = onInspectCommander,
            onReorder = onReorder,
            onRedraw = onRedraw,
            onKeep = onKeep,
            onMulligan = onMulligan,
            canMulligan = canMulligan,
            boxCoords = boxCoords,
        )
    } else {
        PortraitHandContent(
            setup = setup,
            snapshot = snapshot,
            onCardClick = onCardClick,
            onInspectCommander = onInspectCommander,
            onReorder = onReorder,
            onRedraw = onRedraw,
            onKeep = onKeep,
            onMulligan = onMulligan,
            canMulligan = canMulligan,
            boxCoords = boxCoords,
        )
    }
}

// ── Portrait layout ───────────────────────────────────────────────────────────

@Composable
private fun PortraitHandContent(
    setup: PlaytestSetup,
    snapshot: HandSnapshot,
    onCardClick: (Card, Rect) -> Unit,
    onInspectCommander: (Card, Rect) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onRedraw: () -> Unit,
    onKeep: () -> Unit,
    onMulligan: () -> Unit,
    canMulligan: Boolean,
    boxCoords: LayoutCoordinates?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Column(modifier = Modifier.fillMaxSize()) {
        // Commander zone strip.
        val commander = setup.commanderCard
        if (commander != null) {
            var cardRect by remember { mutableStateOf(Rect.Zero) }

            CommandZoneArea(
                commanderCard = commander,
                onClick = { onInspectCommander(commander, cardRect) },
                modifier = Modifier
                    .padding(horizontal = sp.md, vertical = sp.xs)
                    .onGloballyPositioned { coords ->
                        if (boxCoords != null && boxCoords.isAttached && coords.isAttached) {
                            // The image is inside, but the strip is a good target.
                            // Ideally we'd get the image rect specifically if we wanted precisely
                            // that fly-out, but the strip is fine for now or we can use a thumb rect.
                            cardRect = boxCoords.localBoundingBoxOf(coords)
                        }
                    },
            )
        }

        // London mulligan helper text.
        if (snapshot.mulligansUsed > 0) {
            Text(
                text = stringResource(R.string.playtest_mulligan_keep_hint, snapshot.mulligansUsed),
                style = ty.bodySmall,
                color = mc.textSecondary,
                modifier = Modifier
                    .padding(horizontal = sp.lg)
                    .padding(bottom = sp.xs),
            )
        }

        // Hand area — animated on snapshot id change.
        AnimatedContent(
            targetState = snapshot.id,
            transitionSpec = {
                (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 4 }) togetherWith
                        (fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 4 })
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label = "HandAnimation",
        ) { snapshotId ->
            HandGrid(
                hand = snapshot.hand,
                snapshotId = snapshotId,
                onCardClick = onCardClick,
                onReorder = onReorder,
                boxCoords = boxCoords,
            )
        }

        // Bottom action bar.
        BottomActionBar(
            onRedraw = onRedraw,
            onKeep = onKeep,
            onMulligan = onMulligan,
            canMulligan = canMulligan,
        )
    }
}

// ── Landscape layout ──────────────────────────────────────────────────────────

/**
 * Landscape variant: CommandZone + hint on the left, fan in the center, action buttons on the right.
 * The [SideActionBar] stacks the three buttons vertically to fit the narrow right column.
 */
@Composable
private fun LandscapeHandContent(
    setup: PlaytestSetup,
    snapshot: HandSnapshot,
    onCardClick: (Card, Rect) -> Unit,
    onInspectCommander: (Card, Rect) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onRedraw: () -> Unit,
    onKeep: () -> Unit,
    onMulligan: () -> Unit,
    canMulligan: Boolean,
    boxCoords: LayoutCoordinates?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Row(modifier = Modifier.fillMaxSize()) {
        // LEFT: commander zone + mulligan hint. The 160dp rail width is a fixed layout
        // constant (not an 8dp-grid spacing token).
        Column(
            modifier = Modifier
                .width(LANDSCAPE_RAIL_WIDTH)
                .fillMaxHeight()
                .padding(horizontal = sp.sm, vertical = sp.sm),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            val cmdr = setup.commanderCard
            if (cmdr != null) {
                var cardRect by remember { mutableStateOf(Rect.Zero) }

                CommandZoneArea(
                    commanderCard = cmdr,
                    onClick = { onInspectCommander(cmdr, cardRect) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            if (boxCoords != null && boxCoords.isAttached && coords.isAttached) {
                                cardRect = boxCoords.localBoundingBoxOf(coords)
                            }
                        },
                )
            }
            if (snapshot.mulligansUsed > 0) {
                Text(
                    text = stringResource(
                        R.string.playtest_mulligan_keep_hint,
                        snapshot.mulligansUsed
                    ),
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(top = sp.sm),
                )
            }
        }

        // CENTER: hand fan — takes all remaining horizontal space.
        AnimatedContent(
            targetState = snapshot.id,
            transitionSpec = {
                (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 4 }) togetherWith
                        (fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 4 })
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            label = "HandAnimationLandscape",
        ) { snapshotId ->
            HandGrid(
                hand = snapshot.hand,
                snapshotId = snapshotId,
                onCardClick = onCardClick,
                onReorder = onReorder,
                boxCoords = boxCoords,
            )
        }

        // RIGHT: vertical action bar.
        SideActionBar(
            onRedraw = onRedraw,
            onKeep = onKeep,
            onMulligan = onMulligan,
            canMulligan = canMulligan,
            modifier = Modifier
                .width(LANDSCAPE_ACTION_RAIL_WIDTH)
                .fillMaxHeight(),
        )
    }
}

// ── Hand Grid ───────────────────────────────────────────────────────────────

@Composable
private fun HandGrid(
    hand: List<Card>,
    snapshotId: Int,
    onCardClick: (Card, Rect) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    boxCoords: LayoutCoordinates?,
) {
    if (hand.isEmpty()) return

    val sp = MaterialTheme.spacing
    val haptic = LocalHapticFeedback.current

    // Tracks each grid item's current on-screen rect (relative to boxCoords), keyed by index —
    // mirrors the position-tracking drop-resolution approach used for the PLAY-phase hand fan
    // in BattlefieldContent.HandStrip. Remembered per snapshotId so a mulligan/redraw (which
    // mints a fresh snapshot id) starts with a clean map; a same-snapshot reorder keeps the map
    // (indices stay valid — reordering doesn't change the index range).
    val itemRects = remember(snapshotId) { mutableStateMapOf<Int, Rect>() }
    var dragIndex by remember(snapshotId) { mutableStateOf<Int?>(null) }
    var dragStartCenter by remember(snapshotId) { mutableStateOf(Offset.Zero) }
    var dragDelta by remember(snapshotId) { mutableStateOf(Offset.Zero) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 110.dp),
        contentPadding = PaddingValues(sp.md),
        horizontalArrangement = Arrangement.spacedBy(sp.sm),
        verticalArrangement = Arrangement.spacedBy(sp.sm),
        modifier = Modifier.fillMaxSize()
    ) {
        gridItemsIndexed(hand, key = { index, _ -> "hand_${snapshotId}_${index}" }) { index, card ->
            var cardRect by remember { mutableStateOf(Rect.Zero) }
            val isDragging = dragIndex == index

            PlaytestHandCard(
                card = card,
                width = Dp.Unspecified,
                onClick = { onCardClick(card, cardRect) },
                isDragging = isDragging,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(63f / 88f)
                    // Lift + follow the finger while dragging; snaps back to its grid slot the
                    // instant dragIndex clears (drag end triggers the reorder, if any, in the
                    // same recomposition).
                    .zIndex(if (isDragging) 100f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationX = dragDelta.x
                            translationY = dragDelta.y
                        }
                    }
                    .onGloballyPositioned { coords ->
                        if (boxCoords != null && boxCoords.isAttached && coords.isAttached) {
                            cardRect = boxCoords.localBoundingBoxOf(coords)
                            itemRects[index] = cardRect
                        }
                    }
                    // Drag gesture is chained alongside PlaytestHandCard's own internal tap
                    // detector (same coexistence pattern as BattlefieldContent.DraggableFieldCard
                    // stacking a long-press-drag pointerInput next to a tap pointerInput).
                    .pointerInput(snapshotId, index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                val rect = itemRects[index]
                                if (rect == null || rect == Rect.Zero) return@detectDragGesturesAfterLongPress
                                dragIndex = index
                                dragStartCenter = rect.center
                                dragDelta = Offset.Zero
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                dragDelta += amount
                                change.consume()
                            },
                            onDragEnd = {
                                val from = dragIndex
                                if (from != null) {
                                    val pointer = dragStartCenter + dragDelta
                                    // Nearest tracked item center to the drop point.
                                    val to = itemRects.entries.minByOrNull { (_, rect) ->
                                        val dx = rect.center.x - pointer.x
                                        val dy = rect.center.y - pointer.y
                                        dx * dx + dy * dy
                                    }?.key ?: from
                                    if (to != from) onReorder(from, to)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                dragIndex = null
                                dragDelta = Offset.Zero
                            },
                            onDragCancel = {
                                dragIndex = null
                                dragDelta = Offset.Zero
                            },
                        )
                    }
            )
        }
    }
}
// ── Bottom action bar (portrait) ──────────────────────────────────────────────

@Composable
private fun BottomActionBar(
    onRedraw: () -> Unit,
    onKeep: () -> Unit,
    onMulligan: () -> Unit,
    canMulligan: Boolean,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing

    Surface(
        color = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Subtle top border
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(mc.surfaceVariant)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = sp.lg, vertical = sp.md),
                horizontalArrangement = Arrangement.spacedBy(sp.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Mulligan (Left)
                FilledTonalIconButton(
                    onClick  = onMulligan,
                    enabled  = canMulligan,
                    shape    = CircleShape,
                    colors   = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = mc.secondaryAccent.copy(alpha = 0.15f),
                        contentColor   = mc.secondaryAccent,
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = stringResource(R.string.playtest_action_mulligan),
                    )
                }

                // Keep/Start (Center)
                MagicCtaButton(
                    text = stringResource(R.string.playtest_action_start),
                    onClick = onKeep,
                    color = MagicCtaColor.Primary,
                    modifier = Modifier.weight(1.3f)
                )

                // New hand (Right)
                FilledTonalIconButton(
                    onClick  = onRedraw,
                    shape    = CircleShape,
                    colors   = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = mc.surfaceVariant,
                        contentColor   = mc.textSecondary,
                    ),
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        Icons.Default.Replay,
                        contentDescription = stringResource(R.string.playtest_action_new_hand),
                    )
                }
            }
        }
    }
}

// ── Side action bar (landscape) ───────────────────────────────────────────────

/**
 * Vertical action bar for the landscape layout — same three actions as [BottomActionBar]
 * stacked vertically to fit the 110 dp right column.
 */
@Composable
private fun SideActionBar(
    onRedraw: () -> Unit,
    onKeep: () -> Unit,
    onMulligan: () -> Unit,
    canMulligan: Boolean,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing

    Surface(
        color = mc.background,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .navigationBarsPadding()
                .padding(horizontal = sp.sm, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.md, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // New hand
            IconButton(onClick = onRedraw) {
                Icon(
                    Icons.Default.Replay,

                    tint = mc.primaryAccent,
                    contentDescription = stringResource(R.string.playtest_action_new_hand)
                )
            }
        }
        // Keep
        MagicCtaButton(
            text = "START",
            onClick = onKeep,
            color = MagicCtaColor.Primary,
            modifier = Modifier.fillMaxWidth()
        )

        IconButton(onClick = onMulligan) {
            Icon(
                Icons.Default.History,
                tint = mc.secondaryAccent,
                contentDescription = stringResource(R.string.playtest_action_new_hand)
            )
        }

    }
}



// ── Landscape layout constants ────────────────────────────────────────────────
// Fixed rail widths for the landscape layout — these are deliberate layout dimensions,
// not 8dp-grid spacing tokens, so they live as documented private constants.

/** Width of the left rail (commander zone + mulligan hint) in landscape. */
private val LANDSCAPE_RAIL_WIDTH = 160.dp

/** Width of the right action rail (New hand / Keep / Mulligan) in landscape. */
private val LANDSCAPE_ACTION_RAIL_WIDTH = 110.dp
