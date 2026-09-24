package com.mmg.manahub.feature.today.presentation.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.model.news.FeedContentFilter
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicSegmentedControl
import com.mmg.manahub.core.ui.components.MagicToastState
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.NewsItemCard
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.news.presentation.components.ShimmerNewsItem
import com.mmg.manahub.feature.today.presentation.common.openInBrowser
import com.mmg.manahub.feature.today.presentation.common.openSourceSite
import com.mmg.manahub.feature.today.presentation.common.shareLink
import com.mmg.manahub.feature.today.presentation.feed.MenuItem
import org.jetbrains.compose.resources.painterResource
import org.koin.androidx.compose.koinViewModel

@Composable
fun SavedTab(
    toastState: MagicToastState,
    onVideoClick: (videoId: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = MaterialTheme.spacing
    val toolbarColor = MaterialTheme.magicColors.primaryAccent.toArgb()
    val linkFailed = stringResource(R.string.today_link_open_failed)
    val shareChooser = stringResource(R.string.today_share_chooser)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SavedEvent.Removed -> toastState.show(context.getString(R.string.today_unsaved_toast), MagicToastType.INFO)
                SavedEvent.ActionFailed -> toastState.show(context.getString(R.string.today_action_failed), MagicToastType.ERROR)
            }
        }
    }

    val openItem: (NewsItem) -> Unit = { item ->
        when (item) {
            is NewsItem.Video -> onVideoClick(item.videoId, item.title)
            is NewsItem.Article ->
                if (!openInBrowser(context, item.url, toolbarColor)) toastState.show(linkFailed, MagicToastType.ERROR)
        }
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val placeholder = painterResource(Res.drawable.mtg_card_back)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = spacing.md, bottom = spacing.lg + bottomInset),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
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
        when {
            state.isLoading -> items(SHIMMER_COUNT, key = { "shimmer_$it" }) {
                ShimmerNewsItem(modifier = Modifier.padding(horizontal = spacing.lg))
            }
            !state.hasAnySaved -> item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.today_saved_empty_title),
                    subtitle = stringResource(R.string.today_saved_empty_subtitle),
                    icon = Icons.Default.BookmarkBorder,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.items.isEmpty() -> item(key = "no_results") {
                EmptyState(
                    title = stringResource(R.string.today_feed_no_results_title),
                    subtitle = stringResource(R.string.today_feed_no_results_subtitle),
                    icon = Icons.Default.BookmarkBorder,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> items(state.items, key = { it.id }) { item ->
                val siteUrl = state.sourceSiteUrls[item.sourceId]
                NewsItemCard(
                    item = item,
                    onClick = { openItem(item) },
                    placeholderPainter = placeholder,
                    isSaved = true,
                    onToggleSave = { viewModel.remove(item) },
                    overflowMenu = { dismiss ->
                        if (siteUrl != null) {
                            MenuItem(stringResource(R.string.today_item_open_source)) {
                                dismiss()
                                if (openSourceSite(context, siteUrl, toolbarColor)) viewModel.onSourceSiteOpened()
                                else toastState.show(linkFailed, MagicToastType.ERROR)
                            }
                        }
                        MenuItem(stringResource(R.string.action_share)) {
                            dismiss()
                            if (!shareLink(context, item.title, item.url, shareChooser)) {
                                toastState.show(linkFailed, MagicToastType.ERROR)
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = spacing.lg)
                        .animateItem(),
                )
            }
        }
    }
}

private const val SHIMMER_COUNT = 3
