package com.mmg.magicfolder.feature.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mmg.magicfolder.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mmg.magicfolder.core.domain.model.*
import com.mmg.magicfolder.core.ui.theme.MagicColors
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.core.util.LocaleLanguageProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onCardClick:     (scryfallId: String) -> Unit,
    onSettingsClick: () -> Unit = {},
    viewModel:       StatsViewModel = hiltViewModel(),
) {
    val uiState         by viewModel.uiState.collectAsStateWithLifecycle()
    val mc               = MaterialTheme.magicColors
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.refreshResult) {
        uiState.refreshResult?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearRefreshMessage()
        }
    }
    LaunchedEffect(uiState.refreshError) {
        uiState.refreshError?.let {
            snackbarHostState.showSnackbar("Error: $it")
            viewModel.clearRefreshMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text  = "Statistics",
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                    )
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = mc.textSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val stats = uiState.stats
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = mc.primaryAccent) }

            stats != null -> StatsContent(
                stats              = stats,
                currency           = uiState.currency,
                onCurrencyToggle   = viewModel::onCurrencyToggle,
                onCardClick        = onCardClick,
                isRefreshingPrices = uiState.isRefreshingPrices,
                refreshProgress    = uiState.refreshProgress,
                lastRefreshedAt    = uiState.lastRefreshedAt,
                onRefreshPrices    = viewModel::refreshPrices,
                modifier           = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun StatsContent(
    stats:              CollectionStats,
    currency:           Currency,
    onCurrencyToggle:   () -> Unit,
    onCardClick:        (String) -> Unit,
    isRefreshingPrices: Boolean,
    refreshProgress:    Pair<Int, Int>?,
    lastRefreshedAt:    Long?,
    onRefreshPrices:    () -> Unit,
    modifier:           Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SummarySection(
            stats              = stats,
            currency           = currency,
            onCurrencyToggle   = onCurrencyToggle,
            isRefreshingPrices = isRefreshingPrices,
            refreshProgress    = refreshProgress,
            lastRefreshedAt    = lastRefreshedAt,
            onRefreshPrices    = onRefreshPrices,
        )
        MostValuableSection(cards = stats.mostValuableCards, currency = currency, onCardClick = onCardClick)
        DistributionSection(title = "By color", data = stats.byColor.entries.associate {
            it.key.displayName to it.value
        })
        DistributionSection(title = "By type", data = stats.byType.entries.associate {
            it.key.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it.value
        })
        DistributionSection(title = "By rarity", data = stats.byRarity.entries.associate {
            it.key.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it.value
        })
        ManaCurveSection(curve = stats.cmcDistribution)
    }
}

