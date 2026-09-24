package com.mmg.manahub.feature.decks.presentation.inspirations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.SectionHeader
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.CardContribution
import com.mmg.manahub.feature.decks.domain.engine.CardSection
import com.mmg.manahub.feature.decks.domain.engine.CopyPolicy
import com.mmg.manahub.feature.decks.domain.engine.SynergyEngine
import com.mmg.manahub.feature.decks.domain.inspirations.CollectionSynergyEngine
import com.mmg.manahub.feature.decks.domain.inspirations.CollectionTribeSynergy
import com.mmg.manahub.feature.decks.domain.inspirations.SynergyMember
import com.mmg.manahub.feature.decks.presentation.components.CardSectionRow
import com.mmg.manahub.feature.decks.presentation.components.SynergyEngineCard
import com.mmg.manahub.feature.decks.presentation.components.axisDisplayLabel
import com.mmg.manahub.feature.decks.presentation.wizard.WizardCardInspectionState

/** Owned-card search, then every engine and tribe of the collection in the Synergy section's layout; a pinned card filters them. */
@Composable
internal fun InspirationsStrategiesTab(
    state: InspirationsUiState,
    format: DeckFormat?,
    inspection: WizardCardInspectionState,
    onQueryChange: (String) -> Unit,
    onPinCard: (Card) -> Unit,
    onClearPin: () -> Unit,
    onAddCard: (Card, InspirationSelectionSource) -> Unit,
    onDecrementCard: (Card) -> Unit,
    onBrowseSection: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val picker = state.strategiesPicker
    val pinned = picker.pinned
    val visible = state.visibleSynergies
    val expandedKeys = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "strategies_picker") {
            if (pinned != null) {
                val quantity = state.quantityOf(pinned)
                PinnedCollectionCard(
                    card = pinned,
                    caption = stringResource(R.string.deck_inspirations_pinned_strategies),
                    quantity = quantity,
                    addEnabled = format == null || quantity < CopyPolicy.maxSeedCopies(pinned, format),
                    onAdd = { onAddCard(pinned, InspirationSelectionSource.PINNED) },
                    onDecrement = { onDecrementCard(pinned) },
                    onInspect = { inspection.openFromCenter(pinned) },
                    onClear = onClearPin,
                )
            } else {
                CollectionCardSearchField(query = picker.query, onQueryChange = onQueryChange)
            }
        }

        when {
            pinned == null && picker.query.isNotBlank() -> {
                if (picker.results.isEmpty()) {
                    item(key = "strategies_no_results") {
                        EmptyState(
                            title = stringResource(R.string.deck_inspirations_search_empty),
                            icon = Icons.Default.Search,
                            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xl),
                        )
                    }
                } else {
                    items(picker.results, key = { "strategies_result_${it.scryfallId}" }) { card ->
                        var rect by remember { mutableStateOf(Rect.Zero) }
                        CollectionCardResultRow(
                            card = card,
                            onPick = { onPinCard(card) },
                            onInspect = { inspection.open(card, rect) },
                            modifier = Modifier.onGloballyPositioned { rect = inspection.thumbnailRectOf(it, imageIsInteractive = true) },
                        )
                    }
                }
            }

            state.synergiesFailed -> item(key = "strategies_error") {
                InlineErrorState(
                    message = stringResource(R.string.deck_inspirations_synergies_error),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = onRetry,
                    modifier = Modifier.padding(vertical = spacing.lg),
                )
            }

            state.isLoadingSynergies || visible == null -> item(key = "strategies_loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = spacing.xl), contentAlignment = Alignment.Center) {
                    MagicLoadingSpinner(size = MagicLoadingSize.Medium)
                }
            }

            visible.isEmpty -> item(key = "strategies_empty") {
                EmptyState(
                    title = stringResource(
                        if (pinned != null) R.string.deck_inspirations_card_no_synergies_title else R.string.deck_studio_inspirations_empty_title,
                    ),
                    subtitle = stringResource(
                        if (pinned != null) R.string.deck_inspirations_card_no_synergies_subtitle else R.string.deck_studio_inspirations_empty_subtitle,
                    ),
                    icon = Icons.Default.AutoAwesome,
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xl),
                )
            }

            else -> {
                if (visible.engines.isNotEmpty()) {
                    item(key = "strategies_engines_header") {
                        SectionTitle(stringResource(R.string.deck_inspirations_section_engines, visible.engines.size))
                    }
                    items(visible.engines, key = { "engine_${it.axis}" }) { engine ->
                        val key = "engine_${engine.axis}"
                        CollectionEngineCard(
                            engine = engine,
                            expanded = expandedKeys[key] ?: (pinned != null),
                            onToggleExpanded = { expandedKeys[key] = !(expandedKeys[key] ?: (pinned != null)) },
                            onCardClick = { card -> inspection.openFromCenter(card) },
                            onBrowseSection = onBrowseSection,
                        )
                    }
                }
                if (visible.tribes.isNotEmpty()) {
                    item(key = "strategies_tribes_header") {
                        SectionTitle(
                            text = stringResource(R.string.deck_inspirations_section_tribes, visible.tribes.size),
                            modifier = Modifier.padding(top = if (visible.engines.isNotEmpty()) spacing.sm else 0.dp),
                        )
                    }
                    items(visible.tribes, key = { "tribe_${it.tribeKey}" }) { tribe ->
                        val key = "tribe_${tribe.tribeKey}"
                        CollectionTribeCard(
                            tribe = tribe,
                            expanded = expandedKeys[key] ?: (pinned != null),
                            onToggleExpanded = { expandedKeys[key] = !(expandedKeys[key] ?: (pinned != null)) },
                            onCardClick = { card -> inspection.openFromCenter(card) },
                            onBrowse = { onBrowseSection(tribe.sectionId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.primaryAccent,
        modifier = modifier,
    )
}

private fun List<SynergyMember>.toContributions(): List<CardContribution> =
    map { CardContribution(scryfallId = it.card.scryfallId, quantity = 1, confidence = it.confidence) }

/** An engine of the collection, drawn by the Synergy section's own [SynergyEngineCard]. */
@Composable
private fun CollectionEngineCard(
    engine: CollectionSynergyEngine,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCardClick: (Card) -> Unit,
    onBrowseSection: (String) -> Unit,
) {
    val cardsById = remember(engine) { (engine.producers + engine.payoffs).associate { it.card.scryfallId to it.card } }
    val producers = remember(engine) { engine.producers.toContributions() }
    val payoffs = remember(engine) { engine.payoffs.toContributions() }
    val producerSection = CardSection(id = engine.producersSectionId, label = "", current = producers.size, contributions = producers)
    val payoffSection = if (engine.isProducerOnly) {
        null
    } else {
        CardSection(id = engine.payoffsSectionId, label = "", current = payoffs.size, contributions = payoffs)
    }
    SynergyEngineCard(
        engine = SynergyEngine(
            axis = engine.axis,
            label = engine.axis,
            producers = producers,
            payoffs = payoffs,
            producerIdeal = 0,
            payoffIdeal = 0,
            state = engine.state,
        ),
        producerSection = producerSection,
        payoffSection = payoffSection,
        resolveCard = cardsById::get,
        onCardClick = { id -> cardsById[id]?.let(onCardClick) },
        onBrowseProducers = { onBrowseSection(engine.producersSectionId) },
        onBrowsePayoffs = { onBrowseSection(engine.payoffsSectionId) },
        expanded = expanded,
        onToggleExpanded = onToggleExpanded,
    )
}

/** A tribe of the collection: its cards (payoffs first) in one browsable `tribe:<x>` section. */
@Composable
private fun CollectionTribeCard(
    tribe: CollectionTribeSynergy,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onCardClick: (Card) -> Unit,
    onBrowse: () -> Unit,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val cardsById = remember(tribe) { tribe.cards.associate { it.card.scryfallId to it.card } }
    val title = "TRIBE:${tribe.subtype}".axisDisplayLabel()
    val section = CardSection(id = tribe.sectionId, label = title, current = tribe.cards.size, contributions = tribe.cards.toContributions())
    var cardsExpanded by remember(tribe.tribeKey) { mutableStateOf(true) }

    Surface(
        shape = CardShape,
        color = mc.backgroundSecondary,
        border = BorderStroke(1.dp, mc.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(spacing.sm)) {
            SectionHeader(title = title, expanded = expanded, onToggle = onToggleExpanded, titleColor = mc.textPrimary)
            Text(
                text = stringResource(R.string.deck_inspirations_tribe_counts, tribe.memberCount, tribe.payoffCount),
                style = ty.labelSmall,
                color = mc.textSecondary,
                modifier = Modifier.padding(top = spacing.xxs, bottom = spacing.xs),
            )
            if (expanded) {
                CardSectionRow(
                    section = section,
                    resolveCard = cardsById::get,
                    onCardClick = { id -> cardsById[id]?.let(onCardClick) },
                    onBrowse = onBrowse,
                    expanded = cardsExpanded,
                    onToggleExpanded = { cardsExpanded = !cardsExpanded },
                )
            }
        }
    }
}
