package com.mmg.manahub.core.domain.usecase.search

import com.mmg.manahub.core.domain.model.AdvancedSearchQuery
import com.mmg.manahub.core.domain.model.SearchCriterion
import com.mmg.manahub.core.domain.model.SearchOrder
import javax.inject.Inject

class BuildScryfallQueryUseCase @Inject constructor() {

    operator fun invoke(query: AdvancedSearchQuery): String {
        val parts = query.criteria.mapNotNull { buildPart(it) }
        val queryString = parts.joinToString(" ")

        return buildString {
            append(queryString)
            if (query.orderBy != SearchOrder.NAME) {
                if (queryString.isNotBlank()) append(" ")
                append("order:${query.orderBy.scryfallValue}")
                append(" direction:${query.direction.scryfallValue}")
            }
        }.trim()
    }

    private fun buildPart(criterion: SearchCriterion): String? {
        return when (criterion) {
            is SearchCriterion.Name -> {
                if (criterion.value.isBlank()) return null
                if (criterion.exact)
                    "!\"${criterion.value}\""
                else
                    "name:${escapeValue(criterion.value)}"
            }
            is SearchCriterion.OracleText -> {
                if (criterion.value.isBlank()) return null
                "o:${escapeValue(criterion.value)}"
            }
            is SearchCriterion.CardType -> {
                if (criterion.value.isBlank()) return null
                "t:${escapeValue(criterion.value)}"
            }
            is SearchCriterion.Colors -> {
                if (criterion.colors.isEmpty()) return null
                val colorStr = criterion.colors.joinToString("") { it.lowercase() }
                val op = if (criterion.exactly) "=" else ":"
                "c$op$colorStr"
            }
            is SearchCriterion.ColorIdentity -> {
                if (criterion.colors.isEmpty()) return null
                val colorStr = criterion.colors.joinToString("") { it.lowercase() }
                val op = if (criterion.exactly) "=" else ":"
                "id$op$colorStr"
            }
            is SearchCriterion.ManaCost ->
                "mv${criterion.operator.symbol}${criterion.value}"
            is SearchCriterion.Rarity ->
                "r${criterion.operator.symbol}${criterion.rarity}"
            is SearchCriterion.CardSet -> {
                if (criterion.setCodes.isEmpty()) return null
                if (criterion.setCodes.size == 1)
                    "s:${criterion.setCodes.first().lowercase()}"
                else {
                    val parts = criterion.setCodes.joinToString(" OR ") {
                        "s:${it.lowercase()}"
                    }
                    "($parts)"
                }
            }
            is SearchCriterion.Power ->
                "pow${criterion.operator.symbol}${criterion.value}"
            is SearchCriterion.Toughness ->
                "tou${criterion.operator.symbol}${criterion.value}"
            is SearchCriterion.Loyalty ->
                "loy${criterion.operator.symbol}${criterion.value}"
            is SearchCriterion.Price -> {
                val curr = criterion.currency.lowercase()
                "$curr${criterion.operator.symbol}${criterion.value}"
            }
            is SearchCriterion.Format -> {
                val prefix = if (criterion.legal) "" else "-"
                "${prefix}f:${criterion.format}"
            }
            is SearchCriterion.Keyword ->
                "kw:${escapeValue(criterion.value)}"
            is SearchCriterion.Artist ->
                "a:${escapeValue(criterion.value)}"
            is SearchCriterion.FlavorText ->
                "ft:${escapeValue(criterion.value)}"
            // Collection-local filters have no Scryfall equivalent
            is SearchCriterion.IsInWishlist,
            is SearchCriterion.IsForTrade,
            is SearchCriterion.HasTag -> null
        }
    }

    /**
     * Sanitize a free-text value before embedding it in a Scryfall search expression.
     *
     * Scryfall's query language interprets several characters as operators or
     * delimiters (quotes, parentheses, colons, comparison operators). Passing
     * user-supplied input verbatim allows a search for `foo" OR -f:vintage` to
     * inject an extra Scryfall clause and bypass format-legality filters or expose
     * unintended search results.
     *
     * Strategy: strip every character that carries syntactic meaning in Scryfall
     * queries, then wrap values containing spaces in double-quotes so multi-word
     * names are matched as a phrase rather than being split into separate tokens.
     *
     * Allowed through: letters, digits, hyphens, apostrophes, commas, periods,
     * and whitespace. Everything else is removed.
     */
    private fun escapeValue(value: String): String {
        val sanitized = value.replace(Regex("""[^a-zA-Z0-9\-',.\s]"""), "")
        return if (sanitized.contains(' ')) "\"$sanitized\"" else sanitized
    }
}
