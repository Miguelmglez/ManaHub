package com.mmg.manahub.feature.profile.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.ManaColorPicker
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditSheet(
    onDismiss: () -> Unit,
    onNicknameUpdate: ((String) -> Unit)? = null,
    viewModel: ProfileEditViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    fun dismiss() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null,
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
                    .padding(horizontal = MaterialTheme.spacing.xs, vertical = MaterialTheme.spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::dismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = mc.textSecondary,
                        )
                    }
                    Text(
                        stringResource(R.string.profile_edit_title),
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = mc.textPrimary,
                        modifier = Modifier.offset(x = (-4).dp)
                    )
                }

                if (uiState.currentAvatarUrl != null) {
                    TextButton(
                        onClick = { viewModel.removeAvatar() },
                        contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.md)
                    ) {
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
                    .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        stringResource(R.string.game_setup_player_name_label),
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = mc.textPrimary,
                    )
                }
                
                OutlinedTextField(
                    value = uiState.pendingName,
                    onValueChange = viewModel::onNameChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(mc.surface.copy(alpha = 0.3f), CardShape),
                    singleLine = true,
                    textStyle = MaterialTheme.magicTypography.titleMedium,
                    isError = uiState.pendingName != uiState.currentName && !uiState.isNameValid,
                    supportingText = {
                        if (uiState.pendingName != uiState.currentName && !uiState.isNameValid) {
                            Text(
                                stringResource(R.string.auth_error_name_too_short),
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
                        errorBorderColor = mc.lifeNegative,
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    shape = CardShape
                )
            }

            Spacer(Modifier.height(MaterialTheme.spacing.sm))

            // ── Color filters ─────────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        stringResource(R.string.profile_edit_avatar_filter_label),
                        style = MaterialTheme.magicTypography.titleMedium,
                        color = mc.textPrimary,
                    )
                }
                ManaColorPicker(
                    selectedColors = uiState.selectedColors,
                    onToggleColor = viewModel::toggleColorFilter,
                    itemSize = 42.dp,
                    symbolSize = 30.dp,
                    spacing = MaterialTheme.spacing.sm,
                    colors = listOf("W", "U", "B", "R", "G", "C")
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.md),
                color = mc.primaryAccent.copy(alpha = 0.15f),
            )

            // ── Artwork grid ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    stringResource(R.string.profile_edit_avatar_label),
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.textPrimary,
                )
            }

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
                        MagicLoadingSpinner()
                    }
                }

                uiState.error != null && uiState.artworks.isEmpty() -> {
                    InlineErrorState(
                        message = stringResource(R.string.error_scryfall),
                        retryLabel = stringResource(R.string.action_retry),
                        onRetry = viewModel::loadNextPage,
                        modifier = Modifier.padding(MaterialTheme.spacing.lg)
                    )
                }

                uiState.artworks.isEmpty() && !uiState.isLoading -> {
                    EmptyState(
                        title = stringResource(R.string.profile_edit_no_artworks_title),
                        subtitle = stringResource(R.string.profile_edit_no_artworks_subtitle),
                        modifier = Modifier.weight(1f)
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(
                            horizontal = MaterialTheme.spacing.md,
                            vertical = MaterialTheme.spacing.sm
                        ),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
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
                                        .padding(MaterialTheme.spacing.lg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    MagicLoadingSpinner(
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Confirm bar ───────────────────────────────────────────────────
            AnimatedVisibility(visible = uiState.hasChanges) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MaterialTheme.spacing.lg, vertical = MaterialTheme.spacing.md),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm)
                ) {
                    MagicCtaButton(
                        onClick = {
                            viewModel.confirmChanges(onNicknameUpdate)
                            onDismiss()
                        },
                        text = stringResource(R.string.profile_edit_confirm),
                        enabled = uiState.pendingName == uiState.currentName || uiState.isNameValid,
                        modifier = Modifier.fillMaxWidth(),
                        style = MagicCtaStyle.Filled,
                        color = MagicCtaColor.Primary
                    )
                    MagicCtaButton(
                        onClick = viewModel::cancelSelection,
                        text = stringResource(R.string.action_cancel),
                        modifier = Modifier.fillMaxWidth(),
                        style = MagicCtaStyle.Outlined,
                        color = MagicCtaColor.Primary
                    )
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
    val scale by animateFloatAsState(if (isSelected) 0.95f else 1f, label = "scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(scale)
            .clip(CardShape)
            .background(mc.surface)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = 2.dp,
                        color = mc.primaryAccent,
                        shape = CardShape
                    )
                } else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
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

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(mc.primaryAccent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.BottomEnd
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier
                        .padding(MaterialTheme.spacing.xs)
                        .size(20.dp)
                        .background(mc.background, RoundedCornerShape(10.dp))
                )
            }
        }
    }
}
