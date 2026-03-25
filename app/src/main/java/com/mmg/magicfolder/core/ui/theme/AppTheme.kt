package com.mmg.magicfolder.core.ui.theme

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

    /** Aged parchment and illuminated-manuscript aesthetic — stub. */
    object MedievalGrimoire : AppTheme()

    /** Deep-space star-chart aesthetic — stub. */
    object ArcaneCosmos     : AppTheme()

    /** Industrial oil-slick Phyrexian horror aesthetic — stub. */
    object PhyrexianOil     : AppTheme()
}
