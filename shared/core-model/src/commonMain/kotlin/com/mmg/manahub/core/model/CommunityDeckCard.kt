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
 * @property oracleId the Scryfall oracle id from Archidekt's oracle card record (shared
 *   across every printing of this card — NOT a resolvable printing/image id).
 * @property scryfallId the Scryfall PRINTING uuid of this exact card entry (Archidekt's
 *   `card.uid`), used to build [imageUrl] and to open this exact printing's detail screen.
 *   Empty when Archidekt didn't resolve a printing for this entry.
 * @property manaCost the oracle mana cost string (e.g. `"{2}{U}{U}"`).
 * @property typeLine the composed MTG type line (e.g. `"Legendary Creature — Elf Druid"`).
 * @property cmc converted mana cost.
 * @property rarity the printing's rarity (e.g. `"rare"`).
 * @property setCode the printing's set code (e.g. `"mh2"`).
 * @property setName the printing's set name.
 * @property priceUsd the printing's TCGplayer USD price, when known.
 * @property priceEur the printing's Cardmarket EUR price, when known.
 * @property excludedFromDeckCount true when AT LEAST ONE category this entry carries is flagged
 *   `includedInDeck = false` on the Archidekt deck (Maybeboard, a user's custom "Cut"/"Bench"
 *   bucket, ...). Bug fix, 2026-07-22: such entries used to be DROPPED entirely by the mapper;
 *   they are now kept and treated as sideboard-like via [isSideboard] instead, so they still show
 *   up (and can be moved back to the mainboard) rather than silently vanishing. Deliberately an
 *   ANY-of match, not ALL-of (corrected same day): Archidekt's auto-categorization tags most cards
 *   with a second functional/type category regardless of Maybeboard status, so a genuinely-excluded
 *   entry usually also carries an ordinary category — an ALL-of match almost never fires against
 *   real decks. See `ArchidektDeckDetailDto.toDomain()`'s KDoc for why category NAMES are never
 *   matched directly.
 */
data class CommunityDeckCard(
    val name: String,
    val quantity: Int,
    val categories: List<String>,
    val oracleId: String,
    val scryfallId: String = "",
    val manaCost: String = "",
    val typeLine: String = "",
    val cmc: Double = 0.0,
    val rarity: String = "",
    val setCode: String = "",
    val setName: String = "",
    val priceUsd: Double? = null,
    val priceEur: Double? = null,
    val excludedFromDeckCount: Boolean = false,
) {
    /**
     * True when this entry belongs to the Sideboard category, OR when [excludedFromDeckCount] is
     * true (an Archidekt Maybeboard / custom excluded-category entry) — both are "not really in
     * the 100/60-card deck" zones and are treated uniformly as sideboard for display, counting,
     * and import purposes.
     */
    val isSideboard: Boolean
        get() = excludedFromDeckCount || categories.any {
            it.equals("Sideboard", ignoreCase = true) || it.equals("Side", ignoreCase = true)
        }

    /** True when this entry is the deck's commander. */
    val isCommander: Boolean
        get() = categories.any { 
            it.equals("Commander", ignoreCase = true) || it.equals("Commanders", ignoreCase = true) 
        }

    /**
     * The Scryfall image CDN URL for this exact printing, built directly from [scryfallId]
     * (no Scryfall API call — this is the static `cards.scryfall.io` CDN, not `api.scryfall.com`,
     * so it must NOT go through `ScryfallRequestQueue`). Null when no printing was resolved.
     */
    val imageUrl: String?
        get() = scryfallId.takeIf { it.isNotBlank() }?.let { uid ->
            "https://cards.scryfall.io/normal/front/${uid[0]}/${uid[1]}/$uid.jpg"
        }
}
