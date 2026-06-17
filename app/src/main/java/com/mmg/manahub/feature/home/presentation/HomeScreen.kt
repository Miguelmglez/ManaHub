package com.mmg.manahub.feature.home.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.coloredShadow
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import java.util.Calendar
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════════
//  Home dashboard — fully customizable widget board.
//
//  Stateful entry: observes the ViewModel, hosts the Quick Start sheet, the widget
//  gallery, and the reset dialog, and owns the transient drag-and-drop state. All
//  widget rendering lives in HomeWidgets.kt; this file owns layout + interaction.
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onAction: (HomeAction) -> Unit,
    activeGame: HomeHeroState.ActiveGame? = null,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showGallerySheet by remember { mutableStateOf(false) }

    // The active game lives in the activity-scoped GameViewModel and is passed in
    // from AppNavGraph; it always wins the hero slot when present.
    val effectiveState = if (activeGame != null) uiState.copy(hero = activeGame) else uiState

    HomeScreen(
        uiState = effectiveState,
        onAction = { action ->
            when (action) {
                HomeAction.CustomizeQuickStart -> showCustomizeSheet = true
                is HomeAction.SaveQuickStart -> {
                    viewModel.saveQuickStartActions(action.actions)
                    showCustomizeSheet = false
                }
                HomeAction.DismissAccountNudge -> viewModel.dismissAccountNudge()
                is HomeAction.SkipFirstStep -> viewModel.onAction(action)
                HomeAction.OpenWidgetGallery -> showGallerySheet = true
                // Board mutations + discover/news widget actions are handled in the ViewModel.
                is HomeAction.MoveWidget,
                is HomeAction.AddWidget,
                is HomeAction.RemoveWidget,
                HomeAction.ResetLayout,
                HomeAction.RetryDiscover,
                HomeAction.ResetNewsFilters,
                -> viewModel.onAction(action)
                // RateApp needs an Activity context to launch the store; resolve it upstream.
                HomeAction.RateApp -> onAction(action)
                else -> onAction(action)
            }
        },
        modifier = modifier,
    )

    if (showCustomizeSheet) {
        QuickStartCustomizeSheet(
            allActions = QuickStartAction.entries,
            selectedActions = uiState.quickStartActions,
            onSave = { selected ->
                viewModel.saveQuickStartActions(selected)
                showCustomizeSheet = false
            },
            onDismiss = { showCustomizeSheet = false },
        )
    }

    if (showGallerySheet) {
        WidgetGallerySheet(
            currentLayout = uiState.layout,
            isAuthenticated = uiState.isAuthenticated,
            gamificationEnabled = uiState.gamificationEnabled,
            onAddWidget = { type -> viewModel.onAction(HomeAction.AddWidget(type)) },
            onRemoveWidget = { type -> viewModel.onAction(HomeAction.RemoveWidget(type)) },
            onMoveWidget = { from, to -> viewModel.onAction(HomeAction.MoveWidget(from, to)) },
            onUpdateLayout = { layout -> viewModel.onAction(HomeAction.UpdateLayout(layout)) },
            onCreateAccount = {
                showGallerySheet = false
                onAction(HomeAction.CreateAccount)
            },
            onDismiss = { showGallerySheet = false },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Stateless root + drag controller
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Bounds registry kept for parity with the widget container API (the gallery owns
    // reordering now; the board itself is static).
    val itemBounds = remember { mutableStateMapOf<String, Rect>() }

    Box(modifier = modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                start = spacing.lg,
                end = spacing.lg,
                top = spacing.md,
                bottom = spacing.xxl + navBarBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item(key = "topbar", span = { GridItemSpan(maxLineSpan) }) {
                HomeTopBar(
                    uiState = uiState,
                    onAvatarClick = { onAction(HomeAction.OpenProfile) },
                )
            }

            // Every widget is MEDIUM (full width) after the consolidation.
            items(
                items = uiState.layout,
                key = { it.type.persistedId },
                span = { GridItemSpan(maxLineSpan) },
            ) { widget ->
                HomeWidgetContainer(
                    widget = widget,
                    uiState = uiState,
                    onRegisterBounds = { id, rect -> itemBounds[id] = rect },
                    onAction = onAction,
                    modifier = Modifier.animateItem(),
                )
            }

            if (uiState.layout.isEmpty() && !uiState.isLoading) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        subtitle = stringResource(R.string.home_empty_message),
                        actionLabel = stringResource(R.string.home_add_widgets),
                        onAction = { onAction(HomeAction.OpenWidgetGallery) },
                    )
                }
            }

            uiState.accountNudge?.let { nudge ->
                item(key = "nudge", span = { GridItemSpan(maxLineSpan) }) {
                    AccountNudgeCard(
                        nudge = nudge,
                        onCreateAccount = { onAction(HomeAction.CreateAccount) },
                        onDismiss = { onAction(HomeAction.DismissAccountNudge) },
                    )
                }
            }

            // Entry point to the widget gallery (add / remove / reorder) at the bottom.
            item(key = "edit_widgets_btn", span = { GridItemSpan(maxLineSpan) }) {
                EditWidgetsButton(onClick = { onAction(HomeAction.OpenWidgetGallery) })
            }
        }
    }
}

