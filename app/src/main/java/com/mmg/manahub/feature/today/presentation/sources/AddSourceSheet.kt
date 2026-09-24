package com.mmg.manahub.feature.today.presentation.sources

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.model.news.ContentSource
import com.mmg.manahub.core.model.news.ResolvedSource
import com.mmg.manahub.core.model.news.SourceResolveError
import com.mmg.manahub.core.ui.components.AvatarImage
import com.mmg.manahub.core.ui.components.CopyBadge
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicFilterChip
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.TimeAgoFormatter
import com.mmg.manahub.feature.today.presentation.common.languageLabelRes
import com.mmg.manahub.feature.today.presentation.common.sourceInitials
import com.mmg.manahub.feature.today.presentation.common.sourceKindRes
import org.koin.androidx.compose.koinViewModel

/** "Paste any URL" add flow: one field → resolve → preview with editable name/language → follow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSourceSheet(
    onDismiss: () -> Unit,
    onFollowed: (sourceName: String) -> Unit,
    viewModel: AddSourceViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentOnFollowed by rememberUpdatedState(onFollowed)
    val dismiss = {
        viewModel.reset()
        onDismiss()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AddSourceEvent.Followed -> currentOnFollowed(event.sourceName)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = dismiss,
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.xl)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.today_add_title),
                style = MaterialTheme.magicTypography.titleLarge,
                color = mc.textPrimary,
            )
            val resolved = state.resolved
            if (resolved == null) {
                LinkStep(
                    state = state,
                    onInputChanged = viewModel::onInputChanged,
                    onFind = viewModel::find,
                )
            } else {
                PreviewStep(
                    resolved = resolved,
                    state = state,
                    onNameChanged = viewModel::onNameChanged,
                    onLanguageSelected = viewModel::onLanguageSelected,
                    onFollow = viewModel::follow,
                    onEditLink = viewModel::editLink,
                )
            }
        }
    }
}

@Composable
private fun LinkStep(
    state: AddSourceUiState,
    onInputChanged: (String) -> Unit,
    onFind: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val error = state.error
    OutlinedTextField(
        value = state.input,
        onValueChange = onInputChanged,
        label = { Text(stringResource(R.string.today_add_hint)) },
        supportingText = {
            Text(
                text = if (error != null) stringResource(errorMessageRes(error)) else stringResource(R.string.today_add_supporting),
                style = ty.bodySmall,
                color = if (error != null) mc.lifeNegative else mc.textSecondary,
            )
        },
        isError = error != null,
        singleLine = true,
        enabled = !state.isResolving,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onFind() }),
        colors = textFieldColors(),
        shape = CardShape,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.isResolving) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            MagicLoadingSpinner(size = MagicLoadingSize.Small)
        }
    }
    MagicCtaButton(
        onClick = onFind,
        text = stringResource(R.string.today_add_find),
        enabled = state.canFind,
        isLoading = state.isResolving,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PreviewStep(
    resolved: ResolvedSource,
    state: AddSourceUiState,
    onNameChanged: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onFollow: () -> Unit,
    onEditLink: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
        AvatarImage(avatarUrl = null, initials = sourceInitials(state.name.ifBlank { resolved.name }), size = PREVIEW_AVATAR_SIZE)
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChanged,
            label = { Text(stringResource(R.string.today_add_name_label)) },
            singleLine = true,
            colors = textFieldColors(),
            shape = CardShape,
            modifier = Modifier.weight(1f),
        )
    }
    CopyBadge(label = stringResource(sourceKindRes(resolved.type)))
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(stringResource(R.string.today_add_language_label), style = ty.labelMedium, color = mc.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ContentSource.SUPPORTED_LANGUAGES.forEach { code ->
                MagicFilterChip(
                    selected = state.language == code,
                    onClick = { onLanguageSelected(code) },
                    label = stringResource(languageLabelRes(code)),
                )
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(stringResource(R.string.today_add_latest), style = ty.labelMedium, color = mc.textSecondary)
        resolved.preview.forEach { item ->
            Column {
                Text(item.title, style = ty.bodyMedium, color = mc.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val age = TimeAgoFormatter.format(item.publishedAt)
                if (age.isNotEmpty()) Text(age, style = ty.labelSmall, color = mc.textDisabled)
            }
        }
    }
    state.error?.let { error ->
        Text(stringResource(errorMessageRes(error)), style = ty.bodySmall, color = mc.lifeNegative)
    }
    MagicCtaButton(
        onClick = onFollow,
        text = stringResource(R.string.today_add_follow),
        enabled = !state.isFollowing,
        isLoading = state.isFollowing,
        modifier = Modifier.fillMaxWidth(),
    )
    MagicCtaButton(
        onClick = onEditLink,
        text = stringResource(R.string.today_add_try_another),
        style = MagicCtaStyle.Ghost,
        color = MagicCtaColor.Neutral,
        enabled = !state.isFollowing,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun textFieldColors() = MaterialTheme.magicColors.let { mc ->
    OutlinedTextFieldDefaults.colors(
        focusedBorderColor = mc.primaryAccent,
        unfocusedBorderColor = mc.surfaceVariant,
        cursorColor = mc.primaryAccent,
        focusedTextColor = mc.textPrimary,
        unfocusedTextColor = mc.textPrimary,
        focusedLabelColor = mc.primaryAccent,
        unfocusedLabelColor = mc.textSecondary,
        errorBorderColor = mc.lifeNegative,
        errorLabelColor = mc.lifeNegative,
    )
}

@StringRes
private fun errorMessageRes(error: SourceResolveError): Int = when (error) {
    SourceResolveError.INVALID_INPUT -> R.string.today_add_error_invalid
    SourceResolveError.NOT_HTTPS -> R.string.today_add_error_not_https
    SourceResolveError.UNREACHABLE -> R.string.today_add_error_unreachable
    SourceResolveError.NO_FEED_FOUND -> R.string.today_add_error_no_feed
    SourceResolveError.YOUTUBE_CHANNEL_NOT_FOUND -> R.string.today_add_error_youtube
    SourceResolveError.EMPTY_FEED -> R.string.today_add_error_empty
    SourceResolveError.ALREADY_FOLLOWING -> R.string.today_add_error_already
}

private const val PREVIEW_AVATAR_SIZE = 48
