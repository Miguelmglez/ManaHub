package com.mmg.magicfolder.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.game.model.GameMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameSetupScreen(
    onBack:      () -> Unit,
    onStartGame: (GameMode, Int) -> Unit,
) {
    var selectedMode  by remember { mutableStateOf(GameMode.COMMANDER) }
    var playerCount   by remember { mutableIntStateOf(4) }
    val mc = MaterialTheme.magicColors

    Scaffold(
        containerColor = mc.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "New Game",
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = mc.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement   = Arrangement.spacedBy(32.dp),
            horizontalAlignment   = Alignment.CenterHorizontally,
        ) {
            // ── Title ─────────────────────────────────────────────────────────
            Text(
                text      = "Æther Tracker",
                style     = MaterialTheme.magicTypography.displayMedium,
                color     = mc.goldMtg,
                textAlign = TextAlign.Center,
            )

            // ── Mode selector ─────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Game Mode",
                    style = MaterialTheme.magicTypography.labelLarge,
                    color = mc.textSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GameMode.entries.forEach { mode ->
                        ModeTile(
                            mode     = mode,
                            selected = mode == selectedMode,
                            onClick  = { selectedMode = mode },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            // ── Player count ──────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Players",
                    style = MaterialTheme.magicTypography.labelLarge,
                    color = mc.textSecondary,
                )
                PlayerCountGrid(
                    selected  = playerCount,
                    onSelect  = { playerCount = it },
                )
            }

            // ── Color preview ─────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val colors = MaterialTheme.magicColors.playerColors
                repeat(playerCount) { i ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(colors[i % colors.size].accent)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Begin button ──────────────────────────────────────────────────
            Button(
                onClick  = { onStartGame(selectedMode, playerCount) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                shape    = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "Begin the Game",
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.background,
                )
            }
        }
    }
}

@Composable
private fun ModeTile(
    mode:     GameMode,
    selected: Boolean,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc         = MaterialTheme.magicColors
    val borderColor = if (selected) mc.primaryAccent else mc.surfaceVariant
    val bgColor     = if (selected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (selected) 1.5.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text  = if (mode == GameMode.COMMANDER) "⚔" else "🔮",
            style = MaterialTheme.magicTypography.displayMedium,
        )
        Text(
            text  = mode.displayName,
            style = MaterialTheme.magicTypography.titleMedium,
            color = if (selected) mc.primaryAccent else mc.textPrimary,
        )
        Text(
            text  = "${mode.startingLife} life",
            style = MaterialTheme.magicTypography.bodySmall,
            color = mc.textSecondary,
        )
    }
}

@Composable
private fun PlayerCountGrid(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (2..10).forEach { count ->
            val isSelected = count == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelected) mc.primaryAccent.copy(alpha = 0.18f)
                        else mc.surface
                    )
                    .border(
                        width = if (isSelected) 1.dp else 0.5.dp,
                        color = if (isSelected) mc.primaryAccent else mc.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelect(count) },
            ) {
                Text(
                    text  = count.toString(),
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = if (isSelected) mc.primaryAccent else mc.textSecondary,
                )
            }
        }
    }
}
