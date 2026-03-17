package com.mmg.magicfolder.code.core.domain.model

/**
 * Tri-state result wrapper.
 * Success can carry isStale = true when data is served from a stale cache
 * and the network refresh failed — the UI shows a warning badge without
 * treating it as a hard error.
 */
sealed class DataResult {
    data class Success(val data: T, val isStale: Boolean = false) : DataResult()
    data class Error(val message: String) : DataResult()
}