package com.mmg.manahub.feature.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * Unified catalog for managing the dashboard layout.
 *
 * This version integrates reordering directly into the category-grouped list,
 * removing the separate "Current Layout" section. Reordering happens at two
 * levels: entire categories can be moved, and items can be moved within or
 * between categories.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetGallerySheet(
    currentLayout: List<WidgetInstance>,
    isAuthenticated: Boolean,
    gamificationEnabled: Boolean,
    onAddWidget: (HomeWidgetType) -> Unit,
    onRemoveWidget: (HomeWidgetType) -> Unit,
    onMoveWidget: (from: Int, to: Int) -> Unit,
    onUpdateLayout: (List<WidgetInstance>) -> Unit,
    onCreateAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    // Derive the initial category order from the current layout.
    val initialCategories = remember(currentLayout) {
        val inOrder = currentLayout.map { it.type.category }.distinct()
        val others = WidgetCategory.entries.filter { it !in inOrder }
        inOrder + others
    }

    val localCategoryOrder = remember { mutableStateListOf<WidgetCategory>() }
    LaunchedEffect(initialCategories) {
        if (localCategoryOrder.isEmpty()) {
            localCategoryOrder.addAll(initialCategories)
        }
    }

    var draggedId by remember { mutableStateOf<String?>(null) }
    var draggedCategory by remember { mutableStateOf<WidgetCategory?>(null) }
    var dragAccumY by remember { mutableStateOf(0f) }
    
    val rowHeightPx = with(density) { (72.dp + spacing.sm).toPx() }
    val categoryHeaderHeightPx = with(density) { (40.dp + spacing.md).toPx() }

    val addedTypes = remember(currentLayout) { currentLayout.map { it.type }.toSet() }

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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.offset(x = (-12).dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = mc.textSecondary
                    )
                }
                Text(
                    text = stringResource(R.string.home_widget_gallery_title),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(vertical = spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                localCategoryOrder.forEach { category ->
                    val widgets = HomeWidgetType.entries.filter { it.category == category }
                        // Hidden for release — see docs/gamification-hidden-for-release.md.
                        // When gamification is off, omit its widget types from the gallery entirely
                        // (instead of showing them as greyed/disabled rows).
                        .filter { gamificationEnabled || !it.isGamification }
                    // Skip categories with no visible widget types (e.g. TOURNAMENT / COMMUNITY have
                    // no types assigned, and gamification-only categories vanish when the toggle is off)
                    // so we never render an orphaned header.
                    if (widgets.isEmpty()) return@forEach
                    val isDraggingCategory = draggedCategory == category
                    
                    val categoryZIndex = if (isDraggingCategory) 10f else 1f
                    
                    item(key = "header_${category.name}") {
                        CategoryHeader(
                            category = category,
                            modifier = Modifier
                                .then(if (isDraggingCategory) Modifier else Modifier.animateItem())
                                .zIndex(categoryZIndex)
                                .graphicsLayer {
                                    translationY = if (isDraggingCategory) dragAccumY else 0f
                                },
                            dragHandleModifier = if (category != WidgetCategory.ACTIVITY) {
                                Modifier.pointerInput(category.name) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedCategory = category
                                            draggedId = null
                                            dragAccumY = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragAccumY += amount.y
                                            
                                            val currentIdx = localCategoryOrder.indexOf(category)
                                            val blockHeight = categoryHeaderHeightPx + (widgets.size * rowHeightPx)
                                            
                                            if (dragAccumY <= -blockHeight && currentIdx > 1) {
                                                val to = currentIdx - 1
                                                localCategoryOrder.add(to, localCategoryOrder.removeAt(currentIdx))
                                                val newLayout = currentLayout.sortedBy { localCategoryOrder.indexOf(it.type.category) }
                                                onUpdateLayout(newLayout)
                                                dragAccumY += blockHeight
                                            } else if (dragAccumY >= blockHeight && currentIdx < localCategoryOrder.lastIndex) {
                                                val to = currentIdx + 1
                                                localCategoryOrder.add(to, localCategoryOrder.removeAt(currentIdx))
                                                val newLayout = currentLayout.sortedBy { localCategoryOrder.indexOf(it.type.category) }
                                                onUpdateLayout(newLayout)
                                                dragAccumY -= blockHeight
                                            }
                                        },
                                        onDragEnd = { draggedCategory = null; dragAccumY = 0f },
                                        onDragCancel = { draggedCategory = null; dragAccumY = 0f },
                                    )
                                }
                            } else Modifier
                        )
                    }

                    items(widgets, key = { it.persistedId }) { type ->
                        val isAdded = type in addedTypes
                        val isFixed = type.isAlwaysPresent
                        val isDraggingItem = draggedId == type.persistedId
                        val isDraggingInBlock = isDraggingCategory

                        CatalogRow(
                            type = type,
                            isAdded = isAdded,
                            isFixed = isFixed,
                            isLocked = type.audience == WidgetAudience.ACCOUNT_GATED && !isAuthenticated,
                            isGamificationDisabled = type.isGamification && !gamificationEnabled,
                            isDragging = isDraggingItem || isDraggingInBlock,
                            modifier = Modifier
                                .then(if (isDraggingItem || isDraggingInBlock) Modifier else Modifier.animateItem())
                                .zIndex(if (isDraggingItem) 11f else categoryZIndex)
                                .graphicsLayer {
                                    translationY = if (isDraggingItem || isDraggingInBlock) dragAccumY else 0f
                                },
                            onAdd = { onAddWidget(type) },
                            onRemove = { onRemoveWidget(type) },
                            onUnlock = onCreateAccount,
                            dragHandleModifier = if (isAdded && !isFixed && category != WidgetCategory.ACTIVITY) {
                                Modifier.pointerInput(type.persistedId) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedId = type.persistedId
                                            draggedCategory = null
                                            dragAccumY = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragAccumY += amount.y

                                            val currentIdx = currentLayout.indexOfFirst { it.type == type }
                                            if (currentIdx != -1) {
                                                // Items may ONLY be reordered WITHIN their own category run:
                                                // a move is rejected if the neighbour it would swap with belongs
                                                // to a different category (that keeps the category-grouped
                                                // invariant the ViewModel relies on).
                                                if (dragAccumY <= -rowHeightPx && currentIdx > 0) {
                                                    val target = currentIdx - 1
                                                    if (currentLayout[target].type.category == category) {
                                                        onMoveWidget(currentIdx, target)
                                                    }
                                                    dragAccumY += rowHeightPx
                                                } else if (dragAccumY >= rowHeightPx && currentIdx < currentLayout.lastIndex) {
                                                    val target = currentIdx + 1
                                                    if (currentLayout[target].type.category == category) {
                                                        onMoveWidget(currentIdx, target)
                                                    }
                                                    dragAccumY -= rowHeightPx
                                                }
                                            }
                                        },
                                        onDragEnd = { draggedId = null; dragAccumY = 0f },
                                        onDragCancel = { draggedId = null; dragAccumY = 0f },
                                    )
                                }
                            } else Modifier
                        )
                    }
                }
            }

            // Footer
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.primaryAccent,
                    contentColor = mc.background
                )
            ) {
                Text(
                    text = stringResource(R.string.home_widget_gallery_done).uppercase(),
                    style = ty.labelLarge
                )
            }
        }
    }
}

