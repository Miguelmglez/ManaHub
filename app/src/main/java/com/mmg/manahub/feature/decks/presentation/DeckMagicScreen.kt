package com.mmg.manahub.feature.decks.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.decks.presentation.engine.MagicCard
import com.mmg.manahub.feature.decks.presentation.engine.MagicDiscovery
import com.mmg.manahub.feature.decks.presentation.engine.MagicSuggestion

@Composable
fun DeckMagicScreen(
    viewModel: DeckMagicViewModel = hiltViewModel(),
    onCardClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val mc = MaterialTheme.magicColors

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        containerColor = mc.background,
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = mc.primaryAccent)
                        Spacer(Modifier.width(8.dp))
                        Text("Deck Magic Creator", style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
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
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStartEmpty,
                colors = CardDefaults.cardColors(containerColor = mc.surface)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Start from scratch", style = ty.titleMedium, color = mc.textPrimary)
                    Text("Choose your colors and strategy manually", style = ty.bodySmall, color = mc.textSecondary)
                }
            }
        }

        if (uiState.discoveries.isNotEmpty()) {
            item {
                Text("Magic Discoveries", style = ty.titleLarge, fontWeight = FontWeight.Bold, color = mc.textPrimary)
                Text("Based on your collection's strongest synergies", style = ty.bodyMedium, color = mc.textSecondary)
            }

            items(uiState.discoveries) { discovery ->
                DiscoveryCard(discovery = discovery, onClick = { onDiscoveryClick(discovery) })
            }
        }
    }
}

@Composable
private fun DiscoveryCard(discovery: MagicDiscovery, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = mc.surface)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(discovery.label, style = ty.titleMedium, color = mc.textPrimary)
            Text(discovery.description, style = ty.bodySmall, color = mc.textSecondary)
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
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Building your deck", style = ty.titleMedium, color = mc.textPrimary)
        Text("${uiState.mainboard.size} cards added", style = ty.bodySmall, color = mc.textSecondary)

        Spacer(Modifier.height(16.dp))

        Text("Suggestions", style = ty.labelMedium, color = mc.textPrimary)
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
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = mc.surface)
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
                Text(suggestion.magicCard.card.name, style = ty.bodyMedium, color = mc.textPrimary)
                Text(suggestion.reasons.take(2).joinToString(", "), style = ty.labelSmall, color = mc.textSecondary)
            }
            Text("${(suggestion.score * 100).toInt()}%", style = ty.titleMedium, color = mc.primaryAccent)
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

    com.mmg.manahub.core.ui.theme.MagicTheme {
        DashboardContent(
            uiState = uiState,
            onDiscoveryClick = {},
            onStartEmpty = {}
        )
    }
}












