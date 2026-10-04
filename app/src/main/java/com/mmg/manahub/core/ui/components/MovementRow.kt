package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * Represents a single action item in a [MovementRow].
 */
data class MovementAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
    val tint: Color? = null,
    val enabled: Boolean = true,
    val contentDescription: String? = null,
)

/**
 * A row of actions (typically 1-3 items) for moving or performing queue actions on a card.
 */
@Composable
fun MovementRow(
    actions: List<MovementAction>,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.sm),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            val effectiveTint = if (action.enabled) {
                action.tint ?: mc.primaryAccent
            } else {
                mc.textDisabled
            }
            val cd = action.contentDescription
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(ChipShape)
                    .clickable(enabled = action.enabled, onClick = action.onClick)
                    .then(
                        if (cd != null) {
                            Modifier.semantics {
                                contentDescription = cd
                            }
                        } else Modifier
                    )
                    .padding(vertical = spacing.sm, horizontal = spacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = effectiveTint,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = action.label,
                    style = ty.labelSmall.copy(fontSize = 9.sp),
                    color = effectiveTint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

/**
 * Backward-compatible overload matching the original MovementRow implementation for DeckStudioScreen.
 */
@Composable
fun MovementRow(
    labelTo: String,
    onMoveTo: () -> Unit,
    modifier: Modifier = Modifier,
    labelFrom: String? = null,
    onMoveFrom: (() -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = spacing.md, end = spacing.md, top = spacing.xxs, bottom = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left Action: Move To [Other]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(ChipShape)
                .clickable(onClick = onMoveTo)
                .padding(vertical = spacing.xs, horizontal = spacing.xs)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.CompareArrows,
                null,
                tint = mc.primaryAccent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(spacing.xs))
            Text(labelTo, style = ty.labelMedium, color = mc.primaryAccent)
        }

        // Right Action: Move From [Other] (if applicable)
        if (labelFrom != null && onMoveFrom != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(ChipShape)
                    .clickable(onClick = onMoveFrom)
                    .padding(vertical = spacing.xs, horizontal = spacing.xs)
            ) {
                Text(labelFrom, style = ty.labelMedium, color = mc.secondaryAccent)
                Spacer(Modifier.width(spacing.xs))
                Icon(
                    Icons.AutoMirrored.Filled.CompareArrows,
                    null,
                    tint = mc.secondaryAccent,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationY = 180f }
                )
            }
        }
    }
}
