package com.mmg.manahub.web.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.model.DeckSummary
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.MagicCard
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.layout.ManaWindowSizeClass
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * Home -- the seventh and last REAL `:webApp` MVP screen from the master plan's originally-scoped
 * screen list (web roadmap W4d). Replaces the `ThemeShowcaseScreen` fallthrough the "Home" nav tab
 * used since W4a (see `WebNavGraph.kt`'s route KDoc) -- "Theme" keeps its own dedicated
 * `ThemeShowcaseScreen`, this is a genuinely distinct screen now.
 *
 * Deliberately NOT a port of Android's 17-widget customizable Home board (`feature/home/`) --
 * that is a multi-phase gamification/trades/community-decks/first-steps system with its own Room
 * schema and DataStore-persisted layout, entirely out of scope for a web MVP. Per this slice's
 * no-stub mandate (CLAUDE.md's Home section: "a widget/slide may only ship if its data path is
 * real end-to-end... anything that can't be wired must be deleted, never left as a placeholder"),
 * this screen ships ONLY: a static greeting header (no MTG-flavored greeting-pool system), three
 * quick-link buttons to the real feature tabs (Search/Decks/Collection -- cheap, high-value nav
 * shortcuts), and two small "recent" strips backed by [HomeViewModel], which is nothing more than
 * a client-side sort+cap over two ALREADY-real repository reads
 * ([com.mmg.manahub.core.domain.repository.DeckRepository.observeAllDeckSummaries] since W3c,
 * [com.mmg.manahub.core.domain.repository.UserCardRepository.observeCollection] since W3d) -- zero
 * new repository/backend work.
 *
 * Responsive: the quick-link row stacks vertically (full-width buttons) at [ManaWindowSizeClass.COMPACT]
 * and lays out horizontally at MEDIUM+, mirroring the stacked-vs-side-by-side split already
 * established by [com.mmg.manahub.web.carddetail.CardDetailScreen] and
 * [com.mmg.manahub.web.deckeditor.DeckEditorScreen]. The two recent strips are `LazyRow`s
 * (horizontal scroll) at every breakpoint -- capped to 5 items each, so there's no reflow need.
 */
@Composable
fun HomeScreen(
    windowSizeClass: ManaWindowSizeClass,
    onNavigateSearch: () -> Unit,
    onNavigateDecks: () -> Unit,
    onNavigateCollection: () -> Unit,
    onDeckClick: (String) -> Unit,
    onCardClick: (String) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<HomeViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val stacked = windowSizeClass == ManaWindowSizeClass.COMPACT

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(
                    text = "Welcome to ManaHub",
                    style = typography.displayMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = "Your Magic collection, decks, and card search — all in one place.",
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }

        item {
            QuickLinks(
                stacked = stacked,
                onNavigateSearch = onNavigateSearch,
                onNavigateDecks = onNavigateDecks,
                onNavigateCollection = onNavigateCollection,
            )
        }

        item {
            HomeSection(
                title = "Recent Decks",
                isLoading = uiState.isLoading,
                isEmpty = uiState.recentDecks.isEmpty(),
                emptyMessage = "No decks yet — create one from the Decks tab.",
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    items(uiState.recentDecks, key = { it.id }) { deck ->
                        RecentDeckCard(deck = deck, onClick = { onDeckClick(deck.id) })
                    }
                }
            }
        }

        item {
            HomeSection(
                title = "Recently Added",
                isLoading = uiState.isLoading,
                isEmpty = uiState.recentCards.isEmpty(),
                emptyMessage = "No cards yet — search for a card and add it to your collection.",
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    items(uiState.recentCards, key = { it.userCard.id }) { entry ->
                        RecentCardTile(entry = entry, onClick = { onCardClick(entry.card.scryfallId) })
                    }
                }
            }
        }
    }
}

