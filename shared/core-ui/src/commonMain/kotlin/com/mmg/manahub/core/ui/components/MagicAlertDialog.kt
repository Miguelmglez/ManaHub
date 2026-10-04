package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
    scrollableBodyAndActions: Boolean = false,
    scrollableContent: Boolean = false,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    BasicAlertDialog(onDismissRequest = onDismissRequest, properties = properties) {
        BoxWithConstraints {
            val hasScrollableContent = scrollableContent && content != null
            val hasScrollableBodyAndActions = scrollableBodyAndActions
            val hasAdaptiveContent = hasScrollableContent || hasScrollableBodyAndActions
            Surface(
                shape = CardShape,
                color = mc.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (hasAdaptiveContent) Modifier.heightIn(max = maxHeight * 0.9f) else Modifier)
                    .border(1.dp, mc.surfaceVariant, CardShape),
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
                        modifier = Modifier.padding(bottom = sp.md),
                    )

                    if (hasScrollableBodyAndActions) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(sp.xl),
                        ) {
                            DialogBody(text, content, ty, mc)
                            DialogActions(
                                buttons = buttons,
                                confirmLabel = confirmLabel,
                                onConfirm = onConfirm,
                                confirmColor = confirmColor,
                                dismissLabel = dismissLabel,
                                onDismiss = onDismiss,
                                dismissStyle = dismissStyle,
                                spacing = sp,
                            )
                        }
                    } else {
                        if (hasScrollableContent) {
                            Column(
                                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                                content = content,
                            )
                        } else {
                            DialogBody(text, content, ty, mc)
                        }
                        DialogActions(
                            buttons = buttons,
                            confirmLabel = confirmLabel,
                            onConfirm = onConfirm,
                            confirmColor = confirmColor,
                            dismissLabel = dismissLabel,
                            onDismiss = onDismiss,
                            dismissStyle = dismissStyle,
                            spacing = sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.DialogBody(
    text: String?,
    content: (@Composable ColumnScope.() -> Unit)?,
    typography: com.mmg.manahub.core.ui.theme.MagicTypography,
    colors: com.mmg.manahub.core.ui.theme.MagicColors,
) {
    if (content != null) {
        content()
    } else if (text != null) {
        Text(
            text = text,
            style = typography.bodyMedium,
            color = colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.xl),
        )
    }
}

@Composable
private fun ColumnScope.DialogActions(
    buttons: (@Composable ColumnScope.() -> Unit)?,
    confirmLabel: String?,
    onConfirm: (() -> Unit)?,
    confirmColor: MagicCtaColor,
    dismissLabel: String?,
    onDismiss: (() -> Unit)?,
    dismissStyle: MagicCtaStyle,
    spacing: com.mmg.manahub.core.ui.theme.Spacing,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        if (buttons != null) {
            buttons()
        } else {
            if (confirmLabel != null && onConfirm != null) {
                MagicCtaButton(
                    onClick = onConfirm,
                    text = confirmLabel,
                    color = confirmColor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (dismissLabel != null && onDismiss != null) {
                MagicCtaButton(
                    onClick = onDismiss,
                    text = dismissLabel,
                    style = dismissStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
