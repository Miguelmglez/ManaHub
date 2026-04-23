package com.mmg.manahub.feature.auth.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * Full-screen-height ModalBottomSheet that handles sign-in, sign-up,
 * and password reset flows using [authViewModel].
 *
 * [initialTab] selects the tab shown when the sheet opens:
 *   0 = Sign In (default), 1 = Create Account.
 *
 * Automatically calls [onDismiss] when [AuthUiState.Success] is observed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginSheet(
    authViewModel: AuthViewModel,
    initialTab: Int = 0,
    initialNickname: String = "",
    initialAvatarUrl: String? = null,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Dismiss on successful authentication (sign-in/sign-up flow).
    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onDismiss()
            authViewModel.resetUiState()
        }
    }

    // Dismiss when the session becomes Authenticated from any source, including
    // the email-confirmation deep link which bypasses the normal uiState flow.
    LaunchedEffect(sessionState) {
        if (sessionState is SessionState.Authenticated) {
            onDismiss()
            authViewModel.resetUiState()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            authViewModel.resetUiState()
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = mc.backgroundSecondary,
        dragHandle = { Spacer(modifier = Modifier.height(8.dp)) },
    ) {
        LoginSheetContent(
            uiState = uiState,
            initialTab = initialTab,
            initialNickname = initialNickname,
            initialAvatarUrl = initialAvatarUrl,
            onSignIn = { email, password -> authViewModel.signInWithEmail(email, password) },
            onSignUp = { email, password, nickname, avatarUrl ->
                authViewModel.signUpWithEmail(email, password, nickname, avatarUrl)
            },
            onGoogleSignIn = { context -> authViewModel.signInWithGoogle(context) },
            onGoogleSignUp = { context, nick, avatarUrl -> 
                authViewModel.signUpWithGoogle(context, nick, avatarUrl) 
            },
            onResetPassword = { email -> authViewModel.resetPassword(email) },
            onResetUiState = { authViewModel.resetUiState() },
        )
    }
}

/**
 * Stateless inner content of [LoginSheet].
 */
