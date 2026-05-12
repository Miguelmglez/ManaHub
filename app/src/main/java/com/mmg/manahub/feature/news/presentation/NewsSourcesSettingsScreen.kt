package com.mmg.manahub.feature.news.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.news.domain.model.ContentSource
import com.mmg.manahub.feature.news.domain.model.SourceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsSourcesSettingsScreen(
    onBack: () -> Unit,
    viewModel: NewsSourcesSettingsViewModel = hiltViewModel(),
) {
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val addState by viewModel.addState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography

    val articleSources = sources.filter { it.type == SourceType.ARTICLE }
    val videoSources = sources.filter { it.type == SourceType.VIDEO }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(
                color = mc.backgroundSecondary,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
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
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            // ── Article Sources ──────────────────────────────────────────
            item {
                Text(
                    text = stringResource(R.string.news_sources_section_articles),
                    style = mt.titleMedium,
                    color = mc.primaryAccent,
                )
            }
            items(articleSources, key = { it.id }) { source ->
                SourceItem(
                    source = source,
                    onToggle = { viewModel.toggleSource(source.id, it) },
                    onDelete = { viewModel.deleteSource(source.id) },
                )
            }

            // ── Video Sources ────────────────────────────────────────────
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Text(
                    text = stringResource(R.string.news_sources_section_videos),
                    style = mt.titleMedium,
                    color = mc.primaryAccent,
                )
            }
            items(videoSources, key = { it.id }) { source ->
                SourceItem(
                    source = source,
                    onToggle = { viewModel.toggleSource(source.id, it) },
                    onDelete = { viewModel.deleteSource(source.id) },
                )
            }

            // ── Add Custom Source ────────────────────────────────────────
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Text(
                    text = stringResource(R.string.news_sources_add_title),
                    style = mt.titleMedium,
                    color = mc.primaryAccent,
                )
            }
            item {
                AddCustomSourceSection(
                    state = addState,
                    onNameChanged = viewModel::onNameChanged,
                    onFeedUrlChanged = viewModel::onFeedUrlChanged,
                    onTypeChanged = viewModel::onTypeChanged,
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
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(mc.surface, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(source.name, style = mt.bodyLarge, color = mc.textPrimary)
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
            IconButton(onClick = onDelete) {
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
    onValidateAndAdd: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            shape = RoundedCornerShape(12.dp),
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
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        // Type radio
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceType.entries.forEach { type ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = state.type == type,
                        onClick = { onTypeChanged(type) },
                        colors = RadioButtonDefaults.colors(selectedColor = mc.primaryAccent),
                    )
                    Text(
                        text = when (type) {
                            SourceType.ARTICLE -> stringResource(R.string.news_filter_articles)
                            SourceType.VIDEO   -> stringResource(R.string.news_filter_videos)
                        },
                        style = mt.bodyMedium,
                        color = mc.textPrimary,
                    )
                }
            }
        }

        // Error message
        if (state.error != null) {
            Text(
                text = state.error,
                style = mt.bodySmall,
                color = mc.lifeNegative,
            )
        }

        // Preview count
        if (state.previewCount != null) {
            Text(
                text = stringResource(R.string.news_sources_found_items, state.previewCount),
                style = mt.bodySmall,
                color = mc.lifePositive,
            )
        }

        Button(
            onClick = onValidateAndAdd,
            enabled = state.name.isNotBlank() && state.feedUrl.isNotBlank() && !state.isValidating,
            colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isValidating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = mc.textPrimary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = stringResource(R.string.news_sources_validate_add),
                style = mt.labelLarge,
            )
        }
    }
}
