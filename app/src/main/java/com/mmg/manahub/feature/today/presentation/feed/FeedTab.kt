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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
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
import com.mmg.manahub.core.ui.components.PullRefreshHeader
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(pullState.nestedScrollConnection),
        contentPadding = PaddingValues(top = spacing.md, bottom = spacing.lg + bottomInset),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (pullState.headerHeightDp > 0.dp) {
            item(key = "pull_header") {
                PullRefreshHeader(
                    height = pullState.headerHeightDp,
                    isRefreshing = state.isRefreshing,
                    dragFraction = pullState.dragFraction,
                    refreshingText = stringResource(R.string.today_refreshing),
                    pullIcon = Icons.Default.KeyboardArrowDown,
                    pullHintDescription = stringResource(R.string.today_pull_to_refresh),
                )
            }
        }

        item(key = "content_filter") {
            MagicSegmentedControl(
                options = listOf(
                    stringResource(R.string.today_filter_all),
                    stringResource(R.string.today_filter_articles),
                    stringResource(R.string.today_filter_videos),
                ),
                selectedIndex = state.contentFilter.ordinal,
                onOptionSelected = { viewModel.onContentFilterSelected(FeedContentFilter.entries[it]) },
                modifier = Modifier.padding(horizontal = spacing.lg),
            )
        }

        item(key = "sources_rail") {
            SourcesRail(
                sources = state.followedSources,
                selectedSourceId = state.selectedSource?.id,
                onSelectAll = { viewModel.selectSource(null) },
                onSelectSource = viewModel::onSourceChipClicked,
                onAddSource = onAddSource,
            )
        }

        state.selectedSource?.let { source ->
            item(key = "source_header_${source.id}") {
                SourceHeaderCard(
                    source = source,
                    onOpenSite = { openSite(source) },
                    onUnfollow = { viewModel.unfollow(source) },
                    modifier = Modifier.padding(horizontal = spacing.lg),
                )
            }
        }

        when {
            !state.sourcesLoaded || (state.isLoading && state.followedSources.isNotEmpty()) -> {
                items(SHIMMER_COUNT, key = { "shimmer_$it" }) {
                    ShimmerNewsItem(modifier = Modifier.padding(horizontal = spacing.lg))
                }
            }
            state.followedSources.isEmpty() -> item(key = "empty_follow") {
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
                    title = stringResource(R.string.today_feed_empty_title),
                    subtitle = stringResource(R.string.today_feed_empty_subtitle),
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
                item(key = "caught_up") {
                    CaughtUpFooter(
                        selectedSource = state.selectedSource,
                        onOpenSite = { state.selectedSource?.let(openSite) },
                    )
                }
            }
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
private fun SourcesRail(
    sources: List<ContentSource>,
    selectedSourceId: String?,
    onSelectAll: () -> Unit,
    onSelectSource: (String) -> Unit,
    onAddSource: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        contentPadding = PaddingValues(horizontal = spacing.lg),
    ) {
        item(key = "rail_all") {
            RailItem(
                label = stringResource(R.string.today_rail_all),
                selected = selectedSourceId == null,
                onClick = onSelectAll,
            ) { RailIcon(Icons.Default.DynamicFeed) }
        }
        items(sources, key = { it.id }) { source ->
            RailItem(
                label = source.name,
                selected = source.id == selectedSourceId,
                onClick = { onSelectSource(source.id) },
            ) { AvatarImage(avatarUrl = source.iconUrl, initials = sourceInitials(source.name), size = RAIL_AVATAR_SIZE) }
        }
        item(key = "rail_add") {
            val addLabel = stringResource(R.string.today_rail_add_a11y)
            RailItem(
                label = stringResource(R.string.today_rail_add),
                selected = false,
                onClick = onAddSource,
                modifier = Modifier.semantics { contentDescription = addLabel },
            ) { RailIcon(Icons.Default.Add) }
        }
    }
}

@Composable
private fun RailItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatar: @Composable () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier
            .width(RailItemWidth)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Box(
            modifier = Modifier
                .then(if (selected) Modifier.border(RailRingWidth, mc.primaryAccent, CircleShape) else Modifier)
                .padding(RailRingWidth),
        ) { avatar() }
        Text(
            text = label,
            style = MaterialTheme.magicTypography.labelSmall,
            color = if (selected) mc.primaryAccent else mc.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RailIcon(icon: ImageVector) {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = Modifier
            .size(RAIL_AVATAR_SIZE.dp)
            .background(mc.primaryAccent.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = mc.primaryAccent)
    }
}

@Composable
private fun SourceHeaderCard(
    source: ContentSource,
    onOpenSite: () -> Unit,
    onUnfollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        color = mc.surface,
        shape = CardShape,
        border = BorderStroke(1.dp, mc.surfaceVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                AvatarImage(avatarUrl = source.iconUrl, initials = sourceInitials(source.name), size = HEADER_AVATAR_SIZE)
                Column(modifier = Modifier.weight(1f)) {
                    Text(source.name, style = ty.titleMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(sourceKindAndLanguage(source), style = ty.bodySmall, color = mc.textSecondary)
                }
            }
            Text(
                text = stringResource(R.string.today_source_recent_only, source.name),
                style = ty.bodySmall,
                color = mc.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (source.siteUrl != null) {
                    MagicCtaButton(
                        onClick = onOpenSite,
                        text = stringResource(openSiteLabelRes(source.type)),
                        style = MagicCtaStyle.Outlined,
                        color = MagicCtaColor.Primary,
                        icon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                    )
                }
                MagicCtaButton(
                    onClick = onUnfollow,
                    text = stringResource(R.string.today_source_unfollow),
                    style = MagicCtaStyle.Ghost,
                    color = MagicCtaColor.Neutral,
                )
            }
        }
    }
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
