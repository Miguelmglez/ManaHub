package com.mmg.manahub.feature.home.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import org.jetbrains.compose.resources.painterResource
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.mtg_card_back
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.svg.SvgDecoder
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.R
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.model.Friend
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.QuickStartAction
import com.mmg.manahub.core.model.TradeProposal
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.ui.isReducedMotionEnabled
import com.mmg.manahub.core.ui.components.AvatarImage
import com.mmg.manahub.core.ui.components.CircularDistribution
import com.mmg.manahub.core.ui.components.DeckItem
import com.mmg.manahub.core.ui.components.DraftSetCard
import com.mmg.manahub.core.ui.components.NewsItemCard
import com.mmg.manahub.core.ui.components.NewsItemOrientation
import com.mmg.manahub.core.ui.components.OracleText
import com.mmg.manahub.core.ui.components.search.SetPickerSheet
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.coloredShadow
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.TimeAgoFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ═══════════════════════════════════════════════════════════════════════════════
//  Home widget host + container + shared chrome
//
//  Every widget renders inside a uniform, flat [WidgetShell] (Surface + CardShape +
//  2dp elevation). [HomeWidgetHost] maps a placed [WidgetInstance] to its concrete
//  composable. [HomeWidgetContainer] registers its root bounds for drag hit-testing
//  in the gallery. All composables here are stateless; state lives in HomeScreen /
//  HomeViewModel. Every widget renders at a single MEDIUM size.
// ═══════════════════════════════════════════════════════════════════════════════

/** Minimum height for a MEDIUM widget — the only supported size after consolidation. */
private val MediumMinHeight: Dp = 160.dp

// ── F-12 (Home feature overhaul Phase 3 hygiene): named, documented font-size constants for the
// few spots that don't fit an existing magicTypography token exactly. Hoisted here rather than
// left as inline `9.sp`/`32.sp`/`17.sp` literals scattered through the file. ──

/** Compact caption size for [StatBox]/[AnimatedStatKPI] labels — smaller than `labelSmall`'s
 * default, needed so a 2x2 stat grid's uppercase captions don't wrap on narrow devices. */
private val CompactCaptionSize = 9.sp

/** Large numeral size for [StatKPI]'s hero value — bigger than `displayMedium`'s default so a
 * single KPI number reads as the focal point of its row. */
private val StatKpiValueSize = 32.sp

/** Slightly taller line height for the Rules Tip body text (readability for multi-line tips
 * rendered through [OracleText], which can include inline mana-symbol icons). */
private val RulesTipBodyLineHeight = 17.sp

/**
 * Fixed seed for the Rules Tip catalog's daily-order shuffle (Home feature overhaul Phase 2.4).
 * A hardcoded, constant seed keeps the permutation stable across app restarts/recompositions and
 * identical for every user — only the epoch-day index into it changes.
 */
private const val RULES_TIP_SHUFFLE_SEED = 20260713L

/**
 * Minimal container for widgets. Removes the solid background box to let the
 * theme background shine through. Content is grouped by vertical spacing.
 */
@Composable
private fun WidgetShell(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScopeMarker.() -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val clickModifier = if (onClick != null) {
        Modifier
            .clip(CardShape)
            .clickable(
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick,
            )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        ColumnScopeMarker.content()
    }
}

/** Marker receiver so [WidgetShell] content reads like a column body without leaking ColumnScope. */
object ColumnScopeMarker

/** Thin wrapper over [stringResource] used throughout the widgets for terse call sites. */
@Composable
@ReadOnlyComposable
private fun stringResourceSafe(id: Int): String = stringResource(id)

@Composable
@ReadOnlyComposable
private fun stringResourceSafe(id: Int, vararg formatArgs: Any): String = stringResource(id, *formatArgs)

/**
 * Section header matching the Stats screen style: Icon + Uppercase Label.
 *
 * When [onClick] is non-null (Home widget board overhaul, TASK 6) the icon+label area (NOT
 * [trailingContent]) becomes a ≥48dp tap target that opens the widget's logical destination —
 * the same one its "See more"/internal taps already use.
 */
@Composable
private fun WidgetSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        modifier = modifier.fillMaxWidth().padding(bottom = spacing.xs)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .then(
                    if (onClick != null) {
                        Modifier
                            .clip(ChipShape)
                            .clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title.uppercase(),
                style = ty.labelLarge,
                color = mc.textPrimary,
                letterSpacing = 2.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailingContent?.invoke()
    }
}

/** Centered loading spinner used while a data slice is null (not yet loaded). */
@Composable
private fun WidgetLoading() {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MediumMinHeight - 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = mc.primaryAccent, modifier = Modifier.size(28.dp))
    }
}

/** Inline empty-message body for an otherwise-loaded widget. */
@Composable
private fun WidgetEmptyBody(message: String) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Text(text = message, style = ty.bodySmall, color = mc.textSecondary)
}

/**
 * Empty body with a tap-to-retry affordance, used when a widget's data failed to load
 * (e.g. the Discover/Card-of-the-day fetch) so the user can re-trigger it instead of
 * staring at an endless spinner. The whole row is a ≥48dp tap target.
 */
@Composable
private fun WidgetRetryBody(message: String, onRetry: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ChipShape)
            .clickable(onClickLabel = stringResourceSafe(R.string.home_retry), role = Role.Button, onClick = onRetry)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = null,
            tint = mc.primaryAccent,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = ty.bodySmall,
            color = mc.textSecondary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Sign-in prompt used by account-gated widgets when the user is unauthenticated. */
@Composable
fun AccountGatedPlaceholder(
    widgetTitle: String,
    onSignIn: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    WidgetShell(onClick = onSignIn) {
        Text(
            text = widgetTitle,
            style = ty.titleMedium,
            color = mc.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        WidgetEmptyBody(message = stringResourceSafe(R.string.home_account_gated_lock))
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = stringResourceSafe(R.string.home_account_gated_cta),
            style = ty.labelMedium,
            color = mc.primaryAccent,
        )
    }
}

/**
 * Board-level loading skeleton (Home widget board overhaul, TASK 1), shown by [HomeScreen] while
 * the FIRST combine emission of [HomeUiState] is still pending — replaces an empty/partial grid
 * with a few placeholder blocks so real widgets don't all pop in at once once the board resolves.
 */
@Composable
fun HomeBoardSkeleton(modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        repeat(HOME_BOARD_SKELETON_BLOCK_COUNT) {
            HomeBoardSkeletonBlock()
        }
    }
}

/** Number of placeholder blocks shown by [HomeBoardSkeleton] — matches a typical first screenful. */
private const val HOME_BOARD_SKELETON_BLOCK_COUNT = 4

/** A single pulsing placeholder block, sized like a typical MEDIUM widget. */
@Composable
private fun HomeBoardSkeletonBlock() {
    val mc = MaterialTheme.magicColors
    val infiniteTransition = rememberInfiniteTransition(label = "home-board-skeleton")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "skeleton-pulse",
    )
    // Reduced-motion users see a static mid-tone block, not a pulsing one (mirrors the hero pulse
    // at ContextHeroWidget's isReducedMotionEnabled() guard).
    val alpha = if (isReducedMotionEnabled()) 0.5f else animatedAlpha
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MediumMinHeight)
            .clip(CardShape)
            .background(mc.surfaceVariant.copy(alpha = alpha)),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Host — dispatch on widget type (exhaustive over all HomeWidgetType entries)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
