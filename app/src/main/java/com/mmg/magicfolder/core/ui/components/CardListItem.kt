package com.mmg.magicfolder.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.domain.model.UserCardWithCard
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

@Composable
fun CardListItem(
    item:     UserCardWithCard,
    onClick:  () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card     = item.card
    val userCard = item.userCard
    val mc       = MaterialTheme.magicColors
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                )
                if (userCard.isFoil) {
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
                    text     = "${card.setCode.uppercase()} · ${card.printedTypeLine?:card.typeLine.substringBefore(" —").trim()}",
                    style    = MaterialTheme.magicTypography.bodySmall,
                    color    = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = "· ${userCard.condition}",
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textDisabled,
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                val price = if (userCard.isFoil) card.priceUsdFoil else card.priceUsd
                if (price != null) {
                    Text(
                        text  = "$${String.format("%.2f", price)}",
                        style = MaterialTheme.magicTypography.bodyMedium,
                        color = mc.goldMtg,
                    )
                }
                Text(
                    text  = "×${userCard.quantity}",
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                )
                IconButton(
                    onClick  = { showDeleteDialog = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        modifier           = Modifier.size(16.dp),
                        tint               = mc.textDisabled,
                    )
                }
            }
        },
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title            = { Text(stringResource(R.string.carddetail_delete_title)) },
            text             = { Text(stringResource(R.string.carddetail_delete_message, card.name)) },
            confirmButton    = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_remove), color = mc.lifeNegative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
