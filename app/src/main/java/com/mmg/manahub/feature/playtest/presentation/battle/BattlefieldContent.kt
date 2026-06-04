package com.mmg.manahub.feature.playtest.presentation.battle

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.playtest.domain.model.BattlefieldState
import com.mmg.manahub.feature.playtest.domain.model.PlayCard
import com.mmg.manahub.feature.playtest.domain.model.PlaytestSetup
import com.mmg.manahub.feature.playtest.domain.model.PlayZone
import com.mmg.manahub.feature.playtest.presentation.components.CommandZoneArea
import kotlin.math.roundToInt

// ── Drag state ────────────────────────────────────────────────────────────────

/**
 * The "coarse" part of an in-progress cross-zone drag — the bits that change at most
 * once per drag (NOT per pixel).
 *
 * Kept deliberately separate from the per-pixel pointer offset (see [BattlefieldContent]'s
 * `pointerOffset` state): zones only observe this coarse session (via `draggingId`), so a
 * pixel-by-pixel pointer move never invalidates the field zones or hand strip. Only the
 * floating ghost reads the fine offset, and it does so through a deferred lambda
 * (`Modifier.offset { ... }`) that reads the [State] inside the layout phase, so reading it
 * does not invalidate composition at all.
 *
 * @param card The card being dragged.
 * @param fromZone The zone the drag started in.
 * @param startCenter The card's centre in root coordinates at drag start (px).
 * @param cardWidth The display width of the floating ghost.
 */
private data class DragSession(
    val card: PlayCard,
    val fromZone: PlayZone,
    val startCenter: Offset,
    val cardWidth: Dp,
)

/**
 * The simulated battlefield ("fake game") rendered as conditional content inside the
 * playtest hand screen — NOT a separate nav destination. All state is ephemeral and
 * driven by the same ViewModel; nothing here writes to the database.
 *
 * Cross-zone drag & drop: each zone registers its root bounds via
 * [Modifier.onGloballyPositioned]. A long-press lifts a card into a floating ghost
 * (drawn in the root [Box] at max z-index); on release the pointer position is
 * hit-tested against the registered bounds to resolve the destination zone.
 *
 * @param setup The playtest configuration (used for the commander strip).
 * @param battlefield The current ephemeral battlefield state.
 * @param onDrawCard Draws the top library card into the hand.
 * @param onMoveCard Moves a card (by instanceId) to a destination zone.
 * @param onToggleTap Toggles a field card's tapped state.
 * @param onCardClick Opens the full-screen preview for a card.
 * @param onEndTest Requests ending the test (opens the confirmation dialog).
 */
