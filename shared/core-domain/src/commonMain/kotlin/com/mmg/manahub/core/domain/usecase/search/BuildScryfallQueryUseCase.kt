package com.mmg.manahub.core.domain.usecase.search

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.ColorMatchMode
import com.mmg.manahub.core.model.SearchCriterion
import com.mmg.manahub.core.model.SearchOrder
import com.mmg.manahub.core.model.SearchPrefer

class BuildScryfallQueryUseCase {

    private companion object {
        /** The picker's colorless option; not a Scryfall color letter (see [buildColorPart]). */
        const val COLORLESS = "C"
    }

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
                if (criterion.exclude) {
                    // Suggestions Tab UI Polish plan (W11): negated, AND'd together
                    // (`-t:x -t:y`) — every listed type must be ABSENT, never an OR of negations.
                    return criterion.types.joinToString(" ") { "-t:${escapeValue(it)}" }
                }
                val parts = criterion.types.map { "t:${escapeValue(it)}" }
                if (criterion.matchAll) {
                    parts.joinToString(",", prefix = "(", postfix = ")")
                } else {
                    "(${parts.joinToString(" OR ", prefix = "(", postfix = ")")})"
                }
            }
            is SearchCriterion.CardFunction -> {
                if (criterion.functions.isEmpty()) return null
                // Closed, hand-curated enum (CardFunctionOption) — values are already [a-z-],
                // never user-typed free text, so escapeValue() must NOT be applied here (it would
                // be a no-op at best; running curated constants through it is explicitly disallowed
                // by the plan to avoid ever accidentally mangling a future value).
                val parts = criterion.functions.map { "function:$it" }
                if (criterion.matchAll) {
                    parts.joinToString(" ")
                } else {
                    parts.joinToString(",", prefix = "(", postfix = ")")
                }
            }
            is SearchCriterion.Colors -> buildColorPart("c", criterion.colors, criterion.mode)
            is SearchCriterion.ColorIdentity -> buildColorPart("id", criterion.colors, criterion.mode)
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
            is SearchCriterion.ManaProduction -> {
                val parts = mutableListOf<String>()
                if (criterion.requireLand) parts += "t:land"
                criterion.colors.forEach { parts += "produces:${it.lowercase()}" }
                criterion.minDistinctColors?.let { parts += "produces>=$it" }
                if (parts.isEmpty()) return null
                parts.joinToString(" ")
            }
            is SearchCriterion.OracleTerms -> {
                if (criterion.allOf.isEmpty() && criterion.anyOfGroups.isEmpty() && criterion.typeLineAnyOf.isEmpty()) return null
                val parts = mutableListOf<String>()
                // Curated compile-time constants only (see this criterion's own KDoc) -- rendered
                // verbatim, deliberately NEVER through escapeValue() (would corrupt a multi-word
                // quoted phrase into disconnected single-word tokens).
                criterion.allOf.forEach { parts += "oracle:\"$it\"" }
                criterion.anyOfGroups.forEach { group ->
                    if (group.isNotEmpty()) {
                        parts += if (group.size == 1) "oracle:\"${group[0]}\""
                        else "(" + group.joinToString(" or ") { "oracle:\"$it\"" } + ")"
                    }
                }
                if (criterion.typeLineAnyOf.isNotEmpty()) {
                    parts += if (criterion.typeLineAnyOf.size == 1) "t:${criterion.typeLineAnyOf[0]}"
                    else "(" + criterion.typeLineAnyOf.joinToString(" or ") { "t:$it" } + ")"
                }
                parts.joinToString(" ")
            }
            // Collection-local filters have no Scryfall equivalent
            is SearchCriterion.CollectionStatus,
            is SearchCriterion.HasTag -> null
        }
    }

    /**
     * Renders one color facet — [prefix] is `c` (printed colors) or `id` (color identity).
     *
     * Every mode renders its EXPLICIT operator rather than the bare `:` alias: `:` means at-least
     * after `c` but at-most after `id`, and relying on that asymmetry is what let the local matcher
     * drift into the opposite semantics (see `ColorMatchMode`). The `when` is exhaustive with no
     * `else` on purpose — a new mode must not silently inherit another's rendering.
     *
     * `"C"` (colorless) is not a letter Scryfall accepts inside a color string, so it renders as
     * its own `=c` clause; under a set-comparison mode it is DROPPED when real colors are also
     * selected (`{W,C}` at-least is just `c>=w` — every colorless card already fails "contains W").
     *
     * @return `null` when nothing is selected, matching every other criterion's "not a constraint".
     */
    private fun buildColorPart(prefix: String, colors: Set<String>, mode: ColorMatchMode): String? {
        if (colors.isEmpty()) return null
        val nonColorless = colors.filterNot { it.equals(COLORLESS, ignoreCase = true) }
        val wantsColorless = nonColorless.size != colors.size
        // Case-duplicate letters (e.g. {"W","w"}) must collapse to one — otherwise this renders
        // c>=ww / a duplicate OR-branch for what the local matcher treats as a single letter.
        val letters = nonColorless.map { it.lowercase() }.distinct()
        return when (mode) {
            ColorMatchMode.ANY_OF -> {
                val alternatives = letters.map { "$prefix${ColorMatchMode.AT_LEAST.colorsOperator}$it" } +
                    if (wantsColorless) listOf("$prefix=c") else emptyList()
                if (alternatives.size == 1) alternatives.first()
                else alternatives.joinToString(" or ", prefix = "(", postfix = ")")
            }
            ColorMatchMode.AT_MOST,
            ColorMatchMode.EXACTLY,
            ColorMatchMode.AT_LEAST ->
                if (letters.isEmpty()) "$prefix=c"
                else "$prefix${mode.colorsOperator}${letters.joinToString("")}"
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
