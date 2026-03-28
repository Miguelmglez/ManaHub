package com.mmg.magicfolder.feature.decks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.magicfolder.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.magicfolder.core.domain.model.Deck
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckDetailScreen(
    deckId:    Long,
    onBack:    () -> Unit,
    viewModel: DeckDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors

    Scaffold(
        containerColor = mc.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text  = uiState.deck?.name ?: "Deck",
                            style = MaterialTheme.magicTypography.titleMedium,
                            color = mc.textPrimary,
                        )
                        uiState.deck?.format?.let { fmt ->
                            Surface(
                                shape    = RoundedCornerShape(4.dp),
                                color    = mc.goldMtg.copy(alpha = 0.15f),
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                Text(
                                    text     = fmt.uppercase(),
                                    style    = MaterialTheme.magicTypography.labelSmall,
                                    color    = mc.goldMtg,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector        = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint               = mc.textSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }

            uiState.cards.isEmpty() -> Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = "Este mazo está vacío",
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = mc.textSecondary,
                )
            }

            else -> DeckContent(
                uiState  = uiState,
                onRemove = viewModel::removeCard,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Deck content (summary header + grouped card list)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DeckContent(
    uiState:  DeckDetailViewModel.UiState,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val deck       = uiState.deck ?: return
    val cardGroups = groupCardsByType(uiState.cards)
    val maxInCurve = uiState.manaCurve.values.maxOrNull() ?: 1

    LazyColumn(
        modifier            = modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            DeckSummaryCard(
                deck       = deck,
                totalCards = uiState.totalCards,
                manaCurve  = uiState.manaCurve,
                maxInCurve = maxInCurve,
            )
        }

        cardGroups.forEach { (typeName, cards) ->
            item(key = "header_$typeName") {
                TypeGroupHeader(
                    typeName = typeName,
                    count    = cards.sumOf { it.quantity },
                )
            }
            items(cards, key = { it.scryfallId }) { deckCard ->
                CardRow(
                    deckCard = deckCard,
                    onRemove = { onRemove(deckCard.scryfallId) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Deck summary card (totals + mana curve)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DeckSummaryCard(
    deck:       Deck,
    totalCards: Int,
    manaCurve:  Map<Int, Int>,
    maxInCurve: Int,
) {
    val mc          = MaterialTheme.magicColors
    val isCommander = deck.format.lowercase() == "commander"
    val targetCount = if (isCommander) 100 else 60

    Surface(shape = RoundedCornerShape(12.dp), color = mc.surface) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text  = "Total Cards",
                    style = MaterialTheme.magicTypography.labelMedium,
                    color = mc.textSecondary,
                )
                Text(
                    text  = "$totalCards / $targetCount",
                    style = MaterialTheme.magicTypography.titleMedium,
                    color = if (totalCards >= targetCount) mc.lifePositive else mc.textPrimary,
                )
            }
            if (totalCards < targetCount) {
                LinearProgressIndicator(
                    progress  = { totalCards.toFloat() / targetCount },
                    modifier  = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color     = mc.primaryAccent,
                    trackColor = mc.surfaceVariant,
                )
            }
            if (manaCurve.isNotEmpty()) {
                Text(
                    text  = "MANA CURVE",
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textSecondary,
                )
                ManaCurveBar(manaCurve = manaCurve, maxInCurve = maxInCurve)
            }
        }
    }
}

@Composable
private fun ManaCurveBar(manaCurve: Map<Int, Int>, maxInCurve: Int) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier              = Modifier.fillMaxWidth().height(56.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment     = Alignment.Bottom,
    ) {
        (0..7).forEach { cmc ->
            val count    = manaCurve[cmc] ?: 0
            val fraction = if (maxInCurve > 0) count.toFloat() / maxInCurve else 0f
            Column(
                modifier            = Modifier.weight(1f).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (count > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(mc.primaryAccent.copy(alpha = 0.5f + 0.5f * fraction)),
                    )
                }
                Text(
                    text  = if (cmc == 7) "7+" else cmc.toString(),
                    style = MaterialTheme.magicTypography.labelSmall,
                    color = mc.textDisabled,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Type group header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TypeGroupHeader(typeName: String, count: Int) {
    val mc = MaterialTheme.magicColors
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text  = typeName,
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.goldMtg,
        )
        Text(
            text  = "($count)",
            style = MaterialTheme.magicTypography.bodyMedium,
            color = mc.textSecondary,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Card row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CardRow(
    deckCard: DeckCard,
    onRemove: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Surface(shape = RoundedCornerShape(8.dp), color = mc.surface) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model              = deckCard.card?.imageNormal,
                contentDescription = deckCard.card?.name ?: deckCard.scryfallId,
                modifier           = Modifier
                    .size(width = 44.dp, height = 60.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(mc.surfaceVariant),
                contentScale       = ContentScale.Crop,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = deckCard.card?.name ?: deckCard.scryfallId,
                    style    = MaterialTheme.magicTypography.bodyMedium,
                    color    = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                deckCard.card?.typeLine?.let { type ->
                    Text(
                        text     = type,
                        style    = MaterialTheme.magicTypography.bodySmall,
                        color    = mc.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                deckCard.card?.manaCost?.let { cost ->
                    Text(
                        text  = cost,
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                    )
                }
            }

            if (deckCard.quantity > 1) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = mc.primaryAccent.copy(alpha = 0.2f),
                ) {
                    Text(
                        text     = "\u00d7${deckCard.quantity}",
                        style    = MaterialTheme.magicTypography.labelMedium,
                        color    = mc.primaryAccent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            IconButton(
                onClick  = onRemove,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector        = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_remove),
                    tint               = mc.textDisabled,
                    modifier           = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun groupCardsByType(cards: List<DeckCard>): List<Pair<String, List<DeckCard>>> {
    val typeOrder = listOf(
        "Creatures", "Instants", "Sorceries",
        "Enchantments", "Artifacts", "Planeswalkers", "Lands", "Other",
    )
    val groups = mutableMapOf<String, MutableList<DeckCard>>()

    cards.forEach { deckCard ->
        val typeLine = deckCard.card?.typeLine ?: ""
        val group = when {
            typeLine.contains("Creature",     ignoreCase = true) -> "Creatures"
            typeLine.contains("Instant",      ignoreCase = true) -> "Instants"
            typeLine.contains("Sorcery",      ignoreCase = true) -> "Sorceries"
            typeLine.contains("Enchantment",  ignoreCase = true) -> "Enchantments"
            typeLine.contains("Artifact",     ignoreCase = true) -> "Artifacts"
            typeLine.contains("Planeswalker", ignoreCase = true) -> "Planeswalkers"
            typeLine.contains("Land",         ignoreCase = true) -> "Lands"
            else                                                   -> "Other"
        }
        groups.getOrPut(group) { mutableListOf() }.add(deckCard)
    }

    return typeOrder
        .filter { it in groups }
        .map { it to (groups[it]!! as List<DeckCard>) }
}
