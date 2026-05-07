package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R

// ── Scryfall SVG base URL ─────────────────────────────────────────────────────
private const val SVG_BASE = "https://svgs.scryfall.io/card-symbols/"

/**
 * Converts a Scryfall mana-cost token (e.g. "W", "U/R", "2", "X") to the
 * corresponding Scryfall SVG URL.
 *
 * Scryfall file names:
 *   {W}   → W.svg
 *   {U/R} → UR.svg  (strip the slash for hybrid)
 *   {2/W} → 2W.svg
 *   {W/P} → WP.svg  (Phyrexian)
 */
private fun tokenToSvgUrl(token: String): String {
    val cleaned = token.replace("/", "")
    return "$SVG_BASE$cleaned.svg"
}

/**
 * Renders a single mana symbol as a Scryfall SVG image via Coil.
 *
 * @param token  Raw token from [parseManaString], e.g. "W", "2", "U/R".
 * @param size   Size of the rendered circle.
 */
@Composable
fun ManaSymbolImage(
    token: String,
    size: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(tokenToSvgUrl(token))
            .crossfade(true)
            .build(),
        contentDescription = token,
        contentScale       = ContentScale.Fit,
        modifier           = modifier.size(size),
    )
}

/**
 * Renders a full mana-cost string (e.g. "{2}{W}{U}") as a row of SVG symbols.
 */
@Composable
fun ManaCostImages(
    manaCost: String,
    symbolSize: Dp = 18.dp,
    spacing: Dp = 2.dp,
    modifier: Modifier = Modifier,
) {
    val tokens = parseManaString(manaCost)
    if (tokens.isEmpty()) return

    Row(
        modifier              = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        tokens.forEach { token ->
            ManaSymbolImage(token = token, size = symbolSize)
        }
    }
}

// ── Oracle / card text with inline mana symbols ───────────────────────────────

private val symbolRegex = Regex("\\{([^}]+)\\}")

fun parseManaString(manaCost: String): List<String> =
    symbolRegex
        .findAll(manaCost)
        .map { it.groupValues[1] }
        .toList()

/**
 * Renders oracle / printed card text with inline mana symbols.
 *
 * `{W}`, `{T}`, `{2/W}`, `{E}`, etc. are replaced by the corresponding
 * Scryfall SVG images loaded via Coil; all other text renders normally.
 */
@Composable
fun OracleText(
    text:     String,
    modifier: Modifier = Modifier,
    style:    TextStyle = LocalTextStyle.current,
) {
    // Split the text into alternating plain-text and symbol segments
    val segments = buildList {
        var cursor = 0
        symbolRegex.findAll(text).forEach { match ->
            if (match.range.first > cursor) add(Pair(false, text.substring(cursor, match.range.first)))
            add(Pair(true, match.groupValues[1]))   // true = symbol token
            cursor = match.range.last + 1
        }
        if (cursor < text.length) add(Pair(false, text.substring(cursor)))
    }

    // One InlineTextContent entry per distinct symbol token
    val symbolTokens = segments.filter { it.first }.map { it.second }.distinct()
    val inlineContent = symbolTokens.associate { token ->
        "sym_$token" to androidx.compose.foundation.text.InlineTextContent(
            placeholder = Placeholder(
                width  = style.fontSize.takeIf { it != TextStyle.Default.fontSize }
                    ?.let { it * 1.15f } ?: 15.sp,
                height = style.fontSize.takeIf { it != TextStyle.Default.fontSize }
                    ?.let { it * 1.15f } ?: 15.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
            ),
        ) {
            val sizeDp = with(androidx.compose.ui.platform.LocalDensity.current) {
                (style.fontSize.takeIf { it != TextStyle.Default.fontSize } ?: 14.sp)
                    .times(1.15f).toDp()
            }
            ManaSymbolImage(token = token, size = sizeDp)
        }
    }

    val annotated = buildAnnotatedString {
        segments.forEach { (isSymbol, value) ->
            if (isSymbol) appendInlineContent("sym_$value", "[$value]")
            else          append(value)
        }
    }

    Text(
        text          = annotated,
        inlineContent = inlineContent,
        modifier      = modifier,
        style         = style,
    )
}

/**
 * Renders a card name, substituting the "A-" prefix (and any occurrences after " // ")
 * with the Alchemy icon.
 */
@Composable
fun CardName(
    name: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    color: Color = Color.Unspecified,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
) {
    val mergedStyle = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style

    // Find all occurrences of "A-" at the start, after " // ", or after a newline
    // Regex matches "A-" at the beginning of string OR following " // " OR following a newline
    val alchemyRegex = Regex("(^| // |\\n)A-")

    if (name.contains(alchemyRegex)) {
        val inlineContentId = "alchemy_icon"
        val inlineContent = mapOf(
            inlineContentId to androidx.compose.foundation.text.InlineTextContent(
                placeholder = Placeholder(
                    width = mergedStyle.fontSize.takeIf { it != TextStyle.Default.fontSize }?.let { it * 1.3f } ?: 18.sp,
                    height = mergedStyle.fontSize.takeIf { it != TextStyle.Default.fontSize }?.let { it * 1.1f } ?: 15.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_alchemy),
                        contentDescription = "Alchemy",
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
        )

        val annotatedString = buildAnnotatedString {
            var lastIndex = 0
            alchemyRegex.findAll(name).forEach { match ->
                // Append text before the match
                append(name.substring(lastIndex, match.range.first))
                
                // If it matches a separator, we need to keep that part
                val matchValue = match.value
                when {
                    matchValue.startsWith(" // ") -> append(" // ")
                    matchValue.startsWith("\n") -> append("\n")
                }
                
                // Append the inline icon
                appendInlineContent(inlineContentId, "[A-]")
                
                lastIndex = match.range.last + 1
            }
            // Append remaining text
            if (lastIndex < name.length) {
                append(name.substring(lastIndex))
            }
        }

        Text(
            text = annotatedString,
            inlineContent = inlineContent,
            modifier = modifier,
            style = mergedStyle,
            maxLines = maxLines,
            overflow = overflow,
            color = color
        )
    } else {
        Text(
            text = name,
            modifier = modifier,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
            color = color
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun CardNamePreview() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardName(name = "A-Dorothea, Vengeful Victim\nA-Dorothea's Retribution", style = MaterialTheme.typography.headlineSmall)
            CardName(name = "A-Dorothea, Vengeful Victim // A-Dorothea's Retribution", style = MaterialTheme.typography.headlineSmall)
            CardName(name = "A-Tidal Wave", style = MaterialTheme.typography.headlineSmall)
            CardName(name = "Black Lotus", style = MaterialTheme.typography.headlineSmall)
            CardName(name = "A-Llanowar Elves", style = MaterialTheme.typography.bodyLarge, color = Color.Green)
        }
    }
}
