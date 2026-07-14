package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mmg.manahub.core.ui.theme.ButtonShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    confirmColor: Color = MaterialTheme.magicColors.primaryAccent,
    dismissColor: Color = MaterialTheme.magicColors.textSecondary,
    properties: DialogProperties = DialogProperties(),
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Surface(
            shape = CardShape,
            color = mc.surface,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, mc.surfaceVariant, CardShape)
        ) {
            Column(
                modifier = Modifier.padding(sp.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(bottom = sp.md)
                )

                Text(
                    text = text,
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = sp.xl)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(sp.sm)
                ) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = ButtonShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = confirmColor,
                            contentColor = if (confirmColor == mc.primaryAccent) mc.onAccent else Color.White
                        )
                    ) {
                        Text(
                            text = confirmLabel.uppercase(),
                            style = ty.labelLarge.copy(fontWeight = FontWeight.Bold),
                        )
                    }

                    if (dismissLabel != null && onDismiss != null) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = ButtonShape,
                        ) {
                            Text(
                                text = dismissLabel,
                                style = ty.labelLarge,
                                color = dismissColor
                            )
                        }
                    }
                }
            }
        }
    }
}
