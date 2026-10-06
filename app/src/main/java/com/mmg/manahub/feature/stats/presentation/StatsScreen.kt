package com.mmg.manahub.feature.stats.presentation

// TODO: Re-enable when Survey review for games is fully implemented
// import com.mmg.manahub.core.data.local.entity.SurveyStatus
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.svg.SvgDecoder
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
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.ui.components.ManaColorPicker
import com.mmg.manahub.core.ui.components.ManaCurveChart
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.rememberMagicToastState
import com.mmg.manahub.core.ui.components.search.SetPickerSheet
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ExtraSmallCardShape
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.coloredShadow
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.PriceFormatter
import com.mmg.manahub.core.util.TimeAgoFormatter
import com.mmg.manahub.feature.game.domain.model.ArchetypeMatchupData
import org.koin.androidx.compose.koinViewModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun StatsScreen(
    onCardClick: (scryfallId: String, sharedTransitionKey: String?) -> Unit,
    onBackClick: () -> Unit = {},
    onReviewSurvey: (sessionId: Long) -> Unit = {},
    onDeckClick: (deckId: String) -> Unit = {},
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    // KMP migration — Phase 1 Hilt→Koin cutover: Stats is the second "Koin island". This ViewModel is
    // resolved by Koin (koinViewModel()) while every other screen still uses hiltViewModel().
    viewModel: StatsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mc = MaterialTheme.magicColors
    val toastState = rememberMagicToastState()
    val deleteSuccessLabel = stringResource(R.string.delete_game_toast)
    val deleteErrorLabel = stringResource(R.string.delete_game_error)

    LaunchedEffect(uiState.deleteSessionSuccess) {
        when (uiState.deleteSessionSuccess) {
            true -> {
                toastState.show(deleteSuccessLabel, MagicToastType.SUCCESS)
                viewModel.clearDeleteSessionMessage()
            }
            false -> {
                toastState.show(deleteErrorLabel, MagicToastType.ERROR)
                viewModel.clearDeleteSessionMessage()
            }
            null -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.statusBars,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.stats_title),
                            style = MaterialTheme.magicTypography.titleLarge,
                            color = mc.textPrimary,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                                tint = mc.textPrimary,
                            )
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
                val visibleTabs =
                    buildList {
                        add(StatsTab.COLLECTION)
                        if (uiState.hasGameStats) add(StatsTab.GAMES)
                        if (uiState.hasTradeStats) add(StatsTab.TRADES)
                    }
                if (visibleTabs.size > 1) {
                    val tabLabels =
                        visibleTabs.map { tab ->
                            when (tab) {
                                StatsTab.COLLECTION -> stringResource(R.string.stats_tab_collection)
                                StatsTab.GAMES -> stringResource(R.string.stats_tab_games)
                                StatsTab.TRADES -> stringResource(R.string.stats_tab_trades)
                            }
                        }
                    // Tab display order is NOT the enum's ordinal order once a tab can be
                    // conditionally absent — always resolve the selected index by looking up the
                    // current tab within the actually-visible list, never StatsTab.entries[index].
                    val selectedIndex = visibleTabs.indexOf(uiState.selectedTab).coerceAtLeast(0)
                    TabRow(
                        selectedTabIndex = selectedIndex,
                        containerColor = mc.backgroundSecondary.copy(alpha = 0.9f),
                        contentColor = mc.primaryAccent,
                        divider = {},
                    ) {
                        visibleTabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = index == selectedIndex,
                                onClick = { viewModel.onTabSelected(tab) },
                                text = {
                                    Text(
                                        text = tabLabels[index].uppercase(),
                                        style = MaterialTheme.magicTypography.labelMedium,
                                    )
                                },
                            )
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = mc.surfaceVariant.copy(alpha = 0.5f))
                }

                when {
                    uiState.hasGameStats && uiState.selectedTab == StatsTab.GAMES -> {
                        GameStatsContent(
                            uiState = uiState,
                            onReviewSurvey = onReviewSurvey,
                            onDeckClick = onDeckClick,
                            onDeleteSession = viewModel::deleteSession,
                            onRetry = viewModel::retryGameStats,
                        )
                    }

                    uiState.hasTradeStats && uiState.selectedTab == StatsTab.TRADES -> {
                        TradeStatsContent(
                            state = uiState.tradeStats,
                            onRetry = viewModel::retryTradeStats,
                        )
                    }

                    else -> {
                        CollectionStatsContent(
                            uiState = uiState,
                            onColorSelected = viewModel::onColorSelected,
                            onSetSelected = viewModel::onSetSelected,
                            onRetry = viewModel::retryCollectionStats,
                            onCardClick = onCardClick,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                }
            }
        }

        MagicToastHost(state = toastState)
    }
}

/**
 * Premium container card with CONTEXT_HERO gradient styling, gradient border, and colored shadow.
 */
@Composable
private fun PremiumCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val defaultBorder = remember(mc.primaryAccent) {
        BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    mc.primaryAccent.copy(alpha = 0.4f),
                    mc.primaryAccent.copy(alpha = 0.12f),
                    mc.primaryAccent.copy(alpha = 0.4f),
                )
            )
        )
    }
    val backgroundBrush = remember(mc.surfaceVariant, mc.surface) {
        Brush.verticalGradient(
            colors = listOf(
                mc.surfaceVariant.copy(alpha = 0.85f),
                mc.surface.copy(alpha = 0.95f),
            )
        )
    }

    val cardModifier = modifier
        .coloredShadow(
            color = mc.primaryAccent.copy(alpha = 0.12f),
            borderRadius = 16.dp,
            blurRadius = 16.dp,
        )
        .clip(shape)
        .background(brush = backgroundBrush)

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = cardModifier,
            color = Color.Transparent,
            shape = shape,
            border = border ?: defaultBorder,
        ) {
            content()
        }
    } else {
        Surface(
            modifier = cardModifier,
            color = Color.Transparent,
            shape = shape,
            border = border ?: defaultBorder,
        ) {
            content()
        }
    }
}

