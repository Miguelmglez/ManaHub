package com.mmg.manahub.feature.draft.presentation.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.ui.components.MagicSegmentedControl
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.draft.domain.model.ArchetypeGuide
import com.mmg.manahub.feature.draft.domain.model.DraftVideo
import com.mmg.manahub.feature.draft.domain.model.MechanicGuide
import com.mmg.manahub.feature.draft.presentation.viewmodel.CardSortOption
import com.mmg.manahub.feature.draft.presentation.viewmodel.SetDraftDetailUiState
import com.mmg.manahub.feature.draft.presentation.viewmodel.SetDraftDetailViewModel
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

/** Maps a single color letter (W/U/B/R/G/C) to the Scryfall SVG mana token. */
private fun colorToManaToken(code: String): String? = when (code.uppercase()) {
    "W" -> "W"; "U" -> "U"; "B" -> "B"; "R" -> "R"; "G" -> "G"; "C" -> "C"
    else -> null
}

private val RARITY_ITEMS = listOf(
    Triple("common",   "C", Color(0xFF888888)),
    Triple("uncommon", "U", Color(0xFFB0C4DE)),
    Triple("rare",     "R", Color(0xFFC9A84C)),
    Triple("mythic",   "M", Color(0xFFE8A030)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetDraftDetailScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit,
    viewModel: SetDraftDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    // Art-crop URL map built from eagerly-loaded set cards (CDN URLs, no per-card API calls).
    // Split / DFC card names like "Fire // Ice" are also indexed under their first part ("Fire")
    // so guide/tier-list JSON entries that use the short name still resolve correctly.
    val artCropByName = remember(state.cards) {
        val map = mutableMapOf<String, String>()
        state.cards.forEach { card ->
            val url = card.imageArtCrop?.takeIf { it.isNotBlank() } ?: return@forEach
            map[card.name] = url
            if (card.name.contains("//")) {
                val shortName = card.name.substringBefore("//").trim()
                map.putIfAbsent(shortName, url)
            }
        }
        map as Map<String, String>
    }

    val tabs = listOf(
        stringResource(R.string.draft_tab_guide),
        stringResource(R.string.draft_tab_cards),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), tint = colors.textPrimary)
                }
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(state.setIconUri).decoderFactory(SvgDecoder.Factory()).crossfade(true).build(),
                    contentDescription = state.setName,
                    modifier = Modifier.size(28.dp),
                    colorFilter = ColorFilter.tint(colors.textPrimary),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.setName, style = typography.titleMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.setReleasedAt.isNotBlank()) {
                        Text(formatDate(state.setReleasedAt), style = typography.labelSmall, color = colors.textSecondary)
                    }
                }
            }

            // Main tabs (2 tabs, fixed TabRow with correct indicator offset)
            TabRow(
                selectedTabIndex = state.selectedTab,
                containerColor = Color.Transparent,
                contentColor = colors.primaryAccent,
                indicator = { tabPositions ->
                    if (state.selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[state.selectedTab]),
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
                            Text(title, color = if (state.selectedTab == index) colors.primaryAccent else colors.textDisabled)
                        },
                    )
                }
            }

            when (state.selectedTab) {
                0 -> GuideTab(state, artCropByName, state.setIconUri, viewModel::loadCardDetail)
                1 -> CardsTab(state, artCropByName, state.setIconUri, viewModel)
            }
        }

        // Card detail bottom sheet
        if (state.cardDetail != null || state.isCardDetailLoading) {
            ModalBottomSheet(
                onDismissRequest = viewModel::dismissCardDetail,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = colors.background,
            ) {
                CardDetailBottomSheet(card = state.cardDetail, isLoading = state.isCardDetailLoading)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Tab 0: Guide
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun GuideTab(
    state: SetDraftDetailUiState,
    artCropByName: Map<String, String>,
    setIconUri: String,
    onCardClick: (String) -> Unit,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val context = LocalContext.current

    when {
        state.isGuideLoading -> LoadingIndicator()
        state.guideError != null -> PlaceholderMessage(stringResource(R.string.draft_guide_not_available))
        state.guide != null -> {
            val guide = state.guide
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ExpandableSection(stringResource(R.string.draft_guide_overview), defaultExpanded = true) {
                        Text(guide.overview, style = typography.bodyMedium, color = colors.textPrimary)
                    }
                }
                if (guide.mechanics.isNotEmpty()) {
                    item {
                        ExpandableSection(stringResource(R.string.draft_guide_mechanics)) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                guide.mechanics.forEach { MechanicCard(it) }
                            }
                        }
                    }
                }
                if (guide.archetypes.isNotEmpty()) {
                    item {
                        ExpandableSection(stringResource(R.string.draft_guide_archetypes)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                guide.archetypes.forEach { arch ->
                                    ArchetypeCard(arch, artCropByName, setIconUri, onCardClick)
                                }
                            }
                        }
                    }
                }
                if (guide.topCommons.isNotEmpty()) {
                    item {
                        ExpandableSection(stringResource(R.string.draft_guide_top_commons)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                guide.topCommons.forEach { (color, cardNames) ->
                                    cardNames.forEach { name ->
                                        UnifiedCardItem(name = name, artCropUrl = artCropByName[name], setIconUri = setIconUri, color = color, onClick = { onCardClick(name) })
                                    }
                                }
                            }
                        }
                    }
                }
                if (guide.topUncommons.isNotEmpty()) {
                    item {
                        ExpandableSection(stringResource(R.string.draft_guide_top_uncommons)) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                guide.topUncommons.forEach { (color, cardNames) ->
                                    cardNames.forEach { name ->
                                        UnifiedCardItem(name = name, artCropUrl = artCropByName[name], setIconUri = setIconUri, color = color, onClick = { onCardClick(name) })
                                    }
                                }
                            }
                        }
                    }
                }
                if (guide.generalTips.isNotEmpty()) {
                    item {
                        ExpandableSection(stringResource(R.string.draft_guide_tips)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                guide.generalTips.forEachIndexed { i, tip ->
                                    Row {
                                        Text("${i + 1}. ", style = typography.bodyMedium, color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                                        Text(tip, style = typography.bodyMedium, color = colors.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
                if (state.videos.isNotEmpty()) {
                    item {
                        ExpandableSection(stringResource(R.string.draft_guide_videos), defaultExpanded = true) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.videos.forEach { video ->
                                    VideoCard(video) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${video.videoId}")))
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

// ═══════════════════════════════════════════════════════════════════════════════
//  Guide sub-components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun MechanicCard(mechanic: MechanicGuide) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    Surface(shape = RoundedCornerShape(8.dp), color = colors.surface) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(mechanic.name, style = typography.labelLarge, color = colors.primaryAccent, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(mechanic.description, style = typography.bodySmall, color = colors.textPrimary)
            if (mechanic.draftTip.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Lightbulb, null, tint = colors.goldMtg, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(mechanic.draftTip, style = typography.bodySmall, color = colors.goldMtg)
                }
            }
        }
    }
}

@Composable
private fun ArchetypeCard(
    archetype: ArchetypeGuide,
    artCropByName: Map<String, String>,
    setIconUri: String,
    onCardClick: (String) -> Unit,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val tierColor = TIER_COLORS[archetype.tier] ?: colors.textSecondary
    Surface(shape = RoundedCornerShape(8.dp), color = colors.surface) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                archetype.colors.forEach { char ->
                    colorToManaToken(char.toString())?.let { token ->
                        ManaSymbolImage(token = token, size = 20.dp)
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Spacer(Modifier.width(4.dp))
                Text(archetype.name, style = typography.labelLarge, color = colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(tierColor.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(archetype.tier, style = typography.labelMedium, color = tierColor, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(archetype.description, style = typography.bodySmall, color = colors.textSecondary)
            val keyCards = archetype.keyCommons + archetype.keyUncommons
            if (keyCards.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.draft_key_cards_label), style = typography.labelSmall, color = colors.textDisabled)
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    keyCards.forEach { name ->
                        UnifiedCardItem(name = name, artCropUrl = artCropByName[name], setIconUri = setIconUri, onClick = { onCardClick(name) })
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Unified card item  (Guide + Tier List) — art crop from pre-loaded CDN URLs
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun UnifiedCardItem(
    name: String,
    artCropUrl: String?,      // CDN art-crop URL from loaded cards; null → placeholder shown
    setIconUri: String,       // Set icon SVG URI – shown as fallback when URL is missing or 404s
    color: String? = null,
    rarity: String? = null,
    reason: String? = null,
    tierBadge: String? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    // Track whether the card image failed to load (404 / network error)
    var imageError by remember(artCropUrl) { mutableStateOf(false) }
    val showArtCrop = !artCropUrl.isNullOrBlank() && !imageError

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Art-crop thumbnail
            Box(
                modifier = Modifier.width(80.dp).height(58.dp).clip(RoundedCornerShape(4.dp)).background(colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (showArtCrop) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(artCropUrl).crossfade(true).build(),
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { imageError = true },
                    )
                } else {
                    // Fallback: set icon tinted as a watermark
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(setIconUri)
                            .decoderFactory(SvgDecoder.Factory())
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        colorFilter = ColorFilter.tint(colors.textDisabled.copy(alpha = 0.5f)),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    color?.let { colorToManaToken(it) }?.let { token ->
                        ManaSymbolImage(token = token, size = 14.dp)
                    }
                    Text(name, style = typography.bodyMedium, color = colors.textPrimary, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (tierBadge != null) {
                        val tierColor = TIER_COLORS[tierBadge] ?: colors.textSecondary
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(tierColor.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(tierBadge, style = typography.labelSmall, color = tierColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (rarity != null) {
                    Text(rarity.replaceFirstChar { it.uppercase() }, style = typography.labelSmall, color = colors.textDisabled)
                }
                if (!reason.isNullOrBlank()) {
                    Text(reason, style = typography.labelSmall, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Tab 1: Cards  — segmented toggle between All Cards and Tier List
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardsTab(
    state: SetDraftDetailUiState,
    artCropByName: Map<String, String>,
    setIconUri: String,
    viewModel: SetDraftDetailViewModel,
) {
    val colors = MaterialTheme.magicColors

    Column(modifier = Modifier.fillMaxSize()) {
        // Modern segmented control for sub-tabs
        MagicSegmentedControl(
            options = listOf(
                stringResource(R.string.draft_cards_all),
                stringResource(R.string.draft_tab_tier_list)
            ),
            selectedIndex = state.selectedCardsSubTab,
            onOptionSelected = { viewModel.onCardsSubTabSelected(it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when (state.selectedCardsSubTab) {
            0 -> AllCardsSubTab(state, viewModel)
            1 -> TierListSubTab(state = state, artCropByName = artCropByName, setIconUri = setIconUri, onToggleColor = viewModel::toggleTierListColorFilter, onCardClick = viewModel::loadCardDetail)
        }
    }
}

@Composable
private fun AllCardsSubTab(
    state: SetDraftDetailUiState,
    viewModel: SetDraftDetailViewModel,
) {
    val mc = MaterialTheme.magicColors
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
            CardSortOption.PRICE  -> filtered.sortedByDescending { it.priceUsd ?: 0.0 }
            CardSortOption.NAME   -> filtered.sortedBy { it.name }
            CardSortOption.RARITY -> {
                val order = mapOf("mythic" to 0, "rare" to 1, "uncommon" to 2, "common" to 3)
                filtered.sortedBy { order[it.rarity] ?: 4 }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter toggle bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.collection_card_count, filteredAndSorted.size), style = typography.labelMedium, color = mc.textSecondary)
            IconButton(onClick = { showFilters = !showFilters }) {
                Icon(Icons.Default.FilterList, stringResource(R.string.deckbuilder_filter_title), tint = mc.primaryAccent)
            }
        }

        // Compact filter panel inside a Surface card
        if (showFilters) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = mc.surface,
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Color
                    Text(stringResource(R.string.draft_filter_by_color), style = typography.labelSmall, color = mc.textSecondary)
                    ManaColorFilterRow(state.cardColorFilter, viewModel::toggleCardColorFilter, mc)
                    // Rarity
                    Text(stringResource(R.string.draft_filter_by_rarity), style = typography.labelSmall, color = mc.textSecondary)
                    RarityFilterRow(state.cardRarityFilter, viewModel::toggleCardRarityFilter, mc)
                    // Sort
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.draft_sort_by_label), style = typography.labelSmall, color = mc.textSecondary)
                        CardSortOption.entries.forEach { option ->
                            val label = when (option) {
                                CardSortOption.PRICE  -> stringResource(R.string.draft_sort_price)
                                CardSortOption.NAME   -> stringResource(R.string.draft_sort_name)
                                CardSortOption.RARITY -> stringResource(R.string.draft_filter_by_rarity)
                            }
                            FilterChip(
                                selected = state.cardSortBy == option,
                                onClick  = { viewModel.setCardSortBy(option) },
                                label    = { Text(label, style = typography.labelSmall) },
                                colors   = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = mc.primaryAccent.copy(alpha = 0.2f),
                                    selectedLabelColor     = mc.primaryAccent,
                                    containerColor         = mc.surface,
                                ),
                            )
                        }
                    }
                }
            }
        }

        when {
            state.isCardsLoading && state.cards.isEmpty() -> LoadingIndicator()
            state.cardsError != null && state.cards.isEmpty() -> PlaceholderMessage(state.cardsError ?: stringResource(R.string.state_error))
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
                    // Click opens bottom sheet instead of navigating
                    items(filteredAndSorted, key = { it.scryfallId }) { card ->
                        DraftCardItem(card, onClick = { viewModel.showCardDetail(card) })
                    }
                    // Loading indicator at the bottom while more pages are being fetched
                    if (state.isCardsLoading && state.cards.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.magicColors.primaryAccent,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftCardItem(card: Card, onClick: () -> Unit) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val rarityBorderColor = when (card.rarity) {
        "mythic"   -> Color(0xFFE8A030)
        "rare"     -> Color(0xFFC9A84C)
        "uncommon" -> Color(0xFFB0C4DE)
        else       -> colors.surfaceVariant
    }
    Surface(shape = RoundedCornerShape(6.dp), color = colors.surface, modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(card.imageNormal ?: card.imageArtCrop).crossfade(true).build(),
                contentDescription = card.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                contentScale = ContentScale.Crop,
            )
            Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(rarityBorderColor))
            Column(modifier = Modifier.padding(4.dp)) {
                Text(card.name, style = typography.labelSmall, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                card.priceUsd?.let { Text(stringResource(R.string.price_format_usd, it), style = typography.labelSmall, color = colors.goldMtg) }
            }
        }
    }
}

@Composable
private fun TierListSubTab(
    state: SetDraftDetailUiState,
    artCropByName: Map<String, String>,
    setIconUri: String,
    onToggleColor: (String) -> Unit,
    onCardClick: (String) -> Unit,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    when {
        state.isTierListLoading -> LoadingIndicator()
        state.tierListError != null -> PlaceholderMessage(stringResource(R.string.draft_tier_list_not_available))
        state.tierList != null -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(stringResource(R.string.draft_tier_updated, state.tierList.lastUpdated), style = typography.labelSmall, color = colors.textDisabled)
                }
                item {
                    ManaColorFilterRow(state.tierListColorFilter, onToggleColor, colors)
                }
                state.tierList.tiers.forEach { tier ->
                    val filteredCards = if (state.tierListColorFilter.isEmpty()) tier.cards
                    else tier.cards.filter { it.color in state.tierListColorFilter }
                    if (filteredCards.isNotEmpty()) {
                        item { TierBanner(tier.tier, tier.label, tier.description) }
                        items(filteredCards) { card ->
                            UnifiedCardItem(
                                name = card.name,
                                artCropUrl = artCropByName[card.name],
                                setIconUri = setIconUri,
                                color = card.color,
                                rarity = card.rarity,
                                reason = card.reason,
                                tierBadge = tier.tier,
                                onClick = { onCardClick(card.name) },
                            )
                        }
                    }
                }
            }
        }
        else -> PlaceholderMessage(stringResource(R.string.draft_tier_list_not_available))
    }
}

@Composable
private fun TierBanner(tier: String, label: String, description: String) {
    val tierColor = TIER_COLORS[tier] ?: Color.Gray
    val typography = MaterialTheme.magicTypography
    Surface(shape = RoundedCornerShape(8.dp), color = tierColor.copy(alpha = 0.15f)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tier, style = typography.displayMedium, color = tierColor, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label, style = typography.labelLarge, color = tierColor, fontWeight = FontWeight.Bold)
                if (description.isNotBlank()) Text(description, style = typography.labelSmall, color = tierColor.copy(alpha = 0.7f))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Card detail bottom sheet — always shows full card image (not art crop)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CardDetailBottomSheet(card: Card?, isLoading: Boolean) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        when {
            isLoading -> Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primaryAccent)
            }
            card != null -> {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    // Front face – full card image
                    val frontImageUrl = card.imageNormal ?: card.imageArtCrop
                    if (!frontImageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(frontImageUrl).crossfade(true).build(),
                            contentDescription = card.name,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    // Back face for DFCs
                    if (!card.imageBackNormal.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(card.imageBackNormal).crossfade(true).build(),
                            contentDescription = stringResource(R.string.carddetail_back_face_description, card.name),
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Name + mana cost
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val displayName = card.printedName?.takeIf { it.isNotBlank() } ?: card.name
                            Text(displayName, style = typography.titleMedium, color = colors.textPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (!card.manaCost.isNullOrBlank()) {
                                ManaCostImages(manaCost = card.manaCost, symbolSize = 18.dp)
                            }
                        }
                        val typeLine = card.printedTypeLine?.takeIf { it.isNotBlank() } ?: card.typeLine
                        Text(typeLine, style = typography.labelMedium, color = colors.textSecondary)

                        HorizontalDivider(color = colors.surfaceVariant)

                        val displayText = card.oracleText?.takeIf { it.isNotBlank() }
                            ?: card.printedText?.takeIf { it.isNotBlank() }
                        if (displayText != null) {
                            Text(displayText, style = typography.bodySmall, color = colors.textPrimary)
                        }

                        when {
                            card.power != null && card.toughness != null ->
                                Text("${card.power}/${card.toughness}", style = typography.labelLarge, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                            card.loyalty != null ->
                                Text(stringResource(R.string.carddetail_loyalty_value, card.loyalty), style = typography.labelLarge, color = colors.textPrimary, fontWeight = FontWeight.Bold)
                        }

                        if (!card.flavorText.isNullOrBlank()) {
                            HorizontalDivider(color = colors.surfaceVariant)
                            Text(card.flavorText, style = typography.bodySmall, color = colors.textSecondary, fontStyle = FontStyle.Italic)
                        }
                        if (!card.artist.isNullOrBlank()) {
                            Text(stringResource(R.string.draft_card_artist, card.artist), style = typography.labelSmall, color = colors.textDisabled)
                        }
                        card.priceUsd?.let {
                            Text(stringResource(R.string.price_format_usd, it), style = typography.labelMedium, color = colors.goldMtg, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Video card
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun VideoCard(video: DraftVideo, onClick: () -> Unit) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    Surface(shape = RoundedCornerShape(12.dp), color = colors.surface, modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(video.thumbnailUrl).crossfade(true).build(),
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop,
                )
                Icon(Icons.Default.PlayCircle, stringResource(R.string.nav_play), tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(48.dp).align(Alignment.Center))
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(video.title, style = typography.bodyMedium, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(video.channelName, style = typography.labelSmall, color = colors.textSecondary)
                    Text(formatVideoDate(video.publishedAt), style = typography.labelSmall, color = colors.textDisabled)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Shared components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ExpandableSection(title: String, defaultExpanded: Boolean = false, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    Surface(shape = RoundedCornerShape(12.dp), color = colors.surface.copy(alpha = 0.6f)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = typography.labelLarge,
                color = colors.primaryAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp),
            )
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

/**
 * Mana color filter row – matches CollectionScreen style.
 * Each color is a square [FilterChip] containing the Scryfall SVG symbol.
 */
@Composable
private fun ManaColorFilterRow(selected: Set<String>, onToggle: (String) -> Unit, mc: MagicColors) {
    val colorTokens = listOf("W", "U", "B", "R", "G", "C")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        colorTokens.forEach { token ->
            val isSelected = token in selected
            FilterChip(
                selected = isSelected,
                onClick  = { onToggle(token) },
                label    = { ManaSymbolImage(token = token, size = 24.dp) },
                modifier = Modifier.size(40.dp),
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = mc.primaryAccent.copy(alpha = 0.20f),
                    containerColor         = mc.surface,
                    selectedLabelColor     = mc.primaryAccent,
                    labelColor             = mc.textSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled             = true,
                    selected            = isSelected,
                    selectedBorderColor = mc.primaryAccent.copy(alpha = 0.60f),
                    selectedBorderWidth = 2.dp,
                    borderColor         = mc.surfaceVariant,
                    borderWidth         = 0.5.dp,
                ),
            )
        }
    }
}

/**
 * Rarity filter: compact chips with 1-letter abbreviations + rarity accent color.
 * C = Common, U = Uncommon, R = Rare, M = Mythic
 */
@Composable
private fun RarityFilterRow(selected: Set<String>, onToggle: (String) -> Unit, mc: MagicColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RARITY_ITEMS.forEach { (rarity, label, rarityColor) ->
            val isSelected = rarity in selected
            FilterChip(
                selected = isSelected,
                onClick  = { onToggle(rarity) },
                label    = { Text(label, style = MaterialTheme.magicTypography.labelMedium, fontWeight = FontWeight.Bold) },
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = rarityColor.copy(alpha = 0.20f),
                    selectedLabelColor     = rarityColor,
                    containerColor         = mc.surface,
                    labelColor             = mc.textSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled             = true,
                    selected            = isSelected,
                    selectedBorderColor = rarityColor.copy(alpha = 0.60f),
                    selectedBorderWidth = 2.dp,
                    borderColor         = mc.surfaceVariant,
                    borderWidth         = 0.5.dp,
                ),
            )
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.magicColors.primaryAccent)
    }
}

@Composable
private fun PlaceholderMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.magicTypography.bodyMedium, color = MaterialTheme.magicColors.textSecondary)
    }
}

private fun formatDate(dateStr: String): String = try {
    LocalDate.parse(dateStr).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
} catch (_: Exception) { dateStr }

private fun formatVideoDate(isoDate: String): String = try {
    LocalDate.parse(isoDate.substring(0, 10)).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
} catch (_: Exception) { isoDate }
