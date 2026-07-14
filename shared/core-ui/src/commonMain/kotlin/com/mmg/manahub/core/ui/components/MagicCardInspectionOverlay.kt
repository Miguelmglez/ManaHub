package com.mmg.manahub.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.spacing
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

private const val CARD_ASPECT_RATIO = 63f / 88f

/**
 * An animated overlay that makes a card fly from its [initialRect] to the center
 * of the screen, providing a slot for contextual actions. Supports a list of items
 * for browsing through multiple cards (e.g., Graveyard or Exile).
 *
 * @param items The list of items to inspect.
 * @param initialIndex The index of the item that should fly in from [initialRect].
 * @param initialRect The original screen coordinates and size of the card at [initialIndex].
 * @param isVisible Whether the overlay is shown.
 * @param onDismiss Called when the user taps the background or clicks the card.
 * @param cardExtractor Function to get the [Card] from an item.
 * @param isDismissing Whether the overlay is in the process of being dismissed (triggers reverse flight).
 * @param onDismissRequest Called when the user wants to dismiss the overlay.
 * @param onDismiss Called after the exit animation completes and the overlay should be removed from the composition.
 * @param targetExitRect If provided, the current card will fly to this destination before disappearing.
 * @param onAnimationEnd Called after the entry/exit animation completes.
 * @param isTappedExtractor Whether the item should appear tapped (rotated).
 * @param actions Composable slot for buttons to be shown below the card, receives current item.
 */
