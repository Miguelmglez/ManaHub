package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * A single row representing a participant slot in a game lobby.
 *
 * Renders either an **empty** state (waiting for a player) or an **occupied** state
 * showing the player's color circle, name, host/you badges, and a ready status badge.
 *
 * @param displayName The participant's display name.
 * @param themeKey The [com.mmg.manahub.core.ui.theme.PlayerThemeColors.name] for color lookup.
 * @param isHost Whether this participant is the session host.
 * @param isCurrentUser Whether this slot belongs to the device's local user.
 * @param isReady Whether this participant has marked themselves as ready.
 * @param isEmpty If true, renders an empty "Waiting for player" slot regardless of other params.
 * @param slotIndex Zero-based slot index displayed when [isEmpty] is true.
 * @param modifier Optional modifier.
 */
@Composable
fun ParticipantListRow(
    displayName: String,
    themeKey: String,
    isHost: Boolean,
    isCurrentUser: Boolean,
    isReady: Boolean,
    isEmpty: Boolean = false,
    slotIndex: Int = 0,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val accent = if (!isEmpty) {
        PlayerTheme.ALL.find { it.name == themeKey }?.accent ?: PlayerTheme.ALL[0].accent
    } else {
        mc.surfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(mc.surface)
            .border(
                width = 1.dp,
                color = when {
                    isEmpty -> mc.surfaceVariant
                    isReady -> mc.primaryAccent.copy(alpha = 0.3f)
                    else -> mc.surfaceVariant
                },
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Color circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isEmpty) mc.surfaceVariant else accent),
            ) {
                if (isEmpty) {
                    Text(
                        text = (slotIndex + 1).toString(),
                        style = ty.labelSmall,
                        color = mc.textDisabled,
                    )
                }
            }

            // Name and badges
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = if (isEmpty) stringResource(R.string.lobby_waiting_player) else displayName,
                    style = ty.bodyMedium,
                    color = if (isEmpty) mc.textDisabled else mc.textPrimary,
                )
                if (!isEmpty) {
                    if (isHost) {
                        Text(
                            text = stringResource(R.string.lobby_host_badge),
                            style = ty.labelSmall,
                            color = mc.goldMtg,
                        )
                    }
                    if (isCurrentUser) {
                        Text(
                            text = stringResource(R.string.lobby_you_badge),
                            style = ty.labelSmall,
                            color = mc.primaryAccent,
                        )
                    }
                }
            }
        }

        // Ready badge
        if (!isEmpty) {
            if (isReady) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(mc.lifePositive.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.lobby_ready_label),
                            tint = mc.lifePositive,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.lobby_ready_label),
                            style = ty.labelSmall,
                            color = mc.lifePositive,
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.lobby_waiting_label),
                    style = ty.labelSmall,
                    color = mc.textDisabled,
                )
            }
        }
    }
}
