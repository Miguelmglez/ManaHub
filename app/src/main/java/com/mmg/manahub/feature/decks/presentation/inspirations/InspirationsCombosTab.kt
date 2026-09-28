package com.mmg.manahub.feature.decks.presentation.inspirations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.ui.components.EmptyState
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicLoadingFooter
import com.mmg.manahub.core.ui.components.MagicLoadingSize
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.CopyPolicy
import com.mmg.manahub.feature.decks.domain.inspirations.ComboOwnership
import com.mmg.manahub.feature.decks.domain.inspirations.ComboReadiness
import com.mmg.manahub.feature.decks.domain.inspirations.OwnedComboView
import com.mmg.manahub.feature.decks.presentation.wizard.WizardCardInspectionState

/** Combos: nothing is fetched until the user pins one owned card; then every Spellbook combo with it, grouped by missing pieces. */
@Composable
internal fun InspirationsCombosTab(
    state: InspirationsUiState,
    format: DeckFormat?,
    inspection: WizardCardInspectionState,
    onQueryChange: (String) -> Unit,
    onPinCard: (Card) -> Unit,
    onClearPin: () -> Unit,
    onAddCard: (Card, InspirationSelectionSource) -> Unit,
    onDecrementCard: (Card) -> Unit,
    onAddCombo: (OwnedComboView) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val picker = state.combosPicker
    val pinned = picker.pinned
    val groups = remember(state.combos) { ComboOwnership.group(state.combos) }
    val listState = rememberLazyListState()

    // Infinite scroll: ask for the next page once the last rendered item is on screen.
    val reachedEnd by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= listState.layoutInfo.totalItemsCount - 2
        }
    }
    // Keyed on the page count too: a page that adds nothing visible must still chain to the next one.
    LaunchedEffect(reachedEnd, state.combosHasMore, state.isLoadingMoreCombos, state.combosPage, pinned) {
        if (reachedEnd && state.combosHasMore && !state.isLoadingMoreCombos && pinned != null) onLoadMore()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        item(key = "combos_picker") {
            if (pinned != null) {
                val quantity = state.quantityOf(pinned)
                PinnedCollectionCard(
                    card = pinned,
                    caption = stringResource(R.string.deck_inspirations_pinned_combos),
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
                    item(key = "combos_no_results") {
                        EmptyState(
                            title = stringResource(R.string.deck_inspirations_search_empty),
                            icon = Icons.Default.Search,
                            modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xl),
                        )
                    }
                } else {
                    items(picker.results, key = { "combos_result_${it.scryfallId}" }) { card ->
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

            pinned == null -> item(key = "combos_idle") {
                EmptyState(
                    title = stringResource(R.string.deck_inspirations_combos_idle_title),
                    subtitle = stringResource(R.string.deck_inspirations_combos_idle_subtitle),
                    icon = Icons.Default.AutoAwesome,
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xl),
                )
            }

            state.isLoadingCombos -> item(key = "combos_loading") {
                Box(Modifier.fillMaxWidth().padding(vertical = spacing.xl), contentAlignment = Alignment.Center) {
                    MagicLoadingSpinner(size = MagicLoadingSize.Medium)
                }
            }

            state.combosFailed -> item(key = "combos_error") {
                InlineErrorState(
                    message = stringResource(R.string.deck_inspirations_combos_error),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = onRetry,
                    modifier = Modifier.padding(vertical = spacing.lg),
                )
            }

            state.combos.isEmpty() && !state.combosHasMore -> item(key = "combos_empty") {
                EmptyState(
                    title = stringResource(R.string.deck_studio_combos_empty_title),
                    subtitle = stringResource(R.string.deck_inspirations_combos_empty_subtitle),
                    icon = Icons.Default.AutoAwesome,
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.xl),
                )
            }

            else -> {
                ComboReadiness.entries.forEach { readiness ->
                    val combos = groups[readiness].orEmpty()
                    if (combos.isEmpty()) return@forEach
                    item(key = "combos_header_${readiness.name}") {
                        ComboGroupTitle(readiness, combos.size)
                    }
                    items(combos, key = { "combo_${it.combo.id}" }) { view ->
                        InspirationComboCard(
                            view = view,
                            resolveCard = state::comboCard,
                            isSelected = state.isComboSelected(view),
                            onInspect = { card -> inspection.openFromCenter(card) },
                            onAddToSelection = { onAddCombo(view) },
                        )
                    }
                }
                if (state.combosHasMore || state.isLoadingMoreCombos) {
                    item(key = "combos_footer") {
                        MagicLoadingFooter(label = stringResource(R.string.deck_inspirations_combos_loading_more))
                    }
                }
            }
        }
    }
}

@Composable
private fun ComboGroupTitle(readiness: ComboReadiness, count: Int) {
    val text = when (readiness) {
        ComboReadiness.READY -> stringResource(R.string.deck_studio_combos_complete_header, count)
        ComboReadiness.ONE_AWAY -> stringResource(R.string.deck_studio_combos_almost_header, count)
        ComboReadiness.MORE_AWAY -> stringResource(R.string.deck_inspirations_combos_more_away_header, count)
    }
    Text(
        text = text,
        style = MaterialTheme.magicTypography.labelLarge,
        color = MaterialTheme.magicColors.primaryAccent,
        modifier = Modifier.padding(top = MaterialTheme.spacing.xs),
    )
}
