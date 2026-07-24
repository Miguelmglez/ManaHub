package com.mmg.manahub.feature.home.presentation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import coil3.compose.AsyncImage
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.model.QuickStartAction
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.coloredShadow
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import java.util.Calendar

// ═══════════════════════════════════════════════════════════════════════════════
//  Home dashboard — fully customizable widget board.
//
//  Stateful entry: observes the ViewModel, hosts the Quick Start sheet, the widget
//  gallery, and the reset dialog, and owns the transient drag-and-drop state. All
//  widget rendering lives in HomeWidgets.kt; this file owns layout + interaction.
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = koinViewModel(),
    onAction: (HomeAction) -> Unit,
    activeGame: HomeHeroState.ActiveGame? = null,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    // Deck Doctor Community/Archetype plan, Phase 5 — kept OUTSIDE HomeUiState on purpose, see
    // HomeViewModel.trendingFlow's KDoc.
    val trending by viewModel.trendingFlow.collectAsStateWithLifecycle()
    // Home widget board overhaul, TASK 5b — kept OUTSIDE HomeUiState for the same reason, see
    // HomeViewModel.communityDecksFlow's KDoc.
    val communityDecks by viewModel.communityDecksFlow.collectAsStateWithLifecycle()
    val communityDecksCategory by viewModel.communityDecksCategoryFlow.collectAsStateWithLifecycle()
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showGallerySheet by remember { mutableStateOf(false) }

    // Additive telemetry: leave a breadcrumb when the Home screen is first composed so crash reports
    // can show that the user was on Home. Fires once per entry (keyed on Unit).
    LaunchedEffect(Unit) { FirebaseCrashlytics.getInstance().log("screen_viewed: home") }

    // The active game override is disabled for now (user request: only show Welcome and Loading).
    val effectiveState = uiState

    HomeScreen(
        uiState = effectiveState,
        trending = trending,
        communityDecks = communityDecks,
        communityDecksCategory = communityDecksCategory,
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
                // Resolve the "most recent deck" here — this is the only layer with access to
                // uiState.decks (the static FirstStepItem catalog can't bake a dynamic id in).
                // uiState.decks is nullable while loading (TASK 7b) — null-safe firstOrNull.
                HomeAction.PlaytestRecentDeck ->
                    onAction(HomeAction.NavigatePlaytest(uiState.decks?.firstOrNull()?.id))
                // Board mutations + discover/news/community-decks widget actions are handled in
                // the ViewModel.
                is HomeAction.MoveWidget,
                is HomeAction.AddWidget,
                is HomeAction.RemoveWidget,
                HomeAction.ResetLayout,
                HomeAction.RetryDiscover,
                HomeAction.RefreshDiscover,
                HomeAction.RefreshRandomCard,
                is HomeAction.SelectDiscoverSet,
                is HomeAction.SelectCommunityDecksCategory,
                HomeAction.ResetNewsFilters,
                -> viewModel.onAction(action)
                // RateApp needs an Activity context to launch the store; resolve it upstream.
                HomeAction.RateApp -> onAction(action)
                else -> onAction(action)
            }
        },
        modifier = modifier,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )

    if (showCustomizeSheet) {
        // Breadcrumb: the quick-start customize sheet was opened (fires once per open).
        LaunchedEffect(Unit) { FirebaseCrashlytics.getInstance().log("home_quick_start_customize_opened") }
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
        // Breadcrumb: the widget gallery sheet was opened (fires once per open).
        LaunchedEffect(Unit) { FirebaseCrashlytics.getInstance().log("home_widget_gallery_opened") }
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    // Deck Doctor Community/Archetype plan, Phase 5.
    trending: com.mmg.manahub.core.model.TrendingSnapshot? = null,
    // Home widget board overhaul, TASK 5b.
    communityDecks: List<com.mmg.manahub.core.model.CommunityDeckSummary>? = null,
    communityDecksCategory: HomeCommunityDeckCategory = HomeCommunityDeckCategory.POPULAR,
) {
    val spacing = MaterialTheme.spacing
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())

        // TASK 1: while the board's first combine emission is still pending, render a skeleton
        // instead of an empty/partial grid so the transition to real content is smooth (no giant
        // pop-in once everything resolves at once). The top bar stays visible throughout.
        if (uiState.isLoading) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                HomeTopBar(
                    uiState = uiState,
                    onAvatarClick = { onAction(HomeAction.OpenProfile) },
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
                HomeBoardSkeleton(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = spacing.lg),
                )
            }
            return@Box
        }

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
            item(span = { GridItemSpan(maxLineSpan) }) {
                HomeTopBar(
                    uiState = uiState,
                    onAvatarClick = { onAction(HomeAction.OpenProfile) },
                )
            }

            // Every widget is MEDIUM (full width) after the consolidation.
            // Defensive distinctBy to prevent fatal key collisions if a race condition
            // occurs during rapid add/remove/move actions.
            items(
                items = uiState.layout.distinctBy { it.type.persistedId },
                key = { "widget_${it.type.persistedId}" },
                span = { GridItemSpan(maxLineSpan) },
            ) { widget ->
                HomeWidgetContainer(
                    widget = widget,
                    uiState = uiState,
                    onAction = onAction,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier = Modifier,
                    trending = trending,
                    communityDecks = communityDecks,
                    communityDecksCategory = communityDecksCategory,
                )
            }

            if (uiState.layout.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        subtitle = stringResource(R.string.home_empty_message),
                        actionLabel = stringResource(R.string.home_add_widgets),
                        onAction = { onAction(HomeAction.OpenWidgetGallery) },
                    )
                }
            }

            uiState.accountNudge?.let { nudge ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AccountNudgeCard(
                        nudge = nudge,
                        onCreateAccount = { onAction(HomeAction.CreateAccount) },
                        onDismiss = { onAction(HomeAction.DismissAccountNudge) },
                    )
                }
            }

            // Entry point to the widget gallery (add / remove / reorder) at the bottom.
            item(span = { GridItemSpan(maxLineSpan) }) {
                EditWidgetsButton(onClick = { onAction(HomeAction.OpenWidgetGallery) })
            }
        }
    }
}

