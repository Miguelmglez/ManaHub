package com.mmg.manahub.feature.competitive.presentation

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.model.news.NewsItem
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicFilterChip
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.MagicColors
import com.mmg.manahub.core.ui.theme.ThemeBackground
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.TimeAgoFormatter
import org.koin.androidx.compose.koinViewModel
import java.net.URLEncoder

/**
 * Competitive screen: a static, richly-presented catalog of precise deep links into external MTG
 * competitive sites (tournament decklists, trending decks/cards, standings, power rankings,
 * Limited card ratings, store/event hubs, live streams, deck-building tools), an official
 * event-locator deep link, and a Pro Tour news filter. Stateless-composables-under-a-stateful-root,
 * per the repo's screen convention — [CompetitiveViewModel] owns all state.
 *
 * 2026-08 pivot: this screen makes ZERO live/REST calls to third-party MTG data services — every
 * card below opens a precise, pre-researched URL in a Custom Tab. See
 * [CompetitiveResourceCatalog] and `feature/competitive/CLAUDE.md` for the full rationale.
 *
 * ## 2026-08 redesign
 * The 8 [ResourceCategory] values are grouped into 3 [CompetitiveTab]s (Tournaments/Metagame/Hub)
 * instead of one flat scroll of every category stacked vertically. The format-selector chip row
 * only renders on the two tabs that actually have format-scoped catalog entries
 * ([CompetitiveTab.showsFormatSelector]) — fixing the original design's biggest usability problem,
 * where switching format sat above all 8 categories but only ever visibly changed 2 of them. Each
 * category section is now a collapsible accordion (expand state lives in
 * [CompetitiveUiState.expandedCategories]), and the highest-priority resolved link per category
 * renders as a larger "hero" card (see [HeroResourceCard]) ahead of the compact rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitiveScreen(
    viewModel: CompetitiveViewModel = koinViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mc = MaterialTheme.magicColors

    // Screen-entry breadcrumb (no PII) — matches CardDetailScreen/HomeScreen/DeckStudioScreen's
    // convention. Fires once per entry (keyed on Unit).
    LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("screen_viewed: competitive")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ThemeBackground(modifier = Modifier.fillMaxSize())

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("Competitive", style = MaterialTheme.magicTypography.titleLarge, color = mc.textPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = mc.textPrimary)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
                )
            },
        ) { paddingValues ->
            LazyColumn(
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding() + MaterialTheme.spacing.xxl,
                ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.xl),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "tabs") {
                    CompetitiveTabRow(
                        selected = uiState.selectedTab,
                        onSelect = viewModel::onTabSelected,
                    )
                }

                if (uiState.selectedTab.showsFormatSelector) {
                    item(key = "format_selector") {
                        FormatSelectorRow(
                            selected = uiState.selectedFormat,
                            onSelect = viewModel::onFormatSelected,
                            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
                        )
                    }
                }

                for (category in uiState.selectedTab.categories) {
                    val resolved = CompetitiveResourceCatalog.resolveFor(category, uiState.selectedFormat)
                    item(key = "catalog_${category.name}") {
                        ResourceCategorySection(
                            category = category,
                            links = resolved,
                            selectedFormat = uiState.selectedFormat,
                            expanded = category.name in uiState.expandedCategories,
                            onToggleExpanded = { viewModel.onCategoryToggled(category.name) },
                            onOpen = { url -> openUrl(context, url, mc.primaryAccent.toArgb()) },
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = MaterialTheme.spacing.lg),
                        )
                    }
                }

                item(key = "event_locator") {
                    EventLocatorSection(
                        postalCode = uiState.postalCode,
                        onPostalCodeChanged = viewModel::onPostalCodeChanged,
                        onFindEvents = {
                            val encoded = URLEncoder.encode(uiState.postalCode, "UTF-8")
                            val url = "https://locator.wizards.com/search?searchType=magic-events" +
                                "&query=$encoded&distance=10&unit=km&page=1"
                            openUrl(context, url, mc.primaryAccent.toArgb())
                        },
                        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
                    )
                }

                item(key = "pro_tour_news") {
                    ProTourNewsSection(
                        items = uiState.proTourContent,
                        onOpenArticle = { url -> openUrl(context, url, mc.primaryAccent.toArgb()) },
                        onOpenMore = { openUrl(context, "https://magic.gg/news", mc.primaryAccent.toArgb()) },
                    )
                }

                item(key = "attribution") {
                    AttributionFooter(modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tab selector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompetitiveTabRow(
    selected: CompetitiveTab,
    onSelect: (CompetitiveTab) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    TabRow(
        selectedTabIndex = CompetitiveTab.entries.indexOf(selected),
        containerColor = mc.backgroundSecondary.copy(alpha = 0.9f),
        contentColor = mc.primaryAccent,
        divider = {},
    ) {
        CompetitiveTab.entries.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                text = { Text(tab.displayName, style = ty.labelLarge) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Format selector
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FormatSelectorRow(
    selected: CompetitiveFormat,
    onSelect: (CompetitiveFormat) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm), modifier = modifier) {
        items(CompetitiveFormat.entries, key = { it.id }) { format ->
            FormatChip(
                label = format.displayName,
                isSelected = format == selected,
                onClick = { onSelect(format) },
            )
        }
    }
}

@Composable
private fun FormatChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    MagicFilterChip(
        selected = isSelected,
        onClick = onClick,
        label = label,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Static deep-link resource catalog
// ─────────────────────────────────────────────────────────────────────────────

/** The [MagicColors] accent token that visually identifies a [ResourceCategory] — gives each
 * category section (and its cards' icon badges) a consistent, recognizable color signature. */
