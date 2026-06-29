package com.mmg.manahub.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors

private const val CARD_ASPECT_RATIO = 63f / 88f

/**
 * A generic MTG card display component with ManaHub styling.
 * 
 * Supports common card states like tapped, hidden (for animations), and custom elevation.
 */
@Composable
fun MagicCard(
    card: Card,
    modifier: Modifier = Modifier,
    width: Dp? = null,
    isTapped: Boolean = false,
    alpha: Float = 1f,
    elevation: Dp = 4.dp,
    onClick: (() -> Unit)? = null,
    sourceHidden: Boolean = false,
    animateAlpha: Boolean = true,
    shape: Shape = CardShape,
) {
    val mc = MaterialTheme.magicColors

    val targetAlpha = if (sourceHidden) 0f else alpha
    val sourceAlpha = if (animateAlpha) {
        animateFloatAsState(
            targetValue = targetAlpha,
            animationSpec = tween(durationMillis = 180),
            label = "MagicCardSourceAlpha",
        ).value
    } else {
        targetAlpha
    }

    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier)
            .aspectRatio(CARD_ASPECT_RATIO)
            .rotate(if (isTapped) 90f else 0f)
            .alpha(sourceAlpha)
            .shadow(elevation, shape)
            .clip(shape)
            .border(0.5.dp, mc.surfaceVariant, shape)
            .background(mc.surface)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else Modifier,
            ),
    ) {
        AsyncImage(
            model = card.imageNormal,
            contentDescription = card.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
