package com.mmg.manahub.feature.auth.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
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
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.recordNonFatal
import org.koin.androidx.compose.koinViewModel

/**
 * "Forgot password" completion screen — the destination the `manahub://auth/recovery` deep link
 * routes to once `MainActivity` detects a `type=recovery` Supabase auth callback (see
 * `AppNavGraph.kt`'s deep-link wiring KDoc).
 *
 * Tapping the recovery email link already establishes a temporary, fully-authenticated recovery
 * session on-device (GoTrue mints a real access token for it via
 * `supabaseClient.handleDeeplinks(intent)`, already wired in `MainActivity`) — so this screen needs
 * NO reauthentication code, unlike [UpdatePasswordScreen]. It calls
 * [AuthViewModel.confirmPasswordReset] directly, which is a thin wrapper over
 * `Auth.updateUser(password = ...)` with no nonce.
 *
 * If the link is invalid/expired (no recovery session materializes — [SessionState] never becomes
 * [SessionState.Authenticated]), this renders a recoverable error state instead of a broken form.
 *
 * SECURITY: an [SessionState.Authenticated] session alone is NOT proof this screen was reached via
 * a genuine recovery link — `MainActivity`'s `type=recovery` deep-link check is driven by an
 * attacker-controllable URI, so a forged intent can route an already-logged-in user here on a
 * normal session. This screen therefore also checks [AuthUser.isRecoverySession] (the server-issued
 * `amr` claim) and renders the SAME error state as an invalid/expired link when it is false — the
 * password form is never shown for a non-recovery session. [AuthViewModel.confirmPasswordReset]
 * enforces the identical check as the real security boundary; this is UI-level defense-in-depth so
 * the user is never shown a form that would silently fail on submit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordConfirmScreen(
    onBack: () -> Unit,
    onPasswordReset: () -> Unit,
    authViewModel: AuthViewModel = koinViewModel(),
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    var newPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordStrength = remember(newPassword) { PasswordStrength.from(newPassword) }

    LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("screen_viewed: reset_password_confirm")
    }

    LaunchedEffect(authUiState) {
        if (authUiState is AuthUiState.PasswordResetConfirmed) {
            onPasswordReset()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            Surface(color = mc.backgroundSecondary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = sp.lg, vertical = sp.md),
                ) {
                    Text(
                        text = stringResource(R.string.account_mgmt_reset_password_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        },
    ) { padding ->
        when (val state = sessionState) {
            SessionState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = mc.primaryAccent)
                }
            }

            SessionState.Unauthenticated -> {
                FullErrorState(
                    message = stringResource(R.string.account_mgmt_reset_link_invalid),
                    retryLabel = stringResource(R.string.account_mgmt_back_to_signin),
                    onRetry = onBack,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            }

            is SessionState.Authenticated -> if (!state.user.isRecoverySession) {
                // SECURITY: reached via a forged manahub://auth?type=recovery deep link fired at an
                // already-authenticated (but non-recovery) session — render the same error state as
                // an invalid/expired link rather than a functional-looking password form. See the
                // SECURITY KDoc on this composable and on AuthViewModel.confirmPasswordReset.
                // Distinct NON_FATAL key from the VM-layer gate (account_mgmt_reset_vm_blocked_...)
                // so Crashlytics can tell which defense layer actually caught it.
                LaunchedEffect(Unit) {
                    FirebaseCrashlytics.getInstance().log("reset_password_confirm_screen_blocked_not_recovery_session")
                    recordNonFatal("account_mgmt_reset_screen_blocked_not_recovery_session")
                }
                FullErrorState(
                    message = stringResource(R.string.account_mgmt_reset_link_invalid),
                    retryLabel = stringResource(R.string.account_mgmt_back_to_signin),
                    onRetry = onBack,
                    modifier = Modifier.fillMaxSize().padding(padding),
                )
            } else {
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

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(sp.lg),
                ) {
                    Text(
                        text = stringResource(R.string.account_mgmt_reset_password_body),
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                    )

                    Spacer(modifier = Modifier.height(sp.xl))

                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text(stringResource(R.string.account_mgmt_new_password_field)) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
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
                        onClick = { authViewModel.confirmPasswordReset(newPassword) },
                        text = stringResource(R.string.account_mgmt_reset_password_cta),
                        style = MagicCtaStyle.Filled,
                        color = MagicCtaColor.Primary,
                        isLoading = isLoading,
                        enabled = passwordStrength.allMet,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