fun HomeWidgetHost(
    widget: WidgetInstance,
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    // Deck Doctor Community/Archetype plan, Phase 5 — kept OUTSIDE HomeUiState on purpose, see
    // [com.mmg.manahub.feature.home.presentation.HomeViewModel.trendingFlow]'s KDoc.
    trending: com.mmg.manahub.core.model.TrendingSnapshot? = null,
    // Home widget board overhaul, TASK 5b — kept OUTSIDE HomeUiState for the same reason as
    // [trending]; see [com.mmg.manahub.feature.home.presentation.HomeViewModel.communityDecksFlow]'s
    // KDoc.
    communityDecks: List<com.mmg.manahub.core.model.CommunityDeckSummary>? = null,
    communityDecksCategory: HomeCommunityDeckCategory = HomeCommunityDeckCategory.POPULAR,
) {
    val spacing = MaterialTheme.spacing
    // Gamification widgets render nothing on the dashboard when the master toggle is off — they stay
    // in the persisted layout (so they reappear if re-enabled) but are not shown.
    if (widget.type.isGamification && !uiState.gamificationEnabled) return
    // Phase 5: silently hidden (never an error state) while there's no trending data yet / the
    // community engine is off / the Worker is unreachable — see HomeWidgetType.TRENDING_COMMANDERS'
    // KDoc.
    if (widget.type == HomeWidgetType.TRENDING_COMMANDERS && trending.isNullOrEmptyTrending()) return

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        if (widget.type != HomeWidgetType.CONTEXT_HERO) {
            val titleText = stringResourceSafe(widget.type.defaultTitleRes)
            val titleClickAction = widgetHeaderTitleClickAction(widget.type)
            WidgetSectionHeader(
                title = titleText,
                icon = widget.type.icon,
                onClick = titleClickAction?.let { action -> { onAction(action) } },
                onClickLabel = titleText,
                trailingContent = widgetHeaderTrailingContent(
                    type = widget.type,
                    uiState = uiState,
                    onAction = onAction,
                    communityDecksCategory = communityDecksCategory,
                ),
            )
        }

        when (widget.type) {
            HomeWidgetType.CONTEXT_HERO -> ContextHeroWidget(uiState.hero, onAction)
            HomeWidgetType.QUICK_ACTIONS -> QuickActionsWidget(
                actions = uiState.quickStartActions,
                onAction = onAction,
            )
            HomeWidgetType.PROGRESSION_HUB -> ProgressionHubWidget(uiState.gamification, onAction)
            HomeWidgetType.QUESTS_HUB -> QuestsHubWidget(uiState.gamification, onAction)
            HomeWidgetType.GAME_STATS_HUB -> GameStatsHubWidget(uiState, onAction)
            HomeWidgetType.COLLECTION_STATS_HUB -> CollectionStatsHubWidget(uiState, onAction)
            HomeWidgetType.YOUR_DECKS_SHELF -> DecksShelfWidget(uiState.decks, onAction)
            HomeWidgetType.RECENTLY_ADDED -> RecentlyAddedWidget(
                entries = uiState.recentlyAdded,
                onAction = onAction,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
            HomeWidgetType.WISHLIST_PROGRESS -> WishlistWidget(
                stats = uiState.wishlistStats,
                isAuthenticated = uiState.isAuthenticated,
                authResolved = uiState.authResolved,
                onAction = onAction,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
            HomeWidgetType.DISCOVER_CARDS -> DiscoverCardsWidget(
                cards = uiState.discoverCards,
                loadState = uiState.discoverLoadState,
                onAction = onAction,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
            HomeWidgetType.CARD_OF_THE_DAY -> RandomCardWidget(
                card = uiState.cardOfTheDay,
                loadState = uiState.randomCardLoadState,
                onAction = onAction,
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
            HomeWidgetType.LATEST_SETS -> LatestSetsWidget(uiState.latestSets, onAction)
            HomeWidgetType.MTG_NEWS -> NewsWidget(uiState.recentNews, uiState.newsFiltersActive, onAction)
            HomeWidgetType.RULES_TIP -> RulesTipWidget()
            HomeWidgetType.FRIENDS -> FriendsWidget(
                friends = uiState.friends,
                friendCount = uiState.friendCount,
                latestFriendRequestName = uiState.latestFriendRequestName,
                isAuthenticated = uiState.isAuthenticated,
                authResolved = uiState.authResolved,
                onAction = onAction,
            )
            HomeWidgetType.COMMUNITY_DECKS -> CommunityDecksWidget(
                decks = communityDecks,
                onAction = onAction,
            )
            HomeWidgetType.TRADES_HUB -> TradesHubWidget(uiState, onAction)
            HomeWidgetType.TRENDING_COMMANDERS -> TrendingCommandersWidget(trending, onAction)
        }
    }
}

/**
 * The logical destination for tapping a widget's TITLE (Home widget board overhaul, TASK 6) — the
 * same action its "See more" tile / internal taps already navigate to. Null means the title is not
 * clickable (its internal content already IS the whole tap target, e.g. CONTEXT_HERO/QUICK_ACTIONS,
 * or there is no single destination, e.g. CARD_OF_THE_DAY/RULES_TIP).
 */
private fun widgetHeaderTitleClickAction(type: HomeWidgetType): HomeAction? = when (type) {
    HomeWidgetType.MTG_NEWS -> HomeAction.OpenNews
    HomeWidgetType.YOUR_DECKS_SHELF -> HomeAction.OpenDecks
    HomeWidgetType.RECENTLY_ADDED -> HomeAction.OpenLibrary
    HomeWidgetType.WISHLIST_PROGRESS -> HomeAction.OpenWishlist
    HomeWidgetType.DISCOVER_CARDS -> HomeAction.SearchCard
    HomeWidgetType.LATEST_SETS -> HomeAction.OpenDraftGuide
    HomeWidgetType.GAME_STATS_HUB -> HomeAction.OpenStats
    HomeWidgetType.COLLECTION_STATS_HUB -> HomeAction.OpenStats
    HomeWidgetType.TRADES_HUB -> HomeAction.OpenTrades
    HomeWidgetType.FRIENDS -> HomeAction.OpenFriends
    HomeWidgetType.COMMUNITY_DECKS -> HomeAction.OpenCommunityDecks
    HomeWidgetType.TRENDING_COMMANDERS -> HomeAction.OpenCommunityDecks
    HomeWidgetType.PROGRESSION_HUB -> HomeAction.OpenProfile
    HomeWidgetType.QUESTS_HUB -> HomeAction.OpenProfileQuests
    HomeWidgetType.QUICK_ACTIONS,
    HomeWidgetType.CARD_OF_THE_DAY,
    HomeWidgetType.RULES_TIP,
    HomeWidgetType.CONTEXT_HERO,
    -> null
}

/** `true` when there is no trending data to show (null snapshot or an empty commanders list) —
 * the single guard [HomeWidgetHost] uses to hide [HomeWidgetType.TRENDING_COMMANDERS] entirely. */
private fun com.mmg.manahub.core.model.TrendingSnapshot?.isNullOrEmptyTrending(): Boolean =
    this == null || topCommanders.isEmpty()

/**
 * Top-3 trending commanders of the week (Deck Doctor Community/Archetype plan, Phase 5). Tapping
 * ANY row (or the widget as a whole) navigates into the Community Hub via
 * [HomeAction.OpenCommunityDecks] — the Hub lands on its Discover section by default whenever
 * `communityEngineEnabledFlow` is on (see [com.mmg.manahub.feature.communitydecks.presentation
 * .CommunityDecksSearchViewModel]'s `hubTab` default), so no new nav action was needed.
 */
@Composable
private fun TrendingCommandersWidget(
    trending: com.mmg.manahub.core.model.TrendingSnapshot?,
    onAction: (HomeAction) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val topCommanders = trending?.topCommanders.orEmpty()
    if (topCommanders.isEmpty()) return

    WidgetShell(onClick = { onAction(HomeAction.OpenCommunityDecks) }, onClickLabel = stringResourceSafe(R.string.widget_title_trending_commanders)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            items(topCommanders.take(5)) { commander ->
                DeckItem(
                    deck = DeckSummary(
                        id = commander.name,
                        name = commander.name,
                        description = null,
                        format = "Commander",
                        coverCardId = null,
                        createdAt = 0L,
                        updatedAt = 0L,
                        cardCount = commander.count,
                        colorIdentity = emptySet(),
                        coverImageUrl = null
                    ),
                    onClick = { onAction(HomeAction.OpenCommunityDecks) },
                    reduced = true,
                    cardBackPainter = painterResource(Res.drawable.mtg_card_back),
                    modifier = Modifier.width(160.dp),
                )
            }
        }
    }
}

/**
 * Resolves the trailing affordance shown in a widget's section header, or null when the widget
 * has none. Each branch is self-contained; the DISCOVER_CARDS branch hosts its own local
 * set-picker toggle state and the reusable [SetPickerSheet]; COMMUNITY_DECKS hosts its own local
 * category-picker sheet toggle state (Home widget board overhaul, TASK 5b).
 */
private fun widgetHeaderTrailingContent(
    type: HomeWidgetType,
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    communityDecksCategory: HomeCommunityDeckCategory = HomeCommunityDeckCategory.POPULAR,
): (@Composable () -> Unit)? = when (type) {
    HomeWidgetType.QUICK_ACTIONS -> {
        {
            WidgetHeaderIconButton(
                icon = Icons.Default.Edit,
                contentDescription = stringResourceSafe(R.string.home_customize_shortcuts_a11y),
                onClick = { onAction(HomeAction.CustomizeQuickStart) },
            )
        }
    }
    HomeWidgetType.CARD_OF_THE_DAY -> {
        {
            WidgetHeaderIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.home_random_refresh),
                onClick = { onAction(HomeAction.RefreshRandomCard) },
            )
        }
    }
    HomeWidgetType.DISCOVER_CARDS -> {
        {
            val spacing = MaterialTheme.spacing
            var showSetPicker by remember { mutableStateOf(false) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                DiscoverSetAffordance(
                    set = uiState.discoverSet,
                    onClick = { showSetPicker = true },
                )
                WidgetHeaderIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.home_discover_refresh),
                    onClick = { onAction(HomeAction.RefreshDiscover) },
                )
            }
            if (showSetPicker) {
                SetPickerSheet(
                    selectedSetCodes = setOfNotNull(uiState.discoverSetCode),
                    onToggleSet = { set ->
                        // Single-selection at the call site: re-tapping the active set clears it,
                        // any other set replaces it. Dismiss after the pick.
                        onAction(
                            HomeAction.SelectDiscoverSet(
                                if (set.code == uiState.discoverSetCode) null else set,
                            ),
                        )
                        showSetPicker = false
                    },
                    onDismiss = { showSetPicker = false },
                    availableSets = null,
                    singleSelection = true,
                )
            }
        }
    }
    HomeWidgetType.COMMUNITY_DECKS -> {
        {
            var showCategoryPicker by remember { mutableStateOf(false) }
            CommunityDecksCategoryAffordance(
                category = communityDecksCategory,
                onClick = { showCategoryPicker = true },
            )
            if (showCategoryPicker) {
                CommunityDecksCategoryPickerSheet(
                    selected = communityDecksCategory,
                    onSelect = { category ->
                        onAction(HomeAction.SelectCommunityDecksCategory(category))
                        showCategoryPicker = false
                    },
                    onDismiss = { showCategoryPicker = false },
                )
            }
        }
    }
    else -> null
}

/**
 * The standard ManaHub icon button used in widget section headers. The 24dp glyph is wrapped in a
 * [minimumInteractiveComponentSize] box so the touch target is ≥48dp even though the icon is small.
 */
@Composable
private fun WidgetHeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClickLabel = contentDescription, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.magicColors.primaryAccent,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Discover-widget set selector affordance. When a set is scoped, shows the set's SVG icon + its
 * uppercase code; otherwise falls back to the generic Layers icon. The whole row is a ≥48dp tap
 * target that re-opens the set picker.
 */