@Composable
private fun LoginSheetContent(
    uiState: AuthUiState,
    initialTab: Int = 0,
    initialNickname: String = "",
    initialAvatarUrl: String? = null,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String, String?) -> Unit,
    onGoogleSignIn: (android.content.Context) -> Unit,
    onGoogleSignUp: (android.content.Context, String, String?) -> Unit,
    onResetPassword: (String) -> Unit,
    onResetUiState: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var email by remember { mutableStateOf("") }
    var nickname by remember(initialNickname) { mutableStateOf(initialNickname) }
    var nicknameError by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    val passwordStrength = remember(password) { PasswordStrength.from(password) }

    // Show email confirmation screen when Supabase requires email verification
    if (uiState is AuthUiState.EmailConfirmationSent) {
        EmailConfirmationContent(
            email = email,
            onOpenEmailApp = {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_EMAIL)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    // No email app available — user can open mail manually
                }
            },
            onBackToSignIn = {
                selectedTab = 0
                onResetUiState()
            },
        )
        return
    }

    val isLoading = uiState is AuthUiState.Loading
    val errorMessage = (uiState as? AuthUiState.Error)?.message

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = mc.primaryAccent,
        unfocusedBorderColor = mc.textSecondary.copy(alpha = 0.4f),
        focusedLabelColor = mc.primaryAccent,
        unfocusedLabelColor = mc.textSecondary,
        cursorColor = mc.primaryAccent,
        focusedTextColor = mc.textPrimary,
        unfocusedTextColor = mc.textPrimary,
        errorBorderColor = mc.lifeNegative,
        errorLabelColor = mc.lifeNegative,
        errorCursorColor = mc.lifeNegative,
        errorSupportingTextColor = mc.lifeNegative,
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            // ── Title ──────────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.auth_sheet_title),
                style = ty.titleLarge,
                color = mc.textPrimary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Tabs ───────────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = mc.primaryAccent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = mc.primaryAccent,
                    )
                },
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        nicknameError = false
                        onResetUiState()
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.auth_tab_signin),
                            color = if (selectedTab == 0) mc.primaryAccent else mc.textSecondary,
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        nicknameError = false
                        onResetUiState()
                    },
                    text = {
                        Text(
                            text = stringResource(R.string.auth_tab_signup),
                            color = if (selectedTab == 1) mc.primaryAccent else mc.textSecondary,
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(20.dp))


            // ── Nickname field (sign-up only) ──────────────────────────────────
            if (selectedTab == 1) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { newValue ->
                        if (newValue.length <= 30) {
                            nickname = newValue
                            nicknameError = false
                            onResetUiState()
                        }
                    },
                    label = { Text(stringResource(R.string.auth_field_nickname)) },
                    placeholder = { Text(stringResource(R.string.auth_nickname_hint), color = mc.textSecondary) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                    isError = nicknameError,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            if (nicknameError) {
                                Text(
                                    text = stringResource(R.string.auth_error_nickname_required),
                                    color = mc.lifeNegative,
                                    style = ty.labelSmall,
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                            Text(
                                text = stringResource(R.string.auth_nickname_char_count, nickname.length),
                                color = if (nickname.length == 30) mc.lifeNegative else mc.textSecondary,
                                style = ty.labelSmall,
                            )
                        }
                    },
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ── Email field ────────────────────────────────────────────────────

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    onResetUiState()
                },
                label = { Text(stringResource(R.string.auth_field_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            )


            // ── Password field ─────────────────────────────────────────────────
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    onResetUiState()
                },
                label = { Text(stringResource(R.string.auth_field_password)) },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility
                            else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = mc.textSecondary,
                        )
                    }
                },
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
            )

            // ── Forgot password (Sign In) / Password requirements (Sign Up) ────
            if (selectedTab == 0) {
                TextButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.align(Alignment.End),
                    enabled = !isLoading,
                ) {
                    Text(
                        text = stringResource(R.string.auth_link_forgot),
                        color = mc.secondaryAccent,
                        style = ty.labelMedium,
                    )
                }
            } else {
                PasswordStrengthIndicator(strength = passwordStrength)
                Spacer(modifier = Modifier.height(4.dp))
            }

            // ── Error message ──────────────────────────────────────────────────
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = mc.lifeNegative,
                    style = ty.labelMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            // ── Primary CTA button ─────────────────────────────────────────────
            Button(
                onClick = {
                    if (selectedTab == 0) {
                        onSignIn(email, password)
                    } else {
                        if (nickname.isBlank()) {
                            nicknameError = true
                        } else {
                            onSignUp(email, password, nickname, initialAvatarUrl)
                        }
                    }
                },
                enabled = !isLoading && email.isNotBlank() &&
                    if (selectedTab == 1) passwordStrength.allMet && nickname.isNotBlank()
                    else password.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = mc.primaryAccent,
                    contentColor = mc.background,
                    disabledContainerColor = mc.primaryAccent.copy(alpha = 0.4f),
                    disabledContentColor = mc.background.copy(alpha = 0.6f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = mc.background,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.auth_btn_continue),
                        style = ty.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Divider "or" ───────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = mc.textSecondary.copy(alpha = 0.3f),
                )
                Text(
                    text = stringResource(R.string.auth_or_divider),
                    color = mc.textSecondary,
                    modifier = Modifier.padding(horizontal = 12.dp),
                    style = ty.labelMedium,
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = mc.textSecondary.copy(alpha = 0.3f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Google button ──────────────────────────────────────────────────
            OutlinedButton(
                onClick = {
                    if (selectedTab == 1) {
                        if (nickname.isBlank()) {
                            nicknameError = true
                        } else {
                            onGoogleSignUp(context, nickname, initialAvatarUrl)
                        }
                    } else {
                        onGoogleSignIn(context)
                    }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = mc.textSecondary.copy(alpha = 0.4f),
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = mc.textPrimary,
                    disabledContentColor = mc.textSecondary,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = Color(0xFFFFFFFF),
                            shape = RoundedCornerShape(4.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                Text(
                    text = "G",
                    color = Color(0xFF4285F4),
                    fontWeight = FontWeight.Bold,
                    style = ty.bodyMedium,
                )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.auth_btn_google),
                    style = ty.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Reset password dialog ──────────────────────────────────────────────────
    if (showResetDialog) {
        ResetPasswordDialog(
            uiState = uiState,
            onSend = { resetEmail ->
                onResetPassword(resetEmail)
            },
            onDismiss = {
                showResetDialog = false
                onResetUiState()
            },
        )
    }
}

// ── Email confirmation screen ──────────────────────────────────────────────────

@Composable
private fun EmailConfirmationContent(
    email: String,
    onOpenEmailApp: () -> Unit,
    onBackToSignIn: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Email,
            contentDescription = null,
            tint = mc.primaryAccent,
            modifier = Modifier.size(64.dp),
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.auth_email_confirm_title),
            style = ty.titleLarge,
            color = mc.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.auth_email_confirm_body, email),
            color = mc.textSecondary,
            style = ty.bodyMedium,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Open email app button
        Button(
            onClick = onOpenEmailApp,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = mc.primaryAccent,
                contentColor = mc.background,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.auth_email_confirm_open_app),
                fontWeight = FontWeight.SemiBold,
                style = ty.bodyMedium,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onBackToSignIn) {
            Text(
                text = stringResource(R.string.auth_email_confirm_back),
                color = mc.textSecondary,
                style = ty.bodyMedium,
            )
        }
    }
}

