package com.mmg.magicfolder.feature.collection

data class CollectionUiState(
    val cards:         List<CollectionCardGroup> = emptyList(),
    val isLoading:     Boolean                   = false,
    val error:         String?                   = null,
    val searchQuery:   String                    = "",
    val activeFilters: Set<ColorFilter>          = emptySet(),  // emptySet = ALL (no filter)
    val sortOrder:     SortOrder                 = SortOrder.DATE_ADDED,
    val viewMode:      ViewMode                  = ViewMode.GRID,
    val hasStaleCards: Boolean                   = false,
)

enum class ColorFilter { ALL, W, U, B, R, G, COLORLESS }
enum class SortOrder   { DATE_ADDED, NAME, PRICE_DESC, PRICE_ASC, RARITY }
enum class ViewMode    { GRID, LIST }
