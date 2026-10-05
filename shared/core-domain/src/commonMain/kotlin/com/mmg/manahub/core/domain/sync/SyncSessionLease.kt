package com.mmg.manahub.core.domain.sync

/** One captured auth generation guards network responses and transactional ownership writes. */
class SyncSessionLease(
    val ensureCurrent: () -> Unit,
    private val writeLocal: suspend (suspend () -> Unit) -> Unit,
) {
    suspend fun <T> remote(operation: suspend () -> T): T {
        ensureCurrent()
        val result=operation()
        ensureCurrent()
        return result
    }
    suspend fun local(operation: suspend () -> Unit) {
        ensureCurrent()
        writeLocal { ensureCurrent(); operation(); ensureCurrent() }
    }
}
