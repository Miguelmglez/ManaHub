package com.mmg.manahub.feature.decks.presentation.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.ui.Res
import com.mmg.manahub.core.ui.components.CircularDistribution
import com.mmg.manahub.core.ui.components.MagicCardInspectionOverlay
import com.mmg.manahub.core.ui.components.ManaSymbolImage
import com.mmg.manahub.core.ui.mtg_card_back
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeId
import com.mmg.manahub.feature.decks.domain.engine.ColorStrategyEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ThemeId
import com.mmg.manahub.feature.decks.domain.template.CollectionTribeSignal
import com.mmg.manahub.feature.decks.domain.template.OwnedCommanderCandidate
import com.mmg.manahub.feature.decks.domain.usecase.SeedStrategyCandidate
import com.mmg.manahub.feature.decks.presentation.components.CardRow
import com.mmg.manahub.feature.decks.presentation.components.CommanderBanner
import org.jetbrains.compose.resources.painterResource

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 2 — Direction
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Deck Engine Unification plan (§5 Phase 3.1) — dispatches Step 2's content by
 * [DeckWizardUiState.entryFlow]. [WizardEntryFlow.CARDS] is the ORIGINAL wizard content
 * ([CardsFlowDirectionContent], unchanged in shape, now also showing a ranked "Suggested
 * strategies" section once seeds exist); [WizardEntryFlow.COLORS]/[WizardEntryFlow.STRATEGY] are the
 * two NEW flows, both defined in `DeckWizardEntryFlows.kt`. All three converge on the SAME
 * one-slot archetype/theme/tribe pick and the SAME [onAddSeed]/[onRemoveSeed] seed list — see each
 * flow's own KDoc for its specific UX.
 */
@Composable
internal fun DirectionStepContent(
    uiState: DeckWizardUiState,
    onSelectDirectionTag: (CardTag) -> Unit,
    onSelectTribe: (CollectionTribeSignal) -> Unit,
    onCommanderQueryChange: (String) -> Unit,
    onSelectCommander: (Card) -> Unit,
    onClearCommander: () -> Unit,
    onToggleSeedPicker: () -> Unit,
    onSeedQueryChange: (String) -> Unit,
    onAddSeed: (Card) -> Unit,
    onRemoveSeed: (Card) -> Unit,
    onSelectSeedStrategyCandidate: (SeedStrategyCandidate) -> Unit,
    onToggleColorFlowColor: (ManaColor) -> Unit,
    onSelectColorAffinityEntry: (ColorStrategyEntry) -> Unit,
    onTaxonomyQueryChange: (String) -> Unit,
    onSelectTaxonomyArchetype: (ArchetypeId) -> Unit,
    onSelectTaxonomyTheme: (ThemeId) -> Unit,
    onSelectColorCombo: (ColorComboSuggestion) -> Unit,
    onToggleSuggestedSeed: (Card) -> Unit,
    onNext: () -> Unit,
) {
    when (uiState.entryFlow) {
        WizardEntryFlow.CARDS -> CardsFlowDirectionContent(
            uiState = uiState,
            onSelectDirectionTag = onSelectDirectionTag,
            onSelectTribe = onSelectTribe,
            onCommanderQueryChange = onCommanderQueryChange,
            onSelectCommander = onSelectCommander,
            onClearCommander = onClearCommander,
            onToggleSeedPicker = onToggleSeedPicker,
            onSeedQueryChange = onSeedQueryChange,
            onAddSeed = onAddSeed,
            onRemoveSeed = onRemoveSeed,
            onSelectSeedStrategyCandidate = onSelectSeedStrategyCandidate,
            onNext = onNext,
        )
        WizardEntryFlow.COLORS -> ColorsFlowDirectionContent(
            uiState = uiState,
            onToggleColor = onToggleColorFlowColor,
            onSelectAffinityEntry = onSelectColorAffinityEntry,
            onToggleSuggestedSeed = onToggleSuggestedSeed,
            onNext = onNext,
        )
        WizardEntryFlow.STRATEGY -> StrategyFlowDirectionContent(
            uiState = uiState,
            onQueryChange = onTaxonomyQueryChange,
            onSelectArchetype = onSelectTaxonomyArchetype,
            onSelectTheme = onSelectTaxonomyTheme,
            onSelectCombo = onSelectColorCombo,
            onToggleSuggestedSeed = onToggleSuggestedSeed,
            onNext = onNext,
        )
    }
}

@Composable
private fun CardsFlowDirectionContent(
    uiState: DeckWizardUiState,
    onSelectDirectionTag: (CardTag) -> Unit,
    onSelectTribe: (CollectionTribeSignal) -> Unit,
    onCommanderQueryChange: (String) -> Unit,
    onSelectCommander: (Card) -> Unit,
    onClearCommander: () -> Unit,
    onToggleSeedPicker: () -> Unit,
    onSeedQueryChange: (String) -> Unit,
    onAddSeed: (Card) -> Unit,
    onRemoveSeed: (Card) -> Unit,
    onSelectSeedStrategyCandidate: (SeedStrategyCandidate) -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val isCommanderFormat = uiState.selectedFormat == DeckFormat.COMMANDER
    val canProceed = !isCommanderFormat || uiState.selectedCommander != null

    // Visual-overhaul pass: every card tile in this step (seed search results, commander
    // candidates/search results) can be tap-zoomed in place via MagicCardInspectionOverlay,
    // mirroring DeckStudioScreen's Strategies-tab inline inspection pattern (`inspectionCard`/
    // `inspectionRect`/`isDismissingInspection`) -- rootCoordinates anchors every tile's captured
    // Rect to THIS Box so the flight animation lines up regardless of scroll position.
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var inspectionCard by remember { mutableStateOf<Card?>(null) }
    var inspectionRect by remember { mutableStateOf(Rect.Zero) }
    var isDismissingInspection by remember { mutableStateOf(false) }
    val onZoomCard: (Card, Rect) -> Unit = { card, rect ->
        isDismissingInspection = false
        inspectionCard = card
        inspectionRect = rect
    }

    // C1 (design review): the sticky CTA is a real Column sibling, not a Box overlay with a
    // guessed bottom-padding reservation (see FormatStepContent for the full rationale).
    Box(Modifier.fillMaxSize().onGloballyPositioned { rootCoordinates = it }) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, top = spacing.md, bottom = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.lg),
            ) {
                item(key = "header") {
                    Column {
                        Text(stringResource(R.string.deck_wizard_direction_title), style = ty.titleLarge, color = mc.textPrimary)
                        Text(
                            stringResource(R.string.deck_wizard_direction_subtitle),
                            style = ty.bodyMedium,
                            color = mc.textSecondary,
                            modifier = Modifier.padding(top = spacing.xxs),
                        )
                    }
                }

                // Seed cards moved to the TOP of the step (visual-overhaul pass): picking a few
                // seed cards is the most common entry point for Flow A and should not be buried
                // below the commander picker / collection leans.
                item(key = "seed_toggle") {
                    SeedPickerToggleRow(expanded = uiState.showSeedPicker, onToggle = onToggleSeedPicker)
                }
                if (uiState.showSeedPicker) {
                    item(key = "seed_search") {
                        SeedSearchInline(query = uiState.seedQuery, isSearching = uiState.isSearchingSeeds, onQueryChange = onSeedQueryChange)
                    }
                    if (uiState.seedQuery.trim().length >= 2 && uiState.seedSearchResults.isNotEmpty()) {
                        item(key = "seed_search_results") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                                items(uiState.seedSearchResults, key = { "seedres_${it.scryfallId}" }) { card ->
                                    WizardCardImageTile(
                                        card = card,
                                        rootCoordinates = rootCoordinates,
                                        onSelect = { onAddSeed(card) },
                                        onZoom = onZoomCard,
                                    )
                                }
                            }
                        }
                    }
                    if (uiState.seedCards.isNotEmpty()) {
                        item(key = "seed_picked_header") {
                            Text(
                                stringResource(R.string.deck_seeds_picked_title),
                                style = ty.labelMedium,
                                color = mc.textSecondary,
                                modifier = Modifier.padding(top = spacing.xs),
                            )
                        }
                        items(uiState.seedCards, key = { "seedpick_${it.scryfallId}" }) { card ->
                            // Global CardRow (image-based) replaces the old text-only
                            // WizardCardPickedRow -- onClick opens the same inline zoom (no
                            // on-screen rect is tracked for this full-width row, so it flies in
                            // from Rect.Zero, same fallback SynergyCardTile uses when its
                            // rootCoordinates is null).
                            CardRow(
                                card = card,
                                isInCollection = true,
                                onClick = { onZoomCard(card, Rect.Zero) },
                                onRemove = { onRemoveSeed(card) },
                            )
                        }
                    }
                }

                if (isCommanderFormat) {
                    item(key = "commander_section") {
                        CommanderPickerSection(
                            uiState = uiState,
                            rootCoordinates = rootCoordinates,
                            onQueryChange = onCommanderQueryChange,
                            onSelect = onSelectCommander,
                            onClear = onClearCommander,
                            onZoom = onZoomCard,
                        )
                    }
                }

                item(key = "collection_leans") {
                    CollectionLeanSection(
                        uiState = uiState,
                        onSelectDirectionTag = onSelectDirectionTag,
                        onSelectTribe = onSelectTribe,
                    )
                }

                // Deck Engine Unification plan (§5 Phase 3.2, RC5) — once at least one seed (or the
                // commander) is picked, rank viable strategies FROM the seeds themselves instead of
                // leaving the user to guess a Direction chip that may not even fit what they just picked.
                val suggestion = uiState.seedStrategySuggestion
                if (suggestion != null) {
                    item(key = "seed_strategy_header") {
                        Text(
                            stringResource(R.string.deck_wizard_suggested_strategies_title),
                            style = ty.labelLarge,
                            color = mc.primaryAccent,
                            modifier = Modifier.padding(top = spacing.xs),
                        )
                    }
                    if (!suggestion.isCoherent) {
                        item(key = "seed_coherence_warning") {
                            SeedCoherenceWarning()
                        }
                    }
                    if (suggestion.candidates.isEmpty()) {
                        item(key = "seed_strategy_empty") {
                            Text(
                                stringResource(R.string.deck_wizard_suggested_strategies_empty),
                                style = ty.bodySmall,
                                color = mc.textSecondary,
                            )
                        }
                    } else {
                        item(key = "seed_strategy_chips") {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                                verticalArrangement = Arrangement.spacedBy(spacing.xs),
                            ) {
                                suggestion.candidates.forEach { candidate ->
                                    val selected = candidate.profile.archetype == uiState.selectedArchetype &&
                                        candidate.profile.themes.firstOrNull() == uiState.selectedDirectionTheme &&
                                        candidate.profile.tribe == uiState.selectedTribeKey
                                    DirectionChip(
                                        label = candidate.label,
                                        selected = selected,
                                        onClick = { onSelectSeedStrategyCandidate(candidate) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            WizardStickyButton(
                label = stringResource(R.string.deck_wizard_next),
                enabled = canProceed,
                onClick = onNext,
            )
        }

        inspectionCard?.let { card ->
            MagicCardInspectionOverlay(
                card = card,
                initialRect = inspectionRect,
                isVisible = true,
                isDismissing = isDismissingInspection,
                onDismissRequest = { isDismissingInspection = true },
                onDismiss = {
                    inspectionCard = null
                    isDismissingInspection = false
                },
            )
        }
    }
}

/** Shown when [com.mmg.manahub.feature.decks.domain.usecase.SeedStrategySuggestion.isCoherent] is
 * false — the seeds' own identity-tag/tribe fingerprints barely overlap each other (plan §5 3.2:
 * "incoherent seed set -> inline explanation, never a silent bad build"). The ranked candidates below
 * this banner still render — the user can pick one anyway, or go back and trim their seed picks. */
@Composable
private fun SeedCoherenceWarning() {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(shape = CardShape, color = mc.goldMtg.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.deck_wizard_seed_coherence_warning),
            style = ty.bodySmall,
            color = mc.textSecondary,
            modifier = Modifier.padding(MaterialTheme.spacing.md),
        )
    }
}

@Composable
private fun CommanderPickerSection(
    uiState: DeckWizardUiState,
    rootCoordinates: LayoutCoordinates?,
    onQueryChange: (String) -> Unit,
    onSelect: (Card) -> Unit,
    onClear: () -> Unit,
    onZoom: (Card, Rect) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(stringResource(R.string.deck_wizard_commander_section_title), style = ty.labelLarge, color = mc.primaryAccent)

        val commander = uiState.selectedCommander
        if (commander != null) {
            // Global CommanderBanner replaces the old bespoke WizardHeroRow -- an explicit
            // "Change commander" affordance sits below it since the banner itself has no clear
            // action (unlike the old row's trailing close icon).
            CommanderBanner(commander = commander, modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onClear),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.deck_wizard_change_commander),
                    style = ty.labelMedium,
                    color = mc.textSecondary,
                )
            }
            return@Column
        }

        val candidates = uiState.collectionProfile?.commanderCandidates.orEmpty()
        if (candidates.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                items(candidates, key = { "cand_${it.card.scryfallId}" }) { candidate: OwnedCommanderCandidate ->
                    WizardCardImageTile(
                        card = candidate.card,
                        rootCoordinates = rootCoordinates,
                        onSelect = { onSelect(candidate.card) },
                        onZoom = onZoom,
                    )
                }
            }
        }

        OutlinedTextField(
            value = uiState.commanderQuery,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.deck_wizard_commander_search_hint), style = ty.bodyMedium, color = mc.textDisabled) },
            leadingIcon = {
                if (uiState.isSearchingCommander) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = mc.primaryAccent, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary)
                }
            },
            trailingIcon = if (uiState.commanderQuery.isNotEmpty()) {
                { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_close), tint = mc.textSecondary) } }
            } else null,
            shape = CardShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = mc.primaryAccent,
                unfocusedBorderColor = mc.surfaceVariant,
                focusedTextColor = mc.textPrimary,
                unfocusedTextColor = mc.textPrimary,
                cursorColor = mc.primaryAccent,
            ),
        )
        if (uiState.commanderQuery.trim().length >= 2 && uiState.commanderSearchResults.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                items(uiState.commanderSearchResults.take(8), key = { "candres_${it.scryfallId}" }) { card ->
                    WizardCardImageTile(
                        card = card,
                        rootCoordinates = rootCoordinates,
                        onSelect = { onSelect(card) },
                        onZoom = onZoom,
                    )
                }
            }
        }
    }
}

