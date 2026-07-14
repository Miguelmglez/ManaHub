package com.mmg.manahub.core.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.core.model.CollectionCardGroup

/**
 * Convenience overload that renders a [CollectionCardGroup] as a list row.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CardListItem(
    item:     CollectionCardGroup,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    CardListItem(
        name = item.card.name,
        imageUrl = item.card.imageNormal,
        priceUsd = if (item.hasFoil) item.card.priceUsdFoil else item.card.priceUsd,
        priceEur = if (item.hasFoil) item.card.priceEurFoil else item.card.priceEur,
        quantityText = "×${item.totalQuantity}",
        hasFoil = item.hasFoil,
        isStale = item.card.isStale,
        setCode = item.card.setCode,
        setName = item.card.setName,
        rarity = item.card.rarity,
        typeLine = item.card.typeLine,
        onClick = onClick,
        modifier = modifier,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        scryfallId = item.card.scryfallId,
        extraSupportingContent = {
            if (item.distinctCopies > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = StackedCardsIcon,
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

/**
 * General-purpose card list item with price, badges, and metadata.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
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
    condition: String? = null,
    language: String? = null,
    isStale: Boolean = false,
    setCode: String? = null,
    setName: String? = null,
    rarity: String? = null,
    typeLine: String? = null,
    containerColor: Color = Color.Transparent,
    shape: Shape = RectangleShape,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    scryfallId: String? = null,
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
                val imageModifier = Modifier
                    .size(width = 56.dp, height = 80.dp)
                    .clip(SmallCardShape)
                    .background(mc.surfaceVariant)
                    .border(0.5.dp, mc.surfaceVariant, SmallCardShape)

                val finalImageModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null && scryfallId != null) {
                    with(sharedTransitionScope) {
                        imageModifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = "card-image-$scryfallId"),
                            animatedVisibilityScope = animatedVisibilityScope,
                            clipInOverlayDuringTransition = OverlayClip(SmallCardShape),
                            boundsTransform = { _, _ ->
                                tween(durationMillis = 500, easing = FastOutSlowInEasing)
                            },
                            renderInOverlayDuringTransition = true,
                        )
                    }
                } else {
                    imageModifier
                }

                Box(modifier = finalImageModifier) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
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

                    if (typeLine != null) {
                        Text(
                            text = typeLine,
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = mc.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                language?.let { CopyBadge(label = it.uppercase()) }
                                condition?.let { CopyBadge(label = it) }
                                if (hasFoil) FoilBadge()
                                extraSupportingContent?.invoke(this@Row)
                            }
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
