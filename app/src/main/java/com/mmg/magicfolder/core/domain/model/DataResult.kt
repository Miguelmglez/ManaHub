package com.mmg.magicfolder.core.domain.model

/**
 * Tri-state result wrapper.
 * Success can carry isStale = true when data is served from a stale cache
 * and the network refresh failed — the UI shows a warning badge without
 * treating it as a hard error.
 */
sealed class DataResult<out T> {
    data class Success<out T>(val data: T, val isStale: Boolean = false) : DataResult<T>()
    data class Error(val message: String) : DataResult<Nothing>()
}
