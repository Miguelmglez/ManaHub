package com.mmg.manahub.feature.draft.presentation.ui

// COMMENTS_REVIEWED: 2026-09-22

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.mmg.manahub.R
import com.mmg.manahub.core.FeatureFlags
import com.mmg.manahub.core.ui.components.CardRow
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.ManaColorPicker
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.SmallCardShape
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.draft.presentation.viewmodel.ArchetypeUi
import com.mmg.manahub.feature.draft.presentation.viewmodel.ColorRankingUi
import com.mmg.manahub.feature.draft.presentation.viewmodel.GuideCardUi
import com.mmg.manahub.feature.draft.presentation.viewmodel.GuideRichText
import com.mmg.manahub.feature.draft.presentation.viewmodel.GuideSection
import com.mmg.manahub.feature.draft.presentation.viewmodel.KeyCommonsGroupUi
import com.mmg.manahub.feature.draft.presentation.viewmodel.MechanicUi
import com.mmg.manahub.feature.draft.presentation.viewmodel.SetDraftDetailUiState
import com.mmg.manahub.feature.draft.presentation.viewmodel.SetDraftDetailViewModel
import com.mmg.manahub.feature.draft.presentation.viewmodel.SetDraftGuideUiModel
import com.mmg.manahub.feature.draft.presentation.viewmodel.TIER_FILTERS_ID
import kotlinx.datetime.LocalDate
import org.koin.androidx.compose.koinViewModel

private val TIER_COLORS = mapOf(
    "S" to Color(0xFFFFD700),
    "A" to Color(0xFFC77DFF),
    "B" to Color(0xFF4FC3F7),
    "C" to Color(0xFF81C784),
    "D" to Color(0xFFFFB74D),
    "F" to Color(0xFFE57373),
)

private const val CONTENT_TYPE_SECTION_HEADER = "section_header"
private const val CONTENT_TYPE_SUB_HEADER = "sub_header"
private const val CONTENT_TYPE_RICH_TEXT = "rich_text"
private const val CONTENT_TYPE_LABEL = "label"
private const val CONTENT_TYPE_CARD = "card_row"
private const val CONTENT_TYPE_CALLOUT = "callout"
private const val CONTENT_TYPE_META = "meta"

