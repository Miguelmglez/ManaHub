package com.mmg.manahub.core.domain.collection.transfer

import kotlinx.coroutines.flow.Flow

/** A transfer always belongs to an explicit account or proven installation guest. */
sealed interface TransferOwner {
    data class Account(val id: String) : TransferOwner {
        init { require(id.isNotBlank()) }
    }
    data class VerifiedGuest(val installationToken: String) : TransferOwner {
        init { require(installationToken.isNotBlank()) }
    }
}

/** Opaque, canonical UUID safe to pass through navigation and durable worker data. */
@kotlin.jvm.JvmInline
value class TransferJobId(val value: String) {
    init { require(TRANSFER_UUID.matches(value)) }
}

/** Opaque source identity independent of provider names and URIs. */
@kotlin.jvm.JvmInline
value class TransferFileId(val value: String) {
    init { require(TRANSFER_UUID.matches(value)) }
}

private val TRANSFER_UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

/** Receipts can exist during auth loading, whereas jobs cannot have an absent owner. */
sealed interface TransferSession {
    data object Loading : TransferSession
    data class Available(val owner: TransferOwner, val generation: Long) : TransferSession {
        init { require(generation >= 0) }
    }
}

/** Durable import phases retain resumable preparation independently from local application. */
enum class TransferPhase {
    RECEIVING, PARSING, RESOLVING, REVIEW_READY, APPLYING, COMPLETED,
    COMPLETED_WITH_EXCLUSIONS, WAITING_NETWORK, PAUSED_BY_USER, PAUSED_OWNER,
    FAILED_RETRYABLE, REVIEW_REQUIRED, WAITING_FILE_DECISION, REJECTED, DISCARDED,
}

/** Bounded failure categories suitable for UI and PII-safe telemetry. */
enum class TransferError {
    INVALID_SOURCE, GRANT_LOST, FILE_TOO_LARGE, BATCH_TOO_LARGE, TOO_MANY_FILES,
    NO_SPACE, COPY_TIMEOUT, INVALID_ENCODING, STRUCTURAL_FILE, SELECTION_LIMIT,
    OWNER_UNAVAILABLE, OWNER_CHANGED, REVIEW_CHANGED, CONFIRMATION_REQUIRED,
    QUANTITY_OVERFLOW, NETWORK_RETRYABLE, NOT_FOUND, STORAGE_FAILURE, SOURCE_MEMBERSHIP_CHANGED, DESTINATION_UNAVAILABLE, VARIANT_COLLISION,
}

/** Lightweight per-source accounting; metadata never carries provider paths or URI grants. */
data class TransferFileSummary(
    val id: TransferFileId,
    val order: Int,
    val format: CollectionFileFormat?,
    val phase: TransferPhase,
    val selected: Boolean,
    val duplicate: Boolean,
    val previouslyParticipated: Boolean,
    val bytes: Long,
    val dataRecords: Long,
    val preambleRecords: Long,
    val validRecords: Long,
    val invalidRecords: Long,
    val resolvedRecords: Long,
    val unresolvedRecords: Long,
    val error: TransferError?,
    val retired: Boolean = false,
    val originalCopies: Long = 0L,
    val unrepresentedColumns: List<String> = emptyList(),
)

/** Payload generation invalidates all prior acceptance when selection or pending values change. */
data class TransferConfirmation(
    val generation: Long,
    val payloadVersion: Long,
    val acceptExclusions: Boolean,
    val acceptRepeatedFiles: Boolean,
)

/** Bounded summary includes at most ten current files; retired sources remain in the report. */
data class TransferSummary(
    val id: TransferJobId,
    val owner: TransferOwner,
    val phase: TransferPhase,
    val generation: Long,
    val payloadVersion: Long,
    val files: List<TransferFileSummary>,
    val acceptedCopies: Long,
    val appliedCopies: Long,
    val pendingCopies: Long,
    val appliedEntries: Long,
    val pendingEntries: Long,
    val error: TransferError?,
    val excludedEntries: Long = 0L,
    val intentRevision: Long = 0L,
    val wishlistCompletedEntries: Long = 0L,
    val wishlistCompletedCopies: Long = 0L,
    val filesFrozen: Boolean = false,
    val invalidPendingEntries: Long = 0L,
    val retainedWishlistEntries: Long = 0L,
    val retainedWishlistCopies: Long = 0L,
)

/** Lightweight aggregate; full card metadata is hydrated only for visible pages. */
data class TransferReviewEntry(
    val id: String,
    val scryfallId: String,
    val isFoil: Boolean,
    val condition: String,
    val language: String,
    val quantity: Long,
    val appliedQuantity: Long,
    val excluded: Boolean,
    val error: TransferError?,
    val destination: TransferDestination = TransferDestination.NONE,
    val version: Long = 0L,
    val activeActionId: TransferActionId? = null,
    val state: String = "PENDING",
    val sourceEntryId: String? = null,
)

