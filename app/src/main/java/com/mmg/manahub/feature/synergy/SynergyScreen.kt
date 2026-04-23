package com.mmg.manahub.feature.synergy

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.MtgColor
import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.domain.model.UserCardWithCard

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
                title = { Text(stringResource(R.string.synergy_title)) },
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
                Text(stringResource(R.string.synergy_suggested_decks), style = MaterialTheme.typography.titleMedium)
            }
            items(uiState.suggestedDecks) { deck ->
                DeckSuggestionCard(suggestion = deck, onCardClick = onCardClick)
            }
        }

        // Synergy groups
        if (uiState.synergyGroups.isNotEmpty()) {
            item {
                Text(stringResource(R.string.synergy_groups), style = MaterialTheme.typography.titleMedium)
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
        Text(stringResource(R.string.deckbuilder_format_label), style = MaterialTheme.typography.titleSmall)
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
                        text     = stringResource(R.string.synergy_score_percent, suggestion.synergyScore),
                        style    = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text  = stringResource(R.string.synergy_deck_info, suggestion.cards.size, suggestion.format.name.lowercase()),
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
                                Text(stringResource(R.string.synergy_more_cards, (suggestion.cards.size - 8)),
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
        Text(stringResource(R.string.synergy_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.synergy_empty_subtitle), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true)
@Composable
private fun SynergyScreenPreview() {
    val mockCard = Card(
        scryfallId      = "1",
        name            = "Sol Ring",
        printedName     = null,
        manaCost        = "{1}",
        cmc             = 1.0,
        colors          = emptyList(),
        colorIdentity   = emptyList(),
        typeLine        = "Artifact",
        printedTypeLine = null,
        oracleText      = "{T}: Add {C}{C}.",
        printedText     = null,
        keywords        = emptyList(),
        power           = null,
        toughness       = null,
        loyalty         = null,
        setCode         = "LEA",
        setName         = "Limited Edition Alpha",
        collectorNumber = "232",
        rarity          = "rare",
        releasedAt      = "1993-08-05",
        frameEffects    = emptyList(),
        promoTypes      = emptyList(),
        lang            = "en",
        imageNormal     = null,
        imageArtCrop    = "https://cards.scryfall.io/art_crop/front/b/d/bd8fa327-dd41-4737-8f19-2cf5eb1f7cdd.jpg",
        imageBackNormal = null,
        priceUsd        = 1.0,
        priceUsdFoil    = null,
        priceEur        = 1.0,
        priceEurFoil    = null,
        legalityStandard = "legal",
        legalityPioneer  = "legal",
        legalityModern   = "legal",
        legalityCommander = "legal",
        flavorText      = null,
        artist          = "Mark Poole",
        scryfallUri     = "https://scryfall.com",
    )

    val mockUserCard = UserCardWithCard(
        userCard = UserCard(scryfallId = "1"),
        card     = mockCard
    )

    var selectedFormat by remember { mutableStateOf(DeckFormat.COMMANDER) }

    val uiState = remember(selectedFormat) {
        SynergyUiState(
            isLoading      = false,
            selectedFormat = selectedFormat,
            suggestedDecks = listOf(
                DeckSuggestion(
                    name         = "Artifact Ramp",
                    format       = DeckFormat.COMMANDER,
                    colors       = listOf(MtgColor.COLORLESS),
                    cards        = List(10) { mockUserCard },
                    coverCardId  = "1",
                    synergyScore = 95
                )
            ),
            synergyGroups = listOf(
                SynergyGroup(
                    label       = "Mana Rocks",
                    description = "Essential artifacts for accelerating your mana production in any deck.",
                    cards       = List(6) { mockUserCard }
                )
            )
        )
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                TopAppBar(title = { Text(stringResource(R.string.synergy_title)) })
            }
        ) { padding ->
            SynergyContent(
                uiState        = uiState,
                onFormatChange = { selectedFormat = it },
                onCardClick    = {},
                modifier       = Modifier.padding(padding)
            )
        }
    }
}
