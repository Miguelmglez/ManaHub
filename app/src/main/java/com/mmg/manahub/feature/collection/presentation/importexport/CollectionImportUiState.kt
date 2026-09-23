package com.mmg.manahub.feature.collection.presentation.importexport

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.PreferredCurrency
import com.mmg.manahub.core.model.QueuedCard

/**
 * State of the Collection "Import to collection" flow: paste/file input, batched resolution, and the
 * private review queue (never the shared AddCard/Scanner queue).
 */
data class CollectionImportUiState(
    val isInputSheetVisible: Boolean = false,
    val isResumePromptVisible: Boolean = false,
    val isResolving: Boolean = false,
    val progressProcessed: Int = 0,
    val progressTotal: Int = 0,
    val inputError: CollectionImportError? = null,
    val queue: List<QueuedCard> = emptyList(),
    val isQueueSheetVisible: Boolean = false,
    val isCommitting: Boolean = false,
    val isAddingAllToWishlist: Boolean = false,
    val inFlightIds: Set<String> = emptySet(),
    val isAutoDeleteOnAddEnabled: Boolean = false,
    val preferredCurrency: PreferredCurrency = PreferredCurrency.USD,
    val ownedCardIdentityKeys: Set<String> = emptySet(),
    val queueToast: CollectionImportToast? = null,
    val unresolvedLines: List<String> = emptyList(),
    /** How many lines really failed; [unresolvedLines] is capped, this is not. */
    val unresolvedTotal: Int = 0,
    val isUnresolvedDialogVisible: Boolean = false,
    val editingEntry: QueuedCard? = null,
    val availablePrints: List<Card> = emptyList(),
    val isLoadingPrints: Boolean = false,
    val variantSelectorEntry: QueuedCard? = null,
    val cardVariants: List<Card> = emptyList(),
    val isLoadingVariants: Boolean = false,
    val expandedVariantImageUrl: String? = null,
) {
    /** What the screen actually shows — the flag alone can outlive an emptied queue. */
    val isQueueSheetOpen: Boolean get() = isQueueSheetVisible && queue.isNotEmpty()
}

/** Why the input sheet could not produce a queue. */
sealed interface CollectionImportError {
    data object NothingRecognized : CollectionImportError
    data object NothingResolved : CollectionImportError
    data class RateLimited(val retryAfterSeconds: Long) : CollectionImportError
    data object LookupFailed : CollectionImportError
    data object FileTooLarge : CollectionImportError
    data object FileUnreadable : CollectionImportError

    /** The list, or the list plus the pending review, holds more rows than the queue accepts. */
    data class TooManyLines(val maxLines: Int) : CollectionImportError
}

/** One-shot messages about the review queue. */
sealed interface CollectionImportToast {
    /** @property clampedCopies copies dropped by the 9,999-per-row cap, never silent. */
    data class Resolved(val entries: Int, val unresolved: Int, val clampedCopies: Int = 0) : CollectionImportToast
    data class AddedToCollection(val cardName: String) : CollectionImportToast
    data class AddedToWishlist(val cardName: String) : CollectionImportToast
    data class AddFailed(val cardName: String) : CollectionImportToast
    data class AddedAllToCollection(val count: Int) : CollectionImportToast
    data class AddedAllToWishlist(val count: Int) : CollectionImportToast
    data class AddAllPartialFailure(val failed: Int, val total: Int) : CollectionImportToast
}
