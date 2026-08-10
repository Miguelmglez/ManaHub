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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Web auth screen (email/password sign-in, sign-up, and password reset added in the 2026-08-05 web
 * scope expansion). Google OAuth remains a deliberately separate follow-up task (needs external
 * OAuth client credentials this task doesn't have), not added here. Anonymous/guest sign-in was
 * removed project-wide (2026-08-10) -- no-account users are local storage only and never touch
 * Supabase Auth.
 *
 * Rendered as [AuthViewModel]'s [AuthUiState] -- never the raw session object (see [AuthUiState]'s
 * KDoc for why: a `toString()` render would leak the raw JWT).
 *
 * The email/password form ([EmailPasswordForm]) is only shown while signed out -- there is nothing
 * to sign in/up for once [AuthUiState.SignedIn].
 *
 * Hosts the entry point into [com.mmg.manahub.web.settings.SettingsScreen] -- see
 * [com.mmg.manahub.web.navigation.SettingsRoute]'s KDoc for why Settings hangs off this screen
 * instead of getting its own top-level nav tab. The "Profile" row follows the identical pattern
 * (see [com.mmg.manahub.web.navigation.ProfileRoute]'s KDoc) and is shown ONLY while
 * [AuthUiState.SignedIn] -- there is nothing to view/edit while signed out, and
 * [com.mmg.manahub.web.profile.ProfileScreen] itself handles the anonymous-guest sub-case (no
 * `user_profiles` row) once inside. The "Friends" row follows the SAME pattern -- see
 * [com.mmg.manahub.web.navigation.FriendsRoute]'s KDoc.
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
    val formState by viewModel.formState.collectAsState()

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

        if (uiState !is AuthUiState.SignedIn) {
            EmailPasswordForm(viewModel = viewModel, formState = formState, isBusy = isSigningIn)

            HorizontalDivider(color = colors.surfaceVariant.copy(alpha = 0.5f))
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

/**
 * Email/password sign-in and sign-up form, toggled via [AuthFormState.mode]. The mode-toggle
 * buttons are placed side-by-side in a `Row` with `Modifier.weight(1f)` on each -- REQUIRED, not
 * cosmetic: `MagicCtaButton` internally does `fillMaxWidth()` on its content `Row` whenever it has
 * text, so an unweighted sibling in a `Row` only renders the first button (see the W4d Home-screen
 * finding, `project_w4d_home_screen` memory) -- every multi-button `Row` in this codebase must
 * weight each button.
 */
@Composable
private fun EmailPasswordForm(viewModel: AuthViewModel, formState: AuthFormState, isBusy: Boolean) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    val isSignUp = formState.mode == AuthFormMode.SIGN_UP

    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            text = "Sign in to sync your collection, decks, and stats across devices.",
            style = typography.bodyMedium,
            color = colors.textSecondary,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            MagicCtaButton(
                onClick = { viewModel.setMode(AuthFormMode.SIGN_IN) },
                modifier = Modifier.weight(1f),
                text = "Sign in",
                enabled = !isBusy,
                style = if (isSignUp) MagicCtaStyle.Outlined else MagicCtaStyle.Filled,
            )
            MagicCtaButton(
                onClick = { viewModel.setMode(AuthFormMode.SIGN_UP) },
                modifier = Modifier.weight(1f),
                text = "Create account",
                enabled = !isBusy,
                style = if (isSignUp) MagicCtaStyle.Filled else MagicCtaStyle.Outlined,
            )
        }

        AuthTextField(
            label = "Email",
            value = formState.email,
            onValueChange = viewModel::onEmailChanged,
            error = formState.emailError,
            enabled = !isBusy,
        )

        AuthPasswordField(
            label = "Password",
            value = formState.password,
            onValueChange = viewModel::onPasswordChanged,
            error = formState.passwordError,
            enabled = !isBusy,
            visible = passwordVisible,
            onVisibilityToggle = { passwordVisible = !passwordVisible },
        )

        if (!isSignUp) {
            Text(
                text = "Forgot password?",
                style = typography.bodySmall,
                color = colors.primaryAccent,
                modifier = Modifier.clickable(enabled = !isBusy, onClick = viewModel::requestPasswordReset),
            )
        }

        if (isSignUp) {
            AuthPasswordField(
                label = "Confirm password",
                value = formState.confirmPassword,
                onValueChange = viewModel::onConfirmPasswordChanged,
                error = formState.confirmPasswordError,
                enabled = !isBusy,
                visible = confirmPasswordVisible,
                onVisibilityToggle = { confirmPasswordVisible = !confirmPasswordVisible },
            )
            AuthTextField(
                label = "Nickname",
                value = formState.nickname,
                onValueChange = viewModel::onNicknameChanged,
                error = formState.nicknameError,
                enabled = !isBusy,
            )
        }

        formState.infoMessage?.let {
            Text(text = it, style = typography.bodySmall, color = colors.lifePositive)
        }
        formState.formError?.let {
            Text(text = it, style = typography.bodySmall, color = colors.lifeNegative)
        }

        MagicCtaButton(
            onClick = viewModel::submit,
            text = if (isSignUp) "Create account" else "Sign in",
            enabled = !isBusy,
            isLoading = isBusy,
            style = MagicCtaStyle.Filled,
            color = MagicCtaColor.Primary,
        )
    }
}

@Composable
private fun AuthTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(text = label, style = typography.titleMedium, color = colors.textPrimary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = error != null,
            enabled = enabled,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedBorderColor = colors.primaryAccent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(text = error, style = typography.bodySmall, color = colors.lifeNegative)
        }
    }
}

@Composable
private fun AuthPasswordField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: String?,
    enabled: Boolean,
    visible: Boolean,
    onVisibilityToggle: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Text(text = label, style = typography.titleMedium, color = colors.textPrimary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            isError = error != null,
            enabled = enabled,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = onVisibilityToggle) {
                    Icon(
                        imageVector = if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (visible) "Hide password" else "Show password",
                        tint = colors.textSecondary,
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedBorderColor = colors.primaryAccent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(text = error, style = typography.bodySmall, color = colors.lifeNegative)
        }
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
}
