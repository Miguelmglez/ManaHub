package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.feature.collection.CollectionCardGroup

@Composable
fun CardGridItem(
    item:     CollectionCardGroup,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = item.card
    val mc   = MaterialTheme.magicColors

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
                // Quantity badge (total across all copies)
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                    color    = mc.background.copy(alpha = 0.85f),
                    shape    = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        text     = "×${item.totalQuantity}${if (item.hasFoil) " ✦" else ""}",
                        style    = MaterialTheme.magicTypography.labelSmall,
                        color    = if (item.hasFoil) mc.goldMtg else mc.textPrimary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            // Card name + price
            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                CardName(
                    name          = card.name,
                    showFrontOnly = true,
                    style         = MaterialTheme.magicTypography.labelSmall,
                    color         = mc.textPrimary,
                    maxLines      = 1,
                    overflow      = TextOverflow.Ellipsis,
                )
                val preferredCurrency = LocalPreferredCurrency.current
                val priceText = remember(card.priceUsd, card.priceUsdFoil, card.priceEur, card.priceEurFoil, item.hasFoil, preferredCurrency) {
                    PriceFormatter.formatFromScryfall(
                        priceUsd = if (item.hasFoil) card.priceUsdFoil else card.priceUsd,
                        priceEur = if (item.hasFoil) card.priceEurFoil else card.priceEur,
                        preferredCurrency = preferredCurrency
                    )
                }
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (priceText != "—") {
                        Text(
                            text  = priceText,
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.goldMtg,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // Variants indicator
                    if (item.distinctCopies > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier          = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Style,
                                contentDescription = null,
                                modifier           = Modifier.size(12.dp),
                                tint               = mc.primaryAccent
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text  = item.distinctCopies.toString(),
                                style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 11.sp),
                                color = mc.primaryAccent,
                            )
                        }
                    }
                    SetSymbol(
                        setCode = card.setCode,
                        rarity  = CardRarity.fromString(card.rarity),
                        size    = 14.dp,
                    )
                }
            }
        }
    }
}
