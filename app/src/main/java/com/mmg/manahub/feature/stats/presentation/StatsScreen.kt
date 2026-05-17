package com.mmg.manahub.feature.stats.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.entity.SurveyStatus
import com.mmg.manahub.core.domain.model.CardValue
import com.mmg.manahub.core.domain.model.CollectionStats
import com.mmg.manahub.core.domain.model.MagicSet
import com.mmg.manahub.core.domain.model.MtgColor
import com.mmg.manahub.core.domain.model.PreferredCurrency
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaColorPicker
import com.mmg.manahub.core.ui.components.ManaCurveChart
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.search.SetPickerSheet
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.core.util.TimeAgoFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.runtime.setValue
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onCardClick:      (scryfallId: String) -> Unit,
    onBackClick:      () -> Unit = {},
    onReviewSurvey:   (sessionId: Long) -> Unit = {},
    onDeckClick:      (deckId: String) -> Unit = {},
    viewModel:        StatsViewModel = hiltViewModel(),
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
                // Only show the tab row when game sessions exist
                if (uiState.hasGameStats) {
                    val tabLabels = listOf(
                        stringResource(R.string.stats_tab_collection),
                        stringResource(R.string.stats_tab_games),
                    )
                    TabRow(
                        selectedTabIndex   = uiState.selectedTab.ordinal,
                        containerColor     = mc.backgroundSecondary.copy(alpha = 0.9f),
                        contentColor       = mc.primaryAccent,
                        divider            = {},
                    ) {
                        tabLabels.forEachIndexed { index, label ->
                            val selected = uiState.selectedTab.ordinal == index
                            Tab(
                                selected = selected,
                                onClick  = { viewModel.onTabSelected(StatsTab.entries[index]) },
                                text     = {
                                    Text(
                                        text  = label.uppercase(Locale.getDefault()),
                                        style = MaterialTheme.magicTypography.labelLarge,
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
                        )
                    else ->
                        CollectionStatsContent(
                            uiState          = uiState,
                            onColorSelected  = viewModel::onColorSelected,
                            onSetSelected    = viewModel::onSetSelected,
                            onCurrencyToggle = viewModel::onCurrencyToggle,
                            onRefreshPrices  = viewModel::refreshPrices,
                            onCardClick      = onCardClick,
                        )
                }
            }
        }

        MagicToastHost(state = toastState)
    }
}

@Composable
private fun CollectionStatsContent(
    uiState: StatsUiState,
    onColorSelected: (MtgColor?) -> Unit,
    onSetSelected: (MagicSet?) -> Unit,
    onCurrencyToggle: () -> Unit,
    onRefreshPrices: () -> Unit,
    onCardClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val stats = uiState.stats
    var showSetPicker by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Color Filter Row
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                spacing = 8.dp,
                itemSize = 48.dp,
                symbolSize = 32.dp,
                horizontalArrangement = Arrangement.SpaceBetween
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
                availableSets = uiState.availableSets
            )
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = mc.primaryAccent)
            }
        } else if (stats != null) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
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
                    AestheticsArtSection(stats = stats, currency = uiState.currency, onCardClick = onCardClick)
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
                    HallOfFameSection(stats = stats, currency = uiState.currency, onCardClick = onCardClick)
                }

                Spacer(Modifier.height(32.dp))
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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = mc.surface,
        border = BorderStroke(
            width = if (selectedSet != null) 1.5.dp else 0.5.dp,
            color = if (selectedSet != null) mc.primaryAccent else mc.surfaceVariant.copy(alpha = 0.5f),
        ),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
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
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Collection value card
            Card(
                modifier = Modifier.weight(1f).height(90.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = mc.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val value = if (currency == PreferredCurrency.USD) stats.totalValueUsd else stats.totalValueEur
                    Text(
                        text = PriceFormatter.format(value, currency),
                        style = MaterialTheme.magicTypography.titleLarge.copy(fontSize = 20.sp),
                        color = mc.goldMtg,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.stats_label_est_value),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
            StatCard(
                label = stringResource(R.string.stats_decks_saved),
                value = stats.totalDecks.toString(),
                modifier = Modifier.weight(1f).height(90.dp)
            )
        }
    }
}