@Composable
fun BattlefieldContent(
    setup: PlaytestSetup,
    battlefield: BattlefieldState,
    onDrawCard: () -> Unit,
    onMoveCard: (instanceId: Long, toZone: PlayZone) -> Unit,
    onToggleTap: (instanceId: Long) -> Unit,
    onCardClick: (Card) -> Unit,
    onEndTest: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val isLandscape =
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Registered root bounds for each droppable zone, keyed by zone.
    val zoneBounds = remember { mutableStateMapOf<PlayZone, Rect>() }

    // B1 — drag state is split into two parts so a per-pixel pointer move only recomposes
    // the floating ghost, never the field zones / hand strip:
    //   • dragSession (COARSE): the card, origin zone, start centre, ghost width. Changes at
    //     most once per drag. Zones observe ONLY this (via `draggingId`).
    //   • pointerOffset (FINE): the accumulated pointer delta. Changes every pixel. Read ONLY
    //     by the ghost, and only inside a deferred `Modifier.offset { }` lambda (layout phase),
    //     so reading it never invalidates composition.
    var dragSession by remember { mutableStateOf<DragSession?>(null) }
    val pointerOffset = remember { mutableStateOf(Offset.Zero) }

    // The zone the pointer currently hovers over (for the dashed highlight). Wrapped in
    // derivedStateOf so it only emits a NEW value when the RESOLVED zone changes (coarse) —
    // moving the pointer within the same zone does not invalidate any zone reading this.
    val hoveredZone: PlayZone? by remember {
        derivedStateOf {
            val session = dragSession ?: return@derivedStateOf null
            val pointer = session.startCenter + pointerOffset.value
            zoneBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
        }
    }

    // Shared per-card drag handlers, parameterised by the card's origin zone.
    val onDragStart: (PlayCard, PlayZone, Offset, Dp) -> Unit = { card, zone, center, width ->
        pointerOffset.value = Offset.Zero
        dragSession = DragSession(card, zone, center, width)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val onDragDelta: (Offset) -> Unit = { delta ->
        // Mutate the fine offset ONLY — leaves dragSession untouched so zones don't recompose.
        pointerOffset.value += delta
    }
    val onDragEnd: () -> Unit = {
        val session = dragSession
        // A2: on the first frame after a rotation the zone bounds may not be registered yet.
        // Dropping with no bounds would resolve to no zone and could mis-place the card —
        // cancel cleanly instead of attempting a hit-test against an empty map.
        if (session != null && zoneBounds.isNotEmpty()) {
            val pointer = session.startCenter + pointerOffset.value
            val target = zoneBounds.entries.firstOrNull { it.value.contains(pointer) }?.key
            if (target != null && target != session.fromZone) {
                onMoveCard(session.card.instanceId, target)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            } else if (target == null) {
                // B3: drop landed outside every zone — the card stays put. Fire a cancel
                // haptic so the user feels the no-op, and let the source card fade back in
                // (the alpha animation lives in BattlefieldCard, keyed off `sourceHidden`).
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        dragSession = null
    }
    val onDragCancel: () -> Unit = {
        // B3: cancelled drag (e.g. gesture interrupted) also fades the source card back in.
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        dragSession = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            LandscapeBattlefield(
                setup        = setup,
                battlefield  = battlefield,
                hoveredZone  = hoveredZone,
                draggingId   = dragSession?.card?.instanceId,
                zoneBounds   = zoneBounds,
                onDrawCard   = onDrawCard,
                onToggleTap  = onToggleTap,
                onCardClick  = onCardClick,
                onEndTest    = onEndTest,
                onDragStart  = onDragStart,
                onDragDelta  = onDragDelta,
                onDragEnd    = onDragEnd,
                onDragCancel = onDragCancel,
            )
        } else {
            PortraitBattlefield(
                setup        = setup,
                battlefield  = battlefield,
                hoveredZone  = hoveredZone,
                draggingId   = dragSession?.card?.instanceId,
                zoneBounds   = zoneBounds,
                onDrawCard   = onDrawCard,
                onToggleTap  = onToggleTap,
                onCardClick  = onCardClick,
                onEndTest    = onEndTest,
                onDragStart  = onDragStart,
                onDragDelta  = onDragDelta,
                onDragEnd    = onDragEnd,
                onDragCancel = onDragCancel,
            )
        }

        // Floating drag ghost — drawn above everything at the pointer position.
        // Reads the COARSE dragSession in composition (card/width change once per drag) but
        // reads the FINE pointerOffset ONLY inside the deferred `offset { }` lambda (layout
        // phase), so per-pixel pointer moves reposition the ghost without recomposing anything.
        dragSession?.let { session ->
            BattlefieldCard(
                card       = session.card.card,
                width      = session.cardWidth,
                isTapped   = session.card.isTapped,
                onClick    = null,
                isGhost    = true,
                modifier   = Modifier
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
    }
}

// ── Portrait layout ───────────────────────────────────────────────────────────

@Composable
private fun PortraitBattlefield(
    setup: PlaytestSetup,
    battlefield: BattlefieldState,
    hoveredZone: PlayZone?,
    draggingId: Long?,
    zoneBounds: MutableMap<PlayZone, Rect>,
    onDrawCard: () -> Unit,
    onToggleTap: (Long) -> Unit,
    onCardClick: (Card) -> Unit,
    onEndTest: () -> Unit,
    onDragStart: (PlayCard, PlayZone, Offset, Dp) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val sp = MaterialTheme.spacing

    Column(modifier = Modifier.fillMaxSize()) {
        if (setup.commanderCard != null) {
            CommandZoneArea(
                commanderCard = setup.commanderCard,
                librarySize   = battlefield.library.size,
                modifier      = Modifier.padding(horizontal = sp.md, vertical = sp.xs),
            )
        }

        // Top utility row: library pile + graveyard pile.
        PileRow(
            librarySize  = battlefield.library.size,
            graveyard    = battlefield.graveyard,
            onDrawCard   = onDrawCard,
            onCardClick  = onCardClick,
            hoveredZone  = hoveredZone,
            zoneBounds   = zoneBounds,
            cardWidth    = FIELD_CARD_WIDTH,
            modifier     = Modifier.padding(horizontal = sp.md, vertical = sp.xs),
        )

        // Battlefield zones (lands + permanents) take the available space.
        Column(
            modifier            = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = sp.md),
            verticalArrangement = Arrangement.spacedBy(sp.sm),
        ) {
            FieldZone(
                zone         = PlayZone.LANDS,
                label        = stringResource(R.string.playtest_battle_zone_lands),
                emptyHint    = stringResource(R.string.playtest_battle_field_empty_lands),
                cards        = battlefield.lands,
                cardWidth    = FIELD_CARD_WIDTH,
                isHovered    = hoveredZone == PlayZone.LANDS,
                draggingId   = draggingId,
                onRegister   = { zoneBounds[PlayZone.LANDS] = it },
                onCardClick  = onCardClick,
                onToggleTap  = onToggleTap,
                onDragStart  = { c, center, w -> onDragStart(c, PlayZone.LANDS, center, w) },
                onDragDelta  = onDragDelta,
                onDragEnd    = onDragEnd,
                onDragCancel = onDragCancel,
                modifier     = Modifier.weight(0.4f),
            )
            FieldZone(
                zone         = PlayZone.PERMANENTS,
                label        = stringResource(R.string.playtest_battle_zone_permanents),
                emptyHint    = stringResource(R.string.playtest_battle_field_empty_permanents),
                cards        = battlefield.permanents,
                cardWidth    = FIELD_CARD_WIDTH,
                isHovered    = hoveredZone == PlayZone.PERMANENTS,
                draggingId   = draggingId,
                onRegister   = { zoneBounds[PlayZone.PERMANENTS] = it },
                onCardClick  = onCardClick,
                onToggleTap  = onToggleTap,
                onDragStart  = { c, center, w -> onDragStart(c, PlayZone.PERMANENTS, center, w) },
                onDragDelta  = onDragDelta,
                onDragEnd    = onDragEnd,
                onDragCancel = onDragCancel,
                modifier     = Modifier.weight(0.6f),
            )
        }

        // Hand strip.
        HandStrip(
            cards        = battlefield.hand,
            cardWidth    = HAND_CARD_WIDTH,
            isHovered    = hoveredZone == PlayZone.HAND,
            draggingId   = draggingId,
            onRegister   = { zoneBounds[PlayZone.HAND] = it },
            onCardClick  = onCardClick,
            onDragStart  = { c, center, w -> onDragStart(c, PlayZone.HAND, center, w) },
            onDragDelta  = onDragDelta,
            onDragEnd    = onDragEnd,
            onDragCancel = onDragCancel,
            modifier     = Modifier.padding(horizontal = sp.md, vertical = sp.xs),
        )

        // End Test bar.
        EndTestBar(onEndTest = onEndTest)
    }
}

// ── Landscape layout ──────────────────────────────────────────────────────────

@Composable
private fun LandscapeBattlefield(
    setup: PlaytestSetup,
    battlefield: BattlefieldState,
    hoveredZone: PlayZone?,
    draggingId: Long?,
    zoneBounds: MutableMap<PlayZone, Rect>,
    onDrawCard: () -> Unit,
    onToggleTap: (Long) -> Unit,
    onCardClick: (Card) -> Unit,
    onEndTest: () -> Unit,
    onDragStart: (PlayCard, PlayZone, Offset, Dp) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    val sp = MaterialTheme.spacing

    Row(modifier = Modifier.fillMaxSize()) {
        // LEFT rail: piles + end-test, stacked vertically.
        Column(
            modifier            = Modifier
                .width(120.dp)
                .fillMaxHeight()
                .navigationBarsPadding()
                .padding(horizontal = sp.sm, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (setup.commanderCard != null) {
                CommandZoneArea(
                    commanderCard = setup.commanderCard,
                    librarySize   = battlefield.library.size,
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
            LibraryPile(
                librarySize = battlefield.library.size,
                onDrawCard  = onDrawCard,
                cardWidth   = FIELD_CARD_WIDTH,
            )
            GraveyardPile(
                graveyard   = battlefield.graveyard,
                cardWidth   = FIELD_CARD_WIDTH,
                isHovered   = hoveredZone == PlayZone.GRAVEYARD,
                onRegister  = { zoneBounds[PlayZone.GRAVEYARD] = it },
                onCardClick = onCardClick,
            )
            Spacer(Modifier.weight(1f))
            EndTestBar(onEndTest = onEndTest, modifier = Modifier.fillMaxWidth())
        }

        // CENTER + RIGHT: field zones over hand.
        Column(
            modifier            = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = sp.sm, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.sm),
        ) {
            FieldZone(
                zone         = PlayZone.LANDS,
                label        = stringResource(R.string.playtest_battle_zone_lands),
                emptyHint    = stringResource(R.string.playtest_battle_field_empty_lands),
                cards        = battlefield.lands,
                cardWidth    = FIELD_CARD_WIDTH,
                isHovered    = hoveredZone == PlayZone.LANDS,
                draggingId   = draggingId,
                onRegister   = { zoneBounds[PlayZone.LANDS] = it },
                onCardClick  = onCardClick,
                onToggleTap  = onToggleTap,
                onDragStart  = { c, center, w -> onDragStart(c, PlayZone.LANDS, center, w) },
                onDragDelta  = onDragDelta,
                onDragEnd    = onDragEnd,
                onDragCancel = onDragCancel,
                modifier     = Modifier.weight(0.4f),
            )
            FieldZone(
                zone         = PlayZone.PERMANENTS,
                label        = stringResource(R.string.playtest_battle_zone_permanents),
                emptyHint    = stringResource(R.string.playtest_battle_field_empty_permanents),
                cards        = battlefield.permanents,
                cardWidth    = FIELD_CARD_WIDTH,
                isHovered    = hoveredZone == PlayZone.PERMANENTS,
                draggingId   = draggingId,
                onRegister   = { zoneBounds[PlayZone.PERMANENTS] = it },
                onCardClick  = onCardClick,
                onToggleTap  = onToggleTap,
                onDragStart  = { c, center, w -> onDragStart(c, PlayZone.PERMANENTS, center, w) },
                onDragDelta  = onDragDelta,
                onDragEnd    = onDragEnd,
                onDragCancel = onDragCancel,
                modifier     = Modifier.weight(0.6f),
            )
            HandStrip(
                cards        = battlefield.hand,
                cardWidth    = HAND_CARD_WIDTH,
                isHovered    = hoveredZone == PlayZone.HAND,
                draggingId   = draggingId,
                onRegister   = { zoneBounds[PlayZone.HAND] = it },
                onCardClick  = onCardClick,
                onDragStart  = { c, center, w -> onDragStart(c, PlayZone.HAND, center, w) },
                onDragDelta  = onDragDelta,
                onDragEnd    = onDragEnd,
                onDragCancel = onDragCancel,
            )
        }
    }
}

// ── Pile row (library + graveyard) ──────────────────────────────────────────────

@Composable
private fun PileRow(
    librarySize: Int,
    graveyard: List<PlayCard>,
    onDrawCard: () -> Unit,
    onCardClick: (Card) -> Unit,
    hoveredZone: PlayZone?,
    zoneBounds: MutableMap<PlayZone, Rect>,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier              = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        LibraryPile(librarySize = librarySize, onDrawCard = onDrawCard, cardWidth = cardWidth)
        GraveyardPile(
            graveyard   = graveyard,
            cardWidth   = cardWidth,
            isHovered   = hoveredZone == PlayZone.GRAVEYARD,
            onRegister  = { zoneBounds[PlayZone.GRAVEYARD] = it },
            onCardClick = onCardClick,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun LibraryPile(
    librarySize: Int,
    onDrawCard: () -> Unit,
    cardWidth: Dp,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                // D2: guarantee a ≥48dp interactive target for the draw tap.
                .minimumInteractiveComponentSize()
                .width(cardWidth)
                .aspectRatio(CARD_ASPECT_W / CARD_ASPECT_H)
                .shadow(4.dp, CardShape)
                .clip(CardShape)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDrawCard() })
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Image(
                painter            = painterResource(R.drawable.mtg_card_back),
                contentDescription = stringResource(R.string.playtest_battle_library_cd, librarySize),
                modifier           = Modifier.fillMaxSize(),
            )
            CountBadge(count = librarySize, modifier = Modifier.padding(sp.xxs))
        }
        Text(
            text     = stringResource(R.string.playtest_battle_draw_card),
            style    = ty.labelSmall,
            color    = mc.textSecondary,
            modifier = Modifier.padding(top = sp.xxs),
        )
    }
}

@Composable
private fun GraveyardPile(
    graveyard: List<PlayCard>,
    cardWidth: Dp,
    isHovered: Boolean,
    onRegister: (Rect) -> Unit,
    onCardClick: (Card) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val top = graveyard.lastOrNull()

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                // D2: guarantee a ≥48dp interactive target for the graveyard tap.
                .minimumInteractiveComponentSize()
                .width(cardWidth)
                .aspectRatio(CARD_ASPECT_W / CARD_ASPECT_H)
                .onGloballyPositioned { onRegister(it.boundsInRoot()) }
                .dashedDropHighlight(isHovered)
                .clip(CardShape)
                .background(mc.surfaceVariant)
                .pointerInput(top?.instanceId) {
                    detectTapGestures(onTap = { top?.let { onCardClick(it.card) } })
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            if (top != null) {
                AsyncImage(
                    model              = top.card.imageNormal,
                    contentDescription = top.card.name,
                    modifier           = Modifier.fillMaxSize(),
                )
            } else {
                Image(
                    painter            = painterResource(R.drawable.mtg_card_back),
                    contentDescription = stringResource(R.string.playtest_battle_graveyard_cd, 0),
                    modifier           = Modifier.fillMaxSize(),
                )
            }
            // M6: an empty graveyard shows the card back only — a "0" badge over it is noise.
            if (graveyard.isNotEmpty()) {
                CountBadge(count = graveyard.size, modifier = Modifier.padding(sp.xxs))
            }
        }
        Text(
            text     = stringResource(R.string.playtest_battle_graveyard_label),
            style    = ty.labelSmall,
            color    = mc.textSecondary,
            modifier = Modifier.padding(top = sp.xxs),
        )
    }
}

// ── Field zone (lands / permanents) ─────────────────────────────────────────────

@Composable
private fun FieldZone(
    zone: PlayZone,
    label: String,
    emptyHint: String,
    cards: List<PlayCard>,
    cardWidth: Dp,
    isHovered: Boolean,
    draggingId: Long?,
    onRegister: (Rect) -> Unit,
    onCardClick: (Card) -> Unit,
    onToggleTap: (Long) -> Unit,
    onDragStart: (PlayCard, Offset, Dp) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = label, style = ty.labelMedium, color = mc.textSecondary)
        Spacer(Modifier.height(sp.xxs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onGloballyPositioned { onRegister(it.boundsInRoot()) }
                .clip(ChipShape)
                .background(mc.surface.copy(alpha = 0.5f))
                .dashedDropHighlight(isHovered),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (cards.isEmpty()) {
                Text(
                    text     = emptyHint,
                    style    = ty.bodySmall,
                    // M5: textSecondary (not textDisabled) — the empty hint is informational,
                    // not a disabled control, and textDisabled can fail AA contrast.
                    color    = mc.textSecondary,
                    modifier = Modifier.padding(horizontal = sp.md),
                )
            } else {
                LazyRow(
                    modifier              = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(sp.xs),
                    verticalAlignment     = Alignment.CenterVertically,
                    contentPadding        = PaddingValues(horizontal = sp.xs),
                ) {
                    items(cards, key = { it.instanceId }) { playCard ->
                        DraggableFieldCard(
                            playCard     = playCard,
                            cardWidth    = cardWidth,
                            isDragging   = draggingId == playCard.instanceId,
                            onClick      = { onCardClick(playCard.card) },
                            onToggleTap  = { onToggleTap(playCard.instanceId) },
                            onDragStart  = onDragStart,
                            onDragDelta  = onDragDelta,
                            onDragEnd    = onDragEnd,
                            onDragCancel = onDragCancel,
                        )
                    }
                }
            }
        }
    }
}

// ── Hand strip ──────────────────────────────────────────────────────────────────

@Composable
private fun HandStrip(
    cards: List<PlayCard>,
    cardWidth: Dp,
    isHovered: Boolean,
    draggingId: Long?,
    onRegister: (Rect) -> Unit,
    onCardClick: (Card) -> Unit,
    onDragStart: (PlayCard, Offset, Dp) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HAND_STRIP_HEIGHT)
            .onGloballyPositioned { onRegister(it.boundsInRoot()) }
            .clip(ChipShape)
            .background(mc.backgroundSecondary)
            .dashedDropHighlight(isHovered),
        contentAlignment = Alignment.Center,
    ) {
        if (cards.isEmpty()) {
            Text(
                text  = stringResource(R.string.playtest_battle_hand_empty),
                style = ty.bodySmall,
                // M5: textSecondary (not textDisabled) for AA contrast.
                color = mc.textSecondary,
            )
        } else {
            LazyRow(
                modifier              = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(sp.xs),
                verticalAlignment     = Alignment.CenterVertically,
                contentPadding        = PaddingValues(horizontal = sp.sm),
            ) {
                items(cards, key = { it.instanceId }) { playCard ->
                    DraggableFieldCard(
                        playCard     = playCard,
                        cardWidth    = cardWidth,
                        isDragging   = draggingId == playCard.instanceId,
                        onClick      = { onCardClick(playCard.card) },
                        onToggleTap  = null, // Hand cards are not tappable.
                        onDragStart  = onDragStart,
                        onDragDelta  = onDragDelta,
                        onDragEnd    = onDragEnd,
                        onDragCancel = onDragCancel,
                    )
                }
            }
        }
    }
}