@Composable
private fun CategoryHeader(
    category: WidgetCategory,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = spacing.sm, bottom = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
    ) {
        // Categories WITH a drag handle (everything except ACTIVITY) show the ≡ grip.
        // ACTIVITY has no handle AND no leading spacer, so its title is flush-left.
        if (category != WidgetCategory.ACTIVITY) {
            Box(
                modifier = dragHandleModifier
                    .size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = mc.secondaryAccent.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Icon(
            imageVector = category.icon,
            contentDescription = null,
            tint = mc.secondaryAccent,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = category.displayName.uppercase(),
            style = ty.labelMedium,
            color = mc.secondaryAccent,
        )
    }
}

@Composable
private fun CatalogRow(
    type: HomeWidgetType,
    isAdded: Boolean,
    isFixed: Boolean,
    isLocked: Boolean,
    isGamificationDisabled: Boolean = false,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    onUnlock: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        color = if (isFixed) mc.surfaceVariant.copy(alpha = 0.5f) else mc.surface,
        shape = CardShape,
        // The whole added row is the long-press drag target now (the standalone ≡ handle
        // icon was removed) — dragHandleModifier carries the pointerInput when draggable.
        modifier = modifier
            .fillMaxWidth()
            .then(dragHandleModifier)
            .alpha(if (isDragging) 0.6f else 1f)
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            // Widget Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ChipShape)
                    .background(mc.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else type.icon,
                    contentDescription = null,
                    tint = if (isLocked) mc.secondaryAccent else if (isFixed) mc.primaryAccent.copy(alpha = 0.5f) else mc.primaryAccent,
                    modifier = Modifier.size(24.dp),
                )
            }

            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(type.defaultTitleRes),
                    style = ty.titleMedium,
                    color = if (isFixed) mc.textSecondary else mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = type.description,
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when {
                // Gamification off: the widget cannot be added/shown — render a disabled note.
                isGamificationDisabled -> Text(
                    text = stringResource(R.string.home_gamification_disabled),
                    style = ty.labelSmall,
                    color = mc.textDisabled,
                    modifier = Modifier.padding(end = spacing.sm),
                )
                isFixed -> Text(
                    text = "FIXED",
                    style = ty.labelSmall,
                    color = mc.textDisabled,
                    modifier = Modifier.padding(end = spacing.sm)
                )
                isAdded -> ActionChip(
                    label = stringResource(R.string.home_widget_remove),
                    icon = Icons.Default.Close,
                    onClick = onRemove,
                    isDestructive = true,
                )
                isLocked -> ActionChip(
                    label = stringResource(R.string.home_account_gated_lock),
                    onClick = onUnlock
                )
                else -> ActionChip(
                    label = stringResource(R.string.home_widget_add),
                    icon = Icons.Default.Add,
                    onClick = onAdd
                )
            }
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
    isDestructive: Boolean = false,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val bgColor = if (isDestructive) mc.lifeNegative.copy(alpha = 0.15f) else mc.primaryAccent.copy(alpha = 0.15f)
    val fgColor = if (isDestructive) mc.lifeNegative else mc.primaryAccent
    
    Surface(
        color = bgColor,
        shape = ButtonShape,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(ButtonShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = fgColor, modifier = Modifier.size(16.dp))
            Text(
                text = label.uppercase(),
                style = ty.labelSmall,
                color = fgColor,
                maxLines = 1
            )
        }
    }
}

