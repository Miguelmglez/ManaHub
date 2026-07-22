package com.mmg.manahub.core.common

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Plain-JVM [DispatcherProvider] actual (Deck Engine Unification plan, RUN 5 / D5 — added so
 * :tools:tag-pipeline can depend on this module's commonMain).
 *
 * Unlike the Android actual, [main] is deliberately mapped to [Dispatchers.Default] rather than
 * [Dispatchers.Main]: `Dispatchers.Main` requires a UI-framework Main-dispatcher artifact
 * (Android/JavaFX/Swing) on the classpath, and a plain CLI tool has none — referencing it would
 * compile but throw `IllegalStateException` at runtime the first time it's touched. This mirrors
 * the wasmJs actual's own "no real Main dispatcher available" fallback.
 */
actual class DispatcherProvider actual constructor() {
    actual val io: CoroutineDispatcher = Dispatchers.IO
    actual val default: CoroutineDispatcher = Dispatchers.Default
    actual val main: CoroutineDispatcher = Dispatchers.Default
}
