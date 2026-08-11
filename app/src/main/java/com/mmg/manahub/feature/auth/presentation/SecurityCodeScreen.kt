package com.mmg.manahub.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Reauthentication-code gate ahead of the "Change password" / "Set a password" flow.
 *
 * "Change email" no longer goes through this screen: Supabase's "Secure email change" project
 * setting already double-confirms an email change via links sent to both the old and new inbox, so
 * the reauth-code gate would be redundant friction there (see the KDoc on
 * [com.mmg.manahub.core.domain.auth.AuthRepository.confirmEmailUpdate]). Password changes have no
 * equivalent server-side double-confirm, so this gate stays in front of them.
 *
 * On first composition it fires [AuthViewModel.requestReauthentication] (sends a nonce to the
 * user's verified email) — this is the ONE call site that triggers the initial send, so navigating
 * here never double-sends. "Resend code" re-calls the same use case, gated by a 60s cooldown.
 *
 * The entered code is NOT verified here — the nonce is single-use and tied to the actual
 * `updateUser` call downstream (see [AuthRepository.updatePassword]'s KDoc). "Continue" simply
 * forwards the in-memory code to [onCodeConfirmed]; the caller (`AppNavGraph`) hands it to
 * [UpdatePasswordScreen] via an in-memory hoisted handoff (never a nav-graph string argument, never
 * persisted) — the SAME pattern already used for `PlaytestSetup`.
 *
 * On an invalid/expired-nonce error surfaced by the downstream update call, the caller pops back to
 * a FRESH instance of this screen, which re-triggers [AuthViewModel.requestReauthentication] via the
 * same `LaunchedEffect(Unit)` — see [UpdatePasswordScreen]'s "Request a new code" action.
 *
 * @param onCodeConfirmed Invoked with the user-entered code when "Continue" is tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityCodeScreen(
    onBack: () -> Unit,
    onCodeConfirmed: (code: String) -> Unit,
    authViewModel: AuthViewModel = koinViewModel(),
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val scope = rememberCoroutineScope()

    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    var cooldownRemaining by remember { mutableIntStateOf(0) }

    fun startCooldown() {
        scope.launch {
            for (remaining in RESEND_COOLDOWN_SECONDS downTo 0) {
                cooldownRemaining = remaining
                if (remaining > 0) delay(1_000)
            }
        }
    }

    // The ONE call site for the initial send — fires once per fresh instance of this screen
    // (including a fresh instance reached by popping back after an invalid/expired-code error).
    LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("screen_viewed: security_code")
        authViewModel.requestReauthentication()
        startCooldown()
    }

    val isSending = authUiState is AuthUiState.Loading
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
                        text = stringResource(R.string.account_mgmt_security_code_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(sp.lg),
        ) {
            Text(
                text = stringResource(R.string.account_mgmt_security_code_body),
                style = ty.bodyMedium,
                color = mc.textSecondary,
            )

            Spacer(modifier = Modifier.height(sp.xl))

            OutlinedTextField(
                value = code,
                onValueChange = { newValue ->
                    if (newValue.length <= 6 && newValue.all { it.isDigit() }) {
                        code = newValue
                    }
                },
                label = { Text(stringResource(R.string.account_mgmt_security_code_field_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(sp.sm))
                Text(text = errorMessage, color = mc.lifeNegative, style = ty.labelMedium)
            }

            Spacer(modifier = Modifier.height(sp.md))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                MagicCtaButton(
                    onClick = {
                        FirebaseCrashlytics.getInstance().log("security_code_resend_tapped")
                        authViewModel.requestReauthentication()
                        startCooldown()
                    },
                    text = if (cooldownRemaining > 0) {
                        stringResource(R.string.account_mgmt_resend_code_cooldown, cooldownRemaining)
                    } else {
                        stringResource(R.string.account_mgmt_resend_code)
                    },
                    style = MagicCtaStyle.Ghost,
                    color = MagicCtaColor.Primary,
                    enabled = cooldownRemaining == 0 && !isSending,
                )
            }

            Spacer(modifier = Modifier.height(sp.xl))

            MagicCtaButton(
                onClick = {
                    // Never log the actual 6-digit code — only that the tap happened.
                    FirebaseCrashlytics.getInstance().log("security_code_submit_tapped")
                    onCodeConfirmed(code)
                },
                text = stringResource(R.string.auth_btn_continue),
                style = MagicCtaStyle.Filled,
                color = MagicCtaColor.Primary,
                enabled = code.length == 6,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // NOTE: `code` is held only in this Composable's (and the AppNavGraph-hoisted handoff's)
    // in-memory state — never logged, never written to SavedStateHandle/Room/DataStore.
}

private const val RESEND_COOLDOWN_SECONDS = 60