private val WidgetCategory.displayName: String
    get() = when (this) {
        WidgetCategory.ACTIVITY -> "Activity"
        WidgetCategory.STATS -> "Stats"
        WidgetCategory.COLLECTION -> "Collection"
        WidgetCategory.DISCOVER -> "Discover"
        WidgetCategory.SOCIAL -> "Social"
        WidgetCategory.TOURNAMENT -> "Tournament"
        WidgetCategory.COMMUNITY -> "Community"
    }

private val WidgetCategory.icon: androidx.compose.ui.graphics.vector.ImageVector
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
        HomeWidgetType.CONTEXT_HERO -> "Your most relevant next action"
        HomeWidgetType.QUICK_ACTIONS -> "One-tap shortcuts"
        HomeWidgetType.PROGRESSION_HUB -> stringResource(R.string.home_widget_desc_progression_hub)
        HomeWidgetType.QUESTS_HUB -> stringResource(R.string.home_widget_desc_quests_hub)
        HomeWidgetType.GAME_STATS_HUB -> "Win rate, best deck and nemesis"
        HomeWidgetType.COLLECTION_STATS_HUB -> "Cards, decks, value and color split"
        HomeWidgetType.YOUR_DECKS_SHELF -> "Quick access to your decks"
        HomeWidgetType.WISHLIST_PROGRESS -> "Your wishlist at a glance"
        HomeWidgetType.DISCOVER_CARDS -> "Cards worth exploring"
        HomeWidgetType.CARD_OF_THE_DAY -> "A new card each day"
        HomeWidgetType.LATEST_SETS -> "Newest sets to draft"
        HomeWidgetType.MTG_NEWS -> "Latest MTG headlines"
        HomeWidgetType.RULES_TIP -> "A rules tip each day"
        HomeWidgetType.SOCIAL_HUB -> "Friends, community and online play"
        HomeWidgetType.TRADES_HUB -> "Trade inbox and suggestions"
    }
