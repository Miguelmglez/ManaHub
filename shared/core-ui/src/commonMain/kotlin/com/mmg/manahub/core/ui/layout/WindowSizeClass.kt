package com.mmg.manahub.core.ui.layout

import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hand-rolled window size-class for Compose Multiplatform.
 *
 * CMP has no built-in `WindowSizeClass` equivalent — that artifact (`androidx.window`) is
 * Android/Activity-only and cannot be used from `commonMain`. This is a from-scratch,
 * `BoxWithConstraints`-based replacement, shared by Android and Web, that drives responsive nav
 * chrome via [AdaptiveScaffold] and grid density via [AdaptiveCardGrid].
 *
 * Breakpoints (web roadmap plan, "Responsive-First Incremental Build Plan" §1):
 *
 * | Class    | Width          | Device                      | Nav chrome                      |
 * |----------|----------------|-----------------------------|----------------------------------|
 * | COMPACT  | `< 600.dp`     | phone browser                | bottom `NavigationBar`           |
 * | MEDIUM   | `600–839.dp`   | tablet / narrow desktop win. | collapsed rail (icons only)      |
 * | EXPANDED | `840–1199.dp`  | small desktop window         | expanded rail (icons + labels)   |
 * | LARGE    | `>= 1200.dp`   | wide desktop                 | expanded rail, content clamped   |
 */
enum class ManaWindowSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
    LARGE,
    ;

    companion object {
        /** Resolves the size class for a raw [width]. Pure function — no Composable context needed. */
        fun fromWidth(width: Dp): ManaWindowSizeClass = when {
            width < 600.dp -> COMPACT
            width < 840.dp -> MEDIUM
            width < 1200.dp -> EXPANDED
            else -> LARGE
        }
    }
}

/**
 * Derives the current [ManaWindowSizeClass] from the available width inside a root
 * `BoxWithConstraints` — call this from within [AdaptiveScaffold] (or another root-level
 * `BoxWithConstraints`), not by nesting a second `BoxWithConstraints` deeper in the tree: measuring
 * constraints is not free, and every downstream composable should consume the ALREADY-resolved
 * class via a parameter instead of re-deriving it.
 */
@Composable
fun BoxWithConstraintsScope.rememberWindowSizeClass(): ManaWindowSizeClass =
    remember(maxWidth) { ManaWindowSizeClass.fromWidth(maxWidth) }
