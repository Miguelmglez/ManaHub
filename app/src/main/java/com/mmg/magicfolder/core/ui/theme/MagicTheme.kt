package com.mmg.magicfolder.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════════
//  CompositionLocals
// ═══════════════════════════════════════════════════════════════════════════════

val LocalMagicColors     = staticCompositionLocalOf<MagicColors>     { NeonVoidColors }
val LocalMagicTypography = staticCompositionLocalOf<MagicTypography> { NeonVoidTypography }

// ═══════════════════════════════════════════════════════════════════════════════
//  MaterialTheme extension properties
//  Usage: MaterialTheme.magicColors.primaryAccent
//         MaterialTheme.magicTypography.lifeNumber
// ═══════════════════════════════════════════════════════════════════════════════

/** Access the active [MagicColors] token set from any composable. */
val MaterialTheme.magicColors: MagicColors
    @Composable @ReadOnlyComposable
    get() = LocalMagicColors.current

/** Access the active [MagicTypography] token set from any composable. */
val MaterialTheme.magicTypography: MagicTypography
    @Composable @ReadOnlyComposable
    get() = LocalMagicTypography.current

// ═══════════════════════════════════════════════════════════════════════════════
//  MagicTheme — root composable
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Root theme composable. Wrap the entire app content tree here.
 *
 * Provides [MagicColors] and [MagicTypography] via CompositionLocals, and
 * bridges them into [MaterialTheme] so that Material 3 components
 * (NavigationBar, TopAppBar, BottomSheet, etc.) inherit the correct palette.
 *
 * @param theme The [AppTheme] to apply. Defaults to [AppTheme.NeonVoid].
 */
@Composable
fun MagicTheme(
    theme:   AppTheme = AppTheme.NeonVoid,
    content: @Composable () -> Unit,
) {
    val magicColors = when (theme) {
        is AppTheme.NeonVoid         -> NeonVoidColors
        is AppTheme.MedievalGrimoire -> NeonVoidColors   // stub — implement palette to unlock
        is AppTheme.ArcaneCosmos     -> NeonVoidColors   // stub
        is AppTheme.PhyrexianOil     -> NeonVoidColors   // stub
    }

    val magicTypography = when (theme) {
        is AppTheme.NeonVoid         -> NeonVoidTypography
        is AppTheme.MedievalGrimoire -> NeonVoidTypography  // stub
        is AppTheme.ArcaneCosmos     -> NeonVoidTypography  // stub
        is AppTheme.PhyrexianOil     -> NeonVoidTypography  // stub
    }

    CompositionLocalProvider(
        LocalMagicColors     provides magicColors,
        LocalMagicTypography provides magicTypography,
    ) {
        MaterialTheme(
            colorScheme = magicColors.toMaterial3ColorScheme(),
            typography  = magicTypography.toMaterial3Typography(),
            shapes      = magicMaterial3Shapes(),
            content     = content,
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Material 3 bridge (private helpers)
// ═══════════════════════════════════════════════════════════════════════════════

private fun MagicColors.toMaterial3ColorScheme() = darkColorScheme(
    primary              = primaryAccent,
    onPrimary            = Color.Black,
    primaryContainer     = primaryAccent.copy(alpha = 0.20f),
    onPrimaryContainer   = textPrimary,

    secondary            = secondaryAccent,
    onSecondary          = Color.Black,
    secondaryContainer   = secondaryAccent.copy(alpha = 0.20f),
    onSecondaryContainer = textPrimary,

    tertiary             = goldMtg,
    onTertiary           = Color.Black,
    tertiaryContainer    = goldMtg.copy(alpha = 0.20f),
    onTertiaryContainer  = textPrimary,

    background           = background,
    onBackground         = textPrimary,

    surface              = surface,
    onSurface            = textPrimary,
    surfaceVariant       = surfaceVariant,
    onSurfaceVariant     = textSecondary,

    error                = lifeNegative,
    onError              = Color.Black,

    outline              = textDisabled,
    scrim                = Color(0x99000000),
)

private fun MagicTypography.toMaterial3Typography() = Typography(
    displayLarge  = displayLarge,
    displayMedium = displayMedium,
    displaySmall  = titleLarge,
    titleLarge    = titleLarge,
    titleMedium   = titleMedium,
    titleSmall    = titleMedium,
    labelLarge    = labelLarge,
    labelMedium   = labelMedium,
    labelSmall    = labelSmall,
    bodyLarge     = bodyLarge,
    bodyMedium    = bodyMedium,
    bodySmall     = bodySmall,
    headlineLarge  = displayLarge,
    headlineMedium = displayMedium,
    headlineSmall  = titleLarge,
)

private fun magicMaterial3Shapes() = Shapes(
    small      = ChipShape,
    medium     = CardShape,
    large      = CardShape,
    extraLarge = BottomSheetShape,
)