@Composable
private fun DiscoverSetAffordance(
    set: MagicSet?,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val label = set?.let { stringResource(R.string.home_discover_set_selected, it.code.uppercase()) }
        ?: stringResource(R.string.home_discover_select_set)

    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(ChipShape)
            .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        if (set != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(set.iconSvgUri)
                    .decoderFactory(SvgDecoder.Factory())
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(mc.primaryAccent),
            )
            Text(
                text = set.code.uppercase(),
                style = ty.labelMedium,
                color = mc.primaryAccent,
                maxLines = 1,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Layers,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Context hero — delegates to the First Steps carousel for the Welcome state
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ContextHeroWidget(hero: HomeHeroState, onAction: (HomeAction) -> Unit) {
    // User request: only show Welcome and Loading states.
    if (hero !is HomeHeroState.Welcome && hero !is HomeHeroState.Loading) return

    // Delegate to the carousel / completion card when the hero is the Welcome state.
    if (hero is HomeHeroState.Welcome) {
        val initialTime = remember { System.currentTimeMillis() }
        val wasInitiallyNotEmpty = remember { hero.steps.isNotEmpty() }
        var isVisible by remember { mutableStateOf(hero.steps.isNotEmpty()) }

        LaunchedEffect(hero.steps.isEmpty()) {
            if (hero.steps.isEmpty()) {
                val isRealAction = wasInitiallyNotEmpty && (System.currentTimeMillis() - initialTime > 500L)
                if (isRealAction && isVisible) {
                    delay(1500)
                    isVisible = false
                } else {
                    isVisible = false
                }
            } else {
                isVisible = true
            }
        }

        AnimatedVisibility(
            visible = isVisible,
            exit = fadeOut() + shrinkVertically()
        ) {
            if (hero.steps.isEmpty()) {
                FirstStepsCompletedCard()
            } else {
                FirstStepsCarousel(
                    steps = hero.steps,
                    onAction = onAction,
                    onDismiss = { stepId -> onAction(HomeAction.SkipFirstStep(stepId)) },
                )
            }
        }
        return
    }

    // Summary falls through to the generic hero card below (F-8): heroWidgetCopy/heroSectionLabel
    // already have full copy for HomeHeroState.Summary ("Welcome back, X" / "N games tracked").

    // Quests-ready suggestion uses string resources, so it is rendered here (the generic copy
    // helpers below are not @Composable). Highest priority — opens the Profile Quests tab.
    if (hero is HomeHeroState.QuestsReady) {
        QuestsReadyHero(count = hero.count, onAction = onAction)
        return
    }

    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val (title, subtitle, cta) = heroWidgetCopy(hero)
    val isActive = hero is HomeHeroState.ActiveGame || hero is HomeHeroState.ActiveDraft

    val infiniteTransition = rememberInfiniteTransition(label = "hero-pulse")
    val animatedPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    // F-13: reduced-motion users see the static end state (full opacity), not a pulsing dot.
    val pulseAlpha = if (isReducedMotionEnabled()) 1f else animatedPulseAlpha

    Surface(
        color = mc.surface.copy(alpha = 0.6f),
        shape = CardShape,
        border = if (isActive) BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.3f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .coloredShadow(
                color = if (isActive) mc.primaryAccent.copy(alpha = 0.15f) else Color.Transparent,
                borderRadius = 18.dp,
                blurRadius = 24.dp
            )
    ) {
        Column(modifier = Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(mc.lifePositive.copy(alpha = pulseAlpha)),
                    )
                }
                Text(
                    text = heroSectionLabel(hero),
                    style = ty.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (isActive) mc.lifePositive else mc.primaryAccent,
                    maxLines = 1,
                )
            }

            Text(
                text = title,
                style = ty.displayMedium,
                color = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = ty.bodyMedium,
                color = mc.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(spacing.xs))
            PillButton(
                label = cta,
                icon = Icons.Default.PlayArrow,
                onClick = { onAction(heroWidgetCta(hero)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  First Steps carousel and completion card
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Auto-advancing onboarding carousel backed by a [HorizontalPager].
 *
 * Each page shows an icon badge, a title, a subtitle, and a small top-right dismiss
 * affordance (≥48dp touch target). The pager auto-advances every 4 seconds while the user is not
 * actively swiping ([androidx.compose.foundation.pager.PagerState.isScrollInProgress] pauses the
 * timer automatically). Chevron nav icons let the user browse without waiting.
 *
 * Stateless — driven entirely by the pager state and the two callback lambdas.
 *
 * **Tap semantics (Home feature overhaul Phase 2.2): tapping a slide fires ONLY its CTA — it no
 * longer also dismisses the step.** A step disappears when its data-driven completion condition
 * is met, or when the user explicitly taps the dismiss affordance ([onDismiss]). Previously-
 * skipped ids remain skipped (no migration/data reset — [onDismiss] persists through the same
 * `observeSkippedFirstSteps` DataStore mechanism as before).
 *
 * @param steps    Non-empty list of visible steps to display.
 * @param onAction Called when the user taps the CTA; receives the step's [HomeAction].
 * @param onDismiss Called when the user taps the dismiss affordance; receives the step's id.
 */
@Composable
internal fun FirstStepsCarousel(
    steps: List<FirstStepItem>,
    onAction: (HomeAction) -> Unit,
    onDismiss: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val coroutineScope = rememberCoroutineScope()

    // Re-key the pager whenever the number of steps changes (e.g. a step is skipped),
    // so the current page index can never point past the end of the list.
    val pagerState = rememberPagerState(pageCount = { steps.size })

    // Auto-advance while the user is not actively swiping. isScrollInProgress flips to
    // true during a drag/fling, which cancels and restarts this effect — pausing the timer.
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            delay(4_000L)
            if (steps.size > 1) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % steps.size)
            }
        }
    }

    WidgetShell {
        WidgetSectionHeader(
            title = stringResourceSafe(R.string.first_steps_section_label),
            icon = Icons.Default.AutoAwesome
        )

        Box(contentAlignment = Alignment.Center) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spacing.xl)
            ) { page ->
                val step = steps.getOrNull(page) ?: steps.first()
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                val alphaValue = (1f - kotlin.math.abs(pageOffset)).coerceIn(0f, 1f)
                val scaleValue = (1f - 0.2f * kotlin.math.abs(pageOffset)).coerceIn(0.8f, 1f)

                Surface(
                    color = mc.surface.copy(alpha = 0.6f),
                    shape = CardShape,
                    border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.3f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.md)
                        .padding(horizontal = spacing.xs)
                        .graphicsLayer {
                            alpha = alphaValue
                            scaleX = scaleValue
                            scaleY = scaleValue
                        }
                        .coloredShadow(
                            color = mc.primaryAccent.copy(alpha = 0.15f),
                            borderRadius = 18.dp,
                            blurRadius = 24.dp
                        )
                        .clip(CardShape)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        // Tap = CTA only (Phase 2.2). Dismiss is a SEPARATE affordance below.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    onClickLabel = stringResourceSafe(step.titleRes),
                                    role = Role.Button,
                                ) { onAction(step.action) }
                                .padding(horizontal = spacing.lg, vertical = spacing.xl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(spacing.md)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(mc.primaryAccent.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                when (val icon = step.icon) {
                                    is StepIcon.Vector -> Icon(
                                        imageVector = icon.imageVector,
                                        contentDescription = null,
                                        tint = mc.primaryAccent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    is StepIcon.Drawable -> Icon(
                                        painter = painterResource(id = icon.resId),
                                        contentDescription = null,
                                        tint = mc.primaryAccent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(spacing.xs)
                            ) {
                                Text(
                                    text = stringResourceSafe(step.titleRes),
                                    style = ty.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = mc.textPrimary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResourceSafe(step.subtitleRes),
                                    style = ty.bodySmall,
                                    color = mc.textSecondary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Dismiss affordance (Phase 2.2): subtle, top-right, ≥48dp touch target
                        // via minimumInteractiveComponentSize. Persists through the same
                        // observeSkippedFirstSteps DataStore mechanism as before.
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable(
                                    onClickLabel = stringResourceSafe(R.string.first_step_dismiss),
                                    role = Role.Button,
                                ) { onDismiss(step.id) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResourceSafe(R.string.first_step_dismiss),
                                tint = mc.textSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            if (steps.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = stringResourceSafe(R.string.home_carousel_previous),
                        tint = mc.primaryAccent.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(28.dp)
                            .alpha(if (pagerState.currentPage > 0) 1f else 0.1f)
                            .clip(CircleShape)
                            .clickable(enabled = pagerState.currentPage > 0) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = stringResourceSafe(R.string.home_carousel_next),
                        tint = mc.primaryAccent.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(28.dp)
                            .alpha(if (pagerState.currentPage < steps.size - 1) 1f else 0.1f)
                            .clip(CircleShape)
                            .clickable(enabled = pagerState.currentPage < steps.size - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                    )
                }
            }
        }
    }
}

/**
 * Rendered inside [ContextHeroWidget] when all first steps have been completed or skipped.
 * Displays a friendly "You're all set!" message with no CTA.
 */
@Composable
internal fun FirstStepsCompletedCard() {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    WidgetShell {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(spacing.sm))
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(mc.primaryAccent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(28.dp),
                )
            }
            Text(
                text = stringResourceSafe(R.string.first_steps_all_done_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResourceSafe(R.string.first_steps_all_done_subtitle),
                style = ty.bodySmall,
                color = mc.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Spacer(Modifier.height(spacing.sm))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Quests-ready hero (gamification Phase 2) — highest-priority CONTEXT_HERO suggestion
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QuestsReadyHero(count: Int, onAction: (HomeAction) -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Surface(
        color = mc.surface.copy(alpha = 0.6f),
        shape = CardShape,
        border = BorderStroke(1.dp, mc.lifePositive.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .coloredShadow(
                color = mc.lifePositive.copy(alpha = 0.15f),
                borderRadius = 18.dp,
                blurRadius = 24.dp,
            ),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = mc.lifePositive,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResourceSafe(R.string.home_hero_quests_ready_label).uppercase(),
                    style = ty.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = mc.lifePositive,
                    maxLines = 1,
                )
            }
            Text(
                text = stringResourceSafe(R.string.home_hero_quests_ready_title, count),
                style = ty.displayMedium,
                color = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResourceSafe(R.string.home_hero_quests_ready_subtitle),
                style = ty.bodyMedium,
                color = mc.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(spacing.xs))
            PillButton(
                label = stringResourceSafe(R.string.home_hero_quests_ready_cta),
                icon = Icons.Default.EmojiEvents,
                onClick = { onAction(HomeAction.OpenProfileQuests) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  PROGRESSION_HUB (gamification Phase 2) — level + XP bar + streak + daily quests
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ProgressionHubWidget(gamification: HomeGamification?, onAction: (HomeAction) -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    if (gamification == null) {
        WidgetShell { WidgetLoading() }
        return
    }

    WidgetShell(
        onClick = { onAction(HomeAction.OpenProfileQuests) },
        onClickLabel = stringResource(R.string.home_progression_open_a11y),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Level badge.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(mc.primaryAccent.copy(alpha = 0.15f))
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${gamification.level}",
                    style = ty.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = mc.primaryAccent,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Text(
                    text = stringResourceSafe(R.string.home_progression_level, gamification.level),
                    style = ty.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = mc.textPrimary,
                    maxLines = 1,
                )
                HomeProgressBar(
                    progress = gamification.levelProgress,
                    fillColor = mc.primaryAccent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Streak chip.
            val streakActive = gamification.streak > 0
            Surface(
                shape = ChipShape,
                color = (if (streakActive) mc.lifePositive else mc.textDisabled).copy(alpha = 0.15f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (streakActive) mc.lifePositive else mc.textDisabled,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResourceSafe(R.string.home_progression_streak, gamification.streak),
                        style = ty.labelSmall,
                        color = mc.textPrimary,
                    )
                }
            }
            // Daily-quest completion.
            Text(
                text = stringResourceSafe(
                    R.string.home_progression_daily_done,
                    gamification.dailyDone,
                    gamification.dailyTotal,
                ),
                style = ty.labelSmall,
                color = mc.textSecondary,
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  QUESTS_HUB (gamification Phase 2) — top quests with inline progress; claimable stands out
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QuestsHubWidget(gamification: HomeGamification?, onAction: (HomeAction) -> Unit) {
    val spacing = MaterialTheme.spacing

    if (gamification == null) {
        WidgetShell { WidgetLoading() }
        return
    }

    WidgetShell(
        onClick = { onAction(HomeAction.OpenProfileQuests) },
        onClickLabel = stringResource(R.string.home_quests_open_a11y),
    ) {
        if (gamification.topQuests.isEmpty()) {
            WidgetEmptyBody(stringResourceSafe(R.string.home_quests_empty))
            return@WidgetShell
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            gamification.topQuests.forEach { quest ->
                HomeQuestRow(quest = quest)
            }
        }
    }
}

@Composable
private fun HomeQuestRow(quest: HomeQuest) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    // Claimable quests are visually distinct: a lifePositive border + a "ready" badge.
    Surface(
        shape = ChipShape,
        color = if (quest.isClaimable) mc.lifePositive.copy(alpha = 0.10f) else mc.surface.copy(alpha = 0.4f),
        border = if (quest.isClaimable) BorderStroke(1.dp, mc.lifePositive.copy(alpha = 0.5f)) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = quest.emoji, style = ty.bodyMedium)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.xxs),
            ) {
                Text(
                    text = quest.title,
                    style = ty.labelMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                HomeProgressBar(
                    progress = quest.progressFraction,
                    fillColor = if (quest.isClaimable) mc.lifePositive else mc.primaryAccent,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (quest.isClaimable) {
                Text(
                    text = stringResourceSafe(R.string.home_quests_claimable),
                    style = ty.labelSmall,
                    color = mc.lifePositive,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = "${quest.progress.coerceAtMost(quest.target)}/${quest.target}",
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * A thin determinate progress bar for the Home gamification widgets. Track uses a theme-agnostic
 * low-alpha foreground (NOT surfaceVariant — invisible on HallowedPrint); the fill is computed in the
 * layout pass so it tracks the measured width exactly.
 */
@Composable
private fun HomeProgressBar(
    progress: Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val target = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(6.dp) // intentional: thin bar
            .clip(CircleShape)
            .background(mc.textDisabled.copy(alpha = 0.25f)),
    ) {
        Box(
            modifier = Modifier
                .height(6.dp) // intentional: thin bar
                .clip(CircleShape)
                .background(fillColor)
                .layout { measurable, constraints ->
                    val width = (constraints.maxWidth * target).toInt().coerceAtLeast(0)
                    val placeable = measurable.measure(
                        constraints.copy(minWidth = width, maxWidth = width),
                    )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Quick actions — 2×2 grid panel (no section header; this widget IS the panel)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun QuickActionsWidget(actions: List<QuickStartAction>, onAction: (HomeAction) -> Unit) {
    val spacing = MaterialTheme.spacing
    val visible = actions.take(4)
    WidgetShell {
        visible.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                rowActions.forEach { action ->
                    QuickActionTile(
                        action = action,
                        onClick = { onAction(action.toHomeActionNav()) },
                        onLongClick = { onAction(HomeAction.CustomizeQuickStart) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowActions.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    action: QuickStartAction,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        color = mc.surface.copy(alpha = 0.4f),
        shape = CardShape,
        border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.15f)),
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(CardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = action.navIcon,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text = action.navLabel.uppercase(),
                style = ty.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Auto-sliding hub scaffold — reused by the four hub widgets
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * A reusable auto-sliding pager used by the hub widgets (game stats, collection,
 * social, trades). Shows [slides] one at a time, auto-advancing every 4 seconds while
 * the user is not actively swiping. Renders [emptyContent] when [slides] is empty and
 * a spinner while [loadingWhen] is true.
 */
@Composable
private fun <T> AutoSlideHub(
    slides: List<T>,
    slideContent: @Composable (T) -> Unit,
    emptyContent: @Composable () -> Unit = { WidgetEmptyBody(stringResourceSafe(R.string.home_widget_empty)) },
    loadingWhen: Boolean = false,
    showDots: Boolean = true,
    showNavigationIcons: Boolean = false,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val coroutineScope = rememberCoroutineScope()

    if (loadingWhen) {
        WidgetLoading()
        return
    }
    if (slides.isEmpty()) {
        emptyContent()
        return
    }

    val pagerState = rememberPagerState(pageCount = { slides.size })
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress) {
            delay(4_000L)
            if (slides.size > 1) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % slides.size)
            }
        }
    }

    Box(contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = spacing.xl)
            ) { page ->
                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                val alphaValue = (1f - kotlin.math.abs(pageOffset)).coerceIn(0f, 1f)
                val scaleValue = (1f - 0.2f * kotlin.math.abs(pageOffset)).coerceIn(0.8f, 1f)

                Box(
                    modifier = Modifier.graphicsLayer {
                        alpha = alphaValue
                        scaleX = scaleValue
                        scaleY = scaleValue
                    }
                ) {
                    slideContent(slides[page])
                }
            }
            if (showDots && slides.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    slides.forEachIndexed { idx, _ ->
                        val isCurrent = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .size(if (isCurrent) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isCurrent) mc.primaryAccent else mc.primaryAccent.copy(alpha = 0.30f),
                                ),
                        )
                        if (idx < slides.size - 1) Spacer(Modifier.width(spacing.xs))
                    }
                }
            }
        }

        if (showNavigationIcons && slides.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = stringResourceSafe(R.string.home_carousel_previous),
                    tint = mc.primaryAccent.copy(alpha = 0.25f),
                    modifier = Modifier
                        .size(28.dp)
                        .alpha(if (pagerState.currentPage > 0) 1f else 0.1f)
                        .clip(CircleShape)
                        .clickable(enabled = pagerState.currentPage > 0) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                            }
                        }
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = stringResourceSafe(R.string.home_carousel_next),
                    tint = mc.primaryAccent.copy(alpha = 0.25f),
                    modifier = Modifier
                        .size(28.dp)
                        .alpha(if (pagerState.currentPage < slides.size - 1) 1f else 0.1f)
                        .clip(CircleShape)
                        .clickable(enabled = pagerState.currentPage < slides.size - 1) {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                )
            }
        }
    }
}

/** A single hub slide: an optional leading badge, a big value, and supporting copy. */
@Composable
private fun HubSlide(content: @Composable ColumnScopeMarker.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        ColumnScopeMarker.content()
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  GAME_STATS_HUB
// ═══════════════════════════════════════════════════════════════════════════════

/** The kinds of slides the game-stats hub can show, in priority order. */
private sealed interface GameStatsSlide {
    data class WinRate(val stats: WinRateStats) : GameStatsSlide
    data class BestDeck(val stats: BestDeckStats) : GameStatsSlide
    data class Nemesis(val stats: NemesisStats) : GameStatsSlide
    data class Performance(val details: PerformanceDetails) : GameStatsSlide
    data class Streak(val streak: PlayStreak, val totalGames: Int) : GameStatsSlide
    data class LastGame(val recap: LastGameRecap) : GameStatsSlide
    /** Relocated from the retired SOCIAL_HUB (Home widget board overhaul, TASK 5c) — real data,
     * kept alive per the no-stub rule rather than dropped alongside the low-value slides. */
    data class ActiveTournament(val summary: TournamentSummary) : GameStatsSlide
}

@Composable
private fun GameStatsHubWidget(uiState: HomeUiState, onAction: (HomeAction) -> Unit) {
    val slides = remember(
        uiState.winRate, uiState.bestDeck, uiState.nemesis, uiState.performanceDetails,
        uiState.playStreak, uiState.lastGameRecap, uiState.activeTournamentSummary,
    ) {
        buildList {
            uiState.winRate?.let { add(GameStatsSlide.WinRate(it)) }
            uiState.bestDeck?.let { add(GameStatsSlide.BestDeck(it)) }
            uiState.nemesis?.let { add(GameStatsSlide.Nemesis(it)) }
            uiState.performanceDetails
                ?.takeIf { it.avgWinTurn != null || it.avgLifeOnWin != null }
                ?.let { add(GameStatsSlide.Performance(it)) }
            uiState.playStreak?.let { add(GameStatsSlide.Streak(it, uiState.winRate?.totalGames ?: 0)) }
            uiState.lastGameRecap?.let { add(GameStatsSlide.LastGame(it)) }
            uiState.activeTournamentSummary?.let { add(GameStatsSlide.ActiveTournament(it)) }
        }
    }
    WidgetShell {
        AutoSlideHub(
            slides = slides,
            emptyContent = { WidgetEmptyBody(stringResourceSafe(R.string.home_win_rate_empty)) },
            slideContent = { slide -> GameStatsSlideContent(slide, onAction) },
            showDots = false,
        )
    }
}

@Composable
private fun GameStatsSlideContent(slide: GameStatsSlide, onAction: (HomeAction) -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    when (slide) {
        is GameStatsSlide.ActiveTournament -> HubSlide {
            val summary = slide.summary
            ClickableBox(onClick = { onAction(HomeAction.OpenTournaments) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    HubBadge(Icons.Default.EmojiEvents, mc.primaryAccent)
                    Column {
                        Text(summary.name, style = ty.titleMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            Text(stringResourceSafe(R.string.home_tournament_round, summary.round), style = ty.labelSmall, color = mc.textSecondary)
                            summary.standing?.let {
                                Text(stringResourceSafe(R.string.home_tournament_standing, it), style = ty.labelSmall, color = mc.textSecondary)
                            }
                        }
                    }
                }
            }
        }
        is GameStatsSlide.WinRate -> {
            val stats = slide.stats
            val color = if (stats.percentage >= 50) mc.lifePositive else mc.lifeNegative
            HubSlide {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(HomeAction.OpenStats) }
                        .padding(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.lg)
                ) {
                    AnimatedWinRateRing(
                        percentage = stats.percentage,
                        color = color,
                        modifier = Modifier.size(80.dp)
                    )
                    Column {
                        Text(
                            text = stringResourceSafe(R.string.home_win_rate_record, stats.wins, stats.totalGames),
                            style = ty.labelSmall,
                            color = mc.textSecondary,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = stringResourceSafe(R.string.home_win_rate_caption),
                            style = ty.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = mc.textPrimary
                        )
                    }
                }
            }
        }
        is GameStatsSlide.BestDeck -> {
            val stats = slide.stats
            HubSlide {
                ClickableBox(onClick = { stats.deckId?.let { onAction(HomeAction.OpenDeck(it)) } ?: onAction(HomeAction.OpenDecks) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val infiniteTransition = rememberInfiniteTransition(label = "best-deck-glow")
                            val animatedGlowScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.4f,
                                animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                                label = "glow"
                            )
                            // F-13: reduced-motion users see the static end state, not a pulsing glow.
                            val glowScale = if (isReducedMotionEnabled()) 1.4f else animatedGlowScale
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer(scaleX = glowScale, scaleY = glowScale)
                                    .clip(CircleShape)
                                    .background(mc.lifePositive.copy(alpha = 0.1f))
                            )
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = mc.lifePositive.copy(alpha = 0.2f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = mc.lifePositive, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResourceSafe(R.string.home_best_performer_caption),
                                style = ty.labelSmall,
                                color = mc.lifePositive,
                                letterSpacing = 1.sp
                            )
                            Text(stats.deckName, style = ty.titleMedium.copy(fontWeight = FontWeight.Bold), color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                Text(
                                    text = stringResourceSafe(R.string.home_win_rate_percent, stats.winRate),
                                    style = ty.labelMedium,
                                    color = mc.textPrimary,
                                )
                                ColorIdentityDots(stats.colorIdentity)
                            }
                        }
                    }
                }
            }
        }
        is GameStatsSlide.Nemesis -> {
            val stats = slide.stats
            HubSlide {
                ClickableBox(onClick = { onAction(HomeAction.OpenStats) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = CardShape,
                            color = mc.lifeNegative.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, mc.lifeNegative.copy(alpha = 0.2f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Whatshot, contentDescription = null, tint = mc.lifeNegative, modifier = Modifier.size(32.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResourceSafe(R.string.home_current_nemesis_caption),
                                style = ty.labelSmall,
                                color = mc.lifeNegative,
                                letterSpacing = 1.sp
                            )
                            Text(stats.archetype, style = ty.titleMedium.copy(fontWeight = FontWeight.Bold), color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = stringResourceSafe(R.string.home_nemesis_count, stats.count, stats.percentage),
                                style = ty.labelSmall,
                                color = mc.textSecondary,
                            )
                        }
                    }
                }
            }
        }
        is GameStatsSlide.Performance -> {
            val d = slide.details
            HubSlide {
                ClickableBox(onClick = { onAction(HomeAction.OpenStats) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(spacing.md),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        AnimatedStatKPI(
                            value = d.avgWinTurn?.toInt()?.toString() ?: "—",
                            prefix = "T",
                            label = stringResourceSafe(R.string.home_performance_win_turn),
                            icon = Icons.AutoMirrored.Filled.TrendingUp
                        )
                        AnimatedStatKPI(
                            value = d.avgLifeOnWin?.toInt()?.toString() ?: "—",
                            label = stringResourceSafe(R.string.home_performance_life_win),
                            icon = Icons.Default.Insights
                        )
                    }
                }
            }
        }
        is GameStatsSlide.Streak -> {
            val streak = slide.streak
            HubSlide {
                val color = if (streak.current > 0) mc.lifeNegative else mc.primaryAccent
                StatKPI(
                    value = (if (streak.current > 0) streak.current else slide.totalGames).toString(),
                    label = if (streak.current > 0) stringResourceSafe(R.string.home_streak_current, streak.current) else stringResourceSafe(R.string.home_win_rate_games, slide.totalGames),
                    color = color,
                    icon = if (streak.current > 0) Icons.Default.LocalFireDepartment else Icons.Default.SportsEsports,
                    onClick = { onAction(HomeAction.StartGame) }
                )
            }
        }
        is GameStatsSlide.LastGame -> {
            val recap = slide.recap
            HubSlide {
                ClickableBox(onClick = { onAction(HomeAction.StartGame) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(spacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedResultPulse(recap.won)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResourceSafe(R.string.home_latest_battle_caption),
                                style = ty.labelSmall,
                                color = mc.textSecondary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = recap.deckName ?: recap.mode,
                                style = ty.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = mc.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val modeLine = if (recap.opponentCount > 0) {
                                stringResourceSafe(R.string.home_last_game_mode_vs_opponents, recap.mode, formatDuration(recap.durationMs), recap.opponentCount)
                            } else {
                                stringResourceSafe(R.string.home_last_game_mode_duration, recap.mode, formatDuration(recap.durationMs))
                            }
                            Text(modeLine, style = ty.labelSmall, color = mc.textSecondary)
                        }
                        Icon(Icons.Default.History, contentDescription = null, tint = mc.textDisabled, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedWinRateRing(
    percentage: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val progress = remember { Animatable(0f) }
    // F-13: reduced-motion users see the final ring value immediately, no sweep animation.
    val reducedMotion = isReducedMotionEnabled()

    LaunchedEffect(percentage, reducedMotion) {
        if (reducedMotion) {
            progress.snapTo(percentage / 100f)
        } else {
            progress.animateTo(
                targetValue = percentage / 100f,
                animationSpec = tween(1200, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            drawCircle(
                color = color.copy(alpha = 0.1f),
                style = Stroke(width = strokeWidth)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress.value,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(progress.value * 100).toInt()}%",
                style = ty.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = mc.textPrimary
            )
        }
    }
}

@Composable
private fun AnimatedStatKPI(
    value: String,
    label: String,
    icon: ImageVector,
    prefix: String = ""
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = mc.primaryAccent.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = mc.primaryAccent, modifier = Modifier.size(20.dp))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$prefix$value",
                style = ty.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = mc.textPrimary
            )
            Text(
                text = label.uppercase(),
                style = ty.labelSmall.copy(fontSize = CompactCaptionSize),
                color = mc.textSecondary,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun AnimatedResultPulse(won: Boolean) {
    val mc = MaterialTheme.magicColors
    val color = if (won) mc.lifePositive else mc.lifeNegative
    
    val infiniteTransition = rememberInfiniteTransition(label = "result-pulse")
    val animatedAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulse"
    )
    // F-13: reduced-motion users see the static end state, not a pulsing halo.
    val alpha = if (isReducedMotionEnabled()) 0.6f else animatedAlpha

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(color.copy(alpha = alpha))
        )
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  COLLECTION_STATS_HUB
// ═══════════════════════════════════════════════════════════════════════════════

private sealed interface CollectionSlide {
    data class Snapshot(val stats: LibraryStats) : CollectionSlide
    data class Colors(val byColor: Map<String, Int>) : CollectionSlide
    data class Rarity(val byRarity: Map<String, Int>) : CollectionSlide
}

@Composable
private fun CollectionStatsHubWidget(uiState: HomeUiState, onAction: (HomeAction) -> Unit) {
    val slides = remember(uiState.libraryStats, uiState.collectionByColor, uiState.collectionByRarity) {
        buildList {
            uiState.libraryStats?.let { add(CollectionSlide.Snapshot(it)) }
            if (uiState.collectionByColor.values.sum() > 0) add(CollectionSlide.Colors(uiState.collectionByColor))
            if (uiState.collectionByRarity.values.sum() > 0) add(CollectionSlide.Rarity(uiState.collectionByRarity))
        }
    }
    WidgetShell {
        AutoSlideHub(
            slides = slides,
            emptyContent = { WidgetEmptyBody(stringResourceSafe(R.string.home_collection_color_empty)) },
            slideContent = { slide -> CollectionSlideContent(slide, onAction) },
            showDots = false,
            showNavigationIcons = true
        )
    }
}

@Composable
private fun CollectionSlideContent(slide: CollectionSlide, onAction: (HomeAction) -> Unit) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    when (slide) {
        is CollectionSlide.Snapshot -> {
            val stats = slide.stats
            HubSlide {
                ClickableBox(onClick = { onAction(HomeAction.OpenLibrary) }) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            StatBox(
                                value = stats.totalCards.toString(),
                                label = stringResourceSafe(R.string.stats_total_cards),
                                modifier = Modifier.weight(1f)
                            )
                            StatBox(
                                value = stats.uniqueCards.toString(),
                                label = stringResourceSafe(R.string.stats_unique_cards),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            StatBox(
                                value = stats.estimatedValueDisplay,
                                label = stringResourceSafe(R.string.home_library_value_label),
                                modifier = Modifier.weight(1f),
                                valueColor = mc.goldMtg
                            )
                            StatBox(
                                value = stats.deckCount.toString(),
                                label = stringResourceSafe(R.string.home_collection_decks_label),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
        is CollectionSlide.Colors -> {
            // F-12: these hex values are the official, fixed MTG brand WUBRG colors (mirroring
            // the Stats screen's own pie chart) — never tokenized, since they represent the game's
            // color pie identity itself rather than app theming, and stay constant across all 12
            // ManaHub themes by design.
            val colorWhite = stringResourceSafe(R.string.stats_color_white)
            val colorBlue = stringResourceSafe(R.string.stats_color_blue)
            val colorBlack = stringResourceSafe(R.string.stats_color_black)
            val colorRed = stringResourceSafe(R.string.stats_color_red)
            val colorGreen = stringResourceSafe(R.string.stats_color_green)
            val colorColorless = stringResourceSafe(R.string.stats_color_colorless)
            val colorUnknown = stringResourceSafe(R.string.stats_color_unknown)

            HubSlide {
                ClickableBox(onClick = { onAction(HomeAction.OpenLibrary) }) {
                    CircularDistribution(
                        data = slide.byColor.entries
                            .associate { (code, count) ->
                                val label = when (code) {
                                    "W" -> colorWhite
                                    "U" -> colorBlue
                                    "B" -> colorBlack
                                    "R" -> colorRed
                                    "G" -> colorGreen
                                    "C" -> colorColorless
                                    else -> colorUnknown
                                }
                                label to count
                            },
                        colorMapper = { label ->
                            when (label) {
                                colorWhite -> Color(0xFFF9FAFA)
                                colorBlue -> Color(0xFF0E68AB)
                                colorBlack -> Color(0xFF150B00)
                                colorRed -> Color(0xFFD3202A)
                                colorGreen -> Color(0xFF00733E)
                                colorColorless -> Color(0xFF90ADBB)
                                else -> mc.primaryAccent
                            }
                        },
                        isColor = true,
                        isCompact = true
                    )
                }
            }
        }
        is CollectionSlide.Rarity -> {
            // F-12: fixed rarity brand colors (silver/steel/gold/orange), same rationale as above.
            val rarityCommon = stringResourceSafe(R.string.home_rarity_common)
            val rarityUncommon = stringResourceSafe(R.string.home_rarity_uncommon)
            val rarityRare = stringResourceSafe(R.string.home_rarity_rare)
            val rarityMythic = stringResourceSafe(R.string.home_rarity_mythic)

            HubSlide {
                ClickableBox(onClick = { onAction(HomeAction.OpenLibrary) }) {
                    CircularDistribution(
                        data = slide.byRarity.entries.associate { entry ->
                            val label = when (entry.key) {
                                "MYTHIC" -> rarityMythic
                                "RARE" -> rarityRare
                                "UNCOMMON" -> rarityUncommon
                                "COMMON" -> rarityCommon
                                else -> entry.key
                            }
                            label to entry.value
                        },
                        colorMapper = { label ->
                            when (label) {
                                rarityCommon -> Color(0xFFC0C0C0)
                                rarityUncommon -> Color(0xFFB0C4DE)
                                rarityRare -> Color(0xFFC9A84C)
                                rarityMythic -> Color(0xFFE8A030)
                                else -> Color(0xFF9B6EFF)
                            }
                        },
                        isCompact = true
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBox(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.magicColors.textPrimary
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = modifier,
        color = mc.surfaceVariant.copy(alpha = 0.4f),
        shape = CardShape,
        border = BorderStroke(0.5.dp, mc.primaryAccent.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = spacing.md, horizontal = spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = ty.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label.uppercase(),
                style = ty.labelSmall.copy(fontSize = CompactCaptionSize),
                color = mc.textSecondary,
                textAlign = TextAlign.Center,
                letterSpacing = 0.5.sp
            )
        }
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
//  YOUR_DECKS_SHELF
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DecksShelfWidget(decks: List<DeckSummary>?, onAction: (HomeAction) -> Unit) {
    val spacing = MaterialTheme.spacing
    WidgetShell(onClick = { onAction(HomeAction.OpenDecks) }) {
        // TASK 7b: null = still loading; distinguishes from "the user genuinely has zero decks".
        if (decks == null) {
            WidgetLoading()
            return@WidgetShell
        }
        if (decks.isEmpty()) {
            WidgetEmptyBody(stringResourceSafe(R.string.home_decks_empty))
            return@WidgetShell
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            items(decks, key = { it.id }) { deck ->
                DeckItem(
                    deck            = deck,
                    onClick         = { onAction(HomeAction.OpenDeck(deck.id)) },
                    reduced         = true,
                    cardBackPainter = painterResource(Res.drawable.mtg_card_back),
                    modifier        = Modifier.width(160.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  RECENTLY_ADDED (Home feature overhaul Phase 2.1) — newest local collection additions
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RecentlyAddedWidget(
    entries: List<RecentlyAddedCard>?,
    onAction: (HomeAction) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val spacing = MaterialTheme.spacing
    WidgetShell(onClick = { onAction(HomeAction.OpenLibrary) }) {
        // TASK 7b: null = still loading; distinguishes from "the collection genuinely has nothing".
        if (entries == null) {
            WidgetLoading()
            return@WidgetShell
        }
        if (entries.isEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                WidgetEmptyBody(stringResourceSafe(R.string.home_recently_added_empty))
                PillButton(
                    label = stringResourceSafe(R.string.home_recently_added_cta),
                    icon = Icons.Default.Camera,
                    onClick = { onAction(HomeAction.ScanCard) },
                )
            }
            return@WidgetShell
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            items(entries, key = { "recent|${it.rowId}" }) { entry ->
                Box {
                    val uniqueKey = "recent|${entry.rowId}"
                    DiscoverCardThumb(
                        card = entry.card,
                        onClick = { onAction(HomeAction.OpenCardDetail(entry.card.scryfallId, uniqueKey)) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedTransitionKey = uniqueKey
                    )
                    if (entry.quantity > 1) {
                        QuantityBadge(
                            quantity = entry.quantity,
                            modifier = Modifier.align(Alignment.TopEnd).padding(spacing.xxs),
                        )
                    }
                }
            }
        }
    }
}

/** Small accent-filled circular badge showing a copy count (e.g. "x3"), top-corner overlay. */
@Composable
private fun QuantityBadge(quantity: Int, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        modifier = modifier,
        color = mc.primaryAccent,
        shape = ChipShape,
    ) {
        Text(
            text = stringResourceSafe(R.string.home_recently_added_quantity, quantity),
            style = ty.labelSmall,
            color = mc.background,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  WISHLIST_PROGRESS
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun WishlistWidget(
    stats: WishlistStats?,
    isAuthenticated: Boolean,
    authResolved: Boolean,
    onAction: (HomeAction) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    // TASK 7a: while the session is still resolving, show a spinner — never the gated placeholder.
    if (!authResolved) {
        WidgetShell { WidgetLoading() }
        return
    }
    if (!isAuthenticated) {
        AccountGatedPlaceholder(stringResourceSafe(R.string.widget_title_wishlist)) { onAction(HomeAction.CreateAccount) }
        return
    }
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    WidgetShell(onClick = { onAction(HomeAction.OpenWishlist) }) {
        if (stats == null || stats.count == 0) {
            WidgetEmptyBody(stringResourceSafe(R.string.home_wishlist_empty))
            return@WidgetShell
        }
        
        val showValue = stats.estimatedValueDisplay.isNotBlank()
        if (stats.cards.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                if (showValue) {
                    Text(
                        text = stringResourceSafe(R.string.home_wishlist_value, stats.estimatedValueDisplay),
                        style = ty.labelMedium,
                        color = mc.goldMtg,
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    items(stats.cards.toList(), key = { "wishlist|${it.id}" }) { card ->
                        val uniqueKey = "wishlist|${card.id}"
                        DiscoverCardThumb(
                            card = card,
                            onClick = { onAction(HomeAction.OpenCardDetail(card.scryfallId, uniqueKey)) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedTransitionKey = uniqueKey
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                StatKPI(
                    value = stats.count.toString(),
                    label = stringResourceSafe(R.string.widget_title_wishlist),
                    color = mc.goldMtg,
                    icon = Icons.Default.Star,
                    onClick = { onAction(HomeAction.OpenWishlist) }
                )
                if (showValue) {
                    Text(
                        text = stringResourceSafe(R.string.home_wishlist_value, stats.estimatedValueDisplay),
                        style = ty.labelSmall,
                        color = mc.goldMtg,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  DISCOVER_CARDS
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DiscoverCardsWidget(
    cards: List<DiscoverCard>,
    loadState: DiscoverLoadState,
    onAction: (HomeAction) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    WidgetShell(onClick = { onAction(HomeAction.SearchCard) }) {
        when {
            // Empty + loading: full spinner (initial load / a refresh that just cleared the row).
            cards.isEmpty() && loadState == DiscoverLoadState.LOADING -> WidgetLoading()
            // Empty + not loading: a failed/empty fetch shows a retry affordance.
            cards.isEmpty() -> WidgetRetryBody(
                message = stringResourceSafe(R.string.home_discover_unavailable),
                onRetry = { onAction(HomeAction.RefreshDiscover) },
            )
            else -> {
                // While cards are still streaming in, show a thin inline spinner above the row so a
                // refresh that already has partial results still reads as "loading".
                if (loadState == DiscoverLoadState.LOADING) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            color = mc.primaryAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    items(cards, key = { "discover|${it.id}" }) { card ->
                        val uniqueKey = "discover|${card.id}"
                        DiscoverCardThumb(
                            card = card,
                            onClick = { onAction(HomeAction.OpenCardDetail(card.scryfallId, uniqueKey)) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedTransitionKey = uniqueKey
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun DiscoverCardThumb(
    card: DiscoverCard,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    sharedTransitionKey: String? = null,
) {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = Modifier
            .width(110.dp)
            // Full MTG card aspect ratio (745:1040) so the whole card is shown.
            .aspectRatio(0.717f)
            .clip(CardShape)
            .then(
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(
                                key = sharedTransitionKey ?: "card-image-${card.scryfallId}"
                            ),
                            animatedVisibilityScope = animatedVisibilityScope,
                            clipInOverlayDuringTransition = OverlayClip(CardShape),
                            renderInOverlayDuringTransition = true,
                        )
                    }
                } else Modifier
            )
            .background(mc.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (card.imageUrl != null) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
                placeholder = painterResource(Res.drawable.mtg_card_back),
                error = painterResource(Res.drawable.mtg_card_back),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.Style, contentDescription = null, tint = mc.textDisabled, modifier = Modifier.size(24.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  CARD_OF_THE_DAY (enum) → Random card widget: a single full card image, centered
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RandomCardWidget(
    card: DiscoverCard?,
    loadState: DiscoverLoadState,
    onAction: (HomeAction) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val mc = MaterialTheme.magicColors
    WidgetShell(onClick = { card?.let { onAction(HomeAction.OpenCardDetail(it.scryfallId, "random_card|${it.scryfallId}")) } }) {
        // Priority: LOADING → spinner (even over a previously-shown card, so refresh gives visible
        // feedback); LOADED + card → full image; otherwise → retry affordance.
        if (loadState == DiscoverLoadState.LOADING) {
            WidgetLoading()
            return@WidgetShell
        }
        if (card == null) {
            WidgetRetryBody(
                message = stringResourceSafe(R.string.home_discover_unavailable),
                onRetry = { onAction(HomeAction.RefreshRandomCard) },
            )
            return@WidgetShell
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    // Constrain the width so the full card shows at a pleasant size even on LARGE.
                    .fillMaxWidth(0.62f)
                    // Full MTG card aspect ratio (745:1040).
                    .aspectRatio(0.717f)
                    .clip(CardShape)
                    .then(
                        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                            with(sharedTransitionScope) {
                                Modifier.sharedBounds(
                                    sharedContentState = rememberSharedContentState(
                                        key = "random_card|${card.scryfallId}"
                                    ),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    clipInOverlayDuringTransition = OverlayClip(CardShape),
                                    renderInOverlayDuringTransition = true,
                                )
                            }
                        } else Modifier
                    )
                    .background(mc.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (card.imageUrl != null) {
                    AsyncImage(
                        model = card.imageUrl,
                        contentDescription = card.name,
                        placeholder = painterResource(Res.drawable.mtg_card_back),
                        error = painterResource(Res.drawable.mtg_card_back),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Default.Style, contentDescription = null, tint = mc.textDisabled, modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

/**
 * Shared "See all" trailing tile for horizontal widget rows (Latest Sets, News).
 *
 * A prominent, clearly-tappable tile (≥48dp) with a chevron + label, consistent across
 * widgets. Full-height so it lines up with the cards it follows in the [LazyRow].
 */
@Composable
private fun SeeAllTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier
            .width(96.dp)
            .heightIn(min = 100.dp)
            .clip(CardShape)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        mc.primaryAccent.copy(alpha = 0.15f),
                        mc.primaryAccent.copy(alpha = 0.0f)
                    )
                )
            )
            .clickable(onClickLabel = stringResourceSafe(R.string.home_news_see_all), role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            mc.primaryAccent.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = stringResourceSafe(R.string.home_news_see_all),
            style = ty.labelMedium,
            color = mc.primaryAccent,
            maxLines = 1,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  LATEST_SETS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LatestSetsWidget(sets: List<DraftSet>, onAction: (HomeAction) -> Unit) {
    val spacing = MaterialTheme.spacing
    WidgetShell(onClick = { onAction(HomeAction.OpenDraftGuide) }) {
        if (sets.isEmpty()) {
            WidgetEmptyBody(stringResourceSafe(R.string.home_latest_sets_empty))
            return@WidgetShell
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(sets, key = { it.id }) { set ->
                DraftSetCard(
                    set = set,
                    onClick = { onAction(HomeAction.OpenDraftSetDetail(set)) },
                    modifier = Modifier.width(180.dp),
                )
            }
            item(key = "see_more") {
                SeeAllTile(onClick = { onAction(HomeAction.OpenDraftGuide) })
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  MTG_NEWS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NewsWidget(
    news: List<NewsItem>?,
    filtersActive: Boolean,
    onAction: (HomeAction) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    WidgetShell(onClick = { onAction(HomeAction.OpenNews) }) {
        when {
            news == null -> WidgetLoading()
            // Empty list: offer to reset the (possibly over-restrictive) persisted filters.
            news.isEmpty() -> NewsEmptyWithReset(filtersActive = filtersActive, onAction = onAction)
            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items(news.take(MAX_NEWS_PREVIEW), key = { it.id }) { item ->
                    NewsItemCard(
                        item = item,
                        orientation = NewsItemOrientation.VERTICAL,
                        placeholderPainter = painterResource(Res.drawable.mtg_card_back),
                        modifier = Modifier.width(220.dp),
                        onClick = { onAction(HomeAction.OpenNewsUrl(item.url)) },
                    )
                }
                item(key = "see_more") {
                    SeeAllTile(onClick = { onAction(HomeAction.OpenNews) })
                }
            }
        }
    }
}

/**
 * Empty-state body for the News widget. Always offers a "Reset filters" button (per spec)
 * so the user can recover when the persisted filters hide everything; the message hints at
 * filtering when [filtersActive].
 */
@Composable
private fun NewsEmptyWithReset(filtersActive: Boolean, onAction: (HomeAction) -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        WidgetEmptyBody(stringResourceSafe(R.string.home_news_empty))
        Surface(
            color = mc.primaryAccent.copy(alpha = 0.15f),
            shape = ButtonShape,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(ButtonShape)
                .clickable(
                    onClickLabel = stringResourceSafe(R.string.home_news_reset_filters),
                    role = Role.Button,
                    onClick = { onAction(HomeAction.ResetNewsFilters) },
                ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResourceSafe(R.string.home_news_reset_filters),
                    style = ty.labelSmall,
                    color = mc.primaryAccent,
                    maxLines = 1,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  RULES_TIP — daily tip with HorizontalPager browsing
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun RulesTipWidget() {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    // Deterministic, dependency-free daily ordering (Home feature overhaul Phase 2.4): a
    // fixed-seed shuffle avoids long same-category streaks (the catalog is grouped by category)
    // while staying stable across recompositions and identical for every user on a given UTC day.
    // Cycle length = catalog size; today's index is the epoch-day modulus INTO the shuffled list.
    val shuffledTips = remember { MTG_TIPS_CATALOG.shuffled(Random(RULES_TIP_SHUFFLE_SEED)) }
    val tipIndex = remember(shuffledTips) { (System.currentTimeMillis() / 86_400_000L % shuffledTips.size).toInt() }
    val pagerState = rememberPagerState(initialPage = tipIndex, pageCount = { shuffledTips.size })

    WidgetShell {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val tip = shuffledTips[page]
            val title = tip.title
            val body = tip.body
            Surface(
                color = mc.surfaceVariant.copy(alpha = 0.25f),
                shape = CardShape,
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            mc.primaryAccent.copy(alpha = 0.15f),
                            mc.primaryAccent.copy(alpha = 0.02f)
                        )
                    )
                ),
                modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Decorative background icon
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = mc.primaryAccent.copy(alpha = 0.04f),
                        modifier = Modifier
                            .size(100.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 20.dp, y = 20.dp)
                            .rotate(-15f)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.xs)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(mc.primaryAccent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = mc.primaryAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = title,
                                style = ty.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = mc.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Category chip (Phase 2.4): uppercase labelSmall, accent-tinted, token-based.
                        Surface(
                            color = mc.primaryAccent.copy(alpha = 0.12f),
                            shape = ChipShape,
                        ) {
                            Text(
                                text = stringResourceSafe(tip.category.labelRes).uppercase(),
                                style = ty.labelSmall,
                                color = mc.primaryAccent,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                            )
                        }

                        OracleText(
                            text = body,
                            style = ty.bodySmall.copy(
                                color = mc.textSecondary,
                                lineHeight = RulesTipBodyLineHeight
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// SOCIAL_HUB was split into FRIENDS + COMMUNITY_DECKS (Home widget board overhaul, TASK 5) —
// see those widgets above. Its old MostWishlisted/Milestones slides were dropped as low-value
// (TASK 5c); ActiveTournament moved into GAME_STATS_HUB (see GameStatsSlide.ActiveTournament).

// ═══════════════════════════════════════════════════════════════════════════════
//  FRIENDS (Home widget board overhaul, TASK 5a — split off SOCIAL_HUB's friends slide)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun FriendsWidget(
    friends: List<Friend>?,
    friendCount: Int,
    latestFriendRequestName: String?,
    isAuthenticated: Boolean,
    authResolved: Boolean,
    onAction: (HomeAction) -> Unit,
) {
    // TASK 7a: while the session is still resolving, show a spinner — never the gated placeholder
    // (which would otherwise flash before a real signed-in session lands).
    if (!authResolved) {
        WidgetShell { WidgetLoading() }
        return
    }
    if (!isAuthenticated) {
        AccountGatedPlaceholder(stringResourceSafe(R.string.widget_title_friends)) { onAction(HomeAction.CreateAccount) }
        return
    }
    // TASK 7b: null = still loading the friend list.
    if (friends == null) {
        WidgetShell { WidgetLoading() }
        return
    }
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    WidgetShell(onClick = { onAction(HomeAction.OpenFriends) }) {
        if (latestFriendRequestName != null) {
            Surface(
                color = mc.primaryAccent.copy(alpha = 0.12f),
                shape = ChipShape,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = mc.primaryAccent, modifier = Modifier.size(18.dp))
                    Text(
                        text = stringResourceSafe(R.string.home_friends_pending_request, latestFriendRequestName),
                        style = ty.labelMedium,
                        color = mc.primaryAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (friends.isEmpty()) {
            WidgetEmptyBody(stringResourceSafe(R.string.home_friends_empty))
            return@WidgetShell
        }
        Text(
            text = stringResourceSafe(R.string.home_friends_count, friendCount),
            style = ty.labelSmall,
            color = mc.textSecondary,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            items(friends, key = { it.id }) { friend ->
                Column(
                    modifier = Modifier
                        .width(72.dp)
                        .clip(CardShape)
                        .clickable(onClick = { onAction(HomeAction.OpenFriends) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                ) {
                    AvatarImage(avatarUrl = friend.avatarUrl, initials = friend.nickname.take(1).uppercase(), size = 56)
                    Text(
                        text = friend.nickname,
                        style = ty.labelSmall,
                        color = mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  COMMUNITY_DECKS (Home widget board overhaul, TASK 5b — split off SOCIAL_HUB's Archidekt
//  trending-deck slide; richer real DeckItem cards + native tap-through)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CommunityDecksWidget(
    decks: List<com.mmg.manahub.core.model.CommunityDeckSummary>?,
    onAction: (HomeAction) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    WidgetShell {
        // TASK 7b: null = still loading this category's decks.
        if (decks == null) {
            WidgetLoading()
            return@WidgetShell
        }
        if (decks.isEmpty()) {
            WidgetEmptyBody(stringResourceSafe(R.string.home_community_decks_empty))
            return@WidgetShell
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(decks, key = { it.archidektId }) { deck ->
                DeckItem(
                    deck = deck.toDeckSummary(),
                    onClick = { onAction(HomeAction.OpenCommunityDeck(deck.archidektId)) },
                    reduced = true,
                    ownerName = deck.owner.username,
                    cardBackPainter = painterResource(Res.drawable.mtg_card_back),
                    modifier = Modifier.width(160.dp),
                )
            }
            item(key = "see_more") {
                SeeAllTile(onClick = { onAction(HomeAction.OpenCommunityDecks) })
            }
        }
    }
}

/** Projects a lightweight Archidekt search result onto the shared [DeckItem]'s [DeckSummary] shape. */
private fun com.mmg.manahub.core.model.CommunityDeckSummary.toDeckSummary(): DeckSummary = DeckSummary(
    id = archidektId.toString(),
    name = name,
    description = null,
    format = format,
    coverCardId = null,
    createdAt = 0L,
    updatedAt = 0L,
    cardCount = size,
    colorIdentity = colorIdentity.toSet(),
    coverImageUrl = featuredImageUrl,
)

/**
 * Community-decks widget category selector affordance. Shows the active category's title
 * + a chevron. The whole row is a ≥48dp tap target that re-opens the category picker.
 */
@Composable
private fun CommunityDecksCategoryAffordance(
    category: HomeCommunityDeckCategory,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val label = stringResource(category.titleRes)

    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(ChipShape)
            .clickable(
                onClickLabel = stringResource(R.string.home_community_decks_category_a11y),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Text(
            text = label.uppercase(),
            style = ty.labelMedium,
            color = mc.primaryAccent,
            maxLines = 1,
            letterSpacing = 1.sp
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = mc.primaryAccent,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Category picker for the COMMUNITY_DECKS widget (TASK 5b), mirroring [SetPickerSheet]'s pattern. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityDecksCategoryPickerSheet(
    selected: HomeCommunityDeckCategory,
    onSelect: (HomeCommunityDeckCategory) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(top = spacing.xl, bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResourceSafe(R.string.home_community_decks_category_sheet_title),
                style = ty.titleLarge,
                color = mc.textPrimary,
                modifier = Modifier.padding(bottom = spacing.xs),
            )

            // 2x2 Grid of category cards.
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                HomeCommunityDeckCategory.entries.chunked(2).forEach { rowCategories ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        rowCategories.forEach { category ->
                            CategorySelectionCard(
                                category = category,
                                isSelected = category == selected,
                                onClick = { onSelect(category) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(spacing.sm))
        }
    }
}

@Composable
private fun CategorySelectionCard(
    category: HomeCommunityDeckCategory,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val icon = when (category) {
        HomeCommunityDeckCategory.POPULAR -> Icons.Default.Whatshot
        HomeCommunityDeckCategory.RECENT -> Icons.Default.History
        HomeCommunityDeckCategory.UPDATED -> Icons.Default.Refresh
        HomeCommunityDeckCategory.PRIMERS -> Icons.AutoMirrored.Filled.MenuBook
    }

    Surface(
        color = if (isSelected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface.copy(alpha = 0.4f),
        shape = CardShape,
        border = if (isSelected) BorderStroke(1.5.dp, mc.primaryAccent.copy(alpha = 0.5f)) else null,
        modifier = modifier
            .heightIn(min = 120.dp)
            .clip(CardShape)
            .clickable(
                onClickLabel = stringResource(category.titleRes),
                role = Role.Button,
                onClick = onClick
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) mc.primaryAccent else mc.textSecondary,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = stringResource(category.titleRes),
                style = ty.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isSelected) mc.primaryAccent else mc.textPrimary,
                maxLines = 1,
            )
            Text(
                text = stringResource(category.descriptionRes),
                style = ty.bodySmall,
                color = mc.textSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  TRADES_HUB (account-gated)
// ═══════════════════════════════════════════════════════════════════════════════

/** Home widget board overhaul, TASK 4b — real card-based content replaces the old count-only slides. */
private sealed interface TradesSlide {
    data class Inbox(val summary: TradeSummary?) : TradesSlide
    data class Suggestions(val previews: List<TradeSuggestionPreview>) : TradesSlide
    data class OpenForTrade(val preview: OpenForTradePreview) : TradesSlide
    data class RecentActivity(val proposals: List<TradeProposal>) : TradesSlide
}

@Composable
private fun TradesHubWidget(uiState: HomeUiState, onAction: (HomeAction) -> Unit) {
    // TASK 7a: while the session is still resolving, show a spinner — never the gated placeholder.
    if (!uiState.authResolved) {
        WidgetShell { WidgetLoading() }
        return
    }
    if (!uiState.isAuthenticated) {
        AccountGatedPlaceholder(stringResourceSafe(R.string.widget_title_trades_hub)) { onAction(HomeAction.CreateAccount) }
        return
    }
    val suggestionPreviews = uiState.tradeSuggestionPreviews
    val openForTrade = uiState.openForTradePreview
    val recentTrades = uiState.recentTrades
    // TASK 7b: never render a section whose backing slice hasn't finished loading yet — wait for
    // every slice once so the widget flips straight to its final content, with no partial swaps.
    if (suggestionPreviews == null || openForTrade == null || recentTrades == null) {
        WidgetShell { WidgetLoading() }
        return
    }
    val slides = remember(uiState.tradeSummary, suggestionPreviews, openForTrade, recentTrades) {
        buildList {
            if (recentTrades.isNotEmpty()) {
                add(TradesSlide.RecentActivity(recentTrades))
            }
            add(TradesSlide.Inbox(uiState.tradeSummary))
            if (suggestionPreviews.isNotEmpty()) {
                add(TradesSlide.Suggestions(suggestionPreviews))
            }
            add(TradesSlide.OpenForTrade(openForTrade))
        }
    }
    WidgetShell {
        AutoSlideHub(
            slides = slides,
            slideContent = { slide -> TradesSlideContent(slide, onAction) },
            showDots = false,
        )
    }
}

@Composable
private fun TradesSlideContent(
    slide: TradesSlide,
    onAction: (HomeAction) -> Unit
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    when (slide) {
        is TradesSlide.RecentActivity -> HubSlide {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = stringResourceSafe(R.string.trades_tab_history),
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(horizontal = spacing.xs)
                )
                slide.proposals.forEach { proposal ->
                    ReducedTradeProposalRow(
                        proposal = proposal,
                        onClick = { onAction(HomeAction.OpenTrades) }
                    )
                }
            }
        }
        is TradesSlide.Inbox -> HubSlide {
            ClickableBox(onClick = { onAction(HomeAction.OpenTrades) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    HubBadge(Icons.Default.SwapHoriz, mc.primaryAccent)
                    Column {
                        Text(stringResourceSafe(R.string.widget_title_trade_inbox), style = ty.titleMedium, color = mc.textPrimary)
                        val summary = slide.summary
                        if (summary == null || summary.pendingCount == 0) {
                            Text(stringResourceSafe(R.string.home_trade_inbox_empty), style = ty.labelSmall, color = mc.textSecondary)
                        } else {
                            Text(stringResourceSafe(R.string.home_trade_inbox_count, summary.pendingCount), style = ty.titleMedium, color = mc.primaryAccent)
                            summary.latestItemCount?.let { itemCount ->
                                Text(
                                    stringResourceSafe(R.string.home_trade_inbox_preview, itemCount),
                                    style = ty.labelSmall, color = mc.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
        is TradesSlide.Suggestions -> HubSlide {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(
                    text = stringResourceSafe(R.string.widget_title_trade_suggestions),
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(horizontal = spacing.xs),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    items(slide.previews, key = { "trade_sug|${it.id}" }) { preview ->
                        Column(
                            modifier = Modifier.width(72.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(spacing.xxs),
                        ) {
                            val uniqueKey = "trade_sug|${preview.id}"
                            DiscoverCardThumb(
                                card = preview.card,
                                onClick = { onAction(HomeAction.OpenCardDetail(preview.card.scryfallId, uniqueKey)) },
                                sharedTransitionKey = uniqueKey
                            )
                            // Never log the friend's name — this is UI-only, no telemetry key here.
                            preview.counterpartyName?.let { name ->
                                Text(
                                    text = name,
                                    style = ty.labelSmall,
                                    color = mc.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
        is TradesSlide.OpenForTrade -> HubSlide {
            val preview = slide.preview
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResourceSafe(R.string.widget_title_open_for_trade), style = ty.labelMedium, color = mc.textSecondary)
                    preview.valueDisplay?.let {
                        Text(
                            stringResourceSafe(R.string.home_open_for_trade_value, it),
                            style = ty.labelMedium, color = mc.goldMtg,
                        )
                    }
                }
                if (preview.cards.isEmpty()) {
                    ClickableBox(onClick = { onAction(HomeAction.OpenTrades) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                            HubBadge(Icons.Default.Style, mc.primaryAccent)
                            Text(stringResourceSafe(R.string.home_open_for_trade_empty), style = ty.labelSmall, color = mc.textSecondary)
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        items(preview.cards, key = { "open_trade|${it.id}" }) { card ->
                            val uniqueKey = "open_trade|${card.id}"
                            DiscoverCardThumb(
                                card = card,
                                onClick = { onAction(HomeAction.OpenCardDetail(card.scryfallId, uniqueKey)) },
                                sharedTransitionKey = uniqueKey
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Shared widget building blocks
// ═══════════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────────
//  Shared widget building blocks
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatKPI(
    value: String,
    label: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    
    ClickableBox(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                color = color.copy(alpha = 0.12f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                }
            }
            Column {
                Text(
                    text = value,
                    style = ty.displayMedium.copy(fontSize = StatKpiValueSize),
                    color = mc.textPrimary
                )
                Text(
                    text = label.uppercase(),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

/**
 * A full-width clickable column used by hub slides so the whole slide area is a
 * single ≥48dp touch target. Content is laid out vertically with small spacing.
 */
@Composable
private fun ClickableBox(onClick: () -> Unit, content: @Composable ColumnScopeMarker.() -> Unit) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(CardShape)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        ColumnScopeMarker.content()
    }
}

/** Small rounded icon badge used to lead a hub slide. */
@Composable
private fun HubBadge(icon: ImageVector, tint: Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Small WUBRG color-identity dots for a deck. */
@Composable
private fun ColorIdentityDots(colors: Set<String>) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    if (colors.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        listOf("W", "U", "B", "R", "G").filter { it in colors }.forEach { code ->
            val color = when (code) {
                "W" -> mc.manaW
                "U" -> mc.manaU
                "B" -> mc.manaB
                "R" -> mc.manaR
                else -> mc.manaG
            }
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}


@Composable
private fun ReducedTradeProposalRow(
    proposal: TradeProposal,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val statusTint = when (proposal.status) {
        TradeStatus.COMPLETED -> mc.lifePositive
        TradeStatus.ACCEPTED  -> mc.primaryAccent
        TradeStatus.CANCELLED,
        TradeStatus.REVOKED   -> mc.lifeNegative
        TradeStatus.DECLINED  -> mc.goldMtg
        TradeStatus.COUNTERED -> mc.secondaryAccent
        else                  -> mc.textSecondary
    }

    Surface(
        shape = CardShape,
        color = mc.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = null,
                tint = statusTint,
                modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = proposal.status.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = ty.labelSmall,
                    color = statusTint
                )
                Text(
                    text = stringResourceSafe(R.string.home_trade_inbox_preview, proposal.items.size),
                    style = ty.bodySmall,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = TimeAgoFormatter.format(proposal.updatedAt),
                style = ty.labelSmall,
                color = mc.textDisabled
            )
        }
    }
}

@Composable
private fun FriendAvatarRow(friends: List<Friend>) {
    val spacing = MaterialTheme.spacing
    val mc = MaterialTheme.magicColors
    Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
        friends.take(4).forEach { friend ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(mc.background)
                    .padding(1.dp)
            ) {
                AvatarImage(
                    avatarUrl = friend.avatarUrl,
                    initials = friend.nickname.take(1).uppercase(),
                    size = 30
                )
            }
        }
        if (friends.size > 4) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(mc.background)
                    .padding(1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(mc.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+${friends.size - 4}",
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        color = mc.primaryAccent,
        shape = ButtonShape,
        modifier = modifier.heightIn(min = 48.dp).clip(ButtonShape).clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterHorizontally),
        ) {
            Icon(icon, contentDescription = null, tint = mc.background, modifier = Modifier.size(18.dp))
            Text(label, style = ty.labelMedium, color = mc.background, maxLines = 1)
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Display helpers (pure)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
@ReadOnlyComposable
private fun heroSectionLabel(hero: HomeHeroState): String = when (hero) {
    is HomeHeroState.ActiveGame -> stringResourceSafe(R.string.home_hero_label_active_session)
    is HomeHeroState.ActiveDraft -> stringResourceSafe(R.string.home_hero_label_draft_in_progress)
    is HomeHeroState.Summary -> stringResourceSafe(R.string.home_hero_label_welcome_back)
    // Welcome is handled by FirstStepsCarousel/FirstStepsCompletedCard; kept for exhaustiveness.
    is HomeHeroState.Welcome -> stringResourceSafe(R.string.home_hero_label_get_started)
    // QuestsReady is rendered by QuestsReadyHero before this is called; kept for exhaustiveness.
    is HomeHeroState.QuestsReady -> stringResourceSafe(R.string.home_hero_quests_ready_label)
    HomeHeroState.Loading -> stringResourceSafe(R.string.home_hero_label_loading)
}

@Composable
@ReadOnlyComposable
private fun heroWidgetCopy(hero: HomeHeroState): Triple<String, String, String> = when (hero) {
    is HomeHeroState.ActiveGame -> Triple(
        stringResourceSafe(R.string.home_hero_active_game_title),
        stringResourceSafe(R.string.home_hero_active_game_subtitle, hero.mode, hero.playerCount),
        stringResourceSafe(R.string.home_hero_active_game_cta),
    )
    is HomeHeroState.ActiveDraft -> Triple(
        stringResourceSafe(R.string.home_hero_active_draft_title),
        stringResourceSafe(R.string.home_hero_active_draft_subtitle, hero.setName),
        stringResourceSafe(R.string.home_hero_active_draft_cta),
    )
    is HomeHeroState.Summary -> Triple(
        stringResourceSafe(R.string.home_hero_summary_title, hero.playerName),
        if (hero.totalGames == 1) {
            stringResourceSafe(R.string.home_hero_summary_subtitle_one)
        } else {
            stringResourceSafe(R.string.home_hero_summary_subtitle_many, hero.totalGames)
        },
        stringResourceSafe(R.string.home_hero_summary_cta),
    )
    HomeHeroState.Loading -> Triple(
        stringResourceSafe(R.string.home_hero_loading_title),
        stringResourceSafe(R.string.home_hero_loading_subtitle),
        stringResourceSafe(R.string.home_hero_summary_cta),
    )
    // Welcome is rendered by FirstStepsCarousel/FirstStepsCompletedCard before this is called.
    is HomeHeroState.Welcome -> Triple(
        stringResourceSafe(R.string.home_hero_welcome_title),
        stringResourceSafe(R.string.home_hero_welcome_subtitle),
        stringResourceSafe(R.string.home_hero_summary_cta),
    )
    // QuestsReady is rendered by QuestsReadyHero before this is called; kept for exhaustiveness.
    is HomeHeroState.QuestsReady -> Triple(
        stringResourceSafe(R.string.home_hero_quests_ready_title, hero.count),
        stringResourceSafe(R.string.home_hero_quests_ready_subtitle),
        stringResourceSafe(R.string.home_hero_quests_ready_cta),
    )
}

private fun heroWidgetCta(hero: HomeHeroState): HomeAction = when (hero) {
    is HomeHeroState.ActiveGame -> HomeAction.StartGame
    is HomeHeroState.ActiveDraft -> HomeAction.DraftSimulator
    is HomeHeroState.Welcome -> HomeAction.StartGame // handled by FirstStepsCarousel; kept for exhaustiveness
    else -> HomeAction.StartGame
}

private fun formatDuration(ms: Long): String {
    val minutes = (ms / 60000L).toInt()
    return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
}

/** Maximum number of news items previewed inside the Home news widget row. */
private const val MAX_NEWS_PREVIEW = 5

/** Navigation icon for a Quick Start action. */
private val QuickStartAction.navIcon: ImageVector
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

/** Human label for a Quick Start action. */
private val QuickStartAction.navLabel: String
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        QuickStartAction.SCAN_CARD -> stringResourceSafe(R.string.quick_start_scan_card)
        QuickStartAction.CREATE_DECK -> stringResourceSafe(R.string.quick_start_deck_builder)
        QuickStartAction.DRAFT_GUIDE -> stringResourceSafe(R.string.quick_start_draft_guides)
        QuickStartAction.SEARCH_CARD -> stringResourceSafe(R.string.quick_start_search_card)
        QuickStartAction.DECKS -> stringResourceSafe(R.string.quick_start_my_decks)
        QuickStartAction.NEWS -> stringResourceSafe(R.string.quick_start_news)
        QuickStartAction.STATS -> stringResourceSafe(R.string.quick_start_my_stats)
        QuickStartAction.FRIENDS -> stringResourceSafe(R.string.quick_start_friends)
        QuickStartAction.TRADES -> stringResourceSafe(R.string.quick_start_trades)
        QuickStartAction.COMMUNITY_DECKS -> stringResourceSafe(R.string.quick_start_community)
        QuickStartAction.SETTINGS -> stringResourceSafe(R.string.quick_start_settings)
    }

/** Maps a Quick Start action to its navigation intent. */
private fun QuickStartAction.toHomeActionNav(): HomeAction = when (this) {
    QuickStartAction.SCAN_CARD -> HomeAction.ScanCard
    QuickStartAction.CREATE_DECK -> HomeAction.CreateDeck
    QuickStartAction.DRAFT_GUIDE -> HomeAction.DraftGuide
    QuickStartAction.SEARCH_CARD -> HomeAction.SearchCard
    QuickStartAction.DECKS -> HomeAction.OpenDecks
    QuickStartAction.NEWS -> HomeAction.OpenNews
    QuickStartAction.STATS -> HomeAction.OpenStats
    QuickStartAction.FRIENDS -> HomeAction.OpenFriends
    QuickStartAction.TRADES -> HomeAction.OpenTrades
    QuickStartAction.COMMUNITY_DECKS -> HomeAction.OpenCommunityDecks
    QuickStartAction.SETTINGS -> HomeAction.OpenSettings
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Container — registers root bounds for drag hit-testing in the gallery
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Renders a single placed widget. F-9 (Home feature overhaul Phase 3): the bounds-registry /
 * drag hit-testing API this used to expose (`onRegisterBounds` + the module-level
 * `findTargetIndex()`) was removed — the board itself is static; only the gallery sheet owns
 * add/remove/reorder, via its own local drag state (`WidgetGallerySheet.kt`).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeWidgetContainer(
    widget: WidgetInstance,
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    modifier: Modifier = Modifier,
    // Deck Doctor Community/Archetype plan, Phase 5.
    trending: com.mmg.manahub.core.model.TrendingSnapshot? = null,
    // Home widget board overhaul, TASK 5b.
    communityDecks: List<com.mmg.manahub.core.model.CommunityDeckSummary>? = null,
    communityDecksCategory: HomeCommunityDeckCategory = HomeCommunityDeckCategory.POPULAR,
) {
    Box(modifier = modifier) {
        HomeWidgetHost(
            widget = widget,
            uiState = uiState,
            onAction = onAction,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            trending = trending,
            communityDecks = communityDecks,
            communityDecksCategory = communityDecksCategory,
        )
    }
}

