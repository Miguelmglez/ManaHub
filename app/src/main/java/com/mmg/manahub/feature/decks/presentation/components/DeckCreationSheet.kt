package com.mmg.manahub.feature.decks.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicSelectionItem
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckCreationSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, format: DeckFormat) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden } // Prevent accidental swipe-to-dismiss
    )
    var deckName by remember { mutableStateOf("New Deck") }
    var selectedFormat by remember { mutableStateOf(DeckFormat.COMMANDER) }

    val formats = listOf(
        DeckFormat.COMMANDER, DeckFormat.COMMANDER_CASUAL, DeckFormat.CASUAL,
        DeckFormat.STANDARD, DeckFormat.PIONEER, DeckFormat.MODERN,
        DeckFormat.LEGACY, DeckFormat.VINTAGE, DeckFormat.PAUPER,
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.magicColors.background,
        dragHandle = null, // Cleaner look since we use a manual Close button
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.lg)
                .padding(bottom = MaterialTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.lg)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MaterialTheme.spacing.md),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.magicColors.textSecondary
                    )
                }
                Text(
                    text = "Create New Deck",
                    style = MaterialTheme.magicTypography.titleLarge,
                    color = MaterialTheme.magicColors.textPrimary
                )
            }

            Text(
                text = "Choose a format to begin your building journey.",
                style = MaterialTheme.magicTypography.bodyMedium,
                color = MaterialTheme.magicColors.textSecondary
            )

            OutlinedTextField(
                value = deckName,
                onValueChange = { deckName = it },
                label = { Text("Deck Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.magicColors.primaryAccent,
                    focusedLabelColor = MaterialTheme.magicColors.primaryAccent,
                    unfocusedBorderColor = MaterialTheme.magicColors.surfaceVariant,
                    unfocusedLabelColor = MaterialTheme.magicColors.textSecondary,
                    cursorColor = MaterialTheme.magicColors.primaryAccent,
                )
            )

            Text(
                text = "Select Format",
                style = MaterialTheme.magicTypography.labelLarge,
                color = MaterialTheme.magicColors.primaryAccent
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(formats) { format ->
                    val mc = MaterialTheme.magicColors
                    val accentColor = when (format) {
                        DeckFormat.COMMANDER, DeckFormat.COMMANDER_CASUAL -> mc.goldMtg
                        DeckFormat.STANDARD, DeckFormat.MODERN, DeckFormat.PIONEER -> mc.primaryAccent
                        else -> mc.secondaryAccent
                    }
                    val desc = when (format) {
                        DeckFormat.COMMANDER -> stringResource(R.string.deck_wizard_format_desc_commander)
                        DeckFormat.COMMANDER_CASUAL -> "Commander rules but no card legality checks."
                        DeckFormat.CASUAL -> stringResource(R.string.deck_wizard_format_desc_casual)
                        DeckFormat.STANDARD -> stringResource(R.string.deck_wizard_format_desc_standard)
                        DeckFormat.PIONEER -> stringResource(R.string.deck_wizard_format_desc_pioneer)
                        DeckFormat.MODERN -> stringResource(R.string.deck_wizard_format_desc_modern)
                        DeckFormat.LEGACY -> stringResource(R.string.deck_wizard_format_desc_legacy)
                        DeckFormat.VINTAGE -> stringResource(R.string.deck_wizard_format_desc_vintage)
                        DeckFormat.PAUPER -> stringResource(R.string.deck_wizard_format_desc_pauper)
                        else -> ""
                    }
                    
                    MagicSelectionItem(
                        title = format.displayName,
                        description = desc,
                        isSelected = format == selectedFormat,
                        accentColor = accentColor,
                        onClick = { selectedFormat = format }
                    )
                }
            }

            MagicCtaButton(
                text = "Create Deck",
                onClick = {
                    onCreate(deckName, selectedFormat)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// FormatSelectionItem was replaced by MagicSelectionItem
