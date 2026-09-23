package com.mmg.manahub.feature.collection.presentation.importexport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.collection.transfer.CollectionFileFormat
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

// Material's minimum touch target and the cap that keeps a long list from filling the dialog;
// neither is a spacing-grid value, so they are named here rather than pushed into Spacing.
private val RowTouchTarget = 56.dp
private val DialogListMaxHeight = 280.dp

/** Overflow sheet of the Cards tab: "Export collection" and "Import to collection". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionTransferActionsSheet(
    canExport: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = mc.backgroundSecondary,
        shape = BottomSheetShape,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = sp.xl)) {
            Text(
                text = stringResource(R.string.collection_transfer_sheet_title),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(horizontal = sp.lg, vertical = sp.sm),
            )
            TransferActionRow(
                icon = Icons.Default.FileUpload,
                title = stringResource(R.string.collection_export_option),
                subtitle = stringResource(
                    if (canExport) R.string.collection_export_option_desc else R.string.collection_export_option_disabled
                ),
                enabled = canExport,
                onClick = onExport,
            )
            TransferActionRow(
                icon = Icons.Default.FileDownload,
                title = stringResource(R.string.collection_import_option),
                subtitle = stringResource(R.string.collection_import_option_desc),
                enabled = true,
                onClick = onImport,
            )
        }
    }
}

@Composable
private fun TransferActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    Surface(
        onClick = onClick,
        enabled = enabled,
        // Deliberately not a MagicColors token: this Surface is a click target, and the sheet
        // behind it supplies the colour.
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().heightIn(min = RowTouchTarget),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = sp.lg, vertical = sp.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.md),
        ) {
            Icon(icon, contentDescription = null, tint = if (enabled) mc.primaryAccent else mc.textDisabled)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = ty.titleMedium, color = if (enabled) mc.textPrimary else mc.textDisabled)
                Text(subtitle, style = ty.bodySmall, color = if (enabled) mc.textSecondary else mc.textDisabled)
            }
        }
    }
}

/** Export sheet: format choice plus "Save to device" / "Share file". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionExportSheet(
    state: CollectionExportUiState,
    onFormatSelected: (CollectionFileFormat) -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = mc.backgroundSecondary,
        shape = BottomSheetShape,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sp.lg).padding(bottom = sp.xl),
            verticalArrangement = Arrangement.spacedBy(sp.sm),
        ) {
            Text(stringResource(R.string.collection_export_title), style = ty.titleLarge, color = mc.textPrimary)
            Text(stringResource(R.string.collection_export_format_label), style = ty.labelLarge, color = mc.textSecondary)
            Column(modifier = Modifier.selectableGroup()) {
                CollectionFileFormat.entries.forEach { format ->
                    val (title, desc) = format.labels()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = RowTouchTarget)
                            .selectable(
                                selected = state.format == format,
                                enabled = !state.isExporting,
                                role = Role.RadioButton,
                                onClick = { onFormatSelected(format) },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(sp.sm),
                    ) {
                        RadioButton(
                            selected = state.format == format,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = mc.primaryAccent,
                                unselectedColor = mc.textSecondary,
                            ),
                        )
                        Column {
                            Text(title, style = ty.bodyLarge, color = mc.textPrimary)
                            Text(desc, style = ty.bodySmall, color = mc.textSecondary)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(sp.md), modifier = Modifier.fillMaxWidth()) {
                MagicCtaButton(
                    onClick = onShare,
                    enabled = !state.isExporting,
                    isLoading = state.inFlightAction == CollectionExportAction.SHARE,
                    text = stringResource(R.string.collection_export_share),
                    style = MagicCtaStyle.Outlined,
                    icon = { Icon(Icons.Default.Share, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                )
                MagicCtaButton(
                    onClick = onSave,
                    enabled = !state.isExporting,
                    isLoading = state.inFlightAction == CollectionExportAction.SAVE,
                    text = stringResource(R.string.collection_export_save),
                    color = MagicCtaColor.Primary,
                    icon = { Icon(Icons.Default.Save, contentDescription = null) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CollectionFileFormat.labels(): Pair<String, String> = when (this) {
    CollectionFileFormat.TEXT ->
        stringResource(R.string.collection_export_format_text) to stringResource(R.string.collection_export_format_text_desc)
    CollectionFileFormat.MOXFIELD_CSV ->
        stringResource(R.string.collection_export_format_moxfield) to stringResource(R.string.collection_export_format_moxfield_desc)
    CollectionFileFormat.MANABOX_CSV ->
        stringResource(R.string.collection_export_format_manabox) to stringResource(R.string.collection_export_format_manabox_desc)
}

/** Resume prompt shown when "Import to collection" finds a pending review queue. */
@Composable
fun CollectionImportResumeDialog(
    queuedCount: Int,
    unresolvedCount: Int,
    onReview: () -> Unit,
    onImportMore: () -> Unit,
    onShowUnresolved: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sp = MaterialTheme.spacing
    MagicAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.collection_import_resume_title),
        text = pluralStringResource(R.plurals.collection_import_resume_text, queuedCount, queuedCount),
        buttons = {
            Column(verticalArrangement = Arrangement.spacedBy(sp.sm), modifier = Modifier.fillMaxWidth()) {
                MagicCtaButton(
                    onClick = onReview,
                    text = stringResource(R.string.collection_import_resume_review),
                    modifier = Modifier.fillMaxWidth(),
                )
                MagicCtaButton(
                    onClick = onImportMore,
                    text = stringResource(R.string.collection_import_resume_import_more),
                    style = MagicCtaStyle.Outlined,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (unresolvedCount > 0) {
                    MagicCtaButton(
                        onClick = onShowUnresolved,
                        text = pluralStringResource(R.plurals.collection_import_show_unresolved, unresolvedCount, unresolvedCount),
                        style = MagicCtaStyle.Ghost,
                        color = MagicCtaColor.Warning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MagicCtaButton(
                    onClick = onDismiss,
                    text = stringResource(R.string.action_cancel),
                    style = MagicCtaStyle.Ghost,
                    color = MagicCtaColor.Neutral,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

/** Lists the import lines no card was found for, with a copy-to-clipboard action. */
@Composable
fun UnresolvedLinesDialog(
    lines: List<String>,
    totalCount: Int,
    onCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    MagicAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.collection_import_unresolved_title),
        content = {
            Text(
                // The list is capped, the count is not: say so instead of reporting the cap.
                if (totalCount > lines.size) {
                    pluralStringResource(
                        R.plurals.collection_import_unresolved_text_capped,
                        totalCount,
                        lines.size,
                        totalCount,
                    )
                } else {
                    pluralStringResource(R.plurals.collection_import_unresolved_text, lines.size, lines.size)
                },
                style = ty.bodyMedium,
                color = mc.textSecondary,
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = DialogListMaxHeight).padding(vertical = sp.sm),
                verticalArrangement = Arrangement.spacedBy(sp.xs),
            ) {
                itemsIndexed(lines, key = { index, _ -> index }) { _, line ->
                    Text(line, style = ty.bodySmall, color = mc.textPrimary)
                }
            }
        },
        buttons = {
            Column(verticalArrangement = Arrangement.spacedBy(sp.sm), modifier = Modifier.fillMaxWidth()) {
                MagicCtaButton(
                    onClick = onCopy,
                    text = stringResource(R.string.collection_import_unresolved_copy),
                    modifier = Modifier.fillMaxWidth(),
                )
                MagicCtaButton(
                    onClick = onDismiss,
                    text = stringResource(R.string.action_close),
                    style = MagicCtaStyle.Ghost,
                    color = MagicCtaColor.Neutral,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
