package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * Inline error banner, styled as a tinted row. Suitable for embedding inside a
 * scrollable screen without taking over the full viewport.
 *
 * @param message Error text to display.
 * @param retryLabel Label for the optional retry action. Only shown when non-null.
 * @param onRetry Click callback for the retry action.
 * @param modifier Modifier applied to the root [Row].
 */
@Composable
fun InlineErrorState(
    message: String,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(mc.lifeNegative.copy(alpha = 0.15f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = ty.bodySmall,
            color = mc.lifeNegative,
            modifier = Modifier.weight(1f),
        )
        if (retryLabel != null && onRetry != null) {
            Text(
                text = retryLabel,
                style = ty.labelSmall,
                color = mc.primaryAccent,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clickable(onClick = onRetry),
            )
        }
    }
}

/**
 * Full-viewport error state, centered. Suitable for replacing entire screen content
 * when the initial load fails.
 *
 * @param message Error text to display.
 * @param retryLabel Label for the optional retry button.
 * @param onRetry Click callback for the retry button.
 * @param modifier Modifier applied to the root [Box].
 */
@Composable
fun FullErrorState(
    message: String,
    retryLabel: String? = null,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = message,
                style = ty.bodyMedium,
                color = mc.lifeNegative,
                textAlign = TextAlign.Center,
            )
            if (retryLabel != null && onRetry != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                ) {
                    Text(retryLabel)
                }
            }
        }
    }
}
