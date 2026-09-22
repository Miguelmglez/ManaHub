package com.mmg.manahub.core.domain.search

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.ComparisonOperator
import com.mmg.manahub.core.model.FriendCardSearchParams
import com.mmg.manahub.core.model.SearchCriterion

/**
 * Translates the Advanced Search model into `search_friend_cards` RPC params, pre-applying the
 * server's caps so normal UI input can never trigger an INVALID_ARGUMENT rejection.
 */
object FriendCardSearchMapper {

    const val MAX_TEXT_LENGTH = 100
    const val MAX_ARRAY_SIZE = 50

    /** Type and format arrays have a tighter server cap than the other arrays. */
    const val MAX_TYPE_OR_FORMAT_ARRAY_SIZE = 10

    /** Numeric criteria are clamped here so operator translation (v - 1, v + 1) can never overflow. */
    const val MAX_NUMERIC_VALUE = 1000

    /** Shorter free text is not sent: a 1-char substring matches nearly every card anyway. */
    const val MIN_NAME_LENGTH = 2

    private val COLOR_LETTERS = listOf("W", "U", "B", "R", "G", "C")
    private val FORMAT_ID = Regex("^[a-z]{2,20}$")
    private val DEFAULTS = FriendCardSearchParams()
    // Mirrors the RPC's type validation; anything else is rejected server-side with INVALID_ARGUMENT.
    private val TYPE_TOKEN = Regex("^[A-Za-z][A-Za-z' -]{0,39}$")
    private val WHITESPACE = Regex("\\s+")

    /** True when the RPC can evaluate [criterion]; everything else is hidden from the friend sheet. */
    fun isSupported(criterion: SearchCriterion): Boolean = when (criterion) {
        is SearchCriterion.Name,
        is SearchCriterion.OracleText,
        is SearchCriterion.CardType,
        is SearchCriterion.Colors,
        is SearchCriterion.ColorIdentity,
        is SearchCriterion.Rarity,
        is SearchCriterion.CardSet,
        is SearchCriterion.Format,
        is SearchCriterion.Language -> true
        is SearchCriterion.ManaCost -> criterion.operator != ComparisonOperator.NOT_EQUAL
        is SearchCriterion.Power -> criterion.operator != ComparisonOperator.NOT_EQUAL
        is SearchCriterion.Toughness -> criterion.operator != ComparisonOperator.NOT_EQUAL
        else -> false
    }

    /** True when the RPC accepts [type] (e.g. "Assembly-Worker", "Time Lord"; not Un-set joke types like "B.O.B."). */
    fun isValidType(type: String): Boolean = TYPE_TOKEN.matches(type.trim())

    /**
     * [query] with every criterion the RPC cannot evaluate removed, and card-type criteria reduced
     * to the types the RPC accepts (a criterion left with no valid type is dropped).
     */
    fun supportedOnly(query: AdvancedSearchQuery): AdvancedSearchQuery =
        query.copy(
            criteria = query.criteria.filter(::isSupported).mapNotNull { criterion ->
                if (criterion !is SearchCriterion.CardType) return@mapNotNull criterion
                val valid = criterion.types.filterTo(linkedSetOf(), ::isValidType)
                if (valid.isEmpty()) null else criterion.copy(types = valid)
            }
        )

    /** Trimmed, whitespace-collapsed, capped name; empty when shorter than [MIN_NAME_LENGTH]. */
    fun normalizeName(raw: String): String {
        val collapsed = raw.trim().replace(WHITESPACE, " ").take(MAX_TEXT_LENGTH).trim()
        return if (collapsed.length < MIN_NAME_LENGTH) "" else collapsed
    }

