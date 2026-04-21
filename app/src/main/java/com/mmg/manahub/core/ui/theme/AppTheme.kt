package com.mmg.manahub.core.ui.theme

/**
 * Discriminator for all available visual themes.
 *
 * Adding a new theme requires:
 *  1. Providing its [MagicColors] and [MagicTypography] instances.
 *  2. Adding a branch in [MagicTheme]'s `when(theme)` blocks.
 *
 * Composables never reference a theme object directly — they only consume
 * tokens via MaterialTheme.magicColors / MaterialTheme.magicTypography.
 */
sealed class AppTheme {

    /** Dark neon sci-fi aesthetic — fully implemented. */
    object NeonVoid         : AppTheme()

    /** Aged parchment and illuminated-manuscript aesthetic. */
    object MedievalGrimoire : AppTheme()

    /** Deep-space star-chart aesthetic. */
    object ArcaneCosmos     : AppTheme()

    /** Dark purple mystical aesthetic. */
    object ShadowEssence    : AppTheme()

    /** Dark green forest/nature aesthetic. */
    object ForestMurmur     : AppTheme()

    /** Purple mystical aesthetic with cyan accents. */
    object MysticEcho       : AppTheme()

    /** Gold and silver royal aesthetic. */
    object GildedSilver     : AppTheme()

    /** Dark brown earthy aesthetic. */
    object AncientOak       : AppTheme()

    /** Neon grey and black industrial aesthetic. */
    object ObsidianChrome   : AppTheme()

    // PhyrexianOil — industrial oil-slick Phyrexian horror — reserved for v3
}
