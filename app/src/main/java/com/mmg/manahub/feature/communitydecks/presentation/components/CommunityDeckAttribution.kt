package com.mmg.manahub.feature.communitydecks.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * Attribution row for a community deck: "Created by {author} on Archidekt" plus a
 * tappable icon that opens the original Archidekt deck in the browser.
 *
 * Stateless and reusable. The link button is a 48dp [IconButton] (meets the minimum
 * touch-target requirement) and carries a meaningful content description.
 *
 * @param authorName the deck author's username.
 * @param sourceUrl the canonical Archidekt deck URL opened on tap.
 * @param onOpenSource invoked when the user taps the external-link button.
 */
@Composable
fun CommunityDeckAttribution(
    authorName: String,
    sourceUrl: String,
    onOpenSource: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            tint = mc.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(spacing.xs))
        Text(
            text = stringResource(R.string.community_deck_attribution, authorName),
            style = ty.bodyMedium,
            color = mc.textSecondary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onOpenSource(sourceUrl) }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.community_deck_view_original),
                tint = mc.primaryAccent,
            )
        }
    }
}
