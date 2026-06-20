package com.mmg.manahub.core.model

/**
 * Deck construction format.
 *
 * [displayName] is the English label for this format. The domain layer is resource-free
 * (KMP modularization, Phase 0.5 Blocker 3) so the label is a plain English string rather
 * than an Android string-resource id; the app is English-only.
 */
enum class DeckFormat(
    val displayName: String,
    val targetDeckSize: Int,
    val targetLandCount: Int,
    val maxCopies: Int,
    val requiresCommander: Boolean,
    val uniqueCards: Boolean,
) {
    /*STANDARD(
        displayName       = "Standard",
        targetDeckSize    = 60,
        targetLandCount   = 24,
        maxCopies         = 4,
        requiresCommander = false,
        uniqueCards       = false,
    ),
    // 60-card non-rotating constructed formats (Deck Doctor Phase 4, D1). Same deck shape
    // as Standard but each filters against its OWN Scryfall legality (see DeckScorer.isLegal)
    // and uses a tighter skeleton/curve (DeckSkeletons.forFormat).
      PIONEER(
          displayName       = "Pioneer",
          targetDeckSize    = 60,
          targetLandCount   = 24,
          maxCopies         = 4,
          requiresCommander = false,
          uniqueCards       = false,
      ),
      MODERN(
          displayName       = "Modern",
          targetDeckSize    = 60,
          targetLandCount   = 24,
          maxCopies         = 4,
          requiresCommander = false,
          uniqueCards       = false,
      ),
      LEGACY(
          displayName       = "Legacy",
          targetDeckSize    = 60,
          targetLandCount   = 24,
          maxCopies         = 4,
          requiresCommander = false,
          uniqueCards       = false,
      ),
      VINTAGE(
          displayName       = "Vintage",
          targetDeckSize    = 60,
          targetLandCount   = 24,
          maxCopies         = 4,
          requiresCommander = false,
          uniqueCards       = false,
      ),
      // Pauper: 60-card, commons-only. Slightly higher land count (the format runs leaner curves
      // and wants consistent mana). Filtered by legal:pauper.
      PAUPER(
          displayName       = "Pauper",
          targetDeckSize    = 60,
          targetLandCount   = 23,
          maxCopies         = 4,
          requiresCommander = false,
          uniqueCards       = false,
      ),*/
    COMMANDER(
        displayName       = "Commander",
        targetDeckSize    = 100,
        targetLandCount   = 37,
        maxCopies         = 1,
        requiresCommander = true,
        uniqueCards       = true,
    ),
    // Casual: permissive — no legality restriction and no construction validation. Standard shape.
    CASUAL(
        displayName       = "Casual",
        targetDeckSize    = 60,
        targetLandCount   = 24,
        maxCopies         = 4,
        requiresCommander = false,
        uniqueCards       = false,
    ),
    DRAFT(
        displayName       = "Draft",
        targetDeckSize    = 40,
        targetLandCount   = 17,
        maxCopies         = 99,
        requiresCommander = false,
        uniqueCards       = false,
    );

    val nonLandSlots: Int get() = targetDeckSize - targetLandCount

    /**
     * 60-card non-rotating constructed formats (incl. Standard & Casual). They share the Standard
     * skeleton shape and the 4-copy / 60-card-minimum construction rules. Commander and Draft are
     * special-cased separately.
     */
    val isSixtyCardConstructed: Boolean
        get() = this == CASUAL
}
