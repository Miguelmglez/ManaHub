package com.mmg.manahub.feature.decks.presentation.inspirations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.FeatureFlags
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.ui.components.DeckCardQueueItem
import com.mmg.manahub.core.ui.components.DeckCardQueueSheet
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.components.MagicCtaStyle
import com.mmg.manahub.core.ui.components.MagicToastHost
import com.mmg.manahub.core.ui.components.MagicToastState
import com.mmg.manahub.core.ui.components.ManaTabItem
import com.mmg.manahub.core.ui.components.ManaTabRow
import com.mmg.manahub.core.ui.theme.BottomSheetShape
import com.mmg.manahub.core.ui.theme.LocalPreferredCurrency
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.feature.decks.domain.engine.CopyPolicy
import com.mmg.manahub.feature.decks.domain.inspirations.OwnedComboView
import com.mmg.manahub.feature.decks.presentation.wizard.SeedsAddedPill
import com.mmg.manahub.feature.decks.presentation.wizard.WizardCardInspectionHost
import com.mmg.manahub.feature.decks.presentation.wizard.WizardStickyButton
import com.mmg.manahub.feature.decks.presentation.wizard.rememberWizardCardInspectionState

/** Every state and callback the Browse inspirations sheet needs; the sheet itself holds no business state. */
internal class BrowseInspirationsActions(
    val onDismiss: () -> Unit,
    val onSelectTab: (InspirationsTab) -> Unit,
    val onQueryChange: (InspirationsTab, String) -> Unit,
    val onPinCard: (InspirationsTab, Card) -> Unit,
    val onClearPin: (InspirationsTab) -> Unit,
    val onAddCard: (Card, InspirationSelectionSource) -> Unit,
    val onDecrementCard: (Card) -> Unit,
    val onRemoveCard: (Card) -> Unit,
    val onClearSelection: () -> Unit,
    val onToggleQueue: () -> Unit,
    val onBrowseSection: (String) -> Unit,
    val onRetrySynergies: () -> Unit,
    val onAddCombo: (OwnedComboView) -> Unit,
    val onLoadMoreCombos: () -> Unit,
    val onRetryCombos: () -> Unit,
    val onStartBuilding: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowseInspirationsSheet(
    state: InspirationsUiState,
    format: DeckFormat?,
    ownedCardIds: Set<String>,
    toastState: MagicToastState,
    actions: BrowseInspirationsActions,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val inspection = rememberWizardCardInspectionState()

    ModalBottomSheet(
        onDismissRequest = actions.onDismiss,
        sheetState = sheetState,
        shape = BottomSheetShape,
        containerColor = mc.background,
    ) {
        WizardCardInspectionHost(
            state = inspection,
            modifier = Modifier.fillMaxHeight(0.92f),
            actions = { card ->
                val quantity = state.quantityOf(card)
                val atCap = format != null && quantity >= CopyPolicy.maxSeedCopies(card, format)
                MagicCtaButton(
                    onClick = {
                        actions.onAddCard(card, InspirationSelectionSource.THUMBNAIL)
                        inspection.requestDismiss()
                    },
                    text = stringResource(R.string.deck_inspirations_add_to_selection),
                    enabled = !atCap,
                    style = MagicCtaStyle.Filled,
                    color = MagicCtaColor.Primary,
                    icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (quantity > 0) {
                    Text(
                        text = stringResource(R.string.deck_inspirations_in_selection, quantity),
                        style = ty.labelMedium,
                        color = mc.textSecondary,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                MagicCtaButton(
                    onClick = inspection::requestDismiss,
                    text = stringResource(R.string.action_cancel),
                    style = MagicCtaStyle.Ghost,
                    color = MagicCtaColor.Neutral,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(horizontal = spacing.lg)) {
                    Text(
                        text = stringResource(R.string.deck_studio_browse_inspirations),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                        modifier = Modifier.padding(top = spacing.xs),
                    )
                    Text(
                        text = stringResource(R.string.deck_inspirations_subtitle),
                        style = ty.bodySmall,
                        color = mc.textSecondary,
                        modifier = Modifier.padding(top = spacing.xxs, bottom = spacing.sm),
                    )
                }
                ManaTabRow(
                    items = listOf(
                        ManaTabItem(
                            label = stringResource(R.string.deck_studio_inspirations_tab_strategies),
                            selected = state.tab == InspirationsTab.STRATEGIES,
                            onClick = { actions.onSelectTab(InspirationsTab.STRATEGIES) },
                        ),
                        ManaTabItem(
                            label = stringResource(R.string.deck_studio_inspirations_tab_combos),
                            selected = state.tab == InspirationsTab.COMBOS,
                            onClick = { actions.onSelectTab(InspirationsTab.COMBOS) },
                        ),
                    ),
                )

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (state.tab) {
                        InspirationsTab.STRATEGIES -> InspirationsStrategiesTab(
                            state = state,
                            format = format,
                            inspection = inspection,
                            onQueryChange = { actions.onQueryChange(InspirationsTab.STRATEGIES, it) },
                            onPinCard = { actions.onPinCard(InspirationsTab.STRATEGIES, it) },
                            onClearPin = { actions.onClearPin(InspirationsTab.STRATEGIES) },
                            onAddCard = actions.onAddCard,
                            onDecrementCard = actions.onDecrementCard,
                            onBrowseSection = actions.onBrowseSection,
                            onRetry = actions.onRetrySynergies,
                        )
                        InspirationsTab.COMBOS -> InspirationsCombosTab(
                            state = state,
                            format = format,
                            inspection = inspection,
                            onQueryChange = { actions.onQueryChange(InspirationsTab.COMBOS, it) },
                            onPinCard = { actions.onPinCard(InspirationsTab.COMBOS, it) },
                            onClearPin = { actions.onClearPin(InspirationsTab.COMBOS) },
                            onAddCard = actions.onAddCard,
                            onDecrementCard = actions.onDecrementCard,
                            onAddCombo = actions.onAddCombo,
                            onLoadMore = actions.onLoadMoreCombos,
                            onRetry = actions.onRetryCombos,
                        )
                    }
                }

                SeedsAddedPill(
                    seedCopies = state.selectedCopies,
                    onClick = actions.onToggleQueue,
                    label = { stringResource(R.string.deck_inspirations_selected_pill, it) },
                    contentDescription = { stringResource(R.string.deck_inspirations_selected_pill_a11y, it) },
                )
                if (FeatureFlags.Decks.DISCOVERY_BUILD_HANDOFF_ENABLED && state.selectedCopies > 0) {
                    WizardStickyButton(
                        label = stringResource(R.string.deck_inspirations_start_building),
                        enabled = true,
                        onClick = actions.onStartBuilding,
                    )
                }
            }
            // The sheet is its own window: the screen-level toast host would render underneath it.
            MagicToastHost(state = toastState, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
        }
    }

    if (state.showSelectionQueue) {
        DeckCardQueueSheet(
            title = stringResource(R.string.deck_inspirations_queue_title, state.selectedCopies),
            items = state.selection.map { pick ->
                DeckCardQueueItem(
                    id = pick.card.scryfallId,
                    card = pick.card,
                    quantity = pick.quantity,
                    maxQuantity = format?.let { CopyPolicy.maxSeedCopies(pick.card, it) },
                    isInCollection = pick.card.scryfallId in ownedCardIds,
                )
            },
            preferredCurrency = LocalPreferredCurrency.current,
            isBusy = false,
            onDismiss = actions.onToggleQueue,
            onIncrement = { item -> actions.onAddCard(item.card, InspirationSelectionSource.QUEUE) },
            onDecrement = { item -> actions.onDecrementCard(item.card) },
            onRemove = { item -> actions.onRemoveCard(item.card) },
            // The queue is its own window, so the sheet's overlay only shows once the queue closes.
            onImageClick = { item ->
                actions.onToggleQueue()
                inspection.openFromCenter(item.card)
            },
            onClearAll = actions.onClearSelection,
            emptyTitle = stringResource(R.string.deck_inspirations_queue_empty_title),
            emptySubtitle = stringResource(R.string.deck_inspirations_queue_empty_subtitle),
        )
    }
}