    /**
     * Builds the RPC params. [name] is the search-bar text (already normalized or raw); a
     * [SearchCriterion.Name] inside [query] is used only when [name] is blank.
     */
    fun toParams(name: String, nameExact: Boolean, query: AdvancedSearchQuery): FriendCardSearchParams {
        val criteria = query.criteria.filter(::isSupported)
        val nameCriterion = criteria.filterIsInstance<SearchCriterion.Name>().firstOrNull()
        val effectiveName = normalizeName(name).ifEmpty { nameCriterion?.value?.let(::normalizeName).orEmpty() }
        val effectiveExact = if (normalizeName(name).isNotEmpty()) nameExact else nameCriterion?.exact ?: false

        val typesAll = mutableListOf<String>()
        val typesAny = mutableListOf<String>()
        val typesExclude = mutableListOf<String>()
        criteria.filterIsInstance<SearchCriterion.CardType>().forEach { c ->
            val valid = c.types.filter(::isValidType)
            when {
                c.exclude -> typesExclude += valid
                c.matchAll -> typesAll += valid
                else -> typesAny += valid
            }
        }

        val colors = criteria.filterIsInstance<SearchCriterion.Colors>().firstOrNull()
        val identity = criteria.filterIsInstance<SearchCriterion.ColorIdentity>().firstOrNull()
        val format = criteria.filterIsInstance<SearchCriterion.Format>().firstOrNull()
        val colorLetters = colors?.colors?.let(::colorLetters)
        val identityLetters = identity?.colors?.let(::colorLetters)

        val mv = Bounds()
        val power = Bounds()
        val toughness = Bounds()
        criteria.forEach { c ->
            when (c) {
                is SearchCriterion.ManaCost -> mv.constrain(c.operator, c.value)
                is SearchCriterion.Power -> power.constrain(c.operator, c.value)
                is SearchCriterion.Toughness -> toughness.constrain(c.operator, c.value)
                else -> Unit
            }
        }

        return FriendCardSearchParams(
            name = effectiveName.ifEmpty { null },
            nameExact = effectiveName.isNotEmpty() && effectiveExact,
            oracleText = criteria.filterIsInstance<SearchCriterion.OracleText>()
                .firstNotNullOfOrNull { cleanText(it.value) },
            typesAll = cleanList(typesAll, maxSize = MAX_TYPE_OR_FORMAT_ARRAY_SIZE),
            typesAny = cleanList(typesAny, maxSize = MAX_TYPE_OR_FORMAT_ARRAY_SIZE),
            typesExclude = cleanList(typesExclude, maxSize = MAX_TYPE_OR_FORMAT_ARRAY_SIZE),
            colors = colorLetters,
            colorsMode = colors?.takeIf { colorLetters != null }?.mode ?: DEFAULTS.colorsMode,
            identity = identityLetters,
            identityMode = identity?.takeIf { identityLetters != null }?.mode ?: DEFAULTS.identityMode,
            mvMin = mv.min,
            mvMax = mv.max,
            powerMin = power.min,
            powerMax = power.max,
            toughnessMin = toughness.min,
            toughnessMax = toughness.max,
            rarities = cleanList(criteria.filterIsInstance<SearchCriterion.Rarity>().flatMap { it.rarity }, lowercase = true),
            setCodes = cleanList(criteria.filterIsInstance<SearchCriterion.CardSet>().flatMap { it.setCodes }, lowercase = true),
            formats = format?.format.orEmpty()
                .map { it.trim().lowercase() }
                .filter { FORMAT_ID.matches(it) }
                .let { cleanList(it, maxSize = MAX_TYPE_OR_FORMAT_ARRAY_SIZE) },
            formatLegal = format?.legal ?: true,
            languages = cleanList(criteria.filterIsInstance<SearchCriterion.Language>().map { it.langCode }, lowercase = true),
        )
    }

    private fun cleanText(value: String): String? =
        value.trim().take(MAX_TEXT_LENGTH).trim().ifEmpty { null }

    private fun cleanList(
        values: Collection<String>,
        lowercase: Boolean = false,
        maxSize: Int = MAX_ARRAY_SIZE,
    ): List<String>? =
        values.asSequence()
            .map { it.trim() }
            .map { if (lowercase) it.lowercase() else it }
            .filter { it.isNotEmpty() && it.length <= MAX_TEXT_LENGTH }
            .distinct()
            .take(maxSize)
            .toList()
            .ifEmpty { null }

    // An empty set is "no constraint", mirroring AdvancedSearchCardMatcher.matchesColorSet.
    private fun colorLetters(colors: Set<String>): List<String>? {
        val wanted = colors.map { it.trim().uppercase() }.toSet()
        return COLOR_LETTERS.filter { it in wanted }.ifEmpty { null }
    }

    /** Intersects successive operator constraints into one inclusive min/max window. */
    private class Bounds {
        var min: Int? = null
            private set
        var max: Int? = null
            private set

        fun constrain(operator: ComparisonOperator, rawValue: Int) {
            val value = rawValue.coerceIn(-MAX_NUMERIC_VALUE, MAX_NUMERIC_VALUE)
            when (operator) {
                ComparisonOperator.LESS -> tightenMax(value - 1)
                ComparisonOperator.LESS_OR_EQUAL -> tightenMax(value)
                ComparisonOperator.EQUAL -> { tightenMin(value); tightenMax(value) }
                ComparisonOperator.GREATER_OR_EQUAL -> tightenMin(value)
                ComparisonOperator.GREATER -> tightenMin(value + 1)
                ComparisonOperator.NOT_EQUAL -> Unit
            }
        }

        private fun tightenMin(value: Int) { min = maxOf(min ?: value, value) }
        private fun tightenMax(value: Int) { max = minOf(max ?: value, value) }
    }
}
