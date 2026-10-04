package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
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
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.core.ui.components.MagicActionRow
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import kotlinx.coroutines.launch

internal enum class DeckAddCardsMethod {
    MANUAL_SEARCH,
    SCAN_CARDS,
    IMPORT_LIST,
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
    var isClosingProgrammatically by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden && !isClosingProgrammatically) {
                false
            } else {
                true
            }
        },
    )
    val scope = rememberCoroutineScope()

    fun selectMethod(method: DeckAddCardsMethod) {
        scope.launch {
            isClosingProgrammatically = true
            sheetState.hide()
            onDismiss()
            onMethodSelected(method)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = mc.backgroundSecondary,
        dragHandle = null,
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
                IconButton(
                    onClick = {
                        scope.launch {
                            isClosingProgrammatically = true
                            sheetState.hide()
                            onDismiss()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = mc.textSecondary,
                    )
                }
                Text(
                    text = stringResource(R.string.deck_studio_add_cards_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.deck_studio_add_cards_subtitle),
                style = ty.bodySmall,
                color = mc.textSecondary,
            )
            MagicActionRow(
                icon = Icons.Default.Search,
                title = stringResource(R.string.deck_studio_add_cards_manual_title),
                subtitle = stringResource(R.string.deck_studio_add_cards_manual_subtitle),
                accentColor = mc.primaryAccent,
                onClick = { selectMethod(DeckAddCardsMethod.MANUAL_SEARCH) },
            )
            MagicActionRow(
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
                accentColor = mc.goldMtg,
                onClick = { selectMethod(DeckAddCardsMethod.SCAN_CARDS) },
            )
            MagicActionRow(
                icon = Icons.Default.FileUpload,
                title = stringResource(R.string.deck_import_title),
                subtitle = stringResource(R.string.deck_import_hint),
                accentColor = mc.secondaryAccent,
                onClick = { selectMethod(DeckAddCardsMethod.IMPORT_LIST) },
            )
        }
    }
}
