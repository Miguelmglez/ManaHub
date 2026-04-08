package com.mmg.magicfolder.feature.tagdictionary

import androidx.compose.foundation.background
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
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagDictionaryScreen(
    onBack: () -> Unit,
    viewModel: TagDictionaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val mc = MaterialTheme.magicColors

    Scaffold(
        topBar = {
            Surface(color = mc.backgroundSecondary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
                        text = "Diccionario de etiquetas",
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = viewModel::resetAll) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Restablecer todo",
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
                label         = { Text("Buscar tag…") },
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

            LazyColumn(
                contentPadding      = PaddingValues(bottom = 32.dp),
                modifier            = Modifier.fillMaxSize(),
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
            Text("Umbrales de precisión", style = MaterialTheme.typography.titleSmall)
            Text(
                text  = "Etiquetas con confianza ≥ ${(auto * 100).toInt()}% se añaden automáticamente. " +
                        "Entre ${(suggest * 100).toInt()}% y ${(auto * 100).toInt()}% se sugieren.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Auto-añadir: ${(auto * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
            Slider(value = auto, onValueChange = onAutoChange, valueRange = 0.5f..1f)
            Text("Sugerir: ${(suggest * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
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
            Text(
                text  = "EN: ${row.labelEn.ifBlank { "—" }}   ES: ${row.labelEs.ifBlank { "—" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val patternCount = row.patternsEn.size + row.patternsEs.size + row.patternsDe.size
            if (patternCount > 0) {
                Text(
                    text  = "$patternCount patrones · ${row.category.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text  = row.category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onReset) {
            Icon(Icons.Default.Delete, contentDescription = "Restablecer")
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
    var labelEs by remember { mutableStateOf(initial.labelEs) }
    var patternsEn by remember { mutableStateOf(initial.patternsEn.joinToString("\n")) }
    var patternsEs by remember { mutableStateOf(initial.patternsEs.joinToString("\n")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar: ${initial.key}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Etiquetas", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(value = labelEn, onValueChange = { labelEn = it }, label = { Text("EN") }, singleLine = true)
                OutlinedTextField(value = labelEs, onValueChange = { labelEs = it }, label = { Text("ES") }, singleLine = true)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Patrones (uno por línea, en minúsculas — el motor busca coincidencias literales en el texto de la carta)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = patternsEn, onValueChange = { patternsEn = it }, label = { Text("Patrones EN") }, minLines = 2)
                OutlinedTextField(value = patternsEs, onValueChange = { patternsEs = it }, label = { Text("Patrones ES") }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    initial.copy(
                        labelEn    = labelEn.trim(),
                        labelEs    = labelEs.trim(),
                        patternsEn = patternsEn.lines().map { it.trim().lowercase() }.filter { it.isNotEmpty() },
                        patternsEs = patternsEs.lines().map { it.trim().lowercase() }.filter { it.isNotEmpty() },
                    )
                )
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
