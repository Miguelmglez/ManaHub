package com.mmg.manahub.feature.carddetail

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.domain.model.UserDefinedTag

data class CardDetailUiState(
    val card:             Card?          = null,
    val userCards:        List<UserCard> = emptyList(),
    val userDefinedTags:  List<UserDefinedTag> = emptyList(),
    val decksContainingCard: List<Deck>  = emptyList(),
    val isLoading:        Boolean        = true,
    val error:            String?        = null,
    val isStale:          Boolean        = false,
    // Trade quantities: userCardId → number of copies currently offered for trade
    val tradeQuantities:  Map<String, Int> = emptyMap(),
    // Dialog / sheet state
    val showAddSheet:      Boolean        = false,
    val showWishlistSheet: Boolean        = false,
    val showTradeSheet:    Boolean        = false,
    val showTagPicker:     Boolean        = false,
    val cardToDelete:      UserCard?      = null,
)