/**
 * Root composable for the Collection tab content.
 *
 * Backend perf plan (2026-07-28, WS5a): the root scroll container is a [LazyColumn], not
 * `Column + verticalScroll` — each section is its own `item {}` so a section far from the current
 * scroll position is decomposed instead of staying permanently composed/retained. [SetPickerSheet]
 * is hoisted to a [Box] SIBLING of the [LazyColumn] (not an item inside it): it is a
 * `ModalBottomSheet`, which renders in its own overlay layer, so nesting it inside a lazily
 * composed item would risk the sheet being dismissed out from under the user if its host item
 * scrolled far enough to be decomposed.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun CollectionStatsContent(
    uiState: StatsUiState,
    onColorSelected: (MtgColor?) -> Unit,
    onSetSelected: (MagicSet?) -> Unit,
    onRetry: () -> Unit,
    onCardClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    val stats = uiState.stats
    var showSetPicker by rememberSaveable { mutableStateOf(false) }

    var inventoryValueExpanded by rememberSaveable { mutableStateOf(true) }
    var combatMechanicsExpanded by rememberSaveable { mutableStateOf(true) }
    var aestheticsArtExpanded by rememberSaveable { mutableStateOf(true) }
    var distributionsExpanded by rememberSaveable { mutableStateOf(true) }
    var hallOfFameExpanded by rememberSaveable { mutableStateOf(true) }
    var setCompletionExpanded by rememberSaveable { mutableStateOf(true) }
    var collectionEraExpanded by rememberSaveable { mutableStateOf(true) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(sp.xl),
        ) {
            item(key = "collection_filters") {
                // Color Filter Row
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = sp.xl, vertical = sp.sm),
                    verticalArrangement = Arrangement.spacedBy(sp.lg),
                ) {
                    ManaColorPicker(
                        selectedColors = uiState.selectedColor?.let { setOf(it.name.take(1)) } ?: emptySet(),
                        onToggleColor = { colorCode ->
                            val color =
                                when (colorCode) {
                                    "W" -> MtgColor.W
                                    "U" -> MtgColor.U
                                    "B" -> MtgColor.B
                                    "R" -> MtgColor.R
                                    "G" -> MtgColor.G
                                    "C" -> MtgColor.COLORLESS
                                    "M" -> MtgColor.MULTICOLOR
                                    else -> null
                                }
                            onColorSelected(color)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        spacing = sp.sm,
                        itemSize = 48.dp,
                        symbolSize = 32.dp,
                        isMultiColorExclusive = false,
                        colors = listOf("W", "U", "B", "R", "G", "M", "C"),
                    )

                    SetFilterRow(
                        selectedSet = uiState.selectedSet,
                        onClick = { showSetPicker = true },
                        onClear = { onSetSelected(null) },
                        mc = mc,
                    )

                    // Read-only price-freshness label
                    uiState.lastRefreshedAt?.let { lastRefreshedAt ->
                        Text(
                            text = "Updated ${TimeAgoFormatter.format(lastRefreshedAt)}",
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textSecondary,
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                item(key = "collection_loading") {
                    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        MagicLoadingSpinner()
                    }
                }
            } else if (uiState.error != null) {
                item(key = "collection_error", contentType = "error") {
                    InlineErrorState(
                        message = stringResource(R.string.stats_collection_error),
                        retryLabel = stringResource(R.string.action_retry),
                        onRetry = onRetry,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = sp.lg),
                    )
                }
            } else if (stats != null && stats.totalCards == 0 && uiState.selectedColor == null && uiState.selectedSet == null) {
                item(key = "collection_empty", contentType = "empty") {
                    EmptyState(
                        title = stringResource(R.string.stats_collection_empty_title),
                        subtitle = stringResource(R.string.stats_collection_empty_subtitle),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (stats != null) {
                // 1. Inventory & Value
                item(key = "collection_section_inventory_value") {
                    StatsSection(
                        modifier = Modifier.padding(horizontal = sp.lg),
                        title = stringResource(R.string.stats_section_inventory_value),
                        icon = Icons.Default.Star,
                        expanded = inventoryValueExpanded,
                        onToggle = { inventoryValueExpanded = !inventoryValueExpanded },
                    ) {
                        InventoryValueGrid(stats = stats, currency = uiState.currency)
                    }
                }

                // 2. Combat & Mechanics
                item(key = "collection_section_combat_mechanics") {
                    StatsSection(
                        modifier = Modifier.padding(horizontal = sp.lg),
                        title = stringResource(R.string.stats_section_combat_mechanics),
                        icon = painterResource(R.drawable.ic_battle),
                        expanded = combatMechanicsExpanded,
                        onToggle = { combatMechanicsExpanded = !combatMechanicsExpanded },
                    ) {
                        CombatMechanicsSection(stats = stats)
                    }
                }

                // 3. Aesthetics & Art
                item(key = "collection_section_aesthetics_art") {
                    StatsSection(
                        modifier = Modifier.padding(horizontal = sp.lg),
                        title = stringResource(R.string.stats_section_aesthetics_art),
                        icon = Icons.Default.Star,
                        expanded = aestheticsArtExpanded,
                        onToggle = { aestheticsArtExpanded = !aestheticsArtExpanded },
                    ) {
                        AestheticsArtSection(
                            stats = stats,
                            onCardClick = onCardClick,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                }

                // 4. Distributions
                item(key = "collection_section_distributions") {
                    StatsSection(
                        modifier = Modifier.padding(horizontal = sp.lg),
                        title = stringResource(R.string.stats_section_distributions),
                        icon = Icons.Default.BarChart,
                        expanded = distributionsExpanded,
                        onToggle = { distributionsExpanded = !distributionsExpanded },
                    ) {
                        DistributionsSection(stats = stats, mc = mc)
                    }
                }

                // 5. Hall of Fame
                item(key = "collection_section_hall_of_fame") {
                    StatsSection(
                        modifier = Modifier.padding(horizontal = sp.lg),
                        title = stringResource(R.string.stats_section_hall_of_fame),
                        icon = Icons.Default.Star,
                        expanded = hallOfFameExpanded,
                        onToggle = { hallOfFameExpanded = !hallOfFameExpanded },
                    ) {
                        HallOfFameSection(
                            stats = stats,
                            currency = uiState.currency,
                            onCardClick = onCardClick,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                }

                // 6. Set Completion
                if (uiState.setCompletions.isNotEmpty()) {
                    item(key = "collection_section_set_completion") {
                        StatsSection(
                            modifier = Modifier.padding(horizontal = sp.lg),
                            title = stringResource(R.string.stats_section_set_completion),
                            icon = Icons.Default.Layers,
                            expanded = setCompletionExpanded,
                            onToggle = { setCompletionExpanded = !setCompletionExpanded },
                        ) {
                            SetCompletionSection(completions = uiState.setCompletions)
                        }
                    }
                }

                // 7. Collection Era
                if (stats.decadeDistribution.isNotEmpty()) {
                    item(key = "collection_section_era") {
                        StatsSection(
                            modifier = Modifier.padding(horizontal = sp.lg),
                            title = stringResource(R.string.stats_label_collection_era),
                            icon = Icons.Default.BarChart,
                            expanded = collectionEraExpanded,
                            onToggle = { collectionEraExpanded = !collectionEraExpanded },
                        ) {
                            val decadeOrder = remember(stats.decadeDistribution) { stats.decadeDistribution.keys.sorted() }
                            CircularDistributionSection(
                                title = null,
                                data = stats.decadeDistribution,
                                colorMapper = { label ->
                                    val index = decadeOrder.indexOf(label).coerceAtLeast(0)
                                    val palette =
                                        listOf(
                                            mc.primaryAccent,
                                            mc.secondaryAccent,
                                            mc.goldMtg,
                                            mc.lifePositive,
                                            mc.lifeNegative,
                                            mc.manaU,
                                            mc.manaR,
                                            mc.manaG,
                                            mc.manaW,
                                        )
                                    palette[index % palette.size]
                                },
                            )
                        }
                    }
                }

                item(key = "collection_bottom_spacer") {
                    Spacer(Modifier.height(sp.xxl))
                }
            }
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
                singleSelection = true,
            )
        }
    }
}

@Composable
private fun SetFilterRow(
    selectedSet: MagicSet?,
    onClick: () -> Unit,
    onClear: () -> Unit,
    mc: MagicColors,
) {
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    PremiumCard(
        onClick = onClick,
        shape = CardShape,
        border =
            BorderStroke(
                width = if (selectedSet != null) 1.5.dp else 1.dp,
                color = if (selectedSet != null) mc.primaryAccent else mc.surfaceVariant.copy(alpha = 0.4f),
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
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
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
                overflow = TextOverflow.Ellipsis,
            )

            if (selectedSet != null) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.stats_clear_set_filter), tint = mc.textSecondary, modifier = Modifier.size(16.dp))
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
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(sp.md)) {
        when (icon) {
            is ImageVector -> {
                SectionHeader(
                    title = title.uppercase(),
                    expanded = expanded,
                    onToggle = onToggle,
                    icon = icon,
                    iconColor = mc.primaryAccent,
                    titleColor = mc.textPrimary,
                )
            }

            is Painter -> {
                SectionHeader(
                    title = title.uppercase(),
                    expanded = expanded,
                    onToggle = onToggle,
                    titleColor = mc.textPrimary,
                    leading = {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            tint = mc.primaryAccent,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            content()
        }
    }
}

@Composable
private fun InventoryValueGrid(
    stats: CollectionStats,
    currency: PreferredCurrency,
) {
    val sp = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
            StatCard(
                label = stringResource(R.string.stats_total_cards),
                value = stats.totalCards.toString(),
                modifier = Modifier.weight(1f).heightIn(min = 60.dp),
            )
            StatCard(
                label = stringResource(R.string.stats_unique_cards),
                value = stats.uniqueCards.toString(),
                modifier = Modifier.weight(1f).heightIn(min = 60.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
            val totalValue = if (currency == PreferredCurrency.USD) stats.totalValueUsd else stats.totalValueEur
            CurrencyStatCard(
                label = stringResource(R.string.stats_label_est_value),
                value = PriceFormatter.format(totalValue, currency),
                modifier = Modifier.weight(1f).heightIn(min = 60.dp),
            )
            StatCard(
                label = stringResource(R.string.stats_decks_saved),
                value = stats.totalDecks.toString(),
                modifier = Modifier.weight(1f).heightIn(min = 60.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
            CurrencyStatCard(
                label = stringResource(R.string.stats_label_avg_card_value),
                value = PriceFormatter.format(stats.avgCardValue, currency),
                modifier = Modifier.weight(1f).heightIn(min = 60.dp),
            )
            CurrencyStatCard(
                label = stringResource(R.string.stats_label_median_card_value),
                value = PriceFormatter.format(stats.medianCardValue, currency),
                modifier = Modifier.weight(1f).heightIn(min = 60.dp),
            )
        }
    }
}

/** Currency-value variant of [StatCard]: smaller/gold value text, single line, ellipsized. */
@Composable
private fun CurrencyStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    PremiumCard(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = sp.md, vertical = sp.sm).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = value,
                style = MaterialTheme.magicTypography.titleLarge,
                color = mc.goldMtg,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CombatMechanicsSection(stats: CollectionStats) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    val manaGradient =
        Brush.linearGradient(
            colors = listOf(mc.manaW, mc.manaU, mc.manaB, mc.manaR, mc.manaG),
        )

    Column(verticalArrangement = Arrangement.spacedBy(sp.lg)) {
        Row(horizontalArrangement = Arrangement.spacedBy(sp.md), verticalAlignment = Alignment.CenterVertically) {
            CombatStatsBox(
                avgPower = stats.avgPower ?: 0.0,
                avgToughness = stats.avgToughness ?: 0.0,
                modifier = Modifier.weight(1.4f),
            )

            PremiumCard(
                modifier = Modifier.weight(0.6f).heightIn(min = 100.dp),
                border = BorderStroke(1.dp, manaGradient),
            ) {
                Column(
                    modifier = Modifier.padding(sp.md).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "%.2f".format(stats.avgManaValue),
                        style = MaterialTheme.magicTypography.titleLarge,
                        color = mc.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.stats_label_avg_mana_value),
                        style = MaterialTheme.magicTypography.labelMedium,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        ManaCurveChart(
            cmcDistribution = stats.cmcDistribution,
            modifier = Modifier.fillMaxWidth(),
            title = stringResource(R.string.stats_mana_curve),
        )
    }
}

@Composable
private fun CombatStatsBox(
    avgPower: Double,
    avgToughness: Double,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    PremiumCard(
        modifier = modifier.heightIn(min = 100.dp),
        border = BorderStroke(1.dp, mc.goldMtg.copy(alpha = 0.5f)),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = sp.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_battle),
                        contentDescription = null,
                        tint = mc.secondaryAccent.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "%.1f".format(avgPower),
                        style = MaterialTheme.magicTypography.displayMedium,
                        color = mc.textPrimary,
                    )
                    Text(
                        text = "AVG\nPOWER",
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                Text(
                    text = "/",
                    style = MaterialTheme.magicTypography.displayMedium,
                    color = mc.goldMtg,
                    modifier = Modifier.padding(horizontal = sp.sm),
                )

                Column(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = mc.goldMtg.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "%.1f".format(avgToughness),
                        style = MaterialTheme.magicTypography.displayMedium,
                        color = mc.textPrimary,
                    )
                    Text(
                        text = "AVG\nTOUGHNESS",
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
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
            modifier = Modifier.fillMaxWidth(),
        )

        CuriousStatBox(
            label = stringResource(R.string.stats_label_top_artist),
            value = stats.topArtist ?: stringResource(R.string.stats_not_available),
            supportingText =
                if (stats.topArtistCount >
                    0
                ) {
                    stringResource(R.string.stats_artist_cards_count, stats.topArtistCount)
                } else {
                    null
                },
            mc = mc,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (stats.topArtistCards.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(sp.md),
                    contentPadding = PaddingValues(horizontal = sp.xxs),
                    modifier = Modifier.padding(top = sp.md),
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
    val imageModifier =
        Modifier
            .width(100.dp)
            .aspectRatio(0.717f)
            .clip(SmallCardShape)

    val finalImageModifier =
        if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                imageModifier.sharedBounds(
                    sharedContentState = rememberSharedContentState(key = sharedTransitionKey),
                    animatedVisibilityScope = animatedVisibilityScope,
                    clipInOverlayDuringTransition = OverlayClip(SmallCardShape),
                    boundsTransform = { _, _ -> tween(durationMillis = 500, easing = FastOutSlowInEasing) },
                    renderInOverlayDuringTransition = true,
                )
            }
        } else {
            imageModifier
        }

    AsyncImage(
        model = card.imageNormal,
        contentDescription = card.name,
        contentScale = ContentScale.Fit,
        modifier =
            finalImageModifier
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
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    val safePercentage = percentage.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

    val foilColors =
        listOf(
            mc.secondaryAccent,
            mc.primaryAccent,
            mc.goldMtg,
            mc.manaG,
        )
    val foilBrush = Brush.linearGradient(colors = foilColors)

    PremiumCard(
        modifier = modifier.heightIn(min = 115.dp),
        border = if (isFoil) BorderStroke(1.5.dp, foilBrush) else null,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isFoil) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(colors = foilColors.map { it.copy(alpha = 0.12f) })),
                )
            }

            Column(Modifier.padding(sp.lg).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.magicTypography.displayMedium,
                        color = mc.textPrimary,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            text = "${(safePercentage * 100).toInt()}% " + stringResource(R.string.stats_label_total).lowercase(),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textSecondary,
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(mc.surfaceVariant.copy(alpha = 0.3f)),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(safePercentage)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(
                                        if (isFoil) foilBrush else Brush.horizontalGradient(listOf(mc.primaryAccent, mc.secondaryAccent)),
                                    ),
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
                        card = card,
                        currency = currency,
                        mc = mc,
                        onCardClick = { onCardClick(card.scryfallId, key) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedTransitionKey = key,
                    )
                    Text(
                        stringResource(R.string.stats_label_oldest_card),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            stats.newestCard?.let { card ->
                val key = "stats-newest-${card.scryfallId}"
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(sp.sm)) {
                    HistoryCard(
                        card = card,
                        currency = currency,
                        mc = mc,
                        onCardClick = { onCardClick(card.scryfallId, key) },
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        sharedTransitionKey = key,
                    )
                    Text(
                        stringResource(R.string.stats_label_newest_card),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

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
                        Text(
                            stringResource(R.string.stats_label_most_duplicated_card),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
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
                        Text(
                            stringResource(R.string.stats_label_most_variants_card),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } ?: Spacer(modifier = Modifier.weight(1f))
            }
        }

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
 * Generic Hall of Fame card tile.
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
    PremiumCard(
        modifier = modifier,
        onClick = onCardClick,
    ) {
        Column {
            val imageModifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
            val finalImageModifier =
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        imageModifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = sharedTransitionKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                            clipInOverlayDuringTransition = OverlayClip(CardShape),
                            boundsTransform = { _, _ -> tween(durationMillis = 500, easing = FastOutSlowInEasing) },
                            renderInOverlayDuringTransition = true,
                        )
                    }
                } else {
                    imageModifier
                }

            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = null,
                modifier = finalImageModifier,
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.padding(sp.md)) {
                CardName(
                    card.name,
                    style = MaterialTheme.magicTypography.labelMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

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
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(sp.md))

                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
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
private fun DistributionsSection(
    stats: CollectionStats,
    mc: MagicColors,
) {
    val whiteName = stringResource(R.string.stats_color_white)
    val blueName = stringResource(R.string.stats_color_blue)
    val blackName = stringResource(R.string.stats_color_black)
    val redName = stringResource(R.string.stats_color_red)
    val greenName = stringResource(R.string.stats_color_green)
    val colorlessName = stringResource(R.string.stats_color_colorless)
    val multicolorName = stringResource(R.string.collection_filter_multicolor)
    val unknownName = stringResource(R.string.stats_color_unknown)

    val colorData =
        remember(stats.byColor, whiteName, blueName, blackName, redName, greenName, colorlessName, multicolorName, unknownName) {
            stats.byColor.entries.associate { (color, count) ->
                val name =
                    when (color) {
                        MtgColor.W -> whiteName
                        MtgColor.U -> blueName
                        MtgColor.B -> blackName
                        MtgColor.R -> redName
                        MtgColor.G -> greenName
                        MtgColor.COLORLESS -> colorlessName
                        MtgColor.MULTICOLOR -> multicolorName
                        else -> unknownName
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
                    whiteName -> mc.manaW
                    blueName -> mc.manaU
                    blackName -> mc.manaB
                    redName -> mc.manaR
                    greenName -> mc.manaG
                    colorlessName -> mc.manaC
                    else -> mc.primaryAccent
                }
            },
            isColor = true,
            displayTotal = stats.totalCards,
        )
        CircularDistributionSection(
            title = stringResource(R.string.stats_dist_type),
            data =
                stats.byType.entries.associate {
                    it.key.name
                        .lowercase()
                        .replaceFirstChar { c -> c.uppercase() } to it.value
                },
            colorMapper = { label ->
                val types = listOf("Creature", "Instant", "Sorcery", "Enchantment", "Artifact", "Planeswalker", "Land", "Battle", "Other")
                val index = types.indexOf(label).coerceAtLeast(0)
                val palette =
                    listOf(
                        mc.primaryAccent,
                        mc.secondaryAccent,
                        mc.goldMtg,
                        mc.lifePositive,
                        mc.lifeNegative,
                        mc.commanderAccent,
                        mc.goldMtg,
                        mc.textSecondary,
                        mc.manaU,
                        mc.manaR,
                        mc.manaG,
                        mc.manaB,
                    )
                palette[index % palette.size]
            },
        )
        CircularDistributionSection(
            title = stringResource(R.string.stats_dist_rarity),
            data =
                stats.byRarity.entries.associate {
                    it.key.name
                        .lowercase()
                        .replaceFirstChar { c -> c.uppercase() } to it.value
                },
            colorMapper = { label ->
                when (label.lowercase()) {
                    "common" -> mc.textSecondary
                    "uncommon" -> mc.manaU
                    "rare" -> mc.goldMtg
                    "mythic" -> mc.manaR
                    else -> mc.primaryAccent
                }
            },
        )

        DistributionSection(title = stringResource(R.string.stats_dist_strategy), data = stats.autoTagDistribution)

        DistributionSection(title = stringResource(R.string.stats_dist_keywords), data = stats.keywordDistribution)
        FormatCoverageRow(coverage = stats.formatCoverage, mc = mc)
    }
}

/** Compact 3-chip row of distinct owned cards legal per format (Phase 2). */
@Composable
private fun FormatCoverageRow(
    coverage: Map<String, Int>,
    mc: MagicColors,
) {
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
                PremiumCard(
                    modifier = Modifier.weight(1f),
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
    PremiumCard(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = sp.md, vertical = sp.sm).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                value,
                style = MaterialTheme.magicTypography.titleLarge,
                color = valueColor ?: mc.textPrimary,
                textAlign = TextAlign.Center,
            )
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
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val sp = MaterialTheme.spacing
    PremiumCard(
        modifier = modifier,
    ) {
        Column(Modifier.padding(sp.md)) {
            Text(label, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
            Spacer(Modifier.height(sp.xs))
            Text(
                value,
                style = MaterialTheme.magicTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            supportingText?.let {
                Text(it, style = MaterialTheme.magicTypography.labelSmall, color = mc.textSecondary)
            }

            if (percentage != null) {
                Spacer(Modifier.height(sp.md))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(mc.textDisabled.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth(percentage.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(mc.secondaryAccent, mc.goldMtg),
                                    ),
                                ),
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
    modifier: Modifier = Modifier,
) {
    val sp = MaterialTheme.spacing
    PremiumCard(
        modifier = modifier,
        onClick = onCardClick,
    ) {
        Column {
            val imageModifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)
            val finalImageModifier =
                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                    with(sharedTransitionScope) {
                        imageModifier.sharedBounds(
                            sharedContentState = rememberSharedContentState(key = sharedTransitionKey),
                            animatedVisibilityScope = animatedVisibilityScope,
                            clipInOverlayDuringTransition = OverlayClip(CardShape),
                            boundsTransform = { _, _ -> tween(durationMillis = 500, easing = FastOutSlowInEasing) },
                            renderInOverlayDuringTransition = true,
                        )
                    }
                } else {
                    imageModifier
                }

            AsyncImage(
                model = card.imageArtCrop,
                contentDescription = null,
                modifier = finalImageModifier,
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.padding(sp.md)) {
                CardName(
                    card.name,
                    style = MaterialTheme.magicTypography.labelMedium,
                    color = mc.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

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
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(sp.md))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.stats_label_est_value),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                    )
                    val price = if (currency == PreferredCurrency.USD) card.priceUsd else card.priceEur
                    if (price > 0) {
                        Text(
                            PriceFormatter.format(price, currency),
                            style = MaterialTheme.magicTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = mc.goldMtg,
                        )
                    } else {
                        Text(
                            "—",
                            style = MaterialTheme.magicTypography.labelMedium,
                            color = mc.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MostValuableSection(
    cards: List<CardValue>,
    currency: PreferredCurrency,
    onCardClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    val filteredCards =
        cards.filter { card ->
            (if (currency == PreferredCurrency.USD) card.priceUsd else card.priceEur) > 0
        }
    if (filteredCards.isEmpty()) return

    PremiumCard {
        Column {
            filteredCards.forEachIndexed { index, card ->
                val key = "stats-valuable-${card.scryfallId}-${card.isFoil}"
                ListItem(
                    modifier = Modifier.clickable { onCardClick(card.scryfallId, key) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    headlineContent = {
                        CardName(
                            name = card.name,
                            showFrontOnly = true,
                            color = mc.textPrimary,
                            style = MaterialTheme.magicTypography.labelMedium,
                        )
                    },
                    leadingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                            Text(
                                text = "#${index + 1}",
                                style = MaterialTheme.magicTypography.labelLarge,
                                color = mc.primaryAccent.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(32.dp),
                            )
                            val imageModifier = Modifier.width(40.dp).height(56.dp).clip(ExtraSmallCardShape)
                            val finalImageModifier =
                                if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                    with(sharedTransitionScope) {
                                        imageModifier.sharedBounds(
                                            sharedContentState = rememberSharedContentState(key = key),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                            clipInOverlayDuringTransition = OverlayClip(ExtraSmallCardShape),
                                            boundsTransform = { _, _ -> tween(durationMillis = 500, easing = FastOutSlowInEasing) },
                                            renderInOverlayDuringTransition = true,
                                        )
                                    }
                                } else {
                                    imageModifier
                                }
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
                            text = PriceFormatter.format(price, currency),
                            style = MaterialTheme.magicTypography.labelLarge,
                            color = mc.goldMtg,
                        )
                    },
                    supportingContent =
                        if (card.isFoil) {
                            {
                                Text(
                                    stringResource(R.string.shared_foil),
                                    style = MaterialTheme.magicTypography.labelSmall,
                                    color = mc.goldMtg.copy(alpha = 0.7f),
                                )
                            }
                        } else {
                            null
                        },
                )
                if (index <
                    filteredCards.size - 1
                ) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = mc.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = sp.lg),
                    )
                }
            }
        }
    }
}

@Composable
private fun SetStatsSection(
    stats: CollectionStats,
    currency: PreferredCurrency,
    mc: MagicColors,
) {
    val sp = MaterialTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        stats.topSetByCount?.let { (setCode, count) ->
            PremiumCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier.padding(sp.lg).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.stats_label_most_owned_set),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(sp.md))
                    SetSymbol(setCode = setCode, rarity = CardRarity.RARE, size = 42.dp)
                    Spacer(Modifier.height(sp.md))
                    Text(
                        setCode.uppercase(),
                        style = MaterialTheme.magicTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = mc.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.collection_card_count, count),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        stats.topSetByValue?.let { (setCode, value) ->
            PremiumCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier.padding(sp.lg).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.stats_label_most_valuable_set),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(sp.md))
                    SetSymbol(setCode = setCode, rarity = CardRarity.MYTHIC, size = 42.dp)
                    Spacer(Modifier.height(sp.md))
                    Text(
                        PriceFormatter.format(value, currency),
                        style = MaterialTheme.magicTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = mc.goldMtg,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        setCode.uppercase(),
                        style = MaterialTheme.magicTypography.labelSmall,
                        color = mc.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun CircularDistributionSection(
    title: String? = null,
    data: Map<String, Int>,
    colorMapper: (String) -> Color,
    isColor: Boolean = false,
    displayTotal: Int? = null,
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(sp.lg)) {
        if (!title.isNullOrEmpty()) {
            Text(title, style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        }
        if (isColor) {
            Text(
                text = stringResource(R.string.stats_color_distribution_hint),
                style = MaterialTheme.magicTypography.labelSmall,
                color = mc.textSecondary,
            )
        }

        PremiumCard {
            CircularDistribution(
                data = data,
                colorMapper = colorMapper,
                isColor = isColor,
                displayTotal = displayTotal,
                modifier = Modifier.padding(vertical = sp.md, horizontal = sp.lg),
            )
        }
    }
}

@Composable
private fun DistributionSection(
    title: String,
    data: Map<String, Int>,
) {
    if (data.isEmpty()) return
    val total =
        data.values
            .sum()
            .toFloat()
            .coerceAtLeast(1f)
    val mc = MaterialTheme.magicColors
    val sp = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
        Text(title, style = MaterialTheme.magicTypography.titleMedium, color = mc.textPrimary)
        PremiumCard(
            modifier = Modifier.padding(bottom = sp.xs),
        ) {
            Column(Modifier.padding(sp.lg), verticalArrangement = Arrangement.spacedBy(sp.md)) {
                data.entries.sortedByDescending { it.value }.forEach { (label, count) ->
                    val progress = count / total
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(sp.md),
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textPrimary,
                            modifier = Modifier.width(90.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                                color = mc.primaryAccent,
                                trackColor = mc.surfaceVariant,
                                strokeCap = StrokeCap.Butt,
                            )
                        }
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.magicTypography.labelSmall,
                            color = mc.textPrimary,
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
    PremiumCard {
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
                                    model =
                                        ImageRequest
                                            .Builder(LocalContext.current)
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

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}m ${seconds.toString().padStart(2, '0')}s"
}

@Composable
private fun GameStatsContent(
    uiState: StatsUiState,
    onReviewSurvey: (Long) -> Unit,
    onDeckClick: (String) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    var showFirstConfirm by rememberSaveable { mutableStateOf<Long?>(null) }
    var showFinalConfirm by rememberSaveable { mutableStateOf<Long?>(null) }

    if (uiState.gameError) {
        InlineErrorState(
            message = stringResource(R.string.stats_games_error),
            retryLabel = stringResource(R.string.action_retry),
            onRetry = onRetry,
            modifier = Modifier.fillMaxWidth().padding(sp.lg),
        )
        return
    }
    if (uiState.gameStats == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MagicLoadingSpinner() }
        return
    }

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = sp.lg),
        verticalArrangement = Arrangement.spacedBy(sp.xl),
    ) {
        item(key = "games_top_spacer") { Spacer(Modifier.height(sp.sm)) }

        uiState.gameStats?.let { gs ->
            item(key = "games_kpi_grid") {
                Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                        StatCard(
                            label = stringResource(R.string.stats_kpi_total_games),
                            value = gs.totalGames.toString(),
                            modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                        )
                        StatCard(
                            label = stringResource(R.string.stats_kpi_winrate),
                            value = "${(gs.winrate * 100).toInt()}%",
                            modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                        StatCard(
                            label = stringResource(R.string.stats_kpi_avg_duration),
                            value = formatDuration(gs.avgDurationMs),
                            modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                        )
                        StatCard(
                            label = stringResource(R.string.stats_kpi_most_common_defeat),
                            value = gs.mostFrequentLoss ?: "—",
                            modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                        StatCard(
                            label = stringResource(R.string.stats_kpi_current_streak),
                            value = gs.currentStreak.toString(),
                            modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                        )
                        StatCard(
                            label = stringResource(R.string.stats_kpi_best_streak),
                            value = gs.bestStreak.toString(),
                            modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                        )
                    }
                }
            }
        }

        if (uiState.modeWinrates.isNotEmpty()) {
            item(key = "games_winrate_by_mode") {
                Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                    Text(
                        text = stringResource(R.string.stats_section_winrate_by_mode).uppercase(),
                        style = ty.labelLarge,
                        color = mc.textPrimary,
                        letterSpacing = 2.sp,
                    )
                    PremiumCard {
                        Column(
                            modifier = Modifier.padding(sp.lg),
                            verticalArrangement = Arrangement.spacedBy(sp.lg),
                        ) {
                            uiState.modeWinrates.forEach { item -> ModeWinrateRow(item = item, mc = mc) }
                        }
                    }
                }
            }
        }

        if (uiState.playerCountWinrates.isNotEmpty()) {
            item(key = "games_winrate_by_player_count") {
                Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                    Text(
                        text = stringResource(R.string.stats_section_winrate_by_player_count).uppercase(),
                        style = ty.labelLarge,
                        color = mc.textPrimary,
                        letterSpacing = 2.sp,
                    )
                    PremiumCard {
                        Column(
                            modifier = Modifier.padding(sp.lg),
                            verticalArrangement = Arrangement.spacedBy(sp.lg),
                        ) {
                            uiState.playerCountWinrates.forEach { item -> PlayerCountWinrateRow(item = item, mc = mc) }
                        }
                    }
                }
            }
        }

        item(key = "games_deck_performance") {
            Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                Text(
                    text = stringResource(R.string.stats_section_deck_performance).uppercase(),
                    style = ty.labelLarge,
                    color = mc.textPrimary,
                    letterSpacing = 2.sp,
                )

                if (uiState.deckPerformance.isEmpty()) {
                    EmptyState(
                        title = stringResource(R.string.stats_section_deck_performance),
                        subtitle = stringResource(R.string.stats_empty_decks_subtitle),
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                    )
                } else {
                    PremiumCard {
                        Column(
                            modifier = Modifier.padding(sp.lg),
                            verticalArrangement = Arrangement.spacedBy(sp.lg),
                        ) {
                            uiState.deckPerformance.forEach { deck ->
                                DeckPerformanceRow(
                                    deck = deck,
                                    mc = mc,
                                    onDeckClick = { onDeckClick(deck.deckId) },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.archetypeMatchups.isNotEmpty()) {
            item(key = "games_archetype_matchups") {
                Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                    Text(
                        text = stringResource(R.string.stats_section_matchups).uppercase(),
                        style = ty.labelLarge,
                        color = mc.textPrimary,
                        letterSpacing = 2.sp,
                    )
                    PremiumCard {
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
        }

        if (uiState.recentForm.isNotEmpty()) {
            item(key = "games_recent_form") {
                Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                    Text(
                        text = stringResource(R.string.stats_section_recent_form).uppercase(),
                        style = ty.labelLarge,
                        color = mc.textPrimary,
                        letterSpacing = 2.sp,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(sp.xs)) {
                        uiState.recentForm.forEach { entry ->
                            key(entry.sessionId) {
                                RecentFormBadge(isWin = entry.isWin, isDraw = entry.isDraw, mc = mc)
                            }
                        }
                    }
                }
            }
        }

        item(key = "games_session_history_header", contentType = "header") {
            Text(
                text = stringResource(R.string.stats_section_history).uppercase(),
                style = ty.labelLarge,
                color = mc.textPrimary,
                letterSpacing = 2.sp,
            )
        }
        if (uiState.sessionHistory.isEmpty()) {
            item(key = "games_session_history_empty", contentType = "empty") {
                EmptyState(
                    title = stringResource(R.string.stats_section_history),
                    subtitle = stringResource(R.string.stats_empty_history_subtitle),
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
            }
        } else {
            items(
                items = uiState.sessionHistory,
                key = { "history_${it.sessionId}" },
                contentType = { "history_row" },
            ) { item ->
                PremiumCard {
                    SessionHistoryRow(
                        item = item,
                        mc = mc,
                        onDeleteRequest = { showFirstConfirm = item.sessionId },
                    )
                }
            }
        }

        item(key = "games_bottom_spacer") {
            Spacer(Modifier.height(sp.xxl))
        }
    }

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
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.stats_open_deck)) { onDeckClick() },
        verticalArrangement = Arrangement.spacedBy(sp.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = deck.deckName,
                style = ty.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${deck.wins}/${deck.totalGames}",
                style = ty.labelMedium,
                color = mc.textSecondary,
            )
        }
        LinearProgressIndicator(
            progress = { deck.winrate },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = mc.primaryAccent,
            trackColor = mc.surfaceVariant,
        )
    }
}

@Composable
private fun ArchetypeMatchupItem(
    matchup: ArchetypeMatchupData,
    mc: MagicColors,
) {
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val winrate = if (matchup.totalGames > 0) matchup.wins.toFloat() / matchup.totalGames else 0f
    Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = matchup.opponentArchetype,
                style = ty.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${matchup.wins}/${matchup.totalGames}",
                style = ty.labelMedium,
                color = mc.textSecondary,
            )
        }
        LinearProgressIndicator(
            progress = { winrate },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = mc.primaryAccent,
            trackColor = mc.surfaceVariant,
        )
    }
}

