package com.mmg.magicfolder.feature.draft.presentation.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.mmg.magicfolder.R
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.ui.theme.ThemeBackground
import com.mmg.magicfolder.core.ui.theme.magicColors
import com.mmg.magicfolder.core.ui.theme.magicTypography
import com.mmg.magicfolder.feature.draft.domain.model.*
import com.mmg.magicfolder.feature.draft.presentation.viewmodel.CardSortOption
import com.mmg.magicfolder.feature.draft.presentation.viewmodel.SetDraftDetailUiState
import com.mmg.magicfolder.feature.draft.presentation.viewmodel.SetDraftDetailViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIER_COLORS = mapOf(
    "S" to Color(0xFFFFD700),
    "A" to Color(0xFFC77DFF),
    "B" to Color(0xFF4FC3F7),
    "C" to Color(0xFF81C784),
    "D" to Color(0xFFFFB74D),
    "F" to Color(0xFFE57373),
)

private val MANA_COLORS = mapOf(
    "W" to Color(0xFFF9FAF4),
    "U" to Color(0xFF0E68AB),
    "B" to Color(0xFF150B00),
    "R" to Color(0xFFD3202A),
    "G" to Color(0xFF00733E),
    "C" to Color(0xFF888888),
)

@Composable
fun SetDraftDetailScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    viewModel: SetDraftDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    val tabs = listOf(
        stringResource(R.string.draft_tab_guide),
        stringResource(R.string.draft_tab_tier_list),
        stringResource(R.string.draft_tab_cards),
        stringResource(R.string.draft_tab_videos),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                    )
                }
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(state.setIconUri)
                        .decoderFactory(SvgDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = state.setName,
                    modifier = Modifier.size(28.dp),
                    colorFilter = ColorFilter.tint(colors.textPrimary),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.setName,
                        style = typography.titleMedium,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.setReleasedAt.isNotBlank()) {
                        Text(
                            text = formatDate(state.setReleasedAt),
                            style = typography.labelSmall,
                            color = colors.textSecondary,
                        )
                    }
                }
            }

            // Tabs
            ScrollableTabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = Color.Transparent,
                contentColor = colors.primaryAccent,
                edgePadding = 16.dp,
                indicator = @Composable { tabPositions ->
                    if (state.selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.primaryAccent,
                        )
                    }
                },
                divider = {},
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = state.selectedTab == index,
                        onClick = { viewModel.onTabSelected(index) },
                        text = {
                            Text(
                                text = title,
                                color = if (state.selectedTab == index) colors.primaryAccent
                                else colors.textDisabled,
                            )
                        },
                    )
                }
            }

            // Tab content
            when (state.selectedTab) {
                0 -> GuideTab(state)
                1 -> TierListTab(state, viewModel::toggleTierListColorFilter)
                2 -> CardsTab(state, viewModel, onCardClick)
                3 -> VideosTab(state, viewModel::loadVideos)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Tab 0: Guide
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GuideTab(state: SetDraftDetailUiState) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    when {
        state.isGuideLoading -> LoadingIndicator()
        state.guideError != null -> {
            PlaceholderMessage(stringResource(R.string.draft_guide_not_available))
        }
        state.guide != null -> {
            val guide = state.guide
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Overview
                item {
                    ExpandableSection(stringResource(R.string.draft_guide_overview), defaultExpanded = true) {
                        Text(guide.overview, style = typography.bodyMedium, color = colors.textPrimary)
                    }
                }
                // Mechanics
                if (guide.mechanics.isNotEmpty()) {
                    item {
                        ExpandableSection(stringResource(R.string.draft_guide_mechanics)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                guide.mechanics.forEach { mech ->
                                    MechanicCard(mech)
                                }
                            }
                        }
                    }
                }
                // Archetypes
                if (guide.archetypes.isNotEmpty()) {
                    item {
                        ExpandableSection(stringResource(R.string.draft_guide_archetypes)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                guide.archetypes.forEach { arch ->
                                    ArchetypeCard(arch)
                                }
                            }
                        }
                    }
                }
                // Top Commons
                if (guide.topCommons.isNotEmpty()) {
                    item {
                        ExpandableSection(stringResource(R.string.draft_guide_top_commons)) {
                            ColorGroupedCards(guide.topCommons)
                        }
                    }
                }
                // Top Uncommons
                if (guide.topUncommons.isNotEmpty()) {
                    item {
                        ExpandableSection(stringResource(R.string.draft_guide_top_uncommons)) {
                            ColorGroupedCards(guide.topUncommons)
                        }
                    }
                }
                // General Tips
                if (guide.generalTips.isNotEmpty()) {
                    item {
                        ExpandableSection(stringResource(R.string.draft_guide_tips)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                guide.generalTips.forEachIndexed { i, tip ->
                                    Row {
                                        Text(
                                            "${i + 1}. ",
                                            style = typography.bodyMedium,
                                            color = colors.primaryAccent,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(tip, style = typography.bodyMedium, color = colors.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MechanicCard(mechanic: MechanicGuide) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                mechanic.name,
                style = typography.labelLarge,
                color = colors.primaryAccent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(mechanic.description, style = typography.bodySmall, color = colors.textPrimary)
            if (mechanic.draftTip.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = colors.goldMtg,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        mechanic.draftTip,
                        style = typography.bodySmall,
                        color = colors.goldMtg,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchetypeCard(archetype: ArchetypeGuide) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val tierColor = TIER_COLORS[archetype.tier] ?: colors.textSecondary

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Color indicators
                archetype.colors.forEach { char ->
                    val manaColor = MANA_COLORS[char.toString()] ?: colors.textDisabled
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(manaColor),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    archetype.name,
                    style = typography.labelLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                // Tier badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(tierColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        archetype.tier,
                        style = typography.labelMedium,
                        color = tierColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(archetype.description, style = typography.bodySmall, color = colors.textSecondary)
            if (archetype.keyCommons.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.draft_key_cards) + ": " + archetype.keyCommons.joinToString(", "),
                    style = typography.labelSmall,
                    color = colors.textDisabled,
                )
            }
        }
    }
}

@Composable
private fun ColorGroupedCards(cards: Map<String, List<String>>) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        cards.forEach { (color, cardNames) ->
            val manaColor = MANA_COLORS[color] ?: colors.textDisabled
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(manaColor),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    cardNames.joinToString(", "),
                    style = typography.bodySmall,
                    color = colors.textPrimary,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Tab 1: Tier List
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TierListTab(
    state: SetDraftDetailUiState,
    onToggleColor: (String) -> Unit,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    when {
        state.isTierListLoading -> LoadingIndicator()
        state.tierListError != null -> {
            PlaceholderMessage(stringResource(R.string.draft_tier_list_not_available))
        }
        state.tierList != null -> {
            val tierList = state.tierList
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Header
                item {
                    Text(
                        stringResource(R.string.draft_tier_updated, tierList.lastUpdated),
                        style = typography.labelSmall,
                        color = colors.textDisabled,
                    )
                }
                // Color filter
                item {
                    ColorFilterRow(
                        selected = state.tierListColorFilter,
                        onToggle = onToggleColor,
                    )
                }
                // Tiers
                tierList.tiers.forEach { tier ->
                    val filteredCards = if (state.tierListColorFilter.isEmpty()) {
                        tier.cards
                    } else {
                        tier.cards.filter { it.color in state.tierListColorFilter }
                    }
                    if (filteredCards.isNotEmpty()) {
                        item {
                            TierBanner(tier.tier, tier.label, tier.description)
                        }
                        items(filteredCards) { card ->
                            TierCardItem(card, state.setCode)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TierBanner(tier: String, label: String, description: String) {
    val tierColor = TIER_COLORS[tier] ?: Color.Gray
    val typography = MaterialTheme.magicTypography

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = tierColor.copy(alpha = 0.15f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tier,
                style = typography.displayMedium,
                color = tierColor,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = typography.labelLarge, color = tierColor, fontWeight = FontWeight.Bold)
                if (description.isNotBlank()) {
                    Text(description, style = typography.labelSmall, color = tierColor.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun TierCardItem(card: TierCard, setCode: String) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val manaColor = MANA_COLORS[card.color] ?: colors.textDisabled

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Card image thumbnail
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data("https://api.scryfall.com/cards/named?fuzzy=${card.name}&set=$setCode&format=image&version=small")
                    .crossfade(true)
                    .build(),
                contentDescription = card.name,
                modifier = Modifier
                    .width(50.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(manaColor),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        card.name,
                        style = typography.bodyMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    card.rarity.replaceFirstChar { it.uppercase() },
                    style = typography.labelSmall,
                    color = colors.textDisabled,
                )
                if (card.reason.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        card.reason,
                        style = typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Tab 2: Cards
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CardsTab(
    state: SetDraftDetailUiState,
    viewModel: SetDraftDetailViewModel,
    onCardClick: (String) -> Unit,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    var showFilters by remember { mutableStateOf(false) }

    val filteredAndSorted = remember(state.cards, state.cardColorFilter, state.cardRarityFilter, state.cardSortBy) {
        var filtered = state.cards
        if (state.cardColorFilter.isNotEmpty()) {
            filtered = filtered.filter { card ->
                card.colors.any { it in state.cardColorFilter } ||
                    (state.cardColorFilter.contains("C") && card.colors.isEmpty())
            }
        }
        if (state.cardRarityFilter.isNotEmpty()) {
            filtered = filtered.filter { it.rarity in state.cardRarityFilter }
        }
        when (state.cardSortBy) {
            CardSortOption.PRICE -> filtered.sortedByDescending { it.priceUsd ?: 0.0 }
            CardSortOption.NAME -> filtered.sortedBy { it.name }
            CardSortOption.COLLECTOR -> filtered.sortedBy { it.collectorNumber.padStart(5, '0') }
            CardSortOption.RARITY -> {
                val order = mapOf("mythic" to 0, "rare" to 1, "uncommon" to 2, "common" to 3)
                filtered.sortedBy { order[it.rarity] ?: 4 }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${filteredAndSorted.size} cards",
                style = typography.labelMedium,
                color = colors.textSecondary,
            )
            IconButton(onClick = { showFilters = !showFilters }) {
                Icon(Icons.Default.FilterList, "Filters", tint = colors.primaryAccent)
            }
        }

        if (showFilters) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    stringResource(R.string.draft_filter_by_color),
                    style = typography.labelSmall,
                    color = colors.textSecondary,
                )
                ColorFilterRow(state.cardColorFilter, viewModel::toggleCardColorFilter)
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.draft_filter_by_rarity),
                    style = typography.labelSmall,
                    color = colors.textSecondary,
                )
                RarityFilterRow(state.cardRarityFilter, viewModel::toggleCardRarityFilter)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.draft_sort_by) + ": ",
                        style = typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    CardSortOption.entries.forEach { option ->
                        val label = when (option) {
                            CardSortOption.PRICE -> stringResource(R.string.draft_sort_price)
                            CardSortOption.NAME -> stringResource(R.string.draft_sort_name)
                            CardSortOption.COLLECTOR -> stringResource(R.string.draft_sort_collector)
                            CardSortOption.RARITY -> stringResource(R.string.draft_filter_by_rarity)
                        }
                        FilterChip(
                            selected = state.cardSortBy == option,
                            onClick = { viewModel.setCardSortBy(option) },
                            label = { Text(label, style = typography.labelSmall) },
                            modifier = Modifier.padding(end = 4.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.primaryAccent.copy(alpha = 0.2f),
                                selectedLabelColor = colors.primaryAccent,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        when {
            state.isCardsLoading && state.cards.isEmpty() -> LoadingIndicator()
            state.cardsError != null && state.cards.isEmpty() -> {
                PlaceholderMessage(state.cardsError ?: "Error")
            }
            else -> {
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredAndSorted, key = { it.scryfallId }) { card ->
                        DraftCardItem(card, onCardClick)
                    }
                }

                // Load more trigger
                LaunchedEffect(gridState.canScrollForward) {
                    if (!gridState.canScrollForward && state.hasMoreCards && !state.isCardsLoading) {
                        viewModel.loadCards(nextPage = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftCardItem(card: Card, onCardClick: (String) -> Unit) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val rarityBorderColor = when (card.rarity) {
        "mythic" -> Color(0xFFE8A030)
        "rare" -> Color(0xFFC9A84C)
        "uncommon" -> Color(0xFFB0C4DE)
        else -> colors.surfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = colors.surface,
        modifier = Modifier.clickable { onCardClick(card.scryfallId) },
    ) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(card.imageNormal ?: card.imageArtCrop)
                    .crossfade(true)
                    .build(),
                contentDescription = card.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                contentScale = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(rarityBorderColor),
            )
            Column(modifier = Modifier.padding(4.dp)) {
                Text(
                    card.name,
                    style = typography.labelSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                card.priceUsd?.let { price ->
                    Text(
                        "$${String.format("%.2f", price)}",
                        style = typography.labelSmall,
                        color = colors.goldMtg,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Tab 3: Videos
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideosTab(
    state: SetDraftDetailUiState,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val context = LocalContext.current

    when {
        state.isVideosLoading -> LoadingIndicator()
        state.videosError != null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.draft_videos_error),
                        style = typography.bodyMedium,
                        color = colors.lifeNegative,
                    )
                    if (state.videosError.contains("API key", ignoreCase = true)) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.videosError,
                            style = typography.labelSmall,
                            color = colors.textDisabled,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primaryAccent),
                    ) {
                        Text(stringResource(R.string.draft_retry))
                    }
                }
            }
        }
        state.videos.isEmpty() -> {
            PlaceholderMessage(stringResource(R.string.draft_videos_empty))
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.videos, key = { it.videoId }) { video ->
                    VideoCard(video) {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/watch?v=${video.videoId}"),
                        )
                        context.startActivity(intent)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(video: DraftVideo, onClick: () -> Unit) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(video.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = video.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "Play",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center),
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    video.title,
                    style = typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        video.channelName,
                        style = typography.labelSmall,
                        color = colors.textSecondary,
                    )
                    Text(
                        formatVideoDate(video.publishedAt),
                        style = typography.labelSmall,
                        color = colors.textDisabled,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Shared components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExpandableSection(
    title: String,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface.copy(alpha = 0.6f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = typography.labelLarge,
                color = colors.primaryAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
            )
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun ColorFilterRow(
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    val allColors = listOf("W", "U", "B", "R", "G", "C")
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        allColors.forEach { color ->
            val manaColor = MANA_COLORS[color] ?: Color.Gray
            val isSelected = color in selected
            FilterChip(
                selected = isSelected,
                onClick = { onToggle(color) },
                label = { Text(color, fontWeight = FontWeight.Bold) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = manaColor.copy(alpha = 0.3f),
                    selectedLabelColor = if (color == "B") Color.White else manaColor,
                ),
            )
        }
    }
}

@Composable
private fun RarityFilterRow(
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    val rarities = listOf("common", "uncommon", "rare", "mythic")
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rarities.forEach { rarity ->
            FilterChip(
                selected = rarity in selected,
                onClick = { onToggle(rarity) },
                label = { Text(rarity.replaceFirstChar { it.uppercase() }) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.magicColors.primaryAccent.copy(alpha = 0.2f),
                    selectedLabelColor = MaterialTheme.magicColors.primaryAccent,
                ),
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.magicColors.primaryAccent)
    }
}

@Composable
private fun PlaceholderMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            message,
            style = MaterialTheme.magicTypography.bodyMedium,
            color = MaterialTheme.magicColors.textSecondary,
        )
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        val date = LocalDate.parse(dateStr)
        date.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
    } catch (_: Exception) {
        dateStr
    }
}

private fun formatVideoDate(isoDate: String): String {
    return try {
        val date = LocalDate.parse(isoDate.substring(0, 10))
        date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
    } catch (_: Exception) {
        isoDate
    }
}
