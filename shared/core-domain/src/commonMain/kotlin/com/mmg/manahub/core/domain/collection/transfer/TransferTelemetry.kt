package com.mmg.manahub.core.domain.collection.transfer

/**
 * Coarse count bucket for import/export telemetry, NEVER the exact size of a user's list.
 *
 * Lives next to the transfer use cases so every reporter — the shared use case and the Android
 * ViewModels alike — buckets through the same function instead of reaching for the raw count.
 */
fun transferCountBucket(count: Int): String = transferCountBucket(count.toLong())

/** Long quantities share the same coarse buckets without presentation narrowing. */
fun transferCountBucket(count: Long): String = when {
    count <= 0 -> "0"
    count <= 10 -> "1-10"
    count <= 100 -> "11-100"
    count <= 1_000 -> "101-1000"
    else -> "1000+"
}

/** Only these categories may cross a transfer telemetry boundary. */
enum class TransferFailureCategory(val value: String) {
    NETWORK("network"), STORAGE("storage"), INVALID_PAYLOAD("invalid_payload"), UNKNOWN("unknown")
}

/** Cancellation and obsolete owner reads are normal lifecycle outcomes. */
fun isExpectedTransferInterruption(error: Throwable): Boolean = error is kotlinx.coroutines.CancellationException ||
    error is TransferSessionChangedException || error is TransferReadException && error.error==TransferError.OWNER_CHANGED

/** Classify locally without serializing external text, class names, causes or SQL. */
fun transferFailureCategory(error: Throwable,fallback: TransferFailureCategory=TransferFailureCategory.UNKNOWN): TransferFailureCategory =
    if(error is IllegalArgumentException || error is ArithmeticException || error is TransferQuantityOverflowException)TransferFailureCategory.INVALID_PAYLOAD else fallback

/** Export operations share four bounded keys and never imply external chooser delivery. */
fun exportTelemetry(reporter: com.mmg.manahub.core.common.CrashReporter,event: String,format: String?,rows: Long,category: TransferFailureCategory=TransferFailureCategory.UNKNOWN,failure: Boolean=false) {
    reporter.setCustomKey("collection_export_phase",event)
    reporter.setCustomKey("collection_export_format",format?.let { candidate -> CollectionFileFormat.entries.firstOrNull { it.name==candidate }?.name } ?: "unknown")
    reporter.setCustomKey("collection_export_rows_bucket",transferCountBucket(rows))
    reporter.setCustomKey("collection_export_failure_category",category.value)
    reporter.log("collection_export_$event")
    if(failure)reporter.recordException(IllegalStateException("collection_export_operation_failed"))
}
