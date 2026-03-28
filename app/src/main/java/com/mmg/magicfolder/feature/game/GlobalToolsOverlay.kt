package com.mmg.magicfolder.feature.game

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.magicfolder.core.ui.theme.CinzelFontFamily
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

// ─────────────────────────────────────────────────────────────────────────────
//  State
// ─────────────────────────────────────────────────────────────────────────────

data class GlobalToolsState(
    val isExpanded:     Boolean  = false,
    val lastDiceResult: Int?     = null,    // null = never rolled
    val lastCoinResult: Boolean? = null,    // null = never flipped
    val isRollingDice:  Boolean  = false,
    val isFlippingCoin: Boolean  = false,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Public overlay composable — state driven from outside (ViewModel)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GlobalToolsOverlay(
    state:       GlobalToolsState,
    onToggle:    () -> Unit,
    onRollDice:  () -> Unit,
    onFlipCoin:  () -> Unit,
    modifier:    Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors

    Box(
        contentAlignment = Alignment.Center,
        modifier         = modifier,
    ) {
        // ── Central button — always visible ───────────────────────────────────
        val buttonLabel = when {
            state.isExpanded              -> "✕"
            state.lastDiceResult != null  -> "${state.lastDiceResult}"
            state.lastCoinResult == true  -> "H"
            state.lastCoinResult == false -> "T"
            else                          -> "✦"
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier
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
                text       = buttonLabel,
                fontSize   = 13.sp,
                color      = mc.primaryAccent,
                fontFamily = CinzelFontFamily,
                fontWeight = FontWeight.Bold,
            )
        }

        // ── Expanded panel ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.isExpanded,
            enter   = scaleIn(tween(250), initialScale = 0.3f, transformOrigin = TransformOrigin.Center) + fadeIn(tween(200)),
            exit    = scaleOut(tween(200), targetScale = 0.3f) + fadeOut(tween(150)),
        ) {
            Surface(
                shape           = RoundedCornerShape(20.dp),
                color           = mc.backgroundSecondary,
                border          = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.30f)),
                shadowElevation = 8.dp,
                modifier        = Modifier
                    .widthIn(min = 180.dp)
                    .offset(y = (-72).dp),   // float above the central button
            ) {
                Column(
                    modifier            = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("d20", style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
                    AnimatedDice(result = state.lastDiceResult, isRolling = state.isRollingDice, onClick = onRollDice)
                    HorizontalDivider(color = mc.surfaceVariant)
                    Text("coin", style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
                    AnimatedCoin(result = state.lastCoinResult, isFlipping = state.isFlippingCoin, onClick = onFlipCoin)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AnimatedDice
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AnimatedDice(
    result:    Int?,
    isRolling: Boolean,
    onClick:   () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val infiniteTransition = rememberInfiniteTransition(label = "dice_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue  = 0f,
        targetValue   = 360f,
        animationSpec = infiniteRepeatable(tween(300, easing = LinearEasing)),
        label         = "dice_angle",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .border(1.5.dp, mc.primaryAccent.copy(alpha = 0.50f), RoundedCornerShape(12.dp))
            .graphicsLayer { if (isRolling) rotationZ = spinAngle }
            .clickable(enabled = !isRolling, onClick = onClick),
    ) {
        if (isRolling) {
            Text("?", style = MaterialTheme.magicTypography.titleLarge, color = mc.primaryAccent)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text  = result?.toString() ?: "⚄",
                    style = if (result != null) MaterialTheme.magicTypography.titleLarge
                            else MaterialTheme.magicTypography.titleMedium,
                    color = when (result) {
                        20   -> mc.goldMtg
                        1    -> mc.lifeNegative
                        null -> mc.textDisabled
                        else -> mc.textPrimary
                    },
                )
                when (result) {
                    20   -> Text("NAT 20",   fontSize = 7.sp, color = mc.goldMtg,       letterSpacing = 1.sp)
                    1    -> Text("CRIT FAIL", fontSize = 7.sp, color = mc.lifeNegative, letterSpacing = 1.sp)
                    null -> Text("tap",       fontSize = 9.sp, color = mc.textDisabled)
                    else -> {}
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
    result:    Boolean?,
    isFlipping: Boolean,
    onClick:   () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val infiniteTransition = rememberInfiniteTransition(label = "coin_flip")
    val flipScale by infiniteTransition.animateFloat(
        initialValue  = -1f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(tween(200, easing = LinearEasing), RepeatMode.Reverse),
        label         = "coin_scale",
    )

    val bgBrush = if (!isFlipping && result != null)
        Brush.radialGradient(listOf(mc.goldMtg, mc.goldMtg.copy(alpha = 0.60f)))
    else
        Brush.radialGradient(listOf(mc.surface, mc.surfaceVariant))

    Box(
        contentAlignment = Alignment.Center,
        modifier         = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(bgBrush)
            .border(2.dp, mc.goldMtg.copy(alpha = 0.70f), CircleShape)
            .graphicsLayer { if (isFlipping) scaleX = flipScale }
            .clickable(enabled = !isFlipping, onClick = onClick),
    ) {
        when {
            isFlipping      -> Text("✦", fontSize = 22.sp, color = mc.goldMtg)
            result == true  -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("H", style = MaterialTheme.magicTypography.titleLarge, color = Color(0xFF1A1200))
                Text("HEADS", fontSize = 7.sp, color = Color(0xFF1A1200), letterSpacing = 1.sp)
            }
            result == false -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("T", style = MaterialTheme.magicTypography.titleLarge, color = Color(0xFF1A1200))
                Text("TAILS", fontSize = 7.sp, color = Color(0xFF1A1200), letterSpacing = 1.sp)
            }
            else            -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("○", fontSize = 28.sp, color = mc.goldMtg.copy(alpha = 0.50f))
                Text("tap", fontSize = 9.sp, color = mc.textDisabled)
            }
        }
    }
}
