package com.mmg.magicfolder.core.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.mmg.magicfolder.core.ui.theme.LocalPreferredCurrency
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.magicfolder.core.domain.model.UserCardWithCard
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.core.util.PriceFormatter

@Composable
fun CardGridItem(
    item:     UserCardWithCard,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card     = item.card
    val userCard = item.userCard
    val mc       = MaterialTheme.magicColors

    Card(
        onClick   = onClick,
        modifier  = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors    = CardDefaults.cardColors(containerColor = mc.surface),
        border    = if (card.isStale)
            BorderStroke(1.dp, mc.lifeNegative.copy(alpha = 0.45f))
        else
            BorderStroke(0.5.dp, mc.surfaceVariant),
    ) {
        Column {
            // Card image
            Box {
                AsyncImage(
                    model              = card.imageArtCrop ?: card.imageNormal,
                    contentDescription = card.name,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(MaterialTheme.shapes.small),
                )
                // Set symbol (rarity-tinted)
                SetSymbol(
                    setCode  = card.setCode,
                    rarity   = CardRarity.fromString(card.rarity),
                    size     = 14.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                )
                // Quantity badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                    color    = mc.background.copy(alpha = 0.85f),
                    shape    = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text     = "×${userCard.quantity}${if (userCard.isFoil) " ✦" else ""}",
                        style    = MaterialTheme.magicTypography.labelSmall,
                        color    = if (userCard.isFoil) mc.goldMtg else mc.textPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            // Card name + price + tags
            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text     = card.name,
                    style    = MaterialTheme.magicTypography.labelSmall,
                    color    = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val preferredCurrency = LocalPreferredCurrency.current
                val priceText = remember(card.priceUsd, card.priceUsdFoil, card.priceEur, card.priceEurFoil, userCard.isFoil, preferredCurrency) {
                    if (userCard.isFoil) {
                        PriceFormatter.formatFromScryfall(card.priceUsdFoil, card.priceEurFoil, preferredCurrency = preferredCurrency)
                    } else {
                        PriceFormatter.formatFromScryfall(card.priceUsd, card.priceEur, preferredCurrency = preferredCurrency)
                    }
                }
                if (priceText != "—") {
                    Text(
                        text  = priceText,
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.goldMtg,
                    )
                }
                // Mini tag chips (max 2)
                if (card.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        card.tags.take(2).forEach { tag ->
                            Surface(
                                color = mc.primaryAccent.copy(alpha = 0.12f),
                                shape = MaterialTheme.shapes.extraSmall,
                            ) {
                                Text(
                                    text     = tag.label,
                                    style    = MaterialTheme.magicTypography.labelSmall,
                                    color    = mc.primaryAccent,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
