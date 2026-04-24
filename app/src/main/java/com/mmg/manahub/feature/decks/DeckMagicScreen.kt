package com.mmg.manahub.feature.decks

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.R
import androidx.compose.ui.tooling.preview.Preview
import com.mmg.manahub.feature.decks.engine.MagicCard
import com.mmg.manahub.feature.decks.engine.MagicDiscovery
import com.mmg.manahub.feature.decks.engine.MagicSuggestion

@Composable
fun DeckMagicScreen(
    viewModel: DeckMagicViewModel = hiltViewModel(),
    onCardClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Deck Magic Creator") 
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = uiState.step,
            modifier = Modifier.padding(padding),
            label = "StepTransition"
        ) { step ->
            when (step) {
                DeckMagicStep.DASHBOARD -> DashboardContent(
                    uiState = uiState,
                    onDiscoveryClick = viewModel::startFromDiscovery,
                    onStartEmpty = { viewModel.setStep(DeckMagicStep.SETUP) }
                )
                DeckMagicStep.SETUP -> SetupContent()
                DeckMagicStep.BUILDING -> BuildingContent(
                    uiState = uiState,
                    onCardClick = onCardClick
                )
                DeckMagicStep.REVIEW -> Text("Review Mode")
            }
        }
    }
}

@Composable
private fun DashboardContent(
    uiState: DeckMagicUiState,
    onDiscoveryClick: (MagicDiscovery) -> Unit,
    onStartEmpty: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStartEmpty,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Start from scratch", style = MaterialTheme.typography.titleMedium)
                    Text("Choose your colors and strategy manually", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (uiState.discoveries.isNotEmpty()) {
            item {
                Text("Magic Discoveries", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Based on your collection's strongest synergies", style = MaterialTheme.typography.bodyMedium)
            }

            items(uiState.discoveries) { discovery ->
                DiscoveryCard(discovery = discovery, onClick = { onDiscoveryClick(discovery) })
            }
        }
    }
}

@Composable
private fun DiscoveryCard(discovery: MagicDiscovery, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(discovery.label, style = MaterialTheme.typography.titleMedium)
            Text(discovery.description, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(discovery.cards.take(6)) { magicCard ->
                    AsyncImage(
                        model = magicCard.card.imageArtCrop,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 60.dp, height = 44.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Setup Strategy & Colors")
    }
}

@Composable
private fun BuildingContent(
    uiState: DeckMagicUiState,
    onCardClick: (String) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Building your deck", style = MaterialTheme.typography.titleMedium)
        Text("${uiState.mainboard.size} cards added", style = MaterialTheme.typography.bodySmall)
        
        Spacer(Modifier.height(16.dp))
        
        Text("Suggestions", style = MaterialTheme.typography.titleSmall)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.currentSuggestions) { suggestion ->
                SuggestionItem(
                    suggestion = suggestion,
                    onClick = { onCardClick(suggestion.magicCard.card.scryfallId) }
                )
            }
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: MagicSuggestion,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = suggestion.magicCard.card.imageArtCrop,
                contentDescription = null,
                modifier = Modifier.size(50.dp).clip(MaterialTheme.shapes.small),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(suggestion.magicCard.card.name, style = MaterialTheme.typography.bodyMedium)
                Text(suggestion.reasons.take(2).joinToString(", "), style = MaterialTheme.typography.labelSmall)
            }
            Text("${(suggestion.score * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeckMagicScreenPreview() {
    val mockCard = com.mmg.manahub.core.domain.model.Card(
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

    val mockMagicCard = MagicCard(mockCard, isOwned = true)

    val uiState = DeckMagicUiState(
        step = DeckMagicStep.DASHBOARD,
        isLoading = false,
        discoveries = listOf(
            MagicDiscovery(
                label = "Elf Tribal",
                description = "12 cards with this tag in your collection",
                cards = List(6) { mockMagicCard },
                primaryTag = com.mmg.manahub.core.domain.model.CardTag.ELF
            )
        )
    )

    MaterialTheme {
        DashboardContent(
            uiState = uiState,
            onDiscoveryClick = {},
            onStartEmpty = {}
        )
    }
}
















