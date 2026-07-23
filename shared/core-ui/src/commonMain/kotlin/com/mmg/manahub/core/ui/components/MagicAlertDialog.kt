package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * A unified alert dialog for ManaHub.
 *
 * Supports simple text messages or complex custom content, and standard
 * confirm/dismiss buttons or a custom button stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagicAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    text: String? = null,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    dismissLabel: String? = null,
    onDismiss: (() -> Unit)? = null,
    confirmColor: MagicCtaColor = MagicCtaColor.Primary,
    dismissStyle: MagicCtaStyle = MagicCtaStyle.Ghost,
    properties: DialogProperties = DialogProperties(),
    buttons: (@Composable ColumnScope.() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
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

                if (content != null) {
                    content()
                } else if (text != null) {
                    Text(
                        text = text,
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = sp.xl)
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(sp.sm)
                ) {
                    if (buttons != null) {
                        buttons()
                    } else {
                        if (confirmLabel != null && onConfirm != null) {
                            MagicCtaButton(
                                onClick = onConfirm,
                                text = confirmLabel,
                                color = confirmColor,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (dismissLabel != null && onDismiss != null) {
                            MagicCtaButton(
                                onClick = onDismiss,
                                text = dismissLabel,
                                style = dismissStyle,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
