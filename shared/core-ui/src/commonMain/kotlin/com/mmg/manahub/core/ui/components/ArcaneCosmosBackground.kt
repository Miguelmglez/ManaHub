package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

@Composable
fun ArcaneCosmosBackground(
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.06f),
) {
    val stars = remember {
        (0..120).map {
            Triple(
                (0..1000).random() / 1000f,
                (0..1000).random() / 1000f,
                (1..3).random().toFloat(),
            )
        }
    }

    Canvas(modifier = modifier) {
        stars.forEach { (relX, relY, starSize) ->
            drawCircle(
                color  = color,
                radius = starSize,
                center = Offset(size.width * relX, size.height * relY),
            )
        }
        // Brighter accent stars
        stars.take(10).forEach { (relX, relY, _) ->
            drawCircle(
                color  = color.copy(alpha = color.alpha * 2.5f),
                radius = 1.5f,
                center = Offset(size.width * relX, size.height * relY),
            )
        }
    }
}
