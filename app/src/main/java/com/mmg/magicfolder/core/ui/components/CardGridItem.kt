package com.mmg.magicfolder.core.ui.components


import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.magicfolder.core.domain.model.UserCardWithCard

@Composable
fun CardGridItem(
    item: UserCardWithCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card     = item.card
    val userCard = item.userCard

    Card(
        onClick   = onClick,
        modifier  = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border    = if (card.isStale)
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
        else
            BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            // Card image
            Box {
                AsyncImage(
                    model             = card.imageArtCrop ?: card.imageNormal,
                    contentDescription = card.name,
                    contentScale      = ContentScale.Crop,
                    modifier          = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(MaterialTheme.shapes.small),
                )
                // Rarity dot
                RarityDot(
                    rarity   = card.rarity,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                )
                // Quantity badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                    color    = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    shape    = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text     = "×${userCard.quantity}${if (userCard.isFoil) " ✦" else ""}",
                        style    = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            // Card name + price
            Column(modifier = Modifier.padding(6.dp)) {
                Text(
                    text     = card.name,
                    style    = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val price = if (userCard.isFoil) card.priceUsdFoil else card.priceUsd
                if (price != null) {
                    Text(
                        text  = "$${String.format("%.2f", price)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}