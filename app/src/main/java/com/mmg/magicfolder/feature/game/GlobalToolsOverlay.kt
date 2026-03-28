package com.mmg.magicfolder.feature.game

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────────────
//  State
// ─────────────────────────────────────────────────────────────────────────────

data class GlobalToolsState(
    val expanded:    Boolean  = false,
    val diceResult:  Int?     = null,   // last d20 roll, null = never rolled
    val coinResult:  Boolean? = null,   // last flip, null = never flipped
    val diceSpinning: Boolean = false,
    val coinFlipping: Boolean = false,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Public overlay composable
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GlobalToolsOverlay(
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf(GlobalToolsState()) }
    val scope = rememberCoroutineScope()

    Box(
        contentAlignment = Alignment.Center,
        modifier         = modifier,
    ) {
        if (state.expanded) {
            // Dimmed backdrop to dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.40f))
                    .clickable { state = state.copy(expanded = false) }
            )
        }

        // Floating panel
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.expanded) {
                // ── Dice (d20) ────────────────────────────────────────────
                AnimatedDice(
                    result   = state.diceResult,
                    spinning = state.diceSpinning,
                    onClick  = {
                        if (!state.diceSpinning) {
                            state = state.copy(diceSpinning = true)
                            scope.launch {
                                delay(600L)
                                val roll = (1..20).random()
                                state = state.copy(diceResult = roll, diceSpinning = false)
                            }
                        }
                    },
                )

                // ── Coin ──────────────────────────────────────────────────
                AnimatedCoin(
                    result   = state.coinResult,
                    flipping = state.coinFlipping,
                    onClick  = {
                        if (!state.coinFlipping) {
                            state = state.copy(coinFlipping = true)
                            scope.launch {
                                delay(600L)
                                val heads = listOf(true, false).random()
                                state = state.copy(coinResult = heads, coinFlipping = false)
                            }
                        }
                    },
                )
            }

            // ── Central ✦ toggle button ───────────────────────────────────
            CentralButton(
                expanded = state.expanded,
                onClick  = { state = state.copy(expanded = !state.expanded) },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Central toggle button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CentralButton(expanded: Boolean, onClick: () -> Unit) {
    val mc       = MaterialTheme.magicColors
    val rotation by animateFloatAsState(
        targetValue   = if (expanded) 45f else 0f,
        animationSpec = tween(200),
        label         = "central_rotation",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(mc.surface)
            .clickable(onClick = onClick)
            .graphicsLayer { rotationZ = rotation },
    ) {
        Text(
            text       = "✦",
            fontSize   = 18.sp,
            color      = mc.goldMtg,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AnimatedDice
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedDice(
    result:  Int?,
    spinning: Boolean,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    val infiniteTransition = rememberInfiniteTransition(label = "dice_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue   = 0f,
        targetValue    = 360f,
        animationSpec  = infiniteRepeatable(tween(400, easing = LinearEasing)),
        label          = "dice_angle",
    )

    val isNat20    = result == 20
    val isCritFail = result == 1
    val resultColor = when {
        isNat20    -> mc.goldMtg
        isCritFail -> mc.lifeNegative
        else       -> mc.textPrimary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .clickable(onClick = onClick)
            .graphicsLayer { if (spinning) rotationZ = spinAngle },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = if (spinning) "⬡" else (result?.toString() ?: "d20"),
                fontSize   = if (result != null && !spinning) 22.sp else 14.sp,
                color      = if (spinning) mc.textSecondary else resultColor,
                fontWeight = if (isNat20 || isCritFail) FontWeight.Black else FontWeight.Normal,
            )
            if (!spinning && result != null) {
                Text(
                    text     = when {
                        isNat20    -> "NAT 20!"
                        isCritFail -> "CRIT FAIL"
                        else       -> ""
                    },
                    fontSize = 8.sp,
                    color    = resultColor,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AnimatedCoin
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnimatedCoin(
    result:   Boolean?,
    flipping: Boolean,
    onClick:  () -> Unit,
) {
    val mc = MaterialTheme.magicColors

    val infiniteTransition = rememberInfiniteTransition(label = "coin_flip")
    val scaleX by infiniteTransition.animateFloat(
        initialValue  = 1f,
        targetValue   = -1f,
        animationSpec = infiniteRepeatable(
            tween(300, easing = LinearEasing),
            RepeatMode.Reverse,
        ),
        label = "coin_scale",
    )

    val bgColor = when (result) {
        true  -> mc.goldMtg.copy(alpha = 0.25f)
        false -> mc.surface
        null  -> mc.surface
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick)
            .graphicsLayer { if (flipping) scaleX = this@graphicsLayer.scaleX * scaleX },
    ) {
        Text(
            text       = when {
                flipping      -> "●"
                result == true  -> "H"
                result == false -> "T"
                else            -> "¢"
            },
            fontSize   = if (!flipping && result != null) 22.sp else 18.sp,
            color      = if (result == true && !flipping) mc.goldMtg else mc.textPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}
