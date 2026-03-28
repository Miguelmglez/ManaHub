package com.mmg.magicfolder.feature.collection

import com.mmg.magicfolder.core.domain.model.UserCardWithCard

data class CollectionUiState(
    val cards:         List<UserCardWithCard> = emptyList(),
    val isLoading:     Boolean                = false,
    val error:         String?                = null,
    val searchQuery:   String                 = "",
    val activeFilters: Set<ColorFilter>       = emptySet(),  // emptySet = ALL (no filter)
    val sortOrder:     SortOrder              = SortOrder.DATE_ADDED,
    val viewMode:      ViewMode               = ViewMode.GRID,
    val hasStaleCards: Boolean                = false,
)

enum class ColorFilter { ALL, W, U, B, R, G, COLORLESS, MULTICOLOR }
enum class SortOrder   { DATE_ADDED, NAME, PRICE_DESC, PRICE_ASC, RARITY }
enum class ViewMode    { GRID, LIST }
