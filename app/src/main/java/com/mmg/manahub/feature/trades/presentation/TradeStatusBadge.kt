package com.mmg.manahub.feature.trades.presentation

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.TradeStatus
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/** Status pill shared by the negotiation thread and the trade history list. */
@Composable
internal fun TradeStatusBadge(
    status: TradeStatus,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val style = when (status) {
        TradeStatus.COMPLETED -> StatusStyle(mc.lifePositive, Icons.Default.CheckCircle, R.string.trades_status_completed)
        TradeStatus.ACCEPTED -> StatusStyle(mc.primaryAccent, Icons.Default.Check, R.string.trades_status_accepted)
        TradeStatus.CANCELLED -> StatusStyle(mc.lifeNegative, Icons.Default.Cancel, R.string.trades_status_cancelled)
        TradeStatus.REVOKED -> StatusStyle(mc.lifeNegative, Icons.AutoMirrored.Filled.Undo, R.string.trades_status_revoked)
        TradeStatus.DECLINED -> StatusStyle(mc.goldMtg, Icons.Default.Block, R.string.trades_status_declined)
        TradeStatus.COUNTERED -> StatusStyle(mc.secondaryAccent, Icons.Default.SwapHoriz, R.string.trades_status_countered)
        TradeStatus.PROPOSED -> StatusStyle(mc.primaryAccent, Icons.AutoMirrored.Filled.Send, R.string.trades_status_proposed)
        TradeStatus.DRAFT -> StatusStyle(mc.textSecondary, Icons.Default.Edit, R.string.trades_status_draft)
    }
    TradeAccentBadge(
        label = stringResource(style.labelRes),
        accent = style.accent,
        icon = style.icon,
        modifier = modifier,
    )
}

/**
 * Tonal pill for trade states. The accent only tints the fill, border and icon; the label always
 * renders in `textPrimary`, because an accent on a tint of itself falls below AA on several palettes.
 */
@Composable
internal fun TradeAccentBadge(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    Surface(
        shape = ChipShape,
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.magicTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = mc.textPrimary,
            )
        }
    }
}

private data class StatusStyle(
    val accent: Color,
    val icon: ImageVector,
    @StringRes val labelRes: Int,
)
