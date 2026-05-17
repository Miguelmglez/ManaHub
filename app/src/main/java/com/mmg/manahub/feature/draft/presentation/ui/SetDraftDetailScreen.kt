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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.text.style.TextAlign
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
import com.mmg.manahub.core.ui.components.ManaColorPicker
import com.mmg.manahub.core.ui.components.ManaCostImages
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.SetSymbol
import com.mmg.manahub.core.ui.components.CardRarity
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.draft.domain.model.ArchetypeGuide
import com.mmg.manahub.feature.draft.domain.model.ArchetypeKeyCard
import com.mmg.manahub.feature.draft.domain.model.DraftVideo
import com.mmg.manahub.feature.draft.domain.model.MechanicGuide
import com.mmg.manahub.feature.draft.domain.model.MechanicKeyCard
import com.mmg.manahub.feature.draft.domain.model.TierCard
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

/**
 * Extracts individual color letters from a combined color string or mana symbol string.
 * Handles both "BR" format and "{B}{R}" mana symbol format, including "C" for colorless.
 */
private fun extractColorLetters(colorsStr: String): List<String> {
    // Handle mana symbol format like "{G}{U}" or "{C}"
    return if (colorsStr.contains("{")) {
        Regex("\\{([WUBRGC])\\}").findAll(colorsStr).map { it.groupValues[1] }.toList()
    } else {
        // Handle plain concatenated format like "BR" or "C"
        // Also ensure "C" is captured if present
        colorsStr.filter { it in "WUBRGC" && it != ' ' }.map { it.toString() }
    }
}

private val RARITY_ITEMS = listOf(
    Triple("common", "C", Color(0xFF888888)),
    Triple("uncommon", "U", Color(0xFFB0C4DE)),
    Triple("rare", "R", Color(0xFFC9A84C)),
    Triple("mythic", "M", Color(0xFFE8A030)),
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

    val tabs = listOf(
        stringResource(R.string.draft_tab_guide),
        stringResource(R.string.draft_tab_tier_list),
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

            // Main tabs
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
                0 -> GuideTab(state, state.setIconUri, onCardClick)
                1 -> TierListSubTab(
                    state = state,
                    onToggleColor = viewModel::toggleTierListColorFilter,
                    onCardClick = onCardClick,
                )
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
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Overview: summary + color ranking + gameplay notes
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface.copy(alpha = 0.5f))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            stringResource(R.string.draft_guide_overview),
                            style = typography.titleMedium,
                            color = colors.primaryAccent,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (guide.summary.isNotBlank()) {
                            Text(guide.summary, style = typography.bodyMedium, color = colors.textPrimary)
                        }
                        
                        if (guide.keyGameplayNotes.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    stringResource(R.string.draft_key_notes_label),
                                    style = typography.labelSmall,
                                    color = colors.textDisabled,
                                )
                                guide.keyGameplayNotes.forEach { note ->
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = colors.goldMtg,
                                            modifier = Modifier.size(14.dp).padding(top = 2.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(note, style = typography.bodySmall, color = colors.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }

                // Color Ranking
                if (guide.colorRanking.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                stringResource(R.string.draft_color_ranking_label),
                                style = typography.labelLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            guide.colorRanking.forEachIndexed { index, colorEntry ->
                                ColorRankingItem(
                                    rank = index + 1,
                                    entry = colorEntry,
                                    note = guide.colorNotes[colorEntry],
                                    colors = colors,
                                    typography = typography
                                )
                                if (index < guide.colorRanking.size - 1) {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                // Mechanics
                if (guide.mechanics.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                stringResource(R.string.draft_guide_mechanics),
                                style = typography.labelLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                guide.mechanics.forEach { MechanicCard(mechanic = it, setCode = state.setCode, onCardClick = onCardClick) }
                            }
                        }
                    }
                }

                // Archetypes
                if (guide.archetypes.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                stringResource(R.string.draft_guide_archetypes),
                                style = typography.labelLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                guide.archetypes.forEach { arch ->
                                    ArchetypeCard(arch, onCardClick, state)
                                }
                            }
                        }
                    }
                }

                // Videos
                if (state.videos.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                            Text(
                                stringResource(R.string.draft_guide_videos),
                                style = typography.labelLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

@Composable
private fun ColorRankingItem(
    rank: Int,
    entry: String,
    note: String?,
    colors: MagicColors,
    typography: com.mmg.manahub.core.ui.theme.MagicTypography,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val rankingToken = Regex("\\{([WUBRGC])\\}").find(entry)?.groupValues?.getOrNull(1)
                rankingToken?.let {
                    ManaSymbolImage(token = it, size = 20.dp)
                    Spacer(Modifier.width(12.dp))
                }

                Text(
                    entry.replace(Regex("\\{[^}]+\\}\\s*"), "").trim(),
                    style = typography.bodyMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    "#$rank",
                    style = typography.labelLarge,
                    color = colors.goldMtg,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (!note.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    note,
                    style = typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 32.dp),
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Guide sub-components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun DraftCardListItem(
    name: String,
    artCropUri: String,
    colors: List<String>,
    typeLine: String,
    rarity: String,
    setCode: String,
    onClick: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    var imageError by remember(artCropUri) { mutableStateOf(false) }
    val showArtCrop = artCropUri.isNotBlank() && !imageError

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = mc.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
            // Art crop thumbnail
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(52.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(mc.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (showArtCrop) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(artCropUri).crossfade(true).build(),
                        contentDescription = name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { imageError = true },
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data("https://svgs.scryfall.io/sets/${setCode.lowercase()}.svg") // Fallback to set symbol
                            .decoderFactory(SvgDecoder.Factory())
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        colorFilter = ColorFilter.tint(mc.textDisabled.copy(alpha = 0.5f)),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        name,
                        style = typography.bodyMedium,
                        color = mc.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    
                    val displayColors = colors.ifEmpty { listOf("C") }
                    Spacer(Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        displayColors.forEach { colorLetter ->
                            colorToManaToken(colorLetter)?.let { token ->
                                ManaSymbolImage(token = token, size = 14.dp)
                                Spacer(Modifier.width(2.dp))
                            }
                        }
                    }
                }
                if (typeLine.isNotBlank()) {
                    Text(
                        typeLine,
                        style = typography.labelSmall,
                        color = mc.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SetSymbol(
                        setCode = setCode,
                        rarity = CardRarity.fromString(rarity),
                        size = 18.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun MechanicCard(mechanic: MechanicGuide, setCode: String, onCardClick: (String) -> Unit) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    // Determine if this is a flat-array variant (overperformers only, no underperformers)
    // vs the two-bucket variant. In the flat-array case we hide the "Overperformers" label.
    val examples = mechanic.keyExamples
    val isFlatArray = examples != null &&
        examples.overperformers.isNotEmpty() &&
        examples.underperformers.isEmpty()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                mechanic.name,
                style = typography.titleMedium,
                color = colors.primaryAccent,
                fontWeight = FontWeight.Bold
            )

            if (mechanic.summary.isNotBlank()) {
                Text(mechanic.summary, style = typography.bodyMedium, color = colors.textPrimary)
            }

            if (mechanic.performance.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.goldMtg.copy(alpha = 0.1f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        null,
                        tint = colors.goldMtg,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(mechanic.performance, style = typography.bodySmall, color = colors.textPrimary)
                }
            }

            if (examples != null) {
                if (examples.overperformers.isNotEmpty()) {
                    if (!isFlatArray) {
                        // Two-bucket variant: show the labelled "Overperformers" header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Color(0xFF81C784), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.draft_mechanic_overperformers),
                                style = typography.labelMedium,
                                color = Color(0xFF81C784),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    } else {
                        // Flat-array variant: generic "Key Cards" label
                        Text(
                            stringResource(R.string.draft_mechanic_key_cards),
                            style = typography.labelMedium,
                            color = colors.textDisabled,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        examples.overperformers.forEach { card ->
                            DraftCardListItem(
                                name = card.name,
                                artCropUri = card.artCropUri,
                                colors = card.colors,
                                typeLine = card.typeLine,
                                rarity = card.rarity,
                                setCode = setCode,
                                onClick = { if (card.scryfallId.isNotBlank()) onCardClick(card.scryfallId) }
                            )
                        }
                    }
                }

                if (examples.underperformers.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.TrendingDown, null, tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.draft_mechanic_underperformers),
                            style = typography.labelMedium,
                            color = Color(0xFFE57373),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        examples.underperformers.forEach { card ->
                            DraftCardListItem(
                                name = card.name,
                                artCropUri = card.artCropUri,
                                colors = card.colors,
                                typeLine = card.typeLine,
                                rarity = card.rarity,
                                setCode = setCode,
                                onClick = { if (card.scryfallId.isNotBlank()) onCardClick(card.scryfallId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArchetypeCard(
    archetype: ArchetypeGuide,
    onCardClick: (String) -> Unit,
    state: SetDraftDetailUiState
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val tierColor = TIER_COLORS[archetype.tier.take(1)] ?: colors.textSecondary

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row: mana symbols + name + tier badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    extractColorLetters(archetype.colors).forEach { letter ->
                        colorToManaToken(letter)?.let { token ->
                            ManaSymbolImage(token = token, size = 24.dp)
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    archetype.name,
                    style = typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(tierColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        archetype.tier.substringBefore(" ").substringBefore("—").trim(),
                        style = typography.labelMedium,
                        color = tierColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (archetype.difficulty.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.draft_archetype_difficulty, archetype.difficulty),
                    style = typography.labelSmall,
                    color = colors.textDisabled,
                )
            }

            if (archetype.strategy.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    archetype.strategy,
                    style = typography.bodyMedium,
                    color = colors.textPrimary,
                    lineHeight = typography.bodyMedium.lineHeight * 1.1f
                )
            }

            // Key cards as an art-crop grid
            if (archetype.keyCards.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.draft_key_cards_label),
                    style = typography.labelMedium,
                    color = colors.textDisabled,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    archetype.keyCards.forEach { card ->
                        DraftCardListItem(
                            name = card.name,
                            artCropUri = card.artCropUri,
                            colors = card.colors,
                            typeLine = card.typeLine,
                            rarity = card.rarity,
                            setCode = state.setCode,
                            onClick = { onCardClick(card.scryfallId) }
                        )
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Tier List tab
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun TierListSubTab(
    state: SetDraftDetailUiState,
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
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surface.copy(alpha = 0.5f))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            stringResource(R.string.draft_filter_by_color),
                            style = typography.labelSmall,
                            color = colors.textSecondary
                        )
                        ManaColorPicker(
                            selectedColors = state.tierListColorFilter,
                            onToggleColor = onToggleColor,
                            itemSize = 36.dp,
                            symbolSize = 24.dp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.draft_tier_updated, state.tierList.lastUpdated),
                            style = typography.labelSmall,
                            color = colors.textDisabled,
                        )
                    }
                }
                state.tierList.tiers.forEach { tier ->
                    val filteredCards = if (state.tierListColorFilter.isEmpty()) {
                        tier.cards
                    } else {
                        tier.cards.filter { card ->
                            card.colors.any { it in state.tierListColorFilter }
                        }
                    }
                    if (filteredCards.isNotEmpty()) {
                        item { TierBanner(tier.tier, tier.label, tier.description) }
                        items(filteredCards) { card ->
                            DraftCardListItem(
                                name = card.name,
                                artCropUri = card.artCropUri,
                                colors = card.colors,
                                typeLine = card.typeLine,
                                rarity = card.rarity,
                                setCode = state.setCode,
                                onClick = { onCardClick(card.scryfallId) }
                            )
                        }
                    }
                }
            }
        }
        else -> PlaceholderMessage(stringResource(R.string.draft_tier_list_not_available))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Common components
// ═══════════════════════════════════════════════════════════════════════════════

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
                if (description.isNotBlank()) {
                    Text(description, style = typography.labelSmall, color = tierColor.copy(alpha = 0.7f))
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
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.magicColors.primaryAccent)
    }
}

@Composable
private fun PlaceholderMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.magicTypography.bodyMedium,
            color = MaterialTheme.magicColors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatDate(dateStr: String): String = try {
    LocalDate.parse(dateStr).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
} catch (_: Exception) { dateStr }

private fun formatVideoDate(isoDate: String): String = try {
    LocalDate.parse(isoDate.substring(0, 10)).format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH))
} catch (_: Exception) { isoDate }
