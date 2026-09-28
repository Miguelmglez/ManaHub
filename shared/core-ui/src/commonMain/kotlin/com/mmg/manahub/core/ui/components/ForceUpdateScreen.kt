package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/** Built-in copy shown when the remote force-update message is blank. */
const val DEFAULT_FORCE_UPDATE_MESSAGE: String =
    "This version of ManaHub is no longer supported. Please update to keep using the app."

/**
 * Full-screen, non-dismissable blocker shown when the running build is below the minimum
 * supported version. Stateless: the caller owns back handling and the update action.
 *
 * @param message remote copy; blank falls back to [DEFAULT_FORCE_UPDATE_MESSAGE].
 * @param onUpdateClick starts the update (in-app flow or store listing).
 */
@Composable
fun ForceUpdateScreen(
    message: String,
    onUpdateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    // Surface consumes pointer input, so nothing underneath is reachable.
    Surface(
        modifier = modifier.fillMaxSize(),
        color = mc.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = spacing.xl, vertical = spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(spacing.xxl * 2),
            )
            Spacer(modifier = Modifier.height(spacing.xl))
            Text(
                text = "Update required",
                style = ty.titleLarge,
                color = mc.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(spacing.md))
            Text(
                text = message.ifBlank { DEFAULT_FORCE_UPDATE_MESSAGE },
                style = ty.bodyMedium,
                color = mc.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(spacing.xxl))
            MagicCtaButton(
                onClick = onUpdateClick,
                text = "Update now",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                    )
                },
            )
        }
    }
}
