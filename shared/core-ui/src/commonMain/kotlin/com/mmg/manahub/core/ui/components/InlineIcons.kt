package com.mmg.manahub.core.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Two overlapping rectangles representing stacked/multiple cards.
 * Replaces `Icons.Default.Style` which is unavailable on wasmJs.
 */
internal val StackedCardsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "StackedCards",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Back card (offset top-right)
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
        ) {
            moveTo(8f, 2f)
            lineTo(20f, 2f)
            arcTo(2f, 2f, 0f, false, true, 22f, 4f)
            lineTo(22f, 14f)
            arcTo(2f, 2f, 0f, false, true, 20f, 16f)
            lineTo(8f, 16f)
            arcTo(2f, 2f, 0f, false, true, 6f, 14f)
            lineTo(6f, 4f)
            arcTo(2f, 2f, 0f, false, true, 8f, 2f)
            close()
        }
        // Front card (offset bottom-left)
        path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.5f,
        ) {
            moveTo(4f, 8f)
            lineTo(16f, 8f)
            arcTo(2f, 2f, 0f, false, true, 18f, 10f)
            lineTo(18f, 20f)
            arcTo(2f, 2f, 0f, false, true, 16f, 22f)
            lineTo(4f, 22f)
            arcTo(2f, 2f, 0f, false, true, 2f, 20f)
            lineTo(2f, 10f)
            arcTo(2f, 2f, 0f, false, true, 4f, 8f)
            close()
        }
    }.build()
}

/**
 * Hexagon shape representing the Alchemy card prefix icon.
 * Replaces `R.drawable.ic_alchemy` which is an Android resource.
 */
internal val AlchemyIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Alchemy",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            lineTo(21.66f, 7f)
            lineTo(21.66f, 17f)
            lineTo(12f, 22f)
            lineTo(2.34f, 17f)
            lineTo(2.34f, 7f)
            close()
        }
    }.build()
}

/**
 * Diamond/gem shape used as fallback icon for set symbols.
 * Replaces `R.drawable.ic_counter` which is an Android resource.
 */
internal val SetSymbolFallbackIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SetSymbolFallback",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 2f)
            lineTo(22f, 12f)
            lineTo(12f, 22f)
            lineTo(2f, 12f)
            close()
        }
    }.build()
}