/** Tile width matching the 63:88 standard MTG card aspect (mirrors `DiscoveryRow.kt`'s
 * `SynergyCardTile` proportions so every card-image tile in the app looks consistent). */
private val WizardTileWidth = 88.dp
private const val WIZARD_CARD_ASPECT_RATIO = 63f / 88f

/**
 * The shared image+magnifier tile for every card-picking surface in the Direction step (commander
 * candidates/search results, seed search results). Unlike `DiscoveryRow.kt`'s `SynergyCardTile`
 * (a single tap = zoom), this tile needs TWO independent actions on one thumbnail: tapping the
 * card image itself SELECTS it (adds as a seed / picks as commander), while the small magnifier
 * badge in the bottom-end corner opens [MagicCardInspectionOverlay] instead -- the badge is a
 * sibling `Box` layered on top with its own `clickable`, so a tap within its bounds is consumed by
 * the badge and never reaches the image's `clickable` underneath. Visual precedent:
 * [com.mmg.manahub.core.ui.components.VariantSelectorSheet]'s `VariantCardItem` magnifier badge
 * and `DiscoveryRow.kt`'s `SynergyCardTile` (`Icons.Default.ZoomIn`, translucent circular scrim).
 *
 * @param rootCoordinates the step's root `Box` coordinates (see [CardsFlowDirectionContent]) the
 *   captured [Rect] is relative to; `null` skips rect tracking (zoom still opens, from [Rect.Zero]).
 * @param onSelect invoked when the card image itself is tapped.
 * @param onZoom invoked with the card and its on-screen [Rect] when the magnifier badge is tapped.
 */
