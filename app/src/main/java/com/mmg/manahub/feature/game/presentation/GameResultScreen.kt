package com.mmg.manahub.feature.game.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.game.domain.model.EliminationReason
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.model.GameResult
import com.mmg.manahub.feature.game.domain.model.Player
import com.mmg.manahub.feature.game.domain.model.PlayerResult

@Composable
fun GameResultScreen(
    gameResult: GameResult,
    onNewGame:  () -> Unit,
    onBackHome: () -> Unit,
    onSurvey:   () -> Unit = {},
    /**
     * Room session id of the game just saved (`GameViewModel.uiState.lastSessionId`). Used to
     * correlate the gamification progression outcome for THIS game. Null/0 while the save is still
     * in flight — the strip simply does not appear until the id is known.
     */
    sessionId: Long? = null,
) {
    val mc          = MaterialTheme.magicColors
    val winnerTheme = gameResult.winner.theme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        winnerTheme.accent.copy(alpha = 0.85f),
                        mc.background,
                    )
                )
            )
    ) {
        val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(
                start  = 20.dp,
                end    = 20.dp,
                top    = 24.dp + statusBarTop,
                bottom = 24.dp + navBarBottom,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { VictoryHeader(gameResult = gameResult, winnerColor = winnerTheme.accent) }
            item { StandingsSection(gameResult = gameResult) }
            item { HighlightsSection(gameResult = gameResult) }
            if (sessionId != null && sessionId > 0L) {
                item { GameProgressionStrip(sessionId = sessionId) }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    /*OutlinedButton(
                        onClick  = onSurvey,
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = mc.goldMtg,
                        ),
                        border   = androidx.compose.foundation.BorderStroke(1.dp, mc.goldMtg),
                    ) {
                        Text(
                            stringResource(R.string.gameresult_review_button),
                            style = MaterialTheme.magicTypography.titleMedium,
                        )
                    }*/
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick  = onBackHome,
                            modifier = Modifier.weight(1f),
                            border   = androidx.compose.foundation.BorderStroke(1.dp, mc.secondaryAccent)
                        ) {
                            Text(
                                stringResource(R.string.gameresult_back_home),
                                style = MaterialTheme.magicTypography.titleMedium,
                                color = mc.secondaryAccent
                                )
                        }
                       /* TODO: Re-enable "Play Again" once rematch functionality is implemented
                       Button(
                            onClick  = onNewGame,
                            modifier = Modifier.weight(1f),
                            colors   = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
                        ) {
                            Text(
                                stringResource(R.string.action_play_again),
                                style = MaterialTheme.magicTypography.titleMedium,
                                color = mc.background,
                            )
                        }*/
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
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.fillMaxWidth(),
    ) {
        Text(text = stringResource(R.string.game_crown_symbol), fontSize = 48.sp, textAlign = TextAlign.Center)
        Text(
            text  = stringResource(R.string.gameresult_winner_label),
            style = MaterialTheme.magicTypography.labelLarge,
            color = mc.primaryAccent,
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
                text  = stringResource(R.string.gameresult_game_duration, formatDuration(gameResult.durationMs, context)),
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
        } else {
            if (closestGap != null && closestGap <= 5) {
                HighlightCard(
                    icon = "\uD83C\uDFAF",
                    label = stringResource(R.string.gameresult_closest_match),
                    value = stringResource(R.string.gameresult_closest_match_desc, closestGap),
                )
            }
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
//  Progression strip (ADR-002 §8.3 — gamification Phase 1)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Compact progression strip for the game [sessionId]: the XP breakdown (one line per source), the
 * total XP gained, an optional level-up, and achievement-unlock chips. Driven by
 * [GameResultStripViewModel], which correlates the gamification outcome by session id.
 *
 * The outcome arrives a moment after the screen mounts (the engine processes `GameFinished`
 * asynchronously), so this renders nothing until it does — the result screen is never blocked. When
 * gamification is disabled, the outcome never arrives and the strip stays hidden.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GameProgressionStrip(
    sessionId: Long,
    viewModel: GameResultStripViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(sessionId) { viewModel.observe(sessionId) }
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()

    androidx.compose.animation.AnimatedVisibility(
        visible = outcome != null,
        enter = androidx.compose.animation.fadeIn() +
            androidx.compose.animation.expandVertically(),
    ) {
        val shown = outcome ?: return@AnimatedVisibility
        val mc = MaterialTheme.magicColors

        Surface(shape = CardShape, color = mc.surface) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.progression_strip_title),
                        style = MaterialTheme.magicTypography.labelLarge,
                        color = mc.goldMtg,
                    )
                    if (shown.leveledUp && shown.newLevel != null) {
                        Surface(shape = ChipShape, color = mc.primaryAccent.copy(alpha = 0.20f)) {
                            Text(
                                text = stringResource(R.string.progression_strip_level_up, shown.newLevel),
                                style = MaterialTheme.magicTypography.labelMedium,
                                color = mc.primaryAccent,
                                modifier = Modifier.padding(
                                    horizontal = MaterialTheme.spacing.sm,
                                    vertical = MaterialTheme.spacing.xxs,
                                ),
                            )
                        }
                    }
                }

                // XP breakdown — one line per source bucket.
                shown.breakdown.forEach { line ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = line.label,
                            style = MaterialTheme.magicTypography.bodyMedium,
                            color = mc.textSecondary,
                        )
                        Text(
                            text = stringResource(R.string.progression_strip_line_xp, line.amount),
                            style = MaterialTheme.magicTypography.bodyMedium,
                            color = mc.lifePositive,
                        )
                    }
                }

                // Total XP gained this game.
                if (shown.xpGranted > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.progression_strip_total_label),
                            style = MaterialTheme.magicTypography.titleMedium,
                            color = mc.textPrimary,
                        )
                        Text(
                            text = stringResource(R.string.progression_strip_total_xp, shown.xpGranted),
                            style = MaterialTheme.magicTypography.titleMedium,
                            color = mc.lifePositive,
                        )
                    }
                }

                // Achievement-unlock chips.
                if (shown.achievementUnlocks.isNotEmpty()) {
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                    ) {
                        shown.achievementUnlocks.forEach { unlock ->
                            Surface(
                                shape = ChipShape,
                                color = mc.goldMtg.copy(alpha = 0.16f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = MaterialTheme.spacing.md,
                                        vertical = MaterialTheme.spacing.xs,
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xs),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(text = unlock.emoji, style = MaterialTheme.magicTypography.bodyMedium)
                                    Text(
                                        text = stringResource(unlock.titleRes),
                                        style = MaterialTheme.magicTypography.labelMedium,
                                        color = mc.textPrimary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun formatDuration(ms: Long, context: android.content.Context): String {
    val totalSeconds = ms / 1000
    val hours   = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) context.getString(R.string.game_duration_format_hm, hours, minutes) 
           else context.getString(R.string.game_duration_format_m, minutes)
}
