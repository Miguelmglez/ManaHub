package com.mmg.manahub.feature.collection

import com.mmg.manahub.core.domain.model.AdvancedSearchQuery

enum class SyncState { IDLE, SYNCING, SUCCESS, ERROR }

data class CollectionUiState(
    val cards:              List<CollectionCardGroup> = emptyList(),
    val isLoading:          Boolean                   = false,
    val error:              String?                   = null,
    val searchQuery:        String                    = "",
    val activeQuery:        AdvancedSearchQuery?      = null,
    val sortOrder:          SortOrder                 = SortOrder.DATE_ADDED,
    val viewMode:           ViewMode                  = ViewMode.GRID,
    val hasStaleCards:      Boolean                   = false,
    val selectedTab:        CollectionTab             = CollectionTab.CARDS,
    val syncState:          SyncState                 = SyncState.IDLE,
    val pendingUploadCount: Int                       = 0,
    val syncError:          String?                   = null,
)

val CollectionUiState.activeFilterCount: Int
    get() = activeQuery?.criteria?.size ?: 0

enum class SortOrder { DATE_ADDED, NAME, PRICE_DESC, PRICE_ASC, RARITY }
enum class ViewMode  { GRID, LIST }
enum class CollectionTab { CARDS, DECKS }
