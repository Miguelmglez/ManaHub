package com.mmg.manahub.feature.home.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.QuickStartAction
import com.mmg.manahub.core.model.news.NewsItem
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 */
@Composable
private fun WidgetSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
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
            modifier = Modifier.weight(1f)
        )
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

// ─────────────────────────────────────────────────────────────────────────────
//  Host — dispatch on widget type (exhaustive over the 13 HomeWidgetTypes)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeWidgetHost(
    widget: WidgetInstance,
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    // Gamification widgets render nothing on the dashboard when the master toggle is off — they stay
    // in the persisted layout (so they reappear if re-enabled) but are not shown.
    if (widget.type.isGamification && !uiState.gamificationEnabled) return

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        if (widget.type != HomeWidgetType.CONTEXT_HERO) {
            WidgetSectionHeader(
                title = stringResourceSafe(widget.type.defaultTitleRes),
                icon = widget.type.icon,
                trailingContent = widgetHeaderTrailingContent(widget.type, uiState, onAction),
            )
        }
        
        when (widget.type) {
            HomeWidgetType.CONTEXT_HERO -> ContextHeroWidget(uiState.hero, onAction)
            HomeWidgetType.QUICK_ACTIONS -> QuickActionsWidget(uiState.quickStartActions, onAction)
            HomeWidgetType.PROGRESSION_HUB -> ProgressionHubWidget(uiState.gamification, onAction)
            HomeWidgetType.QUESTS_HUB -> QuestsHubWidget(uiState.gamification, onAction)
            HomeWidgetType.GAME_STATS_HUB -> GameStatsHubWidget(uiState, onAction)
            HomeWidgetType.COLLECTION_STATS_HUB -> CollectionStatsHubWidget(uiState, onAction)
            HomeWidgetType.YOUR_DECKS_SHELF -> DecksShelfWidget(uiState.decks, onAction)
            HomeWidgetType.WISHLIST_PROGRESS -> WishlistWidget(uiState.wishlistStats, uiState.isAuthenticated, onAction)
            HomeWidgetType.DISCOVER_CARDS -> DiscoverCardsWidget(uiState.discoverCards, uiState.discoverLoadState, onAction)
            HomeWidgetType.CARD_OF_THE_DAY -> RandomCardWidget(uiState.cardOfTheDay, uiState.randomCardLoadState, onAction)
            HomeWidgetType.LATEST_SETS -> LatestSetsWidget(uiState.latestSets, onAction)
            HomeWidgetType.MTG_NEWS -> NewsWidget(uiState.recentNews, uiState.newsFiltersActive, onAction)
            HomeWidgetType.RULES_TIP -> RulesTipWidget()
            HomeWidgetType.SOCIAL_HUB -> SocialHubWidget(uiState, onAction)
            HomeWidgetType.TRADES_HUB -> TradesHubWidget(uiState, onAction)
        }
    }
}

/**
 * Resolves the trailing affordance shown in a widget's section header, or null when the widget
 * has none. Each branch is self-contained; the DISCOVER_CARDS branch hosts its own local
 * set-picker toggle state and the reusable [SetPickerSheet].
 */
