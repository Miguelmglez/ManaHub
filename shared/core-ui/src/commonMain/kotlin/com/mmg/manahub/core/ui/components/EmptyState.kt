package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * Generic empty-state composable used across screens.
 *
 * By default the root Column fills all available space. Pass a custom [modifier]
 * (e.g. [Modifier.fillMaxWidth]) to constrain it inside a bottom sheet or card.
 *
 * @param title Primary label shown in the empty state.
 * @param subtitle Optional secondary label below the title.
 * @param icon Optional icon displayed above the title.
 * @param actionLabel Label for the optional action button.
 * @param onAction Click callback for the action button. Button is only shown when non-null.
 * @param modifier Modifier applied to the root [Column]. Defaults to [Modifier.fillMaxSize].
 */
@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = mc.textDisabled,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
        }

        Text(
            text = title,
            style = ty.titleMedium,
            color = mc.textSecondary,
            textAlign = TextAlign.Center,
        )

        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = ty.bodySmall,
                color = mc.textDisabled,
                textAlign = TextAlign.Center,
            )
        }

        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(actionLabel, color = mc.background)
            }
        }
    }
}