/** Ownership guidance remains distinct from operational failures and never authorizes reassignment. */
enum class TransferOwnerChoice { NEUTRAL_RECEIPT, SESSION_CHANGED, OTHER_ACCOUNT }

/** Closed presentation categories map to localized platform resources without exposing external causes. */
enum class TransferPresentationFailure { STORAGE, RECEIVE, REVIEW_CHANGED, PAGE, QUANTITY_OVERFLOW, VARIANT_COLLISION, OWNER, REPORT, VARIANTS, UNAVAILABLE }

/** Intake failures retain bounded resource identities rather than provider messages. */
enum class TransferIntakeFailure { INVALID_DELIVERY, TOO_MANY_FILES, RECEIVE, RECOVERY }

/** Keyset cursor is tied to a review generation, never an offset into mutable data. */
data class TransferPageCursor(val generation: Long, val afterId: String)

/** Repository implementations enforce a maximum of fifty rows per review page. */
data class TransferPage(val entries: List<TransferReviewEntry>, val next: TransferPageCursor?) {
    init { require(entries.size <= TransferLimits.REVIEW_PAGE_SIZE) }
}

/** Pending rows and durable outcomes are queried independently before hydration. */
enum class TransferReviewScope { PENDING, QUEUE, COLLECTION, WISHLIST, EXCLUDED }

/** Both directions use an exclusive stable entry identifier instead of mutable offsets. */
enum class TransferPageDirection { FORWARD, BACKWARD }

/** A review anchor belongs to exactly one job, generation and visible scope. */
data class TransferReviewCursor(val jobId: TransferJobId, val generation: Long, val scope: TransferReviewScope, val anchorId: String)

/** Entries always arrive in ascending order; boundary anchors support a bounded reloadable window. */
data class TransferReviewPage(val entries: List<TransferReviewEntry>, val previous: TransferReviewCursor?, val next: TransferReviewCursor?) {
    init { require(entries.size <= TransferLimits.REVIEW_PAGE_SIZE) }
}

/** Explicit mutation outcome avoids ambiguous success after a stale review/session. */
sealed interface TransferMutationResult {
    data object Accepted : TransferMutationResult
    data class AlreadyReceived(val id: TransferJobId) : TransferMutationResult
    data class Rejected(val error: TransferError) : TransferMutationResult
}

/** Account-bound persistence facade; every read and mutation must validate the active session. */
interface CollectionTransferRepository {
    suspend fun bindReceipt(id: TransferJobId, owner: TransferOwner, authGeneration: Long, origin: TransferOrigin, destinationChosen: Boolean = false): TransferMutationResult
    fun observeSummary(id: TransferJobId, owner: TransferOwner): Flow<TransferSummary?>
    suspend fun readPage(id: TransferJobId, owner: TransferOwner, cursor: TransferPageCursor?): TransferPage
    suspend fun readReviewPage(id: TransferJobId, owner: TransferOwner, cursor: TransferReviewCursor?, scope: TransferReviewScope = TransferReviewScope.PENDING, direction: TransferPageDirection = TransferPageDirection.FORWARD): TransferReviewPage = throw TransferReadException(TransferError.DESTINATION_UNAVAILABLE)
    suspend fun selectFile(id: TransferJobId, owner: TransferOwner, file: TransferFileId, generation: Long, selected: Boolean): TransferMutationResult
    suspend fun includeRepeatedFile(id: TransferJobId, owner: TransferOwner, file: TransferFileId, generation: Long): TransferMutationResult
    suspend fun editPendingEntry(id: TransferJobId, owner: TransferOwner, generation: Long, entry: TransferReviewEntry): TransferMutationResult
    suspend fun duplicatePendingEntry(id: TransferJobId, owner: TransferOwner, generation: Long, payloadVersion: Long, entryId: String, entryVersion: Long, newEntryId: String): TransferMutationResult = TransferMutationResult.Rejected(TransferError.DESTINATION_UNAVAILABLE)
    suspend fun acknowledgeReview(id: TransferJobId, owner: TransferOwner, confirmation: TransferConfirmation): TransferMutationResult
    suspend fun pause(id: TransferJobId, owner: TransferOwner): TransferMutationResult
    suspend fun resume(id: TransferJobId, owner: TransferOwner): TransferMutationResult
    suspend fun discardPending(id: TransferJobId, owner: TransferOwner, generation: Long, payloadVersion: Long): TransferMutationResult
    suspend fun chooseDestination(id: TransferJobId, owner: TransferOwner, generation: Long, intentRevision: Long, destination: TransferDestination): TransferMutationResult
    suspend fun confirmAction(owner: TransferOwner, request: TransferActionRequest): TransferMutationResult
    suspend fun readAction(owner: TransferOwner, action: TransferActionId): TransferActionSummary
    suspend fun reopenWishlistEntry(id: TransferJobId, owner: TransferOwner, entryId: String, version: Long, payloadVersion: Long): TransferMutationResult
    suspend fun reopenWishlistSelection(id: TransferJobId, owner: TransferOwner, generation: Long, payloadVersion: Long): TransferMutationResult = TransferMutationResult.Rejected(TransferError.DESTINATION_UNAVAILABLE)
    suspend fun readProvenance(id: TransferJobId, owner: TransferOwner, entryId: String): List<TransferEntryProvenance>
    suspend fun readErrorPreview(id: TransferJobId, owner: TransferOwner): TransferErrorPreview
    suspend fun readFileInventory(id: TransferJobId, owner: TransferOwner, afterId: String): TransferFileInventoryPage
    suspend fun readDecisions(id: TransferJobId, owner: TransferOwner, afterId: String): List<TransferPendingReviewDecision>
    suspend fun decideReviewEdit(id: TransferJobId, owner: TransferOwner, entryId: String, generation: Long, choice: TransferReviewDecision): TransferMutationResult
}

