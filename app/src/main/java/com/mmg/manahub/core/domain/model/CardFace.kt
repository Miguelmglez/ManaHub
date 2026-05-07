package com.mmg.manahub.core.domain.model

/**
 * Represents one face of a double-faced card (DFC), meld card, or any other
 * multi-face card layout returned by the Scryfall API.
 *
 * All fields except [name] are nullable because Scryfall omits them for faces
 * that do not carry that characteristic (e.g. the back face of a transforming
 * saga has no mana cost).
 */
data class CardFace(
    /** Display name of this face (e.g. "Delver of Secrets"). */
    val name: String,
    /** Mana cost string in Scryfall notation, e.g. "{1}{U}". Null for back faces. */
    val manaCost: String?,
    /** Full type line for this face, e.g. "Creature — Human Wizard". */
    val typeLine: String?,
    /** Oracle rules text for this face. */
    val oracleText: String?,
    /** Power value if this face is a creature, null otherwise. */
    val power: String?,
    /** Toughness value if this face is a creature, null otherwise. */
    val toughness: String?,
    /** Loyalty value if this face is a planeswalker, null otherwise. */
    val loyalty: String?,
    /** Defense value if this face is a battle card, null otherwise. */
    val defense: String?,
    /** Flavor text printed on this face, null if absent. */
    val flavorText: String?,
    /** URL to the normal-size card image for this face. */
    val imageNormal: String?,
    /** URL to the art-crop image for this face. */
    val imageArtCrop: String?,
)
