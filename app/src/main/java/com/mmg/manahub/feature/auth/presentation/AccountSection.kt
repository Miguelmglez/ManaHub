package com.mmg.manahub.feature.auth.presentation

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.ShareProfileSheet
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.domain.auth.AuthUser
import com.mmg.manahub.core.domain.auth.SessionState

/**
 * Profile-screen section that renders different content based on [sessionState]:
 *
 * - [SessionState.Loading]         → shimmer skeleton card while the session is being restored
 * - [SessionState.Unauthenticated] → feature-promotion card with login / sign-up CTAs
 * - [SessionState.Authenticated]   → user identity card with a "Manage my account" CTA (account
 *   settings, sign-out and delete-account live on a dedicated screen owned by a later phase) and a
 *   "Share my profile" CTA opening [ShareProfileSheet].
 *
 * @param onManageAccountClick invoked by the "Manage my account" CTA. The destination screen is not
 *   wired yet (a later phase owns it) — the caller only needs to supply the callback for now.
 * @param onFetchShareLink suspend lookup for the invite link, forwarded verbatim to
 *   [ShareProfileSheet] (see that composable's KDoc for why this stays a lambda rather than a
 *   ViewModel call).
 */
@Composable
fun AccountSection(
    sessionState: SessionState,
    onLoginClick: () -> Unit,
    onSignUpClick: () -> Unit,
    onManageAccountClick: () -> Unit,
    onFetchShareLink: suspend () -> Result<String>,
    modifier: Modifier = Modifier,
    playerName: String? = null,
    avatarUrl: String? = null,
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
                onManageAccountClick = onManageAccountClick,
                onFetchShareLink = onFetchShareLink,
                modifier = modifier,
                displayName = playerName,
                displayAvatarUrl = avatarUrl,
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
            // ── User Info placeholder ──────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(shimmerColor)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerColor)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmerColor)
                    )
                }
            }

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
    onManageAccountClick: () -> Unit,
    onFetchShareLink: suspend () -> Result<String>,
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

    var showShareSheet by remember { mutableStateOf(false) }

    if (showShareSheet) {
        ShareProfileSheet(
            gameTag = gameTag,
            onFetchShareLink = onFetchShareLink,
            onDismiss = { showShareSheet = false },
        )
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = mc.primaryAccent.copy(alpha = 0.25f),
                shape = CardShape,
            ),
        color = mc.surface,
        shape = CardShape,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Accent header band ───────────────────────────────────────────
            // A soft gradient banner behind the avatar row gives the authenticated
            // state a more prominent, "you belong here" identity treatment than a
            // flat surface, while staying entirely token-driven (works on all 12
            // palettes, including the light HallowedPrint theme).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                mc.primaryAccent.copy(alpha = 0.16f),
                                Color.Transparent,
                            ),
                        ),
                    )
                    .padding(16.dp),
            ) {
                // ── User Info Section ──────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar with a gradient accent ring
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(CircleShape)
                            .border(
                                width = 2.dp,
                                brush = Brush.linearGradient(listOf(mc.primaryAccent, mc.secondaryAccent)),
                                shape = CircleShape,
                            )
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(mc.primaryAccent.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        } else {
                            ThemeBackground(modifier = Modifier.fillMaxSize())
                            Text(
                                text = nickname.take(1).uppercase().ifEmpty { "✦" },
                                style = ty.titleLarge.copy(color = mc.primaryAccent.copy(alpha = 0.7f)),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Name & Email
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = nickname,
                                style = ty.titleMedium,
                                color = mc.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (gameTag != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .border(
                                            width = 1.dp,
                                            color = mc.primaryAccent.copy(alpha = 0.4f),
                                            shape = ChipShape,
                                        )
                                        .background(
                                            color = mc.primaryAccent.copy(alpha = 0.12f),
                                            shape = ChipShape,
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = gameTag,
                                        color = mc.primaryAccent,
                                        style = ty.labelSmall.copy(fontSize = 10.sp),
                                    )
                                }
                            }
                        }
                        val email: String? = user.email
                        if (email != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = email,
                                style = ty.bodySmall,
                                color = mc.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                // ── Action Buttons ───────────────────────────────────────────────
                MagicCtaButton(
                    onClick = onManageAccountClick,
                    text = stringResource(R.string.auth_manage_account),
                    style = MagicCtaStyle.Filled,
                    color = MagicCtaColor.Primary,
                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(12.dp))

                MagicCtaButton(
                    onClick = { showShareSheet = true },
                    text = stringResource(R.string.auth_share_my_profile),
                    style = MagicCtaStyle.Outlined,
                    color = MagicCtaColor.Primary,
                    icon = {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
