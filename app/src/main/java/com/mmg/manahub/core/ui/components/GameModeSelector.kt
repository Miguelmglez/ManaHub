package com.mmg.manahub.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.game.domain.model.GameMode

/**
 * A row of pill-cards for selecting a [GameMode].
 *
 * Each pill shows an emoji, the mode's display name, and its starting life total.
 * The selected pill is highlighted with an animated border, background tint, and scale effect.
 *
 * @param modes List of modes to display. Defaults to all [GameMode] entries.
 * @param selectedMode The currently selected mode.
 * @param onModeSelected Callback invoked when the user taps a mode.
 * @param modifier Optional modifier.
 */
@Composable
fun GameModeSelector(
    modes: List<GameMode> = GameMode.entries,
    selectedMode: GameMode,
    onModeSelected: (GameMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        modes.forEach { mode ->
            GameModePill(
                mode = mode,
                selected = mode == selectedMode,
                onClick = { onModeSelected(mode) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * A single pill card representing one [GameMode] option.
 */
@Composable
private fun GameModePill(
    mode: GameMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val scale by animateFloatAsState(
        targetValue = if (selected) 1.05f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "modePillScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) mc.primaryAccent else mc.surfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(durationMillis = 300),
        label = "modePillBorder",
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) mc.primaryAccent.copy(alpha = 0.15f) else mc.surface,
        animationSpec = tween(durationMillis = 300),
        label = "modePillBg",
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                shape = RoundedCornerShape(12.dp)
                clip = true
            }
            .background(bgColor)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = if (mode == GameMode.COMMANDER) "⚔️" else "🔮",
            style = ty.displayMedium,
        )
        Text(
            text = stringResource(mode.displayNameRes),
            style = ty.titleMedium,
            color = if (selected) mc.primaryAccent else mc.textPrimary,
        )
        Text(
            text = "${mode.startingLife} ${stringResource(R.string.gamesetup_mode_standard_life).substringAfter(' ')}",
            style = ty.bodySmall,
            color = mc.textSecondary,
        )
    }
}