// ── Draggable card ──────────────────────────────────────────────────────────────

/**
 * A field/hand card wired for long-press drag and (optionally) tap-to-tap.
 *
 * The card's centre in root coordinates is captured via [onGloballyPositioned] so the
 * floating ghost can be positioned correctly when the drag starts. While dragging, the
 * source card is hidden (alpha 0) — the ghost represents it visually.
 */
@Composable
private fun DraggableFieldCard(
    playCard: PlayCard,
    cardWidth: Dp,
    isDragging: Boolean,
    onClick: () -> Unit,
    onToggleTap: (() -> Unit)?,
    onDragStart: (PlayCard, Offset, Dp) -> Unit,
    onDragDelta: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
) {
    var centerInRoot by remember(playCard.instanceId) { mutableStateOf(Offset.Zero) }

    BattlefieldCard(
        card       = playCard.card,
        width      = cardWidth,
        isTapped   = playCard.isTapped,
        isGhost    = false,
        sourceHidden = isDragging,
        // A3: the parent owns ALL pointer handling (tap + double-tap + drag) in single
        // pointerInput blocks. Passing onClick = null tells BattlefieldCard NOT to install
        // its own tap detector, so a single tap no longer fires two handlers (which opened
        // the full-screen dialog twice).
        onClick    = null,
        modifier   = Modifier
            .onGloballyPositioned { coords ->
                val b = coords.boundsInRoot()
                centerInRoot = Offset(b.left + b.width / 2f, b.top + b.height / 2f)
            }
            .pointerInput(playCard.instanceId) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        // A1: if the card has not been positioned yet (drag started before
                        // the first layout pass, e.g. on a freshly drawn card), centerInRoot
                        // is still Offset.Zero — starting the drag would snap the ghost to
                        // the screen corner. Cancel instead of starting with a bogus center.
                        if (centerInRoot == Offset.Zero) {
                            onDragCancel()
                        } else {
                            onDragStart(playCard, centerInRoot, cardWidth)
                        }
                    },
                    onDrag      = { change, dragAmount ->
                        onDragDelta(dragAmount)
                        change.consume()
                    },
                    onDragEnd    = { onDragEnd() },
                    onDragCancel = { onDragCancel() },
                )
            }
            // Tap (full-screen preview) + double-tap (toggle tap, field cards only) live in
            // a single detectTapGestures so they never fire together. The long-press drag
            // above is a distinct gesture and does not conflict with tap/double-tap.
            .pointerInput(playCard.instanceId) {
                detectTapGestures(
                    onTap       = { onClick() },
                    onDoubleTap = if (onToggleTap != null) {
                        { onToggleTap() }
                    } else {
                        null
                    },
                )
            },
    )
}

