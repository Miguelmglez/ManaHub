package com.mmg.manahub.feature.tagdictionary.presentation

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDictionaryScreen(
    onBack: () -> Unit,
    viewModel: TagDictionaryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val spacing = MaterialTheme.spacing
    val mc = MaterialTheme.magicColors

    val toastState = rememberMagicToastState()
    val resetAllSuccessMessage = stringResource(R.string.tagdictionary_reset_all_success_toast)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                TagDictionaryEvent.CustomTagsCleared ->
                    toastState.show(resetAllSuccessMessage, MagicToastType.SUCCESS)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                Surface(color = mc.backgroundSecondary) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = spacing.xs, vertical = spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = mc.textPrimary,
                            )
                        }
                        Text(
                            text = stringResource(R.string.tagdictionary_title),
                            style = MaterialTheme.magicTypography.titleLarge,
                            color = mc.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = viewModel::onStartCreateCustomTag) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.tagdictionary_create_custom_tag_description),
                                tint = mc.textPrimary,
                            )
                        }
                        IconButton(onClick = viewModel::onRequestResetAll) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.tagdictionary_reset_all_description),
                                tint = mc.textPrimary,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                ThresholdsCard(
                    auto             = state.autoThreshold,
                    suggest          = state.suggestThreshold,
                    onAutoChange     = viewModel::setAutoThreshold,
                    onSuggestChange  = viewModel::setSuggestThreshold,
                )

                OutlinedTextField(
                    value         = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier      = Modifier.fillMaxWidth().padding(horizontal = spacing.lg, vertical = spacing.xs),
                    singleLine    = true,
                    label         = { Text(stringResource(R.string.tagdictionary_search_hint)) },
                )

                val filtered = remember(state.rows, state.query) {
                    if (state.query.isBlank()) state.rows
                    else state.rows.filter { row ->
                        val q = state.query.trim().lowercase()
                        row.key.contains(q) || row.labelEn.lowercase().contains(q)
                    }
                }

                if (filtered.isEmpty()) {
                    EmptyState(
                        title    = stringResource(R.string.tagdictionary_empty_title),
                        subtitle = stringResource(R.string.tagdictionary_empty_subtitle),
                    )
                } else {
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = spacing.xxl + navBarBottom),
                        modifier       = Modifier.fillMaxSize(),
                    ) {
                        items(filtered, key = { it.key }) { row ->
                            DictionaryRow(
                                row     = row,
                                onTap   = { if (!row.isSystem) viewModel.onStartEdit(row.key) },
                                onReset = { viewModel.resetEntry(row.key) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        MagicToastHost(state = toastState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    state.editingKey?.let { key ->
        val row = state.rows.firstOrNull { it.key == key && !it.isSystem } ?: return@let
        EditEntryDialog(
            initial   = row,
            onDismiss = viewModel::onDismissEdit,
            onSave    = viewModel::saveOverride,
        )
    }

    if (state.isCreatingCustomTag) {
        CreateCustomTagDialog(
            onDismiss = viewModel::onDismissCreateCustomTag,
            onCreate  = { label, rules -> viewModel.createCustomTag(label = label, rules = rules) },
        )
    }

    if (state.isConfirmingResetAll) {
        ResetAllConfirmDialog(
            onDismiss = viewModel::onDismissResetAll,
            onConfirm = viewModel::resetAll,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThresholdsCard(
    auto:            Float,
    suggest:         Float,
    onAutoChange:    (Float) -> Unit,
    onSuggestChange: (Float) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    // Slider ranges mirror the VM-level clamps exactly (F6): auto is coerceIn(0.05f, 1f) in
    // TagDictionaryViewModel.setAutoThreshold; suggest's true ceiling is dynamic (auto - 0.05).
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(spacing.lg)) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(stringResource(R.string.tagdictionary_thresholds_title), style = ty.titleMedium)
            Text(
                text  = stringResource(
                    R.string.tagdictionary_thresholds_description,
                    (auto * 100).toInt(),
                    (suggest * 100).toInt()
                ),
                style = ty.bodySmall,
                color = mc.textSecondary,
            )
            Text(
                stringResource(R.string.tagdictionary_thresholds_auto, (auto * 100).toInt()),
                style = ty.labelMedium
            )
            Slider(value = auto, onValueChange = onAutoChange, valueRange = 0.05f..1f)
            Text(
                stringResource(R.string.tagdictionary_thresholds_suggest, (suggest * 100).toInt()),
                style = ty.labelMedium
            )
            Slider(
                value = suggest,
                onValueChange = onSuggestChange,
                valueRange = 0f..(auto - 0.05f).coerceAtLeast(0f),
            )
        }
    }
}

@Composable
private fun DictionaryRow(
    row: TagDictionaryRow,
    onTap: () -> Unit,
    onReset: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !row.isSystem, onClick = onTap)
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text  = row.key,
                style = ty.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(spacing.xs))
            Text(
                text  = stringResource(
                    R.string.tagdictionary_row_labels,
                    row.labelEn.ifBlank { "—" },
                ),
                style = ty.bodySmall,
                color = mc.textSecondary,
            )
            val patternCount = row.rules.size
            Spacer(Modifier.height(spacing.xs))
            if (row.isSystem) {
                Text(
                    text  = stringResource(R.string.tagdictionary_system_readonly),
                    style = ty.labelSmall,
                    color = mc.textDisabled,
                )
            } else if (patternCount > 0) {
                Text(
                    text  = stringResource(
                        R.string.tagdictionary_row_patterns,
                        patternCount,
                        row.category.name
                    ),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                )
            } else {
                Text(
                    text  = row.category.name,
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                )
            }
        }
        if (row.isSystem) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = stringResource(R.string.tagdictionary_system_readonly),
                tint = mc.textDisabled,
            )
        } else {
            IconButton(onClick = onReset) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.tagdictionary_reset_description))
            }
        }
    }
}

