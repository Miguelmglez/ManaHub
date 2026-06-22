package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.PriceFormatter

/**
 * Reusable bottom sheet that lists all available art variants (printings) of a card so
 * the user can switch to a different printing. The variant matching [currentCardId] is
 * highlighted with a subtle tint and a checkmark so the user always knows which printing
 * they are currently viewing.
 *
 * Each variant thumbnail has a "full-screen image" expand affordance that calls
 * [onExpandImage] with the best-available image URL.
 *
 * @param currentCardId Scryfall id of the card currently shown, used for highlighting.
 * @param variants The list of art variants to display.
 * @param isLoading True while [variants] is being fetched; shows a spinner.
 * @param onDismiss Invoked when the sheet is dismissed.
 * @param onSelectVariant Invoked with the chosen [Card] variant.
 * @param onExpandImage Invoked with the image URL to display full-screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VariantSelectorSheet(
    currentCardId: String,
    variants: List<Card>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelectVariant: (Card) -> Unit,
    onExpandImage: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { newValue ->
            if (newValue == SheetValue.Hidden) !isLoading else true
        },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = mc.textSecondary,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.scanner_variant_selector_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = mc.primaryAccent)
                    }
                }

                variants.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.scanner_variant_no_results),
                            style = ty.bodyMedium,
                            color = mc.textSecondary,
                        )
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).fillMaxHeight(0.65f)) {
                        items(variants, key = { it.scryfallId }) { variant ->
                            VariantCardItem(
                                card = variant,
                                isSelected = variant.scryfallId == currentCardId,
                                onSelectVariant = { onSelectVariant(variant) },
                                onExpandImage = {
                                    val url = variant.imageNormal ?: variant.imageArtCrop
                                    if (!url.isNullOrBlank()) onExpandImage(url)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VariantCardItem(
    card: Card,
    isSelected: Boolean,
    onSelectVariant: () -> Unit,
    onExpandImage: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val preferredCurrency = LocalPreferredCurrency.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectVariant() }
            .then(
                if (isSelected) Modifier.background(mc.primaryAccent.copy(alpha = 0.08f))
                else Modifier
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val imageUrl = card.imageNormal ?: card.imageArtCrop
            Box {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = stringResource(R.string.variant_tap_to_expand, card.name),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .width(80.dp)
                        .height(112.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .then(if (imageUrl != null) Modifier.clickable { onExpandImage() } else Modifier),
                )
                if (imageUrl != null) {
                    Icon(
                        imageVector = Icons.Default.ZoomIn,
                        contentDescription = null,
                        tint = mc.textPrimary.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(20.dp)
                            .align(Alignment.BottomEnd)
                            .padding(2.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.name,
                        style = ty.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) mc.primaryAccent else mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isSelected) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = mc.primaryAccent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SetSymbol(
                        setCode = card.setCode,
                        rarity = CardRarity.fromString(card.rarity),
                        size = 14.dp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${card.setName} #${card.collectorNumber}",
                        style = ty.labelSmall,
                        color = mc.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (card.rarity.isNotBlank()) {
                    VariantAttrTag(card.rarity.replaceFirstChar { it.uppercaseChar() })
                }
                Text(
                    text = PriceFormatter.formatFromScryfall(
                        card.priceUsd,
                        card.priceEur,
                        preferredCurrency,
                    ),
                    style = ty.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = mc.goldMtg,
                )
            }
        }
    }
    HorizontalDivider(color = mc.textPrimary.copy(alpha = 0.05f))
}

@Composable
private fun VariantAttrTag(text: String) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        color = mc.textPrimary.copy(0.1f),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Text(
            text = text,
            style = ty.labelSmall,
            color = mc.textPrimary,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

/**
 * Reusable full-screen image viewer rendered in a borderless [Dialog].
 * Tapping anywhere or the close button dismisses it.
 *
 * @param imageUrl The image URL to display.
 * @param onDismiss Invoked when the viewer is dismissed.
 */
@Composable
fun FullScreenImageViewer(
    imageUrl: String,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Full-screen photo viewer — black scrim is intentional for maximum image contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
                    // Black scrim circle is part of the intentional full-screen photo viewer.
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_close),
                    // White is intentional for maximum contrast against the black photo-viewer scrim.
                    tint = Color.White,
                )
            }
        }
    }
}