@Composable
private fun WizardCardImageTile(
    card: Card,
    rootCoordinates: LayoutCoordinates?,
    onSelect: () -> Unit,
    onZoom: (Card, Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val spacing = MaterialTheme.spacing
    var tileRect by remember { mutableStateOf(Rect.Zero) }
    val zoomDescription = stringResource(R.string.deck_wizard_zoom_card, card.name)

    Box(
        modifier = modifier
            .width(WizardTileWidth)
            .aspectRatio(WIZARD_CARD_ASPECT_RATIO)
            .clip(CardShape)
            .background(mc.surfaceVariant)
            .onGloballyPositioned { coords ->
                val root = rootCoordinates
                if (root != null && root.isAttached && coords.isAttached) {
                    tileRect = root.localBoundingBoxOf(coords)
                }
            },
    ) {
        AsyncImage(
            model = card.imageNormal,
            contentDescription = card.name,
            placeholder = painterResource(Res.drawable.mtg_card_back),
            error = painterResource(Res.drawable.mtg_card_back),
            fallback = painterResource(Res.drawable.mtg_card_back),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clip(CardShape)
                .clickable(onClick = onSelect),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(48.dp)
                .clickable { onZoom(card, tileRect) }
                .semantics { contentDescription = zoomDescription },
            contentAlignment = Alignment.BottomEnd,
        ) {
            Box(
                modifier = Modifier
                    .padding(spacing.xxs)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ZoomIn,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * Deck Engine Unification plan (D2): renders one Direction chip per STRATEGY tag the collection
 * leans into ([signal.tag][com.mmg.manahub.feature.decks.domain.template.CollectionStrategySignal
 * .tag]) that resolves onto the unified taxonomy via [DeckIdentitySeedTags.archetypeForTag] or
 * `.themeForTag`. A tag with STILL no [com.mmg.manahub.feature.decks.domain.engine.ArchetypeId]/
 * [ThemeId] home under the new taxonomy is filtered out of the rendered list entirely (never a
 * "dead" -- selectable-but-inert -- chip); this is the taxonomy-era continuation of the Wizard
 * Quality Campaign B1 "every rendered chip must do something when tapped" fix, NOT a regression of
 * it -- the pre-unification fix kept a chip alive via a raw-[CardTag] pin fallback, but D2
 * deliberately closed that fallback slot (the taxonomy is now `StrategyProfile`-only, with no
 * "arbitrary tag" pin anywhere left to carry it). See [DeckIdentitySeedTags]'s `THEME_TAGS` KDoc
 * for how the 4 originally-called-out dead tags (`plus_counters`/`death_triggers`/`spellslinger`/
 * `etb`) all resolve under the new mapping.
 *
 * Visual-overhaul pass: the per-color bar rows (`ColorLeanRow`) were replaced by the shared
 * [CircularDistribution] ring chart (same component `StatsScreen` uses for its color-distribution
 * ring), fed the collection's FULL colored-pip share breakdown instead of just the top 3.
 */
@Composable
private fun CollectionLeanSection(
    uiState: DeckWizardUiState,
    onSelectDirectionTag: (CardTag) -> Unit,
    onSelectTribe: (CollectionTribeSignal) -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(stringResource(R.string.deck_wizard_direction_leans_title), style = ty.labelLarge, color = mc.primaryAccent)

        if (uiState.isLoadingProfile) {
            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = mc.primaryAccent, modifier = Modifier.size(24.dp))
            }
            return@Column
        }

        val profile = uiState.collectionProfile
        if (profile == null || (profile.colorShares.isEmpty() && profile.dominantStrategies.isEmpty() && profile.dominantTribes.isEmpty())) {
            Text(
                stringResource(R.string.deck_wizard_direction_leans_empty),
                style = ty.bodySmall,
                color = mc.textSecondary,
            )
            return@Column
        }

        if (profile.colorShares.isNotEmpty()) {
            // CircularDistribution expects the exact English color names as map keys
            // ("White"/"Blue"/.../"Colorless", which is exactly ManaColor.displayName) and
            // computes BOTH the legend percentages AND its centered "Total" number from the map's
            // raw values -- it must be fed a genuine count, not a normalized share rescaled to an
            // arbitrary int. The previous `(share * 1000f).roundToInt()` fed synthetic weights that
            // always summed to ~1000, so the ring's centered "Total" showed a meaningless number
            // instead of the collection's real colored-pip count (StatsScreen's own
            // CircularDistribution calls always feed real counts -- this call site was the only one
            // that didn't). [CollectionColorShare.pipCount] is the raw pip count; see
            // feedback_circulardistribution_wizard_pipcount_not_scaled_share.md for the full root
            // cause (colorShares() never contains a zero-share entry, so the previously-suspected
            // coerceAtLeast(1)-hides-zero-share bug does not actually occur).
            val colorData = remember(profile.colorShares) {
                profile.colorShares.associate { it.color.displayName to it.pipCount }
            }
            CircularDistribution(
                data = colorData,
                colorMapper = { label -> manaPipColor(label, mc.primaryAccent) },
                isColor = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (profile.dominantStrategies.isNotEmpty() || profile.dominantTribes.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                // D2: only render chips whose tag resolves onto the unified taxonomy -- see this
                // composable's class KDoc.
                val archetypeByTag = profile.dominantStrategies.associate { it.tag to DeckIdentitySeedTags.archetypeForTag(it.tag) }
                val themeByTag = profile.dominantStrategies.associate { it.tag to DeckIdentitySeedTags.themeForTag(it.tag) }
                profile.dominantStrategies.forEach { signal ->
                    val archetype = archetypeByTag[signal.tag]
                    val theme = themeByTag[signal.tag]
                    if (archetype == null && theme == null) return@forEach
                    val selected = when {
                        archetype != null -> archetype == uiState.selectedArchetype
                        else -> theme == uiState.selectedDirectionTheme
                    }
                    DirectionChip(
                        label = stringResource(R.string.deck_wizard_direction_chip_strategy, signal.tag.displayLabel, signal.copies),
                        selected = selected,
                        onClick = { onSelectDirectionTag(signal.tag) },
                    )
                }
                profile.dominantTribes.forEach { tribe: CollectionTribeSignal ->
                    // Edge-case audit (Wizard Quality Campaign final gate, 2026-07-19): compare by
                    // the stable tribeKey, not the pluralized displayLabel -- the label is
                    // display-only, the key is what actually threads into StrategyProfile.tribe.
                    val selected = tribe.tribeKey == uiState.selectedTribeKey
                    DirectionChip(
                        label = stringResource(R.string.deck_wizard_direction_chip_tribe, tribe.copies, tribe.displayLabel),
                        selected = selected,
                        onClick = { onSelectTribe(tribe) },
                    )
                }
            }
        }
    }
}

/** Official MTG pip colors for [CollectionLeanSection]'s ring chart, mirroring `StatsScreen`'s own
 * color-distribution hex values so the same color always represents White/Blue/Black/Red/Green
 * across the app (a fixed real-world MTG color identity, not a themed value -- same justified
 * raw-[Color] precedent as `StatsScreen.DistributionsSection`). */
private fun manaPipColor(label: String, fallback: Color): Color = when (label) {
    "White" -> Color(0xFFF9FAFA)
    "Blue" -> Color(0xFF0E68AB)
    "Black" -> Color(0xFF150B00)
    "Red" -> Color(0xFFD3202A)
    "Green" -> Color(0xFF00733E)
    "Colorless" -> Color(0xFF90ADBB)
    else -> fallback
}

/** Same color language as `DeckStudioScreen`'s `StrategiesTabContent` search chips (a plain M3
 * `FilterChip` with `selectedContainerColor = primaryAccent @ 0.2f` / `selectedLabelColor =
 * primaryAccent`, no border) -- direction-tag chips and strategy-search chips must read as the
 * same visual language across the app. Kept as a custom [Surface] (not swapped for a real M3
 * `FilterChip`) so the explicit `heightIn(min = 48.dp)` touch-target floor survives; a bare
 * `FilterChip` at its default M3 sizing does not guarantee that. */
@Composable
internal fun DirectionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Surface(
        onClick = onClick,
        shape = ChipShape,
        color = if (selected) mc.primaryAccent.copy(alpha = 0.2f) else mc.surface,
    ) {
        Text(
            text = label,
            style = ty.labelMedium,
            color = if (selected) mc.primaryAccent else mc.textSecondary,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .wrapContentHeight(Alignment.CenterVertically)
                .padding(horizontal = MaterialTheme.spacing.sm),
        )
    }
}

@Composable
private fun SeedPickerToggleRow(expanded: Boolean, onToggle: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.deck_wizard_seed_picker_toggle), style = ty.labelLarge, color = mc.primaryAccent, modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = mc.primaryAccent,
        )
    }
}

