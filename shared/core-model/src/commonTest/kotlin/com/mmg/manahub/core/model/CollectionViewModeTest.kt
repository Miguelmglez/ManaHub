package com.mmg.manahub.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Multiplatform test (commonTest) for the moved [CollectionViewMode] — runs on both the Android host
 * test (`testDebugUnitTest`) and the wasmJs test task, proving the shared module's test source set
 * compiles and executes on every target.
 */
class CollectionViewModeTest {

    @Test
    fun fromName_resolvesKnownValue() {
        assertEquals(CollectionViewMode.LIST, CollectionViewMode.fromName("LIST"))
        assertEquals(CollectionViewMode.GRID, CollectionViewMode.fromName("GRID"))
    }

    @Test
    fun fromName_defaultsToGridForNullOrUnknown() {
        assertEquals(CollectionViewMode.GRID, CollectionViewMode.fromName(null))
        assertEquals(CollectionViewMode.GRID, CollectionViewMode.fromName("nonsense"))
    }
}
