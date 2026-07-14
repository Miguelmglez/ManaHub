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
 * Shield shape with a slash through it, used for protection or counters.
 * Replaces `R.drawable.ic_counter` which is an Android resource.
 */
internal val CounterIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Counter",
        defaultWidth = 54.dp,
        defaultHeight = 100.dp,
        viewportWidth = 54f,
        viewportHeight = 100f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(0f, 50.25f)
            lineToRelative(0.16f, -1.97f)
            horizontalLineToRelative(-0.06f)
            lineToRelative(0.06f, -0.03f)
            lineToRelative(2.06f, -26.24f)
            reflectiveCurveTo(3.25f, 40.16f, 6.31f, 45.4f)
            curveTo(7.62f, 44.88f, 8.96f, 44.42f, 10.31f, 44f)
            curveToRelative(3.31f, -8.66f, 4.47f, -34.38f, 4.47f, -34.38f)
            reflectiveCurveToRelative(0.77f, 23.43f, 3.68f, 32.53f)
            curveToRelative(1.6f, -0.24f, 3.22f, -0.41f, 4.87f, -0.51f)
            curveToRelative(3.01f, -11.19f, 3.82f, -41.64f, 3.82f, -41.64f)
            reflectiveCurveToRelative(0.99f, 30.52f, 3.95f, 41.67f)
            curveToRelative(1.58f, 0.11f, 3.13f, 0.29f, 4.65f, 0.53f)
            curveToRelative(2.87f, -9.06f, 4.02f, -32.6f, 4.02f, -32.6f)
            reflectiveCurveToRelative(0.93f, 25.86f, 3.94f, 34.45f)
            curveToRelative(1.32f, 0.41f, 2.62f, 0.87f, 3.89f, 1.38f)
            curveToRelative(3.34f, -5.18f, 4.51f, -23.39f, 4.51f, -23.39f)
            lineToRelative(1.59f, 26.22f)
            lineToRelative(0.07f, 0.03f)
            horizontalLineToRelative(-0.06f)
            lineToRelative(0.12f, 1.95f)
            reflectiveCurveToRelative(-26.69f, 8.69f, -26.69f, 49.49f)
            curveToRelative(0f, -40.6f, -27.15f, -49.49f, -27.15f, -49.49f)
        }
    }.build()
}

/** Alias for [CounterIcon] used as fallback icon for set symbols. */
val SetSymbolFallbackIcon: ImageVector get() = CounterIcon

/**
 * Magnifying glass icon (Material "Search").
 * Replaces `Icons.Default.Search` which requires material-icons-core (no wasmJs artifact).
 */
internal val SearchIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Search",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16
            // c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5z
            moveTo(15.5f, 14f)
            horizontalLineToRelative(-0.79f)
            lineToRelative(-0.28f, -0.27f)
            curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
            curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
            reflectiveCurveTo(-6.5f, 2.91f, -6.5f, 6.5f)
            reflectiveCurveTo(2.91f, 6.5f, 6.5f, 6.5f)
            curveTo(11.11f, 16f, 12.59f, 15.41f, 13.73f, 14.43f)
            lineToRelative(0.27f, 0.28f)
            verticalLineToRelative(0.79f)
            lineToRelative(5f, 4.99f)
            lineTo(20.49f, 19f)
            lineToRelative(-4.99f, -5f)
            close()
            // M9.5 14C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z
            moveTo(9.5f, 14f)
            curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
            reflectiveCurveTo(2.01f, -4.5f, 4.5f, -4.5f)
            reflectiveCurveTo(4.5f, 2.01f, 4.5f, 4.5f)
            reflectiveCurveTo(-2.01f, 4.5f, -4.5f, 4.5f)
            close()
        }
    }.build()
}

/**
 * X mark icon (Material "Close" / "Clear").
 * Replaces `Icons.Default.Clear` which requires material-icons-core (no wasmJs artifact).
 */
internal val ClearIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Clear",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(19f, 6.41f)
            lineTo(17.59f, 5f)
            lineTo(12f, 10.59f)
            lineTo(6.41f, 5f)
            lineTo(5f, 6.41f)
            lineTo(10.59f, 12f)
            lineTo(5f, 17.59f)
            lineTo(6.41f, 19f)
            lineTo(12f, 13.41f)
            lineTo(17.59f, 19f)
            lineTo(19f, 17.59f)
            lineTo(13.41f, 12f)
            close()
        }
    }.build()
}

/**
 * Alias for [ClearIcon] — semantically "close a dialog/overlay".
 * Both [MagicToastCard] and [CardFullScreenDialog] reference this single vector.
 */
internal val CloseIcon: ImageVector get() = ClearIcon

/**
 * Right-pointing triangle (Material "PlayArrow").
 * Replaces `Icons.Default.PlayArrow` which requires material-icons-core (no wasmJs artifact).
 * Used as a video play overlay in [NewsItemCard].
 */
internal val PlayArrowIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PlayArrow",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 5f)
            verticalLineToRelative(14f)
            lineToRelative(11f, -7f)
            close()
        }
    }.build()
}

/**
 * Two curved arrows forming a cycle (Material "Sync" / "Loop").
 * Used as a card-flip icon. Replaces `Icons.Default.Flip` (no wasmJs artifact).
 */
internal val FlipIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Flip",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // Top-right arrow
            moveTo(12f, 6f)
            verticalLineToRelative(3f)
            lineToRelative(4f, -4f)
            lineToRelative(-4f, -4f)
            verticalLineToRelative(3f)
            curveTo(7.58f, 4f, 4f, 7.58f, 4f, 12f)
            curveTo(4f, 13.57f, 4.46f, 15.03f, 5.24f, 16.26f)
            lineTo(6.7f, 14.8f)
            curveTo(6.25f, 13.97f, 6f, 13.01f, 6f, 12f)
            curveTo(6f, 8.69f, 8.69f, 6f, 12f, 6f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            // Bottom-left arrow
            moveTo(18.76f, 7.74f)
            lineTo(17.3f, 9.2f)
            curveTo(17.74f, 10.04f, 18f, 10.99f, 18f, 12f)
            curveTo(18f, 15.31f, 15.31f, 18f, 12f, 18f)
            verticalLineToRelative(-3f)
            lineToRelative(-4f, 4f)
            lineToRelative(4f, 4f)
            verticalLineToRelative(-3f)
            curveTo(16.42f, 20f, 20f, 16.42f, 20f, 12f)
            curveTo(20f, 10.43f, 19.54f, 8.97f, 18.76f, 7.74f)
            close()
        }
    }.build()
}
