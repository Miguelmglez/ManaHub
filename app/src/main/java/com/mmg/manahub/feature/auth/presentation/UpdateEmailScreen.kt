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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.androidx.compose.koinViewModel

/**
 * Final step of the "Change email" flow — collects the new address and calls
 * [AuthViewModel.confirmEmailUpdate].
 *
 * Reached DIRECTLY from [AccountManagementScreen]'s "Change email" row — unlike
 * [UpdatePasswordScreen], this screen is NOT gated behind [SecurityCodeScreen]. Supabase's "Secure
 * email change" project setting already double-confirms the change via links sent to both the old
 * and the new email address, so the pre-change reauthentication-code gate would be redundant
 * friction for this flow specifically (see the KDoc on
 * [com.mmg.manahub.core.domain.auth.AuthRepository.confirmEmailUpdate] for the full rationale).
 * There is therefore no in-memory handoff and no process-death recovery state to render here — a
 * plain form.
 *
 * @param onEmailUpdated Invoked once [AuthUiState.EmailUpdated] is observed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateEmailScreen(
    onBack: () -> Unit,
    onEmailUpdated: () -> Unit,
    authViewModel: AuthViewModel = koinViewModel(),
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    var newEmail by remember { mutableStateOf("") }

    LaunchedEffect(authUiState) {
        if (authUiState is AuthUiState.EmailUpdated) {
            onEmailUpdated()
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
                        text = stringResource(R.string.account_mgmt_change_email),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        },
    ) { padding ->
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
                text = stringResource(R.string.account_mgmt_update_email_body),
                style = ty.bodyMedium,
                color = mc.textSecondary,
            )

            Spacer(modifier = Modifier.height(sp.xl))

            OutlinedTextField(
                value = newEmail,
                onValueChange = { newEmail = it },
                label = { Text(stringResource(R.string.account_mgmt_new_email_field)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = fieldColors,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(sp.sm))
                Text(text = errorMessage, color = mc.lifeNegative, style = ty.labelMedium)
            }

            Spacer(modifier = Modifier.height(sp.xl))

            MagicCtaButton(
                onClick = { authViewModel.confirmEmailUpdate(newEmail) },
                text = stringResource(R.string.action_save),
                style = MagicCtaStyle.Filled,
                color = MagicCtaColor.Primary,
                isLoading = isLoading,
                enabled = newEmail.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
