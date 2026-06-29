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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
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
/**
 * A bottom sheet for editing a single player's name and color theme.
 *
 * @param playerName Current name for this player.
 * @param playerTheme Currently selected [PlayerThemeColors] for this player.
 * @param isAppUser Whether this slot represents the device's own user.
 * @param title The sheet title text (e.g. "Edit Player").
 * @param nameLabel The label above the name field (e.g. "Name").
 * @param namePlaceholder The placeholder shown when the name field is empty (e.g. "Enter name").
 * @param colorLabel The label above the color grid (e.g. "Choose Color").
 * @param selectedIcon The icon to display on the currently selected theme circle (e.g. Icons.Default.Check).
 * @param usedThemes Themes already assigned to other players; these are hidden from the grid.
 * @param onNameChanged Callback for name field changes.
 * @param onThemeSelected Callback when a theme circle is tapped.
 * @param onDismiss Called when the sheet should be closed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerEditSheet(
    playerName: String,
    playerTheme: PlayerThemeColors,
    isAppUser: Boolean,
    title: String,
    nameLabel: String,
    namePlaceholder: String,
    colorLabel: String,
    selectedIcon: ImageVector,
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
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(mc.surfaceVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = title,
                style = ty.titleLarge,
                color = mc.textPrimary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Name Field Section
            Text(
                text = nameLabel.uppercase(),
                style = ty.labelMedium,
                color = mc.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val interactionSource = remember { MutableInteractionSource() }
            val isFocused by interactionSource.collectIsFocusedAsState()
            
            val fieldBgColor by animateColorAsState(
                targetValue = if (isFocused) mc.primaryAccent.copy(alpha = 0.05f) else mc.surface,
                animationSpec = tween(300), label = "fieldBg"
            )
            val fieldBorderColor by animateColorAsState(
                targetValue = if (isFocused) mc.primaryAccent else mc.surfaceVariant.copy(alpha = 0.5f),
                animationSpec = tween(300), label = "fieldBorder"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(fieldBgColor)
                    .border(1.dp, fieldBorderColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isFocused) mc.primaryAccent else mc.textDisabled,
                    modifier = Modifier.size(24.dp)
                )
                
                Spacer(Modifier.width(12.dp))

                BasicTextField(
                    value = playerName,
                    onValueChange = onNameChanged,
                    singleLine = true,
                    textStyle = ty.titleMedium.copy(color = mc.textPrimary),
                    interactionSource = interactionSource,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            if (playerName.isEmpty()) {
                                Text(
                                    text = namePlaceholder,
                                    style = ty.titleMedium,
                                    color = mc.textDisabled,
                                )
                            }
                            innerTextField()
                        }
                    },
                )

                if (playerName.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = mc.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { onNameChanged("") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Color Grid Section
            Text(
                text = colorLabel.uppercase(),
                style = ty.labelMedium,
                color = mc.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.heightIn(max = 300.dp),
            ) {
                items(availableThemes, key = { it.name }) { theme ->
                    val isSelected = theme == playerTheme
                    
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.1f else 1.0f,
                        animationSpec = tween(300), label = "scale"
                    )
                    
                    val bgAlpha by animateFloatAsState(
                        targetValue = if (isSelected) 0.1f else 0.0f,
                        animationSpec = tween(300), label = "bgAlpha"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(theme.accent.copy(alpha = bgAlpha))
                            .clickable { onThemeSelected(theme) }
                            .padding(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
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
                                    imageVector = selectedIcon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        Text(
                            text = theme.name,
                            style = ty.labelSmall,
                            color = if (isSelected) mc.textPrimary else mc.textSecondary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
