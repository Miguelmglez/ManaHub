package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * A card that prominently displays a 6-digit room code with copy (and optional share) actions.
 *
 * - If [shareText] and [onShare] are provided, the Copy and Share buttons are shown side by side.
 * - Otherwise, only a full-width Copy button is shown.
 *
 * @param code The 6-digit session code to display.
 * @param onCopy Callback invoked when the user taps Copy.
 * @param shareText Optional text to share (e.g. "Join my ManaHub game! Code: 123456").
 * @param onShare Optional callback invoked when the user taps Share.
 * @param modifier Optional modifier applied to the outer [Surface].
 */
@Composable
fun RoomCodeDisplay(
    code: String,
    onCopy: () -> Unit,
    shareText: String? = null,
    onShare: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        color = mc.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.lobby_room_code_label),
                style = ty.labelMedium,
                color = mc.textSecondary,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = code,
                style = ty.lifeNumberMd.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 8.sp,
                ),
                color = mc.primaryAccent,
            )

            Spacer(Modifier.height(16.dp))

            if (shareText != null && onShare != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onCopy,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mc.surfaceVariant,
                            contentColor = mc.primaryAccent,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(text = stringResource(R.string.action_copy), style = ty.labelLarge)
                    }

                    Button(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mc.surfaceVariant,
                            contentColor = mc.textPrimary,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(text = stringResource(R.string.action_share), style = ty.labelLarge)
                    }
                }
            } else {
                Button(
                    onClick = onCopy,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mc.surfaceVariant,
                        contentColor = mc.primaryAccent,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(text = stringResource(R.string.lobby_action_copy_code), style = ty.labelLarge)
                }
            }
        }
    }
}
