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

    /**
     * Stable identifier used for persistence and analytics. NEVER use `toString()`/class names:
     * R8 renames them, so persisted values and dashboards would break between builds.
     */
    abstract val persistKey: String

    // ── Kept from v2 ─────────────────────────────────────────────────────────

    /** Dark neon sci-fi aesthetic. (default) */
    object NeonVoid         : AppTheme() { override val persistKey = "NEON_VOID" }

    /** Aged parchment and illuminated-manuscript aesthetic. */
    object MedievalGrimoire : AppTheme() { override val persistKey = "MEDIEVAL_GRIMOIRE" }

    /** Deep-space star-chart aesthetic. */
    object ArcaneCosmos     : AppTheme() { override val persistKey = "ARCANE_COSMOS" }

    /** Dark green forest — moonlit canopy. [v3: primaryAccent updated to cream-white] */
    object ForestMurmur     : AppTheme() { override val persistKey = "FOREST_MURMUR" }

    /** Dark brown earthy aesthetic. */
    object AncientOak       : AppTheme() { override val persistKey = "ANCIENT_OAK" }

    /** Light theme — ink on aged parchment. The only light theme. */
    object HallowedPrint    : AppTheme() { override val persistKey = "HALLOWED_PRINT" }

    // ── New in v4 ─────────────────────────────────────────────────────────────

    companion object {
        /**
         * The single source of truth for "no theme chosen yet".
         *
         * Three call sites used to hardcode three DIFFERENT defaults (SettingsUiState, the DataStore
         * read fallback and MainActivity), so the first frame could show a palette the user never
         * picked.
         */
        val Default: AppTheme = ArcaneCosmos
    }

    /** Electric cobalt primary + hot pink secondary. Inverted-Neon register. */
    object AzureFlux        : AppTheme() { override val persistKey = "AZURE_FLUX" }

    /** Royal violet + aged amber. Old-world planeswalker aesthetic. */
    object PlanarVeil       : AppTheme() { override val persistKey = "PLANAR_VEIL" }

    /** Acid lime + toxic violet on swamp purple. Golgari / poison niche. */
    object VenomShade       : AppTheme() { override val persistKey = "VENOM_SHADE" }

    /** Pale ice + frost violet. Snow-Covered / Kaldheim aesthetic. */
    object GlacialEdge      : AppTheme() { override val persistKey = "GLACIAL_EDGE" }

    /** Coral glow + dusty gold on plum-black. Warm sunset / Boros warmth. */
    object DuskEmber        : AppTheme() { override val persistKey = "DUSK_EMBER" }

    /** Brushed pearl + champagne on volcanic glass. Premium noir. */
    object OnyxNoir         : AppTheme() { override val persistKey = "ONYX_NOIR" }

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
