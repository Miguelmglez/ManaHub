package com.mmg.manahub.core.model

/**
 * Maps Archidekt's numeric `deckFormat` ids to ManaHub's internal format strings
 * (the same lowercase tokens used by `DeckEntity.format` / `DeckFormat`).
 *
 * Archidekt formats with no direct ManaHub equivalent (Custom, Frontier,
 * Penny Dreadful, …) fall back to "casual". The mapping is intentionally lossy in
 * that direction — ManaHub does not model every niche Archidekt format.
 */
enum class ArchidektFormat(val apiId: Int, val manahubFormat: String) {
    STANDARD(1, "standard"),
    MODERN(2, "modern"),
    COMMANDER(3, "commander"),
    LEGACY(4, "legacy"),
    VINTAGE(5, "vintage"),
    PAUPER(6, "pauper"),
    CUSTOM(7, "casual"),
    FRONTIER(8, "casual"),
    FUTURE_STANDARD(9, "standard"),
    PENNY_DREADFUL(10, "casual"),
    COMMANDER_1V1(11, "commander"),
    DUEL_COMMANDER(12, "commander"),
    BRAWL(13, "brawl"),
    OATHBREAKER(14, "oathbreaker"),
    PIONEER(15, "pioneer"),
    HISTORIC(16, "historic"),
    PAUPER_COMMANDER(17, "pauper"),
    ;

    companion object {
        /** Resolves the [ArchidektFormat] for a given API id, or null if unknown. */
        fun fromApiId(id: Int): ArchidektFormat? = entries.firstOrNull { it.apiId == id }

        /** Maps an Archidekt format id to a ManaHub format string, defaulting to "casual". */
        fun toManaHubFormat(apiId: Int): String = fromApiId(apiId)?.manahubFormat ?: "casual"
    }
}
