package feature.stats


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import core.domain.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onCardClick: (scryfallId: String) -> Unit,
    viewModel:   StatsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Collection stats") })
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.stats != null -> StatsContent(
                stats      = uiState.stats!!,
                currency   = uiState.currency,
                onCurrencyToggle = viewModel::onCurrencyToggle,
                onCardClick = onCardClick,
                modifier   = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun StatsContent(
    stats:           CollectionStats,
    currency:        Currency,
    onCurrencyToggle: () -> Unit,
    onCardClick:     (String) -> Unit,
    modifier:        Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Summary KPIs
        SummarySection(stats = stats, currency = currency, onCurrencyToggle = onCurrencyToggle)

        // Most valuable cards
        MostValuableSection(cards = stats.mostValuableCards, currency = currency, onCardClick = onCardClick)

        // Color distribution
        DistributionSection(title = "By color", data = stats.byColor.entries.associate {
            it.key.displayName to it.value
        })

        // Type distribution
        DistributionSection(title = "By type", data = stats.byType.entries.associate {
            it.key.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it.value
        })

        // Rarity distribution
        DistributionSection(title = "By rarity", data = stats.byRarity.entries.associate {
            it.key.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it.value
        })

        // Mana curve
        ManaCurveSection(curve = stats.cmcDistribution)
    }
}

@Composable
private fun SummarySection(
    stats:           CollectionStats,
    currency:        Currency,
    onCurrencyToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Overview", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(label = "Total cards", value = stats.totalCards.toString(), modifier = Modifier.weight(1f))
            StatCard(label = "Unique cards", value = stats.uniqueCards.toString(), modifier = Modifier.weight(1f))
            StatCard(label = "Decks saved", value = stats.totalDecks.toString(), modifier = Modifier.weight(1f))
        }
        // Value card with currency toggle
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier              = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Collection value", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val value = if (currency == Currency.USD) stats.totalValueUsd else stats.totalValueEur
                    val symbol = if (currency == Currency.USD) "$" else "€"
                    Text(
                        text  = "$symbol${String.format("%.2f", value)}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                TextButton(onClick = onCurrencyToggle) {
                    Text(if (currency == Currency.USD) "Switch to EUR" else "Switch to USD")
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MostValuableSection(
    cards:      List<CardValue>,
    currency:   Currency,
    onCardClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Most valuable cards", style = MaterialTheme.typography.titleMedium)
        cards.forEachIndexed { index, card ->
            ListItem(
                modifier        = Modifier.clickable { onCardClick(card.scryfallId) },
                headlineContent = { Text(card.name) },
                leadingContent  = {
                    Text(
                        text  = "#${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    val symbol = if (currency == Currency.USD) "$" else "€"
                    Text(
                        text  = "$symbol${String.format("%.2f", card.priceUsd)}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                },
                supportingContent = if (card.isFoil) {
                    {
                        Text("Foil", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else null,
            )
            if (index < cards.lastIndex) HorizontalDivider(thickness = 0.5.dp)
        }
    }
}

@Composable
private fun DistributionSection(title: String, data: Map<String, Int>) {
    if (data.isEmpty()) return
    val total = data.values.sum().toFloat().coerceAtLeast(1f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        data.entries.sortedByDescending { it.value }.forEach { (label, count) ->
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.width(80.dp))
                LinearProgressIndicator(
                    progress = { count / total },
                    modifier = Modifier.weight(1f).height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text  = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(32.dp),
                )
            }
        }
    }
}

@Composable
private fun ManaCurveSection(curve: Map<Int, Int>) {
    if (curve.isEmpty()) return
    val maxCount = curve.values.maxOrNull()?.toFloat() ?: 1f
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mana curve", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier              = Modifier.fillMaxWidth().height(120.dp),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (0..7).forEach { cmc ->
                val count = curve[cmc] ?: 0
                val fraction = if (maxCount > 0) count / maxCount else 0f
                Column(
                    modifier            = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (count > 0) Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(fraction.coerceAtLeast(0.02f)),
                        color    = MaterialTheme.colorScheme.primary,
                        shape    = MaterialTheme.shapes.extraSmall,
                    ) {}
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = if (cmc == 7) "7+" else cmc.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

val MtgColor.displayName get() = when (this) {
    MtgColor.W          -> "White"
    MtgColor.U          -> "Blue"
    MtgColor.B          -> "Black"
    MtgColor.R          -> "Red"
    MtgColor.G          -> "Green"
    MtgColor.COLORLESS  -> "Colorless"
    MtgColor.MULTICOLOR -> "Multicolor"
}