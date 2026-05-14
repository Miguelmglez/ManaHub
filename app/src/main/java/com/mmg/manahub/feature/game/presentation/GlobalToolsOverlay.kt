package com.mmg.manahub.feature.game.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.MarcellusFontFamily
import com.mmg.manahub.core.ui.theme.coloredShadow
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

// ─────────────────────────────────────────────────────────────────────────────
//  State
// ─────────────────────────────────────────────────────────────────────────────

data class GlobalToolsState(
    val isExpanded: Boolean = false,
    val lastDiceResult: Int? = null,    // null = never rolled
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
    turnNumber: Int,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography

    // ── Pulse animation for central button ──
    val pulseTransition = rememberInfiniteTransition(label = "central_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        // ── Central button — always visible ───────────────────────────────────
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .coloredShadow(
                    color = mc.primaryAccent.copy(alpha = pulseAlpha * 0.6f),
                    blurRadius = 24.dp,
                    borderRadius = 18.dp
                )
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            mc.surface,
                            mc.backgroundSecondary,
                        )
                    )
                )
                .border(1.5.dp, mc.primaryAccent.copy(alpha = pulseAlpha), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle
                ),
        ) {
            Text(
                text = if (state.isExpanded) "×" else "✦",
                style = mt.titleMedium.copy(
                    fontSize = if (state.isExpanded) 20.sp else 14.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = mc.primaryAccent,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = if (state.isExpanded) (-1).dp else 0.dp)
            )
        }

        // ── Expanded panel ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.isExpanded,
            enter = scaleIn(
                tween(300, easing = FastOutSlowInEasing),
                initialScale = 0.8f,
                transformOrigin = TransformOrigin.Center
            ) + fadeIn(tween(250)),
            exit = scaleOut(
                tween(250, easing = FastOutSlowInEasing),
                targetScale = 0.8f,
                transformOrigin = TransformOrigin.Center
            ) + fadeOut(tween(200)),
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = mc.backgroundSecondary.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.35f)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .widthIn(min = 220.dp)
                    .offset(y = (-80).dp)
                    .coloredShadow(
                        color = mc.primaryAccent.copy(alpha = 0.15f),
                        blurRadius = 40.dp,
                        borderRadius = 24.dp
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
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
                        horizontalArrangement = Arrangement.spacedBy(
                            16.dp, Alignment.CenterHorizontally
                        ),
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

                    Spacer(modifier = Modifier.size(4.dp))

                    // ── Primary Actions ───────────────────────────────────────
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ToolsActionRow(
                            icon = Icons.Default.People,
                            label = stringResource(R.string.game_tools_manage_players),
                            onClick = { onManagePlayers(); onToggle() },
                        )

                        if (onTournament != null) {
                            ToolsActionRow(
                                icon = Icons.Default.EmojiEvents,
                                label = stringResource(R.string.game_tools_tournament),
                                onClick = { onTournament(); onToggle() },
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = mc.primaryAccent.copy(alpha = 0.15f),
                    )

                    // ── Game Control Actions ──────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QuickActionCircle(
                            icon = Icons.Default.Pause,
                            label = stringResource(R.string.game_tools_abandon),
                            onClick = { onAbandonGame(); onToggle() },
                        )

                        QuickActionCircle(
                            icon = Icons.Default.Refresh,
                            label = stringResource(R.string.game_tools_reset),
                            onClick = { onReset(); onToggle() },
                        )

                        QuickActionCircle(
                            icon = Icons.Default.ExitToApp,
                            label = stringResource(R.string.game_tools_exit_game),
                            tint = mc.lifeNegative,
                            onClick = { onExitGame(); onToggle() },
                        )
                    }
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
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 12.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.magicTypography.labelMedium,
                    color = tint,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Small circular quick action button for the bottom row.
 */
@Composable
private fun QuickActionCircle(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.magicColors.textPrimary,
) {
    val mc = MaterialTheme.magicColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(mc.surface.copy(alpha = 0.6f))
                .border(1.dp, tint.copy(alpha = 0.3f), CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 10.sp),
            color = tint.copy(alpha = 0.8f)
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
    val mt = MaterialTheme.magicTypography
    val infiniteTransition = rememberInfiniteTransition(label = "dice_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(400, easing = FastOutSlowInEasing)),
        label = "dice_angle",
    )

    val shadowAlpha by animateFloatAsState(
        targetValue = if (isRolling) 0.4f else 0.15f,
        animationSpec = tween<Float>(300),
        label = "dice_shadow"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .coloredShadow(
                color = mc.primaryAccent.copy(alpha = shadowAlpha),
                blurRadius = 16.dp,
                borderRadius = 16.dp
            )
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(mc.surface, mc.backgroundSecondary)
                )
            )
            .border(1.5.dp, mc.primaryAccent.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .graphicsLayer { if (isRolling) rotationZ = spinAngle }
            .clickable(
                enabled = !isRolling,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
    ) {
        if (isRolling) {
            Icon(
                painter = painterResource(R.drawable.ic_dice),
                contentDescription = stringResource(R.string.game_tools_dice_roll_desc),
                tint = mc.primaryAccent.copy(alpha = 0.7f),
                modifier = Modifier.size(40.dp),
            )
        } else {
            if (result != null) {
                Text(
                    text = result.toString(),
                    style = mt.titleLarge.copy(fontSize = 28.sp),
                    color = mc.primaryAccent,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_dice),
                    contentDescription = stringResource(R.string.game_tools_dice_roll_desc),
                    tint = mc.primaryAccent.copy(alpha = 0.5f),
                    modifier = Modifier.size(40.dp),
                )
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
        animationSpec = infiniteRepeatable(tween(250, easing = LinearEasing), RepeatMode.Reverse),
        label = "coin_scale",
    )

    val shadowAlpha by animateFloatAsState(
        targetValue = if (isFlipping) 0.5f else 0.2f,
        animationSpec = tween<Float>(300),
        label = "coin_shadow"
    )

    val bgBrush = if (!isFlipping && result != null) Brush.radialGradient(
        listOf(
            mc.goldMtg,
            mc.goldMtg.copy(alpha = 0.7f)
        )
    )
    else Brush.verticalGradient(listOf(mc.surface, mc.surfaceVariant))

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(72.dp)
            .coloredShadow(
                color = mc.goldMtg.copy(alpha = shadowAlpha),
                blurRadius = 20.dp,
                borderRadius = 36.dp
            )
            .clip(CircleShape)
            .background(bgBrush)
            .border(2.dp, mc.goldMtg.copy(alpha = 0.6f), CircleShape)
            .graphicsLayer { if (isFlipping) scaleX = flipScale }
            .clickable(
                enabled = !isFlipping,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
    ) {
        when {
            isFlipping -> Text("✦", fontSize = 24.sp, color = mc.goldMtg)
            result == true -> {
                Icon(
                    painter = painterResource(R.drawable.ic_heads),
                    contentDescription = stringResource(R.string.game_tools_coin_heads_desc),
                    tint = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(44.dp),
                )
            }

            result == false -> {
                Icon(
                    painter = painterResource(R.drawable.ic_counter),
                    contentDescription = stringResource(R.string.game_tools_coin_tails_desc),
                    tint = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(44.dp),
                )
            }

            else -> {
                Icon(
                    painter = painterResource(R.drawable.ic_coin),
                    contentDescription = stringResource(R.string.game_tools_coin_flip_desc),
                    tint = mc.goldMtg.copy(alpha = 0.8f),
                    modifier = Modifier.size(44.dp),
                )
            }
        }
    }
}
