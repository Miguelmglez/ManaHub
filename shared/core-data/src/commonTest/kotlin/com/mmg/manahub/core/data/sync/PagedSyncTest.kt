package com.mmg.manahub.core.data.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression coverage for [drainPages] — specifically the invariant that "fully drained" (
 * [PageDrainResult.minUnappliedUpdatedAt] == [Long.MAX_VALUE]) may only be reported from evidence
 * that cannot be produced by the server's own `LEAST(p_limit, 500)` page cap.
 *
 * The primary target here is [requestingMoreThanServerCapDoesNotFalselyReportFullyDrained]: before
 * the [SERVER_PAGE_CAP] clamp, a caller requesting `limit = 1000` while the server always returns
 * exactly 500 rows (its own cap) would see `page.size(500) < limit(1000)` on the very first page
 * and wrongly conclude "nothing unapplied" — silently re-creating the collection-sync data-loss bug
 * this whole file exists to prevent, from a one-line `PAGE_SIZE` edit in a different module.
 */
class PagedSyncTest {

    private data class FakeRow(override val id: String, override val updatedAt: Long) : KeysetPageRow

    /** Builds a fixed-size page of [count] rows, each with a distinct id and increasing updatedAt. */
    private fun page(startUpdatedAt: Long, count: Int): List<FakeRow> =
        (0 until count).map { i -> FakeRow(id = "row-${startUpdatedAt + i}", updatedAt = startUpdatedAt + i) }

    @Test
    fun requestingMoreThanServerCapDoesNotFalselyReportFullyDrained() = runTest {
        // Server behaves exactly like the real RPCs: LIMIT least(p_limit, 500) == always 500 here,
        // regardless of what the caller asks for -- and there ARE more rows after this one page.
        var callCount = 0
        val result = drainPages<FakeRow>(
            since = 0L,
            limit = 1000, // caller asks for more than the server will ever return
            fetchPage = { afterUpdatedAt, _, limit ->
                callCount++
                assertEquals(SERVER_PAGE_CAP, limit, "drainPages must clamp the requested limit to SERVER_PAGE_CAP before calling fetchPage")
                val start = (afterUpdatedAt ?: 0L)
                Result.success(page(start, SERVER_PAGE_CAP))
            },
            onPage = { true },
        )

        // MUST NOT report Long.MAX_VALUE ("nothing unapplied") after a single capped page -- that
        // would let the caller's watermark jump past every row the server never got a chance to
        // return this cycle.
        assertTrue(
            result.minUnappliedUpdatedAt < Long.MAX_VALUE,
            "drainPages must not report fully-drained after only a server-capped page when more rows may remain",
        )
        assertTrue(callCount > 1, "drainPages must keep paging past the first server-capped page")
    }

    @Test
    fun genuineShortPageAtOrBelowServerCapReportsFullyDrained() = runTest {
        // Caller requests a sane page size (<= SERVER_PAGE_CAP); the server returns a page smaller
        // than requested because it genuinely ran out of rows. This IS valid "done" evidence.
        val result = drainPages<FakeRow>(
            since = 0L,
            limit = 500,
            fetchPage = { afterUpdatedAt, _, limit ->
                assertEquals(500, limit)
                val start = afterUpdatedAt ?: 0L
                Result.success(page(start, 3)) // short page: genuinely fewer rows than the cap
            },
            onPage = { true },
        )

        assertEquals(Long.MAX_VALUE, result.minUnappliedUpdatedAt)
        assertEquals(1, result.pagesDrained)
    }

    @Test
    fun multipleFullPagesAtServerCapThenShortPageDrainsCompletely() = runTest {
        var call = 0
        val result = drainPages<FakeRow>(
            since = 0L,
            limit = SERVER_PAGE_CAP,
            fetchPage = { afterUpdatedAt, _, limit ->
                call++
                val start = afterUpdatedAt ?: 0L
                val rows = if (call < 3) page(start, limit) else page(start, 10)
                Result.success(rows)
            },
            onPage = { true },
        )

        assertEquals(Long.MAX_VALUE, result.minUnappliedUpdatedAt)
        assertEquals(3, result.pagesDrained)
    }

    @Test
    fun emptyFirstPageReportsFullyDrainedImmediately() = runTest {
        val result = drainPages<FakeRow>(
            since = 42L,
            limit = SERVER_PAGE_CAP,
            fetchPage = { _, _, _ -> Result.success(emptyList()) },
            onPage = { true },
        )

        assertEquals(Long.MAX_VALUE, result.minUnappliedUpdatedAt)
        assertEquals(1, result.pagesDrained)
    }

    @Test
    fun failedPageApplyStopsAtLastConsumedWatermark() = runTest {
        val result = drainPages<FakeRow>(
            since = 0L,
            limit = SERVER_PAGE_CAP,
            fetchPage = { afterUpdatedAt, _, limit ->
                val start = afterUpdatedAt ?: 0L
                Result.success(page(start, limit))
            },
            onPage = { false }, // every page fails to apply
        )

        assertEquals(0L, result.minUnappliedUpdatedAt)
        assertEquals(1, result.pagesDrained)
    }

    @Test
    fun fetchFailureStopsAtLastConsumedWatermark() = runTest {
        var call = 0
        val result = drainPages<FakeRow>(
            since = 10L,
            limit = SERVER_PAGE_CAP,
            fetchPage = { afterUpdatedAt, _, limit ->
                call++
                if (call == 1) {
                    // Full page -- server cap, so the loop keeps going into a 2nd fetch.
                    val start = afterUpdatedAt ?: 0L
                    Result.success(page(start, limit))
                } else {
                    Result.failure(RuntimeException("network error"))
                }
            },
            onPage = { true },
        )

        // The 2nd fetch failed, so the reported ceiling must be the last row actually consumed
        // (the tail of page 1: updatedAt = SERVER_PAGE_CAP - 1), never Long.MAX_VALUE.
        assertEquals((SERVER_PAGE_CAP - 1).toLong(), result.minUnappliedUpdatedAt)
        assertEquals(1, result.pagesDrained)
    }
}
