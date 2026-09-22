package com.mmg.manahub.core.domain.collection.transfer

/**
 * Coarse count bucket for import/export telemetry, NEVER the exact size of a user's list.
 *
 * Lives next to the transfer use cases so every reporter — the shared use case and the Android
 * ViewModels alike — buckets through the same function instead of reaching for the raw count.
 */
fun transferCountBucket(count: Int): String = when {
    count <= 0 -> "0"
    count <= 10 -> "1-10"
    count <= 100 -> "11-100"
    count <= 1_000 -> "101-1000"
    else -> "1000+"
}
