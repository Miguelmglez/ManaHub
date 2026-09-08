package com.mmg.manahub.feature.collection.presentation

import com.mmg.manahub.core.model.AdvancedSearchQuery
import com.mmg.manahub.core.model.CollectionCardGroup
import com.mmg.manahub.core.model.CollectionGroupingMode
import com.mmg.manahub.core.model.CollectionSection
import com.mmg.manahub.core.model.CollectionSource
import com.mmg.manahub.core.model.CollectionViewMode
import com.mmg.manahub.core.sync.CollectionMergeConflict
import com.mmg.manahub.core.sync.SyncState
import com.mmg.manahub.core.domain.auth.SessionState

/**
 * Immutable state for the collection screen.
 *
 * The [pendingUploadCount] field has been removed. The new sync engine uses
 * [updatedAt]-based LWW detection rather than a sync_status counter. The UI
 * sync surface is now a single button that triggers push + pull via [SyncManager].
 */
data class CollectionUiState(
    val cards:               List<CollectionCardGroup> = emptyList(),
    val isLoading:           Boolean                   = false,
    val error:               String?                   = null,
    val searchQuery:         String                    = "",
    val activeQuery:         AdvancedSearchQuery?      = null,
    /**
     * Which of the user's three lists the Cards tab is showing. Mutually exclusive sources, not
     * intersecting filters — [CollectionSource.WISHLIST] lists the wishlist itself, including cards
     * the user does not own.
     */
    val collectionSource:    CollectionSource          = CollectionSource.COLLECTION,
    /**
     * Rows of the active [collectionSource] whose `CardEntity` is not cached locally yet, so they
     * cannot be rendered and are absent from [cards].
     *
     * Same rule as ADR-008's "ownership data never depends on cache metadata": while this is > 0
     * the list is INCOMPLETE, not empty, and the UI must say so instead of claiming the wishlist /
     * trade list has nothing in it. Always 0 for [CollectionSource.COLLECTION], whose rows carry a
     * `pending_hydration` placeholder card and therefore always render.
     */
    val uncachedSourceRows:  Int                       = 0,
    val sortOrder:           SortOrder                 = SortOrder.DATE_ADDED,
    val sortDirection:       SortDirection             = SortDirection.DESC,
    val viewMode:            CollectionViewMode        = CollectionViewMode.GRID,
    /** "Group by" selection for the Cards tab. [CollectionGroupingMode.NONE] = flat [cards] rendering. */
    val groupingMode:        CollectionGroupingMode    = CollectionGroupingMode.NONE,
    /** Populated only when [groupingMode] != [CollectionGroupingMode.NONE]; empty otherwise. */
    val sections:            List<CollectionSection>   = emptyList(),
    val hasStaleCards:       Boolean                   = false,
    val selectedTab:         CollectionTab             = CollectionTab.CARDS,
    val syncState:           SyncState                 = SyncState.IDLE,
    val syncError:           String?                   = null,
    val sessionState:        SessionState              = SessionState.Loading,
    /**
     * True when there are local collection rows modified after the last successful
     * sync watermark. Drives the "Sync your collection" banner visibility.
     */
    val hasUnsyncedChanges:  Boolean                   = false,
    /** One-shot message surfaced as a Snackbar (e.g. trade list migration result). */
    val snackbarMessage:     String?                   = null,
    /**
     * Write-path hardening audit (Phase 7, 2026-09-06): guest/account collection rows left behind
     * by [com.mmg.manahub.core.data.local.dao.UserCardCollectionDao.assignUserId]'s collision
     * guard, awaiting an explicit user choice via [CollectionMergeConflictSheet]. Empty for the
     * overwhelming majority of users (a collision only happens when the exact same
     * card/foil/condition/language was added both offline and in a previously-logged-in session).
     */
    val pendingMergeConflicts: List<MergeConflictUiItem> = emptyList(),
)

/** Display-ready wrapper: a [CollectionMergeConflict] plus the card metadata to render it. */
data class MergeConflictUiItem(
    val conflict: CollectionMergeConflict,
    val cardName: String,
    val imageUrl: String?,
)

val CollectionUiState.activeFilterCount: Int
    get() = activeQuery?.criteria?.size ?: 0

enum class SortOrder { DATE_ADDED, NAME, PRICE, RARITY }
enum class SortDirection { ASC, DESC }
enum class CollectionTab { CARDS, DECKS, TRADES }
