package com.mmg.magicfolder.feature.decks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.mmg.magicfolder.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.magicfolder.core.domain.model.DeckSummary
import com.mmg.magicfolder.core.ui.components.ManaColor
import com.mmg.magicfolder.core.ui.components.ManaSymbol
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
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
                contentPadding      = PaddingValues(top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(uiState.decks, key = { it.id }) { deck ->
                    DeckItem(
                        deck     = deck,
                        onClick  = { onDeckClick(deck.id) },
                        onDelete = { viewModel.deleteDeck(deck.id) },
                    )
                }
            }
        }

        // Create deck FAB pinned at bottom
        Button(
            onClick  = viewModel::onShowCreateDialog,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = mc.primaryAccent),
            shape  = RoundedCornerShape(12.dp),
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

    // Create deck bottom sheet
    if (uiState.showCreateDialog) {
        CreateDeckBottomSheet(
            onDismiss = viewModel::onDismissCreateDialog,
            onCreate  = { name, format -> viewModel.createDeck(name, format) },
        )
    }

    uiState.error?.let {
        LaunchedEffect(it) { viewModel.onErrorDismissed() }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Deck list item
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DeckItem(
    deck:     DeckSummary,
    onClick:  () -> Unit,
    onDelete: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        onClick   = onClick,
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors    = CardDefaults.cardColors(containerColor = mc.surface),
        shape     = RoundedCornerShape(12.dp),
        border    = BorderStroke(0.5.dp, mc.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // ── Art crop / placeholder ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
            ) {
                if (deck.coverImageUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(deck.coverImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier.fillMaxSize(),
                    )
                    // Bottom gradient to blend into the card surface
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.55f)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, mc.surface.copy(alpha = 0.85f)),
                                ),
                            ),
                    )
                } else {
                    // Placeholder
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        mc.primaryAccent.copy(alpha = 0.10f),
                                        mc.surfaceVariant,
                                    ),
                                ),
                            ),
                    ) {
                        Icon(
                            imageVector        = Icons.Default.LibraryBooks,
                            contentDescription = null,
                            tint               = mc.textDisabled,
                            modifier           = Modifier.size(36.dp),
                        )
                    }
                }

                // Format badge — top-right overlay
                Surface(
                    color  = mc.primaryAccent.copy(alpha = 0.85f),
                    shape  = RoundedCornerShape(bottomStart = 8.dp),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        text     = deck.format.replaceFirstChar { it.uppercase() },
                        style    = ty.labelSmall,
                        color    = mc.background,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            // ── Info row ──────────────────────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier            = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Deck name
                    Text(
                        text     = deck.name,
                        style    = ty.titleMedium,
                        color    = mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // Card count + updated date
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        CardCountBadge(count = deck.cardCount)
                        Text(
                            text  = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                .format(Date(deck.updatedAt)),
                            style = ty.bodySmall,
                            color = mc.textDisabled,
                        )
                    }

                    // Mana identity symbols
                    if (deck.colorIdentity.isNotEmpty()) {
                        ColorIdentityRow(colorIdentity = deck.colorIdentity)
                    }
                }

                // Delete button
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                        tint               = mc.textDisabled,
                        modifier           = Modifier.size(18.dp),
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title   = { Text(stringResource(R.string.decklist_delete_title)) },
            text    = { Text(stringResource(R.string.decklist_delete_message, deck.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_delete), color = mc.lifeNegative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CardCountBadge(count: Int) {
    val mc = MaterialTheme.magicColors
    Surface(
        color = mc.secondaryAccent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text     = stringResource(R.string.decklist_card_count, count),
            style    = MaterialTheme.magicTypography.labelSmall,
            color    = mc.secondaryAccent,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Renders the color identity of a deck as a row of mana symbols. */
@Composable
private fun ColorIdentityRow(colorIdentity: Set<String>) {
    val wubrgOrder = listOf("W", "U", "B", "R", "G")
    val sorted = colorIdentity
        .sortedBy { code ->
            val idx = wubrgOrder.indexOf(code.uppercase())
            if (idx >= 0) idx else 99
        }

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        sorted.forEach { code ->
            val manaColor = when (code.uppercase()) {
                "W"  -> ManaColor.W
                "U"  -> ManaColor.U
                "B"  -> ManaColor.B
                "R"  -> ManaColor.R
                "G"  -> ManaColor.G
                else -> ManaColor.C
            }
            ManaSymbol(color = manaColor, size = 18.dp)
        }
        if (sorted.isEmpty()) {
            ManaSymbol(color = ManaColor.C, size = 18.dp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty state
// ─────────────────────────────────────────────────────────────────────────────

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
            stringResource(R.string.decklist_empty_title),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        Text(
            stringResource(R.string.decklist_empty_subtitle),
            style = MaterialTheme.magicTypography.bodyMedium,
            color = mc.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onCreateClick,
            colors  = ButtonDefaults.outlinedButtonColors(contentColor = mc.primaryAccent),
            border  = BorderStroke(1.dp, mc.primaryAccent),
        ) {
            Text(
                stringResource(R.string.decklist_empty_action),
                style = MaterialTheme.magicTypography.labelLarge,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Create deck — ModalBottomSheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateDeckBottomSheet(
    onDismiss: () -> Unit,
    onCreate:  (name: String, format: String) -> Unit,
) {
    val mc         = MaterialTheme.magicColors
    val ty         = MaterialTheme.magicTypography
    var name       by remember { mutableStateOf("") }
    var format     by remember { mutableStateOf("standard") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheetState,
        containerColor   = mc.backgroundSecondary,
        dragHandle       = { BottomSheetDefaults.DragHandle(color = mc.textDisabled) },
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text  = "New Deck",
                style = ty.titleMedium,
                color = mc.textPrimary,
            )

            OutlinedTextField(
                value         = name,
                onValueChange = { name = it },
                label         = { Text(stringResource(R.string.deckbuilder_name_hint)) },
                singleLine    = true,
                modifier      = Modifier.fillMaxWidth(),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = mc.primaryAccent,
                    unfocusedBorderColor = mc.surfaceVariant,
                    focusedLabelColor    = mc.primaryAccent,
                    focusedTextColor     = mc.textPrimary,
                    unfocusedTextColor   = mc.textPrimary,
                    cursorColor          = mc.primaryAccent,
                ),
            )

            FormatSelector(selected = format, onSelect = { format = it })

            Button(
                onClick  = { if (name.isNotBlank()) onCreate(name, format) },
                enabled  = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(
                    containerColor         = mc.primaryAccent,
                    disabledContainerColor = mc.primaryAccent.copy(alpha = 0.3f),
                ),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = mc.background)
                Spacer(Modifier.width(8.dp))
                Text(
                    text  = "Create Deck",
                    style = ty.labelLarge,
                    color = mc.background,
                )
            }
        }
    }
}

@Composable
private fun FormatSelector(selected: String, onSelect: (String) -> Unit) {
    val formats = listOf("standard", "commander", "draft")
    val mc      = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text  = "Format",
            style = MaterialTheme.magicTypography.labelSmall,
            color = mc.textSecondary,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
