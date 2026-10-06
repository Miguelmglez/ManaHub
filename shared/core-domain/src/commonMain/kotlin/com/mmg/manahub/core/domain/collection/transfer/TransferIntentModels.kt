package com.mmg.manahub.core.domain.collection.transfer

/** Recognition and omission acceptance never choose a destination. */
enum class TransferDestination { NONE, COLLECTION, WISHLIST }

/** Each deliberate action gets an installation-local idempotency identity. */
@kotlin.jvm.JvmInline
value class TransferActionId(val value: String) {
    init { TransferJobId(value) }
}

/** Entry versions protect one choice; intent revisions protect an explicitly chosen bulk subset. */
sealed interface TransferActionScope {
    data class Entry(val id: String, val version: Long) : TransferActionScope
    data class DestinationSelection(val intentRevision: Long) : TransferActionScope
}

/** Confirmation concerns only the explicit destination and subset, never every recognized card. */
data class TransferActionRequest(
    val id: TransferActionId,
    val jobId: TransferJobId,
    val generation: Long,
    val destination: TransferDestination,
    val scope: TransferActionScope,
    val acceptExclusions: Boolean = false,
    val acceptRepeatedFiles: Boolean = false,
    val expectedPayloadVersion: Long? = null,
)

/** Frozen command snapshots survive edits to unrelated pending entries. */
enum class TransferActionPhase { CONFIRMED, FROZEN, COMPLETED, REVIEW_REQUIRED, FAILED_RETRYABLE, INVALIDATED, DISCARDED }

/** Action counts are meaningful only for their own destination, not collection totals. */
data class TransferActionSummary(
    val id: TransferActionId,
    val jobId: TransferJobId,
    val destination: TransferDestination,
    val phase: TransferActionPhase,
    val entries: Long,
    val copies: Long,
    val completedEntries: Long,
    val completedCopies: Long,
)

/** Pure eligibility does not replace owner/session checks inside the action transaction. */
fun TransferActionRequest.matchesEntry(entry: TransferReviewEntry, currentGeneration: Long, currentIntentRevision: Long): Boolean =
    generation == currentGeneration && destination != TransferDestination.NONE && entry.destination == destination &&
        !entry.excluded && entry.error == null && entry.appliedQuantity == 0L && entry.activeActionId == null &&
        entry.quantity in 1L..Int.MAX_VALUE.toLong() && when (val selection = scope) {
            is TransferActionScope.Entry -> selection.id == entry.id && selection.version == entry.version
            is TransferActionScope.DestinationSelection -> selection.intentRevision == currentIntentRevision
        }
