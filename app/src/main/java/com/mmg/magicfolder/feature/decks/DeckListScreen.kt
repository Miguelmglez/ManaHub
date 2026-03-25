package com.mmg.magicfolder.feature.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.magicfolder.core.domain.model.Deck
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DeckListScreen(
    onDeckClick:       (deckId: Long) -> Unit,
    onCreateDeckClick: () -> Unit,
    viewModel:         DeckViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> CircularProgressIndicator(
                color    = mc.primaryAccent,
                modifier = Modifier.align(Alignment.Center),
            )

            uiState.decks.isEmpty() -> EmptyDecksState(
                onCreateClick = viewModel::onShowCreateDialog,
                modifier      = Modifier.align(Alignment.Center),
            )

            else -> LazyColumn(
                contentPadding      = PaddingValues(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(uiState.decks, key = { it.id }) { deck ->
                    DeckItem(
                        deck      = deck,
                        onClick   = { onDeckClick(deck.id) },
                        onDelete  = { viewModel.deleteDeck(deck.id) },
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = mc.surfaceVariant)
                }
            }
        }

        // Create deck button — pinned at bottom
        Button(
            onClick  = viewModel::onShowCreateDialog,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = mc.background)
            Spacer(Modifier.width(8.dp))
            Text(
                "Create Deck",
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.background,
            )
        }
    }

    // Create deck dialog
    if (uiState.showCreateDialog) {
        CreateDeckDialog(
            onDismiss = viewModel::onDismissCreateDialog,
            onCreate  = { name, format -> viewModel.createDeck(name, format) },
        )
    }

    // Error
    uiState.error?.let {
        LaunchedEffect(it) { viewModel.onErrorDismissed() }
    }
}

@Composable
private fun DeckItem(
    deck:     Deck,
    onClick:  () -> Unit,
    onDelete: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var showDeleteDialog by remember { mutableStateOf(false) }

    ListItem(
        modifier        = Modifier.clickableNoIndication(onClick),
        colors          = ListItemDefaults.colors(containerColor = mc.background),
        headlineContent = {
            Text(
                text     = deck.name,
                style    = MaterialTheme.magicTypography.titleMedium,
                color    = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                FormatBadge(format = deck.format)
                Text(
                    text  = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(deck.updatedAt)),
                    style = MaterialTheme.magicTypography.bodySmall,
                    color = mc.textDisabled,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete deck",
                    tint               = mc.textDisabled,
                    modifier           = Modifier.size(18.dp),
                )
            }
        },
    )

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text("Delete deck") },
            text    = { Text("Delete \"${deck.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("Delete", color = mc.lifeNegative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun FormatBadge(format: String) {
    val mc = MaterialTheme.magicColors
    Surface(
        color = mc.primaryAccent.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text     = format.replaceFirstChar { it.uppercase() },
            style    = MaterialTheme.magicTypography.labelSmall,
            color    = mc.primaryAccent,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyDecksState(
    onCreateClick: () -> Unit,
    modifier:      Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector        = Icons.Default.LibraryBooks,
            contentDescription = null,
            tint               = mc.textDisabled,
            modifier           = Modifier.size(64.dp),
        )
        Text(
            "No decks yet",
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        Text(
            "Build your first deck to start playing",
            style = MaterialTheme.magicTypography.bodyMedium,
            color = mc.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onCreateClick,
            colors  = ButtonDefaults.outlinedButtonColors(contentColor = mc.primaryAccent),
            border  = androidx.compose.foundation.BorderStroke(1.dp, mc.primaryAccent),
        ) {
            Text("Create your first deck", style = MaterialTheme.magicTypography.labelLarge)
        }
    }
}

@Composable
private fun CreateDeckDialog(
    onDismiss: () -> Unit,
    onCreate:  (name: String, format: String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    var name   by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("casual") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Deck", style = MaterialTheme.magicTypography.titleMedium) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Deck name") },
                    singleLine    = true,
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = mc.primaryAccent,
                        unfocusedBorderColor = mc.surfaceVariant,
                        focusedLabelColor    = mc.primaryAccent,
                        focusedTextColor     = mc.textPrimary,
                        unfocusedTextColor   = mc.textPrimary,
                    ),
                )
                FormatSelector(selected = format, onSelect = { format = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { onCreate(name, format) },
                enabled  = name.isNotBlank(),
            ) {
                Text("Create", color = if (name.isNotBlank()) mc.primaryAccent else mc.textDisabled)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun FormatSelector(selected: String, onSelect: (String) -> Unit) {
    val formats = listOf("casual", "standard", "pioneer", "modern", "legacy", "vintage", "commander", "pauper")
    val mc = MaterialTheme.magicColors
    Column {
        Text(
            "Format",
            style    = MaterialTheme.magicTypography.labelSmall,
            color    = mc.textSecondary,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(formats) { fmt ->
                FilterChip(
                    selected = fmt == selected,
                    onClick  = { onSelect(fmt) },
                    label    = {
                        Text(
                            fmt.replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.magicTypography.labelSmall,
                        )
                    },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = mc.primaryAccent.copy(alpha = 0.18f),
                        selectedLabelColor     = mc.primaryAccent,
                        containerColor         = mc.surface,
                        labelColor             = mc.textSecondary,
                    ),
                )
            }
        }
    }
}

// Clickable without ripple (used for ListItem since it handles its own ripple)
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
