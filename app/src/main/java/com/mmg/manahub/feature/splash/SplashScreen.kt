package com.mmg.manahub.feature.splash

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.manaColorFor
import com.mmg.manahub.core.ui.theme.MagicTheme
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.auth.domain.model.SessionState
import kotlinx.coroutines.delay

/**
 * Splash entry screen displayed while the authentication session state resolves.
 *
 * Orchestrates the Android 12+ system splash (already dismissed by [MainActivity])
 * and a custom Compose splash with animated branding. Navigation is deferred until
 * [SessionState] transitions out of [SessionState.Loading] AND a minimum visibility
 * window of 600 ms has elapsed so animations are always visible.
 *
 * @param onSessionResolved Invoked once with `true` if the user is authenticated,
 *   `false` otherwise. The caller is responsible for navigation.
 * @param viewModel Injected by Hilt; observes session state from [AuthRepository].
 */
@Composable
fun SplashScreen(
    onSessionResolved: (isAuthenticated: Boolean) -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()

    SplashContent(
        sessionState = sessionState,
        onSessionResolved = onSessionResolved,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Stateless content composable
// ─────────────────────────────────────────────────────────────────────────────

private const val MIN_VISIBLE_MS = 600L
private const val LABEL_FADE_DELAY_MS = 200L
private const val FADE_OUT_DURATION_MS = 350

/**
 * Stateless implementation of the splash UI. Separated from [SplashScreen] to
 * allow preview without a Hilt component.
 */
@Composable
internal fun SplashContent(
    sessionState: SessionState,
    onSessionResolved: (isAuthenticated: Boolean) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val mt = MaterialTheme.magicTypography

    // ── Animation state ───────────────────────────────────────────────────────

    // Pulse animation for the background glow
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Icon: scale spring 0.7 → 1 and alpha 0 → 1
    var iconScale by remember { mutableFloatStateOf(0.7f) }
    var iconAlpha by remember { mutableFloatStateOf(0f) }

    val animatedIconScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = iconScale,
        animationSpec = spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium,
        ),
        label = "icon_scale",
    )
    val animatedIconAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = iconAlpha,
        animationSpec = tween(durationMillis = 400),
        label = "icon_alpha",
    )

    // Labels: delayed fade-in after icon
    var labelsAlpha by remember { mutableFloatStateOf(0f) }
    val animatedLabelsAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = labelsAlpha,
        animationSpec = tween(durationMillis = 400),
        label = "labels_alpha",
    )

    // Screen-level fade-out before navigating away
    var screenAlpha by remember { mutableFloatStateOf(1f) }
    val animatedScreenAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = screenAlpha,
        animationSpec = tween(durationMillis = FADE_OUT_DURATION_MS),
        label = "screen_alpha",
    )

    // Tracks whether minimum display time has been satisfied
    var minTimeElapsed by remember { mutableStateOf(false) }

    // Resolved state to navigate to (set when session resolves)
    var resolvedState by remember { mutableStateOf<SessionState?>(null) }

    // ── Entrance animation sequence ───────────────────────────────────────────
    LaunchedEffect(Unit) {
        iconScale = 1f
        iconAlpha = 1f
        delay(LABEL_FADE_DELAY_MS)
        labelsAlpha = 1f
        delay(MIN_VISIBLE_MS - LABEL_FADE_DELAY_MS)
        minTimeElapsed = true
    }

    // ── Session resolution + minimum time gate ────────────────────────────────
    LaunchedEffect(sessionState, minTimeElapsed) {
        if (sessionState !is SessionState.Loading) {
            resolvedState = sessionState
        }
        val canNavigate = resolvedState != null && minTimeElapsed
        if (canNavigate) {
            // Fade out before handing control to the nav graph
            screenAlpha = 0f
            delay(FADE_OUT_DURATION_MS.toLong())
            onSessionResolved(resolvedState is SessionState.Authenticated)
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(animatedScreenAlpha)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        mc.background,
                        mc.backgroundSecondary,
                        mc.background
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Decorative hex grid — subtle overlay
        HexGridBackground(
            modifier = Modifier.fillMaxSize(),
            color = mc.primaryAccent.copy(alpha = 0.08f),
            hexSize = 32f
        )

        // Center branding
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // App icon with circular glow container
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(animatedIconScale)
                    .alpha(animatedIconAlpha),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulseScale)
                        .blur(20.dp)
                        .background(
                            color = mc.primaryAccent.copy(alpha = 0.25f),
                            shape = CircleShape,
                        )
                )

                // Main circle
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            color = mc.background.copy(alpha = 0.8f),
                            shape = CircleShape,
                        )
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    mc.primaryAccent,
                                    mc.secondaryAccent,
                                    mc.primaryAccent
                                )
                            ),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(120.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // App name with gradient / glow effect
            Text(
                text = stringResource(R.string.app_name),
                style = mt.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = mc.primaryAccent.copy(alpha = 0.5f),
                        offset = Offset(0f, 0f),
                        blurRadius = 15f
                    )
                ),
                color = mc.textPrimary,
                modifier = Modifier.alpha(animatedLabelsAlpha),
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tagline
            Text(
                text = stringResource(R.string.splash_tagline).uppercase(),
                style = mt.labelLarge.copy(
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Light
                ),
                color = mc.textSecondary,
                modifier = Modifier.alpha(animatedLabelsAlpha),
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Mana Symbols Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(animatedLabelsAlpha),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("W", "U", "B", "R", "G").forEach { colorCode ->
                    val color = manaColorFor(colorCode, mc)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .size(12.dp)
                            .background(color, CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    )
                }
            }
        }

        // Loading indicator at bottom — visible only while session is loading
        if (sessionState is SessionState.Loading) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = mc.primaryAccent,
                    strokeWidth = 2.dp,
                    strokeCap = StrokeCap.Round,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun SplashContentLoadingPreview() {
    MagicTheme {
        SplashContent(
            sessionState = SessionState.Loading,
            onSessionResolved = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SplashContentAuthenticatedPreview() {
    MagicTheme {
        SplashContent(
            sessionState = SessionState.Unauthenticated,
            onSessionResolved = {},
        )
    }
}
