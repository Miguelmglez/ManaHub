package com.mmg.manahub.web.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlinx.coroutines.flow.collectLatest
import org.koin.compose.viewmodel.koinViewModel

/**
 * Profile -- web scope expansion (Settings -> Profile -> Add Card, approved 2026-08-04). A
 * destination you navigate INTO from [com.mmg.manahub.web.auth.AuthScreen]'s "Profile" row, same
 * "hangs off the account surface, not a top-level tab" precedent as
 * [com.mmg.manahub.web.settings.SettingsScreen].
 *
 * Deliberately MINIMAL: nickname edit, avatar DISPLAY only (no upload -- that needs a Storage
 * bucket + file picker, out of scope for this slice), a read-only game tag if present, an
 * "Invite a friend" share-link section (Friends completion slice, 2026-08-05 -- see
 * [InviteLinkSection]'s KDoc), and sign out. Explicitly excludes everything gamification-related
 * (achievements, stats, level/tier, cosmetics, the `?tab=` deep-link arg) per the web v1 MVP scope
 * -- see Android's `ProfileScreen.kt`/`ProfileViewModel.kt` for the full (out-of-scope-for-web)
 * surface this intentionally does not port.
 *
 * [onSignedOut] is invoked once [ProfileViewModel.events] actually emits [ProfileEvent.SignedOut]
 * (sign-out completed against the SDK), never optimistically on button click.
 */
@Composable
fun ProfileScreen(onSignedOut: () -> Unit = {}) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<ProfileViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val nicknameInput by viewModel.nicknameInput.collectAsState()
    val isSavingNickname by viewModel.isSavingNickname.collectAsState()
    val nicknameError by viewModel.nicknameError.collectAsState()
    val nicknameSaved by viewModel.nicknameSaved.collectAsState()
    val isSigningOut by viewModel.isSigningOut.collectAsState()
    val shareUrl by viewModel.shareUrl.collectAsState()
    val linkCopied by viewModel.linkCopied.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                ProfileEvent.SignedOut -> onSignedOut()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        Text(
            text = "Profile",
            style = typography.titleLarge,
            color = colors.textPrimary,
        )

        when (val state = uiState) {
            is ProfileUiState.Loading -> Text(
                text = "Loading profile...",
                style = typography.bodyMedium,
                color = colors.textSecondary,
            )

            is ProfileUiState.SignedOut -> Text(
                text = "You're not signed in.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
            )

            is ProfileUiState.Error -> InlineErrorState(message = state.message)

            is ProfileUiState.Guest -> {
                Text(
                    text = "You're browsing as a guest. Create a full account to set a nickname " +
                        "and avatar -- your data stays on this device and browser until then.",
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                )
                SignOutSection(
                    isSigningOut = isSigningOut,
                    onSignOut = viewModel::signOut,
                )
            }

            is ProfileUiState.Loaded -> {
                ProfileAvatar(avatarUrl = state.avatarUrl)

                NicknameEditor(
                    nicknameInput = nicknameInput,
                    isSaving = isSavingNickname,
                    error = nicknameError,
                    saved = nicknameSaved,
                    onInputChanged = viewModel::onNicknameInputChanged,
                    onSave = viewModel::saveNickname,
                )

                if (state.gameTag != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(
                            text = "Game tag",
                            style = typography.titleMedium,
                            color = colors.textPrimary,
                        )
                        Text(
                            text = state.gameTag,
                            style = typography.bodyMedium,
                            color = colors.textSecondary,
                        )
                    }
                }

                if (shareUrl != null) {
                    HorizontalDivider(color = colors.surfaceVariant.copy(alpha = 0.5f))
                    InviteLinkSection(
                        shareUrl = shareUrl,
                        linkCopied = linkCopied,
                        onCopy = viewModel::copyInviteLink,
                    )
                }

                HorizontalDivider(color = colors.surfaceVariant.copy(alpha = 0.5f))
                SignOutSection(
                    isSigningOut = isSigningOut,
                    onSignOut = viewModel::signOut,
                )
            }
        }
    }
}

@Composable
private fun ProfileAvatar(avatarUrl: String?) {
    val colors = MaterialTheme.magicColors
    val avatarModifier = Modifier
        .size(96.dp)
        .clip(CircleShape)
        .background(colors.surfaceVariant)

    if (avatarUrl.isNullOrBlank()) {
        Box(modifier = avatarModifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "No avatar set",
                tint = colors.textSecondary,
                modifier = Modifier.size(64.dp),
            )
        }
    } else {
        AsyncImage(
            model = avatarUrl,
            contentDescription = "Your avatar",
            contentScale = ContentScale.Crop,
            modifier = avatarModifier,
        )
    }
}

@Composable
private fun NicknameEditor(
    nicknameInput: String,
    isSaving: Boolean,
    error: String?,
    saved: Boolean,
    onInputChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = "Nickname", style = typography.titleMedium, color = colors.textPrimary)
        OutlinedTextField(
            value = nicknameInput,
            onValueChange = onInputChanged,
            singleLine = true,
            isError = error != null,
            enabled = !isSaving,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedBorderColor = colors.primaryAccent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(text = error, style = typography.bodySmall, color = colors.lifeNegative)
        } else if (saved) {
            Text(text = "Saved.", style = typography.bodySmall, color = colors.lifePositive)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MagicCtaButton(
                onClick = onSave,
                text = "Save nickname",
                enabled = !isSaving && nicknameInput.isNotBlank(),
                isLoading = isSaving,
                style = MagicCtaStyle.Filled,
                color = MagicCtaColor.Primary,
            )
        }
    }
}

/**
 * "Invite a friend" section (Friends completion slice, 2026-08-05) -- the share side of the
 * referral-invite flow. [shareUrl] is always shown in a [SelectionContainer] (manually selectable/
 * copyable) so the link stays usable even if the Clipboard API call fails; the "Copy link" button
 * is an additional convenience, not the only way to get the link out.
 */
@Composable
private fun InviteLinkSection(shareUrl: String?, linkCopied: Boolean, onCopy: () -> Unit) {
    val url = shareUrl ?: return
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = "Invite a friend", style = typography.titleMedium, color = colors.textPrimary)
        Text(
            text = "Share this link -- when a friend opens it and signs in, you'll be friends automatically.",
            style = typography.bodySmall,
            color = colors.textSecondary,
        )
        SelectionContainer {
            Text(
                text = url,
                style = typography.bodyMedium,
                color = colors.primaryAccent,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.backgroundSecondary)
                    .padding(spacing.sm),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MagicCtaButton(
                onClick = onCopy,
                text = if (linkCopied) "Copied!" else "Copy link",
                style = MagicCtaStyle.Outlined,
                color = if (linkCopied) MagicCtaColor.Success else MagicCtaColor.Primary,
            )
        }
    }
}

@Composable
private fun SignOutSection(isSigningOut: Boolean, onSignOut: () -> Unit) {
    MagicCtaButton(
        onClick = onSignOut,
        text = "Sign out",
        enabled = !isSigningOut,
        isLoading = isSigningOut,
        style = MagicCtaStyle.Outlined,
        color = MagicCtaColor.Error,
    )
}