@Composable
private fun EditWidgetsButton(onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    // Prominent tonal pill (full width, ≥48dp) using the primary accent so the entry point
    // to the widget gallery reads as a clear, tappable action rather than plain text.
    Surface(
        color = mc.primaryAccent.copy(alpha = 0.12f),
        shape = ButtonShape,
        border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ButtonShape)
            .clickable(onClickLabel = stringResource(R.string.home_edit_widgets), onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.Widgets,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(spacing.sm))
            Text(
                text = stringResource(R.string.home_edit_widgets),
                style = ty.labelLarge,
                color = mc.primaryAccent,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Top bar — greeting + avatar + edit toggle
// ─────────────────────────────────────────────────────────────────────────────

private val HomeTopBarHeight = 56.dp

@Composable
private fun HomeTopBar(
    uiState: HomeUiState,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val playerName = uiState.playerName
    val greeting = greetingText(uiState.isAuthenticated, playerName)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HomeTopBarHeight + 24.dp)
            .padding(horizontal = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greeting,
                style = ty.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = mc.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        
        Surface(
            modifier = Modifier
                .size(56.dp)
                .coloredShadow(
                    color = mc.primaryAccent.copy(alpha = 0.25f),
                    borderRadius = 28.dp,
                    blurRadius = 16.dp
                )
                .clip(CircleShape)
                .border(BorderStroke(2.dp, mc.primaryAccent.copy(alpha = 0.5f)), CircleShape)
                .clickable(onClick = onAvatarClick)
                .semantics { contentDescription = "Open profile" },
            color = mc.surface,
            shape = CircleShape,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!uiState.avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = uiState.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun greetingText(isAuthenticated: Boolean, name: String?): String {
    if (name.isNullOrBlank()) return stringResource(R.string.home_greeting_signed_out)
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    return when {
        hour < 12 -> stringResource(R.string.home_greeting_morning, name)
        hour < 17 -> stringResource(R.string.home_greeting_afternoon, name)
        else -> stringResource(R.string.home_greeting_evening, name)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Account nudge (reused by the board)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AccountNudgeCard(
    nudge: AccountNudge,
    onCreateAccount: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(color = mc.surface, shape = CardShape, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = mc.primaryAccent, modifier = Modifier.size(22.dp))
                Text("Protect & connect", style = ty.titleMedium, color = mc.textPrimary, modifier = Modifier.weight(1f))
            }
            Text(
                text = nudge.message ?: stringResource(nudge.messageRes),
                style = ty.bodyMedium,
                color = mc.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                Surface(
                    color = mc.primaryAccent,
                    shape = ButtonShape,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(ButtonShape)
                        .clickable(onClick = onCreateAccount),
                ) {
                    Box(modifier = Modifier.padding(vertical = spacing.md), contentAlignment = Alignment.Center) {
                        Text("Create free account", style = ty.labelMedium, color = mc.background, maxLines = 1)
                    }
                }
                Surface(
                    color = mc.surfaceVariant,
                    shape = ButtonShape,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 48.dp)
                        .clip(ButtonShape)
                        .clickable(onClick = onDismiss),
                ) {
                    Box(modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md), contentAlignment = Alignment.Center) {
                        Text("Maybe later", style = ty.labelMedium, color = mc.textSecondary, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Quick Start customization sheet (unchanged behavior; kept here)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickStartCustomizeSheet(
    allActions: List<QuickStartAction>,
    selectedActions: List<QuickStartAction>,
    onSave: (List<QuickStartAction>) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val selection = remember {
        mutableStateListOf<QuickStartAction>().apply { addAll(selectedActions.take(4)) }
    }
    val canSave = selection.size == 4

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(text = "Pick 4 shortcuts", style = ty.titleLarge, color = mc.textPrimary)
            Text(
                text = "Selected ${selection.size} of 4. Tap to add or remove.",
                style = ty.bodySmall,
                color = mc.textSecondary,
            )

            allActions.chunked(2).forEach { rowActions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    rowActions.forEach { action ->
                        val isSelected = action in selection
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selection.remove(action)
                                else if (selection.size < 4) selection.add(action)
                            },
                            label = {
                                Text(action.label, style = ty.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.Check else action.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            shape = ChipShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = mc.primaryAccent.copy(alpha = 0.20f),
                                selectedLabelColor = mc.textPrimary,
                                selectedLeadingIconColor = mc.primaryAccent,
                                labelColor = mc.textSecondary,
                            ),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        )
                    }
                    if (rowActions.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(spacing.sm))
            Surface(
                color = if (canSave) mc.primaryAccent else mc.surfaceVariant,
                shape = ButtonShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(ButtonShape)
                    .clickable(enabled = canSave) { onSave(selection.toList()) },
            ) {
                Box(modifier = Modifier.padding(vertical = spacing.md), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Save",
                        style = ty.labelLarge,
                        color = if (canSave) mc.background else mc.textDisabled,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Display helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Human label for a Quick Start action (used by the customization sheet). */
private val QuickStartAction.label: String
    get() = when (this) {
        QuickStartAction.SCAN_CARD -> "Scan card"
        QuickStartAction.CREATE_DECK -> "Build deck"
        QuickStartAction.DRAFT_GUIDE -> "Draft guide"
        QuickStartAction.SEARCH_CARD -> "Search card"
        QuickStartAction.DECKS -> "Decks"
        QuickStartAction.NEWS -> "News"
        QuickStartAction.STATS -> "Stats"
        QuickStartAction.FRIENDS -> "Friends"
        QuickStartAction.TRADES -> "Trades"
        QuickStartAction.SETTINGS -> "Settings"
    }

/** Icon for a Quick Start action (used by the customization sheet). */
private val QuickStartAction.icon: androidx.compose.ui.graphics.vector.ImageVector
    get() = when (this) {
        QuickStartAction.SCAN_CARD -> Icons.Default.Camera
        QuickStartAction.CREATE_DECK -> Icons.Default.Style
        QuickStartAction.DRAFT_GUIDE -> Icons.Default.SportsEsports
        QuickStartAction.SEARCH_CARD -> Icons.Default.Search
        QuickStartAction.DECKS -> Icons.Default.Style
        QuickStartAction.NEWS -> Icons.AutoMirrored.Filled.MenuBook
        QuickStartAction.STATS -> Icons.Default.Insights
        QuickStartAction.FRIENDS -> Icons.Default.Group
        QuickStartAction.TRADES -> Icons.Default.SwapHoriz
        QuickStartAction.SETTINGS -> Icons.Default.Settings
    }
