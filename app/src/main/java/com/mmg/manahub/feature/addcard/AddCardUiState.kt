package com.mmg.manahub.feature.addcard

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.PreferredCurrency

data class AddCardUiState(
    val query:             String     = "",
    val results:           List<Card> = emptyList(),
    val isSearching:       Boolean    = false,
    val selectedCard:      Card?      = null,
    val showConfirmSheet:  Boolean    = false,
    val addedSuccessfully: Boolean    = false,
    val error:             String?    = null,
    val preferredCurrency: PreferredCurrency = PreferredCurrency.EUR,
)
