package com.mmg.manahub.feature.playtest.presentation.hand

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.playtest.domain.model.HandSnapshot
import com.mmg.manahub.feature.playtest.domain.model.PlaytestPhase
import com.mmg.manahub.feature.playtest.domain.model.PlaytestSetup
import com.mmg.manahub.core.ui.components.CardFullScreenDialog
import com.mmg.manahub.feature.playtest.presentation.battle.BattlefieldContent
import com.mmg.manahub.feature.playtest.presentation.components.BottomNSelector
import com.mmg.manahub.feature.playtest.presentation.components.CommandZoneArea
import com.mmg.manahub.feature.playtest.presentation.components.PlaytestHandCard
import com.mmg.manahub.feature.playtest.presentation.components.PlaytestSaveSheet
import com.mmg.manahub.feature.playtest.presentation.components.PlaytestSurveySheet
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaytestHandScreen(
    setup: PlaytestSetup,
    onBack: () -> Unit,
    viewModel: PlaytestHandViewModel = hiltViewModel(),
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

    var fullScreenCard by remember { mutableStateOf<Card?>(null) }

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

    Box {
        Scaffold(
            containerColor      = mc.background,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                        ) {
                            Text(
                                text  = if (isPlayPhase) {
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
                                        text     = stringResource(R.string.playtest_mulligan_chip, mulligans),
                                        style    = ty.labelSmall,
                                        color    = mc.primaryAccent,
                                        modifier = Modifier.padding(
                                            horizontal = MaterialTheme.spacing.sm,
                                            vertical   = MaterialTheme.spacing.xxs,
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
                    uiState.isLoading -> CircularProgressIndicator(
                        color    = mc.primaryAccent,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    uiState.errorMessage != null -> FullErrorState(
                        message    = uiState.errorMessage!!,
                        retryLabel = stringResource(R.string.action_back),
                        onRetry    = onBack,
                    )

                    isPlayPhase && uiState.battlefield != null -> BattlefieldContent(
                        setup       = setup,
                        battlefield = uiState.battlefield!!,
                        onDrawCard  = viewModel::drawCard,
                        onMoveCard  = viewModel::moveCard,
                        onToggleTap = viewModel::toggleTap,
                        onCardClick = { card -> fullScreenCard = card },
                        onEndTest   = viewModel::requestEndTest,
                    )

                    uiState.snapshot != null -> HandContent(
                        setup                = setup,
                        snapshot             = uiState.snapshot!!,
                        onCardClick          = { card -> fullScreenCard = card },
                        onReorder            = viewModel::onReorderHand,
                        onRedraw             = viewModel::onRedraw,
                        onKeep               = viewModel::onKeep,
                        onMulligan           = viewModel::onMulligan,
                        // Mulligan is disabled when the minimum keepable hand (1 card) would
                        // be reached on the next Keep: mulligansUsed >= drawCount - 1.
                        canMulligan          = uiState.snapshot!!.hand.size > 1 &&
                            uiState.snapshot!!.mulligansUsed < setup.drawCount - 1,
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
            BottomNSelector(
                hand            = uiState.snapshot!!.hand,
                mulligansUsed   = uiState.snapshot!!.mulligansUsed,
                selectedIndices = uiState.selectedBottomIndices,
                onToggle        = viewModel::toggleBottomSelection,
                onConfirm       = viewModel::onConfirmBottomN,
            )
        }

        MagicToastHost(
            state    = toastState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    // Save sheet.
    if (uiState.showSaveSheet && uiState.setup != null && uiState.snapshot != null) {
        PlaytestSaveSheet(
            setup               = uiState.setup!!,
            snapshot            = uiState.snapshot!!,
            isSaving            = uiState.isSaving,
            onSaveWithoutSurvey = viewModel::onSaveWithoutSurvey,
            onSaveAndSurvey     = viewModel::onSaveAndOpenSurvey,
            onDiscard           = viewModel::onDiscard,
            onDismiss           = viewModel::onDismissSaveSheet,
        )
    }

    // Survey sheet.
    if (uiState.showSurveySheet && uiState.snapshot != null) {
        PlaytestSurveySheet(
            handCards = uiState.snapshot!!.hand,
            onFinish  = { answers, types, refs ->
                viewModel.onSurveyFinished(answers, types, refs)
            },
            onDismiss = viewModel::onDismissSurveySheet,
        )
    }

    // End Test confirmation (PLAY phase). Confirming navigates back WITHOUT saving —
    // the battlefield is purely ephemeral, so there is nothing to persist.
    if (uiState.showEndTestConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEndTest,
            containerColor   = mc.surface,
            title = {
                Text(
                    text  = stringResource(R.string.playtest_battle_confirm_end_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
            },
            text = {
                Text(
                    text  = stringResource(R.string.playtest_battle_confirm_end_body),
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmEndTest) {
                    Text(
                        text  = stringResource(R.string.playtest_battle_confirm_end_confirm),
                        style = ty.labelLarge,
                        color = mc.lifeNegative,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEndTest) {
                    Text(
                        text  = stringResource(R.string.playtest_battle_confirm_end_cancel),
                        style = ty.labelLarge,
                        color = mc.textSecondary,
                    )
                }
            },
        )
    }

    // Full-screen card dialog.
    fullScreenCard?.let { card ->
        CardFullScreenDialog(
            card      = card,
            onDismiss = { fullScreenCard = null },
        )
    }
}

// ── Hand content — orientation-aware ─────────────────────────────────────────

@Composable
private fun HandContent(
    setup: PlaytestSetup,
    snapshot: HandSnapshot,
    onCardClick: (Card) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onRedraw: () -> Unit,
    onKeep: () -> Unit,
    onMulligan: () -> Unit,
    canMulligan: Boolean,
) {
    val orientation = LocalConfiguration.current.orientation
    val isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        LandscapeHandContent(
            setup       = setup,
            snapshot    = snapshot,
            onCardClick = onCardClick,
            onReorder   = onReorder,
            onRedraw    = onRedraw,
            onKeep      = onKeep,
            onMulligan  = onMulligan,
            canMulligan = canMulligan,
        )
    } else {
        PortraitHandContent(
            setup       = setup,
            snapshot    = snapshot,
            onCardClick = onCardClick,
            onReorder   = onReorder,
            onRedraw    = onRedraw,
            onKeep      = onKeep,
            onMulligan  = onMulligan,
            canMulligan = canMulligan,
        )
    }
}

// ── Portrait layout ───────────────────────────────────────────────────────────

@Composable
private fun PortraitHandContent(
    setup: PlaytestSetup,
    snapshot: HandSnapshot,
    onCardClick: (Card) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onRedraw: () -> Unit,
    onKeep: () -> Unit,
    onMulligan: () -> Unit,
    canMulligan: Boolean,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Column(modifier = Modifier.fillMaxSize()) {
        // Commander zone strip.
        if (setup.commanderCard != null) {
            CommandZoneArea(
                commanderCard = setup.commanderCard,
                librarySize   = snapshot.library.size,
                modifier      = Modifier.padding(horizontal = sp.md, vertical = sp.xs),
            )
        }

        // London mulligan helper text.
        if (snapshot.mulligansUsed > 0) {
            Text(
                text     = stringResource(R.string.playtest_mulligan_keep_hint, snapshot.mulligansUsed),
                style    = ty.bodySmall,
                color    = mc.textSecondary,
                modifier = Modifier
                    .padding(horizontal = sp.lg)
                    .padding(bottom = sp.xs),
            )
        }

        // Hand area — animated on snapshot id change.
        AnimatedContent(
            targetState    = snapshot.id,
            transitionSpec = {
                (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 4 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 4 })
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            label    = "HandAnimation",
        ) { snapshotId ->
            HandFanRow(
                hand        = snapshot.hand,
                snapshotId  = snapshotId,
                onCardClick = onCardClick,
                onReorder   = onReorder,
            )
        }

        // Bottom action bar.
        BottomActionBar(
            onRedraw    = onRedraw,
            onKeep      = onKeep,
            onMulligan  = onMulligan,
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
    onCardClick: (Card) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onRedraw: () -> Unit,
    onKeep: () -> Unit,
    onMulligan: () -> Unit,
    canMulligan: Boolean,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Row(modifier = Modifier.fillMaxSize()) {
        // LEFT: commander zone + mulligan hint. The 160dp rail width is a fixed layout
        // constant (not an 8dp-grid spacing token).
        Column(
            modifier            = Modifier
                .width(LANDSCAPE_RAIL_WIDTH)
                .fillMaxHeight()
                .padding(horizontal = sp.sm, vertical = sp.sm),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            if (setup.commanderCard != null) {
                CommandZoneArea(
                    commanderCard = setup.commanderCard,
                    librarySize   = snapshot.library.size,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
            if (snapshot.mulligansUsed > 0) {
                Text(
                    text     = stringResource(R.string.playtest_mulligan_keep_hint, snapshot.mulligansUsed),
                    style    = ty.bodySmall,
                    color    = mc.textSecondary,
                    modifier = Modifier.padding(top = sp.sm),
                )
            }
        }

        // CENTER: hand fan — takes all remaining horizontal space.
        AnimatedContent(
            targetState    = snapshot.id,
            transitionSpec = {
                (fadeIn(tween(200)) + slideInVertically(tween(200)) { -it / 4 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(150)) { it / 4 })
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            label    = "HandAnimationLandscape",
        ) { snapshotId ->
            HandFanRow(
                hand        = snapshot.hand,
                snapshotId  = snapshotId,
                onCardClick = onCardClick,
                onReorder   = onReorder,
            )
        }

        // RIGHT: vertical action bar.
        SideActionBar(
            onRedraw    = onRedraw,
            onKeep      = onKeep,
            onMulligan  = onMulligan,
            canMulligan = canMulligan,
            modifier    = Modifier
                .width(LANDSCAPE_ACTION_RAIL_WIDTH)
                .fillMaxHeight(),
        )
    }
}

// ── Adaptive fan row ──────────────────────────────────────────────────────────

/**
 * Renders the hand as a centered fan of overlapping cards.
 *
 * FIX 1 — Adaptive fan algorithm:
 *  1. Read actual available size from [BoxWithConstraints].
 *  2. Apply 12 dp safety padding on each side → availableWidthPx.
 *  3. Pick a target card width based on hand size (portrait targets; landscape is naturally
 *     narrower so the same table also applies — BoxWithConstraints adjusts for free).
 *  4. Reduce card width until card height fits maxHeight (landscape constraint).
 *  5. Compute natural step = (availableWidth - cardWidth) / (handSize - 1).
 *  6. Clamp step into [cardWidth * 0.18, cardWidth - 24dp]:
 *       - Floor (0.18 × width): cards remain distinguishable even under extreme overlap.
 *       - Ceiling (width - 24dp): minimum 24dp visual overlap kept for readability.
 *  7. If total fan width still overflows after clamping, shrink card width by 10 % and repeat.
 *  8. If cards still overflow after 20 iterations, fall back to a scrollable LazyRow — nothing
 *     is ever clipped.
 *
 * FIX 2 — Drag-and-drop with long-press:
 *  - Separate pointerInput for DnD so tap gesture in PlaytestHandCard is unaffected.
 *  - Dragged card gets zIndex 100 and scale 1.08 (via isDragging param).
 *  - On drag end: targetIndex = round((baseOffset + dragDelta + fanOffset) / step).
 */
@Composable
private fun HandFanRow(
    hand: List<Card>,
    snapshotId: Int,
    onCardClick: (Card) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
) {
    if (hand.isEmpty()) return

    val density = LocalDensity.current
    val haptic  = LocalHapticFeedback.current

    // Landscape uses a flatter arc so the fan fits the shorter vertical space.
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val arcFactor = if (isLandscape) 0.6f else 1f

    // Per-card tilt step (degrees) by hand size — a concave fan. The total spread is
    // (size - 1) * perCardAngle, centered on 0°.
    val perCardAngle = when (hand.size) {
        in 0..1 -> 0f
        in 2..4 -> 6f
        in 5..6 -> 5f
        7       -> 4f
        in 8..9 -> 3f
        else    -> 2.5f
    } * arcFactor

    // DnD state — keyed on snapshotId so it resets whenever a new hand is drawn.
    var draggingIndex by remember(snapshotId) { mutableStateOf<Int?>(null) }
    var dragOffsetX   by remember(snapshotId) { mutableStateOf(0f) }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier         = Modifier.fillMaxSize(),
    ) {
        val safetyPaddingDp = 12.dp
        val minOverlapDp    = 24.dp

        val availableWidthPx  = with(density) { (maxWidth  - safetyPaddingDp * 2).toPx() }
        val availableHeightPx = with(density) { maxHeight.toPx() }
        val minOverlapPx      = with(density) { minOverlapDp.toPx() }

        // ── Target card width by hand size ───────────────────────────────────
        val targetCardWidthDp: Dp = when (hand.size) {
            in 1..4  -> 180.dp
            in 5..6  -> 160.dp
            7        -> 140.dp
            in 8..9  -> 130.dp
            else     -> 120.dp
        }

        val targetWidthPx = with(density) { targetCardWidthDp.toPx() }

        // ── Resolve final card width — iteratively shrink until it fits ──────
        var resolvedWidthPx = targetWidthPx
        repeat(20) {
            // Shrink width if card height exceeds available height.
            val cardHeight = resolvedWidthPx * (88f / 63f)
            if (cardHeight > availableHeightPx) {
                resolvedWidthPx = availableHeightPx * (63f / 88f)
            }

            // Natural step to fill ~93 % of available width.
            val naturalStep = if (hand.size > 1) {
                (availableWidthPx - resolvedWidthPx) / (hand.size - 1)
            } else {
                resolvedWidthPx
            }
            val stepFloor = resolvedWidthPx * 0.18f
            val stepCeil  = (resolvedWidthPx - minOverlapPx).coerceAtLeast(stepFloor)
            val step      = naturalStep.coerceIn(stepFloor, stepCeil)
            val fanWidth  = step * (hand.size - 1) + resolvedWidthPx

            if (fanWidth <= availableWidthPx + 0.5f) return@repeat  // fits — stop early
            resolvedWidthPx *= 0.90f  // shrink and retry
        }

        // ── Final step (recomputed with the resolved width) ──────────────────
        val naturalStep = if (hand.size > 1) {
            (availableWidthPx - resolvedWidthPx) / (hand.size - 1)
        } else {
            resolvedWidthPx
        }
        val stepFloor = resolvedWidthPx * 0.18f
        val stepCeil  = (resolvedWidthPx - minOverlapPx).coerceAtLeast(stepFloor)
        val stepPx    = naturalStep.coerceIn(stepFloor, stepCeil)

        val totalFanWidthPx = stepPx * (hand.size - 1) + resolvedWidthPx
        val cardWidthDp     = with(density) { resolvedWidthPx.toDp() }

        // ── Fallback: LazyRow if still overflowing (pathological case) ───────
        if (totalFanWidthPx > availableWidthPx + 1f) {
            LazyRow(
                modifier              = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                contentPadding        = PaddingValues(horizontal = 12.dp),
            ) {
                itemsIndexed(hand) { index, card ->
                    PlaytestHandCard(
                        card       = card,
                        width      = cardWidthDp,
                        onClick    = { onCardClick(card) },
                        isDragging = draggingIndex == index,
                    )
                }
            }
            return@BoxWithConstraints
        }

        // ── Normal fan layout ────────────────────────────────────────────────
        hand.forEachIndexed { index, card ->
            // Centered base offset for this card slot.
            val baseOffsetPx = index * stepPx - totalFanWidthPx / 2f + resolvedWidthPx / 2f

            val xOffsetPx = if (draggingIndex == index) {
                (baseOffsetPx + dragOffsetX).toInt()
            } else {
                baseOffsetPx.toInt()
            }

            val isDragging = draggingIndex == index

            // Concave arc tilt centered on the middle card. The dragged card straightens
            // to 0° (a fast 120 ms tween); resting cards settle with a bouncy spring.
            val targetAngle = if (isDragging) {
                0f
            } else {
                (index - (hand.size - 1) / 2f) * perCardAngle
            }
            val angle by animateFloatAsState(
                targetValue   = targetAngle,
                animationSpec = if (isDragging) {
                    tween(durationMillis = 120)
                } else {
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                },
                label = "FanCardRotation",
            )

            PlaytestHandCard(
                card       = card,
                width      = cardWidthDp,
                onClick    = { onCardClick(card) },
                isDragging = isDragging,
                modifier   = Modifier
                    .zIndex(if (isDragging) 100f else index.toFloat())
                    .offset { IntOffset(xOffsetPx, 0) }
                    .rotate(angle)
                    // DnD: long-press to start, drag to move, release to commit.
                    // The tap gesture lives inside PlaytestHandCard in its own pointerInput
                    // block — both coexist without interference.
                    .pointerInput(index, snapshotId) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { _ ->
                                draggingIndex = index
                                dragOffsetX   = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, dragAmount ->
                                dragOffsetX += dragAmount.x
                                change.consume()
                            },
                            onDragEnd = {
                                // Map final pixel position back to a slot index.
                                val finalCenter   = baseOffsetPx + dragOffsetX
                                val slotFromLeft  = finalCenter + totalFanWidthPx / 2f - resolvedWidthPx / 2f
                                val targetIndex   = (slotFromLeft / stepPx)
                                    .roundToInt()
                                    .coerceIn(0, hand.size - 1)

                                if (targetIndex != index) {
                                    onReorder(index, targetIndex)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                draggingIndex = null
                                dragOffsetX   = 0f
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffsetX   = 0f
                            },
                        )
                    },
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
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Surface(
        color    = mc.background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = sp.md, vertical = sp.sm),
            horizontalArrangement = Arrangement.spacedBy(sp.sm),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            // New hand (ephemeral redraw).
            OutlinedButton(
                onClick  = onRedraw,
                shape    = ButtonShape,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = mc.textSecondary),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Casino,
                    contentDescription = null,
                    modifier           = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(sp.xs))
                Text(stringResource(R.string.playtest_action_new_hand), style = ty.labelSmall)
            }

            // Keep.
            Button(
                onClick  = onKeep,
                shape    = ButtonShape,
                colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text  = stringResource(R.string.playtest_action_keep),
                    style = ty.labelLarge,
                    color = mc.background,
                )
            }

            // Mulligan.
            OutlinedButton(
                onClick  = onMulligan,
                enabled  = canMulligan,
                shape    = ButtonShape,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = mc.primaryAccent),
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.playtest_action_mulligan), style = ty.labelSmall)
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
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Surface(
        color    = mc.background,
        modifier = modifier,
    ) {
        Column(
            modifier            = Modifier
                .fillMaxHeight()
                .navigationBarsPadding()
                .padding(horizontal = sp.sm, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.sm, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // New hand (icon only — narrow column).
            OutlinedButton(
                onClick  = onRedraw,
                shape    = ButtonShape,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = mc.textSecondary),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Default.Casino,
                    contentDescription = stringResource(R.string.playtest_action_new_hand),
                    modifier           = Modifier.size(16.dp),
                )
            }

            // Keep.
            Button(
                onClick  = onKeep,
                shape    = ButtonShape,
                colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text  = stringResource(R.string.playtest_action_keep),
                    style = ty.labelSmall,
                    color = mc.background,
                )
            }

            // Mulligan.
            OutlinedButton(
                onClick  = onMulligan,
                enabled  = canMulligan,
                shape    = ButtonShape,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = mc.primaryAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.playtest_action_mulligan), style = ty.labelSmall)
            }
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
