package com.mmg.manahub.feature.today.presentation.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.FeedContentFilter
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.core.ui.components.MagicFilterChip
import com.mmg.manahub.core.ui.components.search.SearchSection
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.news.domain.source.SourceIconExtractor
import com.mmg.manahub.feature.today.presentation.common.sourceInitials
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FeedFiltersSheet(
    state: FeedUiState,
    onType: (FeedContentFilter) -> Unit,
    onSource: (String?) -> Unit,
    onSaved: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var savedExpanded by rememberSaveable { mutableStateOf(true) }
    var typesExpanded by rememberSaveable { mutableStateOf(true) }
    var sourcesExpanded by rememberSaveable { mutableStateOf(true) }

    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val scope = rememberCoroutineScope()

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    fun handleDismiss() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding(),
        ) {
            // ── Header ──────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.sm, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::handleDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = mc.textPrimary
                    )
                }
                Text(
                    stringResource(R.string.today_filters),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        onType(FeedContentFilter.ALL)
                        onSource(null)
                        onSaved(false)
                    },
                    contentPadding = PaddingValues(horizontal = spacing.sm, vertical = 0.dp),
                ) {
                    Text(
                        text = stringResource(R.string.today_reset_filters).uppercase(),
                        color = mc.lifeNegative,
                        style = ty.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                        ),
                    )
                }
            }

            // ── Scrollable content ──────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
                contentPadding = PaddingValues(vertical = spacing.md)
            ) {
                // Section 1: Saved items
                item(key = "saved_mode") {
                    SearchSection(
                        title = stringResource(R.string.today_saved_only),
                        icon = Icons.Default.Bookmark,
                        expandedState = savedExpanded,
                        onExpandedChange = { savedExpanded = it },
                        titleColor = mc.textPrimary,
                        iconColor = mc.goldMtg,
                    ) {
                        Surface(
                            shape = CardShape,
                            color = mc.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = state.savedOnly,
                                        role = Role.Switch,
                                        onValueChange = onSaved
                                    )
                                    .padding(spacing.md),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.today_saved_only),
                                        style = ty.titleMedium,
                                        color = mc.textPrimary
                                    )
                                    Text(
                                        stringResource(
                                            if (state.savedOnly) R.string.today_saved_mode_desc
                                            else R.string.today_live_mode_desc
                                        ),
                                        style = ty.bodySmall,
                                        color = mc.textSecondary
                                    )
                                }
                                Switch(
                                    checked = state.savedOnly,
                                    onCheckedChange = null,
                                    colors = SwitchDefaults.colors(checkedThumbColor = mc.primaryAccent)
                                )
                            }
                        }
                    }
                }

                // Section 2: Content types
                item(key = "content_types") {
                    SearchSection(
                        title = stringResource(R.string.today_content_type),
                        icon = Icons.Default.Style,
                        expandedState = typesExpanded,
                        onExpandedChange = { typesExpanded = it },
                        titleColor = mc.textPrimary,
                        iconColor = mc.goldMtg,
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FeedContentFilter.entries.forEach { type ->
                                MagicFilterChip(
                                    selected = state.contentFilter == type,
                                    onClick = { onType(type) },
                                    label = stringResource(
                                        when (type) {
                                            FeedContentFilter.ALL -> R.string.today_filter_all
                                            FeedContentFilter.ARTICLES -> R.string.today_filter_articles
                                            FeedContentFilter.VIDEOS -> R.string.today_filter_videos
                                        }
                                    )
                                )
                            }
                        }
                    }
                }

                // Section 3: Sources
                item(key = "sources") {
                    SearchSection(
                        title = stringResource(R.string.today_tab_sources),
                        icon = Icons.Default.RssFeed,
                        expandedState = sourcesExpanded,
                        onExpandedChange = { sourcesExpanded = it },
                        titleColor = mc.textPrimary,
                        iconColor = mc.goldMtg,
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // "All Sources" option
                            MagicFilterChip(
                                selected = state.selectedSourceIds.isEmpty(),
                                onClick = { onSource(null) },
                                label = stringResource(R.string.today_rail_all),
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.RssFeed,
                                        contentDescription = null,
                                        tint = if (state.selectedSourceIds.isEmpty()) mc.primaryAccent else mc.textSecondary,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            )

                            // Individual source options with icon
                            state.filterSources.forEach { source ->
                                val selected = source.id in state.selectedSourceIds
                                MagicFilterChip(
                                    selected = selected,
                                    onClick = { onSource(source.id) },
                                    label = source.name,
                                    leadingIcon = {
                                        SourceChipIcon(source = source)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceChipIcon(source: ContentSource) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val iconModel = source.iconUrl ?: if (source.type == SourceType.ARTICLE) SourceIconExtractor.favicon(source.siteUrl) else null
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(mc.primaryAccent.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        if (iconModel != null) {
            AsyncImage(
                model = iconModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = sourceInitials(source.name),
                style = ty.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                color = mc.primaryAccent
            )
        }
    }
}