private fun widgetHeaderTrailingContent(
    type: HomeWidgetType,
    uiState: HomeUiState,
    onAction: (HomeAction) -> Unit,
): (@Composable () -> Unit)? = when (type) {
    HomeWidgetType.QUICK_ACTIONS -> {
        {
            WidgetHeaderIconButton(
                icon = Icons.Default.Edit,
                contentDescription = "Customize shortcuts",
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
    // Delegate to the carousel / completion card when the hero is the Welcome state.
    if (hero is HomeHeroState.Welcome) {
        if (hero.steps.isEmpty()) {
            // FirstStepsCompletedCard is hidden per user request
            return
        } else {
            FirstStepsCarousel(
                steps = hero.steps,
                onAction = onAction,
                onSkip = { stepId -> onAction(HomeAction.SkipFirstStep(stepId)) },
            )
        }
        return
    }

    if (hero is HomeHeroState.Summary) {
        // Welcome back widget is hidden per user request
        return
    }

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
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )

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
 * Each page shows an icon badge, a slide counter, a title, a subtitle, a full-width
 * CTA button, and a "Skip" affordance. The pager auto-advances every 3 seconds while
 * the user is not actively swiping ([androidx.compose.foundation.pager.PagerState.isScrollInProgress]
 * pauses the timer automatically). Progress dots track the current page.
 *
 * Stateless — driven entirely by the pager state and the two callback lambdas.
 *
 * @param steps    Non-empty list of visible steps to display.
 * @param onAction Called when the user taps the CTA; receives the step's [HomeAction].
 * @param onSkip   Called when the user taps "Skip"; receives the step's id.
 */
@Composable
internal fun FirstStepsCarousel(
    steps: List<FirstStepItem>,
    onAction: (HomeAction) -> Unit,
    onSkip: (String) -> Unit,
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
                        .clickable {
                            onSkip(step.id)
                            onAction(step.action)
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
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
                        contentDescription = null,
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
                        contentDescription = null,
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
                    text = stringResourceSafe(quest.titleRes),
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
                    contentDescription = null,
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
                    contentDescription = null,
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
}

@Composable
private fun GameStatsHubWidget(uiState: HomeUiState, onAction: (HomeAction) -> Unit) {
    val slides = remember(uiState.winRate, uiState.bestDeck, uiState.nemesis, uiState.performanceDetails, uiState.playStreak, uiState.lastGameRecap) {
        buildList {
            uiState.winRate?.let { add(GameStatsSlide.WinRate(it)) }
            uiState.bestDeck?.let { add(GameStatsSlide.BestDeck(it)) }
            uiState.nemesis?.let { add(GameStatsSlide.Nemesis(it)) }
            uiState.performanceDetails
                ?.takeIf { it.avgWinTurn != null || it.avgLifeOnWin != null }
                ?.let { add(GameStatsSlide.Performance(it)) }
            uiState.playStreak?.let { add(GameStatsSlide.Streak(it, uiState.winRate?.totalGames ?: 0)) }
            uiState.lastGameRecap?.let { add(GameStatsSlide.LastGame(it)) }
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
                            text = "WIN RATE",
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
                            val glowScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.4f,
                                animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Reverse),
                                label = "glow"
                            )
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
                                text = "BEST PERFORMER",
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
                                text = "CURRENT NEMESIS",
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
                                text = "LATEST BATTLE",
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
                            Text("${recap.mode} · ${formatDuration(recap.durationMs)}", style = ty.labelSmall, color = mc.textSecondary)
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
    
    LaunchedEffect(percentage) {
        progress.animateTo(
            targetValue = percentage / 100f,
            animationSpec = tween(1200, easing = FastOutSlowInEasing)
        )
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
                style = ty.labelSmall.copy(fontSize = 9.sp),
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
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "pulse"
    )
    
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
                style = ty.labelSmall.copy(fontSize = 9.sp),
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
private fun DecksShelfWidget(decks: List<DeckSummary>, onAction: (HomeAction) -> Unit) {
    val spacing = MaterialTheme.spacing
    WidgetShell(onClick = { onAction(HomeAction.OpenDecks) }) {
        if (decks.isEmpty()) {
            WidgetEmptyBody(stringResourceSafe(R.string.home_decks_empty))
            return@WidgetShell
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            items(decks, key = { it.id }) { deck ->
                DeckItem(
                    deck = deck,
                    onClick = { onAction(HomeAction.OpenDeck(deck.id)) },
                    reduced = true,
                    modifier = Modifier.width(160.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  WISHLIST_PROGRESS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun WishlistWidget(stats: WishlistStats?, isAuthenticated: Boolean, onAction: (HomeAction) -> Unit) {
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
        
        if (stats.cards.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                items(stats.cards.toList(), key = { it.id }) { card ->
                    DiscoverCardThumb(card, onClick = { onAction(HomeAction.OpenCardDetail(card.scryfallId)) })
                }
            }
        } else {
            StatKPI(
                value = stats.count.toString(),
                label = stringResourceSafe(R.string.widget_title_wishlist),
                color = mc.goldMtg,
                icon = Icons.Default.Star,
                onClick = { onAction(HomeAction.OpenWishlist) }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  DISCOVER_CARDS
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DiscoverCardsWidget(
    cards: List<DiscoverCard>,
    loadState: DiscoverLoadState,
    onAction: (HomeAction) -> Unit,
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
                    items(cards, key = { it.id }) { card ->
                        DiscoverCardThumb(card, onClick = { onAction(HomeAction.OpenCardDetail(card.scryfallId)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverCardThumb(card: DiscoverCard, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = Modifier
            .width(110.dp)
            // Full MTG card aspect ratio (745:1040) so the whole card is shown.
            .aspectRatio(0.717f)
            .clip(CardShape)
            .background(mc.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (card.imageUrl != null) {
            AsyncImage(
                model = card.imageUrl,
                contentDescription = card.name,
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

@Composable
private fun RandomCardWidget(
    card: DiscoverCard?,
    loadState: DiscoverLoadState,
    onAction: (HomeAction) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    WidgetShell(onClick = { card?.let { onAction(HomeAction.OpenCardDetail(it.scryfallId)) } }) {
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
                    .background(mc.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (card.imageUrl != null) {
                    AsyncImage(
                        model = card.imageUrl,
                        contentDescription = card.name,
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
            .widthIn(min = 72.dp)
            .heightIn(min = 88.dp)
            .clip(ChipShape)
            .background(mc.primaryAccent.copy(alpha = 0.10f))
            .border(BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.25f)), ChipShape)
            .clickable(onClickLabel = stringResourceSafe(R.string.home_news_see_all), role = Role.Button, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(mc.primaryAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = stringResourceSafe(R.string.home_news_see_all),
            style = ty.labelSmall,
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

    // Today's tip index (changes once per day), used as the pager's starting page.
    val tipIndex = remember { (System.currentTimeMillis() / 86_400_000L % MTG_TIPS.size).toInt() }
    val pagerState = rememberPagerState(initialPage = tipIndex, pageCount = { MTG_TIPS.size })

    WidgetShell {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val (title, body) = MTG_TIPS[page]
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
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OracleText(
                            text = body,
                            style = ty.bodySmall.copy(
                                color = mc.textSecondary,
                                lineHeight = 17.sp
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  SOCIAL_HUB (account-gated)
// ═══════════════════════════════════════════════════════════════════════════════

private sealed interface SocialSlide {
    data class FriendsActivity(val text: String) : SocialSlide
    data class TopCommanders(val entries: List<com.mmg.manahub.core.model.CommunityEntry>) : SocialSlide
    data class MostWishlisted(val entries: List<com.mmg.manahub.core.model.CommunityEntry>) : SocialSlide
    data class ActiveTournament(val summary: TournamentSummary) : SocialSlide
    data class Milestones(val milestones: List<com.mmg.manahub.core.model.CommunityMilestone>) : SocialSlide
}

@Composable
private fun SocialHubWidget(uiState: HomeUiState, onAction: (HomeAction) -> Unit) {
    if (!uiState.isAuthenticated) {
        AccountGatedPlaceholder(stringResourceSafe(R.string.widget_title_social_hub)) { onAction(HomeAction.CreateAccount) }
        return
    }
    val community = uiState.communityStats
    val slides = remember(community, uiState.activeTournamentSummary) {
        buildList {
            add(SocialSlide.FriendsActivity(""))
            community?.topCommanders?.takeIf { it.isNotEmpty() }?.let { add(SocialSlide.TopCommanders(it)) }
            community?.mostWishlisted?.takeIf { it.isNotEmpty() }?.let { add(SocialSlide.MostWishlisted(it)) }
            uiState.activeTournamentSummary?.let { add(SocialSlide.ActiveTournament(it)) }
            community?.milestones?.takeIf { it.isNotEmpty() }?.let { add(SocialSlide.Milestones(it)) }
        }
    }
    WidgetShell {
        AutoSlideHub(
            slides = slides,
            slideContent = { slide -> SocialSlideContent(slide, onAction) },
            showDots = false,
        )
    }
}

@Composable
private fun SocialSlideContent(slide: SocialSlide, onAction: (HomeAction) -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    when (slide) {
        is SocialSlide.FriendsActivity -> HubSlide {
            ClickableBox(onClick = { onAction(HomeAction.OpenFriends) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    HubBadge(Icons.Default.Group, mc.primaryAccent)
                    Column {
                        Text(stringResourceSafe(R.string.home_social_friends_title), style = ty.titleMedium, color = mc.textPrimary)
                        Text(stringResourceSafe(R.string.home_friends_empty), style = ty.labelSmall, color = mc.textSecondary)
                    }
                }
            }
        }
        is SocialSlide.TopCommanders -> HubSlide {
            ClickableBox(onClick = { onAction(HomeAction.OpenStats) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    HubBadge(Icons.Default.Star, mc.goldMtg)
                    Column {
                        Text(stringResourceSafe(R.string.widget_title_community_commanders), style = ty.titleMedium, color = mc.textPrimary)
                        slide.entries.take(1).forEach { entry ->
                            Text("${entry.name} · ${entry.percentage.toInt()}%", style = ty.labelSmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        is SocialSlide.MostWishlisted -> HubSlide {
            ClickableBox(onClick = { onAction(HomeAction.OpenStats) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    HubBadge(Icons.AutoMirrored.Filled.TrendingUp, mc.primaryAccent)
                    Column {
                        Text(stringResourceSafe(R.string.widget_title_most_wishlisted), style = ty.titleMedium, color = mc.textPrimary)
                        slide.entries.take(1).forEach { entry ->
                            Text("${entry.name} · ${entry.count} pilots", style = ty.labelSmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        is SocialSlide.ActiveTournament -> HubSlide {
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
        is SocialSlide.Milestones -> HubSlide {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                HubBadge(Icons.Default.AutoAwesome, mc.primaryAccent)
                Column {
                    Text(stringResourceSafe(R.string.widget_title_community_milestones), style = ty.titleMedium, color = mc.textPrimary)
                    slide.milestones.take(1).forEach { m ->
                        Text("${m.value} · ${m.label}", style = ty.labelSmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  TRADES_HUB (account-gated)
// ═══════════════════════════════════════════════════════════════════════════════

private sealed interface TradesSlide {
    data class Inbox(val summary: TradeSummary?) : TradesSlide
    object Suggestions : TradesSlide
    object OpenForTrade : TradesSlide
}

@Composable
private fun TradesHubWidget(uiState: HomeUiState, onAction: (HomeAction) -> Unit) {
    if (!uiState.isAuthenticated) {
        AccountGatedPlaceholder(stringResourceSafe(R.string.widget_title_trades_hub)) { onAction(HomeAction.CreateAccount) }
        return
    }
    val slides = remember(uiState.tradeSummary) {
        listOf(
            TradesSlide.Inbox(uiState.tradeSummary),
            TradesSlide.Suggestions,
            TradesSlide.OpenForTrade,
        )
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
private fun TradesSlideContent(slide: TradesSlide, onAction: (HomeAction) -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    when (slide) {
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
                        }
                    }
                }
            }
        }
        is TradesSlide.Suggestions -> HubSlide {
            ClickableBox(onClick = { onAction(HomeAction.OpenTrades) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    HubBadge(Icons.Default.AutoAwesome, mc.primaryAccent)
                    Column {
                        Text(stringResourceSafe(R.string.widget_title_trade_suggestions), style = ty.titleMedium, color = mc.textPrimary)
                        Text(stringResourceSafe(R.string.home_trade_suggestions_empty), style = ty.labelSmall, color = mc.textSecondary)
                    }
                }
            }
        }
        is TradesSlide.OpenForTrade -> HubSlide {
            ClickableBox(onClick = { onAction(HomeAction.OpenTrades) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    HubBadge(Icons.Default.Style, mc.primaryAccent)
                    Column {
                        Text(stringResourceSafe(R.string.widget_title_open_for_trade), style = ty.titleMedium, color = mc.textPrimary)
                        Text(stringResourceSafe(R.string.home_open_for_trade_empty), style = ty.labelSmall, color = mc.textSecondary)
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
                    style = ty.displayMedium.copy(fontSize = 32.sp),
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

/** Win/loss result dot (green/red circle). */
@Composable
private fun ResultDot(won: Boolean) {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(if (won) mc.lifePositive else mc.lifeNegative),
    )
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

private fun heroSectionLabel(hero: HomeHeroState): String = when (hero) {
    is HomeHeroState.ActiveGame -> "ACTIVE SESSION"
    is HomeHeroState.ActiveDraft -> "DRAFT IN PROGRESS"
    is HomeHeroState.Summary -> "WELCOME BACK"
    is HomeHeroState.Welcome -> "GET STARTED" // handled by FirstStepsCarousel; kept for exhaustiveness
    // QuestsReady is rendered by QuestsReadyHero before this is called; kept for exhaustiveness.
    is HomeHeroState.QuestsReady -> "REWARDS READY"
    HomeHeroState.Loading -> "LOADING"
}

private fun heroWidgetCopy(hero: HomeHeroState): Triple<String, String, String> = when (hero) {
    is HomeHeroState.ActiveGame -> Triple("Game in progress", "${hero.mode} · ${hero.playerCount} players", "Resume game")
    is HomeHeroState.ActiveDraft -> Triple("Draft in progress", "Set ${hero.setName}", "Resume draft")
    is HomeHeroState.Summary -> Triple(
        "Welcome back, ${hero.playerName}",
        if (hero.totalGames == 1) "1 game tracked" else "${hero.totalGames} games tracked",
        "Start a game",
    )
    HomeHeroState.Loading -> Triple("Loading…", "Getting your dashboard ready", "Start a game")
    // Welcome is rendered by FirstStepsCarousel/FirstStepsCompletedCard before this is called.
    is HomeHeroState.Welcome -> Triple("Welcome to ManaHub", "Track games, scan cards, and build decks.", "Start a game")
    // QuestsReady is rendered by QuestsReadyHero before this is called; kept for exhaustiveness.
    is HomeHeroState.QuestsReady -> Triple("Rewards ready", "Claim your completed quests.", "Claim rewards")
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
    get() = when (this) {
        QuickStartAction.SCAN_CARD -> "Scan Card"
        QuickStartAction.CREATE_DECK -> "Deck Builder"
        QuickStartAction.DRAFT_GUIDE -> "Draft Guides"
        QuickStartAction.SEARCH_CARD -> "Search Card"
        QuickStartAction.DECKS -> "My Decks"
        QuickStartAction.NEWS -> "News"
        QuickStartAction.STATS -> "My Stats"
        QuickStartAction.FRIENDS -> "Friends"
        QuickStartAction.TRADES -> "Trades"
        QuickStartAction.COMMUNITY_DECKS -> "Community"
        QuickStartAction.SETTINGS -> "Settings"
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

@Composable
fun HomeWidgetContainer(
    widget: WidgetInstance,
    uiState: HomeUiState,
    onRegisterBounds: (String, androidx.compose.ui.geometry.Rect) -> Unit,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .onGloballyPositioned { coords -> onRegisterBounds(widget.type.persistedId, coords.boundsInRoot()) },
    ) {
        HomeWidgetHost(widget, uiState, onAction)
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Drag hit-testing helper (used by the gallery reorder section)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Hit-tests the ghost's center against registered widget bounds to find the index
 * of the widget currently under the dragged ghost. Returns -1 when over nothing.
 */
fun findTargetIndex(
    ghostCenter: Offset,
    bounds: Map<String, androidx.compose.ui.geometry.Rect>,
    layout: List<WidgetInstance>,
): Int {
    layout.forEachIndexed { index, widget ->
        val rect = bounds[widget.type.persistedId] ?: return@forEachIndexed
        if (rect.contains(ghostCenter)) return index
    }
    return -1
}

// ═══════════════════════════════════════════════════════════════════════════════
//  MTG rules tips — (title, body) pairs surfaced by RULES_TIP, one per day
// ═══════════════════════════════════════════════════════════════════════════════

private val MTG_TIPS = listOf(
    "Stack Resolution" to "Spells and abilities resolve last-in, first-out. The last spell cast is the first to resolve.",
    "State-Based Actions" to "The game checks for creatures with 0 toughness, lethal damage, and legend rule violations continuously — without using the stack.",
    "Deathtouch & Trample" to "A creature with deathtouch only needs to deal 1 damage to a blocking creature with trample. The rest tramples over.",
    "Hexproof vs Shroud" to "Hexproof protects from opponents' targeting only. Shroud protects from ALL targeting, including your own.",
    "Legend Rule" to "If you control two permanents with the same legendary name, you choose one and put the other in the graveyard.",
    "Flash" to "Permanents with Flash can be played at instant speed — during opponent's turn, in response to spells, even during combat.",
    "Vigilance" to "Attacking doesn't cause vigilant creatures to tap — they can still block next turn.",
    "Haste" to "Creatures need to be on the battlefield since the beginning of your turn to attack or use tap abilities — unless they have Haste.",
    "Flying & Reach" to "Flying creatures can only be blocked by creatures with Flying or Reach. Don't forget your Reach creatures can block fliers!",
    "Lifelink Timing" to "Lifelink causes life gain simultaneously with damage dealing — not after combat. This matters for effects that trigger on life gain.",
    "First Strike & Deathtouch" to "First strike + deathtouch is a devastating combo: the creature deals lethal damage in first-strike step, and the other creature dies before dealing its damage.",
    "Double Strike" to "Double strike creatures deal first-strike damage AND regular combat damage. Combined with pump spells, this doubles the bonus.",
    "Menace" to "A creature with Menace requires TWO or more blockers — great for forcing through damage late game.",
    "Indestructible Limits" to "Indestructible doesn't mean invincible! It can still be exiled, bounced, sacrificed, or have its toughness reduced to 0.",
    "Protection" to "Protection (from a quality) means the creature can't be Blocked, Damaged, Enchanted/Equipped, or Targeted by sources with that quality — BDET.",
    "Phasing" to "Phased-out permanents are treated as if they don't exist. Auras and Equipment attached to them phase out too, and phase back in together.",
    "Cascade" to "Cascade triggers when you cast the spell. You reveal cards until you find one with lesser mana value, then cast it for free — at instant speed if it's an instant.",
    "Morph" to "Face-down morphed creatures are 2/2 colorless creatures with no name, type, or abilities. Opponents must allow you to turn them face-up for the morph cost.",
    "Storm" to "Storm copies the spell for each spell cast before it this turn. Rituals and cantrips earlier in the turn all count!",
    "Flashback" to "Flashback lets you cast a spell from the graveyard by paying its flashback cost. The spell is then exiled, not returned to the graveyard.",
    "Dredge" to "Dredge is a replacement effect — when you would draw a card, you may instead mill yourself equal to the dredge value and return the dredge card to hand.",
    "Delve" to "You can exile any number of cards from your graveyard when paying for Delve — each exiled card pays for {1} of generic mana.",
    "Convoke" to "Tapping a creature for Convoke can pay for one generic mana or one mana of the creature's color. All tap abilities are mana abilities.",
    "Suspend" to "Suspending is casting without paying the mana cost. The spell sits in exile with time counters, one removed each upkeep, then cast for free when the last is removed.",
    "Escape" to "Escape lets you recast cards from the graveyard by paying the escape cost and exiling a set number of other graveyard cards.",
    "Foretell" to "Cards can be foretold face-down in exile for {2} on any turn. The reduced foretell cost can be paid on later turns at sorcery speed (or instant speed if it's an instant).",
    "Ward" to "Ward is a triggered ability — it triggers when an opponent targets the permanent. They must pay the ward cost or the spell/ability is countered.",
    "Boast" to "Boast abilities can only be activated once per turn, and only if the creature attacked this turn. They're not tap abilities.",
    "Sagas" to "Sagas trigger on your precombat main phase. Chapter I triggers when it enters, subsequent chapters on later turns. The saga sacrifices itself after the final chapter.",
    "MDFCs" to "Modal Double-Faced Cards can be played as either face. The back face can only be played as a land (or whatever its type is), not cast normally from hand.",
    "Mana Burn" to "Mana burn was removed in Magic 2010. Unused mana in your mana pool at end of step or phase simply disappears — it doesn't cause damage.",
    "Colorless vs Generic" to "Generic mana ({1}, {2}) can be paid by any mana. Colorless mana ({C}) can ONLY be paid by colorless sources — they're different!",
    "Planeswalker Combat" to "Attackers can target planeswalkers directly. Any damage dealt removes that many loyalty counters. The planeswalker is removed if it reaches 0.",
    "Loyalty Abilities" to "You can activate only one loyalty ability per planeswalker per turn, only at sorcery speed, only when you have priority.",
    "Token Rules" to "Tokens cease to exist when they leave the battlefield. A token 'returning to hand' just disappears — it never actually reaches the hand.",
    "Copy Spells" to "Copying a spell doesn't count as casting it. Effects that trigger 'when you cast' won't trigger from a copy.",
    "Split Second" to "While a spell with Split Second is on the stack, players can't cast spells or activate non-mana abilities. Triggered abilities still trigger!",
    "Priority" to "Active player gets priority first in each step and phase, then each other player in turn order. A player must pass priority before the stack can resolve.",
    "Mana Abilities" to "Mana abilities (producing mana) don't use the stack and can't be responded to. They resolve immediately when activated.",
    "Regenerate" to "Regeneration is a replacement effect — if a creature would be destroyed, instead tap it, remove all damage, and remove from combat. It's a shield, not actual death.",
    "Tapped Creatures" to "Tapped creatures cannot block, but they can still attack next turn if untapped during the untap step. Tapping a blocker mid-combat after blocks are declared doesn't remove the block.",
    "Blocking Rules" to "Once a block is declared, the blocked creature remains 'blocked' even if all blockers are removed. A 0/1 can still block a 10/10.",
    "Trample Assignment" to "When a trampling creature attacks, the attacker must assign lethal damage to each blocker before assigning excess to the defending player.",
    "Deathtouch Blocking" to "When multiple creatures block a deathtouch attacker, the attacker can assign 1 damage to each blocker (lethal) and still trample if it has trample.",
    "Commander Damage" to "In Commander format, 21 combat damage from the SAME commander to the same player is lethal — regardless of their life total.",
    "Commander Tax" to "Each time a commander is cast from the command zone, it costs {2} more. The tax stacks — a third cast costs {4} more than the base cost.",
    "Color Identity" to "A card's color identity includes all mana symbols in its mana cost AND its rules text. You can't include cards outside your commander's color identity.",
    "Tutors" to "Cards that 'search your library' are called tutors. Remember to shuffle your library after searching, even if you didn't find anything.",
    "Landfall" to "Landfall triggers when a land enters the battlefield under your control — including extra lands played, fetched lands, and bounced/replayed lands.",
    "Kicker" to "Kicker is an optional additional cost. You choose to pay it as you cast the spell — you can't pay it after casting. Some cards have multiple kicker costs.",
    "Morph vs Manifests" to "Morphs are cast face-down (you can turn them face-up anytime). Manifests are put into play face-down from effects — different rules apply for turning them over.",
    "Cycling" to "Cycling can be activated at instant speed. You discard the card first (mandatory), then draw. This can fill your graveyard for synergies.",
    "Split Cards" to "A split card in hand or on the stack has the combined characteristics of both halves. Its mana value is the sum of both sides.",
    "Adventures" to "Adventure cards can be cast as the Adventure (exile it), then cast the creature later from exile. While in exile, it's still a card you own.",
    "Investigate" to "Clue tokens are artifacts. You can sacrifice them and pay {2} to draw a card. Multiple clues mean multiple potential draws.",
    "Food Tokens" to "Food tokens are artifacts. Pay {2} and sacrifice to gain 3 life. They synergize with anything caring about artifacts or sacrifice effects.",
    "Treasure Tokens" to "Treasure tokens produce one mana of any color when tapped and sacrificed. They're temporary mana rocks that count as artifacts.",
    "Undergrowth" to "Undergrowth counts creature cards in your graveyard — not tokens (they cease to exist when they leave the battlefield).",
    "Afterlife" to "Afterlife X creates X 1/1 white and black flying Spirit tokens when the creature dies. Each token has flying.",
    "Amass" to "Amass creates a Zombie Army token if you don't have one, then puts +1/+1 counters on it. You can only have one Army at a time.",
    "Proliferate" to "Proliferate lets you choose any number of permanents and players with counters on them, then add one more counter of a kind already there. Great with planeswalkers!",
)
