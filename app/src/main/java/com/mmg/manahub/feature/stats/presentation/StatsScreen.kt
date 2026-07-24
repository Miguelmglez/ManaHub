package com.mmg.manahub.feature.stats.presentation

// TODO: Re-enable when Survey review for games is fully implemented
//import com.mmg.manahub.core.data.local.entity.SurveyStatus
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import coil3.compose.AsyncImage
import coil3.svg.SvgDecoder
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.mmg.manahub.R
import com.mmg.manahub.core.model.CardValue
import com.mmg.manahub.core.model.CollectionStats
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.model.MtgColor
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.CircularDistribution
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicAlertDialog
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaColorPicker
import com.mmg.manahub.core.ui.components.ManaCurveChart
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.search.SetPickerSheet
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.core.util.TimeAgoFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun StatsScreen(
    onCardClick:      (scryfallId: String, sharedTransitionKey: String?) -> Unit,
    onBackClick:      () -> Unit = {},
    onReviewSurvey:   (sessionId: Long) -> Unit = {},
    onDeckClick:      (deckId: String) -> Unit = {},
    sharedTransitionScope:   SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    // KMP migration — Phase 1 Hilt→Koin cutover: Stats is the second "Koin island". This ViewModel is
    // resolved by Koin (koinViewModel()) while every other screen still uses hiltViewModel().
    viewModel:        StatsViewModel = koinViewModel(),
) {
    val uiState    by viewModel.uiState.collectAsStateWithLifecycle()
    val mc          = MaterialTheme.magicColors
    val toastState  = rememberMagicToastState()

    LaunchedEffect(uiState.refreshResult) {
        uiState.refreshResult?.let {
            toastState.show(it, MagicToastType.SUCCESS)
            viewModel.clearRefreshMessage()
        }
    }
    LaunchedEffect(uiState.refreshError) {
        uiState.refreshError?.let {
            toastState.show("Error: $it", MagicToastType.ERROR)
            viewModel.clearRefreshMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text  = stringResource(R.string.stats_title),
                            style = MaterialTheme.magicTypography.titleLarge,
                            color = mc.textPrimary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back), tint = mc.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
                )
            },
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                // Tab row is shown whenever more than just Collection is available — GAMES gates
                // on hasGameStats (zero-session pattern, unchanged), TRADES gates on hasTradeStats
                // (Phase 4: at least one COMPLETED trade proposal exists for the local user).
                val visibleTabs = buildList {
                    add(StatsTab.COLLECTION)
                    if (uiState.hasGameStats) add(StatsTab.GAMES)
                    if (uiState.hasTradeStats) add(StatsTab.TRADES)
                }
                if (visibleTabs.size > 1) {
                    val tabLabels = visibleTabs.map { tab ->
                        when (tab) {
                            StatsTab.COLLECTION -> stringResource(R.string.stats_tab_collection)
                            StatsTab.GAMES      -> stringResource(R.string.stats_tab_games)
                            StatsTab.TRADES     -> stringResource(R.string.stats_tab_trades)
                        }
                    }
                    // Tab display order is NOT the enum's ordinal order once a tab can be
                    // conditionally absent — always resolve the selected index by looking up the
                    // current tab within the actually-visible list, never StatsTab.entries[index].
                    val selectedIndex = visibleTabs.indexOf(uiState.selectedTab).coerceAtLeast(0)
                    TabRow(
                        selectedTabIndex   = selectedIndex,
                        containerColor     = mc.backgroundSecondary.copy(alpha = 0.9f),
                        contentColor       = mc.primaryAccent,
                        divider            = {},
                    ) {
                        visibleTabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = index == selectedIndex,
                                onClick  = { viewModel.onTabSelected(tab) },
                                text     = {
                                    Text(
                                        text  = tabLabels[index].uppercase(Locale.getDefault()),
                                        style = MaterialTheme.magicTypography.labelMedium,
                                    )
                                },
                            )
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = mc.surfaceVariant.copy(alpha = 0.5f))
                }

                when {
                    uiState.hasGameStats && uiState.selectedTab == StatsTab.GAMES ->
                        GameStatsContent(
                            uiState        = uiState,
                            toastState     = toastState,
                            onReviewSurvey = onReviewSurvey,
                            onDeckClick    = onDeckClick,
                            onDeleteSession = viewModel::deleteSession,
                            onClearDeleteMessage = viewModel::clearDeleteSessionMessage,
                        )
                    uiState.hasTradeStats && uiState.selectedTab == StatsTab.TRADES ->
                        TradeStatsContent(
                            state    = uiState.tradeStats,
                            currency = uiState.currency,
                            onRetry  = viewModel::retryTradeStats,
                        )
                    else ->
                        CollectionStatsContent(
                            uiState          = uiState,
                            onColorSelected  = viewModel::onColorSelected,
                            onSetSelected    = viewModel::onSetSelected,
                            onRefreshPrices  = viewModel::refreshPrices,
                            onCardClick      = onCardClick,
                            sharedTransitionScope   = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                }
            }
        }

        MagicToastHost(state = toastState)
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CollectionStatsContent(
    uiState: StatsUiState,
    onColorSelected: (MtgColor?) -> Unit,
    onSetSelected: (MagicSet?) -> Unit,
    onRefreshPrices: () -> Unit,
    onCardClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    val stats = uiState.stats
    var showSetPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(sp.xl),
    ) {
        // Refresh-prices affordance (Phase 1 audit fix: previously wired but never rendered).
        /*Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sp.xl).padding(top = sp.lg),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val statusText = when {
                uiState.isRefreshingPrices && uiState.refreshProgress != null ->
                    stringResource(R.string.stats_refresh_progress, uiState.refreshProgress.first, uiState.refreshProgress.second)
                uiState.isRefreshingPrices -> stringResource(R.string.stats_refreshing)
                uiState.lastRefreshedAt != null -> "Updated ${TimeAgoFormatter.format(uiState.lastRefreshedAt)}"
                else -> null
            }
            Text(
                text = statusText ?: "",
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textDisabled,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onRefreshPrices,
                enabled = !uiState.isRefreshingPrices,
                modifier = Modifier.size(48.dp),
            ) {
                if (uiState.isRefreshingPrices) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = mc.primaryAccent)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.stats_refresh_prices), tint = mc.primaryAccent)
                }
            }
        }*/

        // Color Filter Row
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sp.xl, vertical = sp.sm),
            verticalArrangement = Arrangement.spacedBy(sp.lg)
        ) {
            ManaColorPicker(
                selectedColors = uiState.selectedColor?.let { setOf(it.name.take(1)) } ?: emptySet(),
                onToggleColor = { colorCode ->
                    val color = when (colorCode) {
                        "W" -> MtgColor.W
                        "U" -> MtgColor.U
                        "B" -> MtgColor.B
                        "R" -> MtgColor.R
                        "G" -> MtgColor.G
                        "C" -> MtgColor.COLORLESS
                        else -> null
                    }
                    onColorSelected(color)
                },
                modifier = Modifier.fillMaxWidth(),
                spacing = sp.sm,
                itemSize = 48.dp,
                symbolSize = 32.dp,
            )

            SetFilterRow(
                selectedSet = uiState.selectedSet,
                onClick = { showSetPicker = true },
                onClear = { onSetSelected(null) },
                mc = mc
            )
        }

        if (showSetPicker) {
            SetPickerSheet(
                selectedSetCodes = uiState.selectedSet?.let { setOf(it.code) } ?: emptySet(),
                onToggleSet = { set -> 
                    onSetSelected(if (uiState.selectedSet?.code == set.code) null else set)
                    showSetPicker = false
                },
                onDismiss = { showSetPicker = false },
                availableSets = uiState.availableSets,
                singleSelection = true
            )
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }
        } else if (stats != null) {
            Column(
                modifier = Modifier.padding(horizontal = sp.lg),
                verticalArrangement = Arrangement.spacedBy(sp.xxl)
            ) {
                // 1. Inventory & Value
                StatsSection(
                    title = stringResource(R.string.stats_section_inventory_value),
                    icon = Icons.Default.Star
                ) {
                    InventoryValueGrid(stats = stats, currency = uiState.currency)
                }

                // 2. Combat & Mechanics
                StatsSection(
                    title = stringResource(R.string.stats_section_combat_mechanics),
                    icon = painterResource(R.drawable.ic_battle)
                ) {
                    CombatMechanicsSection(stats = stats)
                }

                // 3. Aesthetics & Art
                StatsSection(
                    title = stringResource(R.string.stats_section_aesthetics_art),
                    icon = Icons.Default.Star
                ) {
                    AestheticsArtSection(
                        stats = stats,
                        onCardClick = onCardClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }

                // 4. Distributions
                StatsSection(
                    title = stringResource(R.string.stats_section_distributions),
                    icon = Icons.Default.BarChart
                ) {
                    DistributionsSection(stats = stats, mc = mc)
                }

                // 5. Hall of Fame
                StatsSection(
                    title = stringResource(R.string.stats_section_hall_of_fame),
                    icon = Icons.Default.Star
                ) {
                    HallOfFameSection(
                        stats = stats,
                        currency = uiState.currency,
                        onCardClick = onCardClick,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }

                // 6. Set Completion (Phase 2) — global/unfiltered, top sets by completion ratio.
                if (uiState.setCompletions.isNotEmpty()) {
                    StatsSection(
                        title = stringResource(R.string.stats_section_set_completion),
                        icon = Icons.Default.Layers
                    ) {
                        SetCompletionSection(completions = uiState.setCompletions)
                    }
                }

                // 7. Collection Era (2026-07 stats expansion) — replaces the retired
                // month-by-month Collection Growth chart; owned card quantity by release decade.
                // CircularDistributionSection is self-titled/self-carded, so it is placed directly
                // rather than nested inside another StatsSection header.
                if (stats.decadeDistribution.isNotEmpty()) {
                    val decadeOrder = remember(stats.decadeDistribution) { stats.decadeDistribution.keys.sorted() }
                    CircularDistributionSection(
                        title = stringResource(R.string.stats_label_collection_era),
                        data = stats.decadeDistribution,
                        colorMapper = { label ->
                            val index = decadeOrder.indexOf(label).coerceAtLeast(0)
                            val palette = listOf(
                                mc.primaryAccent, mc.secondaryAccent, mc.goldMtg,
                                mc.lifePositive, mc.lifeNegative, mc.manaU,
                                mc.manaR, mc.manaG, mc.manaW,
                            )
                            palette[index % palette.size]
                        }
                    )
                }

                Spacer(Modifier.height(sp.xxl))
            }
        }
    }
}

