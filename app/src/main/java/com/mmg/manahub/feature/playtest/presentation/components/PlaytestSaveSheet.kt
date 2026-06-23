package com.mmg.manahub.feature.playtest.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.model.HandSnapshot
import com.mmg.manahub.core.model.PlaytestSetup

/**
 * Bottom sheet shown when the user taps "Keep" (with 0 mulligans) or confirms bottom-N.
 *
 * Presents three actions:
 *   1. Save without survey — persists and navigates back.
 *   2. Save + Add survey — persists and opens the survey sheet.
 *   3. Discard — no persistence, dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaytestSaveSheet(
    setup: PlaytestSetup,
    snapshot: HandSnapshot,
    isSaving: Boolean,
    onSaveWithoutSurvey: () -> Unit,
    onSaveAndSurvey: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = mc.backgroundSecondary,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text  = stringResource(R.string.playtest_save_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
            )

            // Test summary.
            Text(
                text  = stringResource(
                    R.string.playtest_save_summary,
                    setup.deckName,
                    snapshot.hand.size,
                    snapshot.mulligansUsed,
                ),
                style = ty.bodyMedium,
                color = mc.textSecondary,
            )

            Spacer(Modifier.height(4.dp))

            // Save without survey.
            Button(
                onClick  = onSaveWithoutSurvey,
                enabled  = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = mc.primaryAccent,
                    disabledContainerColor = mc.primaryAccent.copy(alpha = 0.4f),
                ),
            ) {
                Text(
                    text  = stringResource(R.string.playtest_save_without_survey),
                    style = ty.labelLarge,
                    color = mc.background,
                )
            }

            // Save + survey.
            OutlinedButton(
                onClick  = onSaveAndSurvey,
                enabled  = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = mc.primaryAccent),
            ) {
                Text(
                    text  = stringResource(R.string.playtest_save_and_survey),
                    style = ty.labelLarge,
                )
            }

            // Discard.
            TextButton(
                onClick  = onDiscard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text  = stringResource(R.string.playtest_discard),
                    style = ty.labelLarge,
                    color = mc.textDisabled,
                )
            }
        }
    }
}
