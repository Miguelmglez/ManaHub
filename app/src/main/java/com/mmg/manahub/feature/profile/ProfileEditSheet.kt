package com.mmg.manahub.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.ManaColorPicker
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditSheet(
    onDismiss: () -> Unit,
    onNicknameUpdate: ((String) -> Unit)? = null,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding(),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.profile_edit_title),
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = mc.textPrimary,
                    )
                    
                    // Game Tag Badge
                    uiState.gameTag?.let { tag ->
                        Box(
                            modifier = Modifier
                                .background(
                                    color = mc.primaryAccent.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = tag,
                                color = mc.primaryAccent,
                                style = MaterialTheme.magicTypography.labelSmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ),
                            )
                        }
                    }
                }

                if (uiState.currentAvatarUrl != null) {
                    TextButton(onClick = {
                        viewModel.removeAvatar()
                    }) {
                        Text(
                            stringResource(R.string.profile_edit_avatar_remove),
                            color = mc.lifeNegative,
                            style = MaterialTheme.magicTypography.labelMedium,
                        )
                    }
                }
            }

            // ── Name Edit Field ──────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    stringResource(R.string.game_setup_player_name_label),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textDisabled,
                )
                OutlinedTextField(
                    value = uiState.pendingName,
                    onValueChange = viewModel::onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.magicTypography.bodyMedium,
                    isError = !uiState.isNameValid,
                    supportingText = {
                        if (!uiState.isNameValid) {
                            Text(
                                stringResource(R.string.auth_error_name_too_short), // Reusing error or creating new one
                                style = MaterialTheme.magicTypography.labelSmall,
                                color = mc.lifeNegative
                            )
                        } else {
                            Text(
                                "${uiState.pendingName.length}/30",
                                style = MaterialTheme.magicTypography.labelSmall,
                                color = mc.textDisabled,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.End
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = mc.textPrimary,
                        unfocusedTextColor = mc.textPrimary,
                        cursorColor = mc.primaryAccent,
                        focusedBorderColor = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // ── Color filters ─────────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.profile_edit_avatar_filter_label),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textDisabled,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = uiState.selectedColors.isEmpty(),
                        onClick = viewModel::clearColorFilters,
                        label = {
                            Text(
                                stringResource(R.string.collection_filter_all),
                                style = MaterialTheme.magicTypography.labelSmall,
                            )
                        },
                    )
                    ManaColorPicker(
                        selectedColors = uiState.selectedColors,
                        onToggleColor = viewModel::toggleColorFilter,
                        itemSize = 40.dp,
                        symbolSize = 28.dp,
                        spacing = 8.dp
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = mc.surfaceVariant,
            )

            // ── Artwork grid ──────────────────────────────────────────────────
            val gridState = rememberLazyGridState()

            LaunchedEffect(gridState) {
                snapshotFlow {
                    val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()
                    val total = gridState.layoutInfo.totalItemsCount
                    lastVisible?.index to total
                }
                    .distinctUntilChanged()
                    .collect { (lastIndex, total) ->
                        if (lastIndex != null &&
                            lastIndex >= total - 6 &&
                            uiState.hasMore &&
                            !uiState.isLoading
                        ) {
                            viewModel.loadNextPage()
                        }
                    }
            }

            when {
                uiState.isLoading && uiState.artworks.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = mc.primaryAccent,
                        )
                    }
                }

                uiState.error != null && uiState.artworks.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.error_scryfall),
                            color = mc.textSecondary,
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = uiState.artworks,
                            key = { it.artCropUrl },
                        ) { art ->
                            val isSelected =
                                art.artCropUrl == uiState.pendingSelection ||
                                        (uiState.pendingSelection == null &&
                                                art.artCropUrl == uiState.currentAvatarUrl)

                            ArtworkTile(
                                art = art,
                                isSelected = isSelected,
                                onClick = { viewModel.selectArt(art.artCropUrl) },
                            )
                        }

                        if (uiState.isLoading && uiState.artworks.isNotEmpty()) {
                            item(span = { GridItemSpan(3) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = mc.primaryAccent,
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Confirm bar ───────────────────────────────────────────────────
            AnimatedVisibility(visible = uiState.hasChanges) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::cancelSelection,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, mc.surfaceVariant),
                    ) {
                        Text(
                            stringResource(R.string.action_cancel),
                            color = mc.textSecondary,
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.confirmChanges(onNicknameUpdate)
                            onDismiss()
                        },
                        enabled = uiState.isNameValid,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mc.primaryAccent,
                        ),
                    ) {
                        Text(stringResource(R.string.profile_edit_confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkTile(
    art: ProfileEditViewModel.PlaneswalkerArt,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.25f)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = mc.primaryAccent,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else Modifier
            )
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(art.artCropUrl)
                .crossfade(true)
                .build(),
            contentDescription = art.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