@Composable
private fun SummarySection(
    stats:              CollectionStats,
    currency:           Currency,
    onCurrencyToggle:   () -> Unit,
    isRefreshingPrices: Boolean,
    refreshProgress:    Pair<Int, Int>?,
    lastRefreshedAt:    Long?,
    onRefreshPrices:    () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Overview",
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(label = "Total cards",  value = stats.totalCards.toString(),  modifier = Modifier.weight(1f))
            StatCard(label = "Unique cards", value = stats.uniqueCards.toString(), modifier = Modifier.weight(1f))
            StatCard(label = "Decks saved",  value = stats.totalDecks.toString(),  modifier = Modifier.weight(1f))
        }
        // Collection value card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors   = CardDefaults.cardColors(containerColor = mc.surfaceVariant),
        ) {
            Row(
                modifier              = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Collection value",
                        style = MaterialTheme.magicTypography.labelMedium,
                        color = mc.textSecondary,
                    )
                    val value  = if (currency == Currency.USD) stats.totalValueUsd else stats.totalValueEur
                    val symbol = if (currency == Currency.USD) "$" else "€"
                    Text(
                        text  = "$symbol${String.format("%.2f", value)}",
                        style = MaterialTheme.magicTypography.displayMedium,
                        color = mc.goldMtg,
                    )
                    if (lastRefreshedAt != null) {
                        Text(
                            text  = "Updated ${formatRelativeTime(lastRefreshedAt)}",
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = mc.textDisabled,
                        )
                    } else {
                        Text(
                            text  = "Prices may be outdated",
                            style = MaterialTheme.magicTypography.bodySmall,
                            color = mc.lifeNegative.copy(alpha = 0.7f),
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    // Currency toggle chips
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Currency.USD to "$", Currency.EUR to "€").forEach { (c, label) ->
                            val selected = currency == c
                            Surface(
                                color  = if (selected) mc.goldMtg.copy(alpha = 0.18f) else mc.surface,
                                shape  = MaterialTheme.shapes.extraSmall,
                                border = if (selected)
                                    BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.60f))
                                else
                                    BorderStroke(0.5.dp, mc.surfaceVariant),
                                modifier = Modifier.clickable { if (!selected) onCurrencyToggle() },
                            ) {
                                Text(
                                    text     = label,
                                    style    = MaterialTheme.magicTypography.labelSmall,
                                    color    = if (selected) mc.goldMtg else mc.textSecondary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    // Refresh button / spinner
                    if (isRefreshingPrices) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier    = Modifier.size(28.dp),
                                color       = mc.primaryAccent,
                                strokeWidth = 2.5.dp,
                            )
                            refreshProgress?.let { (current, total) ->
                                Text(
                                    text  = "$current/$total",
                                    style = MaterialTheme.magicTypography.labelSmall,
                                    color = mc.textDisabled,
                                )
                            }
                        }
                    } else {
                        IconButton(
                            onClick  = onRefreshPrices,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(mc.primaryAccent.copy(alpha = 0.1f)),
                        ) {
                            Icon(
                                imageVector        = Icons.Default.Refresh,
                                contentDescription = "Refresh prices",
                                tint               = mc.primaryAccent,
                                modifier           = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    Card(
        modifier = modifier,
        colors   = CardDefaults.cardColors(containerColor = mc.surfaceVariant),
    ) {
        Column(
            modifier            = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.magicTypography.titleLarge, color = mc.primaryAccent)
            Text(label, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
        }
    }
}

@Composable
private fun MostValuableSection(
    cards:       List<CardValue>,
    currency:    Currency,
    onCardClick: (String) -> Unit,
) {
    val isUs = remember { LocaleLanguageProvider().get() == "us" }

    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.stats_most_valuable), style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        cards.forEachIndexed { index, card ->
            ListItem(
                modifier        = Modifier.clickable { onCardClick(card.scryfallId) },
                colors          = ListItemDefaults.colors(containerColor = mc.surface),
                headlineContent = { Text(card.name, color = mc.textPrimary) },
                leadingContent  = {
                    Text(
                        text  = "#${index + 1}",
                        style = MaterialTheme.magicTypography.labelLarge,
                        color = mc.primaryAccent,
                    )
                },
                trailingContent = {
                    val symbol = if (currency == Currency.USD) "$" else "€"
                    val price  = if (currency == Currency.USD) card.priceUsd else card.priceUsd
                    Text(
                        text  = "$symbol${String.format("%.2f", price)}",
                        style = MaterialTheme.magicTypography.bodyLarge,
                        color = mc.goldMtg,
                    )
                },
                supportingContent = if (card.isFoil) {
                    { Text(stringResource(if (isUs) R.string.carddetail_price_foil_usd else R.string.carddetail_price_foil_eur)  , style = MaterialTheme.magicTypography.bodySmall, color = mc.goldMtg) }
                } else null,
            )
            if (index < cards.size - 1) HorizontalDivider(thickness = 0.5.dp, color = mc.surfaceVariant)
        }
    }
}

@Composable
private fun DistributionSection(title: String, data: Map<String, Int>) {
    if (data.isEmpty()) return
    val total = data.values.sum().toFloat().coerceAtLeast(1f)
    val mc    = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        data.entries.sortedByDescending { it.value }.forEach { (label, count) ->
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    label,
                    style    = MaterialTheme.magicTypography.bodySmall,
                    color    = mc.textSecondary,
                    modifier = Modifier.width(80.dp),
                )
                LinearProgressIndicator(
                    progress     = { count / total },
                    modifier     = Modifier.weight(1f).height(8.dp),
                    color        = mc.primaryAccent,
                    trackColor   = mc.surfaceVariant,
                )
                Text(
                    text     = count.toString(),
                    style    = MaterialTheme.magicTypography.labelSmall,
                    color    = mc.textDisabled,
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
    val mc       = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.stats_mana_curve), style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        Row(
            modifier              = Modifier.fillMaxWidth().height(120.dp),
            verticalAlignment     = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            (0..7).forEach { cmc ->
                val count    = curve[cmc] ?: 0
                val fraction = if (maxCount > 0) count / maxCount else 0f
                val barColor = cmcBarColor(cmc, mc)
                Column(
                    modifier            = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (count > 0) {
                        Text(
                            count.toString(),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().fillMaxHeight(fraction.coerceAtLeast(0.02f)),
                        color    = barColor,
                        shape    = MaterialTheme.shapes.extraSmall,
                    ) {}
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text  = if (cmc == 7) "7+" else cmc.toString(),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textDisabled,
                    )
                }
            }
        }
    }
}

private fun cmcBarColor(cmc: Int, mc: MagicColors): Color = when {
    cmc <= 1 -> mc.lifePositive
    cmc <= 3 -> mc.goldMtg
    cmc <= 5 -> mc.lifeNegative
    else     -> mc.primaryAccent
}

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L      -> "just now"
        diff < 3_600_000L   -> "${diff / 60_000} min ago"
        diff < 86_400_000L  -> "${diff / 3_600_000}h ago"
        else                -> "${diff / 86_400_000}d ago"
    }
}

val MtgColor.displayName get() = when (this) {
    MtgColor.W          -> "White"
    MtgColor.U          -> "Blue"
    MtgColor.B          -> "Black"
    MtgColor.R          -> "Red"
    MtgColor.G          -> "Green"
    MtgColor.COLORLESS  -> "Colorless"
    else                -> "Unknown"
}
