package com.mmg.manahub.core.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mmg.manahub.core.ui.theme.ManaFontFamily
import com.mmg.manahub.core.ui.theme.MulishFontFamily

// ── Unicode glyphs from the Mana font ─────────────────────────────────────────
// Reference: https://andrewgioia.github.io/mana/cheatsheet.html
object ManaGlyph {
    const val W = "\uE600"   // White mana
    const val U = "\uE601"   // Blue mana
    const val B = "\uE602"   // Black mana
    const val R = "\uE603"   // Red mana
    const val G = "\uE604"   // Green mana
    const val C = "\uE904"   // Colorless
    const val TAP = "\uE612" // Tap symbol
    const val ZERO  = "\uE605"
    const val ONE   = "\uE606"
    const val TWO   = "\uE607"
    const val THREE = "\uE608"
    const val FOUR  = "\uE609"
    const val FIVE  = "\uE60A"
    const val SIX   = "\uE60B"
    const val SEVEN = "\uE60C"
    const val EIGHT = "\uE60D"
    const val NINE  = "\uE60E"
    const val TEN   = "\uE60F"
    const val X     = "\uE610"
    const val PHYREXIAN_W = "\uE62A"
    const val PHYREXIAN_U = "\uE62B"
    const val PHYREXIAN_B = "\uE62C"
    const val PHYREXIAN_R = "\uE62D"
    const val PHYREXIAN_G = "\uE62E"
}

enum class ManaColor(
    val glyph: String,
    val background: Color,
    val border: Color,
    val textColor: Color,
    val label: String,
) {
    W(ManaGlyph.W, Color(0xFFF0E8C0), Color(0xFFC8B86E), Color(0xFF5A3A00), "White"),
    U(ManaGlyph.U, Color(0xFF1A78C2), Color(0xFF4CC9F0), Color(0xFFA8E0F8), "Blue"),
    B(ManaGlyph.B, Color(0xFF3D2B6E), Color(0xFF9966CC), Color(0xFFD8B8FF), "Black"),
    R(ManaGlyph.R, Color(0xFF8B1A1A), Color(0xFFE63946), Color(0xFFFFC0B8), "Red"),
    G(ManaGlyph.G, Color(0xFF1A6640), Color(0xFF57CC99), Color(0xFFA8E8C0), "Green"),
    C(ManaGlyph.C, Color(0xFF444444), Color(0xFF888888), Color(0xFFCCCCCC), "Colorless"),
}

@Composable
fun ManaSymbol(
    color: ManaColor,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color.background)
            .border(
                width = (size.value * 0.06f).dp,
                color = color.border,
                shape = CircleShape,
            ),
    ) {
        Text(
            text = color.glyph,
            fontFamily = ManaFontFamily,
            fontSize = (size.value * 0.55f).sp,
            color = color.textColor,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ManaSymbolRow(
    colors: List<ManaColor>,
    size: Dp = 20.dp,
    spacing: Dp = 3.dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        colors.forEach { ManaSymbol(color = it, size = size) }
    }
}

// ── Parser for Scryfall mana cost strings ────────────────────────────────────
// Converts "{2}{W}{U}" → list of rendered symbols

@Composable
fun ManaCost(
    manaCost: String,
    symbolSize: Dp = 18.dp,
    modifier: Modifier = Modifier,
) {
    val symbols = parseManaString(manaCost)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        symbols.forEach { symbol ->
            ManaSymbolSingle(symbol = symbol, size = symbolSize)
        }
    }
}

@Composable
private fun ManaSymbolSingle(symbol: String, size: Dp) {
    val manaColor = when (symbol.uppercase()) {
        "W" -> ManaColor.W
        "U" -> ManaColor.U
        "B" -> ManaColor.B
        "R" -> ManaColor.R
        "G" -> ManaColor.G
        "C" -> ManaColor.C
        else -> null
    }
    if (manaColor != null) {
        ManaSymbol(color = manaColor, size = size)
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF555555))
                .border(
                    width = (size.value * 0.06f).dp,
                    color = Color(0xFF888888),
                    shape = CircleShape,
                ),
        ) {
            Text(
                text = symbol,
                fontFamily = MulishFontFamily,
                fontSize = (size.value * 0.5f).sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

fun parseManaString(manaCost: String): List<String> =
    Regex("\\{([^}]+)\\}")
        .findAll(manaCost)
        .map { it.groupValues[1] }
        .toList()