@Composable
private fun EditWidgetsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Prominent tonal pill (full width, ≥48dp) using the primary accent so the entry point
    // to the widget gallery reads as a clear, tappable action rather than plain text.
    MagicCtaButton(
        onClick = onClick,
        text = stringResource(R.string.home_edit_widgets),
        style = MagicCtaStyle.Outlined,
        color = MagicCtaColor.Primary,
        icon = {
            Icon(
                Icons.Default.Widgets,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        },
        modifier = modifier.fillMaxWidth()
    )
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
    val greeting = greetingText(playerName)
    val openProfileDescription = stringResource(R.string.home_open_profile_a11y)

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
                .semantics { contentDescription = openProfileDescription },
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

/** Milliseconds in a day — used to derive the deterministic daily greeting-variant index. */
private const val GREETING_DAY_MS = 24L * 60L * 60L * 1000L

/** Morning band (05:00–11:59): 4 MTG-flavored variants (Home widget board overhaul, TASK 2). */
private val MORNING_GREETINGS = intArrayOf(
    R.string.home_greeting_morning_1,
    R.string.home_greeting_morning_2,
    R.string.home_greeting_morning_3,
    R.string.home_greeting_morning_4,
)

/** Afternoon band (12:00–16:59). */
private val AFTERNOON_GREETINGS = intArrayOf(
    R.string.home_greeting_afternoon_1,
    R.string.home_greeting_afternoon_2,
    R.string.home_greeting_afternoon_3,
    R.string.home_greeting_afternoon_4,
)

/** Evening band (17:00–20:59). */
private val EVENING_GREETINGS = intArrayOf(
    R.string.home_greeting_evening_1,
    R.string.home_greeting_evening_2,
    R.string.home_greeting_evening_3,
)

/** Night band (21:00–04:59). */
private val NIGHT_GREETINGS = intArrayOf(
    R.string.home_greeting_night_1,
    R.string.home_greeting_night_2,
    R.string.home_greeting_night_3,
    R.string.home_greeting_night_4,
)

/** Signed-out fallback (no player name resolved yet). */
private val SIGNED_OUT_GREETINGS = intArrayOf(
    R.string.home_greeting_signed_out_1,
    R.string.home_greeting_signed_out_2,
    R.string.home_greeting_signed_out_3,
)

/**
 * Resolves the greeting variant's string-resource id, given a caller-supplied [hour] (0..23),
 * [epochDay] (days since epoch), and whether a [name] is available.
 *
 * Pure and non-`@Composable` on purpose (Home widget board overhaul, TASK 2) so it is unit
 * testable without Compose UI test infra — see `HomeScreenGreetingTest`. Selection is
 * DETERMINISTIC — seeded by [epochDay], mirroring [RulesTipWidget]'s `RULES_TIP_SHUFFLE_SEED`
 * pattern — so the greeting is stable across recompositions and within the same day, but varies
 * day to day. Never uses an unseeded `Random()` per call.
 */
internal fun resolveGreetingVariant(hour: Int, epochDay: Long, hasName: Boolean): Int {
    val variants = if (!hasName) {
        SIGNED_OUT_GREETINGS
    } else {
        when (hour) {
            in 5..11 -> MORNING_GREETINGS
            in 12..16 -> AFTERNOON_GREETINGS
            in 17..20 -> EVENING_GREETINGS
            else -> NIGHT_GREETINGS
        }
    }
    val index = (epochDay % variants.size).toInt()
    return variants[index]
}

/** Picks a time-of-day-appropriate, MTG-flavored greeting variant (Home widget board overhaul, TASK 2). */
@Composable
private fun greetingText(name: String?): String {
    val epochDay = remember { System.currentTimeMillis() / GREETING_DAY_MS }
    if (name.isNullOrBlank()) {
        return stringResource(resolveGreetingVariant(hour = 0, epochDay = epochDay, hasName = false))
    }
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    return stringResource(resolveGreetingVariant(hour = hour, epochDay = epochDay, hasName = true), name)
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
                Text(stringResource(R.string.home_nudge_title), style = ty.titleMedium, color = mc.textPrimary, modifier = Modifier.weight(1f))
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
                        Text(stringResource(R.string.home_account_gated_cta), style = ty.labelMedium, color = mc.background, maxLines = 1)
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
                        Text(stringResource(R.string.home_nudge_dismiss), style = ty.labelMedium, color = mc.textSecondary, maxLines = 1)
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
        dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textDisabled.copy(alpha = 0.4f)) },
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(text = stringResource(R.string.quick_start_sheet_title), style = ty.titleLarge, color = mc.textPrimary)
            Text(
                text = stringResource(R.string.quick_start_sheet_subtitle, selection.size),
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
            
            MagicCtaButton(
                onClick = { onSave(selection.toList()) },
                text = stringResource(R.string.quick_start_sheet_save),
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Display helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Human label for a Quick Start action (used by the customization sheet). */
private val QuickStartAction.label: String
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        QuickStartAction.SCAN_CARD -> stringResource(R.string.quick_start_sheet_scan_card)
        QuickStartAction.CREATE_DECK -> stringResource(R.string.quick_start_sheet_build_deck)
        QuickStartAction.DRAFT_GUIDE -> stringResource(R.string.quick_start_sheet_draft_guide)
        QuickStartAction.SEARCH_CARD -> stringResource(R.string.quick_start_sheet_search_card)
        QuickStartAction.DECKS -> stringResource(R.string.quick_start_sheet_decks)
        QuickStartAction.NEWS -> stringResource(R.string.quick_start_sheet_news)
        QuickStartAction.STATS -> stringResource(R.string.quick_start_sheet_stats)
        QuickStartAction.FRIENDS -> stringResource(R.string.quick_start_sheet_friends)
        QuickStartAction.TRADES -> stringResource(R.string.quick_start_sheet_trades)
        QuickStartAction.COMMUNITY_DECKS -> stringResource(R.string.quick_start_sheet_community)
        QuickStartAction.SETTINGS -> stringResource(R.string.quick_start_sheet_settings)
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
        QuickStartAction.COMMUNITY_DECKS -> Icons.Default.Group
        QuickStartAction.SETTINGS -> Icons.Default.Settings
    }
