package com.mmg.manahub.feature.auth.presentation

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.recordNonFatal
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * "Forgot password" completion screen. `MainActivity.handleSupabaseAuthDeepLink` imports the
 * recovery session from a `type=recovery` Supabase auth callback, but does NOT navigate here
 * itself (hardened 2026-08-18) — routing is owned entirely by `AppNavGraph`'s reactive
 * `LaunchedEffect(recoverySessionState)`, which observes `AuthRepository.sessionState` directly
 * and navigates here the instant it sees `Authenticated(isRecoverySession = true)`, independent of
 * the Activity's intent/coroutine lifecycle (see that effect's KDoc and
 * `feedback_supabase_deeplink_onsessionsuccess_race`).
 *
 * Tapping the recovery email link already establishes a temporary, fully-authenticated recovery
 * session on-device (GoTrue mints a real access token for it via
 * `supabaseClient.handleDeeplinks(intent)`, already wired in `MainActivity`) — so this screen needs
 * NO reauthentication code, unlike [UpdatePasswordScreen]. It calls
 * [AuthViewModel.confirmPasswordReset] directly, which is a thin wrapper over
 * `Auth.updateUser(password = ...)` with no nonce.
 *
 * The form branch also renders [AuthFlowStepsCard] with a persistent (non-toast) reminder that
 * completing this flow signs the user out of every device — this was verified live behaviour
 * (`AuthRepositoryImpl.confirmPasswordReset` uses `SignOutScope.GLOBAL`) and must be visible before
 * the user submits, not discovered afterward.
 *
 * If the link is invalid/expired (no recovery session materializes — [SessionState] never becomes
 * [SessionState.Authenticated]), this renders a recoverable error state instead of a broken form.
 *
 * SECURITY (hardened 2026-08-18 — see `docs/plans/password-recovery-hardening-plan-2026-08-18.md`
 * §2.1/§3): an [SessionState.Authenticated] session alone is NOT proof this screen was reached via
 * a genuine recovery link — `MainActivity`'s `type=recovery` deep-link check is driven by an
 * attacker-controllable URI, so a forged intent can route an already-logged-in user here on a
 * normal session. Separately, [AuthUser.isRecoverySession] (the server-issued `amr` claim) is by
 * itself NOT sufficient either: verified live, GoTrue also tags a signup-email-confirmation session
 * `amr: otp` — the SAME value a real recovery-link consumption gets — so a brand-new user confirming
 * their email would otherwise also satisfy it. This screen's render gate therefore calls
 * [isActiveRecoveryFlow], which additionally requires an app-side "a recovery deep link produced
 * THIS session" marker (`UserPreferencesDataStore.pendingRecoveryMarkerFlow`, written ONLY by
 * `MainActivity` when a genuine recovery deep link is imported) bound to the current session's id
 * and still within its TTL, and renders the SAME error state as an invalid/expired link when that
 * predicate is false — the password form is never shown otherwise. [AuthViewModel.confirmPasswordReset]
 * enforces the identical check as the real security boundary; this is UI-level defense-in-depth so
 * the user is never shown a form that would silently fail on submit. The predicate is ALSO what
 * closes the "sticky `amr`" loop: since it stays satisfiable only while the marker remains armed,
 * and the marker is consumed on every exit (success/Back/sign-out), revisiting this screen/session
 * afterward correctly falls back to the invalid-link state instead of re-showing the form.
 *
 * SECURITY (T6 adversarial-audit fix, 2026-08-18): the form-rendering branch — the ONLY state in
 * which a live, exploitable recovery session actually exists — used to have no way to invoke
 * [onBack] at all: the top bar had no navigation icon, and there was no `BackHandler`. System
 * back / gesture back / predictive back therefore fell through to Navigation-Compose's default
 * `popBackStack()`, which never calls [onBack] and so never runs `abandonRecoverySession()` — a
 * byte-for-byte reproduction of the original "Back leaves an authenticated recovery session" bug
 * (S3) through the single most natural "I changed my mind" gesture, which was also the ONLY way to
 * leave this screen without submitting. Fixed with a [BackHandler] enabled only while [isFormState]
 * is true, PLUS a visible top-bar back icon wired to the same path, so there is an intentional
 * control rather than only an intercepted gesture. Both routes through a [MagicAlertDialog]
 * confirmation first — Back here is destructive (it signs the user out of the recovery session and
 * burns the link, per `AuthRepositoryImpl.abandonRecoverySession`'s `SignOutScope.LOCAL`), so an
 * accidental gesture silently costing the user the whole flow is a poor trade. The error-state
 * branches below are unaffected: they have no live recovery session to protect, so their existing
 * `FullErrorState(onRetry = onBack)` wiring calls [onBack] directly, unprompted, as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPasswordConfirmScreen(
    onBack: () -> Unit,
    onPasswordReset: () -> Unit,
    authViewModel: AuthViewModel = koinViewModel(),
    userPreferencesDataStore: UserPreferencesDataStore = koinInject(),
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val pendingRecoveryMarker by userPreferencesDataStore.pendingRecoveryMarkerFlow
        .collectAsStateWithLifecycle(initialValue = null)
    var newPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val passwordStrength = remember(newPassword) { PasswordStrength.from(newPassword) }
    val toastState = rememberMagicToastState()

    // Computed ONCE per composition and reused by the BackHandler/top-bar AND the render `when`
    // below (rather than each calling isActiveRecoveryFlow with its own System.currentTimeMillis())
    // so the two can never disagree at a TTL boundary.
    val isFormState = (sessionState as? SessionState.Authenticated)
        ?.let { isActiveRecoveryFlow(it, pendingRecoveryMarker, System.currentTimeMillis()) } == true

    var showAbandonConfirmDialog by remember { mutableStateOf(false) }

    // SECURITY: see the class KDoc's "T6 adversarial-audit fix" paragraph. Enabled ONLY in the
    // form state — the error/loading branches have no live recovery session to protect, so system
    // back there keeps its default (unintercepted) behavior, unchanged from before this fix.
    BackHandler(enabled = isFormState) {
        showAbandonConfirmDialog = true
    }

    LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("screen_viewed: reset_password_confirm")
    }

    LaunchedEffect(authUiState) {
        when (val state = authUiState) {
            is AuthUiState.PasswordResetConfirmed -> onPasswordReset()
            is AuthUiState.Error -> {
                toastState.show(state.message, MagicToastType.ERROR)
                authViewModel.resetUiState()
            }
            else -> Unit
        }
    }

    if (showAbandonConfirmDialog) {
        MagicAlertDialog(
            onDismissRequest = { showAbandonConfirmDialog = false },
            title = stringResource(R.string.account_mgmt_reset_abandon_confirm_title),
            text = stringResource(R.string.account_mgmt_reset_abandon_confirm_body),
            confirmLabel = stringResource(R.string.action_discard),
            confirmColor = MagicCtaColor.ErrorSolid,
            onConfirm = {
                showAbandonConfirmDialog = false
                onBack()
            },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { showAbandonConfirmDialog = false },
        )
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
                    IconButton(
                        onClick = {
                            // Route through the same confirmation as the BackHandler only while a
                            // live recovery session actually exists (isFormState) — the error/loading
                            // branches have nothing to protect, so this tap behaves exactly like
                            // their existing FullErrorState(onRetry = onBack) wiring, unprompted.
                            if (isFormState) showAbandonConfirmDialog = true else onBack()
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textPrimary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.account_mgmt_reset_password_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
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

                is SessionState.Authenticated -> if (!isFormState) {
                    // SECURITY: reached via a forged manahub://auth?type=recovery deep link fired at an
                    // already-authenticated (but non-recovery) session, a signup-confirmation session
                    // (amr-only would admit it — see the SECURITY KDoc above), or a session whose
                    // recovery marker was already consumed/expired — render the same error state as an
                    // invalid/expired link rather than a functional-looking password form. See the
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

                        Spacer(modifier = Modifier.height(sp.md))

                        // Persistent (not a toast) reminder: the user must know the global
                        // sign-out is coming BEFORE they submit, not be surprised by it after.
                        AuthFlowStepsCard(
                            title = stringResource(R.string.account_mgmt_reset_confirm_steps_title),
                            steps = listOf(
                                stringResource(R.string.account_mgmt_reset_confirm_step_choose),
                                stringResource(R.string.account_mgmt_reset_confirm_step_signout_all),
                            ),
                        )

                        Spacer(modifier = Modifier.height(sp.lg))

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

                        Spacer(modifier = Modifier.height(sp.xl))

                        MagicCtaButton(
                            onClick = { authViewModel.confirmPasswordReset(newPassword, pendingRecoveryMarker) },
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

            MagicToastHost(
                state = toastState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
    }
}
