package com.mmg.manahub.feature.playtest.presentation.battle

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.BattlefieldState
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.PlayCard
import com.mmg.manahub.core.model.PlayZone
import com.mmg.manahub.core.model.PlaytestSetup
import com.mmg.manahub.core.ui.components.MagicCard
import com.mmg.manahub.core.ui.components.MagicCardInspectionOverlay
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.playtest.presentation.components.CommandZoneArea
import kotlin.math.roundToInt

private val FIELD_CARD_WIDTH = 80.dp
private val HAND_CARD_WIDTH = 100.dp
private const val CARD_ASPECT_W = 63f
private const val CARD_ASPECT_H = 88f

/** Parameters for the wheel/fan animation in [HandStrip]. */
private val WHEEL_RADIUS = 600.dp
private val WHEEL_OVERLAP = 48.dp
private val FAN_PIVOT_Y = 1.25f // Relative to card height

private data class DragSession(
    val card: PlayCard,
    val fromZone: PlayZone,
    val startCenter: Offset,
    val cardWidth: Dp,
)

private data class InspectionSession(
    val cards: List<PlayCard>,
    val startIndex: Int,
    val initialRect: Rect,
)

// ── Public entry point ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BattlefieldContent(
    setup: PlaytestSetup,
    battlefield: BattlefieldState,
    onDrawCard: () -> Unit,
    onMoveCard: (instanceId: Long, toZone: PlayZone) -> Unit,
    onReorderHand: (from: Int, to: Int) -> Unit,
    onToggleTap: (instanceId: Long) -> Unit,
    onUpdateCardOffset: (instanceId: Long, x: Float, y: Float) -> Unit = { _, _, _ -> },
    onInspectCommander: (Card, Rect) -> Unit = { _, _ -> },
) {
    val haptic = LocalHapticFeedback.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val zoneCoords = remember { mutableStateMapOf<PlayZone, LayoutCoordinates>() }
    val zoneBounds by remember {
        derivedStateOf {
            val root = boxCoords
            if (root == null || !root.isAttached) emptyMap<PlayZone, Rect>()
            else {
                zoneCoords.mapValues { (_, coords) ->
                    if (coords.isAttached) root.localBoundingBoxOf(coords)
                    else Rect.Zero
                }
            }
        }
    }

    var dragSession by remember { mutableStateOf<DragSession?>(null) }
    val pointerOffset = remember { mutableStateOf(Offset.Zero) }

    val hoveredZone: PlayZone? by remember {
        derivedStateOf {
            val session = dragSession ?: return@derivedStateOf null
            val pointer = session.startCenter + pointerOffset.value
            zoneBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
        }
    }

    val onDragStart: (PlayCard, PlayZone, Offset, Dp) -> Unit = { card, zone, center, width ->
        pointerOffset.value = Offset.Zero
        dragSession = DragSession(card, zone, center, width)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val onDragDelta: (Offset) -> Unit = { delta ->
        pointerOffset.value += delta
    }
    val onDragEnd: () -> Unit = {
        val session = dragSession
        if (session != null && zoneBounds.isNotEmpty()) {
            val pointer = session.startCenter + pointerOffset.value
            val target = zoneBounds.entries.firstOrNull { it.value.contains(pointer) }?.key

            if (target != null && target != session.fromZone) {
                onMoveCard(session.card.instanceId, target)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } else if (target == PlayZone.HAND && session.fromZone == PlayZone.HAND) {
                val handBounds = zoneBounds[PlayZone.HAND]
                if (handBounds != null) {
                    val relX = pointer.x - handBounds.left
                    val fromIndex = battlefield.hand.indexOfFirst { it.instanceId == session.card.instanceId }
                    if (fromIndex != -1) {
                        val cardStep = (handBounds.width / battlefield.hand.size).coerceAtLeast(1f)
                        val toIndex = (relX / cardStep).toInt().coerceIn(battlefield.hand.indices)
                        onReorderHand(fromIndex, toIndex)
                    }
                }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } else if (target != null && target == session.fromZone &&
                (target == PlayZone.LANDS || target == PlayZone.PERMANENTS)
            ) {
                val zoneBound = zoneBounds[target]
                if (zoneBound != null) {
                    val relX = pointer.x - zoneBound.left
                    val relY = pointer.y - zoneBound.top
                    val clampedX = relX.coerceIn(0f, zoneBound.width)
                    val clampedY = relY.coerceIn(0f, zoneBound.height)
                    onUpdateCardOffset(session.card.instanceId, clampedX, clampedY)
                }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        dragSession = null
    }
    val onDragCancel: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        dragSession = null
    }

    var inspectionSession by remember { mutableStateOf<InspectionSession?>(null) }
    var isDismissingInspection by remember { mutableStateOf(false) }

    // Data class to ensure moving state is updated atomically
    data class PendingMove(val id: Long, val zone: PlayZone)
    var pendingMove by remember { mutableStateOf<PendingMove?>(null) }

    // Ensure the lambda captures the State object itself, not its current value
    val latestPendingMove = rememberUpdatedState(pendingMove)

    val completeMove = remember(onMoveCard) {
        {
            val move = latestPendingMove.value
            android.util.Log.d("Battlefield", "completeMove: move=$move")
            if (move != null) {
                onMoveCard(move.id, move.zone)
            }
            inspectionSession = null
            isDismissingInspection = false
            pendingMove = null
        }
    }

    // ── Draw animation logic ─────────────────────────────────────────────────
    var prevHandSize by remember { mutableStateOf(battlefield.hand.size) }
    var prevHandIds by remember { mutableStateOf(battlefield.hand.map { it.instanceId }.toSet()) }
    var animatingDrawCard by remember { mutableStateOf<PlayCard?>(null) }
    val drawAnimProgress = remember { Animatable(0f) }

    LaunchedEffect(battlefield.hand.size) {
        val currentSize = battlefield.hand.size
        if (currentSize > prevHandSize && prevHandSize > 0) {
            val newCard = battlefield.hand.find { !prevHandIds.contains(it.instanceId) }
            if (newCard != null) {
                animatingDrawCard = newCard
                drawAnimProgress.snapTo(0f)
                drawAnimProgress.animateTo(1f, tween(450))
                animatingDrawCard = null
            }
        }
        prevHandSize = currentSize
        prevHandIds = battlefield.hand.map { it.instanceId }.toSet()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxCoords = it }
    ) {
        val commonProps = BattlefieldProps(
            setup = setup,
            battlefield = battlefield,
            hoveredZone = hoveredZone,
            draggingId = dragSession?.card?.instanceId,
            animatingDrawId = animatingDrawCard?.instanceId,
            boxCoords = boxCoords,
            zoneBounds = zoneBounds,
            onRegisterZone = { zone, coords -> zoneCoords[zone] = coords },
            onDrawCard = onDrawCard,
            onToggleTap = onToggleTap,
            onCardClick = { cards, rect ->
                inspectionSession = InspectionSession(
                    cards = cards,
                    startIndex = cards.size - 1,
                    initialRect = rect
                )
            },
            onInspectCommander = onInspectCommander,
            onDragStart = onDragStart,
            onDragDelta = onDragDelta,
            onDragEnd = onDragEnd,
            onDragCancel = onDragCancel,
            onUpdateCardOffset = onUpdateCardOffset,
        )

        if (isLandscape) {
            LandscapeBattlefield(commonProps)
        } else {
            PortraitBattlefield(commonProps)
        }

        // Floating drag ghost
        dragSession?.let { session ->
            MagicCard(
                card = session.card.card,
                width = session.cardWidth,
                isTapped = session.card.isTapped,
                onClick = null,
                elevation = 20.dp,
                modifier = Modifier
                    .zIndex(Float.MAX_VALUE)
                    .offset {
                        val center = session.startCenter + pointerOffset.value
                        val w = session.cardWidth.toPx()
                        val h = w * (CARD_ASPECT_H / CARD_ASPECT_W)
                        IntOffset(
                            (center.x - w / 2f).roundToInt(),
                            (center.y - h / 2f).roundToInt(),
                        )
                    },
            )
        }

        // Draw animation overlay
        animatingDrawCard?.let { playCard ->
            val libraryRect = zoneBounds[PlayZone.LIBRARY]
            val handRect = zoneBounds[PlayZone.HAND]
            if (libraryRect != null && handRect != null) {
                val start = libraryRect.center
                val end = handRect.center
                val currentCenter = lerp(start, end, drawAnimProgress.value)
                val currentScale = lerp(0.8f, 1.2f, drawAnimProgress.value)
                val currentAlpha = if (drawAnimProgress.value < 0.2f) drawAnimProgress.value * 5f else 1f

                MagicCard(
                    card = playCard.card,
                    width = HAND_CARD_WIDTH,
                    onClick = null,
                    modifier = Modifier
                        .zIndex(Float.MAX_VALUE)
                        .graphicsLayer {
                            val w = HAND_CARD_WIDTH.toPx()
                            val h = w * (CARD_ASPECT_H / CARD_ASPECT_W)
                            translationX = currentCenter.x - w / 2f
                            translationY = currentCenter.y - h / 2f
                            scaleX = currentScale
                            scaleY = currentScale
                            alpha = currentAlpha
                        }
                )
            }
        }
    }

    // ── Contextual actions overlay ───────────────────────────────────────────
    inspectionSession?.let { session ->
        MagicCardInspectionOverlay(
            items = session.cards,
            initialIndex = session.startIndex,
            initialRect = session.initialRect,
            isVisible = true,
            isDismissing = isDismissingInspection,
            onDismissRequest = { isDismissingInspection = true },
            onDismiss = { completeMove() },
            cardExtractor = { it.card },
            targetExitRect = pendingMove?.let { zoneBounds[it.zone] },
            isTappedExtractor = { it.isTapped },
            onAnimationEnd = { completeMove() },
            actions = { playCard ->
                val card = playCard.card
                val (_, currentZone) = findCardInBattlefield(battlefield, playCard.instanceId) ?: (playCard to PlayZone.HAND)
                val isLand = card.typeLine.contains("Land", ignoreCase = true)

                if (currentZone != PlayZone.LANDS && currentZone != PlayZone.PERMANENTS) {
                    Button(
                        onClick = {
                            android.util.Log.d("Battlefield", "Play button clicked for instanceId=${playCard.instanceId}")
                            pendingMove = PendingMove(playCard.instanceId, if (isLand) PlayZone.LANDS else PlayZone.PERMANENTS)
                            isDismissingInspection = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mc.primaryAccent,
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(sp.sm))
                        Text(text = if (isLand) "Play as Land" else "Play", style = ty.titleMedium)
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(sp.sm),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (currentZone != PlayZone.GRAVEYARD) {
                        Button(
                            onClick = {
                                android.util.Log.d("Battlefield", "Grave button clicked for instanceId=${playCard.instanceId}")
                                pendingMove = PendingMove(playCard.instanceId, PlayZone.GRAVEYARD)
                                isDismissingInspection = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = mc.lifeNegative,
                                contentColor = mc.surface,
                            ),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(sp.xs))
                            Text("Grave", style = ty.labelLarge)
                        }
                    }

                    if (currentZone != PlayZone.EXILE) {
                        Button(
                            onClick = {
                                android.util.Log.d("Battlefield", "Exile button clicked for instanceId=${playCard.instanceId}")
                                pendingMove = PendingMove(playCard.instanceId, PlayZone.EXILE)
                                isDismissingInspection = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = mc.secondaryAccent,
                                contentColor = mc.surface,
                            ),
                            modifier = Modifier.weight(1f).height(48.dp),
                        ) {
                            Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(sp.xs))
                            Text("Exile", style = ty.labelLarge)
                        }
                    }
                }

                if (currentZone != PlayZone.HAND) {
                    Button(
                        onClick = {
                            android.util.Log.d("Battlefield", "Hand button clicked for instanceId=${playCard.instanceId}")
                            pendingMove = PendingMove(playCard.instanceId, PlayZone.HAND)
                            isDismissingInspection = true
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mc.surfaceVariant,
                            contentColor = mc.textPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) {
                        Text("Return to Hand", style = ty.labelMedium)
                    }
                }
            }
        )
    }
}

/** Locates a PlayCard across all zones of a BattlefieldState. */
private fun findCardInBattlefield(
    battlefield: BattlefieldState,
    instanceId: Long,
): Pair<PlayCard, PlayZone>? {
    battlefield.hand.find { it.instanceId == instanceId }?.let { return it to PlayZone.HAND }
    battlefield.lands.find { it.instanceId == instanceId }?.let { return it to PlayZone.LANDS }
    battlefield.permanents.find { it.instanceId == instanceId }?.let { return it to PlayZone.PERMANENTS }
    battlefield.graveyard.find { it.instanceId == instanceId }?.let { return it to PlayZone.GRAVEYARD }
    battlefield.exile.find { it.instanceId == instanceId }?.let { return it to PlayZone.EXILE }
    return null
}

private data class BattlefieldProps(
    val setup: PlaytestSetup,
    val battlefield: BattlefieldState,
    val hoveredZone: PlayZone?,
    val draggingId: Long?,
    val animatingDrawId: Long?,
    val boxCoords: LayoutCoordinates?,
    val zoneBounds: Map<PlayZone, Rect>,
    val onRegisterZone: (PlayZone, LayoutCoordinates) -> Unit,
    val onDrawCard: () -> Unit,
    val onToggleTap: (Long) -> Unit,
    val onCardClick: (List<PlayCard>, Rect) -> Unit,
    val onInspectCommander: (Card, Rect) -> Unit,
    val onDragStart: (PlayCard, PlayZone, Offset, Dp) -> Unit,
    val onDragDelta: (Offset) -> Unit,
    val onDragEnd: () -> Unit,
    val onDragCancel: () -> Unit,
    val onUpdateCardOffset: (instanceId: Long, x: Float, y: Float) -> Unit,
)

// ── Portrait layout ─────────────────────────────────────────────────────────

@Composable
private fun PortraitBattlefield(props: BattlefieldProps) {
    val sp = MaterialTheme.spacing

    Column(modifier = Modifier.fillMaxSize()) {
        val commander = props.setup.commanderCard
        if (commander != null) {
            var cardRect by remember { mutableStateOf(Rect.Zero) }

            CommandZoneArea(
                commanderCard = commander,
                librarySize   = props.battlefield.library.size,
                onClick       = { props.onInspectCommander(commander, cardRect) },
                modifier      = Modifier
                    .padding(horizontal = sp.md, vertical = sp.xs)
                    .onGloballyPositioned { coords ->
                        if (props.boxCoords != null && props.boxCoords.isAttached && coords.isAttached) {
                            cardRect = props.boxCoords.localBoundingBoxOf(coords)
                        }
                    },
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = sp.md, vertical = sp.sm),
            horizontalArrangement = Arrangement.spacedBy(sp.sm)
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(sp.sm)
            ) {
                FreeFormFieldZone(
                    zone = PlayZone.PERMANENTS,
                    label = stringResource(R.string.playtest_battle_zone_permanents),
                    emptyHint = stringResource(R.string.playtest_battle_field_empty_permanents),
                    cards = props.battlefield.permanents,
                    cardWidth = FIELD_CARD_WIDTH,
                    isHovered = props.hoveredZone == PlayZone.PERMANENTS,
                    draggingId = props.draggingId,
                    boxCoords = props.boxCoords,
                    onRegister = { props.onRegisterZone(PlayZone.PERMANENTS, it) },
                    onCardClick = props.onCardClick,
                    onToggleTap = props.onToggleTap,
                    onDragStart = { c, center, w -> props.onDragStart(c, PlayZone.PERMANENTS, center, w) },
                    onDragDelta = props.onDragDelta,
                    onDragEnd = props.onDragEnd,
                    onDragCancel = props.onDragCancel,
                    onUpdateCardOffset = props.onUpdateCardOffset,
                    modifier = Modifier.weight(0.6f),
                )
                FreeFormFieldZone(
                    zone = PlayZone.LANDS,
                    label = stringResource(R.string.playtest_battle_zone_lands),
                    emptyHint = stringResource(R.string.playtest_battle_field_empty_lands),
                    cards = props.battlefield.lands,
                    cardWidth = FIELD_CARD_WIDTH,
                    isHovered = props.hoveredZone == PlayZone.LANDS,
                    draggingId = props.draggingId,
                    boxCoords = props.boxCoords,
                    onRegister = { props.onRegisterZone(PlayZone.LANDS, it) },
                    onCardClick = props.onCardClick,
                    onToggleTap = props.onToggleTap,
                    onDragStart = { c, center, w -> props.onDragStart(c, PlayZone.LANDS, center, w) },
                    onDragDelta = props.onDragDelta,
                    onDragEnd = props.onDragEnd,
                    onDragCancel = props.onDragCancel,
                    onUpdateCardOffset = props.onUpdateCardOffset,
                    modifier = Modifier.weight(0.4f),
                )
            }

            Column(
                modifier = Modifier.width(90.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(sp.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LibraryPile(
                    librarySize = props.battlefield.library.size,
                    onDrawCard = props.onDrawCard,
                    cardWidth = FIELD_CARD_WIDTH,
                    onRegister = { props.onRegisterZone(PlayZone.LIBRARY, it) },
                    boxCoords = props.boxCoords,
                )
                GraveyardPile(
                    graveyard = props.battlefield.graveyard,
                    cardWidth = FIELD_CARD_WIDTH,
                    isHovered = props.hoveredZone == PlayZone.GRAVEYARD,
                    boxCoords = props.boxCoords,
                    onRegister = { props.onRegisterZone(PlayZone.GRAVEYARD, it) },
                    onCardClick = props.onCardClick,
                )
                ExilePile(
                    exile = props.battlefield.exile,
                    cardWidth = FIELD_CARD_WIDTH,
                    isHovered = props.hoveredZone == PlayZone.EXILE,
                    boxCoords = props.boxCoords,
                    onRegister = { props.onRegisterZone(PlayZone.EXILE, it) },
                    onCardClick = props.onCardClick,
                )
            }
        }

        HandStrip(
            cards = props.battlefield.hand,
            cardWidth = HAND_CARD_WIDTH,
            isHovered = props.hoveredZone == PlayZone.HAND,
            draggingId = props.draggingId,
            animatingDrawId = props.animatingDrawId,
            boxCoords = props.boxCoords,
            onRegister = { props.onRegisterZone(PlayZone.HAND, it) },
            onCardClick = props.onCardClick,
            onDragStart = { c, center, w -> props.onDragStart(c, PlayZone.HAND, center, w) },
            onDragDelta = props.onDragDelta,
            onDragEnd = props.onDragEnd,
            onDragCancel = props.onDragCancel,
            modifier = Modifier.padding(horizontal = sp.md, vertical = sp.xs),
        )
    }
}

// ── Landscape layout ────────────────────────────────────────────────────────

@Composable
private fun LandscapeBattlefield(props: BattlefieldProps) {
    val sp = MaterialTheme.spacing

    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .width(120.dp)
                .fillMaxHeight()
                .navigationBarsPadding()
                .padding(horizontal = sp.sm, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val cmdr = props.setup.commanderCard
            if (cmdr != null) {
                var cardRect by remember { mutableStateOf(Rect.Zero) }

                CommandZoneArea(
                    commanderCard = cmdr,
                    librarySize   = props.battlefield.library.size,
                    onClick       = { props.onInspectCommander(cmdr, cardRect) },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            if (props.boxCoords != null && props.boxCoords.isAttached && coords.isAttached) {
                                cardRect = props.boxCoords.localBoundingBoxOf(coords)
                            }
                        },
                )
            }
            LibraryPile(
                librarySize = props.battlefield.library.size,
                onDrawCard = props.onDrawCard,
                cardWidth = FIELD_CARD_WIDTH,
                onRegister = { props.onRegisterZone(PlayZone.LIBRARY, it) },
                boxCoords = props.boxCoords,
            )
            GraveyardPile(
                graveyard = props.battlefield.graveyard,
                cardWidth = FIELD_CARD_WIDTH,
                isHovered = props.hoveredZone == PlayZone.GRAVEYARD,
                boxCoords = props.boxCoords,
                onRegister = { props.onRegisterZone(PlayZone.GRAVEYARD, it) },
                onCardClick = props.onCardClick,
            )
            ExilePile(
                exile = props.battlefield.exile,
                cardWidth = FIELD_CARD_WIDTH,
                isHovered = props.hoveredZone == PlayZone.EXILE,
                boxCoords = props.boxCoords,
                onRegister = { props.onRegisterZone(PlayZone.EXILE, it) },
                onCardClick = props.onCardClick,
            )
            Spacer(Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = sp.sm, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.sm),
        ) {
            FreeFormFieldZone(
                zone = PlayZone.PERMANENTS,
                label = stringResource(R.string.playtest_battle_zone_permanents),
                emptyHint = stringResource(R.string.playtest_battle_field_empty_permanents),
                cards = props.battlefield.permanents,
                cardWidth = FIELD_CARD_WIDTH,
                isHovered = props.hoveredZone == PlayZone.PERMANENTS,
                draggingId = props.draggingId,
                boxCoords = props.boxCoords,
                onRegister = { props.onRegisterZone(PlayZone.PERMANENTS, it) },
                onCardClick = props.onCardClick,
                onToggleTap = props.onToggleTap,
                onDragStart = { c, center, w -> props.onDragStart(c, PlayZone.PERMANENTS, center, w) },
                onDragDelta = props.onDragDelta,
                onDragEnd = props.onDragEnd,
                onDragCancel = props.onDragCancel,
                onUpdateCardOffset = props.onUpdateCardOffset,
                modifier = Modifier.weight(0.6f),
            )
            FreeFormFieldZone(
                zone = PlayZone.LANDS,
                label = stringResource(R.string.playtest_battle_zone_lands),
                emptyHint = stringResource(R.string.playtest_battle_field_empty_lands),
                cards = props.battlefield.lands,
                cardWidth = FIELD_CARD_WIDTH,
                isHovered = props.hoveredZone == PlayZone.LANDS,
                draggingId = props.draggingId,
                boxCoords = props.boxCoords,
                onRegister = { props.onRegisterZone(PlayZone.LANDS, it) },
                onCardClick = props.onCardClick,
                onToggleTap = props.onToggleTap,
                onDragStart = { c, center, w -> props.onDragStart(c, PlayZone.LANDS, center, w) },
                onDragDelta = props.onDragDelta,
                onDragEnd = props.onDragEnd,
                onDragCancel = props.onDragCancel,
                onUpdateCardOffset = props.onUpdateCardOffset,
                modifier = Modifier.weight(0.4f),
            )
            HandStrip(
                cards = props.battlefield.hand,
                cardWidth = HAND_CARD_WIDTH,
                isHovered = props.hoveredZone == PlayZone.HAND,
                draggingId = props.draggingId,
                animatingDrawId = props.animatingDrawId,
                boxCoords = props.boxCoords,
                onRegister = { props.onRegisterZone(PlayZone.HAND, it) },
                onCardClick = props.onCardClick,
                onDragStart = { c, center, w -> props.onDragStart(c, PlayZone.HAND, center, w) },
                onDragDelta = props.onDragDelta,
                onDragEnd = props.onDragEnd,
                onDragCancel = props.onDragCancel,
            )
        }
    }
}

