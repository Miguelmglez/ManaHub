package com.mmg.manahub.core.domain.model

import com.mmg.manahub.R

enum class DeckFormat(
    val displayNameRes: Int,
    val targetDeckSize: Int,
    val targetLandCount: Int,
    val maxCopies: Int,
    val requiresCommander: Boolean,
    val uniqueCards: Boolean,
) {
    /*STANDARD(
        displayNameRes    = R.string.format_standard,
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
          displayNameRes    = R.string.format_pioneer,
          targetDeckSize    = 60,
          targetLandCount   = 24,
          maxCopies         = 4,
          requiresCommander = false,
          uniqueCards       = false,
      ),
      MODERN(
          displayNameRes    = R.string.format_modern,
          targetDeckSize    = 60,
          targetLandCount   = 24,
          maxCopies         = 4,
          requiresCommander = false,
          uniqueCards       = false,
      ),
      LEGACY(
          displayNameRes    = R.string.format_legacy,
          targetDeckSize    = 60,
          targetLandCount   = 24,
          maxCopies         = 4,
          requiresCommander = false,
          uniqueCards       = false,
      ),
      VINTAGE(
          displayNameRes    = R.string.format_vintage,
          targetDeckSize    = 60,
          targetLandCount   = 24,
          maxCopies         = 4,
          requiresCommander = false,
          uniqueCards       = false,
      ),
      // Pauper: 60-card, commons-only. Slightly higher land count (the format runs leaner curves
      // and wants consistent mana). Filtered by legal:pauper.
      PAUPER(
          displayNameRes    = R.string.format_pauper,
          targetDeckSize    = 60,
          targetLandCount   = 23,
          maxCopies         = 4,
          requiresCommander = false,
          uniqueCards       = false,
      ),*/
    COMMANDER(
        displayNameRes    = R.string.format_commander,
        targetDeckSize    = 100,
        targetLandCount   = 37,
        maxCopies         = 1,
        requiresCommander = true,
        uniqueCards       = true,
    ),
    // Casual: permissive — no legality restriction and no construction validation. Standard shape.
    CASUAL(
        displayNameRes    = R.string.format_casual,
        targetDeckSize    = 60,
        targetLandCount   = 24,
        maxCopies         = 4,
        requiresCommander = false,
        uniqueCards       = false,
    ),
    DRAFT(
        displayNameRes    = R.string.format_draft,
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
