package com.mmg.manahub.feature.decks.presentation.components
// COMMENTS_REVIEWED: 2026-09-16

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlinx.coroutines.launch

internal enum class DeckAddCardsMethod {
    MANUAL_SEARCH,
    SCAN_CARDS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeckAddCardsMethodSheet(
    scanEnabled: Boolean,
    onDismiss: () -> Unit,
    onMethodSelected: (DeckAddCardsMethod) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun selectMethod(method: DeckAddCardsMethod) {
        onMethodSelected(method)
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = mc.backgroundSecondary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.deck_studio_add_cards_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = mc.textSecondary,
                    )
                }
            }
            Text(
                text = stringResource(R.string.deck_studio_add_cards_subtitle),
                style = ty.bodySmall,
                color = mc.textSecondary,
            )
            AddCardsMethodRow(
                icon = Icons.Default.Search,
                title = stringResource(R.string.deck_studio_add_cards_manual_title),
                subtitle = stringResource(R.string.deck_studio_add_cards_manual_subtitle),
                onClick = { selectMethod(DeckAddCardsMethod.MANUAL_SEARCH) },
            )
            AddCardsMethodRow(
                icon = Icons.Default.CameraAlt,
                title = stringResource(R.string.deck_studio_add_cards_scan_title),
                subtitle = stringResource(
                    if (scanEnabled) {
                        R.string.deck_studio_add_cards_scan_subtitle
                    } else {
                        R.string.deck_studio_add_cards_scan_disabled
                    },
                ),
                enabled = scanEnabled,
                onClick = { selectMethod(DeckAddCardsMethod.SCAN_CARDS) },
            )
        }
    }
}

@Composable
private fun AddCardsMethodRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        shape = CardShape,
        color = if (enabled) mc.surface else mc.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MaterialTheme.spacing.xxl + MaterialTheme.spacing.lg)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) mc.primaryAccent else mc.textDisabled,
                modifier = Modifier.size(spacing.xl),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = ty.titleMedium,
                    color = if (enabled) mc.textPrimary else mc.textDisabled,
                )
                Text(
                    text = subtitle,
                    style = ty.bodySmall,
                    color = if (enabled) mc.textSecondary else mc.textDisabled,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
