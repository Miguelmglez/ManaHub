package com.mmg.manahub.core.domain.usecase.collection

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CollectionAddRequest
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock

/**
 * [CardBatchCommitter] for imported lists: writes collection rows WITHOUT any XP (an import is not a
 * scan nor a manual add). A commit that wrote at least one copy emits the zero-XP
 * [ProgressionEvent.CollectionChanged] so DERIVED collection achievements re-evaluate (G-25).
 *
 * Every [BATCH_SIZE] slice is ONE [UserCardRepository.addOrIncrementBatch] transaction, so a large
 * "add all" invalidates collection observers once per slice. A failed slice fails only its own
 * entries. Rows need no cached card (ADR-008): the repository writes ownership unconditionally.
 */
class CommitImportedCardsUseCase(
    private val userCardRepository: UserCardRepository,
    private val crashReporter: CrashReporter? = null,
    private val progressionEventBus: ProgressionEventBus? = null,
) : CardBatchCommitter {

    override suspend fun commit(entries: List<CardCommit>): CommitScanResult {
        val succeeded = ArrayList<Boolean>(entries.size)
        var committedCopies = 0
        entries.chunked(BATCH_SIZE).forEach { slice ->
            val ok = try {
                userCardRepository.addOrIncrementBatch(slice.map { it.toRequest() }, userId = null)
                true
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                crashReporter?.log("collection_import_commit_slice_failed")
                crashReporter?.recordException(RuntimeException("[collection_import_commit] ${t::class.simpleName}"))
                false
            }
            repeat(slice.size) { succeeded += ok }
            if (ok) committedCopies += slice.sumOf { it.quantity }
        }
        if (committedCopies > 0) {
            progressionEventBus?.emit(ProgressionEvent.CollectionChanged(occurredAt = Clock.System.now()))
        }
        return CommitScanResult(
            committedCopies = committedCopies,
            failedEntries = succeeded.count { !it },
            entrySucceeded = succeeded,
        )
    }

    private fun CardCommit.toRequest() = CollectionAddRequest(
        scryfallId = scryfallId,
        isFoil = isFoil,
        condition = condition,
        language = language,
        quantity = quantity,
    )

    companion object {
        const val BATCH_SIZE = 500
    }
}
