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
import androidx.compose.runtime.LaunchedEffect
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
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.decks.presentation.components.SeedsContent
import com.mmg.manahub.feature.decks.presentation.improvement.components.label
import com.mmg.manahub.feature.decks.domain.engine.DeckSkeletons
import com.mmg.manahub.feature.decks.domain.engine.MagicCard
import com.mmg.manahub.feature.decks.domain.engine.MagicDiscovery
import com.mmg.manahub.feature.decks.domain.engine.MagicSuggestion

@Composable
fun DeckMagicScreen(
    viewModel: DeckMagicViewModel = hiltViewModel(),
    onCardClick: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val mc = MaterialTheme.magicColors
    val toastState = rememberMagicToastState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DeckMagicEvent.Error -> toastState.show(event.message, MagicToastType.ERROR)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                        onStartEmpty = { viewModel.setStep(DeckMagicStep.SETUP) },
                        onStartFromSeeds = viewModel::startFromSeeds,
                    )
                    DeckMagicStep.SEEDS -> SeedsContent(
                        seedCards = uiState.seedCards,
                        identity = uiState.inferredIdentity,
                        skeleton = DeckSkeletons.forFormat(uiState.format.toDeckFormat()),
                        budget = uiState.budget,
                        query = uiState.seedQuery,
                        searchResults = uiState.seedSearchResults,
                        isSearching = uiState.isSearchingSeeds,
                        canGenerate = uiState.canGenerate,
                        isGenerating = uiState.isGenerating,
                        onQueryChange = viewModel::onSeedQueryChange,
                        onAddSeed = viewModel::addSeed,
                        onRemoveSeed = viewModel::removeSeed,
                        onBudgetChanged = viewModel::onBudgetChanged,
                        onGenerate = viewModel::generateFromSeeds,
                    )
                    DeckMagicStep.SETUP -> SetupContent()
                    DeckMagicStep.BUILDING -> BuildingContent(
                        uiState = uiState,
                        onCardClick = onCardClick
                    )
                    DeckMagicStep.REVIEW -> ReviewContent(uiState = uiState, onCardClick = onCardClick)
                }
            }
        }

        MagicToastHost(toastState)
    }
}

@Composable
private fun DashboardContent(
    uiState: DeckMagicUiState,
    onDiscoveryClick: (MagicDiscovery) -> Unit,
    onStartEmpty: () -> Unit,
    onStartFromSeeds: () -> Unit,
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
                onClick = onStartFromSeeds,
                colors = CardDefaults.cardColors(containerColor = mc.surface)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = mc.primaryAccent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Build from a seed card", style = ty.titleMedium, color = mc.textPrimary)
                        Text("Pick one or more cards and auto-generate a deck", style = ty.bodySmall, color = mc.textSecondary)
                    }
                }
            }
        }

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

            items(uiState.discoveries, key = { it.primaryTag.key }) { discovery ->
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
                items(discovery.cards.take(6), key = { it.card.scryfallId }) { magicCard ->
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

/**
 * REVIEW step. After a seed-based generation it renders the generated mainboard (non-land spells)
 * plus a note that the reserved land slots are filled by the standard basic-land flow.
 */
@Composable
private fun ReviewContent(
    uiState: DeckMagicUiState,
    onCardClick: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "review_header") {
            Column {
                Text("Generated deck", style = ty.titleLarge, fontWeight = FontWeight.Bold, color = mc.textPrimary)
                Text(
                    "${uiState.mainboard.size} spells + ${uiState.reservedLandSlots} lands (added by the land step)",
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        items(uiState.mainboard, key = { "mb_${it.card.scryfallId}" }) { magicCard ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onCardClick(magicCard.card.scryfallId) },
                colors = CardDefaults.cardColors(containerColor = mc.surface),
            ) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = magicCard.card.imageArtCrop,
                        contentDescription = null,
                        modifier = Modifier.size(width = 52.dp, height = 38.dp).clip(MaterialTheme.shapes.small),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(magicCard.card.name, style = ty.bodyMedium, color = mc.textPrimary)
                        Text(magicCard.card.typeLine, style = ty.labelSmall, color = mc.textSecondary)
                    }
                    if (!magicCard.isOwned) {
                        Text("New", style = ty.labelSmall, color = mc.goldMtg)
                    }
                }
            }
        }
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
            items(uiState.currentSuggestions, key = { it.magicCard.card.scryfallId }) { suggestion ->
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
                // E5: render localized ScoreReason labels (not raw enum names). label() is @Composable,
                // so resolve each reason's string inside the composition, then join the top two.
                val reasonLabels = suggestion.reasons.take(2).map { it.label() }
                Text(reasonLabels.joinToString(", "), style = ty.labelSmall, color = mc.textSecondary)
            }
            Text("${(suggestion.score * 100).toInt()}%", style = ty.titleMedium, color = mc.primaryAccent)
        }
    }
}














