package com.mmg.manahub.feature.news.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.search.SearchSection
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.news.domain.model.ContentSource
import com.mmg.manahub.core.domain.model.news.SourceType
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewsFilterSheet(
    allSources: List<ContentSource>,
    currentFilterTypes: Set<SourceType>,
    currentFilterLanguages: Set<String>,
    currentFilterSourceIds: Set<String>?,
    onApply: (types: Set<SourceType>, languages: Set<String>, sourceIds: Set<String>?) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val scope = rememberCoroutineScope()
    var canDismiss by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { newValue ->
            newValue != SheetValue.Hidden || canDismiss
        }
    )

    var selectedTypes by remember { mutableStateOf(currentFilterTypes) }
    var selectedLanguages by remember { mutableStateOf(currentFilterLanguages) }
    
    val allEnabledSources = remember(allSources) { allSources.filter { it.isEnabled } }
    val allEnabledSourceIds = remember(allEnabledSources) { allEnabledSources.map { it.id }.toSet() }
    
    var selectedSourceIds by remember(allSources) {
        mutableStateOf(currentFilterSourceIds ?: allEnabledSourceIds)
    }

    fun handleDismiss() {
        if (canDismiss) return
        canDismiss = true
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
                .fillMaxHeight(0.85f)
                .navigationBarsPadding(),
        ) {
            // ── Header ──────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::handleDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = mc.textPrimary
                    )
                }
                Text(
                    text = stringResource(R.string.news_filter_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    selectedTypes = setOf(SourceType.ARTICLE, SourceType.VIDEO)
                    selectedLanguages = setOf("en", "es")
                    selectedSourceIds = allEnabledSourceIds
                }) {
                    Text(
                        text = stringResource(R.string.action_reset),
                        color = mc.lifeNegative,
                        style = ty.labelMedium
                    )
                }
            }

            // ── Scrollable Content ──────────────────────────────────────────────
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── Type Section ──
                item {
                    SearchSection(
                        title = stringResource(R.string.news_filter_content_type),
                        icon = Icons.Default.Description
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TypeChip(
                                label = stringResource(R.string.news_filter_articles),
                                icon = Icons.Default.Description,
                                selected = SourceType.ARTICLE in selectedTypes,
                                onToggle = { selectedTypes = selectedTypes.toggle(SourceType.ARTICLE) },
                            )
                            TypeChip(
                                label = stringResource(R.string.news_filter_videos),
                                icon = Icons.Default.PlayCircle,
                                selected = SourceType.VIDEO in selectedTypes,
                                onToggle = { selectedTypes = selectedTypes.toggle(SourceType.VIDEO) },
                            )
                        }
                    }
                }

                // ── Language Section ──
                item {
                    SearchSection(
                        title = stringResource(R.string.news_filter_language),
                        icon = Icons.Default.Language
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LanguageChip(
                                label = stringResource(R.string.news_language_en),
                                code = "en",
                                selected = "en" in selectedLanguages,
                                onToggle = { selectedLanguages = selectedLanguages.toggle("en") },
                            )
                            LanguageChip(
                                label = stringResource(R.string.news_language_es),
                                code = "es",
                                selected = "es" in selectedLanguages,
                                onToggle = { selectedLanguages = selectedLanguages.toggle("es") },
                            )
                        }
                    }
                }

                // ── Sources Section ──
                item {
                    SearchSection(
                        title = stringResource(R.string.news_filter_sources),
                        icon = Icons.Default.RssFeed
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = { selectedSourceIds = allEnabledSourceIds },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(stringResource(R.string.news_filter_select_all), style = ty.labelSmall, color = mc.primaryAccent)
                            }
                            TextButton(
                                onClick = { selectedSourceIds = emptySet() },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(stringResource(R.string.news_filter_deselect_all), style = ty.labelSmall, color = mc.textDisabled)
                            }
                        }
                        
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            allEnabledSources.forEach { source ->
                                SourceChip(
                                    source = source,
                                    selected = source.id in selectedSourceIds,
                                    onToggle = { selectedSourceIds = selectedSourceIds.toggle(source.id) },
                                )
                            }
                        }
                    }
                }
            }

            // ── Apply Button ──
            Button(
                onClick = {
                    val resolvedSourceIds = if (selectedSourceIds == allEnabledSourceIds) null
                    else selectedSourceIds
                    onApply(selectedTypes, selectedLanguages, resolvedSourceIds)
                    handleDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.primaryAccent,
                    contentColor = mc.background,
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.news_filter_apply),
                    style = ty.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun TypeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    FilterChip(
        selected = selected,
        onClick = onToggle,
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        },
        label = { Text(label, style = ty.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = mc.primaryAccent.copy(alpha = 0.15f),
            selectedLabelColor = mc.primaryAccent,
            selectedLeadingIconColor = mc.primaryAccent,
            containerColor = mc.surface,
            labelColor = mc.textSecondary,
            iconColor = mc.textDisabled,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = mc.surfaceVariant.copy(alpha = 0.5f),
            selectedBorderColor = mc.primaryAccent,
            enabled = true,
            selected = selected,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.5.dp
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun LanguageChip(label: String, code: String, selected: Boolean, onToggle: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text("$label (${code.uppercase()})", style = ty.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = mc.primaryAccent.copy(alpha = 0.15f),
            selectedLabelColor = mc.primaryAccent,
            containerColor = mc.surface,
            labelColor = mc.textSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = mc.surfaceVariant.copy(alpha = 0.5f),
            selectedBorderColor = mc.primaryAccent,
            enabled = true,
            selected = selected,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.5.dp
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
private fun SourceChip(source: ContentSource, selected: Boolean, onToggle: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val context = LocalContext.current

    FilterChip(
        selected = selected,
        onClick = onToggle,
        leadingIcon = {
            if (source.iconUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(source.iconUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = if (source.type == SourceType.VIDEO) Icons.Default.PlayCircle 
                                 else Icons.Default.RssFeed,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        label = { Text(source.name, style = ty.labelSmall) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = mc.primaryAccent.copy(alpha = 0.15f),
            selectedLabelColor = mc.primaryAccent,
            selectedLeadingIconColor = mc.primaryAccent,
            containerColor = mc.surface,
            labelColor = mc.textSecondary,
            iconColor = mc.textDisabled,
        ),
        border = FilterChipDefaults.filterChipBorder(
            borderColor = mc.surfaceVariant.copy(alpha = 0.5f),
            selectedBorderColor = mc.primaryAccent,
            enabled = true,
            selected = selected,
            borderWidth = 1.dp,
            selectedBorderWidth = 1.5.dp
        ),
        shape = RoundedCornerShape(10.dp)
    )
}

private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item
