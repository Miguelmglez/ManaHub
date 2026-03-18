package com.mmg.magicfolder.feature.synergy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SynergyScreen(
    onCardClick: (scryfallId: String) -> Unit,
    viewModel:   SynergyViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Synergies & decks") },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.synergyGroups.isEmpty() && uiState.collectionCards.isEmpty() ->
                EmptySynergyState(modifier = Modifier.padding(padding))

            else -> SynergyContent(
                uiState     = uiState,
                onFormatChange = viewModel::onFormatChange,
                onCardClick = onCardClick,
                modifier    = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun SynergyContent(
    uiState:       SynergyUiState,
    onFormatChange: (DeckFormat) -> Unit,
    onCardClick:   (String) -> Unit,
    modifier:      Modifier = Modifier,
) {
    LazyColumn(
        modifier        = modifier.fillMaxSize(),
        contentPadding  = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Format selector
        item {
            FormatSelector(
                selected = uiState.selectedFormat,
                onChange = onFormatChange,
            )
        }

        // Deck suggestions
        if (uiState.suggestedDecks.isNotEmpty()) {
            item {
                Text("Suggested decks", style = MaterialTheme.typography.titleMedium)
            }
            items(uiState.suggestedDecks) { deck ->
                DeckSuggestionCard(suggestion = deck, onCardClick = onCardClick)
            }
        }

        // Synergy groups
        if (uiState.synergyGroups.isNotEmpty()) {
            item {
                Text("Synergy groups", style = MaterialTheme.typography.titleMedium)
            }
            items(uiState.synergyGroups) { group ->
                SynergyGroupCard(group = group, onCardClick = onCardClick)
            }
        }
    }
}

@Composable
private fun FormatSelector(selected: DeckFormat, onChange: (DeckFormat) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Format", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(DeckFormat.entries) { format ->
                FilterChip(
                    selected = format == selected,
                    onClick  = { onChange(format) },
                    label    = { Text(format.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
    }
}

@Composable
private fun DeckSuggestionCard(
    suggestion:  DeckSuggestion,
    onCardClick: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                Text(suggestion.name, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                // Synergy score
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        text     = "${suggestion.synergyScore}%",
                        style    = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text  = "${suggestion.cards.size} cards · ${suggestion.format.name.lowercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Color identity pills
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                suggestion.colors.forEach { color ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall,
                    ) {
                        Text(
                            text     = color.name,
                            style    = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            // Card thumbnails
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(suggestion.cards.take(8)) { item ->
                    AsyncImage(
                        model              = item.card.imageArtCrop,
                        contentDescription = item.card.name,
                        contentScale       = ContentScale.Crop,
                        modifier           = Modifier
                            .size(width = 48.dp, height = 34.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .clickable { onCardClick(item.card.scryfallId) },
                    )
                }
                if (suggestion.cards.size > 8) {
                    item {
                        Surface(
                            modifier = Modifier.size(width = 48.dp, height = 34.dp),
                            color    = MaterialTheme.colorScheme.surfaceVariant,
                            shape    = MaterialTheme.shapes.extraSmall,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("+${suggestion.cards.size - 8}",
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SynergyGroupCard(
    group:       SynergyGroup,
    onCardClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(group.label, style = MaterialTheme.typography.titleSmall)
            Text(group.description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(group.cards.take(6)) { item ->
                    Column(
                        modifier            = Modifier.width(70.dp).clickable { onCardClick(item.card.scryfallId) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AsyncImage(
                            model              = item.card.imageArtCrop,
                            contentDescription = item.card.name,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier
                                .fillMaxWidth()
                                .aspectRatio(4f / 3f)
                                .clip(MaterialTheme.shapes.small),
                        )
                        Text(
                            text     = item.card.name,
                            style    = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySynergyState(modifier: Modifier = Modifier) {
    Column(
        modifier            = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Add more cards to see synergies", style = MaterialTheme.typography.titleMedium)
        Text("You need at least 10 cards", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}