package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * A reusable, fully stateless card-name search picker: a debounced text field (the caller owns
 * the debounce/search — this composable only forwards keystrokes) whose results render as a
 * horizontal row of full card images, or — once a card is picked — a compact "selected card" row
 * with a clear affordance. Used by the Community Hub's advanced-search Commander/Card sections,
 * and reusable anywhere else a single card needs to be picked by name.
 *
 * Tapping a result tile does NOT open the inspection overlay itself (this composable has no
 * business logic) — it reports the tapped index and the tile's [Rect] relative to
 * [rootCoordinates] via [onCardTapped] so the CALLER can drive a [MagicCardInspectionOverlay]
 * (mirrors the existing `boxCoords.localBoundingBoxOf(coords)` pattern already used for card
 * inspection elsewhere in this codebase).
 *
 * @param rootCoordinates the [LayoutCoordinates] of the caller's own root `Box`
 *   (`Modifier.onGloballyPositioned { rootCoordinates = it }`) that the inspection overlay will be
 *   drawn within; tile rects are computed relative to it. Pass `null` to skip rect tracking
 *   (tiles are then still clickable, but [onCardTapped] receives [Rect.Zero]).
 */
@Composable
fun CardPickerField(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Card>,
    isSearching: Boolean,
    selectedCard: Card?,
    onClearSelection: () -> Unit,
    onCardTapped: (index: Int, rect: Rect) -> Unit,
    rootCoordinates: LayoutCoordinates?,
    modifier: Modifier = Modifier,
    searchHint: String = "Search card name...",
    clearContentDescription: String = "Clear selection",
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing

    Column(modifier = modifier) {
        if (selectedCard != null) {
            SelectedCardRow(
                card = selectedCard,
                onClear = onClearSelection,
                clearContentDescription = clearContentDescription,
            )
            return@Column
        }

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(searchHint, color = mc.textDisabled) },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = mc.primaryAccent,
                        strokeWidth = 2.dp,
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = mc.primaryAccent,
                unfocusedBorderColor = mc.surfaceVariant,
                focusedTextColor = mc.textPrimary,
                unfocusedTextColor = mc.textPrimary,
                cursorColor = mc.primaryAccent,
            ),
        )

        if (results.isNotEmpty()) {
            Spacer(Modifier.height(sp.xs))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                itemsIndexed(results, key = { _, card -> card.scryfallId }) { index, card ->
                    CardPickerTile(
                        card = card,
                        onTap = { rect -> onCardTapped(index, rect) },
                        rootCoordinates = rootCoordinates,
                    )
                }
            }
        }
    }
}

/** One full-card-image result tile (63:88 aspect), reporting its own rect on tap. */
@Composable
private fun CardPickerTile(
    card: Card,
    onTap: (Rect) -> Unit,
    rootCoordinates: LayoutCoordinates?,
) {
    val mc = MaterialTheme.magicColors
    var tileRect by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = Modifier
            .width(90.dp)
            .aspectRatio(63f / 88f)
            .clip(CardShape)
            .background(mc.surfaceVariant)
            .onGloballyPositioned { coords ->
                val root = rootCoordinates
                if (root != null && root.isAttached && coords.isAttached) {
                    tileRect = root.localBoundingBoxOf(coords)
                }
            }
            .clickable { onTap(tileRect) },
    ) {
        if (card.imageNormal != null) {
            AsyncImage(
                model = card.imageNormal,
                contentDescription = card.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The compact selected-card row shown once a card has been picked, with a trailing clear button. */
@Composable
private fun SelectedCardRow(
    card: Card,
    onClear: () -> Unit,
    clearContentDescription: String,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Surface(
        shape = ChipShape,
        color = mc.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(sp.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.sm),
        ) {
            if (card.imageArtCrop != null) {
                AsyncImage(
                    model = card.imageArtCrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(ChipShape)
                        .background(mc.surfaceVariant),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.name,
                    style = ty.bodyMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Bind to a local val before the null check — smart-casting a nullable property
                // read directly off a cross-module data class (Card lives in :shared:core-model)
                // is not reliably preserved by the compiler across module boundaries.
                val manaCost = card.manaCost
                if (!manaCost.isNullOrBlank()) {
                    ManaCostImages(manaCost = manaCost, symbolSize = 14.dp)
                }
            }
            IconButton(onClick = onClear, modifier = Modifier.size(48.dp)) {
                Icon(CloseIcon, contentDescription = clearContentDescription, tint = mc.textSecondary)
            }
        }
    }
}
