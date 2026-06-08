package com.mmg.manahub.feature.home.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

// ═══════════════════════════════════════════════════════════════════════════════
//  Home dashboard — free-first, account-enhanced entry screen.
//
//  Stateful entry point: observes the ViewModel, hosts the Quick Start sheet, and
//  delegates every navigation intent to [onAction]. All sub-composables below are
//  stateless and reusable.
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

    // The active game lives in the activity-scoped GameViewModel and is passed in
    // from AppNavGraph; it always wins the hero slot when present.
    val effectiveState = if (activeGame != null) {
        uiState.copy(hero = activeGame)
    } else {
        uiState
    }

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
}

// ─────────────────────────────────────────────────────────────────────────────
//  Stateless root
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = spacing.xxl + navBarBottom),
            verticalArrangement = Arrangement.spacedBy(spacing.xl),
        ) {
            item(key = "topbar") {
                HomeTopBar(
                    avatarUrl = uiState.avatarUrl,
                    onAvatarClick = { onAction(HomeAction.OpenProfile) },
                )
            }

            item(key = "hero") {
                ContextHeroCard(
                    hero = uiState.hero,
                    onCtaClick = { onAction(heroCta(uiState.hero)) },
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }

            item(key = "quick_start") {
                QuickStartGrid(
                    actions = uiState.quickStartActions,
                    onActionClick = { onAction(it.toHomeAction()) },
                    onCustomizeClick = { onAction(HomeAction.CustomizeQuickStart) },
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }

            if (uiState.continueItems.isNotEmpty()) {
                item(key = "continue") {
                    ContinueSection(
                        items = uiState.continueItems,
                        onItemClick = { onAction(HomeAction.ContinueItem(it)) },
                    )
                }
            }

            item(key = "library") {
                LibrarySummaryCard(
                    stats = uiState.libraryStats,
                    onLibraryClick = { onAction(HomeAction.OpenLibrary) },
                    onScanClick = { onAction(HomeAction.ScanCard) },
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }

            item(key = "improve") {
                ImproveYourGameSection(
                    onPlaytestClick = { onAction(HomeAction.PlaytestRecentDeck) },
                    onDraftGuideClick = { onAction(HomeAction.DraftGuide) },
                    onDeckImprovementClick = { onAction(HomeAction.ImproveRecentDeck) },
                    onTournamentClick = { onAction(HomeAction.OpenTournaments) },
                )
            }

            if (uiState.recentNews.isNotEmpty()) {
                item(key = "latest") {
                    LatestSection(
                        news = uiState.recentNews,
                        onNewsItemClick = { onAction(HomeAction.OpenNews) },
                        onSeeAllClick = { onAction(HomeAction.OpenNews) },
                    )
                }
            }

            uiState.accountNudge?.let { nudge ->
                item(key = "nudge") {
                    AccountNudgeCard(
                        nudge = nudge,
                        onCreateAccount = { onAction(HomeAction.CreateAccount) },
                        onDismiss = { onAction(HomeAction.DismissAccountNudge) },
                        modifier = Modifier.padding(horizontal = spacing.lg),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Top bar
// ─────────────────────────────────────────────────────────────────────────────

private val HomeTopBarHeight = 56.dp

@Composable
fun HomeTopBar(
    avatarUrl: String?,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(HomeTopBarHeight)
            .padding(horizontal = spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ManaHub",
            style = ty.titleLarge,
            color = mc.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onAvatarClick)
                .semantics { contentDescription = "Open profile" },
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = mc.textPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Context hero
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ContextHeroCard(
    hero: HomeHeroState,
    onCtaClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val (title, subtitle, ctaLabel) = heroCopy(hero)
    val gradient = Brush.linearGradient(
        listOf(
            mc.primaryAccent.copy(alpha = 0.22f),
            mc.secondaryAccent.copy(alpha = 0.16f),
        ),
    )

    Surface(
        color = mc.surface,
        shape = CardShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(text = title, style = ty.titleLarge, color = mc.textPrimary)
            Text(
                text = subtitle,
                style = ty.bodyMedium,
                color = mc.textSecondary,
            )
            Spacer(Modifier.height(spacing.sm))
            if (hero !is HomeHeroState.Loading) {
                PrimaryCtaButton(label = ctaLabel, onClick = onCtaClick)
            }
        }
    }
}

@Composable
private fun PrimaryCtaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        color = mc.primaryAccent,
        shape = ButtonShape,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(ButtonShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = mc.background,
                modifier = Modifier.size(20.dp),
            )
            Text(text = label, style = ty.labelLarge, color = mc.background)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Quick Start grid (2×2)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun QuickStartGrid(
    actions: List<QuickStartAction>,
    onActionClick: (QuickStartAction) -> Unit,
    onCustomizeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val visible = actions.take(4)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SectionHeader(
            title = "Quick start",
            trailingIcon = Icons.Default.Edit,
            trailingContentDescription = "Customize quick start",
            onTrailingClick = onCustomizeClick,
        )
        // Two rows of two. The list is always exactly four in practice (the
        // DataStore flow guarantees it), but we chunk defensively.
        visible.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                rowActions.forEach { action ->
                    QuickStartButton(
                        action = action,
                        onClick = { onActionClick(action) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad an odd final row so the lone button keeps its half-width.
                if (rowActions.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickStartButton(
    action: QuickStartAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        color = mc.surface,
        shape = CardShape,
        modifier = modifier
            .heightIn(min = 72.dp)
            .clip(CardShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ChipShape)
                    .background(mc.primaryAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = action.label,
                style = ty.labelMedium,
                color = mc.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Quick Start customization sheet
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

    // Local editable selection — order preserved (selection index = display order).
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
            Text(
                text = "Pick 4 shortcuts",
                style = ty.titleLarge,
                color = mc.textPrimary,
            )
            Text(
                text = "Selected ${selection.size} of 4. Tap to add or remove.",
                style = ty.bodySmall,
                color = mc.textSecondary,
            )

            // FlowRow-free chip layout: wrap manually in rows of two for predictability.
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
                                if (isSelected) {
                                    selection.remove(action)
                                } else if (selection.size < 4) {
                                    selection.add(action)
                                }
                            },
                            label = {
                                Text(
                                    text = action.label,
                                    style = ty.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
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
                Box(
                    modifier = Modifier.padding(vertical = spacing.md),
                    contentAlignment = Alignment.Center,
                ) {
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
//  Continue section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ContinueSection(
    items: List<ContinueItem>,
    onItemClick: (ContinueItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SectionHeader(
            title = "Continue",
            modifier = Modifier.padding(horizontal = spacing.lg),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            items(items, key = { "${it.type}:${it.id}" }) { item ->
                ContinueCard(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
private fun ContinueCard(
    item: ContinueItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        color = mc.surface,
        shape = CardShape,
        modifier = modifier
            .width(220.dp)
            .heightIn(min = 88.dp)
            .clip(CardShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(ChipShape)
                    .background(mc.secondaryAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = item.type.icon,
                    contentDescription = null,
                    tint = mc.secondaryAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.label,
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.subtitle,
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Library summary
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LibrarySummaryCard(
    stats: LibraryStats?,
    onLibraryClick: () -> Unit,
    onScanClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val isEmpty = stats == null || (stats.uniqueCards == 0 && stats.deckCount == 0)

    Surface(
        color = mc.surface,
        shape = CardShape,
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .clickable(onClick = if (isEmpty) onScanClick else onLibraryClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Your library",
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = mc.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (isEmpty) {
                Text(
                    text = "Scan your first card to start building your collection.",
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xl)) {
                    LibraryStatColumn(value = "${stats?.uniqueCards ?: 0}", label = "Cards")
                    LibraryStatColumn(value = "${stats.deckCount}", label = "Decks")
                    LibraryStatColumn(value = stats.estimatedValueDisplay, label = "Value")
                }
            }
        }
    }
}

@Composable
private fun LibraryStatColumn(value: String, label: String) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Column(horizontalAlignment = Alignment.Start) {
        Text(text = value, style = ty.titleLarge, color = mc.primaryAccent, maxLines = 1)
        Text(text = label, style = ty.labelSmall, color = mc.textSecondary)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Improve your game
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ImproveYourGameSection(
    onPlaytestClick: () -> Unit,
    onDraftGuideClick: () -> Unit,
    onDeckImprovementClick: () -> Unit,
    onTournamentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    val chips = remember(onPlaytestClick, onDraftGuideClick, onDeckImprovementClick, onTournamentClick) {
        listOf(
            ImproveChip("Playtest", Icons.Default.Science, onPlaytestClick),
            ImproveChip("Draft guide", Icons.Default.MenuBook, onDraftGuideClick),
            ImproveChip("Tune deck", Icons.Default.Healing, onDeckImprovementClick),
            ImproveChip("Tournament", Icons.Default.EmojiEvents, onTournamentClick),
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SectionHeader(
            title = "Improve your game",
            modifier = Modifier.padding(horizontal = spacing.lg),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            items(chips, key = { it.label }) { chip ->
                ActionChip(label = chip.label, icon = chip.icon, onClick = chip.onClick)
            }
        }
    }
}

private data class ImproveChip(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
private fun ActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        color = mc.surface,
        shape = ChipShape,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(ChipShape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(20.dp),
            )
            Text(text = label, style = ty.labelMedium, color = mc.textPrimary, maxLines = 1)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Latest (news)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LatestSection(
    news: List<NewsItem>,
    onNewsItemClick: (NewsItem) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        SectionHeader(
            title = "Latest",
            trailingText = "See all",
            onTrailingClick = onSeeAllClick,
            modifier = Modifier.padding(horizontal = spacing.lg),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            items(news, key = { it.id }) { item ->
                NewsCard(item = item, onClick = { onNewsItemClick(item) })
            }
        }
    }
}

@Composable
private fun NewsCard(
    item: NewsItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        color = mc.surface,
        shape = CardShape,
        modifier = modifier
            .width(240.dp)
            .heightIn(min = 88.dp)
            .clip(CardShape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(mc.surfaceVariant),
            ) {
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Newspaper,
                        contentDescription = null,
                        tint = mc.textDisabled,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(32.dp),
                    )
                }
            }
            Text(
                text = item.title,
                style = ty.bodyMedium,
                color = mc.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(spacing.md),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Account nudge
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

    Surface(
        color = mc.surface,
        shape = CardShape,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "Protect & connect",
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(text = nudge.message, style = ty.bodyMedium, color = mc.textSecondary)
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
                    Box(
                        modifier = Modifier.padding(vertical = spacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Create free account",
                            style = ty.labelMedium,
                            color = mc.background,
                            maxLines = 1,
                        )
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
                    Box(
                        modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Maybe later",
                            style = ty.labelMedium,
                            color = mc.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared bits
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailingText: String? = null,
    trailingIcon: ImageVector? = null,
    trailingContentDescription: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            style = ty.labelLarge,
            color = mc.primaryAccent,
            modifier = Modifier.weight(1f),
        )
        if (onTrailingClick != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTrailingClick)
                    .then(
                        if (trailingContentDescription != null) {
                            Modifier.semantics { contentDescription = trailingContentDescription }
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    trailingText != null -> Text(
                        text = trailingText,
                        style = ty.labelMedium,
                        color = mc.secondaryAccent,
                    )
                    trailingIcon != null -> Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = mc.secondaryAccent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Display helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Title / subtitle / CTA copy for each hero state. */
private fun heroCopy(hero: HomeHeroState): Triple<String, String, String> = when (hero) {
    is HomeHeroState.ActiveGame ->
        Triple(
            "Game in progress",
            "${hero.mode} · ${hero.playerCount} players",
            "Resume game",
        )
    is HomeHeroState.ActiveDraft ->
        Triple(
            "Draft in progress",
            "Set ${hero.setName} · pick up where you left off",
            "Resume draft",
        )
    is HomeHeroState.Summary ->
        Triple(
            "Welcome back, ${hero.playerName}",
            if (hero.totalGames == 1) "1 game tracked" else "${hero.totalGames} games tracked",
            "Start a game",
        )
    HomeHeroState.Loading ->
        Triple("Loading…", "Getting your dashboard ready", "Start a game")
    HomeHeroState.Welcome ->
        Triple(
            "Welcome to ManaHub",
            "Track games, scan cards, and build decks — no account needed.",
            "Start a game",
        )
}

/** The CTA intent emitted by the hero button per state. */
private fun heroCta(hero: HomeHeroState): HomeAction = when (hero) {
    is HomeHeroState.ActiveGame -> HomeAction.StartGame
    is HomeHeroState.ActiveDraft -> HomeAction.DraftSimulator
    is HomeHeroState.Summary -> HomeAction.StartGame
    HomeHeroState.Loading -> HomeAction.StartGame
    HomeHeroState.Welcome -> HomeAction.StartGame
}

/** Human label for a Quick Start action. */
private val QuickStartAction.label: String
    get() = when (this) {
        QuickStartAction.START_GAME -> "Start game"
        QuickStartAction.SCAN_CARD -> "Scan card"
        QuickStartAction.CREATE_DECK -> "Build deck"
        QuickStartAction.DRAFT_GUIDE -> "Draft guide"
        QuickStartAction.DRAFT_SIMULATOR -> "Draft sim"
        QuickStartAction.SEARCH_CARD -> "Search card"
        QuickStartAction.LIBRARY -> "Library"
        QuickStartAction.DECKS -> "Decks"
        QuickStartAction.NEWS -> "News"
        QuickStartAction.STATS -> "Stats"
        QuickStartAction.FRIENDS -> "Friends"
        QuickStartAction.TRADES -> "Trades"
        QuickStartAction.TOURNAMENTS -> "Tournaments"
        QuickStartAction.SETTINGS -> "Settings"
    }

/** Icon for a Quick Start action. */
private val QuickStartAction.icon: ImageVector
    get() = when (this) {
        QuickStartAction.START_GAME -> Icons.Default.PlayArrow
        QuickStartAction.SCAN_CARD -> Icons.Default.Bolt
        QuickStartAction.CREATE_DECK -> Icons.Default.Style
        QuickStartAction.DRAFT_GUIDE -> Icons.Default.MenuBook
        QuickStartAction.DRAFT_SIMULATOR -> Icons.Default.AutoAwesome
        QuickStartAction.SEARCH_CARD -> Icons.Default.Search
        QuickStartAction.LIBRARY -> Icons.Default.CollectionsBookmark
        QuickStartAction.DECKS -> Icons.Default.Style
        QuickStartAction.NEWS -> Icons.Default.Newspaper
        QuickStartAction.STATS -> Icons.Default.QueryStats
        QuickStartAction.FRIENDS -> Icons.Default.Group
        QuickStartAction.TRADES -> Icons.Default.SwapHoriz
        QuickStartAction.TOURNAMENTS -> Icons.Default.EmojiEvents
        QuickStartAction.SETTINGS -> Icons.Default.Settings
    }

/** Maps a Quick Start action onto its navigation intent. */
private fun QuickStartAction.toHomeAction(): HomeAction = when (this) {
    QuickStartAction.START_GAME -> HomeAction.StartGame
    QuickStartAction.SCAN_CARD -> HomeAction.ScanCard
    QuickStartAction.CREATE_DECK -> HomeAction.CreateDeck
    QuickStartAction.DRAFT_GUIDE -> HomeAction.DraftGuide
    QuickStartAction.DRAFT_SIMULATOR -> HomeAction.DraftSimulator
    QuickStartAction.SEARCH_CARD -> HomeAction.SearchCard
    QuickStartAction.LIBRARY -> HomeAction.OpenLibrary
    QuickStartAction.DECKS -> HomeAction.OpenDecks
    QuickStartAction.NEWS -> HomeAction.OpenNews
    QuickStartAction.STATS -> HomeAction.OpenStats
    QuickStartAction.FRIENDS -> HomeAction.OpenFriends
    QuickStartAction.TRADES -> HomeAction.OpenTrades
    QuickStartAction.TOURNAMENTS -> HomeAction.OpenTournaments
    QuickStartAction.SETTINGS -> HomeAction.OpenSettings
}

/** Icon for a Continue item type. */
private val ContinueType.icon: ImageVector
    get() = when (this) {
        ContinueType.GAME -> Icons.Default.PlayArrow
        ContinueType.DRAFT -> Icons.Default.AutoAwesome
        ContinueType.TOURNAMENT -> Icons.Default.EmojiEvents
        ContinueType.DECK -> Icons.Default.Style
    }
