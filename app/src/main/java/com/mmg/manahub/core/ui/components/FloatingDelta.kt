package com.mmg.manahub.core.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mmg.manahub.core.ui.theme.MulishFontFamily

@Composable
fun FloatingDelta(
    delta: Int?,
    positiveColor: Color = Color(0xFF57CC99),
    negativeColor: Color = Color(0xFFE63946),
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = delta != null,
        enter = slideInVertically(tween(150)) { if ((delta ?: 0) > 0) it else -it } +
                fadeIn(tween(100)),
        exit  = slideOutVertically(tween(400)) { if ((delta ?: 0) > 0) -it else it } +
                fadeOut(tween(600)),
        modifier = modifier,
    ) {
        delta?.let { d ->
            Text(
                text       = if (d > 0) "+$d" else "$d",
                fontFamily = MulishFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize   = 22.sp,
                color      = if (d > 0) positiveColor else negativeColor,
            )
        }
    }
}
