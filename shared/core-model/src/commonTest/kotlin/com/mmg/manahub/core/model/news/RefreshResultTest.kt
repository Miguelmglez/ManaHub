package com.mmg.manahub.core.model.news

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Unit tests for [RefreshResult] — News feature improvements Phase 3 (failure visibility).
 * `attempted` must always equal the sum of the three buckets, so the presentation layer can
 * distinguish "nothing happened" from "everything failed" from "everything succeeded".
 */
class RefreshResultTest {

    @Test
    fun given_allZero_then_attemptedIsZero() {
        val result = RefreshResult(fetched = 0, failed = 0, notModified = 0)

        assertEquals(0, result.attempted)
    }

    @Test
    fun given_everySourceFailed_then_attemptedEqualsFailed() {
        val result = RefreshResult(fetched = 0, failed = 5, notModified = 0)

        assertEquals(5, result.attempted)
        assertEquals(result.failed, result.attempted)
    }

    @Test
    fun given_everySourceFetched_then_attemptedEqualsFetched() {
        val result = RefreshResult(fetched = 4, failed = 0, notModified = 0)

        assertEquals(4, result.attempted)
    }

    @Test
    fun given_everySourceNotModified_then_attemptedEqualsNotModified() {
        val result = RefreshResult(fetched = 0, failed = 0, notModified = 7)

        assertEquals(7, result.attempted)
    }

    @Test
    fun given_aMixOfOutcomes_then_attemptedSumsAllThreeBuckets() {
        val result = RefreshResult(fetched = 2, failed = 3, notModified = 1)

        assertEquals(6, result.attempted)
    }
}
