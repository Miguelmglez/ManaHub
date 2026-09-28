package com.mmg.manahub.core.ui.components.search
// COMMENTS_REVIEWED: 2026-09-09

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.core.model.ColorMatchMode
import com.mmg.manahub.core.model.SearchCriterion

/**
 * Short, human-readable label for a locked [SearchCriterion] chip — shared by [AdvancedSearchSheet]'s
 * own locked-filter row and by callers (the Deck Wizard commander pick step) that render a summary
 * of an applied structured search outside the sheet, so the two never drift on wording for the same
 * lock (Deck Wizard Commander v3 plan, Phase 3.2/3.4, D14).
 *
 * Covers only the criteria this app currently locks anywhere; any other type falls back to its
 * simple class name — defensive, not expected to render in practice.
 */
fun SearchCriterion.shortLabel(): String = when (this) {
    SearchCriterion.CommanderEligible -> "Commander legal"
    is SearchCriterion.Format -> {
        val names = format.joinToString(", ") { it.replaceFirstChar(Char::uppercase) }
        if (legal) "Format: $names" else "Format: $names (banned)"
    }
    else -> this::class.simpleName ?: "Filter"
}

/** Human-readable chip label for an applied criterion; falls back to [shortLabel]. */
@Composable
fun SearchCriterion.displayLabel(): String = when (this) {
    is SearchCriterion.Name ->
        if (exact) "${stringResource(R.string.advsearch_chip_name_exact)}: $value"
        else "${stringResource(R.string.advsearch_section_name)}: $value"
    is SearchCriterion.OracleText -> stringResource(R.string.advsearch_chip_oracle, value)
    is SearchCriterion.CardType -> {
        val joined = types.joinToString(if (matchAll || exclude) " + " else " / ")
        if (exclude) stringResource(R.string.advsearch_chip_type_exclude, joined)
        else stringResource(R.string.advsearch_chip_type, joined)
    }
    is SearchCriterion.Colors ->
        stringResource(R.string.advsearch_chip_colors, colorModeLabel(mode), colors.colorLetters())
    is SearchCriterion.ColorIdentity ->
        stringResource(R.string.advsearch_chip_identity, colorModeLabel(mode), colors.colorLetters())
    is SearchCriterion.ManaCost -> stringResource(R.string.advsearch_chip_mana_value, operator.symbol, value)
    is SearchCriterion.Power -> stringResource(R.string.advsearch_chip_power, operator.symbol, value)
    is SearchCriterion.Toughness -> stringResource(R.string.advsearch_chip_toughness, operator.symbol, value)
    is SearchCriterion.Rarity ->
        stringResource(R.string.advsearch_chip_rarity, rarity.joinToString(", ") { it.replaceFirstChar(Char::uppercase) })
    is SearchCriterion.CardSet -> stringResource(R.string.advsearch_chip_set, setCodes.summarized())
    is SearchCriterion.Format -> {
        val names = format.joinToString(", ") { it.replaceFirstChar(Char::uppercase) }
        stringResource(if (legal) R.string.advsearch_chip_format_legal else R.string.advsearch_chip_format_not_legal, names)
    }
    is SearchCriterion.Language -> stringResource(R.string.advsearch_chip_language, langCode.uppercase())
    else -> shortLabel()
}

@Composable
private fun colorModeLabel(mode: ColorMatchMode): String =
    if (mode == ColorMatchMode.ANY_OF) stringResource(R.string.advsearch_chip_color_mode_any) else mode.colorsOperator

private val COLOR_ORDER = listOf("W", "U", "B", "R", "G", "C")

private fun Set<String>.colorLetters(): String {
    val upper = map { it.uppercase() }.toSet()
    return COLOR_ORDER.filter { it in upper }.joinToString("")
}

// Long set selections collapse to "A, B, C +N" so a chip never grows wider than the screen.
private fun Set<String>.summarized(): String {
    val codes = map { it.uppercase() }.sorted()
    return if (codes.size <= 3) codes.joinToString(", ") else codes.take(3).joinToString(", ") + " +${codes.size - 3}"
}
