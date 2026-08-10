package com.mmg.manahub.core.domain.usecase.search

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.SearchOrder
import com.mmg.manahub.core.model.SearchPrefer

class BuildScryfallQueryUseCase {

    operator fun invoke(query: AdvancedSearchQuery): String {
        val parts = query.criteria.mapNotNull { buildPart(it) }
        val queryString = parts.joinToString(" ")

        return buildString {
            append(queryString)
            if (query.orderBy != SearchOrder.NAME) {
                if (queryString.isNotBlank()) append(" ")
                append("order:${query.orderBy.scryfallValue}")
                append(" direction:${query.direction.scryfallValue}")
                append(" prefer:${SearchPrefer.BEST}")
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
                criterion.value.split(Regex("\\s+")).filter { it.isNotBlank() }
                    .joinToString(" AND ", prefix = "(", postfix = ")") { "oracle:${escapeValue(it)}" }
            }
            is SearchCriterion.CardType -> {
                if (criterion.types.isEmpty()) return null
                val parts = criterion.types.map { "t:${escapeValue(it)}" }
                if (criterion.matchAll) {
                    parts.joinToString(",", prefix = "(", postfix = ")")
                } else {
                    "(${parts.joinToString(" OR ", prefix = "(", postfix = ")")})"
                }
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
                (criterion.rarity).joinToString(" OR ", prefix = "(", postfix = ")") {"r:${it.lowercase()}" }

            is SearchCriterion.CardSet -> {
                if (criterion.setCodes.isEmpty()) return null
                if (criterion.setCodes.size == 1)
                    "set:${criterion.setCodes.first().lowercase()}"
                else {
                    val parts = criterion.setCodes.joinToString(" OR ", prefix = "(", postfix = ")") {
                        "set:${it.lowercase()}"
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
                (criterion.format).joinToString(" AND ", prefix = "(", postfix = ")") {if (criterion.legal) "f:" + it.lowercase() else "banned:" + it.lowercase() }
            }
            is SearchCriterion.Language ->
                "lang:${criterion.langCode}"
            is SearchCriterion.Artist ->
                "a:${escapeValue(criterion.value)}"
            is SearchCriterion.FlavorText ->
                "ft:${escapeValue(criterion.value)}"
            // Collection-local filters have no Scryfall equivalent
            is SearchCriterion.CollectionStatus,
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
