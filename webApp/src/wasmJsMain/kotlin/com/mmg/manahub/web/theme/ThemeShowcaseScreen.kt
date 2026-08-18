package com.mmg.manahub.web.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.ui.components.MagicCard
import com.mmg.manahub.core.ui.layout.AdaptiveCardGrid
import com.mmg.manahub.core.ui.layout.ManaWindowSizeClass
import com.mmg.manahub.core.ui.components.MagicFilterChip
import com.mmg.manahub.core.ui.theme.AppTheme
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import org.koin.compose.viewmodel.koinViewModel

/**
 * First real `:webApp` screen (web roadmap W1) — deliberately a showcase, not an MVP screen (Auth
 * is W2's job). Proves three things live in the browser at once:
 *  1. All 12 [AppTheme] palettes render correctly through [com.mmg.manahub.core.ui.theme.MagicTheme].
 *  2. [AdaptiveCardGrid] reflows a placeholder card grid across breakpoints.
 *  3. A preference toggle round-trips through the real `window.localStorage`-backed
 *     [com.mmg.manahub.core.common.KeyValueStore] (via [ThemeShowcaseViewModel] + `koinViewModel()`).
 */
private val ALL_THEMES: List<AppTheme> = listOf(
    AppTheme.NeonVoid,
    AppTheme.MedievalGrimoire,
    AppTheme.ArcaneCosmos,
    AppTheme.ForestMurmur,
    AppTheme.AncientOak,
    AppTheme.HallowedPrint,
    AppTheme.AzureFlux,
    AppTheme.PlanarVeil,
    AppTheme.VenomShade,
    AppTheme.GlacialEdge,
    AppTheme.DuskEmber,
    AppTheme.OnyxNoir,
)

/** Placeholder cards for the grid — no [Card.imageNormal], so [MagicCard] renders its shared
 * `mtg_card_back` fallback art for every one. Real card data arrives once a repository is wired
 * (web roadmap W3); this screen only needs to prove the grid reflows. */
private val PLACEHOLDER_CARDS: List<Card> = (1..8).map { index ->
    Card(
        scryfallId = "w1-placeholder-$index",
        name = "Placeholder Card $index",
        printedName = null,
        manaCost = null,
        cmc = 0.0,
        colors = emptyList(),
        colorIdentity = emptyList(),
        typeLine = "",
        printedTypeLine = null,
        oracleText = null,
        printedText = null,
        keywords = emptyList(),
        power = null,
        toughness = null,
        loyalty = null,
        setCode = "",
        setName = "",
        collectorNumber = "",
        rarity = "",
        releasedAt = "",
        frameEffects = emptyList(),
        promoTypes = emptyList(),
        lang = "en",
        imageNormal = null,
        imageArtCrop = null,
        imageBackNormal = null,
        priceUsd = null,
        priceUsdFoil = null,
        priceEur = null,
        priceEurFoil = null,
        legalityStandard = "not_legal",
        legalityPioneer = "not_legal",
        legalityModern = "not_legal",
        legalityCommander = "not_legal",
        flavorText = null,
        artist = null,
        scryfallUri = "",
    )
}

@Composable
fun ThemeShowcaseScreen(
    windowSizeClass: ManaWindowSizeClass,
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.magicColors
    val typography = MaterialTheme.magicTypography
    val viewModel = koinViewModel<ThemeShowcaseViewModel>()
    val prefEnabled by viewModel.prefEnabled.collectAsState()
    val collectionViewMode by viewModel.collectionViewMode.collectAsState()

    // Root column scrolls -- AdaptiveScaffold/AdaptiveContentArea never provide scrolling
    // themselves (they only clamp/pad), so a short-but-wide viewport (mobile landscape, or a short
    // desktop window at MEDIUM) would otherwise clip content with no way to reach it. Safe to
    // combine with the bounded-height LazyVerticalGrid below (AdaptiveCardGrid has a fixed
    // `.height(360.dp)` here, not `fillMaxHeight()`/wrap-unbounded, so there's no nested
    // unbounded-scroll measurement conflict).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        Text(
            text = "ManaHub Web — W1 responsive + persistence showcase",
            style = typography.titleLarge,
            color = colors.textPrimary,
        )

        // ── 1) Theme picker — all 12 AppTheme palettes ───────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text("Theme", style = typography.titleMedium, color = colors.textPrimary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                ALL_THEMES.forEach { theme ->
                    val label = theme::class.simpleName.orEmpty()
                    MagicFilterChip(
                        selected = theme == selectedTheme,
                        onClick = { onThemeSelected(theme) },
                        label = label,
                    )
                }
            }
        }

        // ── 2) Persisted preference toggle — real window.localStorage round-trip ────────────
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text("Persistence", style = typography.titleMedium, color = colors.textPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(
                    text = "Reload this page — this toggle should keep its value.",
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = prefEnabled, onCheckedChange = { viewModel.togglePref() })
            }
        }

        // ── 2b) UserPreferencesRepository smoke check (web roadmap W3a) ─────────────────────
        // Reload this page after toggling -- the value round-trips through the real
        // WebUserPreferencesRepository (localStorage-backed), not just the raw KeyValueStore
        // toggle above -- this proves the REPOSITORY layer's own serialization/flow logic works.
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text("Collection view mode (repository)", style = typography.titleMedium, color = colors.textPrimary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                Text(
                    text = "Current: ${collectionViewMode.name} — reload to confirm it persisted.",
                    style = typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { viewModel.toggleCollectionViewMode() }) {
                    Text("Toggle")
                }
            }
        }

        // ── 3) Placeholder card grid — proves AdaptiveCardGrid reflow ────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text("Cards (placeholder)", style = typography.titleMedium, color = colors.textPrimary)
            AdaptiveCardGrid(
                windowSizeClass = windowSizeClass,
                modifier = Modifier.fillMaxWidth().height(360.dp),
            ) {
                items(PLACEHOLDER_CARDS, key = { it.scryfallId }) { card ->
                    MagicCard(card = card, modifier = Modifier.padding(spacing.xs))
                }
            }
        }
    }
}
