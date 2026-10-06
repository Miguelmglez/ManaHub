package com.mmg.manahub.feature.today.presentation.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.IconButton
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.FeedContentFilter
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.AvatarImage
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicSegmentedControl
import com.mmg.manahub.core.ui.components.MagicToastState
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.NewsItemCard
import com.mmg.manahub.core.ui.components.PullRefreshState
import com.mmg.manahub.core.ui.components.rememberPullRefreshState
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.news.presentation.components.ShimmerNewsItem
import com.mmg.manahub.feature.today.presentation.common.openInBrowser
import com.mmg.manahub.feature.today.presentation.common.openSiteLabelRes
import com.mmg.manahub.feature.today.presentation.common.openSourceSite
import com.mmg.manahub.feature.today.presentation.common.shareLink
import com.mmg.manahub.feature.today.presentation.common.sourceInitials
import com.mmg.manahub.feature.today.presentation.common.sourceKindAndLanguage
import org.jetbrains.compose.resources.painterResource

@Composable
fun FeedTab(
    viewModel: FeedViewModel,
    toastState: MagicToastState,
    onVideoClick: (videoId: String, title: String) -> Unit,
    onAddSource: () -> Unit,
    onDiscoverSources: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val toolbarColor = mc.primaryAccent.toArgb()
    val linkFailed = stringResource(R.string.today_link_open_failed)
    val shareChooser = stringResource(R.string.today_share_chooser)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is FeedEvent.PartialRefreshFailure -> toastState.show(
                    context.getString(R.string.news_refresh_partial_failure, event.failedCount),
                    MagicToastType.ERROR,
                )
                is FeedEvent.SavedChanged -> toastState.show(
                    context.getString(if (event.saved) R.string.today_saved_toast else R.string.today_unsaved_toast),
                    if (event.saved) MagicToastType.SUCCESS else MagicToastType.INFO,
                )
                is FeedEvent.Unfollowed -> toastState.show(
                    context.getString(R.string.today_unfollowed_toast, event.sourceName),
                    MagicToastType.INFO,
                )
                FeedEvent.ActionFailed -> toastState.show(context.getString(R.string.today_action_failed), MagicToastType.ERROR)
            }
        }
    }

    val openSite: (ContentSource) -> Unit = { source ->
        val opened = source.siteUrl?.let { openSourceSite(context, it, toolbarColor) } == true
        if (opened) viewModel.onSourceSiteOpened() else toastState.show(linkFailed, MagicToastType.ERROR)
    }
    val openItem: (NewsItem) -> Unit = { item ->
        when (item) {
            is NewsItem.Video -> onVideoClick(item.videoId, item.title)
            is NewsItem.Article ->
                if (!openInBrowser(context, item.url, toolbarColor)) toastState.show(linkFailed, MagicToastType.ERROR)
        }
    }

    val pullState = rememberPullRefreshState(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh(force = true) },
    )
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val placeholder = painterResource(Res.drawable.mtg_card_back)

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(pullState.nestedScrollConnection)
                .graphicsLayer { translationY = pullState.headerHeightPx.value },
            contentPadding = PaddingValues(top = spacing.md, bottom = spacing.lg + bottomInset),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {

        item(key = "feed_search") {
            val filterCount = (if (state.contentFilter != FeedContentFilter.ALL) 1 else 0) +
                state.selectedSourceIds.size + (if (state.savedOnly) 1 else 0)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedTextField(
                    value = state.searchQuery, onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = { Text(stringResource(if (state.savedOnly) R.string.today_saved_search_hint else R.string.today_search_hint), color = mc.textDisabled, style = MaterialTheme.magicTypography.bodyLarge) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = mc.textSecondary) },
                    trailingIcon = if (state.searchQuery.isNotEmpty()) {{
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Clear, stringResource(R.string.action_close), tint = mc.textSecondary)
                        }
                    }} else null,
                    singleLine = true, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = mc.textPrimary,
                        unfocusedTextColor = mc.textPrimary, focusedBorderColor = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant, cursorColor = mc.primaryAccent),
                )
                BadgedBox(badge = {
                    if (filterCount > 0) {
                        Badge(containerColor = mc.primaryAccent, contentColor = mc.background) {
                            Text("$filterCount")
                        }
                    }
                }) {
                    IconButton(
                        onClick = { viewModel.onFiltersOpened(); showFilters = true },
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                            .background(mc.primaryAccent.copy(alpha = if (filterCount > 0) 0.15f else 0.1f)),
                    ) {
                        Icon(Icons.Default.Tune, stringResource(R.string.today_filters), tint = mc.primaryAccent)
                    }
                }
            }
        }

        when {
            !state.sourcesLoaded || (state.isLoading && state.followedSources.isNotEmpty()) -> {
                items(SHIMMER_COUNT, key = { "shimmer_$it" }) {
                    ShimmerNewsItem(modifier = Modifier.padding(horizontal = spacing.lg))
                }
            }
            state.followedSources.isEmpty() && !state.savedOnly -> item(key = "empty_follow") {
                EmptyState(
                    title = stringResource(R.string.today_feed_empty_follow_title),
                    subtitle = stringResource(R.string.today_feed_empty_follow_subtitle),
                    icon = Icons.Default.RssFeed,
                    actionLabel = stringResource(R.string.today_feed_discover_sources),
                    onAction = onDiscoverSources,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.showTotalFailure -> item(key = "total_failure") {
                InlineErrorState(
                    message = stringResource(R.string.today_feed_error_total),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = { viewModel.refresh(force = true) },
                    enabled = !state.isRefreshing,
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }
            state.items.isEmpty() && state.isFiltered -> item(key = "no_results") {
                EmptyState(
                    title = stringResource(R.string.today_feed_no_results_title),
                    subtitle = stringResource(R.string.today_feed_no_results_subtitle),
                    icon = Icons.Default.SearchOff,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.items.isEmpty() -> item(key = "empty_feed") {
                EmptyState(
                    title = stringResource(if (state.savedOnly) R.string.today_saved_empty_title else R.string.today_feed_empty_title),
                    subtitle = stringResource(if (state.savedOnly) R.string.today_saved_empty_subtitle else R.string.today_feed_empty_subtitle),
                    icon = Icons.Default.DynamicFeed,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                items(state.items, key = { it.id }) { item ->
                    val source = state.followedSources.firstOrNull { it.id == item.sourceId }
                    NewsItemCard(
                        item = item,
                        onClick = { openItem(item) },
                        placeholderPainter = placeholder,
                        languageBadge = if (state.showLanguageBadge) state.sourceLanguages[item.sourceId] else null,
                        isSaved = item.id in state.savedIds,
                        onToggleSave = { viewModel.toggleSaved(item) },
                        overflowMenu = { dismiss ->
                            if (source?.siteUrl != null) {
                                MenuItem(stringResource(R.string.today_item_open_source)) {
                                    dismiss()
                                    openSite(source)
                                }
                            }
                            MenuItem(stringResource(R.string.action_share)) {
                                dismiss()
                                if (!shareLink(context, item.title, item.url, shareChooser)) {
                                    toastState.show(linkFailed, MagicToastType.ERROR)
                                }
                            }
                            if (source != null) {
                                MenuItem(stringResource(R.string.today_item_unfollow, source.name)) {
                                    dismiss()
                                    viewModel.unfollow(source)
                                }
                            }
                        },
                        modifier = Modifier
                            .padding(horizontal = spacing.lg)
                            .animateItem(),
                    )
                }
                if (!state.savedOnly) item(key = "caught_up") {
                    CaughtUpFooter(
                        selectedSource = state.selectedSource,
                        onOpenSite = { state.selectedSource?.let(openSite) },
                    )
                }
            }
        }
        }
        FeedRefreshOverlay(
            pullState = pullState,
            isRefreshing = state.isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
    if (showFilters) FeedFiltersSheet(state, viewModel::onContentFilterSelected, viewModel::selectSource,
        viewModel::onSavedOnlyChanged, onDismiss = { showFilters = false })

}

@Composable
private fun FeedRefreshOverlay(
    pullState: PullRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val height = pullState.headerHeightDp
    if (height > 0.dp) {
        FeedRefreshBanner(
            height = height,
            isRefreshing = isRefreshing,
            dragFraction = pullState.dragFraction,
            modifier = modifier,
        )
    }
}

@Composable
private fun FeedRefreshBanner(
    height: Dp,
    isRefreshing: Boolean,
    dragFraction: Float,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val infiniteTransition = rememberInfiniteTransition(label = "feed_refresh_spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "angle",
    )
    Box(
        modifier = modifier.fillMaxWidth().height(height).clipToBounds()
            .background(mc.primaryAccent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(18.dp).graphicsLayer {
                    rotationZ = if (isRefreshing) angle else dragFraction.coerceIn(0f, 1f) * 180f
                },
            )
            Text(
                text = stringResource(
                    if (isRefreshing || dragFraction >= 1f) R.string.today_refreshing
                    else R.string.today_pull_to_refresh,
                ),
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.primaryAccent,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun MenuItem(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.magicTypography.bodyMedium, color = MaterialTheme.magicColors.textPrimary) },
        onClick = onClick,
    )
}

@Composable
private fun CaughtUpFooter(selectedSource: ContentSource?, onOpenSite: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Text(
            text = stringResource(R.string.today_feed_caught_up),
            style = MaterialTheme.magicTypography.bodyMedium,
            color = mc.textSecondary,
        )
        if (selectedSource?.siteUrl != null) {
            MagicCtaButton(
                onClick = onOpenSite,
                text = stringResource(R.string.today_feed_open_source_for_older, selectedSource.name),
                style = MagicCtaStyle.Ghost,
                color = MagicCtaColor.Primary,
            )
        }
    }
}

private const val SHIMMER_COUNT = 4
private const val RAIL_AVATAR_SIZE = 48
private const val HEADER_AVATAR_SIZE = 40
private val RailItemWidth = 72.dp
private val RailRingWidth = 2.dp
