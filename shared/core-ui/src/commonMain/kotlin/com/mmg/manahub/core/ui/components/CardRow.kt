package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckSlotEntry
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardCornerRadius
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.coloredShadow
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.jetbrains.compose.resources.painterResource

/**
 * A single card row inside a deck list or search result.
 *
 * @param entry the deck slot to render.
 * @param isInCollection whether the user owns this card (shows a star).
 * @param onClick invoked when the row body is tapped (opens detail).
 * @param onRemove invoked when the close button is tapped (removes the slot).
 * @param isCommander whether this card is the deck's commander (applies a gold style).
 */
@Composable
fun CardRow(
    entry: DeckSlotEntry,
    isInCollection: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    isCommander: Boolean = false,
) {
    val card = entry.card
    if (card != null) {
        // Delegates to the [Card]-based overload -- the single visual source of truth for a
        // resolved slot. Only an unresolved slot (card == null) keeps its own minimal fallback
        // below, since the [Card]-based overload requires a non-null card.
        CardRow(
            card = card,
            isInCollection = isInCollection,
            onClick = onClick,
            onRemove = onRemove,
            modifier = modifier,
            quantity = entry.quantity,
            isCommander = isCommander,
        )
        return
    }

    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = mc.surface,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, mc.surfaceVariant.copy(alpha = 0.5f), CardShape)
            .semantics(mergeDescendants = true) {}
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            AsyncImage(
                model = null,
                contentDescription = null,
                placeholder = painterResource(Res.drawable.mtg_card_back),
                error = painterResource(Res.drawable.mtg_card_back),
                fallback = painterResource(Res.drawable.mtg_card_back),
                modifier = Modifier
                    .size(width = 44.dp, height = 62.dp)
                    .clip(SmallCardShape)
                    .border(1.dp, mc.surfaceVariant, SmallCardShape),
                contentScale = ContentScale.Crop
            )
            Text(
                text = "Unresolved Card",
                style = ty.titleMedium,
                color = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (entry.quantity > 1) {
                Surface(shape = ChipShape, color = mc.primaryAccent.copy(alpha = 0.2f)) {
                    Text("×${entry.quantity}", style = ty.labelMedium, color = mc.primaryAccent, modifier = Modifier.padding(horizontal = spacing.sm, vertical = 2.dp))
                }
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = null, tint = mc.textDisabled, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * The [Card]-based sibling of the [DeckSlotEntry] overload above -- renders the identical visual
 * (image thumbnail, name, type line, mana cost, quantity badge, owned indicator, remove
 * affordance) but takes a raw [Card] instead of a deck-specific [DeckSlotEntry].
 *
 * @param card the card to render.
 * @param isInCollection whether the user owns this card (shows a bookmark icon).
 * @param onClick invoked when the row body is tapped.
 * @param onRemove invoked when the trailing close button is tapped; the button is hidden
 *   entirely when null (e.g. a picker preview with no remove affordance).
 * @param modifier optional [Modifier].
 * @param quantity shown as a "×N" badge when greater than 1.
 * @param selected when true, tints the row's container with the same primaryAccent selected
 *   convention used elsewhere in the app.
 * @param onAdd invoked when an increment button is tapped; showing +/- controls instead of a
 *   single remove button when non-null.
 * @param isCommander whether this card is the deck's commander (applies a gold style).
 * @param extraSupportingContent optional slot for additional content below the metadata row.
 */
@Composable
fun CardRow(
    card: Card,
    isInCollection: Boolean,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    quantity: Int = 1,
    selected: Boolean = false,
    onAdd: (() -> Unit)? = null,
    isCommander: Boolean = false,
    extraSupportingContent: @Composable (() -> Unit)? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    // Map card colors to theme colors for the visual accent
    val cardColors = remember(card.colors, isCommander) {
        if (isCommander) listOf(mc.goldMtg)
        else card.colors.map { manaColorFor(it, mc) }
    }
    val primaryAccentColor = cardColors.firstOrNull() ?: mc.surfaceVariant

    Surface(
        onClick = onClick,
        shape = CardShape,
        color = if (isCommander) mc.goldMtg.copy(alpha = 0.1f) else mc.surface,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isCommander) {
                    Modifier.border(1.dp, mc.goldMtg.copy(alpha = 0.4f), CardShape)
                } else if (selected) {
                    Modifier.coloredShadow(
                        color = mc.primaryAccent,
                        borderRadius = CardCornerRadius,
                        blurRadius = 12.dp,
                        spread = 0.8f
                    ).border(2.dp, mc.primaryAccent, CardShape)
                } else {
                    Modifier.border(1.dp, mc.surfaceVariant.copy(alpha = 0.5f), CardShape)
                }
            )
            .semantics(mergeDescendants = true) {}
    ) {
        Box(Modifier.fillMaxWidth().heightIn(min = 68.dp)) {
            // 1. Subtle art-crop background texture
            if (card.imageArtCrop != null) {
                AsyncImage(
                    model = card.imageArtCrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.08f,
                    modifier = Modifier.matchParentSize()
                )
            }

            // 2. Subtle color gradient from the card's mana identity
            if (cardColors.isNotEmpty()) {
                val gradient = remember(cardColors) {
                    Brush.horizontalGradient(
                        colors = listOf(
                            primaryAccentColor.copy(alpha = 0.12f),
                            mc.surface.copy(alpha = 0f)
                        )
                    )
                }
                Box(Modifier.matchParentSize().background(gradient))
            }

            // 3. Color accent bar on the left
            if (cardColors.isNotEmpty()) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(
                            primaryAccentColor,
                            shape = RoundedCornerShape(
                                topStart = CardCornerRadius,
                                bottomStart = CardCornerRadius
                            )
                        )
                )
            }

            // 4. Content row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                // Thumbnail with CardShape and a subtle border
                AsyncImage(
                    model = card.imageNormal,
                    contentDescription = null,
                    placeholder = painterResource(Res.drawable.mtg_card_back),
                    error = painterResource(Res.drawable.mtg_card_back),
                    fallback = painterResource(Res.drawable.mtg_card_back),
                    modifier = Modifier
                        .size(width = 44.dp, height = 62.dp)
                        .clip(CardShape)
                        .border(1.dp, mc.surfaceVariant.copy(alpha = 0.8f), CardShape),
                    contentScale = ContentScale.Crop
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CardName(
                            name = card.name,
                            showFrontOnly = true,
                            style = ty.titleMedium,
                            color = mc.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isInCollection) {
                            Spacer(Modifier.width(spacing.xs))
                            Icon(
                                Icons.Rounded.CollectionsBookmark,
                                contentDescription = null,
                                tint = if (isCommander) mc.goldMtg else mc.primaryAccent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        card.typeLine,
                        style = ty.bodySmall,
                        color = if (isCommander) mc.goldMtg.copy(alpha = 0.8f) else mc.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = spacing.xxs)
                    ) {
                        if (card.manaCost != null) {
                            ManaCostImages(
                                manaCost = card.manaCost!!,
                                symbolSize = 14.dp
                            )
                        }
                        SetSymbol(
                            setCode = card.setCode,
                            rarity = CardRarity.fromString(card.rarity),
                            size = 14.dp
                        )
                        Text(
                            text = card.setCode.uppercase(),
                            style = ty.labelSmall.copy(fontSize = 10.sp),
                            color = if (isCommander) mc.goldMtg.copy(alpha = 0.7f) else mc.textSecondary
                        )
                    }

                    if (extraSupportingContent != null) {
                        Spacer(Modifier.height(spacing.xxs))
                        extraSupportingContent()
                    }
                }

                // Trailing: Quantity and Remove
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    if (onAdd != null) {
                        // Increment/Decrement mode (AddCardSheet)
                        if (quantity > 0 && onRemove != null) {
                            IconButton(
                                onClick = onRemove,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = null,
                                    tint = mc.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "$quantity",
                                style = ty.labelMedium,
                                color = if (isCommander) mc.goldMtg else mc.primaryAccent
                            )
                        }

                        IconButton(
                            onClick = onAdd,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = if (isCommander) mc.goldMtg else mc.primaryAccent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        // Standard mode (Deck list)
                        if (quantity > 1) {
                            Surface(
                                shape = ChipShape,
                                color = (if (isCommander) mc.goldMtg else mc.secondaryAccent).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    "×$quantity",
                                    style = ty.labelMedium,
                                    color = if (isCommander) mc.goldMtg else mc.secondaryAccent,
                                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = 2.dp)
                                )
                            }
                        }
                        if (onRemove != null) {
                            IconButton(
                                onClick = onRemove,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (isCommander) mc.goldMtg else mc.textDisabled,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
