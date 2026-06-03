package com.mmg.manahub.feature.playtest.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * A compact strip showing the commander card thumbnail and the "Command Zone" label.
 * Only rendered for commander-format decks.
 */
@Composable
fun CommandZoneArea(
    commanderCard: Card,
    librarySize: Int,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(mc.surface.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model             = commanderCard.imageNormal,
            contentDescription = commanderCard.name,
            contentScale      = ContentScale.Crop,
            modifier          = Modifier
                .size(width = 36.dp, height = 50.dp)
                .clip(RoundedCornerShape(4.dp)),
        )

        Column {
            Text(
                text  = stringResource(R.string.playtest_command_zone_label),
                style = ty.labelSmall,
                color = mc.primaryAccent,
            )
            Text(
                text  = commanderCard.name,
                style = ty.bodyMedium,
                color = mc.textPrimary,
            )
            Text(
                text  = stringResource(R.string.playtest_library_size, librarySize),
                style = ty.labelSmall,
                color = mc.textSecondary,
            )
        }

        Spacer(Modifier.weight(1f))
    }
}
