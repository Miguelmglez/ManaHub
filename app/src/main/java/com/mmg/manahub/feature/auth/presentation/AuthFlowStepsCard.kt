package com.mmg.manahub.feature.auth.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * Always-visible, non-dismissing guidance block for a multi-step auth flow that requires the user
 * to leave the app (check an email, tap a link) and come back before the flow can finish.
 *
 * Deliberately NOT a [com.mmg.manahub.core.ui.components.MagicToast]: a toast vanishes after a few
 * seconds and would leave the user mid-flow with no reminder of what comes next. This renders as
 * ordinary screen content instead, so it survives recomposition, backgrounding the app to check
 * mail, and coming back — exactly the case these flows are built around.
 *
 * Reused (rather than duplicated) across [UpdateEmailScreen], [AccountManagementScreen]'s pending
 * "change email" note, [UpdatePasswordScreen], [LoginSheet]'s forgot-password dialog, and
 * [ResetPasswordConfirmScreen] — every screen that walks the user through a multi-step account
 * flow. Kept in `feature/auth/presentation` rather than `shared/core-ui` since its shape (a
 * numbered list of plain-English steps) is specific to these flows, not a generic design-system
 * primitive.
 *
 * @param title Short heading, e.g. "How this works".
 * @param steps Ordered guidance lines, rendered as a numbered list.
 */
@Composable
fun AuthFlowStepsCard(
    title: String,
    steps: List<String>,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = mc.primaryAccent.copy(alpha = 0.06f),
        shape = CardShape,
        border = BorderStroke(1.dp, mc.primaryAccent.copy(alpha = 0.2f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sp.md),
            verticalArrangement = Arrangement.spacedBy(sp.sm),
        ) {
            Text(
                text = title,
                style = ty.labelLarge,
                color = mc.textPrimary,
                fontWeight = FontWeight.Bold,
            )
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(mc.primaryAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            style = ty.labelSmall,
                            color = mc.primaryAccent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.width(sp.sm))
                    Text(
                        text = step,
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * The facts every "we sent you an email" wording in this feature must convey, centralised here so
 * every call site shows the SAME wording instead of re-typing it. The sender name itself is
 * centralised in a single string resource ([R.string.auth_email_sender_name]) — every string that
 * needs it (this note included) references that resource via a `%1$s` placeholder rather than a
 * repeated literal, so changing the displayed brand name is a one-line edit.
 *
 * The app never states a literal email address: the notification sender is a server-side secret
 * (see `feature/auth/CLAUDE.md`), and Supabase's own auth mail may come from a different address
 * entirely — only the brand name is ever shown.
 *
 * @param includeLinkFacts Also states the link's expiry (1 hour, `MAILER_OTP_EXP` on this project)
 *   and single-use behavior. Verified today (2026-08-18) against this project's Supabase auth
 *   settings for the password-recovery link specifically — pass `true` only where that has been
 *   confirmed for the flow in question (currently: the forgot-password flow).
 * @param centered Center each line instead of the default full-width/start-aligned layout — for
 *   use inside an already horizontally-centered composition (e.g. [LoginSheet]'s post-signup
 *   "Check your email" screen) so this note doesn't look like a stray left-aligned block.
 */
@Composable
fun EmailDeliveryNote(
    includeLinkFacts: Boolean = false,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val senderName = stringResource(R.string.auth_email_sender_name)
    val textAlign = if (centered) TextAlign.Center else TextAlign.Start

    Column(
        modifier = if (centered) modifier else modifier.fillMaxWidth(),
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(sp.xxs),
    ) {
        // bodySmall (not labelSmall): this note carries two full sentences of prose — the label
        // style's letter-spacing reads as wide-tracked and noticeably dimmer than the step rows
        // right above it for what is, functionally, the most important text on the screen for a
        // stuck user (device review, 2026-08-18). Matches the style AuthFlowStepsCard's own step
        // text uses (see the Row above) so the two blocks read as one consistent block of prose.
        Text(
            text = stringResource(R.string.auth_email_hint_sender, senderName),
            style = ty.bodySmall,
            color = mc.textSecondary,
            textAlign = textAlign,
        )
        Text(
            text = stringResource(R.string.auth_email_hint_spam),
            style = ty.bodySmall,
            color = mc.textSecondary,
            textAlign = textAlign,
        )
        if (includeLinkFacts) {
            Text(
                text = stringResource(R.string.auth_email_hint_link_expiry),
                style = ty.bodySmall,
                color = mc.textSecondary,
                textAlign = textAlign,
            )
            Text(
                text = stringResource(R.string.auth_email_hint_link_single_use),
                style = ty.bodySmall,
                color = mc.textSecondary,
                textAlign = textAlign,
            )
        }
    }
}
