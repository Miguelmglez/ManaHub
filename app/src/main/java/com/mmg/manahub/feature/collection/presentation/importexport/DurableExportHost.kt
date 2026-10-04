package com.mmg.manahub.feature.collection.presentation.importexport

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.core.domain.collection.transfer.*
import com.mmg.manahub.core.ui.components.*
import com.mmg.manahub.core.ui.theme.*
import org.koin.androidx.compose.koinViewModel

/** The existing format controls now initiate one durable job before any external destination opens. */
@Composable
fun DurableExportHost(
    state: CollectionExportUiState,
    query: CollectionSelectionQuery,
    isResumed: Boolean,
    toast: MagicToastState,
    onFormat: (CollectionFileFormat)->Unit,
    onDismiss: ()->Unit,
    viewModel: DurableExportViewModel=koinViewModel(),
) {
    val observed by viewModel.state.collectAsStateWithLifecycle()
    val export=observed.takeIf { viewModel.isCurrentOwner() } ?: DurableExportUiState()
    val context=LocalContext.current
    val saveText=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { viewModel.save(it?.toString()) }
    val saveCsv=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { viewModel.save(it?.toString()) }
    val report=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { uri -> viewModel.report(uri.toString()) } }
    LaunchedEffect(export.requestSave,isResumed,state.isSheetVisible) {
        val value=export.value ?: return@LaunchedEffect
        if(export.requestSave && (!isResumed || !state.isSheetVisible)) {
            viewModel.cancelPickerRequest();return@LaunchedEffect
        }
        if(export.requestSave && isResumed && viewModel.isCurrentOwner()) {
            viewModel.pickerLaunched()
            val picker=if(value.format==CollectionFileFormat.TEXT)saveText else saveCsv
            picker.launch("manahub-export.${value.format.fileExtension}")
        }
    }
    LaunchedEffect(export.share,isResumed,state.isSheetVisible) {
        val share=export.share ?: return@LaunchedEffect
        if(!isResumed || !state.isSheetVisible) { viewModel.shared(cancelled=true);return@LaunchedEffect }
        if(!viewModel.canLaunchShare())return@LaunchedEffect
        val uri=Uri.parse(share.location)
        val intent=Intent(Intent.ACTION_SEND).apply { type=share.mimeType;putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newRawUri(null,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        if(viewModel.canLaunchShare()) {
            runCatching { context.startActivity(Intent.createChooser(intent,"Share collection export").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                .onFailure { viewModel.shareChooserFailed();toast.show("No share target is available. The frozen export is retained.",MagicToastType.ERROR) }
            viewModel.shared()
        }
    }
    LaunchedEffect(export.notice) { export.notice?.let { toast.show(it,MagicToastType.WARNING);viewModel.clearNotice() } }
    if(state.isSheetVisible && isResumed) {
        CollectionExportSheet(state.copy(isExporting=export.busy,inFlightAction=if(export.busy)export.value?.target?.let { if(it==CollectionExportTarget.SAVE)CollectionExportAction.SAVE else CollectionExportAction.SHARE } else null),onFormat,
            onSave={viewModel.begin(query,state.format,CollectionExportTarget.SAVE)},
            onShare={viewModel.begin(query,state.format,CollectionExportTarget.SHARE)},onDismiss=onDismiss,
            additionalContent={
                val colors=MaterialTheme.magicColors
                val type=MaterialTheme.magicTypography
                val spacing=MaterialTheme.spacing
                val actionHeight=spacing.xxl+spacing.lg
                ExportInfoCard {
                    Text(
                        "Exports keep the original filtered selection and every printing variant. Purchase, binder and other external fields are not retained. Shared files may be removed by Android cache eviction; recent attachments are kept for up to 24 hours.",
                        style=type.bodySmall,
                        color=colors.textSecondary,
                    )
                }
                when(state.format) {
                    CollectionFileFormat.TEXT -> ExportInfoCard {
                        Text(
                            "TXT loses condition and language. Names with line breaks cannot round-trip through TXT; use ManaBox CSV for represented fields.",
                            style=type.bodySmall,
                            color=colors.textSecondary,
                        )
                    }
                    CollectionFileFormat.MOXFIELD_CSV -> ExportInfoCard {
                        Text(
                            "Moxfield uses a coarser condition scale and may resolve a different printing. ManaBox CSV retains the represented printing and attributes.",
                            style=type.bodySmall,
                            color=colors.textSecondary,
                        )
                    }
                    CollectionFileFormat.MANABOX_CSV -> Unit
                }
                export.value?.let { value ->
                    val phaseLabel=when(value.phase) { CollectionExportPhase.READY -> "Private file ready";CollectionExportPhase.SAVED -> "Destination closed successfully";CollectionExportPhase.SHARED -> "Attachment ready to share";else -> value.phase.name }
                    ExportInfoCard {
                        Text("Frozen ${value.format.name} export",style=type.titleMedium,color=colors.textPrimary)
                        Text(phaseLabel,style=type.bodyMedium,color=colors.textPrimary)
                        Text("${value.rows} rows / ${value.copies} copies available; ${value.omittedRows} source rows / ${value.omittedCopies} copies omitted.",style=type.bodySmall,color=colors.textSecondary)
                        Text("Missing metadata can leave filtered membership unknown.",style=type.bodySmall,color=colors.textSecondary)
                        if(export.busy)MagicProgressBar()
                        if(value.partialDestination) {
                            ExportInfoCard(warning=true) {
                                Text("The previous destination may contain an incomplete copy. Choose a new destination for this same frozen export.",style=type.bodySmall,color=colors.lifeNegative)
                            }
                        }
                        Text("Retry uses the same frozen selection.",style=type.bodySmall,color=colors.textSecondary)
                        MagicCtaButton(onClick=viewModel::retry,text="Retry",enabled=!export.busy,style=MagicCtaStyle.Outlined,modifier=Modifier.fillMaxWidth().heightIn(min=actionHeight))
                    }
                    if(value.phase==CollectionExportPhase.NEEDS_METADATA) {
                        ExportInfoCard {
                            Text("Choose Omit to export only available rows with the missing rows declared in the full report.",style=type.bodySmall,color=colors.textSecondary)
                            MagicCtaButton(onClick=viewModel::availableOnly,text="Omit",enabled=!export.busy,style=MagicCtaStyle.Outlined,modifier=Modifier.fillMaxWidth().heightIn(min=actionHeight))
                        }
                    }
                    if(value.omittedRows>0L) {
                        ExportInfoCard {
                            Text("Report saves every omitted source row and its original attributes.",style=type.bodySmall,color=colors.textSecondary)
                            MagicCtaButton(onClick={viewModel.reportRequested();report.launch("manahub-export-omissions.csv")},text="Report",enabled=!export.busy,style=MagicCtaStyle.Ghost,modifier=Modifier.fillMaxWidth().heightIn(min=actionHeight))
                        }
                    }
                }
            })
    }
}

@Composable
private fun ExportInfoCard(
    warning: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors=MaterialTheme.magicColors
    val spacing=MaterialTheme.spacing
    Surface(
        modifier=Modifier.fillMaxWidth(),
        shape=CardShape,
        color=if(warning)colors.lifeNegative.copy(alpha=0.10f) else colors.surface,
        border=BorderStroke(1.dp,if(warning)colors.lifeNegative.copy(alpha=0.35f) else colors.surfaceVariant.copy(alpha=0.65f)),
    ) {
        Column(
            modifier=Modifier.padding(spacing.md),
            verticalArrangement=Arrangement.spacedBy(spacing.sm),
            content=content,
        )
    }
}


