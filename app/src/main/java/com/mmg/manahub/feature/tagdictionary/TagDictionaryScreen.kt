package com.mmg.manahub.feature.tagdictionary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDictionaryScreen(
    onBack: () -> Unit,
    viewModel: TagDictionaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val mc = MaterialTheme.magicColors

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(color = mc.backgroundSecondary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = mc.textPrimary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.tagdictionary_title),
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::resetAll) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
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
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine    = true,
                label         = { Text(stringResource(R.string.tagdictionary_search_hint)) },
            )

            val filtered = remember(state.rows, state.query) {
                if (state.query.isBlank()) state.rows
                else state.rows.filter { row ->
                    val q = state.query.trim().lowercase()
                    row.key.contains(q) ||
                            row.labelEn.lowercase().contains(q) ||
                            row.labelEs.lowercase().contains(q)
                }
            }

            val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp + navBarBottom),
                modifier       = Modifier.fillMaxSize(),
            ) {
                items(filtered, key = { it.key }) { row ->
                    DictionaryRow(
                        row    = row,
                        onTap  = { viewModel.onStartEdit(row.key) },
                        onReset = { viewModel.resetEntry(row.key) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    state.editingKey?.let { key ->
        val row = state.rows.firstOrNull { it.key == key } ?: return@let
        EditEntryDialog(
            initial   = row,
            onDismiss = viewModel::onDismissEdit,
            onSave    = viewModel::saveOverride,
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
    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.tagdictionary_thresholds_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text  = stringResource(
                    R.string.tagdictionary_thresholds_description,
                    (auto * 100).toInt(),
                    (suggest * 100).toInt()
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.tagdictionary_thresholds_auto, (auto * 100).toInt()),
                style = MaterialTheme.typography.labelMedium
            )
            Slider(value = auto, onValueChange = onAutoChange, valueRange = 0.5f..1f)
            Text(
                stringResource(R.string.tagdictionary_thresholds_suggest, (suggest * 100).toInt()),
                style = MaterialTheme.typography.labelMedium
            )
            Slider(value = suggest, onValueChange = onSuggestChange, valueRange = 0f..0.95f)
        }
    }
}

@Composable
private fun DictionaryRow(
    row: TagDictionaryRow,
    onTap: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text  = row.key,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = stringResource(
                    R.string.tagdictionary_row_labels,
                    row.labelEn.ifBlank { "—" },
                    //row.labelEs.ifBlank { "—" }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val patternCount = row.patternsEn.size + row.patternsEs.size + row.patternsDe.size
            if (patternCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = stringResource(
                        R.string.tagdictionary_row_patterns,
                        patternCount,
                        row.category.name
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = row.category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onReset) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.tagdictionary_reset_description))
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
    //var labelEs by remember { mutableStateOf(initial.labelEs) }
    var patternsEn by remember { mutableStateOf(initial.patternsEn.joinToString("\n")) }
    var patternsEs by remember { mutableStateOf(initial.patternsEs.joinToString("\n")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tagdictionary_edit_title, initial.key)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.tagdictionary_labels_section), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(value = labelEn, onValueChange = { labelEn = it }, label = { Text("EN") }, singleLine = true)
            //    OutlinedTextField(value = labelEs, onValueChange = { labelEs = it }, label = { Text("ES") }, singleLine = true)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.tagdictionary_patterns_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = patternsEn, onValueChange = { patternsEn = it }, label = { Text(stringResource(R.string.tagdictionary_patterns_en_label)) }, minLines = 2)
              //  OutlinedTextField(value = patternsEs, onValueChange = { patternsEs = it }, label = { Text(stringResource(R.string.tagdictionary_patterns_es_label)) }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        labelEn    = labelEn.trim(),
                      //  labelEs    = labelEs.trim(),
                        patternsEn = patternsEn.lines().map { it.trim().lowercase() }.filter { it.isNotEmpty() },
                     //   patternsEs = patternsEs.lines().map { it.trim().lowercase() }.filter { it.isNotEmpty() },
                    )
                )
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