@Composable
fun <T> MagicCardInspectionOverlay(
    items: List<T>,
    initialIndex: Int,
    initialRect: Rect,
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    onDismiss: () -> Unit,
    cardExtractor: (T) -> Card,
    modifier: Modifier = Modifier,
    isDismissing: Boolean = false,
    targetExitRect: Rect? = null,
    onAnimationEnd: () -> Unit = {},
    isTappedExtractor: (T) -> Boolean = { _ -> false },
    actions: @Composable (ColumnScope.(T) -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val currentOnDismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)
    val currentOnAnimationEnd by androidx.compose.runtime.rememberUpdatedState(onAnimationEnd)

    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { items.size })

    // Animation state
    val animProgress = remember { Animatable(0f) }
    val exitProgress = remember { Animatable(0f) }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - exitProgress.value
                }
                .background(mc.surface.copy(alpha = 0.85f))
                .pointerInput(Unit) {
                    detectTapGestures { if (targetExitRect == null && !isDismissing) onDismissRequest() }
                }
        ) {
            val screenCenter = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)

            // Layout parameters
            val isLandscape = constraints.maxWidth > constraints.maxHeight
            val inspectWidth = if (isLandscape) 220.dp else 280.dp
            val inspectWidthPx = with(density) { inspectWidth.toPx() }
            val inspectHeightPx = inspectWidthPx / CARD_ASPECT_RATIO

            // Buttons sizing estimation for centering
            val buttonSpacingPx = with(density) { sp.sm.toPx() }
            val buttonHeightPx = with(density) { 48.dp.toPx() }
            val totalButtonsHeightPx = (buttonHeightPx * 2) + buttonSpacingPx + with(density) { sp.xl.toPx() }

            val compositionHeightPx = inspectHeightPx + if (actions != null) totalButtonsHeightPx else 0f
            val compositionTopPx = screenCenter.y - (compositionHeightPx / 2f)

            val centerRect = Rect(
                left = screenCenter.x - inspectWidthPx / 2f,
                top = compositionTopPx,
                right = screenCenter.x + inspectWidthPx / 2f,
                bottom = compositionTopPx + inspectHeightPx
            )

            LaunchedEffect(initialIndex) {
                animProgress.snapTo(0f)
                animProgress.animateTo(1f, tween(350))
            }

            LaunchedEffect(targetExitRect != null, isDismissing) {
                if (targetExitRect != null || isDismissing) {
                    val isMovingToZone = targetExitRect != null
                    exitProgress.snapTo(0f)
                    exitProgress.animateTo(1f, tween(400))
                    if (isMovingToZone) {
                        currentOnAnimationEnd()
                    } else {
                        currentOnDismiss()
                    }
                }
            }

            val currentRect = if (targetExitRect != null) {
                // Moving to a specific zone (Grave, Exile, etc.)
                lerp(centerRect, targetExitRect, exitProgress.value)
            } else if (isDismissing) {
                // Normal dismissal back to original position
                lerp(centerRect, initialRect, exitProgress.value)
            } else {
                // Entry animation
                lerp(initialRect, centerRect, animProgress.value)
            }

            val buttonsAlpha = if (animProgress.value > 0.8f && targetExitRect == null && !isDismissing) {
                (animProgress.value - 0.8f) * 5f * (1f - exitProgress.value)
            } else 0f

            Box(modifier = Modifier.fillMaxSize()) {
                if (animProgress.value < 1f || targetExitRect != null || isDismissing) {
                    // Flying card animation (entry or exit)
                    val cardToShow = items.getOrNull(pagerState.currentPage)?.let { cardExtractor(it) }

                    if (cardToShow != null) {
                        MagicCard(
                            card = cardToShow,
                            width = inspectWidth,
                            isTapped = items.getOrNull(pagerState.currentPage)?.let { isTappedExtractor(it) } ?: false,
                            onClick = { if (targetExitRect == null && !isDismissing) onDismissRequest() },
                            animateAlpha = false,
                            modifier = Modifier
                                .offset {
                                    IntOffset(
                                        centerRect.left.roundToInt(),
                                        centerRect.top.roundToInt()
                                    )
                                }
                                .graphicsLayer {
                                    val scale = if (targetExitRect != null) {
                                        lerp(1f, targetExitRect.width / centerRect.width, exitProgress.value)
                                    } else if (isDismissing) {
                                        lerp(1f, initialRect.width / centerRect.width, exitProgress.value)
                                    } else {
                                        lerp(initialRect.width / centerRect.width, 1f, animProgress.value)
                                    }
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = currentRect.left - centerRect.left
                                    translationY = currentRect.top - centerRect.top
                                    transformOrigin = TransformOrigin(0f, 0f)
                                }
                                .zIndex(100f)
                        )
                    }
                } else {
                    // Pager for multiple cards
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(buttonsAlpha)
                            .zIndex(100f)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.Top,
                            key = { index -> items[index].hashCode() }
                        ) { index ->
                            val item = items[index]
                            val card = cardExtractor(item)
                            val isTapped = isTappedExtractor(item)

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val pageOffset = ((pagerState.currentPage - index) + pagerState.currentPageOffsetFraction).absoluteValue
                                        alpha = 1f - pageOffset.coerceIn(0f, 1f)
                                        scaleX = 0.8f + alpha * 0.2f
                                        scaleY = 0.8f + alpha * 0.2f
                                    },
                                contentAlignment = Alignment.TopCenter
                            ) {
                                MagicCard(
                                    card = card,
                                    width = inspectWidth,
                                    isTapped = isTapped,
                                    onClick = { onDismiss() },
                                    animateAlpha = false,
                                    modifier = Modifier.offset(y = with(density) { compositionTopPx.toDp() })
                                )
                            }
                        }

                        // Navigation arrows
                        if (items.size > 1) {
                            val showLeft by remember { derivedStateOf { pagerState.currentPage > 0 } }
                            val showRight by remember { derivedStateOf { pagerState.currentPage < items.size - 1 } }

                            Row(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = sp.md)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                NavigationArrow(
                                    icon = Icons.Default.ChevronLeft,
                                    visible = showLeft,
                                    onClick = {
                                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                                    }
                                )

                                NavigationArrow(
                                    icon = Icons.Default.ChevronRight,
                                    visible = showRight,
                                    onClick = {
                                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                                    }
                                )
                            }
                        }
                    }
                }

                // Action buttons below the card
                if (actions != null && buttonsAlpha > 0f) {
                    val currentItem = items.getOrNull(pagerState.currentPage)
                    if (currentItem != null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = with(density) { (compositionTopPx + inspectHeightPx).toDp() + sp.xl })
                                .width(inspectWidth)
                                .alpha(buttonsAlpha)
                                .zIndex(200f)
                                .navigationBarsPadding(),
                            verticalArrangement = Arrangement.spacedBy(sp.sm),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            actions(currentItem)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    visible: Boolean,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f)
    val mc = MaterialTheme.magicColors

    Box(
        modifier = Modifier
            .size(48.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(mc.surface.copy(alpha = 0.5f))
            .clickable(enabled = visible, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = mc.textPrimary,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun MagicCardInspectionOverlay(
    card: Card,
    initialRect: Rect,
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isDismissing: Boolean = false,
    targetExitRect: Rect? = null,
    onAnimationEnd: () -> Unit = {},
    isTapped: Boolean = false,
    actions: @Composable (ColumnScope.() -> Unit)? = null,
) {
    MagicCardInspectionOverlay(
        items = listOf(card),
        initialIndex = 0,
        initialRect = initialRect,
        isVisible = isVisible,
        onDismissRequest = onDismissRequest,
        onDismiss = onDismiss,
        cardExtractor = { it },
        modifier = modifier,
        isDismissing = isDismissing,
        targetExitRect = targetExitRect,
        onAnimationEnd = onAnimationEnd,
        isTappedExtractor = { isTapped },
        actions = actions?.let { { _ -> actions() } }
    )
}

/** Linear interpolation for Rects. */
private fun lerp(start: Rect, end: Rect, fraction: Float): Rect {
    return Rect(
        left = lerp(start.left, end.left, fraction),
        top = lerp(start.top, end.top, fraction),
        right = lerp(start.right, end.right, fraction),
        bottom = lerp(start.bottom, end.bottom, fraction)
    )
}

/** Linear interpolation for Floats. */
private fun lerp(start: Float, end: Float, fraction: Float): Float {
    return start + (end - start) * fraction
}