/** Linear interpolation for Offsets. */
private fun lerp(start: Offset, end: Offset, fraction: Float): Offset {
    return Offset(
        start.x + (end.x - start.x) * fraction,
        start.y + (end.y - start.y) * fraction
    )
}

/** Linear interpolation for Floats. */
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}

// ── Piles ────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryPile(
    librarySize: Int,
    onDrawCard: () -> Unit,
    cardWidth: Dp,
    onRegister: (LayoutCoordinates) -> Unit,
    boxCoords: LayoutCoordinates?,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .width(cardWidth)
                .aspectRatio(CARD_ASPECT_W / CARD_ASPECT_H)
                .onGloballyPositioned { coords ->
                    if (boxCoords != null && boxCoords.isAttached && coords.isAttached) {
                        onRegister(coords)
                    }
                }
                .shadow(4.dp, CardShape)
                .clip(CardShape)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDrawCard() })
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Image(
                painter = painterResource(R.drawable.mtg_card_back),
                contentDescription = stringResource(R.string.playtest_battle_library_cd, librarySize),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            CountBadge(count = librarySize, modifier = Modifier.padding(sp.xxs))
        }
        Text(
            text = stringResource(R.string.playtest_battle_draw_card),
            style = ty.labelSmall,
            color = mc.textSecondary,
            modifier = Modifier.padding(top = sp.xxs),
        )
    }
}

