package com.mmg.manahub.core.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Reads the system-wide "Remove animations" accessibility setting
 * ([Settings.Global.ANIMATOR_DURATION_SCALE]) so purely decorative infinite animations can be
 * gated off for users who have disabled system animations.
 *
 * `ANIMATOR_DURATION_SCALE == 0f` means the user has turned system animations off (Settings >
 * Accessibility > Remove animations, or a developer-options animation-scale of 0x). Any caller
 * driving an [androidx.compose.animation.core.rememberInfiniteTransition] purely for decoration
 * (a pulsing dot, a glow, a sweep) should check this first and render the static end state
 * instead when `true` — functional animations (e.g. a progress-value tween that conveys real
 * information) are unaffected.
 *
 * Introduced for the Home dashboard's F-13 accessibility/motion cleanup (win-rate ring sweep,
 * hero "active session" pulsing dot, best-deck glow, last-game result pulse) but lives here so
 * any feature can adopt the same gate.
 */
@Composable
@ReadOnlyComposable
fun isReducedMotionEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale == 0f
}
