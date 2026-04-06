package com.mmg.magicfolder.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

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
    /*// Scryfall has no M.svg for multicolor — render a gold circle manually
    if (token.uppercase() == "M") {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFFB8860B))
                .border((size.value * 0.06f).dp, Color(0xFFDAA520), CircleShape),
        ) {
            Text(
                text       = "✦",
                fontSize   = (size.value * 0.48f).sp,
                color      = Color(0xFFFFF3CD),
                textAlign  = TextAlign.Center,
            )
        }
        return
    }*/
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
 *
 * Falls back gracefully to the Mana-font [ManaCost] composable if a symbol
 * fails to load (Coil shows nothing on error by default).
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