@Composable
private fun SeedSearchInline(query: String, isSearching: Boolean, onQueryChange: (String) -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.deck_seeds_search_hint), style = ty.bodyMedium, color = mc.textDisabled) },
        leadingIcon = {
            if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = mc.primaryAccent, strokeWidth = 2.dp)
            else Icon(Icons.Default.Search, contentDescription = null, tint = mc.textSecondary)
        },
        shape = CardShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = mc.primaryAccent,
            unfocusedBorderColor = mc.surfaceVariant,
            focusedTextColor = mc.textPrimary,
            unfocusedTextColor = mc.textPrimary,
            cursorColor = mc.primaryAccent,
        ),
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
//  Step 3 — Identity
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun IdentityStepContent(
    uiState: DeckWizardUiState,
    onToggleColor: (ManaColor) -> Unit,
    onSelectTheme: (String?) -> Unit,
    onNext: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val isCommanderFormat = uiState.selectedFormat == DeckFormat.COMMANDER

    // D2: only offer EDHREC theme strings that resolve onto a real ThemeId -- an unresolvable
    // free-form aggregate tag has nowhere to bind under the unified taxonomy (mirrors
    // CollectionLeanSection's own dead-chip filter).
    val resolvableThemeTags = remember(uiState.availableThemeTags) {
        uiState.availableThemeTags.filter { ThemeId.fromDisplayName(it) != null }
    }

    // Visual-overhaul pass: the theme picker now needs one full row PER theme (name +
    // description), which no longer fits a wrapping FlowRow of chips -- the whole step moved from
    // Column+verticalScroll to a real LazyColumn (mirrors CardsFlowDirectionContent's own
    // container) so the theme list gets genuine LazyColumn semantics/keys instead of nesting an
    // unbounded LazyColumn inside a scrollable Column.
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            item(key = "header") {
                Column {
                    Text(stringResource(R.string.deck_wizard_identity_title), style = ty.titleLarge, color = mc.textPrimary)
                    Text(
                        if (isCommanderFormat) stringResource(R.string.deck_wizard_identity_subtitle_commander)
                        else stringResource(R.string.deck_wizard_identity_subtitle_casual),
                        style = ty.bodyMedium,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(top = spacing.xxs),
                    )
                }
            }

            item(key = "color_identity") {
                Surface(shape = CardShape, color = mc.backgroundSecondary, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Text(stringResource(R.string.deck_seeds_identity_colors).uppercase(), style = ty.labelMedium, color = mc.primaryAccent)
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            ManaColor.entries.filter { it != ManaColor.C }.forEach { color ->
                                ColorToggleChip(
                                    color = color,
                                    selected = color in uiState.colorIdentity,
                                    readOnly = isCommanderFormat,
                                    onClick = { onToggleColor(color) },
                                )
                            }
                        }
                        if (uiState.colorIdentity.isEmpty()) {
                            Text(stringResource(R.string.deck_seeds_identity_colorless), style = ty.bodySmall, color = mc.textSecondary)
                        }
                    }
                }
            }

            if (uiState.showColorDisciplineHint) {
                item(key = "color_discipline_hint") {
                    Surface(shape = CardShape, color = mc.goldMtg.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.deck_wizard_color_discipline_hint),
                            style = ty.bodySmall,
                            color = mc.textSecondary,
                            modifier = Modifier.padding(spacing.md),
                        )
                    }
                }
            }

            if (uiState.isLoadingThemeTags) {
                item(key = "theme_loading") {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = mc.primaryAccent, modifier = Modifier.size(20.dp))
                    }
                }
            } else if (resolvableThemeTags.isNotEmpty()) {
                item(key = "theme_picker_header") {
                    Text(stringResource(R.string.deck_wizard_theme_picker_title), style = ty.labelLarge, color = mc.primaryAccent)
                }
                items(resolvableThemeTags, key = { "theme_$it" }) { theme ->
                    // Every entry in `resolvableThemeTags` already resolved to a real ThemeId
                    // above -- the `!!` is safe (never actually null here).
                    val themeId = ThemeId.fromDisplayName(theme)!!
                    ThemeRow(
                        themeId = themeId,
                        selected = theme == uiState.selectedThemeHint,
                        onClick = { onSelectTheme(theme) },
                    )
                }
            }
        }
        WizardStickyButton(
            label = stringResource(R.string.deck_wizard_next),
            enabled = true,
            onClick = onNext,
        )
    }
}

