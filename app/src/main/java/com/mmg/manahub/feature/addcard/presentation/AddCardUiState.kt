package com.mmg.manahub.feature.addcard.presentation

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.model.PreferredCurrency

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
    )

val AddCardUiState.activeFilterCount: Int
    get() = activeQuery?.criteria?.size ?: 0
