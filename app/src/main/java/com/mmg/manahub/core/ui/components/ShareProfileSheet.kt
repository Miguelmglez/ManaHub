package com.mmg.manahub.core.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlinx.coroutines.launch

/**
 * Reusable bottom sheet offering the two ways to invite someone to ManaHub: sharing the raw
 * game tag as text, or sharing the referral invite link (built server-side from the user's
 * referral code, see `ShareInviteUseCase`).
 *
 * Stateless per CLAUDE.md's UI rules: it never touches a ViewModel directly. The invite-link
 * fetch is a suspend lambda supplied by the host screen (which owns whichever ViewModel can
 * resolve [com.mmg.manahub.feature.friends.domain.usecase.ShareInviteUseCase]), so this
 * composable stays reusable across any screen that knows the user's game tag.
 *
 * Uses the platform `Intent.ACTION_SEND` chooser — this is intentionally Android-only (not
 * `commonMain`): sharing via the OS chooser has no wasmJs equivalent, so this component lives in
 * `app/` rather than `shared/core-ui`, matching how the rest of the Auth/Friends presentation
 * layer is still Android-only pending its own KMP migration slice.
 *
 * @param gameTag the current user's game tag (e.g. "#A3KX9Z"), or null while it has not loaded
 *   yet — the "Share by game tag" row is hidden in that case so the sheet never shares a blank tag.
 * @param onFetchShareLink suspend lambda resolving the invite URL (wraps
 *   `ShareInviteUseCase`/`FriendRepository.getMyShareUrl`); failure surfaces an inline retry hint.
 * @param onDismiss invoked when the sheet is dismissed, including right after a successful share.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareProfileSheet(
    gameTag: String?,
    onFetchShareLink: suspend () -> Result<String>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isFetchingLink by remember { mutableStateOf(false) }
    var linkFetchFailed by remember { mutableStateOf(false) }

    val chooserTitle = stringResource(R.string.friends_share_chooser_title)
    val gameTagShareTemplate = stringResource(R.string.share_profile_gametag_text)
    val linkShareTemplate = stringResource(R.string.share_profile_link_text)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
        shape = BottomSheetShape,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.share_profile_sheet_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
                modifier = Modifier.padding(horizontal = spacing.xl, vertical = spacing.sm),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .padding(horizontal = spacing.lg, vertical = spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (gameTag != null) {
                    ShareOptionRow(
                        leading = {
                            ShareOptionGlyph(text = "#", accent = mc.primaryAccent)
                        },
                        title = stringResource(R.string.share_profile_option_gametag_title),
                        subtitle = gameTag,
                        onClick = {
                            launchShareIntent(
                                context = context,
                                shareText = gameTagShareTemplate.format(gameTag),
                                chooserTitle = chooserTitle,
                            )
                            onDismiss()
                        },
                    )
                }

                ShareOptionRow(
                    leading = {
                        if (isFetchingLink) {
                            Box(
                                modifier = Modifier.size(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = mc.primaryAccent,
                                )
                            }
                        } else {
                            ShareOptionIcon(icon = Icons.AutoMirrored.Filled.OpenInNew, accent = mc.primaryAccent)
                        }
                    },
                    title = stringResource(R.string.share_profile_option_link_title),
                    subtitle = if (linkFetchFailed) {
                        stringResource(R.string.share_profile_link_error)
                    } else {
                        stringResource(R.string.share_profile_option_link_subtitle)
                    },
                    subtitleColor = if (linkFetchFailed) mc.lifeNegative else mc.textSecondary,
                    enabled = !isFetchingLink,
                    onClick = {
                        scope.launch {
                            isFetchingLink = true
                            linkFetchFailed = false
                            val result = onFetchShareLink()
                            isFetchingLink = false
                            result.onSuccess { url ->
                                launchShareIntent(
                                    context = context,
                                    shareText = linkShareTemplate.format(url),
                                    chooserTitle = chooserTitle,
                                )
                                onDismiss()
                            }.onFailure {
                                linkFetchFailed = true
                            }
                        }
                    },
                )

                Box(modifier = Modifier.padding(bottom = spacing.lg))
            }
        }
    }
}

@Composable
private fun ShareOptionRow(
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleColor: Color = MaterialTheme.magicColors.textSecondary,
    enabled: Boolean = true,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CardShape)
            .background(mc.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = ty.bodyMedium, color = mc.textPrimary)
            Text(text = subtitle, style = ty.labelSmall, color = subtitleColor)
        }
    }
}

@Composable
private fun ShareOptionGlyph(text: String, accent: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.magicTypography.titleMedium, color = accent)
    }
}

@Composable
private fun ShareOptionIcon(icon: ImageVector, accent: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
    }
}

/** Fires the OS share chooser with plain-text [shareText]. */
private fun launchShareIntent(context: Context, shareText: String, chooserTitle: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(sendIntent, chooserTitle))
}