@Composable
private fun CombatMechanicsSection(stats: CollectionStats) {
    val mc = MaterialTheme.magicColors
    val manaGradient = Brush.linearGradient(
        colors = listOf(mc.manaW, mc.manaU, mc.manaB, mc.manaR, mc.manaG)
    )

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CombatStatsBox(
                avgPower = stats.avgPower ?: 0.0,
                avgToughness = stats.avgToughness ?: 0.0,
                modifier = Modifier.weight(1.2f)
            )
            
            Card(
                modifier = Modifier.weight(0.8f).height(100.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = mc.surfaceVariant),
                border = BorderStroke(1.dp, manaGradient)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp).fillMaxSize(),
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
    Card(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(topStart = 4.dp, bottomEnd = 4.dp, topEnd = 16.dp, bottomStart = 16.dp),
        colors = CardDefaults.cardColors(containerColor = mc.surfaceVariant),
        border = BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.5f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxSize().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.ic_battle),
                        contentDescription = null,
                        tint = mc.secondaryAccent.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "%.1f".format(avgPower),
                        style = MaterialTheme.magicTypography.displayMedium.copy(fontSize = 26.sp),
                        color = mc.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.stats_label_avg_power).uppercase(),
                        style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 8.sp),
                        color = mc.textSecondary
                    )
                }
                
                Text(
                    text = "/",
                    style = MaterialTheme.magicTypography.displayMedium.copy(fontSize = 26.sp),
                    color = mc.goldMtg,
                    modifier = Modifier.padding(horizontal = 12.dp).padding(top = 16.dp) // Align with numbers
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = mc.goldMtg.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "%.1f".format(avgToughness),
                        style = MaterialTheme.magicTypography.displayMedium.copy(fontSize = 26.sp),
                        color = mc.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.stats_label_avg_toughness).uppercase(),
                        style = MaterialTheme.magicTypography.labelSmall.copy(fontSize = 8.sp),
                        color = mc.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun AestheticsArtSection(
    stats: CollectionStats,
    currency: PreferredCurrency,
    onCardClick: (String) -> Unit
) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AestheticStatCard(
                label = stringResource(R.string.stats_label_foil_cards),
                value = stats.totalFoil.toString(),
                percentage = if (stats.totalCards > 0) (stats.totalFoil.toFloat() / stats.totalCards) else 0f,
                isFoil = true,
                modifier = Modifier.weight(1f)
            )
            AestheticStatCard(
                label = stringResource(R.string.stats_label_full_art_cards),
                value = stats.totalFullArt.toString(),
                percentage = if (stats.totalCards > 0) (stats.totalFullArt.toFloat() / stats.totalCards) else 0f,
                isFoil = false,
                modifier = Modifier.weight(1f)
            )
        }

        CuriousStatBox(
            label = stringResource(R.string.stats_label_top_artist),
            value = stats.topArtist ?: stringResource(R.string.stats_not_available),
            supportingText = if (stats.topArtistCount > 0) stringResource(R.string.stats_artist_cards_count, stats.topArtistCount) else null,
            mc = mc,
            modifier = Modifier.fillMaxWidth()
        )
    }
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
    
    val foilBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF00E5FF).copy(alpha = 0.2f),
            Color(0xFFBF00FF).copy(alpha = 0.2f),
            Color(0xFFFFD700).copy(alpha = 0.2f),
            Color(0xFF00FFCC).copy(alpha = 0.2f)
        )
    )

    Card(
        modifier = modifier.height(110.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = mc.surface),
        border = if (isFoil) BorderStroke(1.dp, foilBrush) else BorderStroke(1.dp, mc.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isFoil) {
                Box(modifier = Modifier.fillMaxSize().background(foilBrush))
            }
            
            Column(Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(label, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
                    Text(
                        text = value,
                        style = MaterialTheme.magicTypography.titleLarge.copy(fontSize = 24.sp),
                        color = mc.textPrimary
                    )
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${(percentage * 100).toInt()}%", style = MaterialTheme.magicTypography.labelSmall, color = mc.textDisabled)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
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

@Composable
private fun HallOfFameSection(
    stats: CollectionStats,
    currency: PreferredCurrency,
    onCardClick: (String) -> Unit
) {
    val mc = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        // Oldest / Newest
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            stats.oldestCard?.let { card ->
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryCard(card = card, currency = currency, mc = mc, onCardClick = { onCardClick(card.scryfallId) })
                    Text(stringResource(R.string.stats_label_oldest_card), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
            stats.newestCard?.let { card ->
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HistoryCard(card = card, currency = currency, mc = mc, onCardClick = { onCardClick(card.scryfallId) })
                    Text(stringResource(R.string.stats_label_newest_card), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        MostValuableSection(cards = stats.mostValuableCards, currency = currency, onCardClick = onCardClick)

        SetStatsSection(stats = stats, currency = currency, mc = mc)
    }
}

@Composable
private fun DistributionsSection(stats: CollectionStats, mc: MagicColors) {
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        CircularDistributionSection(
            title = stringResource(R.string.stats_dist_color),
            data = stats.byColor.entries.associate { it.key.toDisplayName() to it.value },
            colorMapper = { label ->
                when (label) {
                    "White", "Blanco", "Weiß" -> Color(0xFFF9FAFA)
                    "Blue", "Azul", "Blau" -> Color(0xFF0E68AB)
                    "Black", "Negro", "Schwarz" -> Color(0xFF150B00)
                    "Red", "Rojo", "Rot" -> Color(0xFFD3202A)
                    "Green", "Verde", "Grün" -> Color(0xFF00733E)
                    "Colorless", "Incoloro", "Farblos" -> Color(0xFF90ADBB)
                    else -> mc.primaryAccent
                }
            }
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
    }
}


@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = mc.surfaceVariant),
    ) {
        Column(
            modifier            = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(value, style = MaterialTheme.magicTypography.titleLarge.copy(fontSize = 24.sp), color = mc.textPrimary, textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.magicTypography.labelMedium, color = mc.textSecondary, textAlign = TextAlign.Center)
        }
    }
}


@Composable
private fun CuriousStatBox(
    label: String,
    value: String,
    percentage: Float? = null,
    supportingText: String? = null,
    mc: MagicColors,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = mc.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.magicTypography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            
            if (supportingText != null) {
                Text(supportingText, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
            }

            if (percentage != null) {
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(mc.surfaceVariant.copy(alpha = 0.3f))
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
        }
    }
}

@Composable
private fun HistoryCard(
    card: CardValue,
    currency: PreferredCurrency,
    mc: MagicColors,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onCardClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = mc.surface),
    ) {
        Column {
            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f/3f),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.padding(10.dp)) {
                CardName(card.name, style = MaterialTheme.magicTypography.labelMedium, color = mc.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                
                Spacer(Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                
                Spacer(Modifier.height(12.dp))
                
                val price = if (currency == PreferredCurrency.USD) card.priceUsd else card.priceEur
                if (price > 0) {
                    Text(PriceFormatter.format(price, currency), style = MaterialTheme.magicTypography.labelMedium.copy(fontWeight = FontWeight.Bold), color = mc.goldMtg)
                } else {
                    Text("—", style = MaterialTheme.magicTypography.labelMedium, color = mc.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun MostValuableSection(
    cards:       List<CardValue>,
    currency:    PreferredCurrency,
    onCardClick: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val filteredCards = cards.filter { card ->
        (if (currency == PreferredCurrency.USD) card.priceUsd else card.priceEur) > 0
    }
    if (filteredCards.isEmpty()) return
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = mc.surface)
    ) {
        Column {
            filteredCards.forEachIndexed { index, card ->
                ListItem(
                    modifier        = Modifier.clickable { onCardClick(card.scryfallId) },
                    colors          = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        CardName(
                            name = card.name,
                            showFrontOnly = true,
                            color = mc.textPrimary,
                            style = MaterialTheme.magicTypography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    },
                    leadingContent  = {
                        Text(
                            text  = "#${index + 1}",
                            style = MaterialTheme.magicTypography.labelLarge,
                            color = mc.primaryAccent.copy(alpha = 0.8f),
                        )
                    },
                    trailingContent = {
                        val price = if (currency == PreferredCurrency.USD) card.priceUsd else card.priceEur
                        Text(
                            text  = PriceFormatter.format(price, currency),
                            style = MaterialTheme.magicTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = mc.goldMtg,
                        )
                    },
                    supportingContent = if (card.isFoil) {
                        { Text(stringResource(R.string.addcard_confirm_foil), style = MaterialTheme.magicTypography.labelSmall, color = mc.goldMtg.copy(alpha = 0.7f)) }
                    } else null,
                )
                if (index < filteredCards.size - 1) HorizontalDivider(thickness = 0.5.dp, color = mc.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.padding(horizontal = 16.dp))
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
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        stats.topSetByCount?.let { (setCode, count) ->
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = mc.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.stats_label_most_owned_set), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    SetSymbol(setCode = setCode, rarity = CardRarity.RARE, size = 42.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(setCode.uppercase(), style = MaterialTheme.magicTypography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = mc.textPrimary, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.collection_card_count, count), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
        
        stats.topSetByValue?.let { (setCode, value) ->
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = mc.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(stringResource(R.string.stats_label_most_valuable_set), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    SetSymbol(setCode = setCode, rarity = CardRarity.MYTHIC, size = 42.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(PriceFormatter.format(value, currency), style = MaterialTheme.magicTypography.labelMedium.copy(fontWeight = FontWeight.Bold), color = mc.goldMtg, textAlign = TextAlign.Center)
                    Text(setCode.uppercase(), style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CircularDistributionSection(
    title: String,
    data: Map<String, Int>,
    colorMapper: (String) -> Color
) {
    if (data.isEmpty()) return
    val total = data.values.sum().toFloat().coerceAtLeast(1f)
    val mc = MaterialTheme.magicColors
    
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = mc.surface),
        ) {
            Row(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // The Ring Chart
                Box(modifier = Modifier.size(120.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 16.dp.toPx()
                        val ringSize = size.minDimension - strokeWidth
                        val topLeftOffset = androidx.compose.ui.geometry.Offset(
                            (size.width - ringSize) / 2,
                            (size.height - ringSize) / 2
                        )
                        val arcSize = androidx.compose.ui.geometry.Size(ringSize, ringSize)
                        val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)

                        var startAngle = -90f
                        val sortedData = data.entries.sortedByDescending { it.value }
                        
                        // Draw Background Segments (Alpha)
                        sortedData.forEach { (label, count) ->
                            val sweepAngle = (count / total) * 360f * animationProgress.value
                            if (sweepAngle <= 0f) return@forEach
                            val baseColor = colorMapper(label)
                            drawArc(
                                color = baseColor.copy(alpha = 0.75f),
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = topLeftOffset,
                                size = arcSize,
                                style = Stroke(width = strokeWidth)
                            )
                            startAngle += sweepAngle
                        }
                        
                        // Draw Strokes and Dividers (Solid)
                        startAngle = -90f
                        sortedData.forEach { (label, count) ->
                            val sweepAngle = (count / total) * 360f * animationProgress.value
                            if (sweepAngle <= 0f) return@forEach
                            val baseColor = colorMapper(label)
                            
                            // 1. Outer Stroke (1dp)
                            val outerSizeValue = ringSize + strokeWidth
                            drawArc(
                                color = baseColor,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    (center.x - outerSizeValue / 2),
                                    (center.y - outerSizeValue / 2)
                                ),
                                size = androidx.compose.ui.geometry.Size(outerSizeValue, outerSizeValue),
                                style = Stroke(width = 1.dp.toPx())
                            )

                            // 2. Inner Stroke (1dp)
                            val innerSizeValue = ringSize - strokeWidth
                            drawArc(
                                color = baseColor,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(
                                    (center.x - innerSizeValue / 2),
                                    (center.y - innerSizeValue / 2)
                                ),
                                size = androidx.compose.ui.geometry.Size(innerSizeValue, innerSizeValue),
                                style = Stroke(width = 1.dp.toPx())
                            )

                            // 3. Radial Separator
                            val startAngleRad = (startAngle * PI / 180f)
                            val innerR = innerSizeValue / 2
                            val outerR = outerSizeValue / 2
                            
                            drawLine(
                                color = mc.surface,
                                start = androidx.compose.ui.geometry.Offset(
                                    center.x + innerR * cos(startAngleRad).toFloat(),
                                    center.y + innerR * sin(startAngleRad).toFloat()
                                ),
                                end = androidx.compose.ui.geometry.Offset(
                                    center.x + outerR * cos(startAngleRad).toFloat(),
                                    center.y + outerR * sin(startAngleRad).toFloat()
                                ),
                                strokeWidth = 1.dp.toPx()
                            )

                            startAngle += sweepAngle
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${total.toInt()}",
                            style = MaterialTheme.magicTypography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
                            color = mc.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.stats_label_total),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textSecondary
                        )
                    }
                }

                // Legend
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    data.entries.sortedByDescending { it.value }.forEach { (label, count) ->
                        val percentage = (count / total * 100).toInt()
                        val colorCode = when (label) {
                            "White", "Blanco", "Weiß" -> "W"
                            "Blue", "Azul", "Blau" -> "U"
                            "Black", "Negro", "Schwarz" -> "B"
                            "Red", "Rojo", "Rot" -> "R"
                            "Green", "Verde", "Grün" -> "G"
                            "Colorless", "Incoloro", "Farblos" -> "C"
                            else -> null
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (title.contains(stringResource(R.string.stats_color_white).take(2), ignoreCase = true) && colorCode != null) {
                                ManaSymbolImage(token = colorCode, size = 18.dp)
                            } else {
                                Box(
                                    modifier = Modifier.size(10.dp).clip(CircleShape).background(colorMapper(label))
                                )
                            }
                            
                            Text(
                                text = label,
                                style = MaterialTheme.magicTypography.labelMedium,
                                color = mc.textPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$percentage%",
                                style = MaterialTheme.magicTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = mc.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributionSection(title: String, data: Map<String, Int>) {
    if (data.isEmpty()) return
    val total = data.values.sum().toFloat().coerceAtLeast(1f)
    val mc    = MaterialTheme.magicColors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = mc.surface),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                data.entries.sortedByDescending { it.value }.forEach { (label, count) ->
                    val progress = count / total
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            label,
                            style    = MaterialTheme.magicTypography.bodySmall,
                            color    = mc.textPrimary,
                            modifier = Modifier.width(90.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            LinearProgressIndicator(
                                progress     = { progress },
                                modifier     = Modifier.fillMaxWidth().height(8.dp),
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
 * @param onDeleteSession Triggers deletion of a session after double confirmation.
 */
@Composable
private fun GameStatsContent(
    uiState: StatsUiState,
    toastState: com.mmg.manahub.core.ui.components.MagicToastState,
    onReviewSurvey: (Long) -> Unit,
    onDeckClick: (String) -> Unit,
    onDeleteSession: (Long) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    // Delete confirmation state: holds the session id pending first confirmation
    var showFirstConfirm  by rememberSaveable { mutableStateOf<Long?>(null) }
    // Second confirmation: holds the session id pending final irreversible confirm
    var showFinalConfirm  by rememberSaveable { mutableStateOf<Long?>(null) }

    val deleteToastLabel  = stringResource(R.string.delete_game_toast)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Spacer(Modifier.height(8.dp))

        // ── KPI grid ─────────────────────────────────────────────────────────
        uiState.gameStats?.let { gs ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        label    = stringResource(R.string.stats_kpi_avg_duration),
                        value    = formatDuration(gs.avgDurationMs),
                        modifier = Modifier.weight(1f).height(90.dp),
                    )
                    StatCard(
                        label    = stringResource(R.string.stats_kpi_favorite_mode),
                        value    = gs.favoriteMode ?: "—",
                        modifier = Modifier.weight(1f).height(90.dp),
                    )
                }

                // Pending surveys banner
                if (gs.pendingSurveys > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = CardDefaults.cardColors(containerColor = mc.goldMtg.copy(alpha = 0.15f)),
                        border   = BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text     = stringResource(R.string.stats_pending_surveys, gs.pendingSurveys),
                            style    = ty.bodySmall,
                            color    = mc.goldMtg,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }

        // ── Deck performance ─────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = mc.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
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

        // ── Session history ───────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = mc.surface),
                ) {
                    Column {
                        uiState.sessionHistory.forEachIndexed { index, item ->
                            SessionHistoryRow(
                                item            = item,
                                mc              = mc,
                                onReviewSurvey  = { onReviewSurvey(item.sessionId) },
                                onDeleteRequest = { showFirstConfirm = item.sessionId },
                            )
                            if (index < uiState.sessionHistory.size - 1) {
                                HorizontalDivider(
                                    thickness = 0.5.dp,
                                    color     = mc.surfaceVariant.copy(alpha = 0.4f),
                                    modifier  = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // ── Delete dialogs ────────────────────────────────────────────────────────

    showFirstConfirm?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { showFirstConfirm = null },
            title  = { Text(stringResource(R.string.delete_game_title), style = ty.titleMedium, color = mc.textPrimary) },
            text   = { Text(stringResource(R.string.delete_game_message_1), style = ty.bodyMedium, color = mc.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showFinalConfirm = sessionId
                    showFirstConfirm = null
                }) {
                    Text(stringResource(R.string.action_delete_game), color = mc.lifeNegative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFirstConfirm = null }) {
                    Text(stringResource(R.string.action_cancel), color = mc.textSecondary)
                }
            },
            containerColor = mc.backgroundSecondary,
        )
    }

    showFinalConfirm?.let { sessionId ->
        AlertDialog(
            onDismissRequest = { showFinalConfirm = null },
            title  = { Text(stringResource(R.string.delete_game_final_title), style = ty.titleMedium, color = mc.textPrimary) },
            text   = { Text(stringResource(R.string.delete_game_final_message), style = ty.bodyMedium, color = mc.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteSession(sessionId)
                    showFinalConfirm = null
                    toastState.show(deleteToastLabel, MagicToastType.SUCCESS)
                }) {
                    Text(stringResource(R.string.action_delete_game), color = mc.lifeNegative)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFinalConfirm = null }) {
                    Text(stringResource(R.string.action_cancel), color = mc.textSecondary)
                }
            },
            containerColor = mc.backgroundSecondary,
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeckClick() },
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
private fun SessionHistoryRow(
    item: GameHistoryItem,
    mc: MagicColors,
    onReviewSurvey: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    val ty          = MaterialTheme.magicTypography
    var menuExpanded by remember { mutableStateOf(false) }
    val locale       = Locale.getDefault()

    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Win/loss badge
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (item.isWin) mc.lifePositive.copy(alpha = 0.2f)
                    else mc.lifeNegative.copy(alpha = 0.15f),
        ) {
            Text(
                text     = if (item.isWin) "W" else "L",
                style    = ty.labelMedium.copy(fontWeight = FontWeight.Bold),
                color    = if (item.isWin) mc.lifePositive else mc.lifeNegative,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Mode + duration
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                text  = TimeAgoFormatter.format(item.playedAt, locale),
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

            // Survey status chip
            SurveyStatusChip(item = item, mc = mc)
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
                DropdownMenuItem(
                    text    = { Text(stringResource(R.string.action_review_survey)) },
                    onClick = {
                        menuExpanded = false
                        onReviewSurvey()
                    },
                )
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

@Composable
private fun SurveyStatusChip(item: GameHistoryItem, mc: MagicColors) {
    val ty = MaterialTheme.magicTypography
    val (label, color) = when (item.surveyStatus) {
        SurveyStatus.PENDING   -> stringResource(R.string.survey_pending) to mc.goldMtg.copy(alpha = 0.7f)
        SurveyStatus.PARTIAL   -> stringResource(R.string.survey_partial) to mc.goldMtg.copy(alpha = 0.5f)
        SurveyStatus.COMPLETED -> {
            val timestamp = item.surveyStatus.let {
                // Show relative time if we had surveyCompletedAt; fall back to generic label
                stringResource(R.string.survey_completed)
            }
            timestamp to mc.lifePositive.copy(alpha = 0.7f)
        }
        SurveyStatus.SKIPPED   -> stringResource(R.string.survey_skipped) to mc.textDisabled
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.15f),
    ) {
        Text(
            text     = label,
            style    = ty.labelSmall,
            color    = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}


@Composable
fun MtgColor.toDisplayName(): String = when (this) {
    MtgColor.W          -> stringResource(R.string.stats_color_white)
    MtgColor.U          -> stringResource(R.string.stats_color_blue)
    MtgColor.B          -> stringResource(R.string.stats_color_black)
    MtgColor.R          -> stringResource(R.string.stats_color_red)
    MtgColor.G          -> stringResource(R.string.stats_color_green)
    MtgColor.COLORLESS  -> stringResource(R.string.stats_color_colorless)
    else                -> stringResource(R.string.stats_color_unknown)
}
