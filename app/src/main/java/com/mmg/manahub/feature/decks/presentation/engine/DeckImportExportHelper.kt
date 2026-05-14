package com.mmg.manahub.feature.decks.presentation.engine

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DeckCard

/**
 * Handles parsing and exporting decks in the Moxfield / MTG Arena text format.
 *
 * Supported import formats:
 *   4 Lightning Bolt
 *   4 Lightning Bolt (M11) 163
 *   4 Lightning Bolt (M11) 163 *F*
 *
 * Section headers (case-insensitive): Commander, Deck, Mainboard, Sideboard, Companion
 */
object DeckImportExportHelper {

    // ── Data model ────────────────────────────────────────────────────────────

    data class ParsedLine(
        val quantity: Int,
        val name: String,
        val setCode: String? = null,
        val collectorNumber: String? = null,
    )

    data class ParsedDeckList(
        val mainboard: List<ParsedLine>,
        val sideboard: List<ParsedLine>,
        val commander: ParsedLine?,
    )

    // ── Parser ────────────────────────────────────────────────────────────────

    private val CARD_LINE_REGEX = Regex(
        """^(\d+)[x×]?\s+(.+?)(?:\s+\(([A-Za-z0-9]+)\)\s+(\S+))?(?:\s+\*F\*)?$"""
    )

    private val SECTION_HEADERS = setOf(
        "deck", "mainboard", "main", "sideboard", "side", "sb",
        "commander", "companions", "companion",
    )

    private enum class Section { MAIN, SIDE, COMMANDER }

    fun parse(text: String): ParsedDeckList {
        val mainboard  = mutableListOf<ParsedLine>()
        val sideboard  = mutableListOf<ParsedLine>()
        var commander: ParsedLine? = null

        var currentSection = Section.MAIN

        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isBlank()) continue

            // Check for section header
            val lower = line.lowercase()
            when {
                lower == "commander"                          -> { currentSection = Section.COMMANDER; continue }
                lower in setOf("sideboard", "side", "sb")   -> { currentSection = Section.SIDE;      continue }
                lower in setOf("deck", "mainboard", "main") -> { currentSection = Section.MAIN;      continue }
                // "companion" / "companions" lines — treat as mainboard
                lower.startsWith("companion")                -> { currentSection = Section.MAIN;      continue }
                // Skip comment lines
                line.startsWith("//")                        -> continue
            }

            val parsed = parseLine(line) ?: continue

            when (currentSection) {
                Section.MAIN      -> mainboard.add(parsed)
                Section.SIDE      -> sideboard.add(parsed)
                Section.COMMANDER -> {
                    commander = parsed.copy(quantity = 1)
                    // Also add to mainboard so it's included in the deck
                }
            }
        }

        return ParsedDeckList(mainboard, sideboard, commander)
    }

    private fun parseLine(line: String): ParsedLine? {
        val match = CARD_LINE_REGEX.matchEntire(line) ?: return null
        val qty    = match.groupValues[1].toIntOrNull() ?: return null
        val name   = match.groupValues[2].trim()
        val set    = match.groupValues[3].takeIf { it.isNotBlank() }
        val num    = match.groupValues[4].takeIf { it.isNotBlank() }
        return ParsedLine(qty, name, set, num)
    }

    // ── Exporter ──────────────────────────────────────────────────────────────

    /**
     * Exports a deck to Moxfield / MTG Arena compatible text format.
     *
     * @param deckName    Name of the deck (used as a comment header)
     * @param mainboard   List of (DeckCard, includeSetInfo) pairs for the mainboard
     * @param sideboard   List of (DeckCard, includeSetInfo) pairs for the sideboard
     * @param commander   Optional commander card
     * @param includeSet  If true, includes set code and collector number: `4 Name (SET) 001`
     */
    fun export(
        deckName:   String,
        mainboard:  List<DeckCard>,
        sideboard:  List<DeckCard>,
        commander:  Card? = null,
        includeSet: Boolean = true,
    ): String = buildString {
        appendLine("// $deckName")
        appendLine()

        if (commander != null) {
            appendLine("Commander")
            appendLine(formatLine(1, commander, includeSet))
            appendLine()
        }

        appendLine("Deck")
        mainboard.forEach { dc ->
            appendLine(formatLine(dc.quantity, dc.card, includeSet))
        }

        if (sideboard.isNotEmpty()) {
            appendLine()
            appendLine("Sideboard")
            sideboard.forEach { dc ->
                appendLine(formatLine(dc.quantity, dc.card, includeSet))
            }
        }
    }.trimEnd()

    private fun formatLine(qty: Int, card: Card, includeSet: Boolean): String = buildString {
        append(qty)
        append(" ")
        append(card.name)
        if (includeSet && card.setCode.isNotBlank()) {
            append(" (")
            append(card.setCode.uppercase())
            append(") ")
            append(card.collectorNumber)
        }
    }
}
