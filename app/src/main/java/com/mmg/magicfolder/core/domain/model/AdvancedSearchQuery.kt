package com.mmg.magicfolder.core.domain.model

// Operadores de comparación para valores numéricos
enum class ComparisonOperator(val symbol: String) {
    LESS("<"),
    LESS_OR_EQUAL("<="),
    EQUAL("="),
    GREATER_OR_EQUAL(">="),
    GREATER(">"),
    NOT_EQUAL("!=")
}

// Un criterio individual de búsqueda
sealed class SearchCriterion {

    data class Name(
        val value: String,
        val exact: Boolean = false,
    ) : SearchCriterion()

    data class OracleText(val value: String) : SearchCriterion()

    data class CardType(val value: String) : SearchCriterion()

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
        val rarity: String,
        val operator: ComparisonOperator = ComparisonOperator.EQUAL,
    ) : SearchCriterion()

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
        val format: String,
        val legal: Boolean = true,
    ) : SearchCriterion()

    data class Keyword(val value: String) : SearchCriterion()

    data class Artist(val value: String) : SearchCriterion()

    data class FlavorText(val value: String) : SearchCriterion()

    // ── Collection-local filters (not translated to Scryfall syntax) ──────────

    data class IsInWishlist(val value: Boolean) : SearchCriterion()

    data class IsForTrade(val value: Boolean) : SearchCriterion()

    /** Matches cards that have ANY of the given tag keys (in auto-tags OR user-tags). */
    data class HasTag(val keys: List<String>) : SearchCriterion()
}

data class AdvancedSearchQuery(
    val criteria: List<SearchCriterion> = emptyList(),
    val orderBy: SearchOrder = SearchOrder.NAME,
    val direction: SearchDirection = SearchDirection.ASC,
) {
    fun isEmpty() = criteria.isEmpty()
}

enum class SearchOrder(val scryfallValue: String) {
    NAME("name"),
    CMC("cmc"),
    PRICE("usd"),
    RARITY("rarity"),
    RELEASED("released"),
    COLOR("color"),
}

enum class SearchDirection(val scryfallValue: String) {
    ASC("asc"),
    DESC("desc"),
}
