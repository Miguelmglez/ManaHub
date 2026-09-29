package com.mmg.manahub.feature.profile.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.auth.NicknameValidationResult
import com.mmg.manahub.core.domain.auth.NicknameValidator
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaColorPicker
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val AVATAR_GRID_COLUMNS = 3
private const val AVATAR_PREFETCH_DISTANCE = 6

/**
 * Profile edit sheet: nickname and planeswalker avatar.
 *
 * Drag and scrim taps cannot dismiss it while a draft exists; system Back then asks to discard
 * (P-14). The sheet stays open until the save succeeds; a failure keeps the draft and shows a toast.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditSheet(
    onDismiss: () -> Unit,
    viewModel: ProfileEditViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val scope = rememberCoroutineScope()
    val toastState = rememberMagicToastState()
    val hasDraft = uiState.hasChanges || uiState.isSaving
    val allowHide by rememberUpdatedState(!hasDraft)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || allowHide },
    )
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showRemoveDialog by rememberSaveable { mutableStateOf(false) }

    fun dismiss() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    LaunchedEffect(Unit) { viewModel.onSheetOpened() }

    val saveFailedMessage = stringResource(R.string.profile_edit_save_failed)
    val inappropriateMessage = stringResource(R.string.auth_error_nickname_inappropriate)
    val tooLongMessage = stringResource(R.string.auth_error_nickname_too_long)
    val removeFailedMessage = stringResource(R.string.profile_edit_remove_avatar_failed)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ProfileEditViewModel.Event.Saved -> dismiss()
                is ProfileEditViewModel.Event.SaveFailed -> toastState.show(
                    message = when (event.reason) {
                        ProfileEditViewModel.SaveFailure.NICKNAME_INAPPROPRIATE -> inappropriateMessage
                        ProfileEditViewModel.SaveFailure.NICKNAME_TOO_LONG -> tooLongMessage
                        ProfileEditViewModel.SaveFailure.GENERIC -> saveFailedMessage
                    },
                    type = MagicToastType.ERROR,
                )
                ProfileEditViewModel.Event.AvatarRemoveFailed ->
                    toastState.show(message = removeFailedMessage, type = MagicToastType.ERROR)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = mc.backgroundSecondary,
        sheetState = sheetState,
        contentWindowInsets = { WindowInsets(0) },
        dragHandle = null,
    ) {
        BackHandler(enabled = hasDraft) {
            if (!uiState.isSaving) showDiscardDialog = true
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
                    .navigationBarsPadding(),
            ) {
                EditSheetHeader(
                    showRemove = uiState.currentAvatarUrl != null,
                    removeEnabled = !uiState.isSaving,
                    onClose = {
                        if (uiState.hasChanges) showDiscardDialog = true else dismiss()
                    },
                    onRemoveClick = { showRemoveDialog = true },
                )

                NameField(
                    uiState = uiState,
                    onNameChange = viewModel::onNameChange,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
                )

                Spacer(Modifier.height(spacing.sm))

                Column(
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    SheetSectionLabel(icon = Icons.Default.Palette, text = stringResource(R.string.profile_edit_avatar_filter_label))
                    ManaColorPicker(
                        selectedColors = uiState.selectedColors,
                        onToggleColor = viewModel::toggleColorFilter,
                        modifier = Modifier.fillMaxWidth(),
                        itemSize = 48.dp,
                        symbolSize = 32.dp,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        colors = listOf("W", "U", "B", "R", "G", "C"),
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = spacing.md),
                    color = mc.primaryAccent.copy(alpha = 0.15f),
                )

                SheetSectionLabel(
                    icon = Icons.Default.Face,
                    text = stringResource(R.string.profile_edit_avatar_label),
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                )

                ArtworkGrid(
                    uiState = uiState,
                    onSelect = viewModel::selectArt,
                    onLoadMore = viewModel::loadNextPage,
                    onRetry = viewModel::retry,
                    modifier = Modifier.weight(1f),
                )

                AnimatedVisibility(visible = uiState.hasChanges) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.lg, vertical = spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        MagicCtaButton(
                            onClick = viewModel::confirmChanges,
                            text = stringResource(R.string.profile_edit_confirm),
                            enabled = uiState.canSave,
                            isLoading = uiState.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                            style = MagicCtaStyle.Filled,
                            color = MagicCtaColor.Primary,
                        )
                        MagicCtaButton(
                            onClick = viewModel::cancelSelection,
                            text = stringResource(R.string.action_cancel),
                            enabled = !uiState.isSaving,
                            modifier = Modifier.fillMaxWidth(),
                            style = MagicCtaStyle.Outlined,
                            color = MagicCtaColor.Primary,
                        )
                    }
                }
            }

            MagicToastHost(state = toastState, modifier = Modifier.matchParentSize())
        }
    }

    if (showDiscardDialog) {
        MagicAlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = stringResource(R.string.profile_edit_discard_title),
            text = stringResource(R.string.profile_edit_discard_text),
            confirmLabel = stringResource(R.string.action_discard),
            onConfirm = {
                showDiscardDialog = false
                viewModel.cancelSelection()
                dismiss()
            },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { showDiscardDialog = false },
            confirmColor = MagicCtaColor.Error,
        )
    }

    if (showRemoveDialog) {
        MagicAlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = stringResource(R.string.profile_edit_remove_avatar_title),
            text = stringResource(R.string.profile_edit_remove_avatar_text),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                showRemoveDialog = false
                viewModel.removeAvatar()
            },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { showRemoveDialog = false },
            confirmColor = MagicCtaColor.Error,
        )
    }
}

@Composable
private fun EditSheetHeader(
    showRemove: Boolean,
    removeEnabled: Boolean,
    onClose: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xs, vertical = spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
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
            )
        }

        if (showRemove) {
            MagicCtaButton(
                onClick = onRemoveClick,
                text = stringResource(R.string.profile_edit_avatar_remove),
                enabled = removeEnabled,
                style = MagicCtaStyle.Ghost,
                color = MagicCtaColor.Error,
                contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.md),
            )
        }
    }
}

@Composable
private fun SheetSectionLabel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = mc.primaryAccent, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
    }
}

@Composable
private fun NameField(
    uiState: ProfileEditViewModel.UiState,
    onNameChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val showError = uiState.isNameEdited && uiState.nameValidation != NicknameValidationResult.VALID
    val errorText = when (uiState.nameValidation) {
        NicknameValidationResult.REQUIRED -> stringResource(R.string.auth_error_nickname_required)
        NicknameValidationResult.TOO_LONG -> stringResource(R.string.auth_error_nickname_too_long)
        NicknameValidationResult.INVALID_CHARACTERS -> stringResource(R.string.auth_error_nickname_invalid)
        NicknameValidationResult.VALID -> null
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
    ) {
        SheetSectionLabel(icon = Icons.Default.Person, text = stringResource(R.string.game_setup_player_name_label))
        OutlinedTextField(
            value = uiState.pendingName,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !uiState.isSaving,
            textStyle = MaterialTheme.magicTypography.titleMedium,
            isError = showError,
            supportingText = {
                if (showError && errorText != null) {
                    Text(errorText, style = MaterialTheme.magicTypography.labelSmall, color = mc.lifeNegative)
                } else {
                    Text(
                        "${uiState.pendingName.length}/${NicknameValidator.MAX_LENGTH}",
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textDisabled,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
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
                focusedContainerColor = mc.surface.copy(alpha = 0.3f),
                unfocusedContainerColor = mc.surface.copy(alpha = 0.3f),
            ),
            shape = CardShape,
        )
    }
}

@Composable
private fun ArtworkGrid(
    uiState: ProfileEditViewModel.UiState,
    onSelect: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val gridState = rememberLazyGridState()
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)

    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastVisible to gridState.layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (lastIndex, total) ->
                if (lastIndex != null && lastIndex >= total - AVATAR_PREFETCH_DISTANCE) currentOnLoadMore()
            }
    }

    when {
        uiState.isLoading && uiState.artworks.isEmpty() -> Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) { MagicLoadingSpinner() }

        uiState.loadFailed && uiState.artworks.isEmpty() -> Box(modifier = modifier.fillMaxWidth()) {
            InlineErrorState(
                message = stringResource(R.string.error_scryfall),
                retryLabel = stringResource(R.string.action_retry),
                onRetry = onRetry,
                modifier = Modifier.padding(spacing.lg),
            )
        }

        uiState.artworks.isEmpty() -> EmptyState(
            title = stringResource(R.string.profile_edit_no_artworks_title),
            subtitle = stringResource(R.string.profile_edit_no_artworks_subtitle),
            modifier = modifier.fillMaxWidth(),
        )

        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(AVATAR_GRID_COLUMNS),
            state = gridState,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = spacing.md, vertical = spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            items(items = uiState.artworks, key = { it.artCropUrl }) { art ->
                val isSelected = art.artCropUrl == uiState.pendingSelection ||
                    (uiState.pendingSelection == null && art.artCropUrl == uiState.currentAvatarUrl)
                ArtworkTile(art = art, isSelected = isSelected, onClick = { onSelect(art.artCropUrl) })
            }

            if (uiState.isLoading || uiState.appendFailed) {
                item(key = "grid_footer", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.lg),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (uiState.appendFailed) {
                            InlineErrorState(
                                message = stringResource(R.string.error_scryfall),
                                retryLabel = stringResource(R.string.action_retry),
                                onRetry = onRetry,
                            )
                        } else {
                            MagicLoadingSpinner(size = MagicLoadingSize.Small)
                        }
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
    val scale by animateFloatAsState(if (isSelected) 0.95f else 1f, label = "scale")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(scale)
            .clip(CardShape)
            .background(mc.surface)
            .then(
                if (isSelected) Modifier.border(width = 2.dp, color = mc.primaryAccent, shape = CardShape) else Modifier
            )
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
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
                contentAlignment = Alignment.BottomEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier
                        .padding(MaterialTheme.spacing.xs)
                        .size(20.dp)
                        .background(mc.background, CircleShape),
                )
            }
        }
    }
}
