package com.mmg.manahub.feature.carddetail.presentation

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.model.UserCard
import com.mmg.manahub.core.model.UserCardWithCard
import com.mmg.manahub.core.model.UserDefinedTag
import com.mmg.manahub.core.model.WishlistEntry

/**
 * Card Versions & Languages, Phase 1B. Distinguishes WHERE the [CardDetailUiState.showVariantSelector]
 * sheet was opened from, so dismissing/selecting a variant returns the user to the right surface:
 * - [SCREEN]: opened from the main screen's "Explore All Versions" row — selecting a variant
 *   navigates away to that printing ([CardDetailEvent.NavigateToCard]).
 * - [SHEET]: opened from the Add/Edit collection or wishlist sheet's "Set / Variant" field —
 *   selecting a variant swaps [CardDetailUiState.sheetPrinting] in place and the underlying sheet
 *   stays open (no navigation).
 */
enum class VariantSelectorSource { SCREEN, SHEET }

data class CardDetailUiState(
    val card:             Card?          = null,
    val userCards:        List<UserCardWithCard> = emptyList(),
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
    val variantSelectorSource: VariantSelectorSource = VariantSelectorSource.SCREEN,
    val expandedVariantImageUrl: String?  = null,
    // Language selector state (Card Versions & Languages, Phase 1B)
    val showLanguageSelector: Boolean     = false,
    val languagePrints:       List<Card>  = emptyList(),
    val isLoadingLanguages:   Boolean     = false,
    // Add/edit sheet printing state (Card Versions & Languages, Phase 1B). The printing the
    // Add-to-collection/wishlist sheet is currently configured for — defaults to the displayed
    // [card] when a sheet opens, but can diverge when the user picks a different printing via the
    // sheet's own "Set / Variant" field (see [VariantSelectorSource.SHEET]).
    val sheetPrinting:        Card?       = null,
    // Non-null while the Add-to-collection sheet is in EDIT mode for this collection entry.
    val entryBeingEdited:     UserCardWithCard? = null,
    // Non-null while the Add-to-wishlist sheet is in EDIT mode for this wishlist entry.
    val wishlistEntryBeingEdited: WishlistEntry? = null,
)
