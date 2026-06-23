package com.mmg.manahub.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.mmg.manahub.core.model.PreferredCurrency


// ═══════════════════════════════════════════════════════════════════════════════
//  CompositionLocals
// ═══════════════════════════════════════════════════════════════════════════════

val LocalMagicColors     = staticCompositionLocalOf<MagicColors>     { NeonVoidColors }
val LocalMagicTypography = staticCompositionLocalOf<MagicTypography> { MagicTypography() }
val LocalAppTheme        = compositionLocalOf<AppTheme>              { AppTheme.NeonVoid }
val LocalPreferredCurrency = staticCompositionLocalOf<PreferredCurrency> { PreferredCurrency.USD }

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
 * Light vs dark Material 3 base scheme is chosen automatically from
 * [MagicColors.isLight] — currently only [AppTheme.HallowedPrint] returns true.
 *
 * @param theme The [AppTheme] to apply. Defaults to [AppTheme.NeonVoid].
 * @param typography The [MagicTypography] to apply. On Android, callers should pass the
 *   font-loaded instance (e.g. `NeonVoidTypography`); on web, the default system-font
 *   typography is used until web font loading is implemented.
 */
@Composable
fun MagicTheme(
    theme:      AppTheme = AppTheme.NeonVoid,
    typography: MagicTypography = MagicTypography(),
    content:    @Composable () -> Unit,
) {
    val magicColors = when (theme) {
        is AppTheme.NeonVoid         -> NeonVoidColors
        is AppTheme.MedievalGrimoire -> MedievalGrimoireColors
        is AppTheme.ArcaneCosmos     -> ArcaneCosmosColors
        is AppTheme.ForestMurmur     -> ForestMurmurColors
        is AppTheme.AncientOak       -> AncientOakColors
        is AppTheme.HallowedPrint    -> HallowedPrintColors
        is AppTheme.AzureFlux        -> AzureFluxColors
        is AppTheme.PlanarVeil       -> PlanarVeilColors
        is AppTheme.VenomShade       -> VenomShadeColors
        is AppTheme.GlacialEdge      -> GlacialEdgeColors
        is AppTheme.DuskEmber        -> DuskEmberColors
        is AppTheme.OnyxNoir         -> OnyxNoirColors
    }

    // TODO(v2.1): give themes that warrant it their own typography instance.
    // For now every theme still inherits the same typography.
    val magicTypography = typography

    CompositionLocalProvider(
        LocalMagicColors     provides magicColors,
        LocalMagicTypography provides magicTypography,
        LocalSpacing         provides Spacing(),
        LocalAppTheme        provides theme,
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

private fun MagicColors.toMaterial3ColorScheme() = if (isLight) {
    lightColorScheme(
        primary              = primaryAccent,
        onPrimary            = Color.White,
        primaryContainer     = primaryAccent.copy(alpha = 0.14f),
        onPrimaryContainer   = textPrimary,

        secondary            = secondaryAccent,
        onSecondary          = Color.White,
        secondaryContainer   = secondaryAccent.copy(alpha = 0.14f),
        onSecondaryContainer = textPrimary,

        tertiary             = goldMtg,
        onTertiary           = Color.White,
        tertiaryContainer    = goldMtg.copy(alpha = 0.18f),
        onTertiaryContainer  = textPrimary,

        background           = background,
        onBackground         = textPrimary,

        surface              = surface,
        onSurface            = textPrimary,
        surfaceVariant       = surfaceVariant,
        onSurfaceVariant     = textSecondary,

        error                = lifeNegative,
        onError              = Color.White,

        outline              = textDisabled,
        scrim                = Color(0x66000000),
    )
} else {
    darkColorScheme(
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
}

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