private fun ResourceCategory.accentColor(mc: MagicColors): Color = when (this) {
    ResourceCategory.TOURNAMENT_DECKLISTS -> mc.primaryAccent
    ResourceCategory.TRENDING_DECKS_CARDS -> mc.lifePositive
    ResourceCategory.STANDINGS_STATS -> mc.goldMtg
    ResourceCategory.POWER_RANKINGS -> mc.secondaryAccent
    ResourceCategory.LIMITED_RATINGS -> mc.manaU
    ResourceCategory.STORES_HUBS -> mc.manaG
    ResourceCategory.WATCH_LIVE -> mc.commanderAccent
    ResourceCategory.DECK_BUILDING -> mc.manaB
}

/**
 * A collapsible accordion section for one [ResourceCategory]. The header row (icon, name, chevron)
 * toggles [expanded]; the body only renders when there is at least one resolved [links] entry —
 * an empty category has nothing to collapse and shows its "not tracked" message directly. When
 * expanded, the first (highest-priority) resolved link renders as a larger [HeroResourceCard] and
 * the rest as compact [ResourceCatalogCard] rows, giving each section visual hierarchy instead of
 * a flat stack of identical rows.
 */
@Composable
private fun ResourceCategorySection(
    category: ResourceCategory,
    links: List<Pair<CompetitiveResourceLink, String>>,
    selectedFormat: CompetitiveFormat,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val accent = category.accentColor(mc)
    val isCollapsible = links.isNotEmpty()
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "category_chevron_rotation",
    )

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(if (isCollapsible) Modifier.clickable(role = Role.Button, onClick = onToggleExpanded) else Modifier)
                .semantics(mergeDescendants = true) {
                    contentDescription = if (isCollapsible) {
                        "${category.displayName}, ${if (expanded) "expanded" else "collapsed"}"
                    } else {
                        category.displayName
                    }
                },
        ) {
            Icon(category.icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Text(
                text = category.displayName,
                style = ty.titleLarge,
                color = mc.textPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MaterialTheme.spacing.sm),
            )
            if (isCollapsible) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = mc.textSecondary,
                    modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                )
            }
        }

        if (!isCollapsible) {
            Text(
                text = "Not tracked for ${selectedFormat.displayName} yet",
                style = ty.bodySmall,
                color = mc.textDisabled,
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.sm),
            )
        } else {
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
                    modifier = Modifier.padding(top = MaterialTheme.spacing.sm),
                ) {
                    links.forEachIndexed { index, (link, url) ->
                        if (index == 0) {
                            HeroResourceCard(link = link, url = url, icon = category.icon, accent = accent, onOpen = onOpen)
                        } else {
                            ResourceCatalogCard(link = link, url = url, icon = category.icon, accent = accent, onOpen = onOpen)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The visually prominent "hero" treatment for a category's highest-priority resolved link — a
 * bigger icon badge and headline than [ResourceCatalogCard], so each section has one clear focal
 * point instead of a flat stack of identically-weighted rows.
 */
@Composable
private fun HeroResourceCard(
    link: CompetitiveResourceLink,
    url: String,
    icon: ImageVector,
    accent: Color,
    onOpen: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val accessibleDescription = "${link.siteName}. ${link.description}. Opens in browser."

    Surface(
        color = accent.copy(alpha = 0.14f),
        shape = CardShape,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = { onOpen(url) })
            .semantics(mergeDescendants = true) { contentDescription = accessibleDescription },
    ) {
        Column(modifier = Modifier.padding(MaterialTheme.spacing.lg)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(accent.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Text(
                text = link.siteName,
                style = ty.titleLarge,
                color = mc.textPrimary,
                modifier = Modifier.padding(top = MaterialTheme.spacing.md),
            )
            Text(
                text = link.description,
                style = ty.bodyMedium,
                color = mc.textSecondary,
                modifier = Modifier.padding(top = MaterialTheme.spacing.xs),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = MaterialTheme.spacing.md),
            ) {
                Text("Open", style = ty.labelLarge, color = accent)
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .padding(start = MaterialTheme.spacing.xs)
                        .size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ResourceCatalogCard(
    link: CompetitiveResourceLink,
    url: String,
    icon: ImageVector,
    accent: Color,
    onOpen: (String) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val isMinimal = link.emphasis == LinkEmphasis.MINIMAL
    val badgeSize = if (isMinimal) 32.dp else 40.dp
    val iconSize = if (isMinimal) 16.dp else 20.dp

    val accessibleDescription = if (isMinimal) {
        "${link.siteName}. Opens in browser."
    } else {
        "${link.siteName}. ${link.description}. Opens in browser."
    }

    Surface(
        color = mc.surfaceVariant.copy(alpha = if (isMinimal) 0.20f else 0.35f),
        shape = CardShape,
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = { onOpen(url) })
            .semantics(mergeDescendants = true) {
                contentDescription = accessibleDescription
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(
                    horizontal = MaterialTheme.spacing.md,
                    vertical = if (isMinimal) MaterialTheme.spacing.sm else MaterialTheme.spacing.md,
                ),
        ) {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .background(accent.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(iconSize))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = MaterialTheme.spacing.md),
            ) {
                Text(
                    text = link.siteName,
                    style = if (isMinimal) ty.bodyMedium else ty.titleMedium,
                    color = mc.textPrimary,
                )
                if (!isMinimal) {
                    Text(
                        text = link.description,
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = MaterialTheme.spacing.xxs),
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = "Opens in browser",
                tint = mc.textDisabled,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Event locator
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EventLocatorSection(
    postalCode: String,
    onPostalCodeChanged: (String) -> Unit,
    onFindEvents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md), modifier = modifier) {
        Text("Find events near you", style = ty.titleLarge, color = mc.textPrimary)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.sm),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = postalCode,
                onValueChange = onPostalCodeChanged,
                label = { Text("Postal code") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = mc.textSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = mc.textPrimary,
                    unfocusedTextColor = mc.textPrimary,
                    focusedBorderColor = mc.primaryAccent,
                    unfocusedBorderColor = mc.textDisabled,
                    focusedLabelColor = mc.primaryAccent,
                    unfocusedLabelColor = mc.textSecondary,
                    cursorColor = mc.primaryAccent,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        MagicCtaButton(
            onClick = onFindEvents,
            text = "Find events",
            enabled = postalCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Pro Tour news
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ProTourNewsSection(
    items: List<NewsItem>?,
    onOpenArticle: (String) -> Unit,
    onOpenMore: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md)) {
        Text(
            text = "Pro Tour coverage",
            style = ty.titleLarge,
            color = mc.textPrimary,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.lg),
        )

        when {
            items == null -> LoadingRow()
            items.isEmpty() -> EmptyState(
                title = "No Pro Tour coverage cached yet",
                subtitle = "Check back once your News sources cover an upcoming event.",
                icon = Icons.Default.Info,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MaterialTheme.spacing.lg),
            )
            else -> LazyRow(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.md),
                contentPadding = PaddingValues(horizontal = MaterialTheme.spacing.lg),
            ) {
                items(items.take(PRO_TOUR_NEWS_DISPLAY_LIMIT), key = { it.id }) { item ->
                    ProTourNewsCard(item = item, onClick = { onOpenArticle(item.url) })
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clickable(onClick = onOpenMore)
                .padding(horizontal = MaterialTheme.spacing.lg),
        ) {
            Text("More on magic.gg", style = ty.bodySmall, color = mc.primaryAccent)
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = mc.primaryAccent,
                modifier = Modifier
                    .padding(start = MaterialTheme.spacing.xs)
                    .size(14.dp),
            )
        }
    }
}

/**
 * Image-forward Pro Tour news card, ~220dp wide (matches the widget-board horizontal-strip card
 * width convention used elsewhere in the app). Reuses `ArticleCard.kt`'s exact `coil3.compose.AsyncImage`
 * pattern (`ContentScale.Crop`) for [NewsItem.imageUrl] — gracefully omits the image block when
 * null instead of showing a broken-image placeholder.
 */
@Composable
private fun ProTourNewsCard(item: NewsItem, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    Surface(
        color = mc.surfaceVariant.copy(alpha = 0.35f),
        shape = CardShape,
        border = BorderStroke(0.5.dp, mc.surfaceVariant),
        modifier = Modifier
            .width(220.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "${item.title}. ${item.sourceName}. Opens in browser."
            },
    ) {
        Column {
            if (item.imageUrl != null) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                )
            }
            Column(modifier = Modifier.padding(MaterialTheme.spacing.md)) {
                Text(
                    text = item.title,
                    style = ty.bodyMedium,
                    color = mc.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${item.sourceName} · ${TimeAgoFormatter.format(item.publishedAt)}",
                    style = ty.labelSmall,
                    color = mc.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = MaterialTheme.spacing.xs),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Attribution footer
// ─────────────────────────────────────────────────────────────────────────────

/** Static courtesy attribution — every source linked from this screen is opened in the user's own
 * browser via Custom Tabs; ManaHub never hosts, scrapes, or re-serves any of their data. */
@Composable
private fun AttributionFooter(modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Text(
        text = "Every link above opens the source's own site in your browser. ManaHub does not " +
            "host, scrape, or re-serve third-party competitive Magic data.",
        style = ty.labelSmall,
        color = mc.textDisabled,
        modifier = modifier.padding(vertical = MaterialTheme.spacing.md),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Shared small pieces
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingRow() {
    val mc = MaterialTheme.magicColors
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = mc.primaryAccent)
    }
}

private const val PRO_TOUR_NEWS_DISPLAY_LIMIT = 5

/**
 * Opens [url] in a Custom Tab, mirroring `NewsScreen.kt`'s private `openArticle` helper shape
 * (scheme-validated `https`/`http`, try/catch fallback to plain `ACTION_VIEW`) rather than
 * reinventing it. Kept as a small local duplicate (not promoted to `core/util`) — the same
 * judgment call `NewsScreen.kt` made for its own single-file helper. Backs every card on this
 * screen: the resource catalog, event locator, and Pro Tour articles.
 */
private fun openUrl(context: Context, url: String, toolbarColor: Int) {
    val uri = Uri.parse(url)
    if (uri.scheme != "https" && uri.scheme != "http") return
    try {
        val params = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(toolbarColor)
            .build()
        val intent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(params)
            .setShowTitle(true)
            .build()
        intent.launchUrl(context, uri)
    } catch (_: Exception) {
        try {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            // No browser available on the device.
            FirebaseCrashlytics.getInstance().apply {
                log("competitive_event_locator_no_browser")
                recordException(e)
            }
        }
    }
}
