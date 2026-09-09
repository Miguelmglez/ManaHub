package com.mmg.manahub.core.ui.components.search
// COMMENTS_REVIEWED: 2026-09-09

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
