package com.mmg.magicfolder.feature.news.presentation

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.news.domain.model.ContentSource
import com.mmg.magicfolder.feature.news.domain.model.ContentType
import com.mmg.magicfolder.feature.news.domain.model.NewsItem
import com.mmg.magicfolder.feature.news.presentation.components.ArticleCard
import com.mmg.magicfolder.feature.news.presentation.components.ShimmerNewsItem
import com.mmg.magicfolder.feature.news.presentation.components.VideoCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    viewModel: NewsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(mc.background),
    ) {
        // ── Title ────────────────────────────────────────────────────────────
        Text(
            text = stringResource(R.string.news_title),
            style = mt.titleLarge,
            color = mc.textPrimary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        // ── Search bar ───────────────────────────────────────────────────────
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(8.dp))

        // ── Content type chips ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ContentType.entries.forEach { type ->
                val selected = uiState.contentType == type
                val label = when (type) {
                    ContentType.ALL     -> stringResource(R.string.news_filter_all)
                    ContentType.ARTICLE -> stringResource(R.string.news_filter_articles)
                    ContentType.VIDEO   -> stringResource(R.string.news_filter_videos)
                }
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.onContentTypeChanged(type) },
                    label = { Text(label, style = mt.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = mc.primaryAccent.copy(alpha = 0.2f),
                        selectedLabelColor = mc.primaryAccent,
                        containerColor = mc.surface,
                        labelColor = mc.textSecondary,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = mc.surfaceVariant,
                        selectedBorderColor = mc.primaryAccent,
                        enabled = true,
                        selected = selected,
                    ),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ── Source filter dropdown ────────────────────────────────────────────
        SourceFilterDropdown(
            sources = sources,
            selectedSourceId = uiState.selectedSourceId,
            onSourceSelected = viewModel::onSourceFilterChanged,
        )

        Spacer(Modifier.height(8.dp))

        // ── Error inline ─────────────────────────────────────────────────────
        if (uiState.error != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(mc.lifeNegative.copy(alpha = 0.15f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uiState.error ?: "",
                    style = mt.bodySmall,
                    color = mc.lifeNegative,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.action_retry),
                    style = mt.labelSmall,
                    color = mc.primaryAccent,
                    modifier = Modifier.clickable { viewModel.refresh() },
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        // ── Content ──────────────────────────────────────────────────────────
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
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            items = uiState.items,
                            key = { it.id },
                        ) { item ->
                            when (item) {
                                is NewsItem.Article -> ArticleCard(
                                    article = item,
                                    onClick = {
                                        openArticle(context, item.url, mc.primaryAccent.toArgb())
                                    },
                                )
                                is NewsItem.Video -> VideoCard(
                                    video = item,
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url))
                                        context.startActivity(intent)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceFilterDropdown(
    sources: List<ContentSource>,
    selectedSourceId: String?,
    onSourceSelected: (String?) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    var expanded by remember { mutableStateOf(false) }
    val selectedName = sources.find { it.id == selectedSourceId }?.name
        ?: stringResource(R.string.news_all_sources)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = mc.primaryAccent,
                unfocusedBorderColor = mc.surfaceVariant,
                focusedTextColor = mc.textPrimary,
                unfocusedTextColor = mc.textPrimary,
            ),
            shape = RoundedCornerShape(12.dp),
            textStyle = mt.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = mc.backgroundSecondary,
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.news_all_sources),
                        color = mc.textPrimary,
                        style = mt.bodyMedium,
                    )
                },
                onClick = {
                    onSourceSelected(null)
                    expanded = false
                },
            )
            sources.filter { it.isEnabled }.forEach { source ->
                DropdownMenuItem(
                    text = {
                        Text(source.name, color = mc.textPrimary, style = mt.bodyMedium)
                    },
                    onClick = {
                        onSourceSelected(source.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