/**
 * Renders the three quick-link buttons -- stacked full-width at COMPACT, side-by-side at MEDIUM+.
 *
 * **Deliberately duplicated per-branch rather than sharing one `@Composable (Modifier) -> Unit`
 * lambda across both layouts.** [MagicCtaButton]'s internal content `Row` applies its own
 * `Modifier.fillMaxWidth()` whenever it has text (see its `CenteredButtonContent` helper) --
 * placed inside a plain (non-weighted) [Row], each button's Box has no bounded max-width
 * constraint of its own, so this internal `fillMaxWidth()` bubbles up and every button
 * independently claims the FULL row width, overflowing the other two off-screen (verified live:
 * only the first of three buttons rendered at 1280px before this fix). [Modifier.weight] is a
 * `RowScope` extension -- it can only be applied to a modifier built INSIDE a [Row]'s content
 * lambda, so a shared cross-scope helper can't apply it. The COMPACT branch doesn't have this
 * problem ([Column] + explicit `fillMaxWidth()` per button is the intended, already-correct full-
 * width stack), so only the MEDIUM+ branch needs the `weight(1f)` fix.
 */
@Composable
private fun QuickLinks(
    stacked: Boolean,
    onNavigateSearch: () -> Unit,
    onNavigateDecks: () -> Unit,
    onNavigateCollection: () -> Unit,
) {
    val spacing = MaterialTheme.spacing

    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            MagicCtaButton(
                onClick = onNavigateSearch,
                text = "Search Cards",
                style = MagicCtaStyle.Filled,
                color = MagicCtaColor.Primary,
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            MagicCtaButton(
                onClick = onNavigateDecks,
                text = "My Decks",
                style = MagicCtaStyle.Outlined,
                color = MagicCtaColor.Accent,
                icon = { Icon(Icons.Default.Style, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
            MagicCtaButton(
                onClick = onNavigateCollection,
                text = "My Collection",
                style = MagicCtaStyle.Outlined,
                color = MagicCtaColor.Accent,
                icon = { Icon(Icons.Default.ViewModule, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            MagicCtaButton(
                onClick = onNavigateSearch,
                text = "Search Cards",
                style = MagicCtaStyle.Filled,
                color = MagicCtaColor.Primary,
                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier.weight(1f),
            )
            MagicCtaButton(
                onClick = onNavigateDecks,
                text = "My Decks",
                style = MagicCtaStyle.Outlined,
                color = MagicCtaColor.Accent,
                icon = { Icon(Icons.Default.Style, contentDescription = null) },
                modifier = Modifier.weight(1f),
            )
            MagicCtaButton(
                onClick = onNavigateCollection,
                text = "My Collection",
                style = MagicCtaStyle.Outlined,
                color = MagicCtaColor.Accent,
                icon = { Icon(Icons.Default.ViewModule, contentDescription = null) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Shared loading/empty/content shell for the two "recent" strips below the quick links. */
@Composable
private fun HomeSection(
    title: String,
    isLoading: Boolean,
    isEmpty: Boolean,
    emptyMessage: String,
    content: @Composable () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(text = title, style = typography.titleMedium, color = colors.textPrimary)
        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.primaryAccent)
            }

            isEmpty -> Text(
                text = emptyMessage,
                style = typography.bodySmall,
                color = colors.textSecondary,
            )

            else -> content()
        }
    }
}

@Composable
private fun RecentDeckCard(deck: DeckSummary, onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(
        modifier = Modifier
            .width(220.dp)
            .clip(CardShape)
            .background(colors.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Text(
            text = deck.name,
            style = typography.titleMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${deck.format.replaceFirstChar { it.uppercase() }} · ${deck.cardCount} cards",
            style = typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RecentCardTile(entry: UserCardWithCard, onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography

    Column(
        modifier = Modifier.width(140.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        MagicCard(
            card = entry.card,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
        )
        CardName(
            name = entry.card.name,
            showFrontOnly = true,
            style = typography.labelSmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