/**
 * One row in the Identity step's theme picker (visual-overhaul pass): replaces the old wrapping
 * [DirectionChip] `FlowRow` with a full-width list row showing both the theme's name AND a short
 * plain-English explanation of what it means in MTG deckbuilding terms -- a bare theme name
 * ("Voltron", "Aristocrats") means little to a player unfamiliar with the term.
 */
@Composable
private fun ThemeRow(themeId: ThemeId, selected: Boolean, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = if (selected) mc.primaryAccent.copy(alpha = 0.12f) else mc.surface,
        border = if (selected) BorderStroke(1.dp, mc.primaryAccent) else BorderStroke(0.5.dp, mc.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(spacing.md)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(themeId.displayName, style = ty.bodyLarge, color = if (selected) mc.primaryAccent else mc.textPrimary)
                Text(themeDescription(themeId), style = ty.bodySmall, color = mc.textSecondary)
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = null, tint = mc.primaryAccent)
            }
        }
    }
}

/** One 1-2 line plain-English explanation per [ThemeId] (the 22 themes from Appendix A.2 --
 * see `ArchetypeModels.kt`). A bare theme name means little to a player unfamiliar with the term,
 * so the Identity step's theme list (see [ThemeRow]) always pairs the name with this description. */
@Composable
private fun themeDescription(themeId: ThemeId): String = when (themeId) {
    ThemeId.REANIMATOR -> stringResource(R.string.deck_wizard_theme_desc_reanimator)
    ThemeId.SELF_MILL -> stringResource(R.string.deck_wizard_theme_desc_self_mill)
    ThemeId.ARISTOCRATS -> stringResource(R.string.deck_wizard_theme_desc_aristocrats)
    ThemeId.TOKENS -> stringResource(R.string.deck_wizard_theme_desc_tokens)
    ThemeId.SPELLSLINGER -> stringResource(R.string.deck_wizard_theme_desc_spellslinger)
    ThemeId.VOLTRON -> stringResource(R.string.deck_wizard_theme_desc_voltron)
    ThemeId.STAX -> stringResource(R.string.deck_wizard_theme_desc_stax)
    ThemeId.LANDFALL -> stringResource(R.string.deck_wizard_theme_desc_landfall)
    ThemeId.LIFEGAIN -> stringResource(R.string.deck_wizard_theme_desc_lifegain)
    ThemeId.PLUS1_COUNTERS -> stringResource(R.string.deck_wizard_theme_desc_plus1_counters)
    ThemeId.TRIBAL -> stringResource(R.string.deck_wizard_theme_desc_tribal)
    ThemeId.ARTIFACTS -> stringResource(R.string.deck_wizard_theme_desc_artifacts)
    ThemeId.ENCHANTRESS -> stringResource(R.string.deck_wizard_theme_desc_enchantress)
    ThemeId.WHEELS -> stringResource(R.string.deck_wizard_theme_desc_wheels)
    ThemeId.MILL -> stringResource(R.string.deck_wizard_theme_desc_mill)
    ThemeId.GROUP_HUG -> stringResource(R.string.deck_wizard_theme_desc_group_hug)
    ThemeId.GROUP_SLUG -> stringResource(R.string.deck_wizard_theme_desc_group_slug)
    ThemeId.BLINK -> stringResource(R.string.deck_wizard_theme_desc_blink)
    ThemeId.SUPERFRIENDS -> stringResource(R.string.deck_wizard_theme_desc_superfriends)
    ThemeId.VEHICLES -> stringResource(R.string.deck_wizard_theme_desc_vehicles)
    ThemeId.TOOLBOX -> stringResource(R.string.deck_wizard_theme_desc_toolbox)
    ThemeId.CLONES_THEFT -> stringResource(R.string.deck_wizard_theme_desc_clones_theft)
}

