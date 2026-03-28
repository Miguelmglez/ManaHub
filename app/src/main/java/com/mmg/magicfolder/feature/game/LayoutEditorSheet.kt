package com.mmg.magicfolder.feature.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.game.model.*

// ─────────────────────────────────────────────────────────────────────────────
//  LayoutEditorSheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutEditorSheet(
    players:         List<Player>,
    activeLayout:    LayoutTemplate,
    playerRotations: Map<Int, Int>,
    onSelectLayout:  (LayoutTemplate) -> Unit,
    onRotatePlayer:  (playerId: Int) -> Unit,
    onSwapPositions: (Int, Int) -> Unit,
    onDismiss:       () -> Unit,
) {
    val mc      = MaterialTheme.magicColors
    val layouts = LayoutTemplates.getLayoutsForCount(players.size)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = mc.backgroundSecondary,
    ) {
        Column(
            modifier            = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text  = stringResource(R.string.game_layout_editor_title),
                style = MaterialTheme.magicTypography.titleMedium,
                color = mc.textPrimary,
            )
            Text(
                text  = stringResource(R.string.game_layout_editor_subtitle),
                style = MaterialTheme.magicTypography.bodySmall,
                color = mc.textSecondary,
            )

            // ── Template selector ─────────────────────────────────────────────
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(layouts, key = { it.id }) { template ->
                    LayoutPreviewTile(
                        template   = template,
                        isSelected = template.id == activeLayout.id,
                        onClick    = { onSelectLayout(template) },
                    )
                }
            }

            HorizontalDivider(color = mc.surfaceVariant)

            // ── Per-player rotation ───────────────────────────────────────────
            Text(
                text  = stringResource(R.string.game_layout_editor_rotation),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )
            players.forEach { player ->
                val slotPosition = activeLayout.slots.find { it.playerId == player.id }?.position
                val currentDeg   = playerRotations[player.id] ?: slotPosition.toDefaultDegrees()
                PlayerRotationRow(
                    player     = player,
                    currentDeg = currentDeg,
                    onRotate   = { onRotatePlayer(player.id) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LayoutPreviewTile
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LayoutPreviewTile(
    template:   LayoutTemplate,
    isSelected: Boolean,
    onClick:    () -> Unit,
) {
    val mc          = MaterialTheme.magicColors
    val borderColor = if (isSelected) mc.primaryAccent else mc.surfaceVariant
    val bgColor     = if (isSelected) mc.primaryAccent.copy(alpha = 0.10f) else mc.surface

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier            = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        LayoutMiniPreview(
            template   = template,
            isSelected = isSelected,
            modifier   = Modifier
                .fillMaxWidth()
                .height(60.dp),
        )
        Text(
            text      = template.name,
            fontSize  = 9.sp,
            color     = if (isSelected) mc.primaryAccent else mc.textSecondary,
            textAlign = TextAlign.Center,
            maxLines  = 2,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  LayoutMiniPreview  — Canvas-drawn grid of slots
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LayoutMiniPreview(
    template:   LayoutTemplate,
    isSelected: Boolean,
    modifier:   Modifier = Modifier,
) {
    val mc        = MaterialTheme.magicColors
    val cellColor = if (isSelected) mc.primaryAccent.copy(alpha = 0.40f) else mc.textDisabled.copy(alpha = 0.30f)
    val gapPx     = 2f

    Canvas(modifier = modifier) {
        drawLayoutPreview(template, cellColor, gapPx)
    }
}

private fun DrawScope.drawLayoutPreview(
    template:  LayoutTemplate,
    cellColor: Color,
    gap:       Float,
) {
    val rows     = template.gridRows
    val numRows  = rows.size
    if (numRows == 0) return

    val maxCols  = rows.maxOf { it.size }.coerceAtLeast(1)
    val cellW    = (size.width  - gap * (maxCols - 1)) / maxCols
    val cellH    = (size.height - gap * (numRows  - 1)) / numRows

    rows.forEachIndexed { rowIdx, rowSlots ->
        rowSlots.forEachIndexed { colIdx, slotIndex ->
            if (slotIndex != null) {
                val x = colIdx * (cellW + gap)
                val y = rowIdx * (cellH + gap)
                drawRoundRect(
                    color        = cellColor,
                    topLeft      = Offset(x, y),
                    size         = Size(cellW, cellH),
                    cornerRadius = CornerRadius(3f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  PlayerRotationRow
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlayerRotationRow(
    player:     Player,
    currentDeg: Int,
    onRotate:   () -> Unit,
) {
    val mc    = MaterialTheme.magicColors
    val theme = player.theme

    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier              = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(theme.accent)
        )
        Text(
            text     = player.name,
            style    = MaterialTheme.magicTypography.bodyMedium,
            color    = mc.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text  = "${currentDeg}°",
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.textSecondary,
        )
        TextButton(
            onClick          = onRotate,
            contentPadding   = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text       = stringResource(R.string.game_layout_editor_rotate),
                color      = mc.primaryAccent,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
