package com.mmg.manahub.feature.game

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
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.game.model.*

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
                            stringResource(R.string.gameresult_review_button),
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
                            Text(
                                stringResource(R.string.gameresult_back_home),
                                style = MaterialTheme.magicTypography.labelLarge
                            )
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
            text  = stringResource(R.string.gameresult_winner_label),
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
            text  = stringResource(R.string.gameresult_life_remaining, gameResult.winner.life),
            style = MaterialTheme.magicTypography.bodyLarge,
            color = mc.lifePositive,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text  = stringResource(R.string.gameresult_game_duration, formatDuration(gameResult.durationMs)),
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textSecondary,
            )
            Text(
                text  = stringResource(R.string.gameresult_game_ended_turn, gameResult.totalTurns),
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
            text  = stringResource(R.string.gameresult_standings_title),
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
                null                            -> stringResource(R.string.gameresult_player_life_count, result?.finalLife ?: player.life)
                EliminationReason.LIFE          -> stringResource(R.string.gameresult_elimination_life)
                EliminationReason.POISON        -> stringResource(R.string.gameresult_player_poison_count)
                EliminationReason.COMMANDER_DAMAGE -> stringResource(R.string.gameresult_player_cmd_damage_count)
                EliminationReason.CONCEDE       -> stringResource(R.string.gameresult_player_conceded)
            }
            Text(
                text  = statusText,
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textSecondary,
            )
            if (gameMode == GameMode.COMMANDER && result != null) {
                Text(
                    text  = stringResource(R.string.gameresult_cmd_damage_label, result.totalCommanderDamageDealt),
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
            text  = stringResource(R.string.gameresult_highlights_title),
            style = MaterialTheme.magicTypography.labelLarge,
            color = mc.goldMtg,
        )
        if (mostDmg != null && mostDmg.totalCommanderDamageDealt > 0) {
            HighlightCard(
                icon  = "\u2694",
                label = stringResource(R.string.gameresult_most_damage),
                value = stringResource(
                    R.string.gameresult_most_damage_desc,
                    mostDmg.player.name,
                    mostDmg.totalCommanderDamageDealt
                ),
            )
        }
        if (closestGap != null && closestGap <= 5) {
            HighlightCard(
                icon  = "\uD83C\uDFAF",
                label = stringResource(R.string.gameresult_closest_match),
                value = stringResource(R.string.gameresult_closest_match_desc, closestGap),
            )
        }
        if (hasPoison) {
            HighlightCard(
                icon  = "\u2620",
                label = stringResource(R.string.gameresult_poison_elimination),
                value = stringResource(R.string.gameresult_poison_elimination_desc),
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
