package com.mmg.manahub.core.ui.theme

/**
 * Discriminator for all available visual themes.
 *
 * Adding a new theme requires:
 *  1. Providing its [MagicColors] and [MagicTypography] instances.
 *  2. Adding a branch in [MagicTheme]'s when(theme) blocks.
 *
 * Composables never reference a theme object directly — they only consume
 * tokens via MaterialTheme.magicColors / MaterialTheme.magicTypography.
 */
sealed class AppTheme {

    // ── Kept from v2 ─────────────────────────────────────────────────────────

    /** Dark neon sci-fi aesthetic. (default) */
    object NeonVoid         : AppTheme()

    /** Aged parchment and illuminated-manuscript aesthetic. */
    object MedievalGrimoire : AppTheme()

    /** Deep-space star-chart aesthetic. */
    object ArcaneCosmos     : AppTheme()

    /** Dark green forest — moonlit canopy. [v3: primaryAccent updated to cream-white] */
    object ForestMurmur     : AppTheme()

    /** Dark brown earthy aesthetic. */
    object AncientOak       : AppTheme()

    /** Light theme — ink on aged parchment. The only light theme. */
    object HallowedPrint    : AppTheme()

    // ── New in v4 ─────────────────────────────────────────────────────────────

    /** Electric cobalt primary + hot pink secondary. Inverted-Neon register. */
    object AzureFlux        : AppTheme()

    /** Royal violet + aged amber. Old-world planeswalker aesthetic. */
    object PlanarVeil       : AppTheme()

    /** Acid lime + toxic violet on swamp purple. Golgari / poison niche. */
    object VenomShade       : AppTheme()

    /** Pale ice + frost violet. Snow-Covered / Kaldheim aesthetic. */
    object GlacialEdge      : AppTheme()

    /** Coral glow + dusty gold on plum-black. Warm sunset / Boros warmth. */
    object DuskEmber        : AppTheme()

    /** Brushed pearl + champagne on volcanic glass. Premium noir. */
    object OnyxNoir         : AppTheme()

    // ── v4 changelog ──────────────────────────────────────────────────────────
    //  REMOVED:
    //   - ShadowEssence  → migrate → PlanarVeil  (purple slot; Shadow overlapped Neon)
    //   - Reliquary      → migrate → AncientOak  (same warm-walnut register)
    //   - Pyromancer     → migrate → MedievalGrimoire (same red-on-dark silhouette)
    //   - Hydromancy     → migrate → GlacialEdge (too close chromatically)
    //  REFINED:
    //   - ForestMurmur   → primaryAccent #CDDC39 → #F2FAEC (moonlit cream)
    //
    // PhyrexianOil — reserved for v5
}
