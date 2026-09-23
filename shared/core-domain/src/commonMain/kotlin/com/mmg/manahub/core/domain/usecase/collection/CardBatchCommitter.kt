package com.mmg.manahub.core.domain.usecase.collection

/** Writes a batch of queued cards to the collection; the strategy behind `CardQueueActions`. */
fun interface CardBatchCommitter {

    /** @return per-entry outcome, same order and size as [entries]. */
    suspend fun commit(entries: List<CardCommit>): CommitScanResult
}
