package com.mmg.manahub.feature.collection.presentation.importexport

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.core.domain.collection.transfer.LegacyImportRecoveryState
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.*
import com.mmg.manahub.core.ui.theme.*
import com.mmg.manahub.feature.collection.data.AndroidTransferDelivery

/** Activity-owned launchers preserve their request while the external picker stops the origin. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionImportHost(
    intake: TransferIntakeViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
    onResume: (String) -> Unit,
) {
    val receiving by intake.receiving.collectAsStateWithLifecycle()
    val ready by intake.pickerReady.collectAsStateWithLifecycle()
    val recovery by intake.recoveryNotice.collectAsStateWithLifecycle()
    val recoveryPicker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let(intake::saveRecovery) }
    var confirmRecovery by remember { mutableStateOf(false) }
    val pending by intake.unfinished.collectAsStateWithLifecycle()
    val error by intake.error.collectAsStateWithLifecycle()
    val toast = rememberMagicToastState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments(), intake::receivePickerResult)
    var paste by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    val intakeError=error?.let { stringResource(intakeFailureResource(it)) }
    LaunchedEffect(intakeError, visible) {
        if (visible) intakeError?.let { toast.show(it, MagicToastType.ERROR); intake.clearError() }
    }
    LaunchedEffect(pending,saved) { if(saved && pending.isEmpty())saved=false }
    LaunchedEffect(visible) { if (!visible) { paste = false; saved = false } }
    if (!visible) return
    if(confirmRecovery) MagicAlertDialog(
        onDismissRequest={confirmRecovery=false},
        title=stringResource(R.string.import_legacy_title),
        text=stringResource(R.string.import_legacy_explanation),
        buttons={ MagicCtaButton(text=stringResource(R.string.import_save_recovery),onClick={confirmRecovery=false;recoveryPicker.launch("legacy-import-recovery.mhrecovery")},style=MagicCtaStyle.Outlined) },
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = BottomSheetShape,
        containerColor = mc.backgroundSecondary,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        sheetGesturesEnabled = false,
        dragHandle = null,
    ) {
        TransferSheetHeader(stringResource(R.string.import_title), stringResource(R.string.import_close), onDismiss)
        Box(Modifier.fillMaxWidth().imePadding()) {
            if(saved) LazyColumn(Modifier.fillMaxWidth().heightIn(max=androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.6f),contentPadding=PaddingValues(horizontal=sp.lg,vertical=sp.md)) {
                    itemsIndexed(pending, key = { _, job -> job.id }) { index, job ->
                        MagicCtaButton(
                            text = stringResource(R.string.import_saved_item, index + 1, job.accepted - job.applied),
                            style = MagicCtaStyle.Outlined,
                            onClick = { onDismiss(); onResume(job.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
            } else Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=sp.lg,vertical=sp.md),verticalArrangement=Arrangement.spacedBy(sp.sm)) {
                if (paste) {
                    run {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text(stringResource(R.string.import_paste)) },
                            placeholder = { Text(stringResource(R.string.import_paste_example)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 4,
                        )
                    }
                    run {
                        MagicCtaButton(
                            text = stringResource(R.string.import_review),
                            enabled = text.isNotBlank() && ready && !receiving,
                            onClick = { intake.receiveText(text); onDismiss() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    run { Text(stringResource(R.string.import_help), color = mc.textSecondary, style = MaterialTheme.magicTypography.bodyMedium) }
                    run {
                        MagicCtaButton(
                            text = stringResource(R.string.import_choose_files),
                            enabled = ready && !receiving,
                            onClick = { if (intake.capturePicker()) picker.launch(AndroidTransferDelivery.mimeTypes) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    run {
                        MagicCtaButton(
                            text = stringResource(R.string.import_paste),
                            enabled = ready && !receiving,
                            style = MagicCtaStyle.Outlined,
                            onClick = { paste = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (receiving || !ready) run {
                        MagicProgressBar()
                        Text(stringResource(if (receiving) R.string.import_receiving else R.string.import_wait_session), color = mc.textSecondary)
                    }
                    if(recovery.state==LegacyImportRecoveryState.RECOVERY_AVAILABLE) run {
                        MagicCtaButton(text=stringResource(R.string.import_save_recovery),style=MagicCtaStyle.Outlined,onClick={confirmRecovery=true},modifier=Modifier.fillMaxWidth())
                    }
                    if (pending.isNotEmpty()) run {
                        MagicCtaButton(
                            text = if (pending.size == 1) stringResource(R.string.import_continue, pending.first().accepted - pending.first().applied)
                                else stringResource(R.string.import_saved_count, pending.size),
                            style = MagicCtaStyle.Outlined,
                            onClick = { if (pending.size == 1) { onDismiss(); onResume(pending.first().id) } else saved = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            MagicToastHost(toast,Modifier.matchParentSize())
        }
    }
}

internal fun intakeFailureResource(failure: com.mmg.manahub.core.domain.collection.transfer.TransferIntakeFailure): Int = when(failure) {
    com.mmg.manahub.core.domain.collection.transfer.TransferIntakeFailure.INVALID_DELIVERY -> R.string.import_failure_invalid_delivery
    com.mmg.manahub.core.domain.collection.transfer.TransferIntakeFailure.TOO_MANY_FILES -> R.string.import_failure_many_files
    com.mmg.manahub.core.domain.collection.transfer.TransferIntakeFailure.RECEIVE -> R.string.import_failure_receive
    com.mmg.manahub.core.domain.collection.transfer.TransferIntakeFailure.RECOVERY -> R.string.import_failure_recovery
}