/** Source amounts remain original contributions, independently from edited or applied quantities. */
data class TransferEntryProvenance(val file: TransferFileId,val records: Long,val copies: Long,val collectionParticipated: Boolean,val wishlistParticipated: Boolean)

/** Examples are bounded; the complete error count is computed in persistence. */
data class TransferErrorExample(val file: TransferFileId,val ordinal: Long,val error: String,val preview: String)
data class TransferErrorPreview(val total: Long,val examples: List<TransferErrorExample>) {
    init { require(examples.size<=200 && examples.all { it.preview.length<=300 }) }
}
data class TransferFileInventoryPage(val files: List<TransferFileSummary>,val nextAfterId: String?) {
    init { require(files.size<=50) }
}
/** Membership invalidation remains visible until the user resolves its saved intent explicitly. */
data class TransferPendingReviewDecision(val entryId: String,val generation: Long,val reason: String,val entry: TransferReviewEntry)

/** Read failures have bounded categories and never carry a previous account's rows. */
class TransferReadException(val error: TransferError): Exception(error.name)

/** Private source lifetime is platform-owned and closes on return, failure, or cancellation. */
interface CollectionTransferFileStore {
    suspend fun <T> readSource(id: TransferJobId, file: TransferFileId, consume: suspend (TransferCharacterSource) -> T): T
    suspend fun discardSources(id: TransferJobId)
}

/** Slice results distinguish ordinary continuation/waiting from genuine failures. */
enum class TransferSliceResult { CONTINUE, WAITING, FINISHED }

/** Intake origin is app-defined, not provider text or a persistent external URI. */
enum class TransferOrigin { SAF, SHARE, PASTE }

/** Platform work scheduling uses only the job UUID and consults persisted checkpoints. */
interface CollectionTransferCoordinator {
    suspend fun hasImmediateWork(id: TransferJobId): Boolean
    suspend fun runPreparationSlice(id: TransferJobId): TransferSliceResult
    suspend fun runApplicationSlice(id: TransferJobId): TransferSliceResult
    suspend fun reconcile(owner: TransferOwner)
}

/** Confirmation checks are pure; owner/session checks are repeated inside the Room transaction. */
fun TransferConfirmation.matches(summary: TransferSummary): Boolean =
    generation == summary.generation && payloadVersion == summary.payloadVersion &&
        (summary.phase == TransferPhase.REVIEW_READY || summary.phase == TransferPhase.REVIEW_REQUIRED) &&
        summary.files.size in 1..TransferLimits.MAX_FILES &&
        summary.files.map { it.id }.distinct().size == summary.files.size &&
        summary.files.all { !it.selected || it.phase == TransferPhase.REVIEW_READY } &&
        summary.files.filter { it.selected }.let { selected ->
            selected.isNotEmpty() &&
                selected.withinBudget(TransferLimits.MAX_DATA_RECORDS) { it.dataRecords } &&
                selected.withinBudget(TransferLimits.MAX_PREAMBLE_RECORDS) { it.preambleRecords } &&
                (acceptRepeatedFiles || selected.none { it.duplicate || it.previouslyParticipated })
        } &&
        (acceptExclusions || (summary.excludedEntries == 0L && summary.files.all { it.selected && it.invalidRecords == 0L && it.unresolvedRecords == 0L }))

private inline fun List<TransferFileSummary>.withinBudget(limit: Long, count: (TransferFileSummary) -> Long): Boolean {
    var remaining = limit
    for (file in this) {
        val value = count(file)
        if (value !in 0..remaining) return false
        remaining -= value
    }
    return true
}
