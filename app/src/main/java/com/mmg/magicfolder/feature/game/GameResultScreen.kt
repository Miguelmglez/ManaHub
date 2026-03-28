package com.mmg.magicfolder.feature.game

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.game.model.*

@Composable
fun GameResultScreen(
    gameResult: GameResult,
    onNewGame:  () -> Unit,
    onBackHome: () -> Unit,
    onSurvey:   () -> Unit = {},
) {
    val mc          = MaterialTheme.magicColors
    val winnerTheme = gameResult.winner.theme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        winnerTheme.accent.copy(alpha = 0.25f),
                        mc.background,
                    )
                )
            )
    ) {
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { VictoryHeader(gameResult = gameResult, winnerColor = winnerTheme.accent) }
            item { StandingsSection(gameResult = gameResult) }
            item { HighlightsSection(gameResult = gameResult) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick  = onSurvey,
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = mc.goldMtg,
                        ),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, mc.goldMtg),
                    ) {
                        Text(
                            "\u2756 Review this game",
                            style = MaterialTheme.magicTypography.labelLarge,
                        )
                    }
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick  = onBackHome,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Back to Home", style = MaterialTheme.magicTypography.labelLarge)
                        }
                        Button(
                            onClick  = onNewGame,
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.buttonColors(containerColor = winnerTheme.accent),
                        ) {
                            Text(
                                stringResource(R.string.action_play_again),
                                style = MaterialTheme.magicTypography.labelLarge,
                                color = mc.background,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Victory header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VictoryHeader(gameResult: GameResult, winnerColor: Color) {
    val mc = MaterialTheme.magicColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.fillMaxWidth(),
    ) {
        Text(
            text  = "VICTORY",
            style = MaterialTheme.magicTypography.labelLarge,
            color = winnerColor,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text      = gameResult.winner.name,
            style     = MaterialTheme.magicTypography.displayMedium,
            color     = mc.textPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = "${gameResult.winner.life} life remaining",
            style = MaterialTheme.magicTypography.bodyLarge,
            color = mc.lifePositive,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text  = formatDuration(gameResult.durationMs),
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textSecondary,
            )
            Text(
                text  = "Game ended on turn ${gameResult.totalTurns}",
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textSecondary,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Final standings
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StandingsSection(gameResult: GameResult) {
    val mc = MaterialTheme.magicColors

    val ordered = listOf(gameResult.winner) +
        gameResult.playerResults
            .filter { it.player.id != gameResult.winner.id }
            .sortedByDescending { it.finalLife }
            .map { it.player }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text  = "FINAL STANDINGS",
            style = MaterialTheme.magicTypography.labelLarge,
            color = mc.goldMtg,
        )
        ordered.forEachIndexed { index, player ->
            val result = gameResult.playerResults.find { it.player.id == player.id }
            val theme  = player.theme
            StandingRow(
                position    = index + 1,
                player      = player,
                result      = result,
                playerColor = theme.accent,
                gameMode    = gameResult.gameMode,
            )
        }
    }
}

@Composable
private fun StandingRow(
    position:    Int,
    player:      Player,
    result:      PlayerResult?,
    playerColor: Color,
    gameMode:    GameMode,
) {
    val mc = MaterialTheme.magicColors
    Surface(shape = RoundedCornerShape(8.dp), color = mc.surface) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text     = "#$position",
                style    = MaterialTheme.magicTypography.titleMedium,
                color    = playerColor,
                modifier = Modifier.width(32.dp),
            )
            Text(
                text     = player.name,
                style    = MaterialTheme.magicTypography.bodyLarge,
                color    = mc.textPrimary,
                modifier = Modifier.weight(1f),
            )
            val statusText = when (result?.eliminationReason) {
                null                            -> "${result?.finalLife ?: player.life} life"
                EliminationReason.LIFE          -> "0 life"
                EliminationReason.POISON        -> "\u2620 10 poison"
                EliminationReason.COMMANDER_DAMAGE -> "\u2694 21 cmd dmg"
                EliminationReason.CONCEDE       -> "conceded"
            }
            Text(
                text  = statusText,
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textSecondary,
            )
            if (gameMode == GameMode.COMMANDER && result != null) {
                Text(
                    text  = "${result.totalCommanderDamageDealt} cmd",
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.commanderAccent,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Highlights
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HighlightsSection(gameResult: GameResult) {
    val mc       = MaterialTheme.magicColors
    val results  = gameResult.playerResults
    val mostDmg  = results.maxByOrNull { it.totalCommanderDamageDealt }
    val hasPoison = results.any { it.eliminationReason == EliminationReason.POISON }
    val lifeDiffs = results.map { it.finalLife }.sorted()
    val closestGap = if (lifeDiffs.size >= 2) lifeDiffs[1] - lifeDiffs[0] else null

    val hasHighlights = (mostDmg != null && mostDmg.totalCommanderDamageDealt > 0)
        || hasPoison || (closestGap != null && closestGap <= 5)

    if (!hasHighlights) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text  = "HIGHLIGHTS",
            style = MaterialTheme.magicTypography.labelLarge,
            color = mc.goldMtg,
        )
        if (mostDmg != null && mostDmg.totalCommanderDamageDealt > 0) {
            HighlightCard(
                icon  = "\u2694",
                label = "Most damage dealt",
                value = "${mostDmg.player.name} \u2014 ${mostDmg.totalCommanderDamageDealt} cmd dmg",
            )
        }
        if (closestGap != null && closestGap <= 5) {
            HighlightCard(
                icon  = "\uD83C\uDFAF",
                label = "Closest match",
                value = "Life difference of $closestGap",
            )
        }
        if (hasPoison) {
            HighlightCard(
                icon  = "\u2620",
                label = "Poison elimination",
                value = "A player was eliminated by poison counters",
            )
        }
    }
}

@Composable
private fun HighlightCard(icon: String, label: String, value: String) {
    val mc = MaterialTheme.magicColors
    Surface(shape = RoundedCornerShape(8.dp), color = mc.surfaceVariant) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(icon, style = MaterialTheme.magicTypography.titleMedium)
            Column {
                Text(
                    text  = label,
                    style = MaterialTheme.magicTypography.labelMedium,
                    color = mc.textSecondary,
                )
                Text(
                    text  = value,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textPrimary,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
