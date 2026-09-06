package com.mmg.manahub.feature.collection.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.core.sync.MergeConflictResolution
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.collection.presentation.MergeConflictUiItem

/**
 * Lets the user resolve each pending guest/account collection merge conflict left behind by
 * [com.mmg.manahub.core.data.local.dao.UserCardCollectionDao.assignUserId]'s collision guard
 * (write-path hardening audit, Phase 7, 2026-09-06).
 *
 * Nothing is discarded until the user picks a resolution for a specific row — dismissing this
 * sheet (back gesture / scrim tap) loses nothing; every unresolved conflict simply reappears the
 * next time this sheet is shown.
 *
 * @param conflicts Rows still awaiting a choice — the sheet renders one card per conflict and
 *   removes it from the list (via the caller updating [conflicts]) as each is resolved.
 * @param onResolve Called with the chosen [MergeConflictResolution] for one conflict's
 *   [com.mmg.manahub.core.sync.CollectionMergeConflict].
 * @param onDismiss Called when the sheet is dismissed without necessarily resolving everything.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionMergeConflictSheet(
    conflicts: List<MergeConflictUiItem>,
    onResolve: (MergeConflictUiItem, MergeConflictResolution) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = mc.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                "Resolve collection conflicts",
                style = MaterialTheme.magicTypography.titleLarge,
                color = mc.textPrimary,
            )
            Text(
                "These cards were added both offline and to your account. Choose how to keep each one — nothing is lost until you decide.",
                style = MaterialTheme.magicTypography.bodyMedium,
                color = mc.textSecondary,
            )

            if (conflicts.isEmpty()) {
                Text(
                    "All conflicts resolved.",
                    style = MaterialTheme.magicTypography.bodyMedium,
                    color = mc.textSecondary,
                    modifier = Modifier.padding(vertical = spacing.lg),
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                    contentPadding = PaddingValues(vertical = spacing.sm),
                ) {
                    items(conflicts, key = { it.conflict.guestRow.id }) { item ->
                        ConflictRow(item = item, onResolve = { resolution -> onResolve(item, resolution) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictRow(
    item: MergeConflictUiItem,
    onResolve: (MergeConflictResolution) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(mc.surfaceVariant)
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp, 64.dp)
                    .clip(CardShape)
                    .background(mc.surface),
            )
            Column {
                Text(item.cardName, style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
                Text(
                    "Offline: ×${item.conflict.guestRow.quantity}   Account: ×${item.conflict.accountRow.quantity}",
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textSecondary,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            MagicCtaButton(
                text = "Sum (${item.conflict.guestRow.quantity + item.conflict.accountRow.quantity})",
                onClick = { onResolve(MergeConflictResolution.SUM) },
                style = MagicCtaStyle.Filled,
                color = MagicCtaColor.Primary,
                modifier = Modifier.weight(1f),
            )
            MagicCtaButton(
                text = "Keep account",
                onClick = { onResolve(MergeConflictResolution.KEEP_ACCOUNT) },
                style = MagicCtaStyle.Outlined,
                color = MagicCtaColor.Neutral,
                modifier = Modifier.weight(1f),
            )
            MagicCtaButton(
                text = "Keep offline",
                onClick = { onResolve(MergeConflictResolution.KEEP_OFFLINE) },
                style = MagicCtaStyle.Outlined,
                color = MagicCtaColor.Neutral,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
