package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    fontWeight: FontWeight? = null,
    showFrontOnly: Boolean = false,
) {
    val displayName = if (showFrontOnly) name.split(" // ").first() else name
    val mergedStyle = if (fontWeight != null) style.copy(fontWeight = fontWeight) else style

    // Find all occurrences of "A-" at the start, after " // ", or after a newline
    val alchemyRegex = Regex("(^| // |\\n)A-")

    if (displayName.contains(alchemyRegex)) {
        val inlineContentId = "alchemy_icon"
        val inlineContent = mapOf(
            inlineContentId to InlineTextContent(
                placeholder = Placeholder(
                    width = mergedStyle.fontSize.takeIf { it != TextStyle.Default.fontSize }?.let { it * 1.3f } ?: 18.sp,
                    height = mergedStyle.fontSize.takeIf { it != TextStyle.Default.fontSize }?.let { it * 1.1f } ?: 15.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = rememberVectorPainter(AlchemyIcon),
                        contentDescription = "Alchemy",
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
        )

        val annotatedString = buildAnnotatedString {
            var lastIndex = 0
            alchemyRegex.findAll(displayName).forEach { match ->
                append(displayName.substring(lastIndex, match.range.first))
                val matchValue = match.value
                when {
                    matchValue.startsWith(" // ") -> append(" // ")
                    matchValue.startsWith("\n") -> append("\n")
                }
                appendInlineContent(inlineContentId, "[A-]")
                lastIndex = match.range.last + 1
            }
            if (lastIndex < displayName.length) {
                append(displayName.substring(lastIndex))
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
            text = displayName,
            modifier = modifier,
            style = style,
            maxLines = maxLines,
            overflow = overflow,
            color = color
        )
    }
}
