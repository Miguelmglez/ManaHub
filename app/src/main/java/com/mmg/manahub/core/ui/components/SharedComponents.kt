package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.R
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
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { expanded = true },
            color = mc.backgroundSecondary,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.deckbuilder_group_label),
                    style = ty.labelLarge,
                    color = mc.textSecondary
                )
                Text(
                    text = when (selected) {
                        GroupingMode.TYPE -> stringResource(R.string.deckbuilder_group_type)
                        GroupingMode.COLOR -> stringResource(R.string.deckbuilder_group_color)
                        GroupingMode.COST -> stringResource(R.string.deckbuilder_group_cmc)
                        GroupingMode.TAG -> stringResource(R.string.carddetail_tags_section)
                    },
                    style = ty.labelLarge,
                    color = mc.primaryAccent
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = mc.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = mc.backgroundSecondary,
            modifier = Modifier.width(200.dp)
        ) {
            GroupingMode.entries.forEachIndexed { index, mode ->
                val label = when (mode) {
                    GroupingMode.TYPE -> stringResource(R.string.deckbuilder_group_type)
                    GroupingMode.COLOR -> stringResource(R.string.deckbuilder_group_color)
                    GroupingMode.COST -> stringResource(R.string.deckbuilder_group_cmc)
                    GroupingMode.TAG -> stringResource(R.string.carddetail_tags_section)
                }
                DropdownMenuItem(
                    text = {
                        Text(
                            text = label,
                            style = ty.bodyMedium,
                            color = if (mode == selected) mc.primaryAccent else mc.textPrimary
                        )
                    },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    },
                    trailingIcon = if (mode == selected) {
                        { Icon(Icons.Default.Check, null, tint = mc.primaryAccent, modifier = Modifier.size(18.dp)) }
                    } else null
                )
                if (index < GroupingMode.entries.size - 1) {
                    HorizontalDivider(color = mc.surfaceVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
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
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
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

