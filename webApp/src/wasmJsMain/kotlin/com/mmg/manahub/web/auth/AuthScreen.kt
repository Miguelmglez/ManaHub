package com.mmg.manahub.web.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Web auth screen (web roadmap W2a, [onOpenSettings] added by the Settings expansion slice,
 * [onOpenProfile] added by the Profile expansion slice) -- guest sign-in ONLY. Google OAuth is a
 * deliberately separate follow-up task, not started here.
 *
 * Rendered as [AuthViewModel]'s [AuthUiState] -- never the raw `SessionStatus`/`UserSession`
 * object (see [AuthUiState]'s KDoc for why: a `toString()` render would leak the raw JWT).
 *
 * Hosts the entry point into [com.mmg.manahub.web.settings.SettingsScreen] -- see
 * [com.mmg.manahub.web.navigation.SettingsRoute]'s KDoc for why Settings hangs off this screen
 * instead of getting its own top-level nav tab. The "Profile" row follows the identical pattern
 * (see [com.mmg.manahub.web.navigation.ProfileRoute]'s KDoc) and is shown ONLY while
 * [AuthUiState.SignedIn] -- there is nothing to view/edit while signed out, and
 * [com.mmg.manahub.web.profile.ProfileScreen] itself handles the anonymous-guest sub-case (no
 * `user_profiles` row) once inside. The "Friends" row (web scope expansion, Friends slice, approved
 * 2026-08-04) follows the SAME pattern -- see [com.mmg.manahub.web.navigation.FriendsRoute]'s KDoc.
 */
@Composable
fun AuthScreen(
    onOpenSettings: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onOpenFriends: () -> Unit = {},
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<AuthViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val isSigningIn by viewModel.isSigningIn.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        Text(
            text = "Account",
            style = typography.titleLarge,
            color = colors.textPrimary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
            Text(
                text = "Sign in to sync your collection, decks, and stats across devices. " +
                    "You can also continue as a guest -- your data stays on this device and " +
                    "browser until you create a full account.",
                style = typography.bodyMedium,
                color = colors.textSecondary,
            )

            MagicCtaButton(
                onClick = viewModel::signInAsGuest,
                text = "Continue as guest",
                enabled = uiState !is AuthUiState.SignedIn,
                isLoading = isSigningIn,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                text = "Session status",
                style = typography.titleMedium,
                color = colors.textPrimary,
            )
            Text(
                text = uiState.toStatusLabel(),
                style = typography.bodyMedium,
                color = colors.textSecondary,
            )
        }

        HorizontalDivider(color = colors.surfaceVariant.copy(alpha = 0.5f))
        if (uiState is AuthUiState.SignedIn) {
            AccountNavRow(
                title = "Profile",
                subtitle = "Nickname, avatar, and sign out.",
                onClick = onOpenProfile,
            )
            HorizontalDivider(color = colors.surfaceVariant.copy(alpha = 0.5f))
            AccountNavRow(
                title = "Friends",
                subtitle = "Manage friends and requests.",
                onClick = onOpenFriends,
            )
            HorizontalDivider(color = colors.surfaceVariant.copy(alpha = 0.5f))
        }
        AccountNavRow(
            title = "Settings",
            subtitle = "Card search language, currency, and collection display.",
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun AccountNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(text = title, style = typography.titleMedium, color = colors.textPrimary)
            Text(text = subtitle, style = typography.bodySmall, color = colors.textSecondary)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.textSecondary,
        )
    }
}

/**
 * Renders only a short, non-sensitive summary of [AuthUiState] -- never the underlying session
 * object or any token material.
 */
private fun AuthUiState.toStatusLabel(): String = when (this) {
    is AuthUiState.Loading -> "Resolving session..."
    is AuthUiState.SignedOut -> "Not signed in."
    is AuthUiState.SignedIn -> {
        val kind = if (isAnonymous) "guest" else "account"
        "Signed in as $kind. User id: $userId"
    }
    is AuthUiState.Error -> "Sign-in error: $message"
}
