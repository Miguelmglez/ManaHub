package com.mmg.manahub.feature.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.core.domain.model.*
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography

@Composable
fun DeckAddCardsScreen(
    onBack:    () -> Unit,
    viewModel: DeckAddCardsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Scaffold(
        containerColor = mc.background,
        topBar = {
            Surface(color = mc.backgroundSecondary) {
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint               = mc.textSecondary,
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(
                            text     = "Add Cards",
                            style    = ty.titleMedium,
                            color    = mc.textPrimary,
                            maxLines = 1,
                        )
                        if (uiState.deckName.isNotBlank()) {
                            Text(
                                text     = uiState.deckName,
                                style    = ty.bodySmall,
                                color    = mc.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Search bar
            OutlinedTextField(
                value         = uiState.query,
                onValueChange = viewModel::onQueryChange,
                modifier      = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder   = { Text("Search cards…", color = mc.textDisabled) },
                leadingIcon   = {
                    if (uiState.isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color    = mc.primaryAccent,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary)
                    }
                },
                trailingIcon  = if (uiState.query.isNotEmpty()) {{
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = mc.textSecondary)
                    }
                }} else null,
                singleLine    = true,
                shape         = MaterialTheme.shapes.medium,
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = mc.primaryAccent,
                    unfocusedBorderColor = mc.surfaceVariant,
                    focusedTextColor     = mc.textPrimary,
                    unfocusedTextColor   = mc.textPrimary,
                    cursorColor          = mc.primaryAccent,
                ),
            )

            when {
                uiState.isLoading -> Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = mc.primaryAccent)
                }

                uiState.cards.isEmpty() && !uiState.isSearching -> Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text  = if (uiState.query.isBlank()) "Your collection is empty"
                                else "No results for \"${uiState.query}\"",
                        style = ty.bodyMedium,
                        color = mc.textDisabled,
                    )
                }

                else -> LazyColumn(
                    contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(uiState.cards, key = { it.card.scryfallId }) { row ->
                        AddCardSheetRow(
                            row      = row,
                            onAdd    = { viewModel.addCard(row.card.scryfallId) },
                            onRemove = { viewModel.removeCard(row.card.scryfallId) },
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AddCardSheetRow(
    row:      AddCardRow,
    onAdd:    () -> Unit,
    onRemove: () -> Unit,
) {
    val mc   = MaterialTheme.magicColors
    val ty   = MaterialTheme.magicTypography
    val card = row.card

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = mc.surface,
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model              = card.imageArtCrop,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .size(width = 52.dp, height = 38.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(mc.surfaceVariant),
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text     = card.name,
                        style    = ty.bodyMedium,
                        color    = mc.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (!row.isOwned) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = mc.surfaceVariant,
                        ) {
                            Text(
                                text     = "Scryfall",
                                style    = ty.labelSmall,
                                color    = mc.textDisabled,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
                Text(
                    text     = card.typeLine,
                    style    = ty.bodySmall,
                    color    = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Quantity controls
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (row.quantityInDeck > 0) {
                    IconButton(
                        onClick  = onRemove,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Remove,
                            contentDescription = "Remove",
                            tint               = mc.textSecondary,
                            modifier           = Modifier.size(16.dp),
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = mc.primaryAccent.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text     = "${row.quantityInDeck}",
                            style    = ty.labelMedium,
                            color    = mc.primaryAccent,
                            modifier = Modifier
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .widthIn(min = 24.dp),
                        )
                    }
                }
                IconButton(
                    onClick  = onAdd,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Default.Add,
                        contentDescription = "Add",
                        tint               = mc.primaryAccent,
                        modifier           = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
