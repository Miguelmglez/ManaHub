package com.mmg.manahub.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.PlayerThemeColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * A bottom sheet for editing a single player's name and color theme.
 *
 * - Shows a [BasicTextField] for the player's name (with a placeholder when blank).
 * - Displays a grid of all [PlayerTheme.ALL] themes, filtering out themes already in use
 *   by other players (but always including the current player's own theme).
 * - Tapping a theme circle selects it immediately without closing the sheet.
 * - A white checkmark overlay indicates the currently selected theme.
 *
 * @param playerName Current name for this player.
 * @param playerTheme Currently selected [PlayerThemeColors] for this player.
 * @param isAppUser Whether this slot represents the device's own user (affects display-only UI).
 * @param usedThemes Themes already assigned to other players; these are hidden from the grid.
 * @param onNameChanged Callback for name field changes.
 * @param onThemeSelected Callback when a theme circle is tapped. Does NOT auto-dismiss.
 * @param onDismiss Called when the sheet should be closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerEditSheet(
    playerName: String,
    playerTheme: PlayerThemeColors,
    isAppUser: Boolean,
    usedThemes: List<PlayerThemeColors> = emptyList(),
    onNameChanged: (String) -> Unit,
    onThemeSelected: (PlayerThemeColors) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    val availableThemes = PlayerTheme.ALL.filter { it !in usedThemes || it == playerTheme }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.game_edit_player_title),
                style = ty.titleLarge,
                color = mc.textPrimary,
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.game_edit_player_name_label),
                style = ty.labelMedium,
                color = mc.textSecondary,
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(mc.surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = playerName,
                    onValueChange = onNameChanged,
                    singleLine = true,
                    textStyle = ty.titleMedium.copy(color = mc.textPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box {
                            if (playerName.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.game_edit_player_name_placeholder),
                                    style = ty.titleMedium,
                                    color = mc.textDisabled,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.game_edit_player_color_label),
                style = ty.labelMedium,
                color = mc.textSecondary,
            )

            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.heightIn(max = 300.dp),
            ) {
                items(availableThemes, key = { it.name }) { theme ->
                    val isSelected = theme == playerTheme
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onThemeSelected(theme) }
                            .padding(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(theme.accent)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else mc.surfaceVariant.copy(alpha = 0.5f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        Text(
                            text = theme.name,
                            style = ty.labelSmall,
                            color = mc.textSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
