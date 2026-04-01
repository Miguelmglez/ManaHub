package com.mmg.magicfolder.core.domain.model

import com.mmg.magicfolder.R

enum class DeckFormat(
    val displayNameRes: Int,
    val targetDeckSize: Int,
    val targetLandCount: Int,
    val maxCopies: Int,
    val requiresCommander: Boolean,
    val uniqueCards: Boolean,
) {
    STANDARD(
        displayNameRes    = R.string.format_standard,
        targetDeckSize    = 60,
        targetLandCount   = 24,
        maxCopies         = 4,
        requiresCommander = false,
        uniqueCards       = false,
    ),
    COMMANDER(
        displayNameRes    = R.string.format_commander,
        targetDeckSize    = 100,
        targetLandCount   = 37,
        maxCopies         = 1,
        requiresCommander = true,
        uniqueCards       = true,
    ),
    DRAFT(
        displayNameRes    = R.string.format_draft,
        targetDeckSize    = 40,
        targetLandCount   = 17,
        maxCopies         = 99,
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
    PIONEER(
        displayNameRes    = R.string.format_pioneer,
        targetDeckSize    = 60,
        targetLandCount   = 24,
        maxCopies         = 4,
        requiresCommander = false,
        uniqueCards       = false,
    ),
    PAUPER(
        displayNameRes    = R.string.format_pauper,
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
    );

    val nonLandSlots: Int get() = targetDeckSize - targetLandCount
}
