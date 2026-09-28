package com.mmg.manahub.app.navigation

import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Decides what a system Back at the root of the navigation stack does: the first press asks for
 * confirmation, a second press within [windowMs] exits. Pure (clock injected) so it is unit tested
 * without Compose.
 */
class AppExitBackGuard(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: () -> Long,
) {
    /** Outcome of a root-level Back press. */
    enum class Decision { SHOW_PROMPT, EXIT }

    private var promptShownAtMs: Long? = null

    /** Registers a root-level Back press and returns what the app should do with it. */
    fun onBackPressed(): Decision {
        val now = clock()
        val shownAt = promptShownAtMs
        return if (shownAt != null && now - shownAt in 0..windowMs) {
            promptShownAtMs = null
            Decision.EXIT
        } else {
            promptShownAtMs = now
            Decision.SHOW_PROMPT
        }
    }

    /** Forgets a pending prompt, e.g. when the user navigates away from the root. */
    fun reset() {
        promptShownAtMs = null
    }

    companion object {
        /** How long after the prompt a second Back still counts as "confirm exit". */
        const val DEFAULT_WINDOW_MS = 2_000L
    }
}

/**
 * Registers the app-level exit guard on the Activity's back dispatcher. Enabled only while
 * [atRoot] (nothing left to pop); callers must compose this BEFORE the NavHost so every screen,
 * sheet or overlay handler registered later keeps priority over it.
 *
 * A confirmed exit briefly disables the callback and re-dispatches Back, so the platform default
 * (move the task back on Android 12+, finish before) runs exactly as it would without the guard.
 */
@Composable
fun AppExitBackHandler(
    atRoot: Boolean,
    guard: AppExitBackGuard,
    onShowPrompt: () -> Unit,
) {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher ?: return
    val currentAtRoot by rememberUpdatedState(atRoot)
    val currentOnShowPrompt by rememberUpdatedState(onShowPrompt)
    val callback = remember(dispatcher, guard) {
        object : OnBackPressedCallback(atRoot) {
            override fun handleOnBackPressed() {
                when (guard.onBackPressed()) {
                    AppExitBackGuard.Decision.SHOW_PROMPT -> currentOnShowPrompt()
                    AppExitBackGuard.Decision.EXIT -> {
                        isEnabled = false
                        dispatcher.onBackPressed()
                        isEnabled = currentAtRoot
                    }
                }
            }
        }
    }
    SideEffect {
        callback.isEnabled = atRoot
        if (!atRoot) guard.reset()
    }
    DisposableEffect(dispatcher, callback) {
        dispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }
}