// ── Password strength model ────────────────────────────────────────────────────

/**
 * Immutable snapshot of which password requirements are currently satisfied.
 * Mirrors the server-side rules enforced by [AuthViewModel.isPasswordStrong].
 */
private data class PasswordStrength(
    val hasMinLength: Boolean,
    val hasLowercase: Boolean,
    val hasUppercase: Boolean,
    val hasDigit: Boolean,
    val hasSymbol: Boolean,
) {
    val allMet: Boolean
        get() = hasMinLength && hasLowercase && hasUppercase && hasDigit && hasSymbol

    companion object {
        fun from(password: String) = PasswordStrength(
            hasMinLength = password.length >= 8,
            hasLowercase = password.any { it.isLowerCase() },
            hasUppercase = password.any { it.isUpperCase() },
            hasDigit     = password.any { it.isDigit() },
            hasSymbol    = password.any { !it.isLetterOrDigit() },
        )
    }
}

// ── Password strength UI ───────────────────────────────────────────────────────

@Composable
private fun PasswordStrengthIndicator(strength: PasswordStrength) {
    val ty = MaterialTheme.magicTypography
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        RequirementRow(stringResource(R.string.auth_password_requirement_length),   strength.hasMinLength, ty)
        RequirementRow(stringResource(R.string.auth_password_requirement_uppercase), strength.hasUppercase, ty)
        RequirementRow(stringResource(R.string.auth_password_requirement_lowercase), strength.hasLowercase, ty)
        RequirementRow(stringResource(R.string.auth_password_requirement_digit),     strength.hasDigit, ty)
        RequirementRow(stringResource(R.string.auth_password_requirement_symbol),    strength.hasSymbol, ty)
    }
}

@Composable
private fun RequirementRow(label: String, met: Boolean, ty: com.mmg.manahub.core.ui.theme.MagicTypography) {
    val mc = MaterialTheme.magicColors
    val color = if (met) mc.lifePositive else mc.textDisabled
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = if (met) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = color,
            style = ty.labelSmall,
        )
    }
}

/**
 * Dialog that collects an email address and triggers a password-reset request.
 * Shows a success message when [uiState] transitions to [AuthUiState.ResetSent].
 */
@Composable
private fun ResetPasswordDialog(
    uiState: AuthUiState,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    var resetEmail by remember { mutableStateOf("") }
    val isLoading = uiState is AuthUiState.Loading
    val resetSent = uiState is AuthUiState.ResetSent
    val errorMessage = (uiState as? AuthUiState.Error)?.message

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = mc.surface,
        titleContentColor = mc.textPrimary,
        textContentColor = mc.textSecondary,
        title = {
            Text(
                text = stringResource(R.string.auth_reset_title),
                style = ty.titleLarge,
                color = mc.textPrimary,
            )
        },
        text = {
            Column {
                if (resetSent) {
                    Text(
                        text = stringResource(R.string.auth_reset_sent),
                        color = mc.lifePositive,
                        style = ty.bodyMedium,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.auth_reset_subtitle),
                        color = mc.textSecondary,
                        style = ty.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text(stringResource(R.string.auth_field_email)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = mc.primaryAccent,
                            unfocusedBorderColor = mc.textSecondary.copy(alpha = 0.4f),
                            focusedLabelColor = mc.primaryAccent,
                            unfocusedLabelColor = mc.textSecondary,
                            cursorColor = mc.primaryAccent,
                            focusedTextColor = mc.textPrimary,
                            unfocusedTextColor = mc.textPrimary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                    )
                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage,
                            color = mc.lifeNegative,
                            style = ty.labelMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (resetSent) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.action_close),
                        color = mc.primaryAccent,
                        style = ty.labelMedium,
                    )
                }
            } else {
                Button(
                    onClick = { onSend(resetEmail) },
                    enabled = !isLoading && resetEmail.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mc.primaryAccent,
                        contentColor = mc.background,
                    ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = mc.background,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.auth_reset_btn),
                            style = ty.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        dismissButton = {
            if (!resetSent) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = mc.textSecondary,
                        style = ty.labelMedium,
                    )
                }
            }
        },
    )
}