@Composable
private fun GraveyardPile(
    graveyard: List<PlayCard>,
    cardWidth: Dp,
    isHovered: Boolean,
    boxCoords: LayoutCoordinates?,
    onRegister: (LayoutCoordinates) -> Unit,
    onCardClick: (List<PlayCard>, Rect) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    var cardRect by remember { mutableStateOf(Rect.Zero) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .aspectRatio(CARD_ASPECT_W / CARD_ASPECT_H)
                .onGloballyPositioned { coords ->
                    if (boxCoords != null && boxCoords.isAttached && coords.isAttached) {
                        cardRect = boxCoords.localBoundingBoxOf(coords)
                        onRegister(coords)
                    }
                }
                .then(if (isHovered) Modifier.border(2.dp, mc.primaryAccent, CardShape) else Modifier)
                .background(mc.surfaceVariant, CardShape)
                .clip(CardShape)
                .clickable(enabled = graveyard.isNotEmpty()) { onCardClick(graveyard, cardRect) },
            contentAlignment = Alignment.Center,
        ) {
            if (graveyard.isEmpty()) {
                Text(text = "Grave", style = ty.labelMedium, color = mc.textDisabled)
            } else {
                val topCard = graveyard.last()
                AsyncImage(
                    model = topCard.card.imageNormal,
                    contentDescription = topCard.card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                CountBadge(count = graveyard.size, modifier = Modifier.align(Alignment.BottomCenter).padding(sp.xxs))
            }
        }
        Text(
            text = "Graveyard",
            style = ty.labelSmall,
            color = mc.textSecondary,
            modifier = Modifier.padding(top = sp.xxs),
        )
    }
}

