package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.R

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
    val fallbackPainter = painterResource(R.drawable.ic_counter)
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = "$setCode ${rarity.name}",
        error              = fallbackPainter,
        fallback           = fallbackPainter,
        colorFilter        = ColorFilter.tint(rarity.tint),
        contentScale       = ContentScale.Fit,
        modifier           = modifier.size(size),
    )
}