@Composable
private fun EditEntryDialog(
    initial: TagDictionaryRow,
    onDismiss: () -> Unit,
    onSave: (TagDictionaryRow) -> Unit,
) {
    var labelEn by remember { mutableStateOf(initial.labelEn) }
    var rulesText by remember { mutableStateOf(initial.rules.joinToString("\n")) }

    val spacing = MaterialTheme.spacing
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tagdictionary_edit_title, initial.key)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(stringResource(R.string.tagdictionary_labels_section), style = ty.labelMedium)
                OutlinedTextField(
                    value = labelEn,
                    onValueChange = { labelEn = it },
                    label = { Text("EN") },
                    singleLine = true,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    stringResource(R.string.tagdictionary_patterns_hint),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                )
                OutlinedTextField(
                    value = rulesText,
                    onValueChange = { rulesText = it },
                    label = { Text(stringResource(R.string.tagdictionary_patterns_en_label)) },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        labelEn = labelEn.trim(),
                        rules   = rulesText.lines().map { it.trim() }.filter { it.isNotEmpty() },
                    )
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** D12: the "create custom tag" entry point — always produces a fresh `custom_`-namespaced key. */
@Composable
private fun CreateCustomTagDialog(
    onDismiss: () -> Unit,
    onCreate: (label: String, rules: List<String>) -> Unit,
) {
    var labelEn by remember { mutableStateOf("") }
    var rulesText by remember { mutableStateOf("") }

    val spacing = MaterialTheme.spacing
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tagdictionary_create_custom_tag)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(stringResource(R.string.tagdictionary_create_label_hint), style = ty.labelMedium)
                OutlinedTextField(
                    value = labelEn,
                    onValueChange = { labelEn = it },
                    label = { Text("EN") },
                    singleLine = true,
                )
                Spacer(Modifier.height(spacing.xs))
                Text(
                    stringResource(R.string.tagdictionary_patterns_hint),
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                )
                OutlinedTextField(
                    value = rulesText,
                    onValueChange = { rulesText = it },
                    label = { Text(stringResource(R.string.tagdictionary_patterns_en_label)) },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = labelEn.isNotBlank(),
                onClick = {
                    onCreate(labelEn.trim(), rulesText.lines().map { it.trim() }.filter { it.isNotEmpty() })
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** F2: confirmation gate before deleting every custom tag. */
@Composable
private fun ResetAllConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tagdictionary_reset_all_confirm_title)) },
        text = { Text(stringResource(R.string.tagdictionary_reset_all_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.tagdictionary_reset_all_confirm_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
