package com.mmg.manahub.feature.news.presentation

import android.annotation.SuppressLint
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.news.presentation.components.ArticleCard
import com.mmg.manahub.feature.news.presentation.components.NewsFilterSheet
import com.mmg.manahub.feature.news.presentation.components.ShimmerNewsItem
import com.mmg.manahub.feature.news.presentation.components.VideoCard
import org.koin.androidx.compose.koinViewModel

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    onBack: () -> Unit ,
    onVideoClick: (videoId: String, title: String) -> Unit = { _, _ -> },
    viewModel: NewsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val rawSearchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    val context = LocalContext.current
    val toastState = rememberMagicToastState()

    var showFilterSheet by remember { mutableStateOf(false) }

    val allSourceTypes = setOf(SourceType.ARTICLE, SourceType.VIDEO)
    val hasActiveFilters = uiState.filterTypes != allSourceTypes
            || uiState.filterLanguages.size > 1
            || uiState.filterSourceIds != null

    // One-shot toast events (partial/total refresh failure) — never Snackbar (project rule).
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is NewsEvent.ShowPartialRefreshFailure -> {
                    val msg =
                        context.getString(R.string.news_refresh_partial_failure, event.failedCount)
                    toastState.show(msg, MagicToastType.ERROR)
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(
                color = mc.backgroundSecondary
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Advanced search filters",
                            tint = mc.textSecondary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Text(
                        text = stringResource(R.string.news_title),
                        style = mt.titleLarge,
                        color = mc.textPrimary,
                        modifier = Modifier.padding(
                            start = 8.dp,
                        ),
                    )
                }
            }
        },
        containerColor = mc.background,

        ) { padding ->
        HexGridBackground(
            modifier = Modifier.fillMaxSize(),
            color = mc.primaryAccent.copy(alpha = 0.05f)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Spacer(Modifier.height(12.dp))


            // ── Search bar + filter icon ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = rawSearchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = {
                        Text(
                            stringResource(R.string.news_search_hint),
                            style = mt.bodyMedium,
                            color = mc.textDisabled,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = mc.textSecondary
                        )
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
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.padding(horizontal = 16.dp),
                    enabled = !uiState.isRefreshing,
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Content ───────────────────────────────────────────────────────────
            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh(force = true) },
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
                        val navBarBottom =
                            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
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
                                            openArticle(
                                                context,
                                                item.url,
                                                mc.primaryAccent.toArgb()
                                            )
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

        MagicToastHost(state = toastState)
    }
}


private fun openArticle(context: android.content.Context, url: String, toolbarColor: Int) {
    val uri = Uri.parse(url)
    if (uri.scheme != "https" && uri.scheme != "http") return
    try {
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColor)
            .build()
        val intent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(params)
            .setShowTitle(true)
            .build()
        intent.launchUrl(context, uri)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) { /* no handler available */
        }
    }
}