@Composable
private fun SetFilterRow(
    selectedSet: MagicSet?,
    onClick: () -> Unit,
    onClear: () -> Unit,
    mc: MagicColors
) {
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = mc.surface,
        border = BorderStroke(
            width = if (selectedSet != null) 1.5.dp else 0.5.dp,
            color = if (selectedSet != null) mc.primaryAccent else mc.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = sp.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selectedSet != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(selectedSet.iconSvgUri)
                        .decoderFactory(SvgDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(mc.primaryAccent),
                )
            } else {
                Icon(
                    Icons.Default.Layers,
                    contentDescription = null,
                    tint = mc.textDisabled,
                    modifier = Modifier.size(20.dp),
                )
            }
            
            Text(
                text = selectedSet?.name ?: stringResource(R.string.advsearch_set_hint),
                style = ty.bodyMedium,
                color = if (selectedSet != null) mc.primaryAccent else mc.textDisabled,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (selectedSet != null) {
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = mc.textDisabled, modifier = Modifier.size(16.dp))
                }
            } else {
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = mc.textDisabled,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun StatsSection(
    title: String,
    icon: Any, // ImageVector or Painter
    content: @Composable () -> Unit
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(sp.lg)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(sp.md),
            modifier = Modifier.fillMaxWidth()
        ) {
            when (icon) {
                is androidx.compose.ui.graphics.vector.ImageVector -> Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(24.dp)
                )
                is androidx.compose.ui.graphics.painter.Painter -> Icon(
                    painter = icon,
                    contentDescription = null,
                    tint = mc.primaryAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = title.uppercase(),
                style = MaterialTheme.magicTypography.labelLarge,
                color = mc.textPrimary,
                letterSpacing = 2.sp
            )
        }
        content()
    }
}

@Composable
private fun InventoryValueGrid(stats: CollectionStats, currency: PreferredCurrency) {
    val sp = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(sp.md)) {
            StatCard(
                label = stringResource(R.string.stats_total_cards),
                value = stats.totalCards.toString(),
                modifier = Modifier.weight(1f).height(90.dp)
            )
            StatCard(
                label = stringResource(R.string.stats_unique_cards),
                value = stats.uniqueCards.toString(),
                modifier = Modifier.weight(1f).height(90.dp)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(sp.md)) {
            val totalValue = if (currency == PreferredCurrency.USD) stats.totalValueUsd else stats.totalValueEur
            CurrencyStatCard(
                label = stringResource(R.string.stats_label_est_value),
                value = PriceFormatter.format(totalValue, currency),
                modifier = Modifier.weight(1f).height(90.dp)
            )
            StatCard(
                label = stringResource(R.string.stats_decks_saved),
                value = stats.totalDecks.toString(),
                modifier = Modifier.weight(1f).height(90.dp)
            )
        }
        // Phase 2 (2026-07 stats expansion) — avg/median card value, active currency.
        Row(horizontalArrangement = Arrangement.spacedBy(sp.md)) {
            CurrencyStatCard(
                label = stringResource(R.string.stats_label_avg_card_value),
                value = PriceFormatter.format(stats.avgCardValue, currency),
                modifier = Modifier.weight(1f).height(90.dp)
            )
            CurrencyStatCard(
                label = stringResource(R.string.stats_label_median_card_value),
                value = PriceFormatter.format(stats.medianCardValue, currency),
                modifier = Modifier.weight(1f).height(90.dp)
            )
        }
    }
}

/** Currency-value variant of [StatCard]: smaller/gold value text, single line, ellipsized. */
@Composable
private fun CurrencyStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = mc.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(sp.lg).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.magicTypography.titleLarge.copy(fontSize = 20.sp),
                color = mc.goldMtg,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CombatMechanicsSection(stats: CollectionStats) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    val manaGradient = Brush.linearGradient(
        colors = listOf(mc.manaW, mc.manaU, mc.manaB, mc.manaR, mc.manaG)
    )

    Column(verticalArrangement = Arrangement.spacedBy(sp.lg)) {
        Row(horizontalArrangement = Arrangement.spacedBy(sp.md), verticalAlignment = Alignment.CenterVertically) {
            CombatStatsBox(
                avgPower = stats.avgPower ?: 0.0,
                avgToughness = stats.avgToughness ?: 0.0,
                modifier = Modifier.weight(1.2f)
            )
            
            Card(
                modifier = Modifier.weight(0.8f).height(100.dp),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = mc.surfaceVariant),
                border = BorderStroke(1.dp, manaGradient)
            ) {
                Column(
                    modifier = Modifier.padding(sp.md).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "%.2f".format(stats.avgManaValue),
                        style = MaterialTheme.magicTypography.titleLarge.copy(fontSize = 24.sp),
                        color = mc.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = stringResource(R.string.stats_label_avg_mana_value),
                        style = MaterialTheme.magicTypography.labelMedium,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        ManaCurveChart(
            cmcDistribution = stats.cmcDistribution,
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.stats_mana_curve)
        )
    }
}

