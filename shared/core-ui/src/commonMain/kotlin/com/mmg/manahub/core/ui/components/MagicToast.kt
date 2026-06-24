package com.mmg.manahub.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
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
    val onClick: (() -> Unit)? = null,
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
        onClick: (() -> Unit)? = null,
    ) {
        _events.tryEmit(MagicToastMessage(message, type, durationMs, onClick))
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
            current?.let { MagicToastCard(it) {
                visible = false
                it.onClick?.invoke()
            } }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Card UI
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MagicToastCard(msg: MagicToastMessage, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    data class ToastStyle(val icon: ImageVector, val color: Color)
    val style = when (msg.type) {
        MagicToastType.SUCCESS -> ToastStyle(ToastIcons.Check,   mc.lifePositive)
        MagicToastType.INFO    -> ToastStyle(ToastIcons.Info,    mc.primaryAccent)
        MagicToastType.WARNING -> ToastStyle(ToastIcons.Warning, Color(0xFFF59E0B))
        MagicToastType.ERROR   -> ToastStyle(CloseIcon,           mc.lifeNegative)
    }

    Surface(
        onClick        = { if (msg.onClick != null) onClick() },
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

// ─────────────────────────────────────────────────────────────────────────────
//  Inline Material-style icons (avoids material-icons-core dep, wasm-safe)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Minimal inline icon vectors matching Material Filled Check / Info / Warning.
 * Defined here so [MagicToastHost] has zero dependency on `material-icons-core`, which has
 * no Compose Multiplatform (wasmJs) artifact as of CMP 1.11.
 */
private object ToastIcons {
    val Check: ImageVector by lazy {
        ImageVector.Builder(
            name = "ToastCheck", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black)) {
            // M9 16.17 L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z
            moveTo(9f, 16.17f)
            lineTo(4.83f, 12f)
            lineToRelative(-1.42f, 1.41f)
            lineTo(9f, 19f)
            lineTo(21f, 7f)
            lineToRelative(-1.41f, -1.41f)
            close()
        }.build()
    }

    val Info: ImageVector by lazy {
        ImageVector.Builder(
            name = "ToastInfo", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
            reflectiveCurveTo(4.48f, 10f, 10f, 10f)
            reflectiveCurveTo(10f, -4.48f, 10f, -10f)
            reflectiveCurveTo(-4.48f, -10f, -10f, -10f)
            close()
            moveTo(13f, 17f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-6f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(6f)
            close()
            moveTo(13f, 9f)
            horizontalLineToRelative(-2f)
            lineTo(11f, 7f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
        }.build()
    }

    val Warning: ImageVector by lazy {
        ImageVector.Builder(
            name = "ToastWarning", defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black)) {
            moveTo(1f, 21f)
            horizontalLineToRelative(22f)
            lineTo(12f, 2f)
            lineTo(1f, 21f)
            close()
            moveTo(13f, 18f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-2f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(2f)
            close()
            moveTo(13f, 14f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(-4f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(4f)
            close()
        }.build()
    }
}
