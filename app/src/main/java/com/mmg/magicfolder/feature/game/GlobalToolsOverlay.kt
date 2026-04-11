package com.mmg.magicfolder.feature.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.ui.theme.MarcellusFontFamily
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

// ─────────────────────────────────────────────────────────────────────────────
//  State
// ─────────────────────────────────────────────────────────────────────────────

data class GlobalToolsState(
    val isExpanded:     Boolean  = false,
    val lastDiceResult: Int?     = null,    // null = never rolled
    val lastCoinResult: Boolean? = null,    // null = never flipped
    val isRollingDice: Boolean = false,
    val isFlippingCoin: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Public overlay composable — state driven from outside (ViewModel)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Floating tools overlay that shows dice and coin tools plus game management actions.
 *
 * @param state          Current tools state (expanded, dice/coin results).
 * @param onToggle       Toggle the expanded/collapsed state.
 * @param onRollDice     Trigger a dice roll animation.
 * @param onFlipCoin     Trigger a coin flip animation.
 * @param onReset        Open the reset-game confirmation dialog.
 * @param onAbandonGame  Abandon the game temporarily and navigate home without resetting.
 * @param onExitGame     Exit and reset the game permanently.
 * @param onManagePlayers Open the manage-players sheet.
 * @param onTournament   Open tournament management. Null means not in a tournament — button hidden.
 */
@Composable
fun GlobalToolsOverlay(
    state: GlobalToolsState,
    onToggle: () -> Unit,
    onRollDice: () -> Unit,
    onFlipCoin: () -> Unit,
    onReset: () -> Unit,
    onAbandonGame: () -> Unit,
    onExitGame: () -> Unit,
    onManagePlayers: () -> Unit,
    onTournament: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        // ── Central button — always visible ───────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            mc.primaryAccent.copy(alpha = 0.35f),
                            mc.primaryAccent.copy(alpha = 0.10f),
                        )
                    )
                )
                .border(1.5.dp, mc.primaryAccent.copy(alpha = 0.60f), CircleShape)
                .clickable(onClick = onToggle),
        ) {
            Text(
                text = "✦",
                fontSize = 13.sp,
                color = mc.primaryAccent,
                fontFamily = MarcellusFontFamily,
                fontWeight = FontWeight.Bold,
            )
        }

        // ── Expanded panel ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.isExpanded,
            enter = scaleIn(
                tween(250),
                initialScale = 0.3f,
                transformOrigin = TransformOrigin.Center
            ) + fadeIn(tween(200)),
            exit = scaleOut(tween(200), targetScale = 0.3f) + fadeOut(tween(150)),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = mc.backgroundSecondary,
                border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.30f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .widthIn(min = 200.dp)
                    .offset(y = (-72).dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // ── Close button (top-right) ──────────────────────────────
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.game_tools_close_desc),
                            tint = mc.primaryAccent.copy(alpha = 0.50f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onToggle,
                                )
                        )
                    }

                    // ── Dice + Coin row ───────────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedDice(
                            result = state.lastDiceResult,
                            isRolling = state.isRollingDice,
                            onClick = onRollDice,
                        )
                        AnimatedCoin(
                            result = state.lastCoinResult,
                            isFlipping = state.isFlippingCoin,
                            onClick = onFlipCoin,
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = mc.primaryAccent.copy(alpha = 0.20f),
                    )

                    // ── Reset game ────────────────────────────────────────────
                    ToolsActionRow(
                        icon = Icons.Default.Refresh,
                        label = stringResource(R.string.game_tools_reset),
                        onClick = onReset,
                    )

                    // ── Abandonar temporalmente ───────────────────────────────
                    ToolsActionRow(
                        icon = Icons.Default.Pause,
                        label = stringResource(R.string.game_tools_abandon),
                        onClick = { onAbandonGame(); onToggle() },
                    )

                    // ── Exit game (permanent) ─────────────────────────────────
                    ToolsActionRow(
                        icon = Icons.Default.ExitToApp,
                        label = stringResource(R.string.game_tools_exit_game),
                        tint = mc.lifeNegative,
                        onClick = { onExitGame(); onToggle() },
                    )

                    // ── Manage tournament (only if inside a tournament) ───────
                    if (onTournament != null) {
                        ToolsActionRow(
                            icon = Icons.Default.EmojiEvents,
                            label = stringResource(R.string.game_tools_tournament),
                            onClick = onTournament,
                        )
                    }

                    // ── Manage players ────────────────────────────────────────
                    ToolsActionRow(
                        icon = Icons.Default.People,
                        label = stringResource(R.string.game_tools_manage_players),
                        onClick = { onManagePlayers(); onToggle() },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Private action row composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Single tappable action row inside the tools panel.
 *
 * @param icon    Vector icon shown to the left.
 * @param label   Text label shown next to the icon.
 * @param onClick Action to execute when the row is tapped.
 * @param tint    Color applied to both the icon and the label text. Defaults to [MagicColors.textPrimary].
 */
@Composable
private fun ToolsActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.magicColors.textPrimary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.magicTypography.labelMedium,
            color = tint,
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
//  AnimatedDice
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnimatedDice(
    result: Int?,
    isRolling: Boolean,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val infiniteTransition = rememberInfiniteTransition(label = "dice_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing)),
        label = "dice_angle",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .border(1.5.dp, mc.primaryAccent.copy(alpha = 0.50f), RoundedCornerShape(12.dp))
            .graphicsLayer { if (isRolling) rotationZ = spinAngle }
            .clickable(enabled = !isRolling,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick),
    ) {
        if (isRolling) {
            Icon(
                painter = painterResource(R.drawable.ic_dice),
                contentDescription = stringResource(R.string.game_tools_dice_roll_desc),
                tint = mc.primaryAccent.copy(alpha = 0.50f),
                modifier = Modifier.size(42.dp),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (result != null) {
                    Text(
                        text = result.toString(),
                        fontSize = 24.sp,
                        color = mc.primaryAccent,
                        letterSpacing = 1.sp
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_dice),
                        contentDescription = stringResource(R.string.game_tools_dice_roll_desc),
                        tint = mc.primaryAccent.copy(alpha = 0.50f),
                        modifier = Modifier.size(42.dp),
                    )
                }
            }
        }
    }

}

