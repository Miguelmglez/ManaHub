package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@Composable
fun CardListItem(
    item:     CollectionCardGroup,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier,
        extraSupportingContent = {
            if (item.distinctCopies > 1) {
                Text(
                    text  = "· ${item.distinctCopies} ${stringResource(R.string.collection_copy_types)}",
                    style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.magicColors.primaryAccent,
                )
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
    rarity: String? = null,
    showCheckbox: Boolean = false,
    isChecked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    containerColor: Color = Color.Transparent,
    shape: androidx.compose.ui.graphics.Shape = androidx.compose.ui.graphics.RectangleShape,
    extraSupportingContent: @Composable (RowScope.() -> Unit)? = null,
    trailingIcon: @Composable (RowScope.() -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val preferredCurrency = LocalPreferredCurrency.current

    Surface(
        onClick = onClick,
        color = containerColor,
        shape = shape,
        modifier = modifier
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.heightIn(min = 72.dp),
            leadingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showCheckbox) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = onCheckedChange,
                            modifier = Modifier.padding(end = 0.dp),
                            colors = CheckboxDefaults.colors(
                                checkedColor = mc.primaryAccent,
                                uncheckedColor = mc.textDisabled,
                                checkmarkColor = mc.background
                            )
                        )
                    }
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
                }
            },
            headlineContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        CardName(
                            name = name,
                            showFrontOnly = true,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = mc.textPrimary,
                            style = MaterialTheme.magicTypography.bodyMedium,
                        )
                        if (isStale) {
                            Spacer(Modifier.width(4.dp))
                            StaleBadge()
                        }
                    }

                    // Price moved here
                    val priceText = PriceFormatter.formatFromScryfall(
                        priceUsd = priceUsd,
                        priceEur = priceEur,
                        preferredCurrency = preferredCurrency,
                    )
                    if (priceText != "—") {
                        Text(
                            text = priceText,
                            style = MaterialTheme.magicTypography.bodyMedium,
                            color = mc.goldMtg,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (setCode != null && rarity != null) {
                            SetSymbol(
                                setCode = setCode,
                                rarity = CardRarity.fromString(rarity),
                                size = 12.dp,
                            )
                        }
                        if (setName != null) {
                            Text(
                                text = setName,
                                style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 11.sp),
                                color = mc.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        extraSupportingContent?.invoke(this)
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (quantityText != null) {
                        Text(
                            text = quantityText,
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = mc.textSecondary,
                        )
                    }
                    trailingIcon?.invoke(this)
                }
            },
        )
    }
}
