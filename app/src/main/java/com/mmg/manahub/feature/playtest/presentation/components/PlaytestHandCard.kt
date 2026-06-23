package com.mmg.manahub.feature.playtest.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors

/**
 * A single MTG card rendered at full portrait aspect ratio (63 / 88 ≈ 0.716).
 *
 * @param card The card to display.
 * @param width The display width of this card slot.
 * @param onClick Called when the card is tapped (opens full-screen dialog).
 * @param isDragging Whether this card is currently being dragged (lifts visually).
 * @param modifier Additional modifiers (caller applies zIndex / offset for the fan layout).
 */
@Composable
fun PlaytestHandCard(
    card: Card,
    width: Dp,
    onClick: () -> Unit,
    isDragging: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors

    // Lift the card slightly when it is being dragged so it appears above others.
    val elevation = if (isDragging) 18.dp else 6.dp
    val scale = if (isDragging) 1.08f else 1f

    Box(
        modifier = modifier
            .width(width)
            .aspectRatio(63f / 88f)
            .scale(scale)
            .shadow(elevation = elevation, shape = CardShape)
            .clip(CardShape)
            .border(0.5.dp, mc.surfaceVariant, CardShape)
            .background(mc.surface)
            // Tap gesture handled separately from drag — both can coexist.
            .pointerInput(card.scryfallId) {
                detectTapGestures(onTap = { onClick() })
            },
    ) {
        AsyncImage(
            model              = card.imageNormal,
            contentDescription = card.name,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )
    }
}
