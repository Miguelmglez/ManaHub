package com.mmg.manahub.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mmg.manahub.core.ui.components.AncientOakBackground
import com.mmg.manahub.core.ui.components.ArcaneCosmosBackground
import com.mmg.manahub.core.ui.components.ForestMurmurBackground
import com.mmg.manahub.core.ui.components.HallowedPrintBackground
import com.mmg.manahub.core.ui.components.HexGridBackground
import com.mmg.manahub.core.ui.components.MedievalGrimoireBackground

// ═══════════════════════════════════════════════════════════════════════════════
//  Android-specific MagicTheme wrapper
//
//  The core MagicTheme composable is now in :shared:core-ui (commonMain) and
//  accepts a `typography` parameter. This wrapper provides the Android-specific
//  NeonVoidTypography (with R.font.* font families) so existing call sites that
//  use `MagicTheme(theme = xxx) { ... }` continue to work without changes.
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Android-specific [MagicTheme] entry point that provides the font-loaded
 * [NeonVoidTypography] automatically.
 *
 * This is a thin wrapper around the shared [com.mmg.manahub.core.ui.theme.MagicTheme]
 * composable in `:shared:core-ui`. It exists because the custom font families
 * (Marcellus, Mulish, Mana) are loaded from Android `R.font.*` resources, which
 * are not available in `commonMain`.
 *
 * @param theme The [AppTheme] to apply. Defaults to [AppTheme.NeonVoid].
 */
@Composable
fun MagicThemeAndroid(
    theme:   AppTheme = AppTheme.NeonVoid,
    content: @Composable () -> Unit,
) {
    MagicTheme(
        theme      = theme,
        typography = NeonVoidTypography,
        content    = content,
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  ThemeBackground — draws the correct decorative background for the active theme
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
fun ThemeBackground(
    modifier: Modifier = Modifier,
    theme: AppTheme = LocalAppTheme.current,
) {
    when (theme) {
        is AppTheme.NeonVoid         -> HexGridBackground(modifier = modifier)
        is AppTheme.MedievalGrimoire -> MedievalGrimoireBackground(modifier = modifier)
        is AppTheme.ArcaneCosmos     -> ArcaneCosmosBackground(modifier = modifier)
        is AppTheme.ForestMurmur     -> ForestMurmurBackground(modifier = modifier)
        is AppTheme.AncientOak       -> AncientOakBackground(modifier = modifier)
        is AppTheme.HallowedPrint    -> HallowedPrintBackground(modifier = modifier)
        is AppTheme.AzureFlux        -> HexGridBackground(modifier = modifier)
        is AppTheme.PlanarVeil       -> HexGridBackground(modifier = modifier)
        is AppTheme.VenomShade       -> HexGridBackground(modifier = modifier)
        is AppTheme.GlacialEdge      -> HexGridBackground(modifier = modifier)
        is AppTheme.DuskEmber        -> HexGridBackground(modifier = modifier)
        is AppTheme.OnyxNoir         -> HexGridBackground(modifier = modifier)
    }
}
