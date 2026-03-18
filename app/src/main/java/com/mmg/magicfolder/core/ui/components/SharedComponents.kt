package com.mmg.magicfolder.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RarityDot(rarity: String, modifier: Modifier = Modifier) {
    val color = when (rarity.lowercase()) {
        "mythic"   -> Color(0xFFE040FB)
        "rare"     -> Color(0xFFFFD700)
        "uncommon" -> Color(0xFF9E9E9E)
        else       -> Color(0xFF78909C)
    }
    Box(
        modifier = modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun FoilBadge() {
    Surface(
        color  = MaterialTheme.colorScheme.tertiaryContainer,
        shape  = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text     = "foil",
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun StaleBadge() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text     = "⚠ prices",
            style    = MaterialTheme.typography.labelSmall,
            color    = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun StaleWarningBanner() {
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Row(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint   = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text  = "Some prices couldn't be refreshed. Showing cached data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}