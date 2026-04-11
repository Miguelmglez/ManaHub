package com.mmg.magicfolder.feature.profile

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.ui.components.ManaSymbolImage
import com.mmg.magicfolder.core.ui.components.manaColorFor
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarPickerSheet(
    onDismiss: () -> Unit,
    viewModel: AvatarPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.magicColors.backgroundSecondary,
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
                Text(
                    stringResource(R.string.avatar_picker_title),
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = MaterialTheme.magicColors.textPrimary,
                )
                if (uiState.currentAvatarUrl != null) {
                    TextButton(onClick = {
                        viewModel.removeAvatar()
                        onDismiss()
                    }) {
                        Text(
                            stringResource(R.string.avatar_picker_remove),
                            color = MaterialTheme.magicColors.lifeNegative,
                            style = MaterialTheme.magicTypography.labelMedium,
                        )
                    }
                }
            }

            // ── Color filters ─────────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.avatar_picker_filter_label),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = MaterialTheme.magicColors.textDisabled,
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
                    listOf("W", "U", "B", "R", "G", "C").forEach { color ->
                        val isSelected = uiState.selectedColors.contains(color)
                        val manaColor = manaColorFor(color, MaterialTheme.magicColors)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) {
                                        Modifier
                                            .background(manaColor.copy(alpha = 0.2f))
                                            .border(2.dp, manaColor, CircleShape)
                                    } else Modifier
                                )
                                .clickable { viewModel.toggleColorFilter(color) },
                            contentAlignment = Alignment.Center,
                        ) {
                            ManaSymbolImage(token = color, size = 28.dp)
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.magicColors.surfaceVariant,
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
                            color = MaterialTheme.magicColors.primaryAccent,
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
                            color = MaterialTheme.magicColors.textSecondary,
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
                                        color = MaterialTheme.magicColors.primaryAccent,
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Confirm bar ───────────────────────────────────────────────────
            AnimatedVisibility(visible = uiState.pendingSelection != null) {
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
                        border = BorderStroke(1.dp, MaterialTheme.magicColors.surfaceVariant),
                    ) {
                        Text(
                            stringResource(R.string.action_cancel),
                            color = MaterialTheme.magicColors.textSecondary,
                        )
                    }
                    Button(
                        onClick = {
                            viewModel.confirmSelection()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.magicColors.primaryAccent,
                        ),
                    ) {
                        Text(stringResource(R.string.avatar_picker_confirm))
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtworkTile(
    art: AvatarPickerViewModel.PlaneswalkerArt,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.25f)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 3.dp,
                        color = MaterialTheme.magicColors.primaryAccent,
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

        // Gradient + name at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                    ),
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
        ) {
            Text(
                text = art.name,
                style = MaterialTheme.magicTypography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
