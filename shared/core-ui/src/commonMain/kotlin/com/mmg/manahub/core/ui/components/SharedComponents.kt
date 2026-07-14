package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.model.GroupingMode
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

@Composable
fun LanguageBadge(langCode: String, modifier: Modifier = Modifier) {
    val flag = com.mmg.manahub.core.util.CardConstants.getFlag(langCode)
    val isFlag = flag.isNotEmpty()
    val displayLabel = flag.ifEmpty { langCode.uppercase() }
    CopyBadge(label = displayLabel, modifier = modifier, showBackground = !isFlag)
}

@Composable
fun StaleBadge() {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        color = mc.lifeNegative.copy(alpha = 0.18f),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text     = "⚠ prices",
            style    = ty.labelSmall,
            color    = mc.lifeNegative,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

@Composable
fun StaleWarningBanner() {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(color = mc.lifeNegative.copy(alpha = 0.12f)) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint               = mc.lifeNegative,
                modifier           = Modifier.size(16.dp),
            )
            Text(
                text  = "Some prices couldn't be refreshed. Showing cached data.",
                style = ty.bodySmall,
                color = mc.lifeNegative,
            )
        }
    }
}

@Composable
fun GroupingFlowSelector(
    selected: GroupingMode,
    onSelect: (GroupingMode) -> Unit
) {
    ManaHubSelector(
        icon = Icons.AutoMirrored.Filled.Sort,
        label = "Group by:",
        valueText = when (selected) {
            GroupingMode.TYPE -> "Type"
            GroupingMode.COLOR -> "Color"
            GroupingMode.COST -> "CMC"
            GroupingMode.TAG -> "Tags"
        },
        items = GroupingMode.entries,
        selectedItem = selected,
        onSelect = onSelect,
        itemLabel = {
            when (it) {
                GroupingMode.TYPE -> "Type"
                GroupingMode.COLOR -> "Color"
                GroupingMode.COST -> "CMC"
                GroupingMode.TAG -> "Tags"
            }
        }
    )
}

@Composable
fun AvatarImage(avatarUrl: String?, initials: String, size: Int, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(mc.primaryAccent.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text = initials,
                color = mc.primaryAccent,
                style = MaterialTheme.magicTypography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = (size / 2.5).sp,
            )
        }
    }
}
