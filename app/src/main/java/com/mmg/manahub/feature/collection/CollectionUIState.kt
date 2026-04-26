package com.mmg.manahub.feature.collection

import com.mmg.manahub.core.domain.model.AdvancedSearchQuery
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.feature.auth.domain.model.SessionState

/**
 * Immutable state for the collection screen.
 *
 * The [pendingUploadCount] field has been removed. The new sync engine uses
 * [updatedAt]-based LWW detection rather than a sync_status counter. The UI
 * sync surface is now a single button that triggers push + pull via [SyncManager].
 */
data class CollectionUiState(
    val cards:         List<CollectionCardGroup> = emptyList(),
    val isLoading:     Boolean                   = false,
    val error:         String?                   = null,
    val searchQuery:   String                    = "",
    val activeQuery:   AdvancedSearchQuery?      = null,
    val sortOrder:     SortOrder                 = SortOrder.DATE_ADDED,
    val viewMode:      ViewMode                  = ViewMode.GRID,
    val hasStaleCards: Boolean                   = false,
    val selectedTab:   CollectionTab             = CollectionTab.CARDS,
    val syncState:     SyncState                 = SyncState.IDLE,
    val syncError:     String?                   = null,
    val sessionState:  SessionState              = SessionState.Loading,
)

val CollectionUiState.activeFilterCount: Int
    get() = activeQuery?.criteria?.size ?: 0

enum class SortOrder { DATE_ADDED, NAME, PRICE_DESC, PRICE_ASC, RARITY }
enum class ViewMode  { GRID, LIST }
enum class CollectionTab { CARDS, DECKS }
