package com.mmg.manahub.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * wasmJs [DispatcherProvider] actual.
 *
 * Kotlin/Wasm runs on the browser's single-threaded event loop, so there is no real blocking-IO
 * dispatcher: [io] falls back to [Dispatchers.Default]. [default] and [main] use their wasm
 * equivalents.
 */
actual class DispatcherProvider actual constructor() {
    actual val io: CoroutineDispatcher = Dispatchers.Default
    actual val default: CoroutineDispatcher = Dispatchers.Default
    actual val main: CoroutineDispatcher = Dispatchers.Main
}
