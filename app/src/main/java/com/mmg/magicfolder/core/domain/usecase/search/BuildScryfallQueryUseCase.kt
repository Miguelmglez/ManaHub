package com.mmg.magicfolder.core.domain.usecase.search

import com.mmg.magicfolder.core.domain.model.AdvancedSearchQuery
import com.mmg.magicfolder.core.domain.model.SearchCriterion
import com.mmg.magicfolder.core.domain.model.SearchOrder
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
            is SearchCriterion.CardSet ->
                "s:${criterion.setCode.lowercase()}"
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
        }
    }

    private fun escapeValue(value: String): String =
        if (value.contains(' ')) "\"$value\"" else value
}
