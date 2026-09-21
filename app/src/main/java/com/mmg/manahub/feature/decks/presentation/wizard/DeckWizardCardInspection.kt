package com.mmg.manahub.feature.decks.presentation.wizard
// COMMENTS_REVIEWED: 2026-09-21

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.components.MagicCardInspectionOverlay

/** CardRow's thumbnail geometry (44x62dp inside an 8dp row inset, +2dp when the image is its own
 * 48dp touch target) -- derived here rather than exposed by CardRow so the overlay flies from the
 * thumbnail, not from the full-width row (the overlay scales uniformly by rect width). */
private val ROW_INSET = 8.dp
private val INTERACTIVE_IMAGE_INSET = 2.dp
private val THUMBNAIL_WIDTH = 44.dp
private val THUMBNAIL_HEIGHT = 62.dp

/**
 * Session state for a step's single-card [MagicCardInspectionOverlay]: which card is open, the
 * thumbnail rect it flies from (relative to the [WizardCardInspectionHost] Box), and the
 * dismiss-in-flight flag. Held in a plain class so the SEED_PICK and COMMANDER_PICK steps share one
 * wiring instead of two copies of Deck Studio's `inspectionCard`/`inspectionRect`/`isDismissing` trio.
 */
@Stable
internal class WizardCardInspectionState(
    private val rowInsetPx: Float,
    private val interactiveImageInsetPx: Float,
    private val thumbnailSizePx: Size,
) {
    var rootCoordinates by mutableStateOf<LayoutCoordinates?>(null)
    var card by mutableStateOf<Card?>(null)
        private set
    var rect by mutableStateOf(Rect.Zero)
        private set
    var isDismissing by mutableStateOf(false)
        private set

    val isVisible: Boolean get() = card != null

    fun open(card: Card, fromRect: Rect) {
        isDismissing = false
        rect = fromRect
        this.card = card
    }

    /** Opens without a known source rect (e.g. from a modal sheet that is closing) -- flies in
     * from a thumbnail-sized rect at the host's center. */
    fun openFromCenter(card: Card) = open(card, centerRect())

    fun requestDismiss() {
        if (card != null) isDismissing = true
    }

    fun clear() {
        card = null
        isDismissing = false
    }

    /** The thumbnail rect of a [CardRow] whose full bounds are [rowCoordinates], relative to the
     * host Box; [Rect.Zero] until the host is laid out. */
    fun thumbnailRectOf(rowCoordinates: LayoutCoordinates, imageIsInteractive: Boolean): Rect {
        val root = rootCoordinates ?: return Rect.Zero
        if (!root.isAttached || !rowCoordinates.isAttached) return Rect.Zero
        val row = root.localBoundingBoxOf(rowCoordinates)
        val left = row.left + rowInsetPx + if (imageIsInteractive) interactiveImageInsetPx else 0f
        val top = row.center.y - thumbnailSizePx.height / 2f
        return Rect(Offset(left, top), thumbnailSizePx)
    }

    private fun centerRect(): Rect {
        val root = rootCoordinates ?: return Rect.Zero
        val center = Offset(root.size.width / 2f, root.size.height / 2f)
        return Rect(center - Offset(thumbnailSizePx.width / 2f, thumbnailSizePx.height / 2f), thumbnailSizePx)
    }
}

@Composable
internal fun rememberWizardCardInspectionState(): WizardCardInspectionState {
    val density = LocalDensity.current
    return remember(density) {
        with(density) {
            WizardCardInspectionState(
                rowInsetPx = ROW_INSET.toPx(),
                interactiveImageInsetPx = INTERACTIVE_IMAGE_INSET.toPx(),
                thumbnailSizePx = Size(THUMBNAIL_WIDTH.toPx(), THUMBNAIL_HEIGHT.toPx()),
            )
        }
    }
}

/**
 * Full-size Box that hosts [content] and, above it, the single-card [MagicCardInspectionOverlay]
 * for [state]. [actions] renders the overlay's button column for the open card; back press while
 * the overlay is open dismisses it instead of unwinding the wizard.
 */
@Composable
internal fun WizardCardInspectionHost(
    state: WizardCardInspectionState,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.(Card) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().onGloballyPositioned { state.rootCoordinates = it }) {
        content()
        state.card?.let { card ->
            BackHandler(enabled = true) { state.requestDismiss() }
            MagicCardInspectionOverlay(
                card = card,
                initialRect = state.rect,
                isVisible = true,
                isDismissing = state.isDismissing,
                onDismissRequest = state::requestDismiss,
                onDismiss = state::clear,
                actions = { actions(card) },
            )
        }
    }
}
