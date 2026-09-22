package com.mmg.manahub.feature.collection.presentation.importexport

/** Coarse count bucket for import/export telemetry (never the exact size of a user's list). */
fun transferCountBucket(count: Int): String = when {
    count <= 0 -> "0"
    count <= 10 -> "1-10"
    count <= 100 -> "11-100"
    count <= 1_000 -> "101-1000"
    else -> "1000+"
}
