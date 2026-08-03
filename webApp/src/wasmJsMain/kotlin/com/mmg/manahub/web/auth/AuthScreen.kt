package com.mmg.manahub.web.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Web auth screen (web roadmap W2a) -- guest sign-in ONLY. Google OAuth is a deliberately
 * separate follow-up task, not started here.
 *
 * Rendered as [AuthViewModel]'s [AuthUiState] -- never the raw `SessionStatus`/`UserSession`
 * object (see [AuthUiState]'s KDoc for why: a `toString()` render would leak the raw JWT).
 */
@Composable
fun AuthScreen() {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<AuthViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val isSigningIn by viewModel.isSigningIn.collectAsState()
    val profileCheck by viewModel.profileCheck.collectAsState()

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

        // W2b plumbing smoke check ONLY -- proves the web UserProfileClient (shared Ktor client +
        // installSupabaseAuthHeaders) reaches the real backend with the live session's access
        // token. Not a real profile feature; remove/replace once real web screens consume this.
        profileCheck?.let { statusText ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = "user_profiles check (W2b plumbing proof)",
                    style = typography.titleMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = statusText,
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
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
