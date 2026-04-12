package com.mmg.manahub.feature.game.model

/**
 * String keys used to identify the icon for a custom counter.
 * Stored in [CustomCounter.iconKey].
 */
object CounterIconKey {
    const val DEFAULT    = ""        // ic_counter drawable
    const val POISON     = "poison"  // ic_poison drawable
    const val EXPERIENCE = "exp"     // ic_experience drawable
    const val ENERGY     = "energy"  // ic_energy drawable
    const val MANA_W     = "mana_w"  // White mana circle
    const val MANA_U     = "mana_u"  // Blue mana circle
    const val MANA_B     = "mana_b"  // Black mana circle
    const val MANA_R     = "mana_r"  // Red mana circle
    const val MANA_G     = "mana_g"  // Green mana circle
    const val MANA_C     = "mana_c"  // Colorless mana circle
    const val STAR       = "star"    // Star (loyalty/bonus)
    const val SWORD      = "sword"   // Sword (combat)
    const val SHIELD     = "shield"  // Shield (protection)

    /** All icon keys in display order for the icon picker. */
    val ALL = listOf(
        DEFAULT, POISON, EXPERIENCE, ENERGY,
        MANA_W, MANA_U, MANA_B, MANA_R, MANA_G, MANA_C,
        STAR, SWORD, SHIELD,
    )
}
