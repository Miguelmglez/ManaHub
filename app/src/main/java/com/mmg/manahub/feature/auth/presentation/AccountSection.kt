package com.mmg.manahub.feature.auth.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.auth.domain.model.AuthUser
import com.mmg.manahub.feature.auth.domain.model.SessionState

/**
 * Profile-screen section that renders different content based on [sessionState]:
 *
 * - [SessionState.Loading]         → shimmer skeleton card while the session is being restored
 * - [SessionState.Unauthenticated] → feature-promotion card with login / sign-up CTAs
 * - [SessionState.Authenticated]   → compact user identity card with sign-out and delete-account options
 */
@Composable
fun AccountSection(
    sessionState: SessionState,
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
    playerName: String? = null,
    avatarUrl: String? = null,
    shareUrl: String? = null,
) {
    when (sessionState) {
        SessionState.Loading -> AccountSectionSkeleton(modifier = modifier)

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
                shareUrl = shareUrl,
            )
        }
    }
}

// ── Loading skeleton ───────────────────────────────────────────────────────────

/**
 * Shimmer skeleton that mirrors the visual footprint of [AuthenticatedCard].
 * Displayed while [SessionState.Loading] is active (~1-2 seconds on app start).
 *
 * Animation: infinite alpha oscillation between 0.04f and 0.14f applied over
 * [MaterialTheme.magicColors.primaryAccent] so the placeholder tiles respect the
 * active theme without relying on any external shimmer library.
 */
@Composable
private fun AccountSectionSkeleton(modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors

    // Infinite alpha oscillation: 0.04 → 0.14 → 0.04
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton_shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.04f,
        targetValue = 0.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmer_alpha",
    )

    val shimmerColor = mc.primaryAccent.copy(alpha = shimmerAlpha)
    val shimmerBase = mc.primaryAccent.copy(alpha = 0.08f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = mc.primaryAccent.copy(alpha = 0.15f),
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
            // ── Hero placeholder ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.77f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(shimmerColor),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ── Button Row placeholder ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Primary button placeholder (weight(1f) mirrors Sign Out button)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(shimmerColor),
                )
                // Secondary button placeholder (fixed width mirrors Delete Account)
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(shimmerBase),
                )
            }
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
    shareUrl: String? = null,
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
            // ── Hero Section ───────────────────────────────────────────────────
            var imageRatio by remember(avatarUrl) { mutableFloatStateOf(1.77f) }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageRatio.coerceIn(1.2f, 2.5f))
                    .animateContentSize()
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(avatarUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        onSuccess = { state ->
                            val size = state.painter.intrinsicSize
                            if (size.width > 0 && size.height > 0) {
                                imageRatio = size.width / size.height
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        mc.primaryAccent.copy(alpha = 0.3f),
                                        mc.background,
                                    ),
                                ),
                            ),
                    ) {
                        ThemeBackground(modifier = Modifier.fillMaxSize())
                        Text(
                            text = nickname.take(1).uppercase().ifEmpty { "✦" },
                            style = ty.lifeNumberMd.copy(
                                fontSize = 72.sp,
                                color = mc.primaryAccent.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0.85f),
                                ),
                            ),
                        ),
                )

                // Game tag badge — bottom start
                if (gameTag != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .background(
                                color = mc.primaryAccent.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = gameTag,
                            color = mc.primaryAccent,
                            style = ty.labelSmall.copy(fontSize = 11.sp),
                        )
                    }
                }
            }

            // ── Share invite link button ───────────────────────────────────────
            if (shareUrl != null) {
                val context = LocalContext.current
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                        }
                        context.startActivity(
                            Intent.createChooser(
                                intent,
                                context.getString(com.mmg.manahub.R.string.friends_share_chooser_title)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, mc.primaryAccent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = mc.primaryAccent),
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = mc.primaryAccent,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(com.mmg.manahub.R.string.friends_share_my_link),
                        style = ty.labelLarge,
                    )
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
