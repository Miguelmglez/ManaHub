package com.mmg.manahub.core.domain.usecase.collection

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.repository.CollectionAddRequest
import com.mmg.manahub.core.domain.repository.UserCardRepository
import kotlinx.coroutines.CancellationException

/**
 * [CardBatchCommitter] for imported lists: writes collection rows WITHOUT any progression event
 * (an import is not a scan nor a manual add, so it grants no XP).
 *
 * Every [BATCH_SIZE] slice is ONE [UserCardRepository.addOrIncrementBatch] transaction, so a large
 * "add all" invalidates collection observers once per slice. A failed slice fails only its own
 * entries. Rows need no cached card (ADR-008): the repository writes ownership unconditionally.
 */
class CommitImportedCardsUseCase(
    private val userCardRepository: UserCardRepository,
    private val crashReporter: CrashReporter? = null,
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
                crashReporter?.recordException(RuntimeException("[collection_import_commit] ${t::class.simpleName}", t))
                false
            }
            repeat(slice.size) { succeeded += ok }
            if (ok) committedCopies += slice.sumOf { it.quantity }
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
