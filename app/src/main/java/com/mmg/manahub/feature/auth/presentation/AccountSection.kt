package com.mmg.manahub.feature.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
    playerName: String? = null,
    avatarUrl: String? = null,
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
                displayName = playerName,
                displayAvatarUrl = avatarUrl,
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
    val ty = MaterialTheme.magicTypography
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
                style = ty.titleLarge,
                color = mc.textPrimary,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.auth_section_subtitle),
                style = ty.labelSmall,
                color = mc.textSecondary,
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
                        style = ty.titleMedium,
                        color = mc.background,
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
                    style = ty.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── No-account disclaimer ──────────────────────────────────────────
            Text(
                text = stringResource(R.string.auth_no_account_needed),
                style = ty.labelMedium,
                color = mc.secondaryAccent,
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
    val ty = MaterialTheme.magicTypography
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
            style = ty.labelSmall,
            color = mc.textSecondary,
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
    displayName: String? = null,
    displayAvatarUrl: String? = null,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val nickname = displayName
        ?: user.nickname
        ?: user.email?.substringBefore('@')
        ?: "Player"
    val avatarUrl = displayAvatarUrl ?: user.avatarUrl
    val gameTag = user.gameTag

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.auth_delete_account_confirm_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.auth_delete_account_confirm_body),
                    style = ty.bodySmall,
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
                        style = ty.labelMedium,
                        color = Color.Red,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        text = stringResource(R.string.auth_cancel),
                        style = ty.labelMedium,
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
                color = mc.primaryAccent.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp),
            ),
        color = mc.surface,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // ── Identity Row ───────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ── Avatar ─────────────────────────────────────────────────────
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(mc.primaryAccent.copy(alpha = 0.1f))
                        .border(1.dp, mc.primaryAccent.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = "Avatar of $nickname",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // Fallback: initials circle
                        Text(
                            text = nickname.take(1).uppercase(),
                            style = ty.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = mc.primaryAccent,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // ── User Details ───────────────────────────────────────────────
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nickname,
                        style = ty.titleMedium,
                        color = mc.textPrimary,
                        maxLines = 1,
                    )
                    
                    if (user.email != null) {
                        Text(
                            text = user.email,
                            style = ty.labelSmall,
                            color = mc.textSecondary,
                            maxLines = 1,
                        )
                    }

                    if (gameTag != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        // ── Game Tag Badge ─────────────────────────────────────
                        Box(
                            modifier = Modifier
                                .background(
                                    color = mc.primaryAccent.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = gameTag,
                                style = ty.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = mc.primaryAccent,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Action Buttons ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sign Out (Primary Outlined)
                OutlinedButton(
                    onClick = onSignOutClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.5.dp,
                        color = mc.primaryAccent,
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = mc.primaryAccent,
                    ),
                ) {
                    Text(
                        text = stringResource(R.string.auth_sign_out),
                        style = ty.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }

                // Delete Account (Ghost/Red)
                TextButton(
                    onClick = { showDeleteDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color.Red.copy(alpha = 0.7f)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.auth_delete_account),
                        style = ty.labelSmall,
                    )
                }
            }
        }
    }
}
