package com.mmg.manahub.feature.multiadd.presentation

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardSelectionEntry
import com.mmg.manahub.core.model.CardSelectionSession
import com.mmg.manahub.core.model.MagicSet
import com.mmg.manahub.core.ui.components.MagicToastType

data class MultiAddCardUiState(
    val scanSession: CardSelectionSession = CardSelectionSession(),
    val hasPreloadedItems: Boolean = false,
    val firstLoadedCards: List<Card> = emptyList(),
    val loadedCards: List<Card> = emptyList(),
    val isLoading: Boolean = true,
    val selectedSet: MagicSet? = null,
    val showLanguagePicker: Boolean = false,
    val showCardStatusPicker: Boolean = false,
    val defaultLanguage: String = "EN",
    val defaultCardState: String = "NM",
    val showAsGrid: Boolean = true,
    val processingData: Boolean = false,
    val hasMore: Boolean = false,
    val currentPage: Int = 1,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val query: String = "",
    val searchLanguage: String = "en",
    val activeQuery: AdvancedSearchQuery? = null,
    val showAdvancedSearch: Boolean = false,
    val showSelectedQueueSheet: Boolean = false,
    val toastMessage: String? = null,
    val toastType: MagicToastType = MagicToastType.SUCCESS,
    // resolves must not double-commit the queue.
    val isCommittingQueue: Boolean = false,
    val isAutoDeleteOnAddEnabled: Boolean = true,
    val ownedCardIdentityKeys: Set<String> = emptySet(),
    // Variant selector sheet
    val showVariantSelector: Boolean = false,
    val variantSelectorEntry: CardSelectionEntry? = null,
    val cardVariants: List<Card> = emptyList(),
    val isLoadingVariants: Boolean = false,
    // Full-screen image viewer
    val expandedVariantImageUrl: String? = null,
    val selectedIsFoil: Boolean = false,
    val selectedLanguage: String = "en",
    val selectedCondition: String = "NM",
    val selectedQuantity: Int = 1,
    val availablePrints: List<Card> = emptyList(),
    val isLoadingPrints: Boolean = false,
    val selectedCardDetailId: String? = null,
    val returnToQueueOnDetailClose: Boolean = false,
    val editingCard: CardSelectionEntry? = null,
    val totalCards : Int? = null
    )

val MultiAddCardUiState.activeFilterCount: Int
    get() = activeQuery?.criteria?.size ?: 0
