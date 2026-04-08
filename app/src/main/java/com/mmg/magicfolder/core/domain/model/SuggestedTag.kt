package com.mmg.magicfolder.core.domain.model

/**
 * A tag that the auto-tagging engine believes *might* apply to a card,
 * with a numeric confidence in [0,1]. Suggestions in [0.6, autoThreshold)
 * are surfaced in the CardDetail UI for the user to confirm or dismiss.
 */
data class SuggestedTag(
    val tag: CardTag,
    val confidence: Float,
    val source: String = "",
)
