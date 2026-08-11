package com.mmg.manahub.feature.auth.presentation

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.androidx.compose.koinViewModel

/**
 * Final step of the "Change password" / "Set a password" flow — collects the new password (with
 * the same live [PasswordStrengthIndicator] used at sign-up, extracted out of `LoginSheet.kt`) and
 * calls [AuthViewModel.updatePassword] with the [code] handed off from [SecurityCodeScreen].
 *
 * Also backs the "Set a password" CTA for a Google-only account (Phase 2 finding: `updatePassword`
 * on a Google-only account is sufficient by itself to gain email/password sign-in — no separate
 * call needed) — the SAME flow, just reached via different copy on [AccountManagementScreen].
 *
 * @param code The reauthentication nonce forwarded in-memory from `AppNavGraph`'s hoisted handoff
 *   state. Null means the handoff was lost (process death restoring this destination from the back
 *   stack) — mirrors the `PlaytestHand` "pendingPlaytestSetup was null" guard: rather than crash,
 *   this renders a recoverable error state that routes back to a fresh [SecurityCodeScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatePasswordScreen(
    code: String?,
    onBack: () -> Unit,
    onRequestNewCode: () -> Unit,
    onPasswordUpdated: () -> Unit,
    authViewModel: AuthViewModel = koinViewModel(),
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    var newPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordStrength = remember(newPassword) { PasswordStrength.from(newPassword) }

    LaunchedEffect(authUiState) {
        if (authUiState is AuthUiState.PasswordUpdated) {
            onPasswordUpdated()
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
                        text = stringResource(R.string.account_mgmt_change_password),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        },
    ) { padding ->
        if (code == null) {
            FullErrorState(
                message = stringResource(R.string.account_mgmt_session_expired),
                retryLabel = stringResource(R.string.account_mgmt_request_new_code),
                onRetry = onRequestNewCode,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(sp.lg),
        ) {
            Text(
                text = stringResource(R.string.account_mgmt_update_password_body),
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
                Spacer(modifier = Modifier.height(sp.xs))
                MagicCtaButton(
                    onClick = onRequestNewCode,
                    text = stringResource(R.string.account_mgmt_request_new_code),
                    style = MagicCtaStyle.Ghost,
                    color = MagicCtaColor.Primary,
                )
            }

            Spacer(modifier = Modifier.height(sp.xl))

            MagicCtaButton(
                onClick = { authViewModel.updatePassword(newPassword, code) },
                text = stringResource(R.string.action_save),
                style = MagicCtaStyle.Filled,
                color = MagicCtaColor.Primary,
                isLoading = isLoading,
                enabled = passwordStrength.allMet,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