// ── Battlefield card ─────────────────────────────────────────────────────────────

/**
 * A single card rendered on the battlefield. Supports tap rotation and a "ghost" mode
 * (the floating drag avatar) and a hidden-source mode (the in-place card while its
 * ghost is flying).
 */
@Composable
private fun BattlefieldCard(
    card: Card,
    width: Dp,
    onClick: (() -> Unit)?,
    isTapped: Boolean = false,
    isGhost: Boolean = false,
    sourceHidden: Boolean = false,
    instanceId: Long? = null,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val elevation = if (isGhost) 20.dp else 4.dp

    // B3: when a drop lands outside every zone the source card flips from hidden (alpha 0
    // during drag) back to visible. Animate that transition so it fades in smoothly instead
    // of popping. The ghost is never animated (it is removed instantly on drop).
    val sourceAlpha by animateFloatAsState(
        targetValue   = if (sourceHidden) 0f else 1f,
        animationSpec = tween(durationMillis = 180),
        label         = "BattlefieldCardSourceAlpha",
    )

    Box(
        modifier = modifier
            .width(width)
            .aspectRatio(CARD_ASPECT_W / CARD_ASPECT_H)
            .rotate(if (isTapped) 90f else 0f)
            .alpha(sourceAlpha)
            .shadow(elevation, CardShape)
            .clip(CardShape)
            .border(0.5.dp, mc.surfaceVariant, CardShape)
            .background(mc.surface)
            // M1: key the tap pointerInput by the stable instanceId (NOT card.imageNormal —
            // duplicate copies share the same image URL, so a reused LazyRow slot would keep
            // a stale closure). When onClick is null (the parent owns all gestures, or this
            // is the drag ghost) no tap detector is installed.
            .then(
                if (onClick != null) {
                    Modifier.pointerInput(instanceId) {
                        detectTapGestures(onTap = { onClick() })
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        AsyncImage(
            model              = card.imageNormal,
            contentDescription = card.name,
            // B6: Crop (not the default Fit) so card art fills the frame without letterboxing,
            // matching PlaytestHandCard and the rest of the app's card rendering.
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )
    }
}

// ── End-test bar ─────────────────────────────────────────────────────────────────

@Composable
private fun EndTestBar(
    onEndTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = sp.md, vertical = sp.sm),
    ) {
        // D3: a filled lifeNegative button with background-coloured text failed AA contrast
        // in several palettes (≈2.6:1 on NeonVoid, ≈3.4:1 on HallowedPrint). An OutlinedButton
        // draws lifeNegative as both the border and the label on top of the background, where
        // lifeNegative is the designated foreground accent — guaranteed readable in all 12 palettes.
        OutlinedButton(
            onClick  = onEndTest,
            shape    = ButtonShape,
            border   = BorderStroke(1.dp, mc.lifeNegative),
            colors   = ButtonDefaults.outlinedButtonColors(contentColor = mc.lifeNegative),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text  = stringResource(R.string.playtest_battle_action_end_test),
                style = ty.labelLarge,
                color = mc.lifeNegative,
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────────

@Composable
private fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Text(
        text     = count.toString(),
        style    = ty.labelMedium,
        color    = mc.background,
        modifier = modifier
            .clip(ChipShape)
            .background(mc.primaryAccent)
            .padding(horizontal = MaterialTheme.spacing.xs, vertical = MaterialTheme.spacing.xxs),
    )
}

/**
 * Draws a dashed accent border around a drop target while it is being hovered.
 * Uses [Modifier.drawBehind] (NOT `Modifier.border`) so the highlight never affects
 * layout and can be a dashed stroke.
 */
@Composable
private fun Modifier.dashedDropHighlight(active: Boolean): Modifier {
    val accent = MaterialTheme.magicColors.primaryAccent
    val strokePx = with(LocalDensity.current) { 2.dp.toPx() }
    val dashPx = with(LocalDensity.current) { 6.dp.toPx() }
    val radiusPx = with(LocalDensity.current) { 8.dp.toPx() }
    return this.drawBehind {
        if (active) {
            drawRoundRect(
                color       = accent,
                style       = Stroke(
                    width       = strokePx,
                    pathEffect  = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx)),
                ),
                cornerRadius = CornerRadius(radiusPx, radiusPx),
            )
        }
    }
}

// ── Sizing constants ─────────────────────────────────────────────────────────────

// B7: the standard MTG card aspect ratio, shared between `aspectRatio(W/H)` callers and the
// ghost's manual height computation (`w * H/W`) so the two can never drift apart.
private const val CARD_ASPECT_W = 63f
private const val CARD_ASPECT_H = 88f

private val FIELD_CARD_WIDTH = 64.dp
private val HAND_CARD_WIDTH = 72.dp
private val HAND_STRIP_HEIGHT = 116.dp
