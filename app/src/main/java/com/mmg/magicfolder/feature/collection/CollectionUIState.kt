package com.mmg.magicfolder.feature.collection

import com.mmg.magicfolder.core.domain.model.AdvancedSearchQuery

data class CollectionUiState(
    val cards:         List<CollectionCardGroup> = emptyList(),
    val isLoading:     Boolean                   = false,
    val error:         String?                   = null,
    val searchQuery:   String                    = "",
    val activeQuery:   AdvancedSearchQuery?      = null,
    val sortOrder:     SortOrder                 = SortOrder.DATE_ADDED,
    val viewMode:      ViewMode                  = ViewMode.GRID,
    val hasStaleCards: Boolean                   = false,
)

val CollectionUiState.activeFilterCount: Int
    get() = activeQuery?.criteria?.size ?: 0

enum class SortOrder { DATE_ADDED, NAME, PRICE_DESC, PRICE_ASC, RARITY }
enum class ViewMode  { GRID, LIST }