@Composable
private fun CombatStatsBox(avgPower: Double, avgToughness: Double, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    Card(
        modifier = modifier.height(100.dp),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = mc.surfaceVariant),
        border = BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.5f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = sp.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_battle),
                        contentDescription = null,
                        tint = mc.secondaryAccent.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "%.1f".format(avgPower),
                        style = MaterialTheme.magicTypography.displayMedium.copy(fontSize = 24.sp),
                        color = mc.textPrimary
                    )
                    Text(
                        text = "AVG\nPOWER",
                        style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                
                Text(
                    text = "/",
                    style = MaterialTheme.magicTypography.displayMedium.copy(fontSize = 24.sp),
                    color = mc.goldMtg,
                    modifier = Modifier.padding(horizontal = sp.sm)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = mc.goldMtg.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "%.1f".format(avgToughness),
                        style = MaterialTheme.magicTypography.displayMedium.copy(fontSize = 24.sp),
                        color = mc.textPrimary
                    )
                    Text(
                        text = "AVG\nTOUGHNESS",
                        style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AestheticsArtSection(
    stats: CollectionStats,
    onCardClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(sp.lg)) {
        AestheticStatCard(
            label = stringResource(R.string.stats_label_foil_cards),
            value = stats.totalFoil.toString(),
            percentage = if (stats.totalCards > 0) (stats.totalFoil.toFloat() / stats.totalCards) else 0f,
            isFoil = true,
            modifier = Modifier.fillMaxWidth()
        )

        CuriousStatBox(
            label = stringResource(R.string.stats_label_top_artist),
            value = stats.topArtist ?: stringResource(R.string.stats_not_available),
            supportingText = if (stats.topArtistCount > 0) stringResource(R.string.stats_artist_cards_count, stats.topArtistCount) else null,
            mc = mc,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Hall of Fame enrichment (2026-07 stats expansion) — gallery of the top artist's owned cards.
            if (stats.topArtistCards.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(sp.md),
                    contentPadding = PaddingValues(horizontal = sp.xxs),
                    modifier = Modifier.padding(top = sp.md)
                ) {
                    items(stats.topArtistCards, key = { it.scryfallId }) { card ->
                        val key = "stats-artist-${card.scryfallId}"
                        ArtistCardTile(
                            card = card,
                            onClick = { onCardClick(card.scryfallId, key) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedTransitionKey = key,
                        )
                    }
                }
            }
        }
    }
}

/** Full-card-image tile (not art-crop) for the Top Artist gallery row (Hall of Fame enrichment). */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ArtistCardTile(
    card: CardValue,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedTransitionKey: String,
) {
    val mc = MaterialTheme.magicColors
    val imageModifier = Modifier
        .width(100.dp)
        .aspectRatio(0.717f) // full MTG card aspect ratio (745:1040)
        .clip(CardShape)

    val finalImageModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            imageModifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = sharedTransitionKey),
                animatedVisibilityScope = animatedVisibilityScope,
                clipInOverlayDuringTransition = OverlayClip(CardShape),
                boundsTransform = { _, _ -> tween(durationMillis = 500, easing = FastOutSlowInEasing) },
                renderInOverlayDuringTransition = true,
            )
        }
    } else imageModifier

    AsyncImage(
        model = card.imageNormal,
        contentDescription = card.name,
        contentScale = ContentScale.Fit,
        modifier = finalImageModifier
            .background(mc.surfaceVariant)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun AestheticStatCard(
    label: String,
    value: String,
    percentage: Float,
    isFoil: Boolean,
    modifier: Modifier = Modifier
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    
    val foilColors = listOf(
        Color(0xFF00E5FF),
        Color(0xFFBF00FF),
        Color(0xFFFFD700),
        Color(0xFF00FFCC)
    )
    val foilBrush = Brush.linearGradient(colors = foilColors)

    Card(
        modifier = modifier.height(115.dp),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = mc.surface),
        border = if (isFoil) BorderStroke(1.5.dp, foilBrush) else BorderStroke(1.dp, mc.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isFoil) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(colors = foilColors.map { it.copy(alpha = 0.12f) }))
                )
            }
            
            Column(Modifier.padding(sp.lg).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = label.uppercase(), 
                        style = MaterialTheme.magicTypography.labelSmall, 
                        color = mc.textSecondary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.magicTypography.displayMedium.copy(fontSize = 32.sp),
                        color = mc.textPrimary
                    )
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            text = "${(percentage * 100).toInt()}% " + stringResource(R.string.stats_label_total).lowercase(), 
                            style = MaterialTheme.magicTypography.labelSmall, 
                            color = mc.textSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(mc.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percentage)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(if (isFoil) foilBrush else Brush.horizontalGradient(listOf(mc.primaryAccent, mc.secondaryAccent)))
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HallOfFameSection(
    stats: CollectionStats,
    currency: PreferredCurrency,
    onCardClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(sp.xl)) {
        // Oldest / Newest
        Row(horizontalArrangement = Arrangement.spacedBy(sp.lg)) {
            stats.oldestCard?.let { card ->
                val key = "stats-oldest-${card.scryfallId}"
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                    HistoryCard(
                        card = card, currency = currency, mc = mc,
                        onCardClick = { onCardClick(card.scryfallId, key) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedTransitionKey = key,
                    )
                    Text(stringResource(R.string.stats_label_oldest_card), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            stats.newestCard?.let { card ->
                val key = "stats-newest-${card.scryfallId}"
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                    HistoryCard(
                        card = card, currency = currency, mc = mc,
                        onCardClick = { onCardClick(card.scryfallId, key) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedTransitionKey = key,
                    )
                    Text(stringResource(R.string.stats_label_newest_card), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Phase 2 (2026-07 stats expansion) — most-duplicated card, half-width tile.
        // Hall of Fame enrichment — most-DISTINCT-variants card fills the other half.
        if (stats.mostDuplicatedCard != null || stats.mostVariantsCard != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(sp.lg)) {
                stats.mostDuplicatedCard?.let { card ->
                    val key = "stats-mostdup-${card.scryfallId}"
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                        HallOfFameCardTile(
                            card = card,
                            badgeText = "×${stats.mostDuplicatedCardCount} " + stringResource(R.string.stats_label_owned),
                            mc = mc,
                            onCardClick = { onCardClick(card.scryfallId, key) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedTransitionKey = key,
                            badgeIcon = Icons.Default.Style,
                            badgeIconTint = mc.primaryAccent,
                        )
                        Text(stringResource(R.string.stats_label_most_duplicated_card), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                } ?: Spacer(modifier = Modifier.weight(1f))

                stats.mostVariantsCard?.let { card ->
                    val key = "stats-variants-${card.scryfallId}"
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                        HallOfFameCardTile(
                            card = card,
                            badgeText = stringResource(R.string.stats_variants_count, stats.mostVariantsCount),
                            mc = mc,
                            onCardClick = { onCardClick(card.scryfallId, key) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedTransitionKey = key,
                            badgeIcon = Icons.Default.Layers,
                            badgeIconTint = mc.secondaryAccent,
                        )
                        Text(stringResource(R.string.stats_label_most_variants_card), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                } ?: Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Phase 2 — top-10 value concentration KPI, near the most-valuable list.
        if (stats.mostValuableCards.isNotEmpty()) {
            CuriousStatBox(
                label = stringResource(R.string.stats_label_value_concentration),
                value = "${(stats.valueConcentrationTop10Percent * 100).toInt()}%",
                percentage = stats.valueConcentrationTop10Percent,
                supportingText = stringResource(R.string.stats_value_concentration_supporting),
                mc = mc,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        MostValuableSection(
            cards = stats.mostValuableCards,
            currency = currency,
            onCardClick = onCardClick,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )

        SetStatsSection(stats = stats, currency = currency, mc = mc)
    }
}

/**
 * Generic Hall of Fame card tile (art-crop image + name + set + a caller-supplied badge line).
 * Backs BOTH the most-duplicated-card and most-variants-card stats (2026-07 stats expansion) —
 * only the badge text differs between the two axes.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HallOfFameCardTile(
    card: CardValue,
    badgeText: String,
    mc: MagicColors,
    onCardClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedTransitionKey: String,
    modifier: Modifier = Modifier,
    badgeIcon: ImageVector? = null,
    badgeIconTint: Color = mc.textPrimary,
) {
    val sp = MaterialTheme.spacing
    Card(
        modifier = modifier.clickable { onCardClick() },
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = mc.surface),
    ) {
        Column {
            val imageModifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
            val finalImageModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    imageModifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(key = sharedTransitionKey),
                        animatedVisibilityScope = animatedVisibilityScope,
                        clipInOverlayDuringTransition = OverlayClip(CardShape),
                        boundsTransform = { _, _ -> tween(durationMillis = 500, easing = FastOutSlowInEasing) },
                        renderInOverlayDuringTransition = true,
                    )
                }
            } else imageModifier

            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = null,
                modifier = finalImageModifier,
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(sp.md)) {
                CardName(card.name, style = MaterialTheme.magicTypography.labelMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)

                Spacer(Modifier.height(sp.sm))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(sp.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SetSymbol(setCode = card.setCode, rarity = CardRarity.fromString(card.rarity), size = 14.dp)
                    Text(
                        text = card.setName,
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.secondaryAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(sp.md))

                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (badgeIcon != null) {
                        Icon(
                            imageVector = badgeIcon,
                            contentDescription = null,
                            tint = badgeIconTint,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Text(
                        text = badgeText,
                        style = MaterialTheme.magicTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = mc.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DistributionsSection(stats: CollectionStats, mc: MagicColors) {
    val context = LocalContext.current
    val whiteName = stringResource(R.string.stats_color_white)
    val blueName = stringResource(R.string.stats_color_blue)
    val blackName = stringResource(R.string.stats_color_black)
    val redName = stringResource(R.string.stats_color_red)
    val greenName = stringResource(R.string.stats_color_green)
    val colorlessName = stringResource(R.string.stats_color_colorless)

    val colorData = remember(stats.byColor, whiteName, blueName, blackName, redName, greenName, colorlessName) {
        stats.byColor.entries.associate { (color, count) ->
            val name = when (color) {
                MtgColor.W -> whiteName
                MtgColor.U -> blueName
                MtgColor.B -> blackName
                MtgColor.R -> redName
                MtgColor.G -> greenName
                MtgColor.COLORLESS -> colorlessName
                else -> context.getString(R.string.stats_color_unknown)
            }
            name to count
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        CircularDistributionSection(
            title = stringResource(R.string.stats_dist_color),
            data = colorData,
            colorMapper = { label ->
                when (label) {
                    whiteName -> Color(0xFFF9FAFA)
                    blueName -> Color(0xFF0E68AB)
                    blackName -> Color(0xFF150B00)
                    redName -> Color(0xFFD3202A)
                    greenName -> Color(0xFF00733E)
                    colorlessName -> Color(0xFF90ADBB)
                    else -> mc.primaryAccent
                }
            },
            isColor = true
        )
        CircularDistributionSection(
            title = stringResource(R.string.stats_dist_type),
            data = stats.byType.entries.associate { it.key.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it.value },
            colorMapper = { label ->
                val types = listOf("Creature", "Instant", "Sorcery", "Enchantment", "Artifact", "Planeswalker", "Land", "Battle", "Other")
                val index = types.indexOf(label).coerceAtLeast(0)
                val palette = listOf(
                    mc.primaryAccent, mc.secondaryAccent, mc.goldMtg,
                    mc.lifePositive, mc.lifeNegative, Color(0xFF9B6EFF),
                    Color(0xFFE8A030), Color(0xFFC0C0C0), Color(0xFFB0C4DE),
                    Color(0xFFFF6AD5), Color(0xFF00E5FF), Color(0xFFBF00FF)
                )
                palette[index % palette.size]
            }
        )
        CircularDistributionSection(
            title = stringResource(R.string.stats_dist_rarity),
            data = stats.byRarity.entries.associate { it.key.name.lowercase().replaceFirstChar { c -> c.uppercase() } to it.value },
            colorMapper = { label ->
                when (label.lowercase()) {
                    "common" -> Color(0xFFC0C0C0)
                    "uncommon" -> Color(0xFFB0C4DE)
                    "rare" -> Color(0xFFC9A84C)
                    "mythic" -> Color(0xFFE8A030)
                    else -> Color(0xFF9B6EFF)
                }
            }
        )

        DistributionSection(title = stringResource(R.string.stats_dist_strategy), data = stats.autoTagDistribution)

        // Phase 2 (2026-07 stats expansion)
        DistributionSection(title = stringResource(R.string.stats_dist_keywords), data = stats.keywordDistribution)
        FormatCoverageRow(coverage = stats.formatCoverage, mc = mc)
    }
}

/** Compact 3-chip row of distinct owned cards legal per format (Phase 2). */
@Composable
private fun FormatCoverageRow(coverage: Map<String, Int>, mc: MagicColors) {
    if (coverage.values.all { it == 0 }) return
    val sp = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
        Text(
            text = stringResource(R.string.stats_dist_format_coverage),
            style = MaterialTheme.magicTypography.titleMedium,
            color = mc.textPrimary,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
            coverage.forEach { (label, count) ->
                Card(
                    modifier = Modifier.weight(1f),
                    shape = CardShape,
                    colors = CardDefaults.cardColors(containerColor = mc.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = sp.md).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(count.toString(), style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
                        Text(label, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
                    }
                }
            }
        }
    }
}


@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    Card(
        modifier = modifier,
        shape = CardShape,
        colors   = CardDefaults.cardColors(containerColor = mc.surfaceVariant),
    ) {
        Column(
            modifier            = Modifier.padding(sp.lg).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(value, style = MaterialTheme.magicTypography.titleLarge.copy(fontSize = 24.sp), color = valueColor ?: mc.textPrimary, textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.magicTypography.labelMedium, color = mc.textSecondary, textAlign = TextAlign.Center)
        }
    }
}


@Composable
private fun CuriousStatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    percentage: Float? = null,
    supportingText: String? = null,
    mc: MagicColors,
    content: @Composable (androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null
) {
    val sp = MaterialTheme.spacing
    Card(
        modifier = modifier,
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = mc.surface),
    ) {
        Column(Modifier.padding(sp.md)) {
            Text(label, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
            Spacer(Modifier.height(sp.xs))
            Text(value, style = MaterialTheme.magicTypography.labelLarge.copy(fontWeight = FontWeight.Bold), color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            
            supportingText?.let {
                Text(it, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
            }

            if (percentage != null) {
                Spacer(Modifier.height(sp.md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(mc.textDisabled.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(percentage)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(mc.secondaryAccent, mc.goldMtg)
                                )
                            )
                    )
                }
            }

            content?.let {
                it()
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HistoryCard(
    card: CardValue,
    currency: PreferredCurrency,
    mc: MagicColors,
    onCardClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    sharedTransitionKey: String,
    modifier: Modifier = Modifier
) {
    val sp = MaterialTheme.spacing
    Card(
        modifier = modifier.clickable { onCardClick() },
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = mc.surface),
    ) {
        Column {
            val imageModifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
            val finalImageModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                with(sharedTransitionScope) {
                    imageModifier.sharedBounds(
                        sharedContentState = rememberSharedContentState(key = sharedTransitionKey),
                        animatedVisibilityScope = animatedVisibilityScope,
                        clipInOverlayDuringTransition = OverlayClip(CardShape),
                        boundsTransform = { _, _ -> tween(durationMillis = 500, easing = FastOutSlowInEasing) },
                        renderInOverlayDuringTransition = true,
                    )
                }
            } else imageModifier

            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = null,
                modifier = finalImageModifier,
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(sp.md)) {
                CardName(card.name, style = MaterialTheme.magicTypography.labelMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                
                Spacer(Modifier.height(sp.sm))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(sp.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SetSymbol(
                        setCode = card.setCode,
                        rarity = CardRarity.fromString(card.rarity),
                        size = 14.dp,
                    )
                    Text(
                        text = card.setName,
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.secondaryAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(Modifier.height(sp.md))
                
                val price = if (currency == PreferredCurrency.USD) card.priceUsd else card.priceEur
                if (price > 0) {
                    Text(PriceFormatter.format(price, currency), style = MaterialTheme.magicTypography.labelMedium.copy(fontWeight = FontWeight.Bold), color = mc.goldMtg)
                } else {
                    Text("—", style = MaterialTheme.magicTypography.labelMedium, color = mc.textSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MostValuableSection(
    cards:       List<CardValue>,
    currency:    PreferredCurrency,
    onCardClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    val filteredCards = cards.filter { card ->
        (if (currency == PreferredCurrency.USD) card.priceUsd else card.priceEur) > 0
    }
    if (filteredCards.isEmpty()) return

    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = mc.surface)
    ) {
        Column {
            filteredCards.forEachIndexed { index, card ->
                val key = "stats-valuable-${card.scryfallId}"
                ListItem(
                    modifier        = Modifier.clickable { onCardClick(card.scryfallId, key) },
                    colors          = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        CardName(
                            name = card.name,
                            showFrontOnly = true,
                            color = mc.textPrimary,
                            style = MaterialTheme.magicTypography.labelMedium
                        )
                    },
                    leadingContent  = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                            Text(
                                text  = "#${index + 1}",
                                style = MaterialTheme.magicTypography.labelLarge,
                                color = mc.primaryAccent.copy(alpha = 0.8f),
                                modifier = Modifier.width(36.dp)
                            )
                            val imageModifier = Modifier.width(40.dp).height(56.dp).clip(SmallCardShape)
                            val finalImageModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    imageModifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = key),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        clipInOverlayDuringTransition = OverlayClip(SmallCardShape),
                                        boundsTransform = { _, _ -> tween(durationMillis = 500, easing = FastOutSlowInEasing) },
                                        renderInOverlayDuringTransition = true,
                                    )
                                }
                            } else imageModifier
                            AsyncImage(
                                model = card.imageNormal,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = finalImageModifier.background(mc.surfaceVariant),
                            )
                        }
                    },
                    trailingContent = {
                        val price = if (currency == PreferredCurrency.USD) card.priceUsd else card.priceEur
                        Text(
                            text  = PriceFormatter.format(price, currency),
                            style = MaterialTheme.magicTypography.labelLarge,
                            color = mc.goldMtg,
                        )
                    },
                    supportingContent = if (card.isFoil) {
                        { Text(stringResource(R.string.shared_foil), style = MaterialTheme.magicTypography.labelSmall, color = mc.goldMtg.copy(alpha = 0.7f)) }
                    } else null,
                )
                if (index < filteredCards.size - 1) HorizontalDivider(thickness = 0.5.dp, color = mc.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = sp.lg))
            }
        }
    }
}

@Composable
private fun SetStatsSection(
    stats: CollectionStats,
    currency: PreferredCurrency,
    mc: MagicColors
) {
    val sp = MaterialTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(sp.md)
    ) {
        stats.topSetByCount?.let { (setCode, count) ->
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = mc.surface)
            ) {
                Column(
                    modifier = Modifier.padding(sp.lg).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.stats_label_most_owned_set), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(sp.md))
                    SetSymbol(setCode = setCode, rarity = CardRarity.RARE, size = 42.dp)
                    Spacer(Modifier.height(sp.md))
                    Text(setCode.uppercase(), style = MaterialTheme.magicTypography.labelMedium.copy(fontWeight = FontWeight.Bold), color = mc.textPrimary, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.collection_card_count, count), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
        
        stats.topSetByValue?.let { (setCode, value) ->
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = CardShape,
                colors = CardDefaults.cardColors(containerColor = mc.surface)
            ) {
                Column(
                    modifier = Modifier.padding(sp.lg).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.stats_label_most_valuable_set), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(sp.md))
                    SetSymbol(setCode = setCode, rarity = CardRarity.MYTHIC, size = 42.dp)
                    Spacer(Modifier.height(sp.md))
                    Text(PriceFormatter.format(value, currency), style = MaterialTheme.magicTypography.labelMedium.copy(fontWeight = FontWeight.Bold), color = mc.goldMtg, textAlign = TextAlign.Center)
                    Text(setCode.uppercase(), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun CircularDistributionSection(
    title: String,
    data: Map<String, Int>,
    colorMapper: (String) -> Color,
    isColor: Boolean = false,
) {
    if (data.isEmpty()) return
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(sp.lg)) {
        Text(title, style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        
        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = mc.surface),
        ) {
            CircularDistribution(
                data = data,
                colorMapper = colorMapper,
                isColor = isColor,
                modifier = Modifier.padding(vertical = sp.md, horizontal = sp.lg)
            )
        }
    }
}

@Composable
private fun DistributionSection(title: String, data: Map<String, Int>) {
    if (data.isEmpty()) return
    val total = data.values.sum().toFloat().coerceAtLeast(1f)
    val mc    = MaterialTheme.magicColors
    val sp    = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
        Text(title, style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        Card(
            shape = CardShape,
            colors = CardDefaults.cardColors(containerColor = mc.surface),
            modifier = Modifier.padding(bottom = sp.xs)
        ) {
            Column(Modifier.padding(sp.lg), verticalArrangement = Arrangement.spacedBy(sp.md)) {
                data.entries.sortedByDescending { it.value }.forEach { (label, count) ->
                    val progress = count / total
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(sp.md),
                    ) {
                        Text(
                            label,
                            style    = MaterialTheme.magicTypography.labelSmall,
                            color    = mc.textPrimary,
                            modifier = Modifier.width(90.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            LinearProgressIndicator(
                                progress     = { progress },
                                modifier     = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color        = mc.primaryAccent,
                                trackColor   = mc.surfaceVariant,
                                strokeCap    = StrokeCap.Butt
                            )
                        }
                        Text(
                            text     = count.toString(),
                            style    = MaterialTheme.magicTypography.labelSmall,
                            color    = mc.textPrimary,
                            modifier = Modifier.width(32.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Top sets by completion ratio, each row a progress bar + "owned / total" label (Phase 2). */
@Composable
private fun SetCompletionSection(completions: List<SetCompletion>) {
    if (completions.isEmpty()) return
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = mc.surface),
    ) {
        Column(Modifier.padding(sp.lg), verticalArrangement = Arrangement.spacedBy(sp.lg)) {
            completions.forEach { completion ->
                key(completion.set.code) {
                    Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(sp.sm),
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(completion.set.iconSvgUri)
                                        .decoderFactory(SvgDecoder.Factory())
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    colorFilter = ColorFilter.tint(mc.textSecondary),
                                )
                                Text(
                                    text = completion.set.name,
                                    style = MaterialTheme.magicTypography.labelMedium,
                                    color = mc.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            Text(
                                text = "${completion.ownedCount} / ${completion.set.cardCount}",
                                style = MaterialTheme.magicTypography.labelMedium,
                                color = mc.textSecondary,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { completion.completionRatio },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            color = mc.primaryAccent,
                            trackColor = mc.textDisabled.copy(alpha = 0.25f),
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Games tab
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Formats a duration given in milliseconds as "Xm Ys".
 */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}m ${seconds.toString().padStart(2, '0')}s"
}

/**
 * Root composable for the Games tab content.
 *
 * @param onReviewSurvey Navigates to the survey screen in REVIEW mode.
 * @param onDeckClick Navigates to the deck detail screen.
 * @param onDeleteSession Triggers deletion of a session after double confirmation. The success
 *   toast fires only after [StatsUiState.deleteSessionSuccess] confirms the delete actually
 *   succeeded — never optimistically at confirm-click time (Phase 1 audit fix).
 * @param onClearDeleteMessage Clears the one-shot delete result after its toast has been shown.
 */
@Composable
private fun GameStatsContent(
    uiState: StatsUiState,
    toastState: com.mmg.manahub.core.ui.components.MagicToastState,
    onReviewSurvey: (Long) -> Unit,
    onDeckClick: (String) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onClearDeleteMessage: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    // Delete confirmation state: holds the session id pending first confirmation
    var showFirstConfirm  by rememberSaveable { mutableStateOf<Long?>(null) }
    // Second confirmation: holds the session id pending final irreversible confirm
    var showFinalConfirm  by rememberSaveable { mutableStateOf<Long?>(null) }

    val deleteSuccessLabel = stringResource(R.string.delete_game_toast)
    val deleteErrorLabel   = stringResource(R.string.delete_game_error)
    LaunchedEffect(uiState.deleteSessionSuccess) {
        when (uiState.deleteSessionSuccess) {
            true  -> { toastState.show(deleteSuccessLabel, MagicToastType.SUCCESS); onClearDeleteMessage() }
            false -> { toastState.show(deleteErrorLabel, MagicToastType.ERROR); onClearDeleteMessage() }
            null  -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = sp.lg),
        verticalArrangement = Arrangement.spacedBy(sp.xl),
    ) {
        Spacer(Modifier.height(sp.sm))

        // ── KPI grid ─────────────────────────────────────────────────────────
        uiState.gameStats?.let { gs ->
            Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                Row(horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                    StatCard(
                        label    = stringResource(R.string.stats_kpi_total_games),
                        value    = gs.totalGames.toString(),
                        modifier = Modifier.weight(1f).height(90.dp),
                    )
                    StatCard(
                        label    = stringResource(R.string.stats_kpi_winrate),
                        value    = "${(gs.winrate * 100).toInt()}%",
                        modifier = Modifier.weight(1f).height(90.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                    StatCard(
                        label    = stringResource(R.string.stats_kpi_avg_duration),
                        value    = formatDuration(gs.avgDurationMs),
                        modifier = Modifier.weight(1f).height(90.dp),
                    )
                    // Phase 3: the single "Favorite Mode" KPI is REPLACED by the "Win Rate by Mode"
                    // breakdown section below (a superset — the top mode is its first, highest-
                    // games row), so this slot now surfaces mostFrequentLoss instead (was already
                    // computed but never rendered — Phase 1 audit finding).
                    StatCard(
                        label    = stringResource(R.string.stats_kpi_most_common_defeat),
                        value    = gs.mostFrequentLoss ?: "—",
                        modifier = Modifier.weight(1f).height(90.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                    StatCard(
                        label    = stringResource(R.string.stats_kpi_current_streak),
                        value    = gs.currentStreak.toString(),
                        modifier = Modifier.weight(1f).height(90.dp),
                    )
                    StatCard(
                        label    = stringResource(R.string.stats_kpi_best_streak),
                        value    = gs.bestStreak.toString(),
                        modifier = Modifier.weight(1f).height(90.dp),
                    )
                }

                // TODO: Re-enable when Survey review for games is fully implemented
//                if (gs.pendingSurveys > 0) {
//                    Card(
//                        modifier = Modifier.fillMaxWidth(),
//                        shape    = CardShape,
//                        colors   = CardDefaults.cardColors(containerColor = mc.goldMtg.copy(alpha = 0.15f)),
//                        border   = BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.4f)),
//                    ) {
//                        Text(
//                            text     = stringResource(R.string.stats_pending_surveys, gs.pendingSurveys),
//                            style    = ty.bodySmall,
//                            color    = mc.goldMtg,
//                            modifier = Modifier.padding(horizontal = sp.lg, vertical = sp.md),
//                        )
//                    }
//                }
            }
        }

        // ── Win rate by mode ─────────────────────────────────────────────────
        if (uiState.modeWinrates.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                Text(
                    text   = stringResource(R.string.stats_section_winrate_by_mode).uppercase(),
                    style  = ty.labelLarge,
                    color  = mc.textPrimary,
                    letterSpacing = 2.sp,
                )
                Card(
                    shape  = CardShape,
                    colors = CardDefaults.cardColors(containerColor = mc.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(sp.lg),
                        verticalArrangement = Arrangement.spacedBy(sp.lg),
                    ) {
                        uiState.modeWinrates.forEach { item -> ModeWinrateRow(item = item, mc = mc) }
                    }
                }
            }
        }

        // ── Win rate by player count ─────────────────────────────────────────
        if (uiState.playerCountWinrates.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                Text(
                    text   = stringResource(R.string.stats_section_winrate_by_player_count).uppercase(),
                    style  = ty.labelLarge,
                    color  = mc.textPrimary,
                    letterSpacing = 2.sp,
                )
                Card(
                    shape  = CardShape,
                    colors = CardDefaults.cardColors(containerColor = mc.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(sp.lg),
                        verticalArrangement = Arrangement.spacedBy(sp.lg),
                    ) {
                        uiState.playerCountWinrates.forEach { item -> PlayerCountWinrateRow(item = item, mc = mc) }
                    }
                }
            }
        }

        // ── Deck performance ─────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
            Text(
                text   = stringResource(R.string.stats_section_deck_performance).uppercase(),
                style  = ty.labelLarge,
                color  = mc.textPrimary,
                letterSpacing = 2.sp,
            )

            if (uiState.deckPerformance.isEmpty()) {
                EmptyState(
                    title    = stringResource(R.string.stats_section_deck_performance),
                    subtitle = stringResource(R.string.stats_empty_decks_subtitle),
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
            } else {
                Card(
                    shape  = CardShape,
                    colors = CardDefaults.cardColors(containerColor = mc.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(sp.lg),
                        verticalArrangement = Arrangement.spacedBy(sp.lg),
                    ) {
                        uiState.deckPerformance.forEach { deck ->
                            DeckPerformanceRow(
                                deck        = deck,
                                mc          = mc,
                                onDeckClick = { onDeckClick(deck.deckId) },
                            )
                        }
                    }
                }
            }
        }

        // ── Matchup win rates (by opponent archetype) ─────────────────────────
        if (uiState.archetypeMatchups.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                Text(
                    text   = stringResource(R.string.stats_section_matchups).uppercase(),
                    style  = ty.labelLarge,
                    color  = mc.textPrimary,
                    letterSpacing = 2.sp,
                )
                Card(
                    shape  = CardShape,
                    colors = CardDefaults.cardColors(containerColor = mc.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(sp.lg),
                        verticalArrangement = Arrangement.spacedBy(sp.lg),
                    ) {
                        uiState.archetypeMatchups.forEach { matchup ->
                            ArchetypeMatchupItem(matchup = matchup, mc = mc)
                        }
                    }
                }
            }
        }

        // ── Recent form ──────────────────────────────────────────────────────
        if (uiState.recentForm.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                Text(
                    text   = stringResource(R.string.stats_section_recent_form).uppercase(),
                    style  = ty.labelLarge,
                    color  = mc.textPrimary,
                    letterSpacing = 2.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(sp.xs)) {
                    uiState.recentForm.forEach { entry ->
                        key(entry.sessionId) {
                            RecentFormBadge(isWin = entry.isWin, mc = mc)
                        }
                    }
                }
            }
        }

        // ── Session history ───────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
            Text(
                text   = stringResource(R.string.stats_section_history).uppercase(),
                style  = ty.labelLarge,
                color  = mc.textPrimary,
                letterSpacing = 2.sp,
            )

            if (uiState.sessionHistory.isEmpty()) {
                EmptyState(
                    title    = stringResource(R.string.stats_section_history),
                    subtitle = stringResource(R.string.stats_empty_history_subtitle),
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
            } else {
                Card(
                    shape  = CardShape,
                    colors = CardDefaults.cardColors(containerColor = mc.surface),
                ) {
                    Column {
                        uiState.sessionHistory.forEachIndexed { index, item ->
                            SessionHistoryRow(
                                item            = item,
                                mc              = mc,
                                onDeleteRequest = { showFirstConfirm = item.sessionId },
                            )
                            if (index < uiState.sessionHistory.size - 1) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color     = mc.surfaceVariant.copy(alpha = 0.4f),
                                    modifier  = Modifier.padding(horizontal = sp.lg),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(sp.xxl))
    }

    // ── Delete dialogs ────────────────────────────────────────────────────────

    showFirstConfirm?.let { sessionId ->
        MagicAlertDialog(
            onDismissRequest = { showFirstConfirm = null },
            title = stringResource(R.string.delete_game_title),
            text = stringResource(R.string.delete_game_message_1),
            confirmLabel = stringResource(R.string.action_delete_game),
            onConfirm = {
                showFinalConfirm = sessionId
                showFirstConfirm = null
            },
            confirmColor = MagicCtaColor.Error,
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { showFirstConfirm = null },
        )
    }

    showFinalConfirm?.let { sessionId ->
        MagicAlertDialog(
            onDismissRequest = { showFinalConfirm = null },
            title = stringResource(R.string.delete_game_final_title),
            text = stringResource(R.string.delete_game_final_message),
            confirmLabel = stringResource(R.string.action_delete_game),
            onConfirm = {
                onDeleteSession(sessionId)
                showFinalConfirm = null
            },
            confirmColor = MagicCtaColor.Error,
            dismissLabel = stringResource(R.string.action_cancel),
            onDismiss = { showFinalConfirm = null },
        )
    }
}

@Composable
private fun DeckPerformanceRow(
    deck: DeckPerformance,
    mc: MagicColors,
    onDeckClick: () -> Unit,
) {
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeckClick() },
        verticalArrangement = Arrangement.spacedBy(sp.xs),
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text     = deck.deckName,
                style    = ty.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color    = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text  = "${deck.wins}/${deck.totalGames}",
                style = ty.labelMedium,
                color = mc.textSecondary,
            )
        }
        LinearProgressIndicator(
            progress   = { deck.winrate },
            modifier   = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color      = mc.primaryAccent,
            trackColor = mc.surfaceVariant,
        )
    }
}

@Composable
private fun ArchetypeMatchupItem(
    matchup: com.mmg.manahub.feature.game.domain.model.ArchetypeMatchupData,
    mc: MagicColors,
) {
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val winrate = if (matchup.totalGames > 0) matchup.wins.toFloat() / matchup.totalGames else 0f
    Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text     = matchup.opponentArchetype,
                style    = ty.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color    = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text  = "${matchup.wins}/${matchup.totalGames}",
                style = ty.labelMedium,
                color = mc.textSecondary,
            )
        }
        LinearProgressIndicator(
            progress   = { winrate },
            modifier   = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color      = mc.primaryAccent,
            trackColor = mc.surfaceVariant,
        )
    }
}

@Composable
private fun ModeWinrateRow(item: ModeWinrateItem, mc: MagicColors) {
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text     = item.mode,
                style    = ty.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color    = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text  = "${item.wins}/${item.totalGames}",
                style = ty.labelMedium,
                color = mc.textSecondary,
            )
        }
        LinearProgressIndicator(
            progress   = { item.winrate },
            modifier   = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color      = mc.primaryAccent,
            trackColor = mc.textDisabled.copy(alpha = 0.25f),
        )
    }
}

@Composable
private fun PlayerCountWinrateRow(item: PlayerCountWinrateItem, mc: MagicColors) {
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val label = if (item.isFourPlus) {
        stringResource(R.string.stats_player_count_four_plus)
    } else {
        stringResource(R.string.stats_player_count_n, item.playerCount)
    }
    Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text     = label,
                style    = ty.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color    = mc.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text  = "${item.wins}/${item.totalGames}",
                style = ty.labelMedium,
                color = mc.textSecondary,
            )
        }
        LinearProgressIndicator(
            progress   = { item.winrate },
            modifier   = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color      = mc.primaryAccent,
            trackColor = mc.textDisabled.copy(alpha = 0.25f),
        )
    }
}

/**
 * One decorative W/L badge in the "Recent form" strip (Phase 3, 2026-07 stats expansion).
 * Non-interactive — mirrors [SessionHistoryRow]'s win/loss badge styling.
 */
@Composable
private fun RecentFormBadge(isWin: Boolean, mc: MagicColors) {
    val ty = MaterialTheme.magicTypography
    Surface(
        shape    = SmallCardShape,
        color    = if (isWin) mc.lifePositive.copy(alpha = 0.2f) else mc.lifeNegative.copy(alpha = 0.15f),
        modifier = Modifier.size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text  = if (isWin) "W" else "L",
                style = ty.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isWin) mc.lifePositive else mc.lifeNegative,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Trades tab (Phase 4, 2026-07 stats expansion)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Root composable for the Trades tab content. Unlike every other Stats section, [state] is a real
 * network round-trip (see [GetTradeStatsUseCase][com.mmg.manahub.core.data.usecase.stats.GetTradeStatsUseCase])
 * triggered lazily on tab activation, so all four states (idle/loading, content, empty, error) are
 * handled explicitly rather than assuming data is already there.
 */
@Composable
private fun TradeStatsContent(
    state: TradeStatsUiState,
    currency: PreferredCurrency,
    onRetry: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    when (state) {
        is TradeStatsUiState.Idle, is TradeStatsUiState.Loading -> {
            Column(
                modifier             = Modifier.fillMaxSize(),
                horizontalAlignment  = Alignment.CenterHorizontally,
                verticalArrangement  = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = mc.primaryAccent)
                Spacer(Modifier.height(sp.md))
                Text(
                    text  = stringResource(R.string.stats_trades_loading),
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                )
            }
        }
        is TradeStatsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(sp.xl), contentAlignment = Alignment.TopCenter) {
                InlineErrorState(
                    message    = stringResource(R.string.stats_trades_error),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry    = onRetry,
                )
            }
        }
        is TradeStatsUiState.Content -> {
            val stats = state.stats
            if (stats.completedTradesCount == 0) {
                // Defensive: the TRADES tab is only shown when the cheap visibility warm-up saw a
                // COMPLETED proposal, so this branch should be unreachable in practice — kept as a
                // fallback in case that cache was stale (e.g. a cross-account race) when the fresh
                // fetch here landed.
                EmptyState(
                    title    = stringResource(R.string.stats_trades_empty_title),
                    subtitle = stringResource(R.string.stats_trades_empty_subtitle),
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = sp.lg),
                    verticalArrangement = Arrangement.spacedBy(sp.xl),
                ) {
                    Spacer(Modifier.height(sp.sm))

                    Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                            StatCard(
                                label    = stringResource(R.string.stats_kpi_completed_trades),
                                value    = stats.completedTradesCount.toString(),
                                modifier = Modifier.weight(1f).height(90.dp),
                            )
                            StatCard(
                                label    = stringResource(R.string.stats_kpi_cards_sent),
                                value    = stats.cardsSent.toString(),
                                modifier = Modifier.weight(1f).height(90.dp),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                            StatCard(
                                label    = stringResource(R.string.stats_kpi_cards_received),
                                value    = stats.cardsReceived.toString(),
                                modifier = Modifier.weight(1f).height(90.dp),
                            )
                            val deltaSign = if (stats.netValueDelta >= 0) "+" else "−"
                            val deltaMagnitude = PriceFormatter.format(kotlin.math.abs(stats.netValueDelta), currency)
                            StatCard(
                                label      = stringResource(R.string.stats_kpi_net_value),
                                value      = "$deltaSign$deltaMagnitude",
                                valueColor = if (stats.netValueDelta >= 0) mc.lifePositive else mc.lifeNegative,
                                modifier   = Modifier.weight(1f).height(90.dp),
                            )
                        }
                    }

                    Text(
                        text  = stringResource(R.string.stats_trades_net_value_note),
                        style = ty.labelSmall,
                        color = mc.textDisabled,
                    )

                    stats.topPartner?.let { partner ->
                        Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                            Text(
                                text   = stringResource(R.string.stats_section_top_partner).uppercase(),
                                style  = ty.labelLarge,
                                color  = mc.textPrimary,
                                letterSpacing = 2.sp,
                            )
                            Card(
                                shape  = CardShape,
                                colors = CardDefaults.cardColors(containerColor = mc.surface),
                            ) {
                                Row(
                                    modifier              = Modifier.fillMaxWidth().padding(sp.lg),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text     = partner.displayName,
                                        style    = ty.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color    = mc.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text  = stringResource(R.string.stats_trade_count, partner.completedTradesCount),
                                        style = ty.labelMedium,
                                        color = mc.textSecondary,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(sp.xxl))
                }
            }
        }
    }
}

@Composable
private fun SessionHistoryRow(
    item: GameHistoryItem,
    mc: MagicColors,
    onDeleteRequest: () -> Unit,
) {
    val ty          = MaterialTheme.magicTypography
    val sp          = MaterialTheme.spacing
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = sp.lg, vertical = sp.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        // Win/loss badge
        Surface(
            shape = SmallCardShape,
            color = if (item.isWin) mc.lifePositive.copy(alpha = 0.2f)
                    else mc.lifeNegative.copy(alpha = 0.15f),
        ) {
            Text(
                text     = if (item.isWin) "W" else "L",
                style    = ty.labelMedium.copy(fontWeight = FontWeight.Bold),
                color    = if (item.isWin) mc.lifePositive else mc.lifeNegative,
                modifier = Modifier.padding(horizontal = sp.sm, vertical = sp.xs),
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(sp.xxs)) {
            // Mode + duration
            Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                Text(
                    text  = item.mode,
                    style = ty.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = mc.textPrimary,
                )
                Text(
                    text  = "·",
                    style = ty.bodySmall,
                    color = mc.textDisabled,
                )
                Text(
                    text  = formatDuration(item.durationMs),
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                )
            }

            // Date
            Text(
                text  = TimeAgoFormatter.format(item.playedAt),
                style = ty.labelSmall,
                color = mc.textDisabled,
            )

            // Deck name
            item.deckName?.let { name ->
                Text(
                    text     = name,
                    style    = ty.labelSmall,
                    color    = mc.goldMtg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // TODO: Re-enable when Survey review for games is fully implemented
//            SurveyStatusChip(item = item, mc = mc)
        }

        // Trailing more-options button with dropdown
        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    tint = mc.textDisabled,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded         = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                // TODO: Re-enable when Survey review for games is fully implemented
//                DropdownMenuItem(
//                    text    = { Text(stringResource(R.string.action_review_survey)) },
//                    onClick = {
//                        menuExpanded = false
//                        onReviewSurvey()
//                    },
//                )
                DropdownMenuItem(
                    text    = { Text(stringResource(R.string.action_delete_game), color = mc.lifeNegative) },
                    onClick = {
                        menuExpanded = false
                        onDeleteRequest()
                    },
                )
            }
        }
    }
}

// TODO: Re-enable when Survey review for games is fully implemented
//@Composable
//private fun SurveyStatusChip(item: GameHistoryItem, mc: MagicColors) {
//    val ty = MaterialTheme.magicTypography
//    val (label, color) = when (item.surveyStatus) {
//        SurveyStatus.PENDING   -> stringResource(R.string.survey_pending) to mc.goldMtg.copy(alpha = 0.7f)
//        SurveyStatus.PARTIAL   -> stringResource(R.string.survey_partial) to mc.goldMtg.copy(alpha = 0.5f)
//        SurveyStatus.COMPLETED -> {
//            val timestamp = item.surveyStatus.let {
//                stringResource(R.string.survey_completed)
//            }
//            timestamp to mc.lifePositive.copy(alpha = 0.7f)
//        }
//        SurveyStatus.SKIPPED   -> stringResource(R.string.survey_skipped) to mc.textDisabled
//    }
//
//    Surface(
//        shape = RoundedCornerShape(4.dp),
//        color = color.copy(alpha = 0.15f),
//    ) {
//        Text(
//            text     = label,
//            style    = ty.labelSmall,
//            color    = color,
//            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
//        )
//    }
//}


fun MtgColor.toDisplayNameRes(): Int = when (this) {
    MtgColor.W          -> R.string.stats_color_white
    MtgColor.U          -> R.string.stats_color_blue
    MtgColor.B          -> R.string.stats_color_black
    MtgColor.R          -> R.string.stats_color_red
    MtgColor.G          -> R.string.stats_color_green
    MtgColor.COLORLESS  -> R.string.stats_color_colorless
    else                -> R.string.stats_color_unknown
}
