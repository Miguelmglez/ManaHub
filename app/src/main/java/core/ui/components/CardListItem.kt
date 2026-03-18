package core.ui.components

import androidx.compose.foundation.clickable
import core.domain.model.UserCardWithCard
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

@Composable
fun CardListItem(
    item: UserCardWithCard,
    onClick:  () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val card     = item.card
    val userCard = item.userCard
    var showDeleteDialog by remember { mutableStateOf(false) }

    ListItem(
        modifier = modifier.clickable(onClick = onClick),
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
                Text(card.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                RarityDot(rarity = card.rarity)
                Text(
                    text  = "${card.setCode.uppercase()} · ${card.typeLine.substringBefore(" —").trim()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = "· ${userCard.condition}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                val price = if (userCard.isFoil) card.priceUsdFoil else card.priceUsd
                if (price != null) {
                    Text(
                        text  = "$${String.format("%.2f", price)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                Text(
                    text  = "×${userCard.quantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(
                    onClick  = { showDeleteDialog = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier           = Modifier.size(16.dp),
                        tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text("Remove card") },
            text    = { Text("Remove ${card.name} from your collection?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}