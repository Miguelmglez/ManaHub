package com.mmg.manahub.feature.addcard.presentation

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.QueuedCard

/**
 * UI state of the AddCard screen.
 *
 * Multi-select ("Select multiple") fields mirror the app-wide shared card queue: [queue] is a
 * snapshot of it and [selectedScryfallIds] is derived once per queue change, so a card counts as
 * selected whenever any queue entry (scanned or tapped) has its scryfallId.
 */
data class AddCardUiState(
    val query:             String     = "",
    val activeQuery:       AdvancedSearchQuery? = null,
    val results:           List<Card> = emptyList(),
    val totalCards:        Int        = 0,
    val isSearching:       Boolean    = false,
    val isLoadingMore:     Boolean    = false,
    val currentPage:       Int        = 1,
    val hasMore:           Boolean    = false,
    val error:             String?    = null,
    val preferredCurrency: PreferredCurrency = PreferredCurrency.EUR,
    val searchLanguage:    String            = "en",
    val spotlightCards:    List<Card> = emptyList(),
    val spotlightSet:      com.mmg.manahub.core.model.MagicSet? = null,
    val isSpotlightLoading: Boolean = false,
    val viewMode:            CollectionViewMode        = CollectionViewMode.GRID,
    val isMultiSelectMode: Boolean = false,
    val queue: List<QueuedCard> = emptyList(),
    val selectedScryfallIds: Set<String> = emptySet(),
    val showQueueSheet: Boolean = false,
    val isCommittingQueue: Boolean = false,
    val isAddingAllToWishlist: Boolean = false,
    val inFlightQueueIds: Set<String> = emptySet(),
    val queueToast: AddCardQueueToast? = null,
    val ownedCardIdentityKeys: Set<String> = emptySet(),
    val isAutoDeleteOnAddEnabled: Boolean = false,
    val editingQueuedCard: QueuedCard? = null,
    val availablePrints: List<Card> = emptyList(),
    val isLoadingPrints: Boolean = false,
    val variantSelectorEntry: QueuedCard? = null,
    val cardVariants: List<Card> = emptyList(),
    val isLoadingVariants: Boolean = false,
    val expandedVariantImageUrl: String? = null,
    val deckSource: AddCardDeckSource? = null,
    val deckName: String? = null,
    val deckCards: List<Card> = emptyList(),
    val isDeckLoading: Boolean = false,
    val deckLoadFailed: Boolean = false,
    )

/** True while a preloaded deck list replaces the Scryfall search. */
val AddCardUiState.isDeckMode: Boolean
    get() = deckSource != null

val AddCardUiState.activeFilterCount: Int
    get() = activeQuery?.criteria?.size ?: 0

/** Number of entries in the shared queue, shown on the "Proceed with X selected" CTA. */
val AddCardUiState.queueCount: Int
    get() = queue.size

/** True when the bottom "Proceed" CTA must be shown. */
val AddCardUiState.showProceedCta: Boolean
    get() = isMultiSelectMode && queue.isNotEmpty()

/** Ids drawn as selected: selection visuals exist only in multi-select mode; the queue itself is untouched. */
val AddCardUiState.visibleSelectedScryfallIds: Set<String>
    get() = if (isMultiSelectMode) selectedScryfallIds else emptySet()

/** True when every card of the visible (filtered) deck list is queued; flips the CTA to "Unselect all". */
val AddCardUiState.areAllVisibleDeckCardsSelected: Boolean
    get() = isDeckMode && results.isNotEmpty() && results.all { it.scryfallId in selectedScryfallIds }

/** One-shot queue feedback, resolved to a localized string by the screen. */
sealed interface AddCardQueueToast {
    data class AddedToCollection(val cardName: String) : AddCardQueueToast
    data class AddedToWishlist(val cardName: String) : AddCardQueueToast
    data class AddFailed(val cardName: String) : AddCardQueueToast
    data class AddedAllToCollection(val count: Int) : AddCardQueueToast
    data class AddedAllToWishlist(val count: Int) : AddCardQueueToast
    data class AddAllPartialFailure(val failed: Int, val total: Int) : AddCardQueueToast
    data class DeckCardsSelected(val count: Int) : AddCardQueueToast
    data class SelectionLockedWhileAdding(val cardName: String) : AddCardQueueToast
}