@Composable
private fun ModeWinrateRow(
    item: ModeWinrateItem,
    mc: MagicColors,
) {
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.mode,
                style = ty.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = mc.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${item.wins}/${item.totalGames}",
                style = ty.labelMedium,
                color = mc.textSecondary,
            )
        }
        LinearProgressIndicator(
            progress = { item.winrate },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = mc.primaryAccent,
            trackColor = mc.textDisabled.copy(alpha = 0.25f),
        )
    }
}

@Composable
private fun PlayerCountWinrateRow(
    item: PlayerCountWinrateItem,
    mc: MagicColors,
) {
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    val label =
        if (item.isFourPlus) {
            stringResource(R.string.stats_player_count_four_plus)
        } else {
            stringResource(R.string.stats_player_count_n, item.playerCount)
        }
    Column(verticalArrangement = Arrangement.spacedBy(sp.xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = ty.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = mc.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${item.wins}/${item.totalGames}",
                style = ty.labelMedium,
                color = mc.textSecondary,
            )
        }
        LinearProgressIndicator(
            progress = { item.winrate },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = mc.primaryAccent,
            trackColor = mc.textDisabled.copy(alpha = 0.25f),
        )
    }
}

@Composable
private fun RecentFormBadge(
    isWin: Boolean,
    isDraw: Boolean,
    mc: MagicColors,
) {
    val ty = MaterialTheme.magicTypography
    val badgeColor = when {
        isDraw -> mc.textSecondary
        isWin -> mc.lifePositive
        else -> mc.lifeNegative
    }
    Surface(
        shape = CardShape,
        color = badgeColor.copy(alpha = 0.2f),
        modifier = Modifier.size(28.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = when { isDraw -> stringResource(R.string.stats_result_draw); isWin -> stringResource(R.string.stats_result_win); else -> stringResource(R.string.stats_result_loss) },
                style = ty.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = badgeColor,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Trades tab
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TradeStatsContent(
    state: TradeStatsUiState,
    onRetry: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing

    when (state) {
        is TradeStatsUiState.Idle, is TradeStatsUiState.Loading -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MagicLoadingSpinner()
                Spacer(Modifier.height(sp.md))
                Text(
                    text = stringResource(R.string.stats_trades_loading),
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                )
            }
        }

        is TradeStatsUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().padding(sp.xl), contentAlignment = Alignment.TopCenter) {
                InlineErrorState(
                    message = stringResource(R.string.stats_trades_error),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = onRetry,
                )
            }
        }

        is TradeStatsUiState.Content -> {
            val stats = state.stats
            if (stats.completedTradesCount == 0) {
                EmptyState(
                    title = stringResource(R.string.stats_trades_empty_title),
                    subtitle = stringResource(R.string.stats_trades_empty_subtitle),
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = sp.lg),
                    verticalArrangement = Arrangement.spacedBy(sp.xl),
                ) {
                    item(key = "trades_top_spacer") { Spacer(Modifier.height(sp.sm)) }

                    item(key = "trades_kpi_grid") {
                        Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                                StatCard(
                                    label = stringResource(R.string.stats_kpi_completed_trades),
                                    value = stats.completedTradesCount.toString(),
                                    modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                                )
                                StatCard(
                                    label = stringResource(R.string.stats_kpi_cards_sent),
                                    value = stats.cardsSent.toString(),
                                    modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(sp.md)) {
                                StatCard(
                                    label = stringResource(R.string.stats_kpi_cards_received),
                                    value = stats.cardsReceived.toString(),
                                    modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                                )
                                val deltaSign = if (stats.netValueDelta >= 0) "+" else "−"
                                val deltaMagnitude = PriceFormatter.format(abs(stats.netValueDelta), stats.currency)
                                StatCard(
                                    label = stringResource(R.string.stats_kpi_net_value),
                                    value = "$deltaSign$deltaMagnitude",
                                    valueColor = if (stats.netValueDelta >= 0) mc.lifePositive else mc.lifeNegative,
                                    modifier = Modifier.weight(1f).heightIn(min = 60.dp),
                                )
                            }
                        }
                    }

                    item(key = "trades_net_value_note") {
                        Text(
                            text = stringResource(R.string.stats_trades_net_value_note),
                            style = ty.labelSmall,
                            color = mc.textSecondary,
                        )
                    }

                    stats.topPartner?.let { partner ->
                        item(key = "trades_top_partner") {
                            Column(verticalArrangement = Arrangement.spacedBy(sp.md)) {
                                Text(
                                    text = stringResource(R.string.stats_section_top_partner).uppercase(),
                                    style = ty.labelLarge,
                                    color = mc.textPrimary,
                                    letterSpacing = 2.sp,
                                )
                                PremiumCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(sp.lg),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = partner.displayName,
                                            style = ty.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = mc.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = stringResource(R.string.stats_trade_count, partner.completedTradesCount),
                                            style = ty.labelMedium,
                                            color = mc.textSecondary,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "trades_bottom_spacer") {
                        Spacer(Modifier.height(sp.xxl))
                    }
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
    val ty = MaterialTheme.magicTypography
    val sp = MaterialTheme.spacing
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = sp.lg, vertical = sp.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.md),
    ) {
        Surface(
            shape = CardShape,
            color = when { item.isDraw -> mc.textSecondary; item.isWin -> mc.lifePositive; else -> mc.lifeNegative }.copy(alpha = 0.2f),
        ) {
            Text(
                text = when { item.isDraw -> stringResource(R.string.stats_result_draw); item.isWin -> stringResource(R.string.stats_result_win); else -> stringResource(R.string.stats_result_loss) },
                style = ty.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = when { item.isDraw -> mc.textSecondary; item.isWin -> mc.lifePositive; else -> mc.lifeNegative },
                modifier = Modifier.padding(horizontal = sp.sm, vertical = sp.xs),
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(sp.xxs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(sp.sm)) {
                Text(
                    text = item.mode,
                    style = ty.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = mc.textPrimary,
                )
                Text(
                    text = "·",
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                )
                Text(
                    text = formatDuration(item.durationMs),
                    style = ty.bodySmall,
                    color = mc.textSecondary,
                )
            }

            Text(
                text = TimeAgoFormatter.format(item.playedAt),
                style = ty.labelSmall,
                color = mc.textSecondary,
            )

            item.deckName?.let { name ->
                Text(
                    text = name,
                    style = ty.labelSmall,
                    color = mc.goldMtg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.stats_game_options),
                    tint = mc.textDisabled,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete_game), color = mc.lifeNegative) },
                    onClick = {
                        menuExpanded = false
                        onDeleteRequest()
                    },
                )
            }
        }
    }
}

fun MtgColor.toDisplayNameRes(): Int =
    when (this) {
        MtgColor.W -> R.string.stats_color_white
        MtgColor.U -> R.string.stats_color_blue
        MtgColor.B -> R.string.stats_color_black
        MtgColor.R -> R.string.stats_color_red
        MtgColor.G -> R.string.stats_color_green
        MtgColor.COLORLESS -> R.string.stats_color_colorless
        MtgColor.MULTICOLOR -> R.string.collection_filter_multicolor
        else -> R.string.stats_color_unknown
    }
