package com.mmg.manahub.core.common

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platform abstraction over the coroutine dispatchers the app uses.
 *
 * Replaces direct references to `Dispatchers.IO` / `Dispatchers.Default` / `Dispatchers.Main` in
 * shared code, because the wasmJs runtime has no real IO dispatcher (Kotlin/Wasm has a single-threaded
 * event loop). Each platform supplies an `actual`:
 *  - Android: [io] = `Dispatchers.IO`, [default] = `Dispatchers.Default`, [main] = `Dispatchers.Main`.
 *  - wasmJs:  [io] falls back to `Dispatchers.Default` (no dedicated IO pool on the web), with
 *             [default] and [main] mapping to their browser-event-loop equivalents.
 *
 * @property io dispatcher for blocking IO work (Room/network on Android; Default on web).
 * @property default dispatcher for CPU-bound work.
 * @property main the UI dispatcher.
 */
expect class DispatcherProvider() {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}
