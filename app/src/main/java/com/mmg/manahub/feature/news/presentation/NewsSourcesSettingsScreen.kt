package com.mmg.manahub.feature.news.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.model.news.NewsFilterPrefs
import com.mmg.manahub.core.model.news.SourceType
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicFilterChip
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.feature.news.presentation.components.languageLabelRes
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsSourcesSettingsScreen(
    onBack: () -> Unit,
    viewModel: NewsSourcesSettingsViewModel = koinViewModel(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val addState by viewModel.addState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    val articleSources = sources.filter { it.type == SourceType.ARTICLE }
    val videoSources = sources.filter { it.type == SourceType.VIDEO }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(
                color = mc.backgroundSecondary,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = spacing.xs, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textPrimary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.news_sources_title),
                        style = mt.titleLarge,
                        color = mc.textPrimary,
                        modifier = Modifier.padding(start = spacing.md)
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
            contentPadding = PaddingValues(vertical = spacing.lg),
        ) {
            // ── Article Sources ──────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.news_sources_section_articles),
                    style = mt.titleMedium,
                    color = mc.primaryAccent,
                    modifier = Modifier.padding(vertical = spacing.xs)
                )
            }
            items(articleSources, key = { it.id }) { source ->
                SourceItem(
                    source = source,
                    onToggle = { viewModel.toggleSource(source.id, it) },
                    onDelete = { viewModel.deleteSource(source.id) },
                    modifier = Modifier.animateItem()
                )
            }

            // ── Video Sources ────────────────────────────────────────────
            item { Spacer(Modifier.height(spacing.sm)) }
            item {
                Text(
                    text = stringResource(R.string.news_sources_section_videos),
                    style = mt.titleMedium,
                    color = mc.primaryAccent,
                    modifier = Modifier.padding(vertical = spacing.xs)
                )
            }
            items(videoSources, key = { it.id }) { source ->
                SourceItem(
                    source = source,
                    onToggle = { viewModel.toggleSource(source.id, it) },
                    onDelete = { viewModel.deleteSource(source.id) },
                    modifier = Modifier.animateItem()
                )
            }

            // ── Add Custom Source ────────────────────────────────────────
            item { Spacer(Modifier.height(spacing.lg)) }
            item {
                Text(
                    text = stringResource(R.string.news_sources_add_title),
                    style = mt.titleMedium,
                    color = mc.primaryAccent,
                    modifier = Modifier.padding(vertical = spacing.xs)
                )
            }
            item {
                AddCustomSourceSection(
                    state = addState,
                    onNameChanged = viewModel::onNameChanged,
                    onFeedUrlChanged = viewModel::onFeedUrlChanged,
                    onTypeChanged = viewModel::onTypeChanged,
                    onLanguageChanged = viewModel::onLanguageChanged,
                    onValidateAndAdd = viewModel::validateAndAdd,
                )
            }
        }
    }
}

@Composable
private fun SourceItem(
    source: ContentSource,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(mc.surface)
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(source.name, style = mt.bodyLarge, color = mc.textPrimary)
            if (source.language.isNotEmpty()) {
                Text(
                    text = source.language.uppercase(),
                    style = mt.labelSmall,
                    color = mc.textDisabled
                )
            }
        }

        Switch(
            checked = source.isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = mc.surface,
                checkedTrackColor = mc.primaryAccent,
                checkedIconColor = mc.primaryAccent,
            ),
        )
        if (!source.isDefault) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier.padding(start = spacing.sm)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = mc.lifeNegative,
                )
            }
        }
    }
}

@Composable
private fun AddCustomSourceSection(
    state: AddSourceState,
    onNameChanged: (String) -> Unit,
    onFeedUrlChanged: (String) -> Unit,
    onTypeChanged: (SourceType) -> Unit,
    onLanguageChanged: (String) -> Unit,
    onValidateAndAdd: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(mc.surface)
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChanged,
            placeholder = {
                Text(
                    stringResource(R.string.news_sources_name_hint),
                    color = mc.textDisabled,
                    style = mt.bodyMedium
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
            shape = CardShape,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = state.feedUrl,
            onValueChange = onFeedUrlChanged,
            placeholder = {
                Text(
                    stringResource(R.string.news_sources_url_hint),
                    color = mc.textDisabled,
                    style = mt.bodyMedium
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
            shape = CardShape,
            modifier = Modifier.fillMaxWidth(),
        )

        // Type radio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceType.entries.forEach { type ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onTypeChanged(type) }
                ) {
                    RadioButton(
                        selected = state.type == type,
                        onClick = null, // Handled by row clickable
                        colors = RadioButtonDefaults.colors(
                            selectedColor = mc.primaryAccent,
                            unselectedColor = mc.textDisabled
                        ),
                    )
                    Text(
                        text = when (type) {
                            SourceType.ARTICLE -> stringResource(R.string.news_filter_articles)
                            SourceType.VIDEO   -> stringResource(R.string.news_filter_videos)
                        },
                        style = mt.bodyMedium,
                        color = if (state.type == type) mc.textPrimary else mc.textSecondary,
                        modifier = Modifier.padding(start = spacing.xs)
                    )
                }
            }
        }

        // Language selector — F3: pick the content language for the new source (default EN).
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(
                text = stringResource(R.string.news_sources_language_label),
                style = mt.labelMedium,
                color = mc.textSecondary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NewsFilterPrefs.SUPPORTED_NEWS_LANGUAGES.forEach { code ->
                    MagicFilterChip(
                        selected = state.language == code,
                        onClick = { onLanguageChanged(code) },
                        label = stringResource(languageLabelRes(code)),
                    )
                }
            }
        }

        // Error message
        AnimatedVisibility(
            visible = state.error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = state.error ?: "",
                style = mt.bodySmall,
                color = mc.lifeNegative,
            )
        }

        // Preview count
        AnimatedVisibility(
            visible = state.previewCount != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                text = stringResource(R.string.news_sources_found_items, state.previewCount ?: 0),
                style = mt.bodySmall,
                color = mc.lifePositive,
            )
        }

        MagicCtaButton(
            onClick = onValidateAndAdd,
            enabled = state.name.isNotBlank() && state.feedUrl.isNotBlank() && !state.isValidating,
            isLoading = state.isValidating,
            text = stringResource(R.string.news_sources_validate_add),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
