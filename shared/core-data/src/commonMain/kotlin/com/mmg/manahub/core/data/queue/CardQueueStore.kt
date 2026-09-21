package com.mmg.manahub.core.data.queue

/**
 * Synchronous raw-string storage for the persisted card queue payload. Synchronous on purpose:
 * the queue must be restored before its first read, and writes are small and fire-and-forget
 * (Android SharedPreferences `apply()`, browser `localStorage`).
 */
interface CardQueueStore {

    /** Returns the persisted payload, or null when nothing was ever saved. */
    fun read(): String?

    /** Persists [payload], replacing any previous one. */
    fun write(payload: String)
}

/** Non-persistent [CardQueueStore] for tests and targets without storage. */
class InMemoryCardQueueStore(initialPayload: String? = null) : CardQueueStore {

    /** Last payload written (or the initial one). */
    var payload: String? = initialPayload
        private set

    override fun read(): String? = payload

    override fun write(payload: String) {
        this.payload = payload
    }
}