private const val SKELETON_SECTION_COUNT = 5

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
    // Hoisted so each tab keeps its scroll position across tab switches.
    val guideListState = rememberLazyListState()
    val tierListState = rememberLazyListState()

    val tabs = listOf(
        stringResource(R.string.draft_tab_guide),
        stringResource(R.string.draft_tab_tier_list),
    )

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())
        Column(modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()) {
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

            if (state.selectedTab == 1) {
                TierListSubTab(
                    state = state,
                    listState = tierListState,
                    onToggleFilters = viewModel::toggleGuideExpansion,
                    onToggleColor = viewModel::toggleTierListColorFilter,
                    onSearchQueryChanged = viewModel::onSearchQueryChanged,
                    onCardClick = onCardClick,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            } else {
                GuideTab(
                    state = state,
                    listState = guideListState,
                    onToggle = viewModel::toggleGuideExpansion,
                    onCardClick = onCardClick,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Guide tab
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GuideTab(
    state: SetDraftDetailUiState,
    listState: LazyListState,
    onToggle: (String) -> Unit,
    onCardClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val guide = state.guide
    when {
        guide != null -> GuideList(
            guide = guide,
            expandedIds = state.expandedGuideIds,
            listState = listState,
            onToggle = onToggle,
            onCardClick = onCardClick,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
        )
        state.guideError != null -> PlaceholderMessage(stringResource(R.string.draft_guide_not_available))
        else -> GuideSkeleton()
    }
}

/** Header titles resolved once in composition, since the LazyColumn DSL is not composable. */
private data class GuideTitles(
    val overview: String,
    val colorRanking: String,
    val mechanics: String,
    val archetypes: String,
    val keyCommons: String,
    val keyNotes: String,
    val overperformers: String,
    val underperformers: String,
    val mechanicKeyCards: String,
    val archetypeKeyCards: String,
    val cardsToAvoid: String,
)

/** Everything the lazy item builders need that is not per-item data. */
private class GuideListContext(
    val expandedIds: Set<String>,
    val titles: GuideTitles,
    val onToggle: (String) -> Unit,
    val onCardClick: (String, String?) -> Unit,
    val sharedTransitionScope: SharedTransitionScope?,
    val animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    fun isExpanded(id: String) = id in expandedIds
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GuideList(
    guide: SetDraftGuideUiModel,
    expandedIds: Set<String>,
    listState: LazyListState,
    onToggle: (String) -> Unit,
    onCardClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val spacing = MaterialTheme.spacing
    val titles = GuideTitles(
        overview = stringResource(R.string.draft_guide_overview),
        colorRanking = stringResource(R.string.draft_guide_color_ranking),
        mechanics = stringResource(R.string.draft_guide_mechanics),
        archetypes = stringResource(R.string.draft_guide_archetypes),
        keyCommons = stringResource(R.string.draft_key_commons_by_color),
        keyNotes = stringResource(R.string.draft_key_notes_label),
        overperformers = stringResource(R.string.draft_mechanic_overperformers),
        underperformers = stringResource(R.string.draft_mechanic_underperformers),
        mechanicKeyCards = stringResource(R.string.draft_mechanic_key_cards),
        archetypeKeyCards = stringResource(R.string.draft_key_cards_label),
        cardsToAvoid = stringResource(R.string.draft_cards_to_avoid_label),
    )
    val context = GuideListContext(
        expandedIds = expandedIds,
        titles = titles,
        onToggle = onToggle,
        onCardClick = onCardClick,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.lg,
            end = spacing.lg,
            top = spacing.sm,
            bottom = spacing.xxl,
        ),
    ) {
        sectionHeader(GuideSection.OVERVIEW, titles.overview, Icons.AutoMirrored.Filled.MenuBook, count = null, context)
        if (context.isExpanded(GuideSection.OVERVIEW.id)) overviewItems(guide, context)

        if (guide.colorRanking.isNotEmpty()) {
            sectionHeader(GuideSection.COLOR_RANKING, titles.colorRanking, Icons.Default.BarChart, guide.colorRanking.size, context)
            if (context.isExpanded(GuideSection.COLOR_RANKING.id)) {
                guide.colorRanking.forEach { colorRankingItems(it, context) }
            }
        }

        if (guide.mechanics.isNotEmpty()) {
            sectionHeader(GuideSection.MECHANICS, titles.mechanics, Icons.Default.Bolt, guide.mechanics.size, context)
            if (context.isExpanded(GuideSection.MECHANICS.id)) {
                guide.mechanics.forEach { mechanicItems(it, context) }
            }
        }

        if (guide.archetypes.isNotEmpty()) {
            sectionHeader(GuideSection.ARCHETYPES, titles.archetypes, Icons.Default.Layers, guide.archetypes.size, context)
            if (context.isExpanded(GuideSection.ARCHETYPES.id)) {
                guide.archetypes.forEach { archetypeItems(it, context) }
            }
        }

        if (guide.keyCommons.isNotEmpty()) {
            sectionHeader(GuideSection.KEY_COMMONS, titles.keyCommons, Icons.Default.LocalOffer, guide.keyCommons.size, context)
            if (context.isExpanded(GuideSection.KEY_COMMONS.id)) {
                guide.keyCommons.forEach { keyCommonsItems(it, context) }
            }
        }
    }
}

// ── Lazy item builders ─────────────────────────────────────────────────────────

private fun LazyListScope.sectionHeader(
    section: GuideSection,
    title: String,
    icon: ImageVector,
    count: Int?,
    context: GuideListContext,
) {
    item(key = section.id, contentType = CONTENT_TYPE_SECTION_HEADER) {
        GuideSectionHeader(
            title = title,
            icon = icon,
            count = count,
            expanded = context.isExpanded(section.id),
            onToggle = { context.onToggle(section.id) },
            modifier = Modifier
                .animateItem()
                .padding(top = MaterialTheme.spacing.md),
        )
    }
}

private fun LazyListScope.overviewItems(guide: SetDraftGuideUiModel, context: GuideListContext) {
    val overview = guide.overview
    val prefix = GuideSection.OVERVIEW.id
    overview.summary?.let { summary ->
        richTextItem("$prefix-summary", summary, context, emphasis = true)
    }
    overview.formatSpeed?.let { speed ->
        richTextItem("$prefix-speed", speed, context)
    }
    if (overview.keyNotes.isNotEmpty()) {
        labelItem("$prefix-notes-label", context.titles.keyNotes, LabelTone.SECONDARY)
        itemsIndexed(
            items = overview.keyNotes,
            key = { index, _ -> "$prefix-note-$index" },
            contentType = { _, _ -> CONTENT_TYPE_RICH_TEXT },
        ) { index, note ->
            val colors = MaterialTheme.magicColors
            val typography = MaterialTheme.magicTypography
            val spacing = MaterialTheme.spacing
            Row(
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .padding(start = spacing.sm, top = spacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = colors.goldMtg,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(top = spacing.xxs),
                )
                Spacer(Modifier.width(spacing.sm))
                DraftGuideRichText(
                    text = note,
                    style = typography.bodyMedium.copy(lineHeight = typography.bodyMedium.lineHeight * 1.1f),
                    linkKeyPrefix = "$prefix-note-$index",
                    onCardClick = context.onCardClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun LazyListScope.colorRankingItems(entry: ColorRankingUi, context: GuideListContext) {
    val expandable = entry.note != null
    val expanded = context.isExpanded(entry.id)
    item(key = entry.id, contentType = CONTENT_TYPE_SUB_HEADER) {
        val colors = MaterialTheme.magicColors
        val typography = MaterialTheme.magicTypography
        GuideSubSectionHeader(
            expanded = expanded,
            expandable = expandable,
            onToggle = { context.onToggle(entry.id) },
            modifier = subHeaderModifier(),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.primaryAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                if (entry.manaToken != null) {
                    ManaSymbolImage(token = entry.manaToken, size = 18.dp)
                } else {
                    Text("#${entry.rank}", style = typography.labelSmall, color = colors.primaryAccent, fontWeight = FontWeight.Bold)
                }
            }
            DraftGuideRichText(
                text = entry.title,
                style = typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                linkKeyPrefix = "${entry.id}-title",
                onCardClick = context.onCardClick,
                modifier = Modifier.weight(1f),
            )
            if (entry.manaToken != null) {
                Text("#${entry.rank}", style = typography.titleMedium, color = colors.goldMtg, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
    if (expanded && entry.note != null) {
        richTextItem("${entry.id}-note", entry.note, context, muted = true)
    }
}

private fun LazyListScope.mechanicItems(mechanic: MechanicUi, context: GuideListContext) {
    val expanded = context.isExpanded(mechanic.id)
    item(key = mechanic.id, contentType = CONTENT_TYPE_SUB_HEADER) {
        GuideSubSectionHeader(
            expanded = expanded,
            expandable = true,
            onToggle = { context.onToggle(mechanic.id) },
            modifier = subHeaderModifier(),
        ) {
            Text(
                mechanic.name,
                style = MaterialTheme.magicTypography.titleMedium,
                color = MaterialTheme.magicColors.primaryAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (!expanded) return

    mechanic.summary?.let { richTextItem("${mechanic.id}-summary", it, context) }
    mechanic.performance?.let { performance ->
        item(key = "${mechanic.id}-performance", contentType = CONTENT_TYPE_CALLOUT) {
            val colors = MaterialTheme.magicColors
            val spacing = MaterialTheme.spacing
            Row(
                modifier = contentModifier()
                    .clip(SmallCardShape)
                    .background(colors.goldMtg.copy(alpha = 0.1f))
                    .padding(spacing.md),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = colors.goldMtg,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(top = spacing.xxs),
                )
                Spacer(Modifier.width(spacing.sm))
                DraftGuideRichText(
                    text = performance,
                    style = MaterialTheme.magicTypography.bodyMedium,
                    linkKeyPrefix = "${mechanic.id}-performance",
                    onCardClick = context.onCardClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (mechanic.overperformers.isNotEmpty()) {
        if (mechanic.isFlatExamples) {
            labelItem("${mechanic.id}-over-label", context.titles.mechanicKeyCards, LabelTone.SECONDARY)
        } else {
            labelItem("${mechanic.id}-over-label", context.titles.overperformers, LabelTone.POSITIVE)
        }
        cardItems(mechanic.overperformers, context)
    }
    if (mechanic.underperformers.isNotEmpty()) {
        labelItem("${mechanic.id}-under-label", context.titles.underperformers, LabelTone.NEGATIVE)
        cardItems(mechanic.underperformers, context)
    }
}

private fun LazyListScope.archetypeItems(archetype: ArchetypeUi, context: GuideListContext) {
    val expanded = context.isExpanded(archetype.id)
    item(key = archetype.id, contentType = CONTENT_TYPE_SUB_HEADER) {
        val colors = MaterialTheme.magicColors
        val typography = MaterialTheme.magicTypography
        val spacing = MaterialTheme.spacing
        val tierColor = TIER_COLORS[archetype.tierLetter] ?: colors.textSecondary
        GuideSubSectionHeader(
            expanded = expanded,
            expandable = true,
            onToggle = { context.onToggle(archetype.id) },
            modifier = subHeaderModifier(),
        ) {
            if (archetype.manaTokens.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                    archetype.manaTokens.forEach { ManaSymbolImage(token = it, size = 20.dp) }
                }
            }
            Text(
                archetype.name,
                style = typography.titleMedium,
                color = colors.goldMtg,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (archetype.tier.isNotBlank()) {
                Surface(
                    shape = ChipShape,
                    color = tierColor.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, tierColor.copy(alpha = 0.3f)),
                ) {
                    Text(
                        archetype.tier,
                        style = typography.labelMedium,
                        color = tierColor,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xxs),
                    )
                }
            }
        }
    }
    if (!expanded) return

    if (archetype.difficulty.isNotBlank() || archetype.winRate != null) {
        item(key = "${archetype.id}-meta", contentType = CONTENT_TYPE_META) {
            ArchetypeMetaRow(archetype, modifier = contentModifier())
        }
    }
    archetype.strategy?.let { richTextItem("${archetype.id}-strategy", it, context, emphasis = true) }
    archetype.notes?.let { richTextItem("${archetype.id}-notes", it, context, muted = true) }
    if (archetype.keyCards.isNotEmpty()) {
        labelItem("${archetype.id}-key-label", context.titles.archetypeKeyCards, LabelTone.SECONDARY)
        cardItems(archetype.keyCards, context)
    }
    if (archetype.cardsToAvoid.isNotEmpty()) {
        labelItem("${archetype.id}-avoid-label", context.titles.cardsToAvoid, LabelTone.NEGATIVE)
        cardItems(archetype.cardsToAvoid, context)
    }
}

private fun LazyListScope.keyCommonsItems(group: KeyCommonsGroupUi, context: GuideListContext) {
    val expanded = context.isExpanded(group.id)
    item(key = group.id, contentType = CONTENT_TYPE_SUB_HEADER) {
        GuideSubSectionHeader(
            expanded = expanded,
            expandable = true,
            onToggle = { context.onToggle(group.id) },
            modifier = subHeaderModifier(),
        ) {
            group.manaToken?.let { ManaSymbolImage(token = it, size = 20.dp) }
            Text(
                group.label,
                style = MaterialTheme.magicTypography.labelLarge,
                color = MaterialTheme.magicColors.textPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            CountBadge(group.cards.size)
        }
    }
    if (expanded) cardItems(group.cards, context)
}

@OptIn(ExperimentalSharedTransitionApi::class)
private fun LazyListScope.cardItems(cards: List<GuideCardUi>, context: GuideListContext) {
    items(items = cards, key = { it.key }, contentType = { CONTENT_TYPE_CARD }) { cardUi ->
        CardRow(
            card = cardUi.card,
            isInCollection = false,
            onClick = {
                if (cardUi.card.scryfallId.isNotBlank()) {
                    context.onCardClick(cardUi.card.scryfallId, cardUi.key)
                }
            },
            onRemove = null,
            modifier = contentModifier(),
            sharedTransitionScope = context.sharedTransitionScope,
            animatedVisibilityScope = context.animatedVisibilityScope,
            sharedTransitionKey = cardUi.key,
        )
    }
}

private fun LazyListScope.richTextItem(
    key: String,
    text: GuideRichText,
    context: GuideListContext,
    emphasis: Boolean = false,
    muted: Boolean = false,
) {
    item(key = key, contentType = CONTENT_TYPE_RICH_TEXT) {
        val colors = MaterialTheme.magicColors
        val typography = MaterialTheme.magicTypography
        val base = if (emphasis) typography.bodyLarge else typography.bodyMedium
        DraftGuideRichText(
            text = text,
            style = base.copy(lineHeight = base.lineHeight * if (emphasis) 1.2f else 1.1f),
            linkKeyPrefix = key,
            onCardClick = context.onCardClick,
            modifier = contentModifier(),
            baseColor = if (muted) colors.textSecondary else colors.textPrimary,
        )
    }
}

private enum class LabelTone { SECONDARY, POSITIVE, NEGATIVE }

private fun LazyListScope.labelItem(key: String, text: String, tone: LabelTone) {
    item(key = key, contentType = CONTENT_TYPE_LABEL) {
        val colors = MaterialTheme.magicColors
        val spacing = MaterialTheme.spacing
        val color = when (tone) {
            LabelTone.SECONDARY -> colors.secondaryAccent
            LabelTone.POSITIVE -> colors.lifePositive
            LabelTone.NEGATIVE -> colors.lifeNegative
        }
        Row(
            modifier = contentModifier().padding(top = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = when (tone) {
                LabelTone.POSITIVE -> Icons.AutoMirrored.Filled.TrendingUp
                LabelTone.NEGATIVE -> Icons.AutoMirrored.Filled.TrendingDown
                LabelTone.SECONDARY -> null
            }
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(spacing.sm))
            }
            Text(
                text,
                style = MaterialTheme.magicTypography.labelLarge,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
        }
    }
}

@Composable
private fun LazyItemScope.subHeaderModifier(): Modifier = Modifier
    .animateItem()
    .padding(start = MaterialTheme.spacing.sm, top = MaterialTheme.spacing.sm)

@Composable
private fun LazyItemScope.contentModifier(): Modifier = Modifier
    .animateItem()
    .fillMaxWidth()
    .padding(start = MaterialTheme.spacing.lg, top = MaterialTheme.spacing.sm)

// ── Guide components ───────────────────────────────────────────────────────────

@Composable
private fun GuideSectionHeader(
    title: String,
    icon: ImageVector,
    count: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val stateText = expansionStateText(expanded)
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "section_chevron")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CardShape)
            .background(colors.surface)
            .border(0.5.dp, colors.surfaceVariant.copy(alpha = 0.5f), CardShape)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = stateText
            }
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = colors.goldMtg, modifier = Modifier.size(22.dp))
        Text(
            title,
            style = MaterialTheme.magicTypography.titleMedium,
            color = if (expanded) colors.primaryAccent else colors.textPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) CountBadge(count)
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier
                .size(24.dp)
                .rotate(chevronRotation),
        )
    }
}

@Composable
private fun GuideSubSectionHeader(
    expanded: Boolean,
    expandable: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val stateText = expansionStateText(expanded)
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "sub_section_chevron")
    val interaction = if (expandable) {
        Modifier
            .semantics(mergeDescendants = true) { stateDescription = stateText }
            .clickable(role = Role.Button, onClick = onToggle)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(SmallCardShape)
            .background(colors.surface.copy(alpha = if (expanded) 0.9f else 0.55f))
            .then(interaction)
            .padding(horizontal = spacing.md, vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        content()
        if (expandable) {
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation),
            )
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    val colors = MaterialTheme.magicColors
    val description = pluralStringResource(R.plurals.draft_guide_section_count, count, count)
    Surface(shape = ChipShape, color = colors.primaryAccent.copy(alpha = 0.15f)) {
        Text(
            count.toString(),
            style = MaterialTheme.magicTypography.labelSmall,
            color = colors.primaryAccent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = MaterialTheme.spacing.sm, vertical = MaterialTheme.spacing.xxs)
                .semantics { contentDescription = description },
        )
    }
}

@Composable
private fun ArchetypeMetaRow(archetype: ArchetypeUi, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (archetype.difficulty.isNotBlank()) {
            Text(
                stringResource(R.string.draft_archetype_difficulty, ""),
                style = typography.labelLarge,
                color = colors.primaryAccent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(spacing.xs))
            Text(archetype.difficulty, style = typography.labelLarge, color = colors.secondaryAccent)
        }
        Spacer(Modifier.weight(1f))
        val winRate = archetype.winRate
        if (winRate != null) {
            Icon(
                Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = null,
                tint = colors.goldMtg,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(spacing.sm))
            Text(
                stringResource(R.string.draft_archetype_win_rate, winRate * 100),
                style = typography.labelMedium,
                color = colors.goldMtg,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun expansionStateText(expanded: Boolean): String = stringResource(
    if (expanded) R.string.draft_guide_state_expanded else R.string.draft_guide_state_collapsed,
)

@Composable
private fun GuideSkeleton() {
    val colors = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    val loadingDescription = stringResource(R.string.draft_guide_loading)
    val transition = rememberInfiniteTransition(label = "guide_skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "guide_skeleton_pulse",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = spacing.lg, vertical = spacing.sm)
            .semantics(mergeDescendants = true) { contentDescription = loadingDescription },
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        repeat(SKELETON_SECTION_COUNT) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .graphicsLayer { alpha = pulse }
                    .clip(CardShape)
                    .background(colors.surface),
            )
            if (index == 0) {
                listOf(1f, 0.92f, 0.6f).forEach { fraction ->
                    Box(
                        modifier = Modifier
                            .padding(start = spacing.lg)
                            .fillMaxWidth(fraction)
                            .height(14.dp)
                            .graphicsLayer { alpha = pulse }
                            .clip(ChipShape)
                            .background(colors.surfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftGuideRichText(
    text: GuideRichText,
    style: TextStyle,
    linkKeyPrefix: String,
    onCardClick: (String, String?) -> Unit,
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.magicColors.textPrimary,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val density = LocalDensity.current
    val segments = text.segments
    val fontSize = if (style.fontSize == TextUnit.Unspecified) typography.bodyMedium.fontSize else style.fontSize
    val inlineSize = fontSize * 1.15f
    val inlineContent = remember(segments, inlineSize, density) {
        val symbolSize = with(density) { inlineSize.toDp() }
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
                    ManaSymbolImage(token = token, size = symbolSize)
                }
            }
    }
    val annotatedText = remember(segments, linkKeyPrefix, colors, baseColor, onCardClick) {
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

// ═══════════════════════════════════════════════════════════════════════════════
//  Tier List tab
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun TierListSubTab(
    state: SetDraftDetailUiState,
    listState: LazyListState,
    onToggleFilters: (String) -> Unit,
    onToggleColor: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCardClick: (String, String?) -> Unit,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
) {
    val spacing = MaterialTheme.spacing
    val filterTitle = stringResource(R.string.deckbuilder_filter_title)
    val filtersExpanded = TIER_FILTERS_ID in state.expandedGuideIds

    when {
        state.tierListTiers != null -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = TIER_FILTERS_ID, contentType = CONTENT_TYPE_SECTION_HEADER) {
                    GuideSectionHeader(
                        title = filterTitle,
                        icon = Icons.Default.FilterList,
                        count = null,
                        expanded = filtersExpanded,
                        onToggle = { onToggleFilters(TIER_FILTERS_ID) },
                        modifier = Modifier
                            .animateItem()
                            .padding(horizontal = spacing.lg, vertical = spacing.sm),
                    )
                }
                if (filtersExpanded) {
                    item(key = "$TIER_FILTERS_ID-content", contentType = "tier_filters") {
                        TierFilterPanel(
                            searchQuery = state.tierListSearchQuery,
                            colorFilter = state.tierListColorFilter,
                            onSearchQueryChanged = onSearchQueryChanged,
                            onToggleColor = onToggleColor,
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = spacing.lg)
                                .padding(start = spacing.sm, bottom = spacing.sm),
                        )
                    }
                }

                state.filteredTiers.forEachIndexed { tierIndex, tier ->
                    stickyHeader(key = "tier-header-$tierIndex-${tier.tier}", contentType = "tier_header") {
                        TierBanner(
                            tier = tier.tier,
                            label = tier.label,
                            description = tier.description,
                            onCardClick = onCardClick,
                        )
                    }

                    items(tier.cards, key = { it.key }, contentType = { CONTENT_TYPE_CARD }) { cardUi ->
                        CardRow(
                            card = cardUi.card,
                            isInCollection = false,
                            onClick = { onCardClick(cardUi.card.scryfallId, cardUi.key) },
                            onRemove = null,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            sharedTransitionKey = cardUi.key,
                        )
                    }

                    item(key = "tier-spacer-$tierIndex-${tier.tier}", contentType = "tier_spacer") {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
        state.tierListError != null -> PlaceholderMessage(stringResource(R.string.draft_tier_list_not_available))
        else -> LoadingIndicator()
    }
}

@Composable
private fun TierFilterPanel(
    searchQuery: String,
    colorFilter: Set<String>,
    onSearchQueryChanged: (String) -> Unit,
    onToggleColor: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SmallCardShape)
            .background(colors.surface.copy(alpha = 0.9f))
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = {
                Text(
                    stringResource(R.string.draft_search_cards_hint),
                    style = typography.bodyMedium,
                    color = colors.textDisabled,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = SmallCardShape,
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = colors.textDisabled)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = stringResource(R.string.draft_search_clear),
                            tint = colors.textDisabled,
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primaryAccent,
                unfocusedBorderColor = colors.surfaceVariant.copy(alpha = 0.5f),
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                cursorColor = colors.primaryAccent,
            ),
        )
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(
                stringResource(R.string.draft_filter_by_color),
                style = typography.labelSmall,
                color = colors.textDisabled,
                fontWeight = FontWeight.Bold,
            )
            ManaColorPicker(
                selectedColors = colorFilter,
                onToggleColor = onToggleColor,
                itemSize = 40.dp,
                symbolSize = 26.dp,
            )
        }
    }
}

@Composable
private fun TierBanner(
    tier: String,
    label: String,
    description: GuideRichText?,
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
                        if (description != null) {
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
