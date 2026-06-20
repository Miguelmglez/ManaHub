package com.mmg.manahub.feature.communitydecks.presentation

import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeck

/**
 * UI state for the Community Deck detail screen.
 *
 * Models the four canonical render states (loading / error / content) plus the
 * transient import sub-state carried inside [Content].
 */
sealed interface CommunityDeckDetailUiState {

    /** Initial / in-flight fetch of the deck. */
    data object Loading : CommunityDeckDetailUiState

    /**
     * The deck loaded successfully.
     *
     * @property deck the fetched community deck.
     * @property isImporting true while an import is in progress (blocks re-entry).
     * @property importProgress (processed, total) card-resolution progress, or null when idle.
     * @property isStale true when the deck was served from a stale cache (network refresh failed).
     */
    data class Content(
        val deck: CommunityDeck,
        val isImporting: Boolean = false,
        val importProgress: Pair<Int, Int>? = null,
        val isStale: Boolean = false,
    ) : CommunityDeckDetailUiState

    /** The initial deck fetch failed; [message] is a user-facing reason. */
    data class Error(val message: String) : CommunityDeckDetailUiState
}

/**
 * One-shot side effects emitted by [CommunityDeckDetailViewModel].
 *
 * Delivered through a buffered [kotlinx.coroutines.channels.Channel] so repeated
 * events are never equality-collapsed (unlike a StateFlow) and survive a brief
 * lifecycle pause.
 */
sealed interface CommunityDeckDetailEvent {

    /** Navigate to the newly-created local deck in Deck Studio. */
    data class NavigateToDeck(val deckId: String) : CommunityDeckDetailEvent

    /**
     * Show an import-outcome toast.
     *
     * On a fully-successful import [resolvedCount] == [totalCount] and [isError] is false.
     * On a partial import [resolvedCount] < [totalCount]. On a hard failure [isError] is true.
     */
    data class ShowImportResult(
        val isError: Boolean,
        val resolvedCount: Int = 0,
        val totalCount: Int = 0,
    ) : CommunityDeckDetailEvent
}