@Composable
private fun ExilePile(
    exile: List<PlayCard>,
    cardWidth: Dp,
    isHovered: Boolean,
    boxCoords: LayoutCoordinates?,
    onRegister: (LayoutCoordinates) -> Unit,
    onCardClick: (List<PlayCard>, Rect) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    var cardRect by remember { mutableStateOf(Rect.Zero) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .aspectRatio(CARD_ASPECT_W / CARD_ASPECT_H)
                .onGloballyPositioned { coords ->
                    if (boxCoords != null && boxCoords.isAttached && coords.isAttached) {
                        cardRect = boxCoords.localBoundingBoxOf(coords)
                        onRegister(coords)
                    }
                }
                .then(if (isHovered) Modifier.border(2.dp, mc.primaryAccent, CardShape) else Modifier)
                .background(mc.surfaceVariant, CardShape)
                .clip(CardShape)
                .clickable(enabled = exile.isNotEmpty()) { onCardClick(exile, cardRect) },
            contentAlignment = Alignment.Center,
        ) {
            if (exile.isEmpty()) {
                Text(text = "Exile", style = ty.labelMedium, color = mc.textDisabled)
            } else {
                val topCard = exile.last()
                AsyncImage(
                    model = topCard.card.imageNormal,
                    contentDescription = topCard.card.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.7f),
                )
                CountBadge(count = exile.size, modifier = Modifier.align(Alignment.BottomCenter).padding(sp.xxs))
            }
        }
        Text(
            text = "Exile",
            style = ty.labelSmall,
            color = mc.textSecondary,
            modifier = Modifier.padding(top = sp.xxs),
        )
    }
}