// ─────────────────────────────────────────────────────────────────────────────
//  AnimatedCoin
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnimatedCoin(
    result: Boolean?,
    isFlipping: Boolean,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val infiniteTransition = rememberInfiniteTransition(label = "coin_flip")
    val flipScale by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(200, easing = LinearEasing), RepeatMode.Reverse),
        label = "coin_scale",
    )

    val bgBrush = if (!isFlipping && result != null)
        Brush.radialGradient(listOf(mc.goldMtg, mc.goldMtg.copy(alpha = 0.60f)))
    else
        Brush.radialGradient(listOf(mc.surface, mc.surfaceVariant))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(bgBrush)
            .border(2.dp, mc.goldMtg.copy(alpha = 0.70f), CircleShape)
            .graphicsLayer { if (isFlipping) scaleX = flipScale }
            .clickable(enabled = !isFlipping,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick),
    ) {
        when {
            isFlipping -> Text("✦", fontSize = 22.sp, color = mc.goldMtg)
            result == true -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_heads),
                    contentDescription = stringResource(R.string.game_tools_coin_heads_desc),
                    tint = mc.textDisabled.copy(alpha = 0.50f),
                    modifier = Modifier.size(42.dp),
                )
            }

            result == false -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_counter),
                    contentDescription = stringResource(R.string.game_tools_coin_tails_desc),
                    tint = mc.textDisabled.copy(alpha = 0.50f),
                    modifier = Modifier.size(42.dp),
                )
            }

            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = painterResource(R.drawable.ic_coin),
                    contentDescription = stringResource(R.string.game_tools_coin_flip_desc),
                    tint = mc.goldMtg.copy(alpha = 0.50f),
                    modifier = Modifier.size(42.dp),
                )
            }
        }
    }
}
