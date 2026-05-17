package com.mmg.manahub.feature.news.presentation

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.news.domain.model.NewsItem
import com.mmg.manahub.feature.news.domain.model.SourceType
import com.mmg.manahub.feature.news.presentation.components.ArticleCard
import com.mmg.manahub.feature.news.presentation.components.NewsFilterSheet
import com.mmg.manahub.feature.news.presentation.components.ShimmerNewsItem
import com.mmg.manahub.feature.news.presentation.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    onVideoClick: (videoId: String, title: String) -> Unit = { _, _ -> },
    viewModel: NewsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    val context = LocalContext.current

    var showFilterSheet by remember { mutableStateOf(false) }

    val allSourceTypes = setOf(SourceType.ARTICLE, SourceType.VIDEO)
    val allLanguages = setOf("en", "es")
    val hasActiveFilters = uiState.filterTypes != allSourceTypes
        || uiState.filterLanguages != allLanguages
        || uiState.filterSourceIds != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(mc.background)
            .statusBarsPadding(),
    ) {
        Text(
            text = stringResource(R.string.news_title),
            style = mt.titleLarge,
            color = mc.textPrimary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        // ── Search bar + filter icon ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = {
                    Text(
                        stringResource(R.string.news_search_hint),
                        style = mt.bodyMedium,
                        color = mc.textDisabled,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary)
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = mc.primaryAccent,
                    unfocusedBorderColor = mc.surfaceVariant,
                    cursorColor = mc.primaryAccent,
                    focusedTextColor = mc.textPrimary,
                    unfocusedTextColor = mc.textPrimary,
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f),
            )

            BadgedBox(
                badge = {
                    if (hasActiveFilters) {
                        Badge(containerColor = mc.primaryAccent)
                    }
                },
            ) {
                IconButton(onClick = { showFilterSheet = true }) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Advanced search filters",
                        tint = if (hasActiveFilters) mc.primaryAccent else mc.textSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Error inline ──────────────────────────────────────────────────────
        if (uiState.error != null) {
            InlineErrorState(
                message = uiState.error ?: "",
                retryLabel = stringResource(R.string.action_retry),
                onRetry = viewModel::refresh,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        // ── Content ───────────────────────────────────────────────────────────
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                uiState.isLoading -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(5) { ShimmerNewsItem() }
                    }
                }
                uiState.items.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.news_empty),
                            style = mt.bodyLarge,
                            color = mc.textDisabled,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp + navBarBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = uiState.items,
                            key = { it.id },
                        ) { item ->
                            val badge = if (uiState.showLanguageBadge)
                                uiState.sourceLanguageMap[item.sourceId]
                            else null
                            when (item) {
                                is NewsItem.Article -> ArticleCard(
                                    article = item,
                                    languageBadge = badge,
                                    onClick = {
                                        openArticle(context, item.url, mc.primaryAccent.toArgb())
                                    },
                                )
                                is NewsItem.Video -> VideoCard(
                                    video = item,
                                    languageBadge = badge,
                                    onClick = { onVideoClick(item.videoId, item.title) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Advanced search filter sheet ──────────────────────────────────────────
    if (showFilterSheet) {
        NewsFilterSheet(
            allSources = sources,
            currentFilterTypes = uiState.filterTypes,
            currentFilterLanguages = uiState.filterLanguages,
            currentFilterSourceIds = uiState.filterSourceIds,
            onApply = { types, languages, sourceIds ->
                viewModel.onFiltersApplied(types, languages, sourceIds)
            },
            onDismiss = { showFilterSheet = false },
        )
    }
}

private fun openArticle(context: android.content.Context, url: String, toolbarColor: Int) {
    try {
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColor)
            .build()
        val intent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(params)
            .setShowTitle(true)
            .build()
        intent.launchUrl(context, Uri.parse(url))
    } catch (_: Exception) {
        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(fallback)
    }
}
