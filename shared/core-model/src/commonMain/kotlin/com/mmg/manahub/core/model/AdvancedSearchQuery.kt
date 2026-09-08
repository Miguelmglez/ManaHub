package com.mmg.manahub.core.model

// COMMENTS_REVIEWED: 2026-09-08

/**
 * How a color set is compared against a card's colors or color identity.
 *
 * This is deliberately a mode enum rather than an `exactly: Boolean`. The boolean left the
 * non-exact case implied, and the two evaluators read that implication in OPPOSITE directions:
 * `BuildScryfallQueryUseCase` rendered it `id:wub`, which Scryfall special-cases to mean "at most"
 * for identity, while `AdvancedSearchCardMatcher` tested "card identity contains all of these", a
 * superset. A Commander "Browse for Card Draw" therefore returned 97 cards on the All Cards tab and
 * 0 on the Collection tab over the same collection (2026-09-07). Every implementation must cover
 * all four modes explicitly; there is no default to fall through to.
 *
 * The literal token `"C"` may appear in a criterion's color set (the picker offers it) and means
 * COLORLESS, not a sixth color: it never joins the Scryfall letter string, it renders as its own
 * `c=c` / `id=c` clause. Both evaluators must agree on that too — see each mode's rendering rule.
 *
 * @property colorsOperator Scryfall comparison operator, valid after both `c` and `id`.
 */
enum class ColorMatchMode(val colorsOperator: String) {
    /** Card colors are a SUBSET of the given set — `id<=wub`, "fits inside this identity". */
    AT_MOST("<="),

    /** Card colors are exactly the given set — `id=wub`. */
    EXACTLY("="),

    /** Card colors are a SUPERSET of the given set — `c>=wu`, "contains all of these". */
    AT_LEAST(">="),

    /** Card colors INTERSECT the given set — rendered as an OR group, `(c>=w or c>=u)`. */
    ANY_OF(">="),
}

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

    /**
     * A card's printed colors (Scryfall `c`). [ColorMatchMode.AT_LEAST] is the default because it
     * is what the Advanced Search color picker means by "these colors".
     */
    data class Colors(
        val colors: Set<String>,
        val mode: ColorMatchMode = ColorMatchMode.AT_LEAST,
    ) : SearchCriterion()

    /**
     * A card's color IDENTITY (Scryfall `id`) — the Commander-legality property, not its printed
     * colors. [ColorMatchMode.AT_MOST] is the default because "fits in this deck's identity" is
     * what almost every caller wants, and because a superset test here is a semantic inversion that
     * silently returns the wrong cards (see [ColorMatchMode]).
     */
    data class ColorIdentity(
        val colors: Set<String>,
        val mode: ColorMatchMode = ColorMatchMode.AT_MOST,
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

    /**
     * Which of the user's three card lists the results are drawn from.
     *
     * The three sources are MUTUALLY EXCLUSIVE, not intersecting booleans: the predecessor
     * `CollectionStatus(wishlist, forTrade)` was AND'ed against the OWNED collection, so a
     * wishlisted card the user does not own could never match and the filter looked dead.
     */
    data class CollectionStatus(val source: CollectionSource) : SearchCriterion()

    /** Matches cards that have ANY of the given tag keys (in auto-tags OR user-tags). */
    data class HasTag(val keys: List<String>) : SearchCriterion()
}

/**
 * The list a collection-local search reads from.
 *
 * [WISHLIST] and [FOR_TRADE] are backed by their own tables, NOT by a flag on an owned collection
 * row — `UserCard.isForTrade` in particular is never written anywhere in the app.
 */
enum class CollectionSource { COLLECTION, WISHLIST, FOR_TRADE }

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
