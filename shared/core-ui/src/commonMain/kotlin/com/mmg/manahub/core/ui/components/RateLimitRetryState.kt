package com.mmg.manahub.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Ticks down once per second from [retryAfterMs] to `0`, for gating a retry CTA that fronts a
 * rate-limited operation (Backend & Performance Optimization plan, WS2 — see
 * `com.mmg.manahub.core.data.network.RateLimitExhaustedException`).
 *
 * Callers pair this with [InlineErrorState]/[FullErrorState]'s `enabled` param: pass
 * `enabled = remainingSeconds <= 0` so a user cannot re-trigger a rate-limit storm by mashing
 * retry, and show the countdown in the retry label (e.g. "Retry in 5s") while it is still ticking.
 *
 * Restarts whenever a genuinely NEW [retryAfterMs] value arrives (a fresh rate-limit hit after the
 * previous cooldown already cleared) since it is `remember`-keyed on the value itself.
 *
 * @param retryAfterMs Milliseconds remaining at the moment the caller learned about the cooldown
 *   (typically `RateLimitExhaustedException.retryAfterMs`, parsed via `retryAfterMsOrNull` from a
 *   flattened error message). `null` or `<= 0` means no active cooldown — returns `0` immediately
 *   and never starts a ticking effect.
 * @return remaining whole seconds, `0` once the cooldown has cleared (or never applied).
 */
@Composable
fun rememberRateLimitCountdownSeconds(retryAfterMs: Long?): Int {
    var remainingMs by remember(retryAfterMs) {
        mutableStateOf(retryAfterMs?.coerceAtLeast(0L) ?: 0L)
    }
    LaunchedEffect(retryAfterMs) {
        var left = retryAfterMs ?: return@LaunchedEffect
        while (left > 0) {
            delay(1_000L)
            left -= 1_000L
            remainingMs = left.coerceAtLeast(0L)
        }
    }
    return (remainingMs / 1_000L).toInt()
}
