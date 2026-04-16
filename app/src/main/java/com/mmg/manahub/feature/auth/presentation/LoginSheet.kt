package com.mmg.manahub.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.MarcellusFontFamily
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

/**
 * Full-screen-height ModalBottomSheet that handles sign-in, sign-up,
 * and password reset flows using [authViewModel].
 *
 * Automatically calls [onDismiss] when [AuthUiState.Success] is observed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginSheet(
    authViewModel: AuthViewModel,
    onDismiss: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Dismiss automatically on successful authentication
    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            authViewModel.resetUiState()
            onDismiss()
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
            onSignIn = { email, password -> authViewModel.signInWithEmail(email, password) },
            onSignUp = { email, password -> authViewModel.signUpWithEmail(email, password) },
            onGoogleSignIn = { context -> authViewModel.signInWithGoogle(context) },
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
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String) -> Unit,
    onGoogleSignIn: (android.content.Context) -> Unit,
    onResetPassword: (String) -> Unit,
    onResetUiState: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    val isLoading = uiState is AuthUiState.Loading
    val errorMessage = (uiState as? AuthUiState.Error)?.message

    // Reset error when user switches tabs or edits fields
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = mc.primaryAccent,
        unfocusedBorderColor = mc.textSecondary.copy(alpha = 0.4f),
        focusedLabelColor = mc.primaryAccent,
        unfocusedLabelColor = mc.textSecondary,
        cursorColor = mc.primaryAccent,
        focusedTextColor = mc.textPrimary,
        unfocusedTextColor = mc.textPrimary,
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
                style = MaterialTheme.magicTypography.titleLarge,
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

            Spacer(modifier = Modifier.height(12.dp))

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

            // ── Forgot password link (Sign In only) ────────────────────────────
            if (selectedTab == 0) {
                TextButton(
                    onClick = { showResetDialog = true },
                    modifier = Modifier.align(Alignment.End),
                    enabled = !isLoading,
                ) {
                    Text(
                        text = stringResource(R.string.auth_link_forgot),
                        color = mc.secondaryAccent,
                        fontSize = 13.sp,
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ── Error message ──────────────────────────────────────────────────
            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = mc.lifeNegative,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
            }

            // ── Primary CTA button ─────────────────────────────────────────────
            Button(
                onClick = {
                    if (selectedTab == 0) onSignIn(email, password)
                    else onSignUp(email, password)
                },
                enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
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
                        fontFamily = MarcellusFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
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
                    fontSize = 13.sp,
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = mc.textSecondary.copy(alpha = 0.3f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Google button ──────────────────────────────────────────────────
            OutlinedButton(
                onClick = { onGoogleSignIn(context) },
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
                // Fallback "G" badge — no external Google asset needed
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
                        fontSize = 14.sp,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.auth_btn_google),
                    fontSize = 15.sp,
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
                style = MaterialTheme.magicTypography.titleLarge,
                color = mc.textPrimary,
            )
        },
        text = {
            Column {
                if (resetSent) {
                    Text(
                        text = stringResource(R.string.auth_reset_sent),
                        color = mc.lifePositive,
                        fontSize = 14.sp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.auth_reset_subtitle),
                        color = mc.textSecondary,
                        fontSize = 14.sp,
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
                            fontSize = 13.sp,
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
                        Text(stringResource(R.string.auth_reset_btn))
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
                    )
                }
            }
        },
    )
}
