package com.mmg.manahub.feature.carddetail.presentation

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.model.UserDefinedTag
import com.mmg.manahub.core.domain.model.WishlistEntry

data class CardDetailUiState(
    val card:             Card?          = null,
    val userCards:        List<UserCard> = emptyList(),
    val wishlistEntries:  List<WishlistEntry> = emptyList(),
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
    val wishlistEntryToDelete: WishlistEntry? = null,
    // Variant (other prints) selector state
    val showVariantSelector: Boolean      = false,
    val cardVariants:        List<Card>   = emptyList(),
    val isLoadingVariants:   Boolean      = false,
    val expandedVariantImageUrl: String?  = null,
)