@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        color = mc.surface.copy(alpha = 0.85f),
        shape = ChipShape,
        modifier = modifier,
    ) {
        Text(
            text = count.toString(),
            style = ty.labelSmall,
            color = mc.textPrimary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ── Free-form field zone ────────────────────────────────────────────────────

@Composable
private fun FreeFormFieldZone(
    zone: PlayZone,
    label: String,
    emptyHint: String,
    cards: List<PlayCard>,
    cardWidth: Dp,
    isHovered: Boolean,
    draggingId: Long?,
    boxCoords: LayoutCoordinates?,
    onRegister: (LayoutCoordinates) -> Unit,
    onCardClick: (List<PlayCard>, Rect) -> Unit,
    onToggleTap: (Long) -> Unit,
    onDragStart: (PlayCard, Offset, Dp) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onUpdateCardOffset: (instanceId: Long, x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                if (boxCoords != null && boxCoords.isAttached && coords.isAttached) {
                    onRegister(coords)
                }
            }
            .then(
                if (isHovered) Modifier.border(2.dp, mc.primaryAccent, CardShape)
                else Modifier
            )
            .background(mc.surfaceVariant.copy(alpha = 0.3f), CardShape)
            .drawBehind {
                val stroke = Stroke(
                    width = 1.5f.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
                )
                drawRoundRect(
                    color = mc.surfaceVariant,
                    cornerRadius = CornerRadius(8.dp.toPx()),
                    style = stroke,
                )
            }
    ) {
        if (cards.isEmpty()) {
            Text(
                text = emptyHint,
                style = ty.bodyMedium,
                color = mc.textDisabled,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(sp.md),
            )
        }

        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val cardWidthPx = with(density) { cardWidth.toPx() }
        val cardHeightPx = cardWidthPx * (CARD_ASPECT_H / CARD_ASPECT_W)

        cards.forEachIndexed { index, playCard ->
            val isDragging = draggingId == playCard.instanceId

            val effectiveX = if (playCard.xOffset == 0f && playCard.yOffset == 0f) {
                (index * cardWidthPx * 0.35f).coerceAtMost(containerWidthPx - cardWidthPx)
            } else {
                (playCard.xOffset - cardWidthPx / 2f).coerceIn(0f, (containerWidthPx - cardWidthPx).coerceAtLeast(0f))
            }
            val effectiveY = if (playCard.xOffset == 0f && playCard.yOffset == 0f) {
                (index * 8f).coerceAtMost((containerHeightPx - cardHeightPx).coerceAtLeast(0f))
            } else {
                (playCard.yOffset - cardHeightPx / 2f).coerceIn(0f, (containerHeightPx - cardHeightPx).coerceAtLeast(0f))
            }

            DraggableFieldCard(
                playCard = playCard,
                cardWidth = cardWidth,
                isDragging = isDragging,
                boxCoords = boxCoords,
                onClick = { rect -> onCardClick(listOf(playCard), rect) },
                onToggleTap = { onToggleTap(playCard.instanceId) },
                onDragStart = onDragStart,
                onDragDelta = onDragDelta,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
                modifier = Modifier
                    .zIndex(if (isDragging) 100f else index.toFloat())
                    .offset {
                        IntOffset(effectiveX.roundToInt(), effectiveY.roundToInt())
                    },
            )
        }

        Text(
            text = label,
            style = ty.labelSmall,
            color = mc.textSecondary.copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(sp.xs),
        )
    }
}

// ── Hand fan strip ──────────────────────────────────────────────────────────

@Composable
private fun HandStrip(
    cards: List<PlayCard>,
    cardWidth: Dp,
    isHovered: Boolean,
    draggingId: Long?,
    animatingDrawId: Long?,
    boxCoords: LayoutCoordinates?,
    onRegister: (LayoutCoordinates) -> Unit,
    onCardClick: (List<PlayCard>, Rect) -> Unit,
    onDragStart: (PlayCard, Offset, Dp) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    val density = LocalDensity.current

    val listState = rememberLazyListState()
    val radiusPx = with(density) { WHEEL_RADIUS.toPx() }
    val stripHeight = cardWidth * (CARD_ASPECT_H / CARD_ASPECT_W) + 60.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(stripHeight)
            .onGloballyPositioned { coords ->
                if (boxCoords != null && boxCoords.isAttached && coords.isAttached) {
                    onRegister(coords)
                }
            }
            .then(
                if (isHovered) Modifier.border(2.dp, mc.primaryAccent.copy(alpha = 0.5f), CardShape)
                else Modifier
            )
    ) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(-WHEEL_OVERLAP, Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = sp.xl), // Reduced from xl * 2
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(cards, key = { _, c -> c.instanceId }) { index, playCard ->
                val isDragging = draggingId == playCard.instanceId
                val isAnimatingDraw = animatingDrawId == playCard.instanceId

                Box(
                    modifier = Modifier
                        .zIndex(if (isDragging) 100f else index.toFloat())
                        .alpha(if (isAnimatingDraw) 0f else 1f)
                        .graphicsLayer {
                            val layoutInfo = listState.layoutInfo
                            val itemInfo = layoutInfo.visibleItemsInfo.find { it.index == index }
                            
                            if (itemInfo != null) {
                                val viewportCenter = layoutInfo.viewportEndOffset / 2f
                                val itemCenter = itemInfo.offset + itemInfo.size / 2f
                                val deltaX = itemCenter - viewportCenter
                                val angle = (deltaX / radiusPx) * (180f / Math.PI.toFloat())
                                
                                rotationZ = angle
                                translationY = radiusPx * (1f - Math.cos(Math.toRadians(angle.toDouble())).toFloat())
                                transformOrigin = TransformOrigin(0.5f, FAN_PIVOT_Y)
                            }
                        }
                ) {
                    DraggableFieldCard(
                        playCard = playCard,
                        cardWidth = cardWidth,
                        isDragging = isDragging,
                        boxCoords = boxCoords,
                        onClick = { rect -> onCardClick(listOf(playCard), rect) },
                        onToggleTap = null,
                        onDragStart = onDragStart,
                        onDragDelta = onDragDelta,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragCancel,
                    )
                }
            }
        }
    }
}

