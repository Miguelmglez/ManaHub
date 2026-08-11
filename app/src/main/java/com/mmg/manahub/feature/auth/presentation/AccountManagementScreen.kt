package com.mmg.manahub.feature.auth.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.auth.AuthIdentity
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ShareProfileSheet
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.TimeAgoFormatter
import org.koin.androidx.compose.koinViewModel

/**
 * Account settings hub for an authenticated user — the destination behind the "Manage my account"
 * CTA on [AccountSection] (Phase 4a). Reachable ONLY while authenticated: [Screen.AccountManagement]
 * is never a nav target for an unauthenticated/loading session, but this screen still defends
 * against a mid-session sign-out (e.g. triggered from another surface) by calling [onSignedOut]
 * once [SessionState] flips away from [SessionState.Authenticated].
 *
 * Sections top to bottom: identity header (avatar/nickname/gameTag/join date/email), email
 * verification status, change-email/change-password rows, sign-in methods (with remove/link
 * actions), and the danger zone (sign out / delete account) — moved here from the old
 * `AccountSection.kt` per the account-management redesign plan.
 *
 * @param onBack Pops this screen off the back stack.
 * @param onNavigateToUpdateEmail Navigates DIRECTLY to [UpdateEmailScreen] — "Change email" skips
 *   the reauthentication-code gate because Supabase's "Secure email change" project setting
 *   already double-confirms the change via links sent to both the old and new inbox (see the KDoc
 *   on [com.mmg.manahub.core.domain.auth.AuthRepository.confirmEmailUpdate]).
 * @param onNavigateToSecurityCode Navigates to the reauthentication-code gate ahead of
 *   [UpdatePasswordScreen] — "Change password"/"Set a password" only, since it has no equivalent
 *   server-side double-confirm.
 * @param onSignedOut Invoked once the session is confirmed no longer authenticated (sign-out
 *   success or account deletion) — the caller should pop back to a non-account-gated destination.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountManagementScreen(
    onBack: () -> Unit,
    onNavigateToUpdateEmail: () -> Unit,
    onNavigateToSecurityCode: () -> Unit,
    onSignedOut: () -> Unit,
    authViewModel: AuthViewModel = koinViewModel(),
    viewModel: AccountManagementViewModel = koinViewModel(),
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val context = LocalContext.current

    val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()
    val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
    val resendCooldown by viewModel.resendCooldownRemaining.collectAsStateWithLifecycle()

    val toastState = rememberMagicToastState()
    var showShareSheet by remember { mutableStateOf(false) }
    var identityPendingUnlink by remember { mutableStateOf<AuthIdentity?>(null) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val copiedMessage = stringResource(R.string.account_mgmt_gametag_copied)
    val emailSentMessage = stringResource(R.string.auth_email_confirmation_sent)

    // Central reaction to every one-shot AuthUiState this screen's actions can produce. Each branch
    // resets back to Idle so the shared uiState never leaks a stale toast/dialog into a later action.
    LaunchedEffect(authUiState) {
        when (val state = authUiState) {
            is AuthUiState.EmailConfirmationSent -> {
                toastState.show(emailSentMessage, MagicToastType.SUCCESS)
                authViewModel.resetUiState()
            }
            is AuthUiState.IdentityUnlinked -> {
                identityPendingUnlink = null
                authViewModel.resetUiState()
            }
            is AuthUiState.GoogleIdentityLinkStarted -> {
                state.authorizationUrl?.let { url -> launchCustomTab(context, url) }
                authViewModel.resetUiState()
            }
            is AuthUiState.AccountDeleted -> {
                authViewModel.resetUiState()
            }
            is AuthUiState.Error -> {
                toastState.show(state.message, MagicToastType.ERROR)
                authViewModel.resetUiState()
            }
            else -> Unit
        }
    }

    // Defense-in-depth: leave the screen the moment the session is confirmed gone, regardless of
    // WHICH action caused it (signOut()'s uiState transition is Idle, not a dedicated state, so this
    // is the only reliable signal for that specific action; deleteAccount() reaches the same place).
    // Also leaves when the session flips to an anonymous/guest Authenticated session — this screen
    // is reachable only from an authenticated, non-anonymous state, but a mid-session downgrade
    // (or a nav race with the AccountSection/Settings gates) must not leave a guest stranded here:
    // this screen's Google identity-link action IS Supabase's anonymous-to-permanent conversion
    // primitive and bypasses the server-side profile-creation trigger when done in place.
    LaunchedEffect(sessionState) {
        val state = sessionState
        val isAnonymousSession = state is SessionState.Authenticated && state.user.isAnonymous
        if (state is SessionState.Unauthenticated || isAnonymousSession) {
            onSignedOut()
        }
    }

    if (showShareSheet) {
        val user = (sessionState as? SessionState.Authenticated)?.user
        ShareProfileSheet(
            gameTag = user?.gameTag,
            onFetchShareLink = viewModel::fetchShareLink,
            onDismiss = { showShareSheet = false },
        )
    }

    identityPendingUnlink?.let { identity ->
        val providerLabel = identity.provider.replaceFirstChar { it.uppercase() }
        MagicAlertDialog(
            onDismissRequest = { identityPendingUnlink = null },
            title = stringResource(R.string.account_mgmt_unlink_confirm_title),
            text = stringResource(R.string.account_mgmt_unlink_confirm_body, providerLabel),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                // Dismiss synchronously on tap, matching the sign-out/delete-account dialogs below
                // — otherwise a failed unlink leaves this dialog open with the resulting error toast
                // rendered underneath it (the toast is anchored in the screen's own Box; this dialog
                // is a real Android Dialog above everything).
                val identityId = identity.identityId
                identityPendingUnlink = null
                authViewModel.unlinkIdentity(identityId)
            },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { identityPendingUnlink = null },
            confirmColor = MagicCtaColor.Error,
        )
    }

    if (showSignOutDialog) {
        MagicAlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = stringResource(R.string.auth_sign_out),
            text = stringResource(R.string.account_mgmt_sign_out_confirm_body),
            confirmLabel = stringResource(R.string.auth_sign_out),
            onConfirm = {
                showSignOutDialog = false
                authViewModel.signOut()
            },
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { showSignOutDialog = false },
        )
    }

    if (showDeleteDialog) {
        MagicAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = stringResource(R.string.auth_delete_account_confirm_title),
            text = stringResource(R.string.auth_delete_account_confirm_body),
            confirmLabel = stringResource(R.string.auth_delete_account_confirm_btn),
            onConfirm = {
                showDeleteDialog = false
                authViewModel.deleteAccount()
            },
            dismissLabel = stringResource(R.string.auth_cancel),
            onDismiss = { showDeleteDialog = false },
            confirmColor = MagicCtaColor.Error,
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
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textPrimary,
                        )
                    }
                    Text(
                        text = stringResource(R.string.account_mgmt_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = sessionState) {
                is SessionState.Authenticated -> {
                    val user = state.user
                    // Captured into local vals: AuthUser is declared in a different Gradle module
                    // (shared:core-domain), so the Kotlin compiler cannot smart-cast its nullable
                    // properties across the module boundary even after a null check.
                    val userEmail = user.email
                    val pendingNewEmail = user.newEmail
                    val hasEmailIdentity = user.identities.any { it.provider == "email" }
                    val hasGoogleIdentity = user.identities.any { it.provider == "google" }
                    val isEmailVerified = user.emailConfirmedAt != null

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(horizontal = sp.lg, vertical = sp.lg),
                        verticalArrangement = Arrangement.spacedBy(sp.lg),
                    ) {
                        item {
                            IdentityHeader(
                                user = user,
                                onCopyGameTag = { tag ->
                                    copyToClipboard(context, tag)
                                    toastState.show(copiedMessage, MagicToastType.SUCCESS)
                                },
                                onShareClick = { showShareSheet = true },
                            )
                        }

                        if (!isEmailVerified && userEmail != null) {
                            item {
                                EmailVerificationCard(
                                    cooldownRemaining = resendCooldown,
                                    onResend = {
                                        viewModel.startResendCooldown()
                                        authViewModel.resendConfirmationEmail(userEmail)
                                    },
                                )
                            }
                        }

                        item {
                            AccountManagementRow(
                                title = stringResource(R.string.account_mgmt_change_email),
                                subtitle = userEmail,
                                icon = Icons.Default.Email,
                                onClick = onNavigateToUpdateEmail,
                                pendingNote = pendingNewEmail?.let { pending ->
                                    stringResource(R.string.account_mgmt_email_change_pending, pending)
                                },
                            )
                        }

                        item {
                            AccountManagementRow(
                                title = if (hasEmailIdentity) {
                                    stringResource(R.string.account_mgmt_change_password)
                                } else {
                                    stringResource(R.string.account_mgmt_set_password)
                                },
                                subtitle = if (hasEmailIdentity) {
                                    null
                                } else {
                                    stringResource(R.string.account_mgmt_set_password_subtitle)
                                },
                                icon = Icons.Default.Lock,
                                onClick = onNavigateToSecurityCode,
                            )
                        }

                        item {
                            SectionLabel(stringResource(R.string.account_mgmt_signin_methods_title))
                        }

                        items(user.identities, key = { it.identityId }) { identity ->
                            SignInMethodRow(
                                identity = identity,
                                removeEnabled = user.identities.size > 1,
                                onRemoveClick = { identityPendingUnlink = identity },
                            )
                        }

                        if (!hasGoogleIdentity) {
                            item {
                                Spacer(modifier = Modifier.height(sp.xxs))
                                MagicCtaButton(
                                    onClick = {
                                        authViewModel.linkGoogleIdentityNative(MANAHUB_AUTH_REDIRECT_URL)
                                    },
                                    text = stringResource(R.string.account_mgmt_link_google),
                                    style = MagicCtaStyle.Outlined,
                                    color = MagicCtaColor.Primary,
                                    icon = {
                                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }

                        item {
                            SectionLabel(stringResource(R.string.account_mgmt_danger_zone_title))
                        }

                        item {
                            MagicCtaButton(
                                onClick = { showSignOutDialog = true },
                                text = stringResource(R.string.auth_sign_out),
                                style = MagicCtaStyle.Outlined,
                                color = MagicCtaColor.Primary,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        item {
                            MagicCtaButton(
                                onClick = { showDeleteDialog = true },
                                text = stringResource(R.string.auth_delete_account),
                                style = MagicCtaStyle.Ghost,
                                color = MagicCtaColor.Error,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                SessionState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = mc.primaryAccent)
                    }
                }

                SessionState.Unauthenticated -> {
                    // Handled by the LaunchedEffect(sessionState) above (onSignedOut); render nothing
                    // for the single frame before navigation completes.
                    Box(modifier = Modifier.fillMaxSize().padding(padding))
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

// ── Identity header ───────────────────────────────────────────────────────────

@Composable
private fun IdentityHeader(
    user: AuthUser,
    onCopyGameTag: (String) -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    // Captured into local vals: AuthUser is declared in a different Gradle module
    // (shared:core-domain), so the compiler cannot smart-cast its nullable properties across the
    // module boundary even after a null check.
    val email = user.email
    val gameTag = user.gameTag
    val nickname = user.nickname ?: email?.substringBefore('@') ?: "Player"
    val joinedLabel = user.createdAt?.let { TimeAgoFormatter.format(it.toEpochMilliseconds()) }

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = mc.surface,
        shape = CardShape,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(sp.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(mc.primaryAccent.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (user.avatarUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(user.avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ThemeBackground(modifier = Modifier.fillMaxSize())
                        Text(
                            text = nickname.take(1).uppercase().ifEmpty { "✦" },
                            style = ty.titleLarge.copy(color = mc.primaryAccent.copy(alpha = 0.7f)),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(sp.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nickname,
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (email != null) {
                        Spacer(modifier = Modifier.height(sp.xxs))
                        Text(
                            text = email,
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (joinedLabel != null) {
                        Spacer(modifier = Modifier.height(sp.xxs))
                        Text(
                            text = stringResource(R.string.account_mgmt_joined, joinedLabel),
                            style = ty.labelSmall,
                            color = mc.textDisabled,
                        )
                    }
                }
            }

            if (gameTag != null) {
                Spacer(modifier = Modifier.height(sp.md))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ChipShape)
                        .background(mc.primaryAccent.copy(alpha = 0.1f))
                        .clickable { onCopyGameTag(gameTag) }
                        .padding(horizontal = sp.md, vertical = sp.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = gameTag,
                        style = ty.labelLarge,
                        color = mc.primaryAccent,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onShareClick, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.action_share),
                            tint = mc.primaryAccent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Email verification ──────────────────────────────────────────────────────

@Composable
private fun EmailVerificationCard(
    cooldownRemaining: Int,
    onResend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = mc.lifeNegative.copy(alpha = 0.08f),
        shape = CardShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(sp.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = mc.lifeNegative.copy(alpha = 0.15f),
                        shape = ChipShape,
                    ) {
                        Text(
                            text = stringResource(R.string.account_mgmt_email_not_verified),
                            style = ty.labelSmall,
                            color = mc.lifeNegative,
                            modifier = Modifier.padding(horizontal = sp.sm, vertical = sp.xxs),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(sp.xs))
                Text(
                    text = stringResource(R.string.account_mgmt_email_not_verified_body),
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                )
            }
            Spacer(modifier = Modifier.width(sp.md))
            MagicCtaButton(
                onClick = onResend,
                text = if (cooldownRemaining > 0) "${cooldownRemaining}s" else stringResource(R.string.account_mgmt_resend_email),
                style = MagicCtaStyle.Outlined,
                color = MagicCtaColor.Error,
                enabled = cooldownRemaining == 0,
            )
        }
    }
}

// ── Sign-in methods ───────────────────────────────────────────────────────────

@Composable
private fun SignInMethodRow(
    identity: AuthIdentity,
    removeEnabled: Boolean,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val isGoogle = identity.provider == "google"
    val providerLabel = if (isGoogle) {
        stringResource(R.string.account_mgmt_provider_google)
    } else {
        stringResource(R.string.account_mgmt_provider_email)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = mc.surface,
        shape = CardShape,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(sp.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(mc.primaryAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                if (isGoogle) {
                    Text("G", style = ty.labelLarge, color = mc.primaryAccent)
                } else {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = mc.primaryAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(sp.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = providerLabel, style = ty.bodyMedium, color = mc.textPrimary)
                if (!removeEnabled) {
                    Text(
                        text = stringResource(R.string.account_mgmt_only_signin_method),
                        style = ty.labelSmall,
                        color = mc.textDisabled,
                    )
                }
            }
            MagicCtaButton(
                onClick = onRemoveClick,
                text = stringResource(R.string.action_remove),
                style = MagicCtaStyle.Ghost,
                color = MagicCtaColor.Error,
                enabled = removeEnabled,
            )
        }
    }
}

// ── Generic clickable row (Change email / Change password) ────────────────────

@Composable
private fun AccountManagementRow(
    title: String,
    subtitle: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pendingNote: String? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = mc.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(sp.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = mc.primaryAccent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(sp.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = ty.bodyMedium, color = mc.textPrimary)
                if (subtitle != null) {
                    Text(text = subtitle, style = ty.labelSmall, color = mc.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Pending "change email" confirmation note — set only when AuthUser.newEmail is
                // non-null (an in-progress, not-yet-confirmed change via Supabase's "Secure email
                // change", which double-confirms via links to both the old and new inbox).
                if (pendingNote != null) {
                    Text(
                        text = pendingNote,
                        style = ty.labelSmall,
                        color = mc.primaryAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = mc.textDisabled,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.textSecondary,
        modifier = modifier.padding(top = MaterialTheme.spacing.xs, bottom = MaterialTheme.spacing.xxs),
    )
}

// ── Helpers ────────────────────────────────────────────────────────────────────

/** The `manahub://auth` OAuth-redirect deep link — see `AuthRepository.linkGoogleIdentityNative`. */
private const val MANAHUB_AUTH_REDIRECT_URL = "manahub://auth"

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Game Tag", text.removePrefix("#")))
}

/** Opens [url] in a Custom Tab, falling back to a plain browser Intent if none is available. */
private fun launchCustomTab(context: Context, url: String) {
    val uri = Uri.parse(url)
    if (uri.scheme != "https" && uri.scheme != "http") return
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: Exception) { /* no handler available */ }
    }
}
