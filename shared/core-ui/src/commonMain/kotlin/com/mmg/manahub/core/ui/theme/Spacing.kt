package com.mmg.manahub.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════════════════
//  Spacing tokens
//  An 8dp-grid spacing scale shared across all themes (theme-independent).
//  Use these for padding, gaps, and arrangement spacing in composables:
//    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)) { … }
//    Modifier.padding(horizontal = MaterialTheme.spacing.lg)
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Immutable spacing scale based on an 8dp grid.
 *
 * The default values form a single shared scale used by every [AppTheme]; spacing
 * is intentionally not theme-dependent. Prefer these tokens over hardcoded `dp`
 * literals so layout rhythm stays consistent across the app.
 *
 * @property xxs  2.dp  — hairline gaps (e.g. between a pip and its count).
 * @property xs   4.dp  — tight inner padding, icon-to-label gaps.
 * @property sm   8.dp  — base grid unit; default item spacing.
 * @property md   12.dp — comfortable inner padding for cards and rows.
 * @property lg   16.dp — standard screen content padding.
 * @property xl   24.dp — section separation, large content insets.
 * @property xxl  32.dp — major vertical rhythm between distinct blocks.
 */
data class Spacing(
    val xxs: Dp = 2.dp,
    val xs:  Dp = 4.dp,
    val sm:  Dp = 8.dp,
    val md:  Dp = 12.dp,
    val lg:  Dp = 16.dp,
    val xl:  Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

// ═══════════════════════════════════════════════════════════════════════════════
//  CompositionLocal
// ═══════════════════════════════════════════════════════════════════════════════

/** Holds the active [Spacing] scale. Provided by [MagicTheme] with a default [Spacing]. */
val LocalSpacing = staticCompositionLocalOf { Spacing() }

// ═══════════════════════════════════════════════════════════════════════════════
//  MaterialTheme extension property
//  Usage: MaterialTheme.spacing.lg
// ═══════════════════════════════════════════════════════════════════════════════

/** Access the active [Spacing] token set from any composable. */
val MaterialTheme.spacing: Spacing
    @Composable @ReadOnlyComposable
    get() = LocalSpacing.current