@Composable
internal fun ColorToggleChip(color: ManaColor, selected: Boolean, readOnly: Boolean, onClick: () -> Unit) {
    val mc = MaterialTheme.magicColors
    val border = if (selected) BorderStroke(1.5.dp, mc.primaryAccent) else BorderStroke(0.5.dp, mc.surfaceVariant)
    val background = if (selected) mc.primaryAccent.copy(alpha = 0.18f) else mc.surface
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            ManaSymbolImage(token = color.symbol, size = 24.dp)
        }
    }
    // Read-only (Commander -- colors are derived from the commander, not user-editable): a plain
    // Surface with no `onClick` overload so it never shows an interactive ripple. M3 (design
    // review): a screen reader gets no other signal these are fixed, not toggleable -- an explicit
    // contentDescription distinguishes the two variants for TalkBack users.
    if (readOnly) {
        val fixedColorDescription = stringResource(R.string.deck_wizard_color_fixed_by_commander, color.displayName)
        Surface(
            shape = CircleShape,
            color = background,
            border = border,
            modifier = Modifier
                .size(48.dp)
                .semantics { contentDescription = fixedColorDescription },
            content = content,
        )
    } else {
        Surface(onClick = onClick, shape = CircleShape, color = background, border = border, modifier = Modifier.size(48.dp), content = content)
    }
}
