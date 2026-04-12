package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.util.PriceFormatter
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.feature.collection.CollectionCardGroup
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

@Composable
fun CardListItem(
    item:     CollectionCardGroup,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card = item.card
    val mc   = MaterialTheme.magicColors

    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors   = ListItemDefaults.colors(containerColor = mc.background),
        leadingContent = {
            AsyncImage(
                model              = card.imageArtCrop,
                contentDescription = card.name,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .size(width = 44.dp, height = 60.dp)
                    .clip(MaterialTheme.shapes.small),
            )
        },
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text     = card.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color    = mc.textPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.hasFoil) {
                    Spacer(Modifier.width(4.dp))
                    FoilBadge()
                }
                if (card.isStale) {
                    Spacer(Modifier.width(4.dp))
                    StaleBadge()
                }
            }
        },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                SetSymbol(
                    setCode  = card.setCode,
                    rarity   = CardRarity.fromString(card.rarity),
                    size     = 14.dp,
                )
                Text(
                    text     = "${card.setCode.uppercase()} · ${card.printedTypeLine ?: card.typeLine.substringBefore(" —").trim()}",
                    style    = MaterialTheme.magicTypography.bodySmall,
                    color    = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.distinctCopies > 1) {
                    Text(
                        text  = "· ${item.distinctCopies} ${stringResource(R.string.collection_copy_types)}",
                        style = MaterialTheme.magicTypography.bodySmall,
                        color = mc.primaryAccent,
                    )
                }
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val preferredCurrency = LocalPreferredCurrency.current
                val priceText = PriceFormatter.formatFromScryfall(
                    priceUsd = if (item.hasFoil) card.priceUsdFoil else card.priceUsd,
                    priceEur = if (item.hasFoil) card.priceEurFoil else card.priceEur,
                    preferredCurrency = preferredCurrency,
                )
                if (priceText != "—") {
                    Text(
                        text  = priceText,
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.goldMtg,
                    )
                }
                Text(
                    text  = "×${item.totalQuantity}",
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                )
            }
        },
    )
}
