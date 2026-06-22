package com.mmg.manahub.core.model

/**
 * A single card entry inside a [CommunityDeck].
 *
 * Membership in the sideboard / commander zones is derived from the upstream
 * Archidekt category labels rather than dedicated flags, since Archidekt models
 * those zones as free-form categories on the card entry.
 *
 * @property name the oracle card name (used to resolve against Scryfall on import).
 * @property quantity number of copies in the deck.
 * @property categories the raw Archidekt category labels for this entry.
 * @property oracleId the Scryfall oracle id from Archidekt's oracle card record.
 */
data class CommunityDeckCard(
    val name: String,
    val quantity: Int,
    val categories: List<String>,
    val oracleId: String,
) {
    /** True when this entry belongs to the Sideboard category. */
    val isSideboard: Boolean
        get() = categories.any { it.equals("Sideboard", ignoreCase = true) }

    /** True when this entry is the deck's commander. */
    val isCommander: Boolean
        get() = categories.any { it.equals("Commander", ignoreCase = true) }
}
