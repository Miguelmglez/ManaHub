package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * Small pill badge that displays a textual label (e.g. copy count, language code).
 *
 * @param label           Text to render inside the badge.
 * @param showBackground  When `true`, draws a translucent [surfaceVariant] pill with a border;
 *                        when `false`, renders just the text on a transparent background.
 */
@Composable
fun CopyBadge(
    label: String,
    modifier: Modifier = Modifier,
    showBackground: Boolean = true
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        color = if (showBackground) mc.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent,
        shape = MaterialTheme.shapes.extraSmall,
        border = if (showBackground) BorderStroke(0.5.dp, mc.textDisabled.copy(alpha = 0.2f)) else null,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = ty.labelSmall.copy(fontSize = if (showBackground) 10.sp else 14.sp),
            color = mc.textSecondary,
            modifier = Modifier.padding(
                horizontal = if (showBackground) 6.dp else 0.dp,
                vertical = if (showBackground) 2.dp else 0.dp
            ),
        )
    }
}

/**
 * Small golden pill badge indicating a foil printing.
 */
@Composable
fun FoilBadge() {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        color = mc.goldMtg.copy(alpha = 0.18f),
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(0.5.dp, mc.goldMtg.copy(alpha = 0.4f)),
    ) {
        Text(
            text     = "Foil",
            style    = ty.labelSmall.copy(fontSize = 10.sp),
            color    = mc.goldMtg,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

/**
 * Small coloured circle representing a card's rarity.
 *
 * @param rarity Scryfall rarity string (e.g. "mythic", "rare", "uncommon", "common").
 */
@Composable
fun RarityDot(rarity: String, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val color = when (rarity.lowercase()) {
        "mythic"   -> mc.primaryAccent
        "rare"     -> mc.goldMtg
        "uncommon" -> mc.textSecondary
        else       -> mc.textDisabled
    }
    Box(
        modifier = modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color)
    )
}
