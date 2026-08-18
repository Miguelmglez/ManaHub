package com.mmg.manahub.feature.auth.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.androidx.compose.koinViewModel

/**
 * SOLE screen for both "Change password" (account already has an email/password identity) and
 * "Set a password" (Google-only account with none yet) — reached DIRECTLY from
 * [AccountManagementScreen], no intermediate code-entry gate.
 *
 * Architecture pivot (replaces the retired email-nonce reauthentication flow —
 * `SecurityCodeScreen`/`Screen.SecurityCode`/the `pendingReauthCode` nav handoff are all gone):
 * Supabase's "Require current password when updating" project setting (confirmed ON) already
 * protects a password change server-side, so this screen collects the current password directly
 * instead of gating behind a separate emailed-code step. [AuthViewModel.updatePassword] calls
 * `Auth.updateUser { password = ...; currentPassword = ... }` — see its KDoc and
 * [com.mmg.manahub.core.domain.auth.AuthRepository.updatePassword]'s KDoc for the full rationale.
 *
 * @param requireCurrentPassword Whether the account already has an email/password identity
 *   ("Change password" — the current-password field is shown and required) or not ("Set a
 *   password" on a Google-only account — the field is omitted entirely, since GoTrue skips the
 *   current-password check when the account has no password yet). Mirrors
 *   [AccountManagementScreen]'s `hasEmailIdentity` check, threaded through nav by the caller — a
 *   plain boolean nav argument, unlike the retired reauth code, since it carries no sensitive data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatePasswordScreen(
    requireCurrentPassword: Boolean,
    onBack: () -> Unit,
    onPasswordUpdated: () -> Unit,
    authViewModel: AuthViewModel = koinViewModel(),
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()
    var currentPassword by remember { mutableStateOf("") }
    var currentPasswordVisible by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordVisible by remember { mutableStateOf(false) }
    val passwordStrength = remember(newPassword) { PasswordStrength.from(newPassword) }
    val toastState = rememberMagicToastState()
    val resetSentMessage = stringResource(R.string.auth_reset_sent)

    // The account's own email, used to fire the "Forgot your password?" reset link below without
    // asking the user to retype it (this screen is only reachable while authenticated).
    val accountEmail = (sessionState as? SessionState.Authenticated)?.user?.email

    LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("screen_viewed: update_password")
    }

    // authUiState is a single shared state across every AuthViewModel action (see AuthUiState's
    // KDoc) — both this screen's own updatePassword() call AND the "Forgot your password?" link's
    // resetPassword() call land here. ResetSent is reset back to Idle immediately after showing its
    // toast so it never lingers to confuse a later action's isLoading/errorMessage read below.
    LaunchedEffect(authUiState) {
        when (authUiState) {
            is AuthUiState.PasswordUpdated -> onPasswordUpdated()
            is AuthUiState.ResetSent -> {
                toastState.show(resetSentMessage, MagicToastType.SUCCESS)
                authViewModel.resetUiState()
            }
            else -> Unit
        }
    }

    val isLoading = authUiState is AuthUiState.Loading
    val errorMessage = (authUiState as? AuthUiState.Error)?.message

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = mc.primaryAccent,
        unfocusedBorderColor = mc.textSecondary.copy(alpha = 0.4f),
        focusedLabelColor = mc.primaryAccent,
        unfocusedLabelColor = mc.textSecondary,
        cursorColor = mc.primaryAccent,
        focusedTextColor = mc.textPrimary,
        unfocusedTextColor = mc.textPrimary,
    )

    val canSubmit = passwordStrength.allMet && (!requireCurrentPassword || currentPassword.isNotBlank())

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(color = mc.backgroundSecondary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = sp.xxs, vertical = sp.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textPrimary,
                        )
                    }
                    Text(
                        text = stringResource(
                            if (requireCurrentPassword) {
                                R.string.account_mgmt_change_password
                            } else {
                                R.string.account_mgmt_set_password
                            }
                        ),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(sp.lg),
            ) {
                Text(
                    text = stringResource(
                        if (requireCurrentPassword) {
                            R.string.account_mgmt_update_password_body
                        } else {
                            R.string.account_mgmt_set_password_body
                        }
                    ),
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                )

                Spacer(modifier = Modifier.height(sp.md))

                // Persistent guidance (not a toast): states up front that the current password is
                // required, and what happens immediately after saving — no email step is involved
                // on this path, unlike "Change email"/"Forgot password".
                AuthFlowStepsCard(
                    title = stringResource(R.string.account_mgmt_change_password_steps_title),
                    steps = if (requireCurrentPassword) {
                        listOf(
                            stringResource(R.string.account_mgmt_change_password_step_current),
                            stringResource(R.string.account_mgmt_change_password_step_new),
                            stringResource(R.string.account_mgmt_change_password_step_immediate),
                        )
                    } else {
                        listOf(
                            stringResource(R.string.account_mgmt_set_password_step_choose),
                            stringResource(R.string.account_mgmt_set_password_step_immediate),
                        )
                    },
                )

                Spacer(modifier = Modifier.height(sp.lg))

                if (requireCurrentPassword) {
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text(stringResource(R.string.account_mgmt_current_password_field)) },
                        singleLine = true,
                        visualTransformation = if (currentPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { currentPasswordVisible = !currentPasswordVisible }) {
                                Icon(
                                    imageVector = if (currentPasswordVisible) {
                                        Icons.Default.Visibility
                                    } else {
                                        Icons.Default.VisibilityOff
                                    },
                                    contentDescription = null,
                                    tint = mc.textSecondary,
                                )
                            }
                        },
                        colors = fieldColors,
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Recovery path for a forgotten self-set password (2026-08-17 fix): before
                    // this, a Google-only user who set a password once and forgot it had no escape
                    // from this screen other than signing out and going through the login screen's
                    // own reset flow. Reuses AuthViewModel.resetPassword — the SAME action
                    // LoginSheet's forgot-password dialog calls — but skips asking for the email
                    // since it's already known from the authenticated session. Only shown here, not
                    // in the "Set a password" (requireCurrentPassword == false) branch, since there
                    // is no current password to forget in that flow.
                    TextButton(
                        onClick = {
                            accountEmail?.let {
                                FirebaseCrashlytics.getInstance().log("account_mgmt_forgot_password_from_update_screen_tapped")
                                authViewModel.resetPassword(it)
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        enabled = !isLoading && accountEmail != null,
                    ) {
                        Text(
                            text = stringResource(R.string.auth_link_forgot),
                            color = mc.secondaryAccent,
                            style = ty.labelMedium,
                        )
                    }

                    Spacer(modifier = Modifier.height(sp.md))
                }

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(stringResource(R.string.account_mgmt_new_password_field)) },
                    singleLine = true,
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(
                                imageVector = if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null,
                                tint = mc.textSecondary,
                            )
                        }
                    },
                    colors = fieldColors,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )

                PasswordStrengthIndicator(strength = passwordStrength)

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(sp.sm))
                    Text(text = errorMessage, color = mc.lifeNegative, style = ty.labelMedium)
                }

                Spacer(modifier = Modifier.height(sp.xl))

                MagicCtaButton(
                    onClick = {
                        authViewModel.updatePassword(
                            newPassword = newPassword,
                            currentPassword = if (requireCurrentPassword) currentPassword else null,
                        )
                    },
                    text = stringResource(R.string.action_save),
                    style = MagicCtaStyle.Filled,
                    color = MagicCtaColor.Primary,
                    isLoading = isLoading,
                    enabled = canSubmit && !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            MagicToastHost(
                state = toastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
    }
}
