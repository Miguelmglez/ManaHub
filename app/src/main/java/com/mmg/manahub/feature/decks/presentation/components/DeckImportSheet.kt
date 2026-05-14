package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * Bottom sheet where the user can paste a Moxfield/Arena deck list to import.
 *
 * @param isLoading   True while cards are being resolved in the background.
 * @param error       Non-null if the last import finished with an error/warning message.
 * @param onImport    Called with the raw pasted text when the user confirms.
 * @param onDismiss   Called when the sheet is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckImportSheet(
    isLoading: Boolean,
    error: String?,
    onImport:  (text: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    var pastedText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = mc.backgroundSecondary,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text  = stringResource(R.string.deck_import_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
            )

            Text(
                text  = stringResource(R.string.deck_import_hint),
                style = ty.bodySmall,
                color = mc.textSecondary,
            )

            OutlinedTextField(
                value         = pastedText,
                onValueChange = { pastedText = it },
                placeholder   = {
                    Text(
                        text  = stringResource(R.string.deck_import_placeholder),
                        color = mc.textDisabled,
                        style = ty.bodySmall,
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 340.dp),
                colors   = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = mc.primaryAccent,
                    unfocusedBorderColor = mc.surfaceVariant,
                    focusedTextColor     = mc.textPrimary,
                    unfocusedTextColor   = mc.textPrimary,
                    cursorColor          = mc.primaryAccent,
                ),
                textStyle = ty.bodySmall,
                maxLines  = 30,
            )

            // Error / warning banner
            error?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = mc.lifeNegative.copy(alpha = 0.12f),
                ) {
                    Text(
                        text     = msg,
                        style    = ty.bodySmall,
                        color    = mc.lifeNegative,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            if (isLoading) {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        color    = mc.primaryAccent,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text  = stringResource(R.string.deck_import_loading),
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                    )
                }
            } else {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text  = stringResource(R.string.action_cancel),
                            color = mc.textSecondary,
                            style = ty.labelLarge,
                        )
                    }
                    Button(
                        onClick  = { onImport(pastedText) },
                        enabled  = pastedText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor         = mc.primaryAccent,
                            disabledContainerColor = mc.surfaceVariant,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text  = stringResource(R.string.deck_import_button),
                            color = if (pastedText.isNotBlank()) mc.background else mc.textDisabled,
                            style = ty.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
