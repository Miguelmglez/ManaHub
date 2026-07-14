package com.mmg.manahub.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Android [DispatcherProvider] actual — maps directly to the standard JVM dispatchers.
 */
actual class DispatcherProvider actual constructor() {
    actual val io: CoroutineDispatcher = Dispatchers.IO
    actual val default: CoroutineDispatcher = Dispatchers.Default
    actual val main: CoroutineDispatcher = Dispatchers.Main
}
