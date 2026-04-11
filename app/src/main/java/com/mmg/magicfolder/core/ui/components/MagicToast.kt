package com.mmg.magicfolder.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

// ─────────────────────────────────────────────────────────────────────────────
//  Types
// ─────────────────────────────────────────────────────────────────────────────

enum class MagicToastType { SUCCESS, INFO, WARNING, ERROR }

data class MagicToastMessage(
    val message: String,
    val type: MagicToastType = MagicToastType.SUCCESS,
    val durationMs: Long = 2500L,
)

// ─────────────────────────────────────────────────────────────────────────────
//  State
// ─────────────────────────────────────────────────────────────────────────────

class MagicToastState {
    private val _events = MutableSharedFlow<MagicToastMessage>(extraBufferCapacity = 4)
    val events: SharedFlow<MagicToastMessage> = _events

    fun show(
        message: String,
        type: MagicToastType = MagicToastType.SUCCESS,
        durationMs: Long = 2500L,
    ) {
        _events.tryEmit(MagicToastMessage(message, type, durationMs))
    }
}

@Composable
fun rememberMagicToastState(): MagicToastState = remember { MagicToastState() }

// ─────────────────────────────────────────────────────────────────────────────
//  Host — overlay this on top of your screen content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MagicToastHost(
    state: MagicToastState,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf<MagicToastMessage?>(null) }
    var visible  by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        state.events.collect { msg ->
            // If a toast is already visible, briefly hide it before showing the next one
            if (visible) {
                visible = false
                delay(220)
            }
            current = msg
            visible = true
            delay(msg.durationMs)
            visible = false
            delay(340) // wait for exit animation to complete before clearing content
            current = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = 20.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(
                initialOffsetY = { it + 60 },
                animationSpec  = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMediumLow,
                ),
            ) + fadeIn(animationSpec = tween(180)),
            exit = slideOutVertically(
                targetOffsetY  = { it + 60 },
                animationSpec  = tween(durationMillis = 260),
            ) + fadeOut(animationSpec = tween(200)),
        ) {
            current?.let { MagicToastCard(it) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Card UI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MagicToastCard(msg: MagicToastMessage) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    data class ToastStyle(val icon: ImageVector, val color: Color)
    val style = when (msg.type) {
        MagicToastType.SUCCESS -> ToastStyle(Icons.Default.Check,   mc.lifePositive)
        MagicToastType.INFO    -> ToastStyle(Icons.Default.Info,    mc.primaryAccent)
        MagicToastType.WARNING -> ToastStyle(Icons.Default.Warning, Color(0xFFF59E0B))
        MagicToastType.ERROR   -> ToastStyle(Icons.Default.Close,   mc.lifeNegative)
    }

    Surface(
        shape          = RoundedCornerShape(20.dp),
        color          = mc.backgroundSecondary,
        shadowElevation = 14.dp,
        tonalElevation = 4.dp,
        border         = androidx.compose.foundation.BorderStroke(
            0.5.dp, style.color.copy(alpha = 0.25f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment    = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Colored icon circle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(style.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = style.icon,
                    contentDescription = null,
                    tint               = style.color,
                    modifier           = Modifier.size(18.dp),
                )
            }

            // Message
            Text(
                text     = msg.message,
                style    = ty.bodyMedium,
                color    = mc.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
