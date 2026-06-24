package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Renders a set symbol SVG from Scryfall tinted with the card's rarity colour.
 *
 * @param setCode  Scryfall set code, e.g. "ltr", "mh3".
 * @param rarity   [CardRarity] used to tint the symbol.
 * @param size     Width/height of the rendered icon.
 */
@Composable
fun SetSymbol(
    setCode: String,
    rarity: CardRarity,
    size: Dp = 16.dp,
    modifier: Modifier = Modifier,
) {
    val url = "https://svgs.scryfall.io/sets/${setCode.lowercase()}.svg"
    val fallbackPainter = rememberVectorPainter(SetSymbolFallbackIcon)
    AsyncImage(
        model = url,
        contentDescription = "$setCode ${rarity.name}",
        error              = fallbackPainter,
        fallback           = fallbackPainter,
        colorFilter        = ColorFilter.tint(rarity.tint),
        contentScale       = ContentScale.Fit,
        modifier           = modifier.size(size),
    )
}