// ── Draggable field card ────────────────────────────────────────────────────

@Composable
private fun DraggableFieldCard(
    playCard: PlayCard,
    cardWidth: Dp,
    isDragging: Boolean,
    boxCoords: LayoutCoordinates?,
    onClick: (Rect) -> Unit,
    onToggleTap: (() -> Unit)?,
    onDragStart: (PlayCard, Offset, Dp) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rectInBox by remember(playCard.instanceId) { mutableStateOf(Rect.Zero) }

    MagicCard(
        card = playCard.card,
        width = cardWidth,
        isTapped = playCard.isTapped,
        sourceHidden = isDragging,
        onClick = null,
        shape = RoundedCornerShape(6.dp), // Reduced radius for small field/hand cards
        modifier = modifier
            .onGloballyPositioned { coords ->
                if (boxCoords != null && boxCoords.isAttached && coords.isAttached) {
                    rectInBox = boxCoords.localBoundingBoxOf(coords)
                }
            }
            .pointerInput(playCard.instanceId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        if (rectInBox == Rect.Zero) {
                            onDragCancel()
                        } else {
                            onDragStart(playCard, rectInBox.center, cardWidth)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        onDragDelta(dragAmount)
                        change.consume()
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                )
            }
            .pointerInput(playCard.instanceId) {
                detectTapGestures(
                    onTap = { onClick(rectInBox) },
                    onDoubleTap = if (onToggleTap != null) { { onToggleTap() } } else null,
                )
            },
    )
}
