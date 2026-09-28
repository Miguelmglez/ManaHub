package com.mmg.manahub.feature.draft.presentation.ui

// COMMENTS_REVIEWED: 2026-09-17

import com.mmg.manahub.core.ui.components.parseManaString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** A style applied to an editorial text segment. */
internal data class DraftGuideTextStyle(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val strikeThrough: Boolean = false,
    val colorToken: String? = null,
) {
    fun merge(markup: DraftGuideTextStyle): DraftGuideTextStyle = copy(
        bold = bold || markup.bold,
        italic = italic || markup.italic,
        strikeThrough = strikeThrough || markup.strikeThrough,
        colorToken = markup.colorToken ?: colorToken,
    )

    fun withColor(color: String?): DraftGuideTextStyle = copy(
        colorToken = color?.takeIf { it.isNotBlank() } ?: colorToken,
    )
}

/** A parsed segment from a schema-v2 draft guide text field. */
internal sealed interface DraftGuideRichTextSegment {
    data class Text(
        val value: String,
        val style: DraftGuideTextStyle = DraftGuideTextStyle(),
    ) : DraftGuideRichTextSegment

    data class Mana(
        val token: String,
    ) : DraftGuideRichTextSegment

    data class CardReference(
        val scryfallId: String,
        val name: String,
        val style: DraftGuideTextStyle = DraftGuideTextStyle(),
    ) : DraftGuideRichTextSegment
}

/** Parses legacy plain text and the additive schema-v2 editorial markup safely. */
internal object DraftGuideRichTextParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Parses a guide field without throwing for malformed inline JSON or unmatched markup. */
    fun parse(source: String): List<DraftGuideRichTextSegment> =
        parseContent(source, DraftGuideTextStyle()).coalesce()

    private fun parseContent(
        source: String,
        inheritedStyle: DraftGuideTextStyle,
    ): MutableList<DraftGuideRichTextSegment> {
        val result = mutableListOf<DraftGuideRichTextSegment>()
        var index = 0

        while (index < source.length) {
            val structured = if (source[index] == '{') {
                parseStructuredSegment(source, index, inheritedStyle)
            } else {
                null
            }
            if (structured != null) {
                result.addAll(structured.segments)
                index = structured.endExclusive
                continue
            }

            val marker = markerAt(source, index)
            if (marker != null) {
                val contentStart = index + marker.token.length
                val contentEnd = source.indexOf(marker.token, contentStart)
                if (contentEnd > contentStart && source.substring(contentStart, contentEnd).isNotBlank()) {
                    result.addAll(
                        parseContent(
                            source.substring(contentStart, contentEnd),
                            inheritedStyle.merge(marker.style),
                        ),
                    )
                    index = contentEnd + marker.token.length
                    continue
                }
                appendText(result, marker.token, inheritedStyle)
                index = contentStart
                continue
            }

            // Appending one plain run at a time keeps parsing linear; per-char appends were quadratic.
            var runEnd = index + 1
            while (runEnd < source.length && source[runEnd] != '{' && markerAt(source, runEnd) == null) {
                runEnd++
            }
            appendText(result, source.substring(index, runEnd), inheritedStyle)
            index = runEnd
        }

        return result
    }

    private fun parseStructuredSegment(
        source: String,
        start: Int,
        inheritedStyle: DraftGuideTextStyle,
    ): ParsedSegment? {
        val endExclusive = findBalancedObjectEnd(source, start) ?: return null
        val candidate = source.substring(start, endExclusive)
        val parsedObject = parseJsonObject(candidate)
        if (parsedObject != null) {
            val scryfallId = parsedObject.stringValue("scryfall_id")
            val name = parsedObject.stringValue("name")
            if (!scryfallId.isNullOrBlank() && !name.isNullOrBlank()) {
                return ParsedSegment(
                    endExclusive = endExclusive,
                    segments = listOf(
                        DraftGuideRichTextSegment.CardReference(
                            scryfallId = scryfallId,
                            name = name,
                            style = inheritedStyle,
                        ),
                    ),
                )
            }

            val inlineText = parsedObject.stringValue("text")
            if (inlineText != null) {
                val colorToken = parsedObject.stringValue("color")
                return ParsedSegment(
                    endExclusive = endExclusive,
                    segments = parseContent(
                        inlineText,
                        inheritedStyle.withColor(colorToken),
                    ),
                )
            }
        }

        val token = parseManaString(candidate)
            .singleOrNull()
            ?.takeIf { candidate == "{$it}" && !candidate.contains('"') }
        if (token != null) {
            return ParsedSegment(
                endExclusive = endExclusive,
                segments = listOf(DraftGuideRichTextSegment.Mana(token)),
            )
        }
        return null
    }

    private fun parseJsonObject(candidate: String): JsonObject? = runCatching {
        json.parseToJsonElement(candidate).jsonObject
    }.getOrNull()

    private fun findBalancedObjectEnd(source: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false

        for (index in start until source.length) {
            val character = source[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (character == '\\') {
                    escaped = true
                } else if (character == '"') {
                    inString = false
                }
                continue
            }

            when (character) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index + 1
                }
            }
        }
        return null
    }

    private fun markerAt(source: String, index: Int): Marker? = when {
        source.startsWith("**", index) -> Marker("**", DraftGuideTextStyle(bold = true))
        source.startsWith("__", index) -> Marker("__", DraftGuideTextStyle(bold = true))
        source.startsWith("~~", index) -> Marker("~~", DraftGuideTextStyle(strikeThrough = true))
        source[index] == '*' -> Marker("*", DraftGuideTextStyle(italic = true))
        source[index] == '_' -> Marker("_", DraftGuideTextStyle(italic = true))
        else -> null
    }

    private fun appendText(
        result: MutableList<DraftGuideRichTextSegment>,
        value: String,
        style: DraftGuideTextStyle,
    ) {
        if (value.isEmpty()) return
        val previous = result.lastOrNull() as? DraftGuideRichTextSegment.Text
        if (previous != null && previous.style == style) {
            result[result.lastIndex] = previous.copy(value = previous.value + value)
        } else {
            result += DraftGuideRichTextSegment.Text(value, style)
        }
    }

    private fun List<DraftGuideRichTextSegment>.coalesce(): List<DraftGuideRichTextSegment> {
        val result = mutableListOf<DraftGuideRichTextSegment>()
        for (segment in this) {
            if (segment is DraftGuideRichTextSegment.Text) {
                appendText(result, segment.value, segment.style)
            } else {
                result += segment
            }
        }
        return result
    }

    private data class Marker(
        val token: String,
        val style: DraftGuideTextStyle,
    )

    private data class ParsedSegment(
        val endExclusive: Int,
        val segments: List<DraftGuideRichTextSegment>,
    )
}

private fun JsonObject.stringValue(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull
