package com.mmg.manahub.feature.draft.presentation.ui

// COMMENTS_REVIEWED: 2026-09-17

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.mmg.manahub.R
import com.mmg.manahub.core.FeatureFlags
import com.mmg.manahub.core.model.ArchetypeGuide
import com.mmg.manahub.core.model.ArchetypeKeyCard
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.MechanicGuide
import com.mmg.manahub.core.model.MechanicKeyCard
import com.mmg.manahub.core.model.TierCard
import com.mmg.manahub.core.ui.components.CardRow
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.ManaColorPicker
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.components.search.SearchSection
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.MagicTypography
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.feature.draft.presentation.viewmodel.SetDraftDetailUiState
import com.mmg.manahub.feature.draft.presentation.viewmodel.SetDraftDetailViewModel
import kotlinx.datetime.LocalDate
import org.koin.androidx.compose.koinViewModel

private val VALID_YOUTUBE_VIDEO_ID = Regex("^[a-zA-Z0-9_-]{11}$")

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

@Composable
private fun DraftGuideRichText(
    text: String,
    style: TextStyle,
    linkKeyPrefix: String,
    onCardClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.magicColors.textPrimary,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val segments = remember(text) { DraftGuideRichTextParser.parse(text) }
    val fontSize = if (style.fontSize == TextUnit.Unspecified) typography.bodyMedium.fontSize else style.fontSize
    val inlineSize = fontSize * 1.15f
    val inlineContent = remember(segments, inlineSize) {
        segments
            .filterIsInstance<DraftGuideRichTextSegment.Mana>()
            .map { it.token }
            .distinct()
            .associate { token ->
                manaInlineContentId(token) to InlineTextContent(
                    placeholder = Placeholder(
                        width = inlineSize,
                        height = inlineSize,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                    ),
                ) {
                    ManaSymbolImage(
                        token = token,
                        size = with(androidx.compose.ui.platform.LocalDensity.current) { inlineSize.toDp() },
                    )
                }
            }
    }
    val annotatedText = remember(segments, linkKeyPrefix, colors, baseColor) {
        buildAnnotatedString {
            segments.forEachIndexed { index, segment ->
                when (segment) {
                    is DraftGuideRichTextSegment.Text -> {
                        withStyle(segment.style.toSpanStyle(colors, baseColor)) {
                            append(segment.value)
                        }
                    }

                    is DraftGuideRichTextSegment.Mana -> {
                        appendInlineContent(
                            id = manaInlineContentId(segment.token),
                            alternateText = "{${segment.token}}",
                        )
                    }

                    is DraftGuideRichTextSegment.CardReference -> {
                        val stableCardKey = "$linkKeyPrefix-card-$index-${segment.scryfallId}"
                        val link = LinkAnnotation.Clickable(
                            tag = stableCardKey,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = colors.primaryAccent,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ),
                            linkInteractionListener = LinkInteractionListener {
                                onCardClick(segment.scryfallId, stableCardKey)
                            },
                        )
                        withLink(link) {
                            withStyle(segment.style.toSpanStyle(colors, baseColor)) {
                                append(segment.name)
                            }
                        }
                    }
                }
            }
        }
    }

    BasicText(
        text = annotatedText,
        inlineContent = inlineContent,
        style = style.copy(color = baseColor),
        modifier = modifier,
    )
}

private fun manaInlineContentId(token: String): String = "draft-guide-mana-${token.hashCode()}"

private fun DraftGuideTextStyle.toSpanStyle(
    colors: MagicColors,
    baseColor: Color,
): SpanStyle = SpanStyle(
    color = when (colorToken?.trim()?.lowercase()) {
        "primary", "accent", "primary_accent", "primaryaccent" -> colors.primaryAccent
        "secondary", "secondary_accent", "secondaryaccent" -> colors.secondaryAccent
        "gold", "gold_mtg", "goldmtg", "warning" -> colors.goldMtg
        "success", "life_positive", "lifepositive" -> colors.lifePositive
        "error", "negative", "life_negative", "lifenegative" -> colors.lifeNegative
        "text_primary", "textprimary" -> colors.textPrimary
        "text_secondary", "secondary_text", "textsecondary", "muted" -> colors.textSecondary
        "disabled", "text_disabled", "textdisabled" -> colors.textDisabled
        "mana_w", "manaw", "white" -> colors.manaW
        "mana_u", "manau", "blue" -> colors.manaU
        "mana_b", "manab", "black" -> colors.manaB
        "mana_r", "manar", "red" -> colors.manaR
        "mana_g", "manag", "green" -> colors.manaG
        "mana_c", "manac", "colorless" -> colors.manaC
        else -> baseColor
    },
    fontWeight = if (bold) FontWeight.Bold else null,
    fontStyle = if (italic) FontStyle.Italic else null,
    textDecoration = if (strikeThrough) TextDecoration.LineThrough else null,
)

private val RARITY_ITEMS = listOf(
    Triple("common", "C", Color(0xFF888888)),
    Triple("uncommon", "U", Color(0xFFB0C4DE)),
    Triple("rare", "R", Color(0xFFC9A84C)),
    Triple("mythic", "M", Color(0xFFE8A030)),
)

private fun MechanicKeyCard.toCard(setCode: String): Card = Card(
    scryfallId = scryfallId,
    name = name,
    printedName = null,
    manaCost = manaCost,
    cmc = cmc ?: 0.0,
    colors = colors,
    colorIdentity = colorIdentity,
    typeLine = typeLine,
    printedTypeLine = null,
    oracleText = null,
    printedText = null,
    keywords = emptyList(),
    power = null,
    toughness = null,
    loyalty = null,
    setCode = setCode,
    setName = "",
    collectorNumber = "",
    rarity = rarity,
    releasedAt = "",
    frameEffects = emptyList(),
    promoTypes = emptyList(),
    lang = "en",
    imageNormal = imageNormalUri,
    imageArtCrop = artCropUri,
    imageBackNormal = null,
    priceUsd = null,
    priceUsdFoil = null,
    priceEur = null,
    priceEurFoil = null,
    legalityStandard = "legal",
    legalityPioneer = "legal",
    legalityModern = "legal",
    legalityCommander = "legal",
    flavorText = null,
    artist = null,
    scryfallUri = "",
)

private fun ArchetypeKeyCard.toCard(setCode: String): Card = Card(
    scryfallId = scryfallId,
    name = name,
    printedName = null,
    manaCost = manaCost,
    cmc = cmc ?: 0.0,
    colors = colors,
    colorIdentity = colorIdentity,
    typeLine = typeLine,
    printedTypeLine = null,
    oracleText = null,
    printedText = null,
    keywords = emptyList(),
    power = null,
    toughness = null,
    loyalty = null,
    setCode = setCode,
    setName = "",
    collectorNumber = "",
    rarity = rarity,
    releasedAt = "",
    frameEffects = emptyList(),
    promoTypes = emptyList(),
    lang = "en",
    imageNormal = imageNormalUri,
    imageArtCrop = artCropUri,
    imageBackNormal = null,
    priceUsd = null,
    priceUsdFoil = null,
    priceEur = null,
    priceEurFoil = null,
    legalityStandard = "legal",
    legalityPioneer = "legal",
    legalityModern = "legal",
    legalityCommander = "legal",
    flavorText = null,
    artist = null,
    scryfallUri = "",
)

private fun TierCard.toCard(setCode: String): Card = Card(
    scryfallId = scryfallId,
    name = name,
    printedName = null,
    manaCost = manaCost,
    cmc = cmc ?: 0.0,
    colors = colors,
    colorIdentity = colorIdentity,
    typeLine = typeLine,
    printedTypeLine = null,
    oracleText = oracleText,
    printedText = null,
    keywords = emptyList(),
    power = null,
    toughness = null,
    loyalty = null,
    setCode = setCode,
    setName = "",
    collectorNumber = "",
    rarity = rarity,
    releasedAt = "",
    frameEffects = emptyList(),
    promoTypes = emptyList(),
    lang = "en",
    imageNormal = imageNormalUri,
    imageArtCrop = artCropUri,
    imageBackNormal = null,
    priceUsd = stats?.gihWinRate,
    priceUsdFoil = null,
    priceEur = null,
    priceEurFoil = null,
    legalityStandard = "legal",
    legalityPioneer = "legal",
    legalityModern = "legal",
    legalityCommander = "legal",
    flavorText = null,
    artist = null,
    scryfallUri = "",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SetDraftDetailScreen(
    onBack: () -> Unit,
    onCardClick: (String, String?) -> Unit,
    onSimulateDraft: (String) -> Unit = {},
    viewModel: SetDraftDetailViewModel = koinViewModel(),
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
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
        Column(modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
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
                // Simulate Draft entry point — only when the set has a published booster config.
                // HIDDEN for release behind FeatureFlags.Draft.SIMULATOR_ENABLED (UI-only).
                if (FeatureFlags.Draft.SIMULATOR_ENABLED && state.boosterVersion != null) {
                    Surface(
                        onClick = { onSimulateDraft(state.setCode) },
                        shape = RoundedCornerShape(10.dp),
                        color = colors.primaryAccent.copy(alpha = 0.15f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = colors.primaryAccent,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.draft_sim_simulate_button),
                                style = typography.labelMedium,
                                color = colors.primaryAccent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
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
                            Text(title.uppercase(), color = if (state.selectedTab == index) colors.primaryAccent else colors.textDisabled)
                        },
                    )
                }
            }

            when (state.selectedTab) {
                0 -> GuideTab(
                    state = state,
                    setIconUri = state.setIconUri,
                    onCardClick = onCardClick,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
                1 -> TierListSubTab(
                    state = state,
                    onToggleColor = viewModel::toggleTierListColorFilter,
                    onSearchQueryChanged = viewModel::onSearchQueryChanged,
                    onCardClick = onCardClick,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
                else -> GuideTab(
                    state = state,
                    setIconUri = state.setIconUri,
                    onCardClick = onCardClick,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Tab 0: Guide
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GuideTab(
    state: SetDraftDetailUiState,
    setIconUri: String,
    onCardClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val context = LocalContext.current
    val density = LocalDensity.current

    when {
        state.isGuideLoading -> LoadingIndicator()
        state.guideError != null -> PlaceholderMessage(stringResource(R.string.draft_guide_not_available))
        state.guide != null -> {
            val guide = state.guide
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Overview: summary + color ranking + gameplay notes
                item {
                    SearchSection(
                        title = stringResource(R.string.draft_guide_overview),
                        icon = Icons.Default.FilterList,
                        titleColor = colors.primaryAccent,
                        iconColor = colors.primaryAccent
                    ) {
                        if (guide.summary.isNotBlank()) {
                            DraftGuideRichText(
                                text = guide.summary,
                                style = typography.bodyLarge.copy(
                                    lineHeight = typography.bodyLarge.lineHeight * 1.2f,
                                ),
                                linkKeyPrefix = "guide-summary",
                                onCardClick = onCardClick,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }

                        if (guide.formatSpeed.isNotBlank()) {
                            DraftGuideRichText(
                                text = guide.formatSpeed,
                                style = typography.bodyMedium.copy(
                                    lineHeight = typography.bodyMedium.lineHeight * 1.1f,
                                ),
                                linkKeyPrefix = "guide-format-speed",
                                onCardClick = onCardClick,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }

                        if (guide.keyGameplayNotes.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    stringResource(R.string.draft_key_notes_label),
                                    style = typography.labelLarge,
                                    color = colors.secondaryAccent,
                                    fontWeight = FontWeight.Bold,
                                )
                                guide.keyGameplayNotes.forEachIndexed { index, note ->
                                    Row(verticalAlignment = Alignment.Top) {
                                        Icon(
                                            Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint = colors.goldMtg,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .padding(top = 2.dp),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        DraftGuideRichText(
                                            text = note,
                                            style = typography.bodyMedium.copy(
                                                lineHeight = typography.bodyMedium.lineHeight * 1.1f,
                                            ),
                                            linkKeyPrefix = "guide-key-note-$index",
                                            onCardClick = onCardClick,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Color Ranking
                if (guide.colorRanking.isNotEmpty()) {
                    item {
                        SearchSection(
                            title = stringResource(R.string.draft_color_ranking_label),
                            icon = Icons.Default.BarChart,
                            titleColor = colors.textPrimary,
                            iconColor = colors.goldMtg,
                            collapsedByDefault = true
                        ) {
                            guide.colorRanking.forEachIndexed { index, colorEntry ->
                                ColorRankingItem(
                                    rank = index + 1,
                                    entry = colorEntry,
                                    note = guide.colorNotes[colorEntry],
                                    colors = colors,
                                    typography = typography,
                                    onCardClick = onCardClick,
                                )
                            }
                        }
                    }
                }

                // Mechanics
                if (guide.mechanics.isNotEmpty()) {
                    item {
                        SearchSection(
                            title = stringResource(R.string.draft_guide_mechanics),
                            icon = Icons.Default.Bolt,
                            titleColor = colors.textPrimary,
                            iconColor = colors.goldMtg,
                            collapsedByDefault = true
                        ) {
                            guide.mechanics.forEach { 
                                MechanicCard(
                                    mechanic = it, 
                                    setCode = state.setCode, 
                                    onCardClick = onCardClick,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                ) 
                            }
                        }
                    }
                }

                // Archetypes
                if (guide.archetypes.isNotEmpty()) {
                    item {
                        SearchSection(
                            title = stringResource(R.string.draft_guide_archetypes),
                            icon = Icons.Default.Layers,
                            titleColor = colors.textPrimary,
                            iconColor = colors.goldMtg,
                            collapsedByDefault = false
                        ) {
                            guide.archetypes.forEach { arch ->
                                ArchetypeCard(
                                    archetype = arch, 
                                    onCardClick = onCardClick, 
                                    state = state,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope
                                )
                            }
                        }
                    }
                }

                // Key commons by color
                if (guide.keyCommonsByColor.isNotEmpty()) {
                    item {
                        SearchSection(
                            title = stringResource(R.string.draft_key_commons_by_color),
                            icon = Icons.Default.LocalOffer,
                            titleColor = colors.textPrimary,
                            iconColor = colors.goldMtg,
                            collapsedByDefault = true
                        ) {
                            guide.keyCommonsByColor.forEach { (colorLabel, cards) ->
                                if (cards.isNotEmpty()) {
                                    KeyCommonsByColorGroup(
                                        colorLabel = colorLabel, 
                                        cards = cards, 
                                        setCode = state.setCode, 
                                        onCardClick = onCardClick,
                                        sharedTransitionScope = sharedTransitionScope,
                                        animatedVisibilityScope = animatedVisibilityScope
                                    )
                                }
                            }
                        }
                    }
                }

                // Videos
                if (state.videos.isNotEmpty()) {
                    item {
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
    typography: MagicTypography,
    onCardClick: (String, String?) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.primaryAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                val rankingToken = Regex("\\{([WUBRGC])\\}").find(entry)?.groupValues?.getOrNull(1)
                if (rankingToken != null) {
                    ManaSymbolImage(token = rankingToken, size = 18.dp)
                } else {
                    Text(
                        "#$rank",
                        style = typography.labelSmall,
                        color = colors.primaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(Modifier.width(10.dp))

            DraftGuideRichText(
                text = entry.replace(Regex("^\\s*(?:\\{[WUBRGC]\\}\\s*)+"), "").trim(),
                style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                linkKeyPrefix = "guide-color-ranking-$rank",
                onCardClick = onCardClick,
                modifier = Modifier.weight(1f),
            )

            if (Regex("\\{([WUBRGC])\\}").find(entry) != null) {
                Text(
                    "#$rank",
                    style = typography.titleMedium,
                    color = colors.goldMtg,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        if (!note.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            DraftGuideRichText(
                text = note,
                style = typography.bodyMedium.copy(
                    lineHeight = typography.bodyMedium.lineHeight * 1.05f,
                ),
                linkKeyPrefix = "guide-color-note-$rank",
                onCardClick = onCardClick,
                modifier = Modifier.padding(start = 42.dp),
                baseColor = colors.textSecondary,
            )
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = colors.surfaceVariant.copy(alpha = 0.1f))
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Guide sub-components
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun MechanicCard(
    mechanic: MechanicGuide, 
    setCode: String, 
    onCardClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    // Determine if this is a flat-array variant (overperformers only, no underperformers)
    // vs the two-bucket variant. In the flat-array case we hide the "Overperformers" label.
    val examples = mechanic.keyExamples
    val isFlatArray = examples != null &&
        examples.overperformers.isNotEmpty() &&
        examples.underperformers.isEmpty()


        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                mechanic.name,
                style = typography.titleMedium,
                color = colors.primaryAccent,
                fontWeight = FontWeight.Bold
            )

            if (mechanic.summary.isNotBlank()) {
                DraftGuideRichText(
                    text = mechanic.summary,
                    style = typography.bodyLarge.copy(
                        lineHeight = typography.bodyLarge.lineHeight * 1.1f,
                    ),
                    linkKeyPrefix = "mechanic-${mechanic.name}-summary",
                    onCardClick = onCardClick,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                )
            }

            if (mechanic.performance.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.goldMtg.copy(alpha = 0.1f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Lightbulb,
                        null,
                        tint = colors.goldMtg,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    DraftGuideRichText(
                        text = mechanic.performance,
                        style = typography.bodyMedium,
                        linkKeyPrefix = "mechanic-${mechanic.name}-performance",
                        onCardClick = onCardClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (examples != null) {
                if (examples.overperformers.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!isFlatArray) {
                            // Two-bucket variant: show the labelled "Overperformers" header
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Color(0xFF81C784), modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.draft_mechanic_overperformers),
                                    style = typography.labelLarge,
                                    color = Color(0xFF81C784),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        } else {
                            // Flat-array variant: generic "Key Cards" label
                            Text(
                                stringResource(R.string.draft_mechanic_key_cards),
                                style = typography.labelLarge,
                                color = colors.secondaryAccent,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp)) {
                            examples.overperformers.forEach { card ->
                                CardRow(
                                    card = card.toCard(setCode),
                                    isInCollection = false,
                                    onClick = { 
                                        if (card.scryfallId.isNotBlank()) {
                                            val key = "mechanic-${mechanic.name}-over-${card.scryfallId}"
                                            onCardClick(card.scryfallId, key) 
                                        }
                                    },
                                    onRemove = null,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    sharedTransitionKey = "mechanic-${mechanic.name}-over-${card.scryfallId}"
                                )
                            }
                        }
                    }
                }

                if (examples.underperformers.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.TrendingDown, null, tint = Color(0xFFE57373), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.draft_mechanic_underperformers),
                                style = typography.labelLarge,
                                color = Color(0xFFE57373),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            examples.underperformers.forEach { card ->
                                CardRow(
                                    card = card.toCard(setCode),
                                    isInCollection = false,
                                    onClick = { 
                                        if (card.scryfallId.isNotBlank()) {
                                            val key = "mechanic-${mechanic.name}-under-${card.scryfallId}"
                                            onCardClick(card.scryfallId, key) 
                                        }
                                    },
                                    onRemove = null,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    sharedTransitionKey = "mechanic-${mechanic.name}-under-${card.scryfallId}"
                                )
                            }
                        }
                    }
                }
            }
        }

}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ArchetypeCard(
    archetype: ArchetypeGuide,
    onCardClick: (String, String?) -> Unit,
    state: SetDraftDetailUiState,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val tierColor = TIER_COLORS[archetype.tier.take(1)] ?: colors.textSecondary


        Column(modifier = Modifier.padding(8.dp)) {
            // Header row: mana symbols + tier badge
            // Schema v2 sets color_letters directly; v1 guides fall back to parsing archetype.colors.
            val colorLetters = archetype.colorLetters.ifEmpty { extractColorLetters(archetype.colors) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    colorLetters.forEach { letter ->
                        colorToManaToken(letter)?.let { token ->
                            ManaSymbolImage(token = token, size = 24.dp)
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = tierColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, tierColor.copy(alpha = 0.3f))
                ) {
                    Text(
                        archetype.tier,
                        style = typography.labelLarge,
                        color = tierColor,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                archetype.name,
                style = typography.titleLarge,
                color = colors.goldMtg,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )

            if (archetype.difficulty.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.draft_archetype_difficulty, ""),
                        style = typography.labelLarge,
                        color = colors.primaryAccent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        archetype.difficulty,
                        style = typography.labelLarge,
                        color = colors.secondaryAccent,
                    )
                    val archetypeWinRate = archetype.archetypeWinRate
                    if (archetypeWinRate != null) {
                        Spacer(Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = colors.goldMtg,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.draft_archetype_win_rate, archetypeWinRate * 100),
                                style = typography.labelMedium,
                                color = colors.goldMtg,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            if (archetype.strategy.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                DraftGuideRichText(
                    text = archetype.strategy,
                    style = typography.bodyLarge.copy(
                        lineHeight = typography.bodyLarge.lineHeight * 1.2f,
                    ),
                    linkKeyPrefix = "archetype-${archetype.name}-strategy",
                    onCardClick = onCardClick,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                )
            }

            if (archetype.notes.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                DraftGuideRichText(
                    text = archetype.notes,
                    style = typography.bodyMedium.copy(
                        lineHeight = typography.bodyMedium.lineHeight * 1.1f,
                    ),
                    linkKeyPrefix = "archetype-${archetype.name}-notes",
                    onCardClick = onCardClick,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                    baseColor = colors.textSecondary,
                )
            }

            // Key cards
            if (archetype.keyCards.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.draft_key_cards_label),
                        style = typography.labelLarge,
                        color = colors.secondaryAccent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp)) {
                    archetype.keyCards.forEach { card ->
                        CardRow(
                            card = card.toCard(state.setCode),
                            isInCollection = false,
                            onClick = { 
                                val key = "archetype-${archetype.name}-key-${card.scryfallId}"
                                onCardClick(card.scryfallId, key) 
                            },
                            onRemove = null,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedTransitionKey = "archetype-${archetype.name}-key-${card.scryfallId}"
                        )
                    }
                }
            }

            // Cards to avoid
            if (archetype.cardsToAvoid.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.draft_cards_to_avoid_label),
                        style = typography.labelLarge,
                        color = colors.lifeNegative,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    archetype.cardsToAvoid.forEach { card ->
                        CardRow(
                            card = card.toCard(state.setCode),
                            isInCollection = false,
                            onClick = { 
                                val key = "archetype-${archetype.name}-avoid-${card.scryfallId}"
                                onCardClick(card.scryfallId, key) 
                            },
                            onRemove = null,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedTransitionKey = "archetype-${archetype.name}-avoid-${card.scryfallId}"
                        )
                    }
                }
            }
        }

}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun KeyCommonsByColorGroup(
    colorLabel: String,
    cards: List<ArchetypeKeyCard>,
    setCode: String,
    onCardClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val token = Regex("\\{([WUBRGC])\\}").find(colorLabel)?.groupValues?.getOrNull(1)
    val label = colorLabel.replace(Regex("\\{[^}]+\\}\\s*"), "").trim().ifBlank { colorLabel }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            token?.let {
                ManaSymbolImage(token = it, size = 20.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                label,
                style = typography.labelLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            cards.forEach { card ->
                CardRow(
                    card = card.toCard(setCode),
                    isInCollection = false,
                    onClick = { 
                        if (card.scryfallId.isNotBlank()) {
                            val key = "key-commons-${colorLabel}-${card.scryfallId}"
                            onCardClick(card.scryfallId, key) 
                        }
                    },
                    onRemove = null,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedTransitionKey = "key-commons-${colorLabel}-${card.scryfallId}"
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Tier List tab
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun TierListSubTab(
    state: SetDraftDetailUiState,
    onToggleColor: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCardClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    when {
        state.isTierListLoading -> LoadingIndicator()
        state.tierListError != null -> PlaceholderMessage(stringResource(R.string.draft_tier_list_not_available))
        state.tierList != null -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SearchSection(
                            title = stringResource(R.string.deckbuilder_filter_title),
                            icon = Icons.Default.FilterList,
                            collapsedByDefault = true,
                            titleColor = colors.textPrimary,
                            iconColor = colors.goldMtg
                        ) {
                            // Search Bar
                            OutlinedTextField(
                                value = state.tierListSearchQuery,
                                onValueChange = onSearchQueryChanged,
                                placeholder = {
                                    Text(
                                        stringResource(R.string.draft_search_cards_hint),
                                        style = typography.bodyMedium,
                                        color = colors.textDisabled
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = colors.textDisabled)
                                },
                                trailingIcon = {
                                    if (state.tierListSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { onSearchQueryChanged("") }) {
                                            Icon(Icons.Default.Clear, contentDescription = null, tint = colors.textDisabled)
                                        }
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.primaryAccent,
                                    unfocusedBorderColor = colors.surfaceVariant.copy(alpha = 0.5f),
                                    focusedTextColor = colors.textPrimary,
                                    unfocusedTextColor = colors.textPrimary,
                                    cursorColor = colors.primaryAccent,
                                )
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    stringResource(R.string.draft_filter_by_color),
                                    style = typography.labelSmall,
                                    color = colors.textDisabled,
                                    fontWeight = FontWeight.Bold
                                )
                                ManaColorPicker(
                                    selectedColors = state.tierListColorFilter,
                                    onToggleColor = onToggleColor,
                                    itemSize = 40.dp,
                                    symbolSize = 26.dp
                                )
                            }
                        }
                    }
                }

                state.tierList.tiers.forEach { tier ->
                    val filteredCards = tier.cards.filter { card ->
                        val matchesColor = if (state.tierListColorFilter.isEmpty()) {
                            true
                        } else {
                            val filter = state.tierListColorFilter
                            val cardColors = card.colors.ifEmpty { listOf("C") }
                            cardColors.any { it in filter }
                        }
                        val matchesSearch = state.tierListSearchQuery.isEmpty() || 
                                           card.name.contains(state.tierListSearchQuery, ignoreCase = true)
                        
                        matchesColor && matchesSearch
                    }

                    if (filteredCards.isNotEmpty()) {
                        stickyHeader(key = "tier_${tier.tier}") {
                            TierBanner(
                                tier = tier.tier,
                                label = tier.label,
                                description = tier.description,
                                onCardClick = onCardClick,
                            )
                        }

                        items(filteredCards, key = { it.scryfallId }) { card ->
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                CardRow(
                                    card = card.toCard(state.setCode),
                                    isInCollection = false,
                                    onClick = { 
                                        val key = "tier-list-${card.scryfallId}"
                                        onCardClick(card.scryfallId, key) 
                                    },
                                    onRemove = null,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    sharedTransitionKey = "tier-list-${card.scryfallId}"
                                )
                            }
                        }
                        
                        item { Spacer(Modifier.height(16.dp)) }
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
private fun TierBanner(
    tier: String,
    label: String,
    description: String,
    onCardClick: (String, String?) -> Unit,
) {
    val tierColor = TIER_COLORS[tier] ?: Color.Gray
    val typography = MaterialTheme.magicTypography
    val mc = MaterialTheme.magicColors

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(mc.background)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = tierColor.copy(alpha = 0.25f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tier,
                        style = typography.displayMedium,
                        color = tierColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            label,
                            style = typography.labelLarge,
                            color = tierColor,
                            fontWeight = FontWeight.Bold
                        )
                        if (description.isNotBlank()) {
                            DraftGuideRichText(
                                text = description,
                                style = typography.labelSmall,
                                linkKeyPrefix = "tier-description-$tier",
                                onCardClick = onCardClick,
                                baseColor = tierColor.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
        // Sutil diffusion gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(mc.background, Color.Transparent)
                    )
                )
        )
    }
}


// ═══════════════════════════════════════════════════════════════════════════════
//  Shared components
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
private fun LoadingIndicator() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        MagicLoadingSpinner()
    }
}

@Composable
private fun PlaceholderMessage(message: String) {
    Box(modifier = Modifier
        .fillMaxSize()
        .padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            style = MaterialTheme.magicTypography.bodyMedium,
            color = MaterialTheme.magicColors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatDate(dateStr: String): String = try {
    val date = LocalDate.parse(dateStr)
    val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    "$month ${date.year}"
} catch (_: Exception) { dateStr }

private fun formatVideoDate(isoDate: String): String = try {
    val date = LocalDate.parse(isoDate.take(10))
    val month = date.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    "$month ${date.dayOfMonth}, ${date.year}"
} catch (_: Exception) { isoDate }
