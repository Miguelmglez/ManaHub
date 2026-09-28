package com.mmg.manahub.feature.decks.presentation.inspirations

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.feature.decks.domain.inspirations.CollectionSynergies
import com.mmg.manahub.feature.decks.domain.inspirations.InspirationPick
import com.mmg.manahub.feature.decks.domain.inspirations.InspirationSelection
import com.mmg.manahub.feature.decks.domain.inspirations.OwnedComboView

enum class InspirationsTab { STRATEGIES, COMBOS }

/** Where a selection add came from (telemetry only). */
enum class InspirationSelectionSource { BROWSE, THUMBNAIL, PINNED, COMBO, QUEUE }

/** One tab's owned-card search: a query with its results, or a pinned card that replaces the field. */
data class CollectionCardPickerState(
    val query: String = "",
    val results: List<Card> = emptyList(),
    val pinned: Card? = null,
)

/** Browse inspirations sheet state; [selection] is shared by both tabs and dies with the sheet. */
data class InspirationsUiState(
    val isOpen: Boolean = false,
    val tab: InspirationsTab = InspirationsTab.STRATEGIES,
    val selection: List<InspirationPick> = emptyList(),
    val showSelectionQueue: Boolean = false,
    val synergies: CollectionSynergies? = null,
    val isLoadingSynergies: Boolean = false,
    val synergiesFailed: Boolean = false,
    val strategiesPicker: CollectionCardPickerState = CollectionCardPickerState(),
    val combosPicker: CollectionCardPickerState = CollectionCardPickerState(),
    val combos: List<OwnedComboView> = emptyList(),
    val combosTotalCount: Int? = null,
    val combosPage: Int = 0,
    val combosHasMore: Boolean = false,
    val isLoadingCombos: Boolean = false,
    val isLoadingMoreCombos: Boolean = false,
    val combosFailed: Boolean = false,
    /** Keyed by lowercased card name; owned pieces come from the collection, the rest from one batched lookup. */
    val comboCardsByName: Map<String, Card> = emptyMap(),
) {
    val selectedCopies: Int get() = InspirationSelection.copies(selection)

    val visibleSynergies: CollectionSynergies?
        get() = strategiesPicker.pinned?.let { pinned -> synergies?.containing(pinned.name) } ?: synergies

    fun quantityOf(card: Card): Int = InspirationSelection.quantityOf(selection, card)

    fun comboCard(name: String): Card? =
        comboCardsByName[name.lowercase()] ?: comboCardsByName[name.lowercase().substringBefore(" // ")]

    /** True when every piece of [view] is already selected (the combo CTA then reads "Added"). */
    fun isComboSelected(view: OwnedComboView): Boolean =
        view.combo.cardNames.all { name -> comboCard(name)?.let { InspirationSelection.contains(selection, it) } == true }
}
