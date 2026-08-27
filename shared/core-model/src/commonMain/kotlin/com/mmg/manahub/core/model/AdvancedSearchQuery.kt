package com.mmg.manahub.core.model

/** Comparison operators for numeric search criteria. */
enum class ComparisonOperator(val symbol: String) {
    LESS("<"),
    LESS_OR_EQUAL("<="),
    EQUAL("="),
    GREATER_OR_EQUAL(">="),
    GREATER(">"),
    NOT_EQUAL("!=")
}

/** An individual search criterion contributed to a Scryfall query. */
sealed class SearchCriterion {

    data class Name(
        val value: String,
        val exact: Boolean = false,
    ) : SearchCriterion()

    data class OracleText(val value: String) : SearchCriterion()

    // Suggestions Tab UI Polish plan (W11, 2026-08-25): [exclude] renders every listed type
    // NEGATED and AND'd together (`-t:x -t:y`) instead of the normal positive match — needed for
    // Deck Analysis's Curve sections ("mv:N" -> "mv=N -t:land", excluding basic lands which always
    // carry cmc 0 and would otherwise pollute the "0 mana value" bucket). Appended last, defaulted
    // false so every existing construction site is unaffected.
    data class CardType(val types: Set<String>, val matchAll: Boolean = true, val exclude: Boolean = false) : SearchCriterion()

    /** Scryfall "function" oracle-tag facet (see [CardFunctionOption]). */
    data class CardFunction(val functions: Set<String>, val matchAll: Boolean = false) : SearchCriterion()

    data class Colors(
        val colors: Set<String>,
        val exactly: Boolean = false,
    ) : SearchCriterion()

    data class ColorIdentity(
        val colors: Set<String>,
        val exactly: Boolean = false,
    ) : SearchCriterion()

    data class ManaCost(
        val value: Int,
        val operator: ComparisonOperator = ComparisonOperator.EQUAL,
    ) : SearchCriterion()

    data class Rarity(
        val rarity: List<String>) : SearchCriterion()

    data class CardSet(val setCodes: Set<String>) : SearchCriterion()

    data class Power(
        val value: Int,
        val operator: ComparisonOperator = ComparisonOperator.EQUAL,
    ) : SearchCriterion()

    data class Toughness(
        val value: Int,
        val operator: ComparisonOperator = ComparisonOperator.EQUAL,
    ) : SearchCriterion()

    data class Loyalty(
        val value: Int,
        val operator: ComparisonOperator = ComparisonOperator.EQUAL,
    ) : SearchCriterion()

    data class Price(
        val value: Double,
        val currency: String,
        val operator: ComparisonOperator = ComparisonOperator.LESS_OR_EQUAL,
    ) : SearchCriterion()

    data class Format(
        val format: List<String>,
        val legal: Boolean = true,
    ) : SearchCriterion()


    data class Language(val langCode: String) : SearchCriterion()

    data class Artist(val value: String) : SearchCriterion()

    data class FlavorText(val value: String) : SearchCriterion()

    // ── Suggestions Tab UI Polish plan (W11, 2026-08-25) — new criteria extending this model for
    //    structural Deck Analysis "Browse for X" fragments that had no existing field ──────────

    /**
     * Scryfall `produces:`/`produces>=` (mana-PRODUCTION, i.e. what a card can tap for) — distinct
     * from [Colors]/[ColorIdentity], which describe a card's own printed color/identity, not what
     * it taps for. [colors] renders one `produces:X` clause per color, AND'd together (Deck
     * Analysis's Mana Base per-color sections, e.g. "White sources"); [minDistinctColors] renders a
     * count-based `produces>=N` threshold instead (the general "mana fixing" role — Sol Ring-style
     * artifacts count too, not just lands). The two are mutually exclusive in practice (a section
     * asks for a SPECIFIC color OR a general fixing THRESHOLD, never both) but nothing enforces
     * that at the type level. [requireLand] additionally ANDs `t:land` — Mana Base's per-color
     * sections are land-only; the general mana-fixing role is not.
     */
    data class ManaProduction(
        val colors: Set<String> = emptySet(),
        val minDistinctColors: Int? = null,
        val requireLand: Boolean = false,
    ) : SearchCriterion()

    /**
     * Structured, CURATED oracle-text search — distinct from [OracleText] (a single free-text
     * user-TYPED phrase, whitespace-split and escaped per word by `BuildScryfallQueryUseCase`,
     * which would corrupt a multi-word curated phrase like `"sacrifice a creature:"` into
     * disconnected single-word AND terms). Built exclusively from CURATED, compile-time-constant
     * term lists (Deck Analysis's `SectionSearchQuery` TagDictionary-translated role fragments) —
     * never user input — so it is rendered VERBATIM, with no `escapeValue()` pass, same rationale
     * [SearchCriterion.CardFunction]'s own KDoc documents for skipping escaping.
     *
     * @property allOf terms AND'd together as quoted `oracle:"x"` clauses.
     * @property anyOfGroups each inner list is OR'd together inside one parenthesized group
     *   (`(oracle:"a" or oracle:"b")`); multiple groups are AND'd with each other and with [allOf].
     * @property typeLineAnyOf type-line terms OR'd together as `t:x` clauses (e.g. `aura_buff`'s
     *   "must be enchanting an Aura").
     */
    data class OracleTerms(
        val allOf: List<String> = emptyList(),
        val anyOfGroups: List<List<String>> = emptyList(),
        val typeLineAnyOf: List<String> = emptyList(),
    ) : SearchCriterion()

    // ── Collection-local filters (not translated to Scryfall syntax) ──────────

    data class CollectionStatus(val wishlist: Boolean, val forTrade: Boolean) : SearchCriterion()

    /** Matches cards that have ANY of the given tag keys (in auto-tags OR user-tags). */
    data class HasTag(val keys: List<String>) : SearchCriterion()
}

data class AdvancedSearchQuery(
    val criteria: List<SearchCriterion> = emptyList(),
    val orderBy: SearchOrder = SearchOrder.NAME,
    val direction: SearchDirection = SearchDirection.ASC,
    val prefer: SearchPrefer = SearchPrefer.BEST,
) {
    fun isEmpty() = criteria.isEmpty()
}

enum class SearchOrder(val scryfallValue: String) {
    NAME("name"),
    CMC("cmc"),
    PRICE_USD("usd"),
    PRICE_EUR("eur"),
    RARITY("rarity"),
    RELEASED("released"),
    COLOR("color"),
}

enum class SearchDirection(val scryfallValue: String) {
    ASC("asc"),
    DESC("desc"),
}

enum class SearchPrefer(val scryfallValue: String) {
    BEST("best"),
}
