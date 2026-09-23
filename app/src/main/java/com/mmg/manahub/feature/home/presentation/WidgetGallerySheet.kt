package com.mmg.manahub.feature.home.presentation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mmg.manahub.R
import com.mmg.manahub.core.FeatureFlags
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Unified catalog for managing the dashboard layout: widgets grouped by category, with add/remove
 * per row, long-press drag to reorder rows within a category or whole categories, and "Move up" /
 * "Move down" accessibility actions for both.
 *
 * The sheet edits ONE optimistic working copy of the layout synchronously (so a row never vanishes
 * for a frame) and commits each change to the ViewModel, which serializes the writes. An incoming
 * layout replaces the working copy only when it differs and no commit of ours is still in flight.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetGallerySheet(
    currentLayout: List<WidgetInstance>,
    isAuthenticated: Boolean,
    gamificationEnabled: Boolean,
    competitiveEnabled: Boolean,
    onAddWidget: (HomeWidgetType) -> Unit,
    onRemoveWidget: (HomeWidgetType) -> Unit,
    onUpdateLayout: (List<WidgetInstance>) -> Unit,
    onCreateAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val haptic = LocalHapticFeedback.current
    val gapPx = with(LocalDensity.current) { spacing.sm.toPx() }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val editor = remember { GalleryEditor(currentLayout, listState, scope) }
    editor.isVisible = { type -> isGalleryVisible(type, gamificationEnabled, competitiveEnabled) }
    editor.onAddWidget = onAddWidget
    editor.onRemoveWidget = onRemoveWidget
    editor.onUpdateLayout = onUpdateLayout

    LaunchedEffect(currentLayout) { editor.onIncomingLayout(currentLayout) }

    // Back and the scrim close the sheet unless a drag is in progress.
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !editor.gestureActive },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textDisabled.copy(alpha = 0.4f)) },
        contentWindowInsets = { WindowInsets(0) }, // Handle insets manually to avoid overflow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.lg)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.offset(x = -spacing.md),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = mc.textSecondary,
                    )
                }
                Text(
                    text = stringResource(R.string.home_widget_gallery_title),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }

            val visibleCategories = editor.visibleCategories()
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                visibleCategories.forEach { category ->
                    val rows = editor.rows(category)
                    val blockLifted = editor.draggedCategory == category
                    val blockDragging = blockLifted && editor.gestureActive

                    item(key = galleryHeaderKey(category)) {
                        CategoryHeader(
                            category = category,
                            canMoveUp = galleryCategoryNeighbor(category, GalleryMoveDirection.UP, visibleCategories) != null,
                            canMoveDown = galleryCategoryNeighbor(category, GalleryMoveDirection.DOWN, visibleCategories) != null,
                            onMove = { direction -> editor.stepCategory(category, direction) },
                            modifier = Modifier
                                .animateItem(placementSpec = if (blockDragging) null else GalleryPlacementSpec)
                                .zIndex(if (blockLifted) LIFTED_Z else RESTING_Z)
                                .graphicsLayer { translationY = if (blockLifted) editor.dragOffset else 0f },
                            dragHandleModifier = if (category != WidgetCategory.ACTIVITY) {
                                Modifier.pointerInput(category) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            editor.startCategoryDrag(category)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            if (editor.dragCategory(category, amount.y, gapPx)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            }
                                        },
                                        onDragEnd = { editor.endCategoryDrag(commit = true) },
                                        onDragCancel = { editor.endCategoryDrag(commit = false) },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                    }

                    items(rows, key = ::galleryRowKey) { type ->
                        val isAdded = type in editor.placedTypes
                        val isFixed = type.isAlwaysPresent
                        val draggable = isAdded && !isFixed && category != WidgetCategory.ACTIVITY
                        val rowLifted = editor.draggedType == type || blockLifted
                        val rowDragging = rowLifted && editor.gestureActive

                        CatalogRow(
                            type = type,
                            isAdded = isAdded,
                            isFixed = isFixed,
                            isLocked = type.audience == WidgetAudience.ACCOUNT_GATED && !isAuthenticated,
                            isDragging = rowDragging,
                            canMoveUp = draggable && editor.canStepWidget(type, GalleryMoveDirection.UP),
                            canMoveDown = draggable && editor.canStepWidget(type, GalleryMoveDirection.DOWN),
                            onMove = { direction -> editor.stepWidget(type, direction) },
                            modifier = Modifier
                                .animateItem(placementSpec = if (rowDragging) null else GalleryPlacementSpec)
                                .zIndex(
                                    when {
                                        editor.draggedType == type -> LIFTED_ROW_Z
                                        blockLifted -> LIFTED_Z
                                        else -> RESTING_Z
                                    },
                                )
                                .graphicsLayer { translationY = if (rowLifted) editor.dragOffset else 0f },
                            onAdd = { editor.add(type) },
                            onRemove = { editor.remove(type) },
                            onUnlock = onCreateAccount,
                            dragHandleModifier = if (draggable) {
                                Modifier.pointerInput(type) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            editor.startWidgetDrag(type)
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            if (editor.dragWidget(type, amount.y, gapPx)) {
                                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                            }
                                        },
                                        onDragEnd = { editor.endWidgetDrag(commit = true) },
                                        onDragCancel = { editor.endWidgetDrag(commit = false) },
                                    )
                                }
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }

            MagicCtaButton(
                onClick = onDismiss,
                text = stringResource(R.string.home_widget_gallery_done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Working-copy editor
// ─────────────────────────────────────────────────────────────────────────────

private val GalleryPlacementSpec = spring(
    stiffness = Spring.StiffnessMediumLow,
    visibilityThreshold = IntOffset.VisibilityThreshold,
)

private const val RESTING_Z = 1f
private const val LIFTED_Z = 10f
private const val LIFTED_ROW_Z = 11f

private const val GALLERY_ROW_KEY_PREFIX = "gallery_"
private const val GALLERY_HEADER_KEY_PREFIX = "header_"

private fun galleryRowKey(type: HomeWidgetType): String = GALLERY_ROW_KEY_PREFIX + type.persistedId

private fun galleryHeaderKey(category: WidgetCategory): String = GALLERY_HEADER_KEY_PREFIX + category.name

/** Whether [type] is offered in the gallery at all under the current feature flags. */
private fun isGalleryVisible(type: HomeWidgetType, gamificationEnabled: Boolean, competitiveEnabled: Boolean): Boolean =
    (gamificationEnabled || !type.isGamification) &&
        (FeatureFlags.Puzzle.PUZZLE_ENABLED || type != HomeWidgetType.DAILY_PUZZLE) &&
        (competitiveEnabled || type != HomeWidgetType.COMPETITIVE)

/**
 * Holds the sheet's optimistic layout, the gallery category order and the drag state. Every
 * mutation keeps the first visible row at the same index and offset, so adding, removing or
 * reordering never scrolls the list under the user's finger.
 */
@Stable
private class GalleryEditor(
    initialLayout: List<WidgetInstance>,
    private val listState: LazyListState,
    private val scope: CoroutineScope,
) {
    val working = mutableStateListOf<WidgetInstance>().apply {
        addAll(initialLayout.distinctBy { it.type.persistedId })
    }
    val categoryOrder = mutableStateListOf<WidgetCategory>().apply {
        addAll(reconcileGalleryCategoryOrder(emptyList(), working))
    }
    val placedTypes: Set<HomeWidgetType> by derivedStateOf { working.mapTo(HashSet()) { it.type } }

    var draggedType by mutableStateOf<HomeWidgetType?>(null)
        private set
    var draggedCategory by mutableStateOf<WidgetCategory?>(null)
        private set
    var gestureActive by mutableStateOf(false)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    var isVisible: (HomeWidgetType) -> Boolean = { true }
    var onAddWidget: (HomeWidgetType) -> Unit = {}
    var onRemoveWidget: (HomeWidgetType) -> Unit = {}
    var onUpdateLayout: (List<WidgetInstance>) -> Unit = {}

    // The last layout we committed whose echo has not come back yet; older echoes are ignored.
    private var pendingLayout: List<WidgetInstance>? = null
    private var layoutBeforeDrag: List<WidgetInstance> = emptyList()
    private var orderBeforeDrag: List<WidgetCategory> = emptyList()
    private var settleJob: Job? = null

    fun rows(category: WidgetCategory): List<HomeWidgetType> = galleryRowsFor(category, working, isVisible)

    fun visibleCategories(): List<WidgetCategory> = categoryOrder.filter { rows(it).isNotEmpty() }

    fun canStepWidget(type: HomeWidgetType, direction: GalleryMoveDirection): Boolean =
        working.galleryStepNeighbor(type, direction, isVisible) != null

    /** Adopts a layout emitted by the ViewModel unless it is an echo of an older commit of ours. */
    fun onIncomingLayout(layout: List<WidgetInstance>) {
        val incoming = layout.distinctBy { it.type.persistedId }
        val pending = pendingLayout
        if (pending != null) {
            if (incoming == pending) pendingLayout = null
            return
        }
        if (gestureActive || incoming == working.toList()) return
        anchored { replaceWorking(incoming) }
    }

    fun add(type: HomeWidgetType) {
        val next = working.withWidgetAdded(type)
        if (next.size == working.size) return
        anchored { replaceWorking(next) }
        pendingLayout = next
        onAddWidget(type)
    }

    fun remove(type: HomeWidgetType) {
        if (type.isAlwaysPresent) return
        val next = working.filterNot { it.type == type }
        if (next.size == working.size) return
        anchored { replaceWorking(next) }
        pendingLayout = next
        onRemoveWidget(type)
    }

    /** One-step move (accessibility action); commits immediately. */
    fun stepWidget(type: HomeWidgetType, direction: GalleryMoveDirection): Boolean {
        val next = working.withWidgetStepped(type, direction, isVisible) ?: return false
        anchored { replaceWorking(next) }
        commit(next)
        return true
    }

    /** One-step category move (accessibility action); commits immediately. */
    fun stepCategory(category: WidgetCategory, direction: GalleryMoveDirection): Boolean {
        val order = categoryOrder.withCategoryStepped(category, direction, visibleCategories()) ?: return false
        anchored {
            categoryOrder.setAll(order)
            replaceWorking(working.sortedByCategoryOrder(order))
        }
        commit(working.toList())
        return true
    }

    fun startWidgetDrag(type: HomeWidgetType) {
        beginGesture()
        draggedType = type
    }

    /** Applies a drag delta; returns true when the row swapped with a neighbour. */
    fun dragWidget(type: HomeWidgetType, deltaY: Float, gapPx: Float): Boolean {
        if (draggedType != type || !gestureActive) return false
        dragOffset += deltaY
        val direction = if (dragOffset > 0f) GalleryMoveDirection.DOWN else GalleryMoveDirection.UP
        val neighbor = working.galleryStepNeighbor(type, direction, isVisible) ?: return false
        val neighborSize = itemSizePx(galleryRowKey(neighbor)) ?: itemSizePx(galleryRowKey(type)) ?: return false
        val stepPx = neighborSize + gapPx
        if (abs(dragOffset) < stepPx / 2f) return false
        val next = working.withWidgetStepped(type, direction, isVisible) ?: return false
        anchored { replaceWorking(next) }
        dragOffset -= direction.step * stepPx
        return true
    }

    fun endWidgetDrag(commit: Boolean) {
        if (!gestureActive) return
        if (!commit) {
            anchored { replaceWorking(layoutBeforeDrag) }
        } else if (working.toList() != layoutBeforeDrag) {
            commit(working.toList())
        }
        settle()
    }

    fun startCategoryDrag(category: WidgetCategory) {
        beginGesture()
        draggedCategory = category
    }

    /** Applies a drag delta to a whole category block; returns true when it swapped with a neighbour. */
    fun dragCategory(category: WidgetCategory, deltaY: Float, gapPx: Float): Boolean {
        if (draggedCategory != category || !gestureActive) return false
        dragOffset += deltaY
        val direction = if (dragOffset > 0f) GalleryMoveDirection.DOWN else GalleryMoveDirection.UP
        val visible = visibleCategories()
        val neighbor = galleryCategoryNeighbor(category, direction, visible) ?: return false
        val stepPx = blockHeightPx(neighbor, gapPx) + gapPx
        if (abs(dragOffset) < stepPx / 2f) return false
        val order = categoryOrder.withCategoryStepped(category, direction, visible) ?: return false
        anchored { categoryOrder.setAll(order) }
        dragOffset -= direction.step * stepPx
        return true
    }

    fun endCategoryDrag(commit: Boolean) {
        if (!gestureActive) return
        if (!commit) {
            anchored { categoryOrder.setAll(orderBeforeDrag) }
        } else {
            val regrouped = working.sortedByCategoryOrder(categoryOrder)
            if (regrouped != layoutBeforeDrag) {
                anchored { replaceWorking(regrouped) }
                commit(regrouped)
            }
        }
        settle()
    }

    private fun beginGesture() {
        settleJob?.cancel()
        layoutBeforeDrag = working.toList()
        orderBeforeDrag = categoryOrder.toList()
        draggedType = null
        draggedCategory = null
        dragOffset = 0f
        gestureActive = true
    }

    // The dropped row glides from the finger to its slot instead of snapping.
    private fun settle() {
        gestureActive = false
        settleJob = scope.launch {
            animate(initialValue = dragOffset, targetValue = 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                dragOffset = value
            }
            draggedType = null
            draggedCategory = null
        }
    }

    private fun commit(layout: List<WidgetInstance>) {
        pendingLayout = layout
        onUpdateLayout(layout)
    }

    private fun replaceWorking(layout: List<WidgetInstance>) {
        working.setAll(layout)
        categoryOrder.setAll(reconcileGalleryCategoryOrder(categoryOrder, layout))
    }

    // requestScrollToItem pins the viewport by index, overriding the default follow-the-key anchoring.
    private fun anchored(mutation: () -> Unit) {
        val index = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        mutation()
        listState.requestScrollToItem(index, offset)
    }

    private fun itemSizePx(key: String): Int? =
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.size

    // Off-screen parts of a block are estimated from the measured on-screen rows and headers.
    private fun blockHeightPx(category: WidgetCategory, gapPx: Float): Float {
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val sizes = visibleItems.associate { it.key to it.size }
        val rowSizes = visibleItems.filter { (it.key as? String)?.startsWith(GALLERY_ROW_KEY_PREFIX) == true }.map { it.size }
        val headerSizes = visibleItems.filter { (it.key as? String)?.startsWith(GALLERY_HEADER_KEY_PREFIX) == true }.map { it.size }
        val rowFallback = rowSizes.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f
        val headerFallback = headerSizes.average().takeIf { !it.isNaN() }?.toFloat() ?: rowFallback
        val rowKeys = rows(category).map(::galleryRowKey)
        val header = sizes[galleryHeaderKey(category)]?.toFloat() ?: headerFallback
        return header + rowKeys.sumOf { (sizes[it]?.toFloat() ?: rowFallback).toDouble() }.toFloat() + gapPx * rowKeys.size
    }

    private fun <T> MutableList<T>.setAll(items: List<T>) {
        if (this == items) return
        clear()
        addAll(items)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Rows
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryHeader(
    category: WidgetCategory,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (GalleryMoveDirection) -> Boolean,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val moveUpLabel = stringResource(R.string.home_widget_move_up)
    val moveDownLabel = stringResource(R.string.home_widget_move_down)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HeaderMinHeight)
            .padding(top = spacing.xs)
            .semantics(mergeDescendants = true) {
                heading()
                customActions = moveActions(canMoveUp, canMoveDown, moveUpLabel, moveDownLabel, onMove)
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        // ACTIVITY is pinned: no handle and no leading spacer, so its title sits flush-left.
        if (category != WidgetCategory.ACTIVITY) {
            Box(
                modifier = dragHandleModifier.minimumInteractiveComponentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = mc.secondaryAccent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = mc.secondaryAccent,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = category.displayName.uppercase(),
            style = ty.labelMedium,
            color = mc.secondaryAccent,
        )
    }
}

private val HeaderMinHeight = 48.dp

@Composable
private fun CatalogRow(
    type: HomeWidgetType,
    isAdded: Boolean,
    isFixed: Boolean,
    isLocked: Boolean,
    isDragging: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (GalleryMoveDirection) -> Boolean,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onUnlock: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val moveUpLabel = stringResource(R.string.home_widget_move_up)
    val moveDownLabel = stringResource(R.string.home_widget_move_down)

    Surface(
        color = if (isFixed) mc.textDisabled.copy(alpha = SUBTLE_ROW_FILL_ALPHA) else mc.surface,
        shape = SmallCardShape,
        // The whole placed row is the long-press drag target; dragHandleModifier carries the gesture.
        modifier = modifier
            .fillMaxWidth()
            .then(dragHandleModifier)
            .semantics {
                customActions = moveActions(canMoveUp, canMoveDown, moveUpLabel, moveDownLabel, onMove)
            }
            .alpha(if (isDragging) DRAGGING_ROW_ALPHA else 1f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ChipShape)
                    .background(mc.textDisabled.copy(alpha = SUBTLE_ROW_FILL_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else type.icon,
                    contentDescription = null,
                    tint = when {
                        isLocked -> mc.secondaryAccent
                        isFixed -> mc.textSecondary
                        else -> mc.primaryAccent
                    },
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(type.defaultTitleRes),
                    style = ty.titleMedium,
                    color = if (isFixed) mc.textSecondary else mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = type.description,
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            when {
                isFixed -> Text(
                    text = stringResource(R.string.home_widget_fixed),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(end = spacing.sm),
                )
                isAdded || !isLocked -> AddRemoveButton(isAdded = isAdded, onAdd = onAdd, onRemove = onRemove)
                else -> MagicCtaButton(
                    onClick = onUnlock,
                    text = stringResource(R.string.home_account_gated_lock),
                    style = MagicCtaStyle.Outlined,
                    color = MagicCtaColor.Primary,
                    contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.md),
                )
            }
        }
    }
}

/**
 * The row's Add / Remove toggle. Both labels are laid out invisibly underneath so the button keeps
 * one size when it flips, and the row's text never re-flows.
 */
@Composable
private fun AddRemoveButton(isAdded: Boolean, onAdd: () -> Unit, onRemove: () -> Unit) {
    val ghost = Modifier.alpha(0f).clearAndSetSemantics { }
    Box {
        GalleryActionButton(isAdded = true, onClick = {}, enabled = false, modifier = ghost)
        GalleryActionButton(isAdded = false, onClick = {}, enabled = false, modifier = ghost)
        GalleryActionButton(
            isAdded = isAdded,
            onClick = if (isAdded) onRemove else onAdd,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun GalleryActionButton(
    isAdded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = MaterialTheme.spacing
    MagicCtaButton(
        onClick = onClick,
        text = stringResource(if (isAdded) R.string.home_widget_remove else R.string.home_widget_add),
        enabled = enabled,
        style = MagicCtaStyle.Outlined,
        color = if (isAdded) MagicCtaColor.Error else MagicCtaColor.Primary,
        contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.md),
        icon = {
            Icon(
                imageVector = if (isAdded) Icons.Default.Close else Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        modifier = modifier,
    )
}

private fun moveActions(
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    moveUpLabel: String,
    moveDownLabel: String,
    onMove: (GalleryMoveDirection) -> Boolean,
): List<CustomAccessibilityAction> = buildList {
    if (canMoveUp) add(CustomAccessibilityAction(moveUpLabel) { onMove(GalleryMoveDirection.UP) })
    if (canMoveDown) add(CustomAccessibilityAction(moveDownLabel) { onMove(GalleryMoveDirection.DOWN) })
}

// surfaceVariant is ~1.1:1 on HallowedPrint; a low-alpha textDisabled fill stays visible on every palette.
private const val SUBTLE_ROW_FILL_ALPHA = 0.10f

private const val DRAGGING_ROW_ALPHA = 0.85f

private val WidgetCategory.displayName: String
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        WidgetCategory.ACTIVITY -> stringResource(R.string.home_gallery_category_activity)
        WidgetCategory.STATS -> stringResource(R.string.home_gallery_category_stats)
        WidgetCategory.COLLECTION -> stringResource(R.string.home_gallery_category_collection)
        WidgetCategory.DISCOVER -> stringResource(R.string.home_gallery_category_discover)
        WidgetCategory.SOCIAL -> stringResource(R.string.home_gallery_category_social)
        WidgetCategory.TOURNAMENT -> stringResource(R.string.home_gallery_category_tournament)
        WidgetCategory.COMMUNITY -> stringResource(R.string.home_gallery_category_community)
    }

private val WidgetCategory.icon: ImageVector
    get() = when (this) {
        WidgetCategory.ACTIVITY -> Icons.Default.Timeline
        WidgetCategory.STATS -> Icons.Default.AutoAwesome
        WidgetCategory.COLLECTION -> Icons.Default.Style
        WidgetCategory.DISCOVER -> Icons.Default.Visibility
        WidgetCategory.SOCIAL -> Icons.Default.Group
        WidgetCategory.TOURNAMENT -> Icons.Default.EmojiEvents
        WidgetCategory.COMMUNITY -> Icons.Default.Group
    }

/** Short English description shown under each widget title in the gallery. */
private val HomeWidgetType.description: String
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        HomeWidgetType.CONTEXT_HERO -> stringResource(R.string.home_widget_desc_context_hero)
        HomeWidgetType.QUICK_ACTIONS -> stringResource(R.string.home_widget_desc_quick_actions)
        HomeWidgetType.PROGRESSION_HUB -> stringResource(R.string.home_widget_desc_progression_hub)
        HomeWidgetType.QUESTS_HUB -> stringResource(R.string.home_widget_desc_quests_hub)
        HomeWidgetType.GAME_STATS_HUB -> stringResource(R.string.home_widget_desc_game_stats_hub)
        HomeWidgetType.COLLECTION_STATS_HUB -> stringResource(R.string.home_widget_desc_collection_stats_hub)
        HomeWidgetType.YOUR_DECKS_SHELF -> stringResource(R.string.home_widget_desc_decks_shelf)
        HomeWidgetType.WISHLIST_PROGRESS -> stringResource(R.string.home_widget_desc_wishlist)
        HomeWidgetType.RECENTLY_ADDED -> stringResource(R.string.home_widget_desc_recently_added)
        HomeWidgetType.DISCOVER_CARDS -> stringResource(R.string.home_widget_desc_discover)
        HomeWidgetType.CARD_OF_THE_DAY -> stringResource(R.string.home_widget_desc_card_of_day)
        HomeWidgetType.LATEST_SETS -> stringResource(R.string.home_widget_desc_latest_sets)
        HomeWidgetType.MTG_NEWS -> stringResource(R.string.home_widget_desc_news)
        HomeWidgetType.RULES_TIP -> stringResource(R.string.home_widget_desc_rules_tip)
        HomeWidgetType.TRADES_HUB -> stringResource(R.string.home_widget_desc_trades_hub)
        HomeWidgetType.FRIENDS -> stringResource(R.string.home_widget_desc_friends)
        HomeWidgetType.TRENDING_COMMANDERS -> stringResource(R.string.home_widget_desc_trending_commanders)
        HomeWidgetType.COMMUNITY_DECKS -> stringResource(R.string.home_widget_desc_community_decks)
        HomeWidgetType.DAILY_PUZZLE -> stringResource(R.string.home_widget_desc_daily_puzzle)
        HomeWidgetType.COMPETITIVE -> stringResource(R.string.home_widget_desc_competitive)
    }
