package com.mmg.manahub.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * A row control that allows incrementing or decrementing a player count value.
 *
 * Shows a "Players" label on the left and −/count/+ controls on the right.
 * The decrement button is disabled when [playerCount] equals [min];
 * the increment button is disabled when [playerCount] equals [max].
 *
 * @param playerCount The current player count to display.
 * @param onPlayerCountChange Called with the new value when + or − is tapped.
 * @param min Minimum allowed value (inclusive). Defaults to 2.
 * @param max Maximum allowed value (inclusive). Defaults to 6.
 * @param modifier Optional modifier.
 */
@Composable
fun PlayerCountStepper(
    playerCount: Int,
    onPlayerCountChange: (Int) -> Unit,
    min: Int = 2,
    max: Int = 6,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(mc.surface.copy(alpha = 0.5f))
            .padding(12.dp),
    ) {
        Text(
            text = "Players",
            style = ty.titleMedium,
            color = mc.textSecondary,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepperButton(
                text = "−",
                enabled = playerCount > min,
                onClick = { onPlayerCountChange(playerCount - 1) },
            )

            Box(
                modifier = Modifier.widthIn(min = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$playerCount",
                    style = ty.displayMedium,
                    color = mc.primaryAccent,
                    textAlign = TextAlign.Center,
                )
            }

            StepperButton(
                text = "+",
                enabled = playerCount < max,
                onClick = { onPlayerCountChange(playerCount + 1) },
            )
        }
    }
}

/**
 * A circular button used inside [PlayerCountStepper] to increment or decrement the count.
 *
 * @param text The label to display (typically "+" or "−").
 * @param enabled Whether the button can be interacted with.
 * @param onClick Action invoked on tap.
 */
@Composable
private fun StepperButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val bgColor by animateColorAsState(
        targetValue = if (enabled) mc.surfaceVariant else mc.surface.copy(alpha = 0.3f),
        label = "stepperBtnBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (enabled) mc.textPrimary else mc.textDisabled,
        label = "stepperBtnContent",
    )

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = ty.titleLarge,
            color = contentColor,
        )
    }
}
