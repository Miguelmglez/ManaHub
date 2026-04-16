package com.mmg.manahub.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState

/**
 * Profile-screen section that renders different content based on [sessionState]:
 *
 * - [SessionState.Loading]         → renders nothing (avoids flicker)
 * - [SessionState.Unauthenticated] → feature-promotion card with login / sign-up CTAs
 * - [SessionState.Authenticated]   → compact user identity card with sign-out and delete-account options
 */
@Composable
fun AccountSection(
    sessionState: SessionState,
    authUiState: AuthUiState,
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (sessionState) {
        SessionState.Loading -> Unit // Render nothing while session is resolving

        SessionState.Unauthenticated -> {
            UnauthenticatedCard(
                onLoginClick = onLoginClick,
                onSignUpClick = onSignUpClick,
                modifier = modifier,
            )
        }

        is SessionState.Authenticated -> {
            AuthenticatedCard(
                user = sessionState.user,
                onSignOutClick = onSignOutClick,
                onDeleteAccountClick = onDeleteAccountClick,
                modifier = modifier,
            )
        }
    }
}

// ── Unauthenticated ────────────────────────────────────────────────────────────

@Composable
private fun UnauthenticatedCard(
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = mc.primaryAccent.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp),
            ),
        color = mc.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
        ) {

            // ── Header ─────────────────────────────────────────────────────────
            Text(
                text = stringResource(R.string.auth_section_title),
                style = MaterialTheme.magicTypography.titleLarge,
                color = mc.textPrimary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.auth_section_subtitle),
                color = mc.textSecondary,
                fontSize = 13.sp,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Benefit list ───────────────────────────────────────────────────
            BenefitRow(
                icon = Icons.Default.Sync,
                text = stringResource(R.string.auth_benefit_collection),
            )
            BenefitRow(
                icon = Icons.Default.Cloud,
                text = stringResource(R.string.auth_benefit_decks),
            )
            BenefitRow(
                icon = Icons.Default.BarChart,
                text = stringResource(R.string.auth_benefit_stats),
            )
            BenefitRow(
                icon = Icons.Default.AccountCircle,
                text = stringResource(R.string.auth_benefit_profile),
            )
            BenefitRow(
                icon = Icons.Default.Star,
                text = stringResource(R.string.auth_benefit_future),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ── CTA: Create Account (gradient) ─────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(mc.primaryAccent, mc.secondaryAccent)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(
                    onClick = onSignUpClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.auth_cta_create),
                        color = mc.background,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ── CTA: Sign In (outlined) ────────────────────────────────────────
            OutlinedButton(
                onClick = onLoginClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = mc.primaryAccent,
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = mc.primaryAccent,
                ),
            ) {
                Text(
                    text = stringResource(R.string.auth_cta_signin),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── No-account disclaimer ──────────────────────────────────────────
            Text(
                text = stringResource(R.string.auth_no_account_needed),
                color = mc.textDisabled,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * Single benefit row with an accent icon and a description text.
 */
@Composable
private fun BenefitRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = mc.primaryAccent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = mc.textSecondary,
            fontSize = 13.sp,
        )
    }
}

// ── Authenticated ──────────────────────────────────────────────────────────────

@Composable
private fun AuthenticatedCard(
    user: AuthUser,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val displayName = user.displayName
        ?: user.email?.substringBefore('@')
        ?: "User"

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.auth_delete_account_confirm_title),
                    color = mc.textPrimary,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.auth_delete_account_confirm_body),
                    color = mc.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteAccountClick()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.auth_delete_account_confirm_btn),
                        color = Color.Red,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        text = stringResource(R.string.auth_cancel),
                        color = mc.textSecondary,
                    )
                }
            },
            containerColor = mc.surface,
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = mc.primaryAccent.copy(alpha = 0.3f),
                shape = RoundedCornerShape(16.dp),
            ),
        color = mc.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // ── Identity row ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                // ── Avatar ─────────────────────────────────────────────────────
                if (user.avatarUrl != null) {
                    AsyncImage(
                        model = user.avatarUrl,
                        contentDescription = "Avatar of $displayName",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .border(1.dp, mc.primaryAccent.copy(alpha = 0.5f), CircleShape),
                    )
                } else {
                    // Fallback: initials circle
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(mc.primaryAccent.copy(alpha = 0.2f))
                            .border(1.dp, mc.primaryAccent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = displayName.take(1).uppercase(),
                            color = mc.primaryAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // ── Name + email ───────────────────────────────────────────────
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        color = mc.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                    )
                    if (user.email != null) {
                        Text(
                            text = user.email,
                            color = mc.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // ── Synced chip ────────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .background(
                                color = mc.lifePositive.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.auth_synced_chip),
                            color = mc.lifePositive,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // ── Sign out button ────────────────────────────────────────────
                TextButton(onClick = onSignOutClick) {
                    Text(
                        text = stringResource(R.string.auth_sign_out),
                        color = mc.textSecondary,
                        fontSize = 13.sp,
                    )
                }
            }

            // ── Delete account row ─────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { showDeleteDialog = true }) {
                    Text(
                        text = stringResource(R.string.auth_delete_account),
                        color = Color.Red.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
