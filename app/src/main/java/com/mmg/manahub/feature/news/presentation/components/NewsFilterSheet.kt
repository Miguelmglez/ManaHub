package com.mmg.manahub.feature.news.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.news.domain.model.ContentSource
import com.mmg.manahub.feature.news.domain.model.SourceType

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
    val mt = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedTypes by remember { mutableStateOf(currentFilterTypes) }
    var selectedLanguages by remember { mutableStateOf(currentFilterLanguages) }
    // null means "all"; non-null set means only those source IDs are selected
    var selectedSourceIds by remember {
        mutableStateOf(currentFilterSourceIds ?: allSources.filter { it.isEnabled }.map { it.id }.toSet())
    }

    val allEnabledSourceIds = remember(allSources) {
        allSources.filter { it.isEnabled }.map { it.id }.toSet()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Spacer(Modifier.height(20.dp))

            Text(
                text = "Advanced Search",
                style = mt.titleMedium,
                color = mc.textPrimary,
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = mc.surfaceVariant)
            Spacer(Modifier.height(16.dp))

            // ── Type filter ───────────────────────────────────────────────────
            Text(
                text = "Type",
                style = mt.labelMedium,
                color = mc.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypeChip(
                    label = "Articles",
                    selected = SourceType.ARTICLE in selectedTypes,
                    onToggle = { selectedTypes = selectedTypes.toggle(SourceType.ARTICLE) },
                )
                TypeChip(
                    label = "Videos",
                    selected = SourceType.VIDEO in selectedTypes,
                    onToggle = { selectedTypes = selectedTypes.toggle(SourceType.VIDEO) },
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = mc.surfaceVariant)
            Spacer(Modifier.height(16.dp))

            // ── Language filter ───────────────────────────────────────────────
            Text(
                text = "Feed Language",
                style = mt.labelMedium,
                color = mc.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageChip(
                    label = "English",
                    code = "en",
                    selected = "en" in selectedLanguages,
                    onToggle = { selectedLanguages = selectedLanguages.toggle("en") },
                )
                LanguageChip(
                    label = "Español",
                    code = "es",
                    selected = "es" in selectedLanguages,
                    onToggle = { selectedLanguages = selectedLanguages.toggle("es") },
                )
            }

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = mc.surfaceVariant)
            Spacer(Modifier.height(16.dp))

            // ── Sources filter ────────────────────────────────────────────────
            Text(
                text = "Sources",
                style = mt.labelMedium,
                color = mc.textSecondary,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allSources.filter { it.isEnabled }.forEach { source ->
                    SourceChip(
                        label = source.name,
                        selected = source.id in selectedSourceIds,
                        onToggle = { selectedSourceIds = selectedSourceIds.toggle(source.id) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Action buttons ────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        selectedTypes = setOf(SourceType.ARTICLE, SourceType.VIDEO)
                        selectedLanguages = setOf("en", "es")
                        selectedSourceIds = allEnabledSourceIds
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = mc.textSecondary),
                ) {
                    Text("Reset", style = mt.labelMedium)
                }
                Button(
                    onClick = {
                        val resolvedSourceIds = if (selectedSourceIds == allEnabledSourceIds) null
                        else selectedSourceIds
                        onApply(selectedTypes, selectedLanguages, resolvedSourceIds)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mc.primaryAccent,
                        contentColor = mc.background,
                    ),
                ) {
                    Text("Apply", style = mt.labelMedium)
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onToggle: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    FilterChip(
        selected = selected,
        onClick = onToggle,
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

@Composable
private fun LanguageChip(label: String, code: String, selected: Boolean, onToggle: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    FilterChip(
        selected = selected,
        onClick = onToggle,
        label = { Text("$label (${code.uppercase()})", style = mt.labelSmall) },
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

@Composable
private fun SourceChip(label: String, selected: Boolean, onToggle: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography
    FilterChip(
        selected = selected,
        onClick = onToggle,
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

private fun <T> Set<T>.toggle(item: T): Set<T> =
    if (item in this) this - item else this + item
