package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.util.PriceFormatter
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.feature.collection.CollectionCardGroup
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import androidx.compose.ui.tooling.preview.Preview
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.ui.theme.MagicTheme

@Composable
fun CardListItem(
    item:     CollectionCardGroup,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,) {
    CardListItem(
        name = item.card.name,
        imageUrl = item.card.imageArtCrop,
        priceUsd = if (item.hasFoil) item.card.priceUsdFoil else item.card.priceUsd,
        priceEur = if (item.hasFoil) item.card.priceEurFoil else item.card.priceEur,
        quantityText = "×${item.totalQuantity}",
        hasFoil = item.hasFoil,
        isStale = item.card.isStale,
        setCode = item.card.setCode,
        setName = item.card.setName,
        rarity = item.card.rarity,
        onClick = onClick,
        modifier = modifier,        extraSupportingContent = {
            if (item.distinctCopies > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Style,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.magicColors.primaryAccent
                    )
                    Text(
                        text  = item.distinctCopies.toString(),
                        style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.magicColors.primaryAccent,
                    )
                }
            }
        }
    )
}

@Composable
fun CardListItem(
    name: String,
    imageUrl: String?,
    priceUsd: Double?,
    priceEur: Double?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    quantityText: String? = null,
    hasFoil: Boolean = false,
    isStale: Boolean = false,
    setCode: String? = null,
    setName: String? = null,
    rarity: String? = null,    containerColor: Color = Color.Transparent,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape,
    extraSupportingContent: @Composable (RowScope.() -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val preferredCurrency = LocalPreferredCurrency.current

    Surface(
        onClick = onClick,
        color = containerColor,
        shape = shape,
        modifier = modifier.height(IntrinsicSize.Min)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.heightIn(min = 72.dp),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(width = 48.dp, height = 64.dp)
                        .clip(MaterialTheme.shapes.small)
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Enhanced Foil Representation: Subtle holographic edge
                    if (hasFoil) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0x40FF0000),
                                            Color(0x4000FF00),
                                            Color(0x400000FF),
                                            Color(0x40FF00FF),
                                        )
                                    ),
                                    alpha = 0.3f
                                )
                        )
                    }
                }
            },
            headlineContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardName(
                        name = name,
                        showFrontOnly = true,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = mc.textPrimary,
                        style = MaterialTheme.magicTypography.titleMedium,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (setCode != null && rarity != null) {
                            SetSymbol(
                                setCode = setCode,
                                rarity = CardRarity.fromString(rarity),
                                size = 16.dp,
                            )
                        }
                        if (setName != null) {
                            Text(
                                text = setName,
                                style = MaterialTheme.magicTypography.labelMedium.copy(fontSize = 14.sp),
                                color = mc.secondaryAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            extraSupportingContent?.invoke(this@Row)
                        }

                        val priceText = PriceFormatter.formatFromScryfall(
                            priceUsd = priceUsd,
                            priceEur = priceEur,
                            preferredCurrency = preferredCurrency,
                        )
                        if (priceText != "—") {
                            Text(
                                text = priceText,
                                style = MaterialTheme.magicTypography.labelMedium,
                                color = mc.goldMtg,
                            )
                        }

                        if (quantityText != null) {
                            Text(
                                text = quantityText,
                                style = MaterialTheme.magicTypography.labelMedium,
                                color = mc.textSecondary,
                            )
                        }
                    }
                }
            },
            supportingContent = null,
        )
    }
}


