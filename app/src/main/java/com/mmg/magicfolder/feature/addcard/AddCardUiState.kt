package com.mmg.magicfolder.feature.addcard

import com.mmg.magicfolder.core.domain.model.Card

data class AddCardUiState(
    val query:             String     = "",
    val results:           List<Card> = emptyList(),
    val isSearching:       Boolean    = false,
    val selectedCard:      Card?      = null,
    val showConfirmSheet:  Boolean    = false,
    val addedSuccessfully: Boolean    = false,
    val error:             String?    = null,
)
