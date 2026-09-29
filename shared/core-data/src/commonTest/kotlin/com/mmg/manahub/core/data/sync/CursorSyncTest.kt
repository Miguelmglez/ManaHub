package com.mmg.manahub.core.data.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Coverage for [drainFromCursor], [pushInSlices] and [advancePastPulledRows] (G-01, ADR-008). */
class CursorSyncTest {

    private data class Row(val seq: Long, val key: String)

    /** Serves rows strictly after the cursor, ordered by (seq, key), capped at the server's 500. */
    private fun serve(rows: List<Row>, after: SyncCursor?, limit: Int): List<Row> = rows
        .sortedWith(compareBy<Row>({ it.seq }, { it.key }))
        .filter { after == null || it.seq > after.position || (it.seq == after.position && it.key > after.key) }
        .take(minOf(limit, SERVER_PAGE_CAP))

    @Test
    fun drainsMoreThanOnePageAndSavesTheCursorAfterEachAppliedPage() = runTest {
        val rows = (1L..1_001L).map { Row(it, "k$it") }
        val requests = mutableListOf<SyncCursor?>()
        val saved = mutableListOf<SyncCursor>()
        val applied = mutableListOf<Row>()

        val result = drainFromCursor<Row>(
            start = null,
            cursorOf = { SyncCursor(it.seq, it.key) },
            fetchPage = { after, limit -> requests += after; Result.success(serve(rows, after, limit)) },
            applyPage = { applied += it; true },
            saveCursor = { saved += it },
        )

        assertTrue(result.fullyDrained)
        assertEquals(3, result.pagesDrained)
        assertEquals(listOf(null, SyncCursor(500, "k500"), SyncCursor(1_000, "k1000")), requests)
        assertEquals(listOf(SyncCursor(500, "k500"), SyncCursor(1_000, "k1000"), SyncCursor(1_001, "k1001")), saved)
        assertEquals(1_001, applied.size)
        assertEquals(SyncCursor(1_001, "k1001"), result.cursor)
    }

    @Test
    fun resumesFromTheStartCursor() = runTest {
        val rows = listOf(Row(5, "a"), Row(5, "b"), Row(6, "a"))
        val requests = mutableListOf<SyncCursor?>()

        val result = drainFromCursor<Row>(
            start = SyncCursor(5, "a"),
            cursorOf = { SyncCursor(it.seq, it.key) },
            fetchPage = { after, limit -> requests += after; Result.success(serve(rows, after, limit)) },
            applyPage = { true },
            saveCursor = {},
        )

        assertEquals(listOf<SyncCursor?>(SyncCursor(5, "a")), requests)
        assertEquals(SyncCursor(6, "a"), result.cursor)
    }

    @Test
    fun fetchFailureKeepsTheLastAppliedCursorAndReportsTheError() = runTest {
        val rows = (1L..700L).map { Row(it, "k$it") }
        val boom = IllegalStateException("network")
        val saved = mutableListOf<SyncCursor>()

        val result = drainFromCursor<Row>(
            start = null,
            cursorOf = { SyncCursor(it.seq, it.key) },
            fetchPage = { after, limit ->
                if (after != null) Result.failure(boom) else Result.success(serve(rows, after, limit))
            },
            applyPage = { true },
            saveCursor = { saved += it },
        )

        assertFalse(result.fullyDrained)
        assertSame(boom, result.failure)
        assertEquals(listOf(SyncCursor(500, "k500")), saved)
        assertEquals(SyncCursor(500, "k500"), result.cursor)
    }

    @Test
    fun aPageThatDoesNotApplyNeverMovesTheCursor() = runTest {
        val rows = (1L..3L).map { Row(it, "k$it") }
        val saved = mutableListOf<SyncCursor>()

        val result = drainFromCursor<Row>(
            start = SyncCursor(0, ""),
            cursorOf = { SyncCursor(it.seq, it.key) },
            fetchPage = { after, limit -> Result.success(serve(rows, after, limit)) },
            applyPage = { false },
            saveCursor = { saved += it },
        )

        assertFalse(result.fullyDrained)
        assertNull(result.failure)
        assertTrue(saved.isEmpty())
        assertEquals(SyncCursor(0, ""), result.cursor)
    }

    @Test
    fun pushInSlicesChunksAt500AndStopsAtTheFirstFailure() = runTest {
        val rows = (1..1_201).toList()
        val sent = mutableListOf<List<Int>>()
        val confirmed = mutableListOf<List<Int>>()

        val result = pushInSlices(
            rows = rows,
            keyOf = { it },
            push = { slice ->
                sent += slice
                if (sent.size == 2) Result.failure(IllegalStateException("x")) else Result.success(Unit)
            },
            onSliceConfirmed = { confirmed += it },
            sliceSize = 10_000,
        )

        assertTrue(result.isFailure)
        assertEquals(listOf(500, 500), sent.map { it.size })
        assertEquals(listOf(500), confirmed.map { it.size })
    }

    @Test
    fun pushInSlicesNeverRepeatsAKeyAndSkipsEmptyInput() = runTest {
        val sent = mutableListOf<List<String>>()

        val result = pushInSlices(
            rows = listOf("a", "b", "a", "c"),
            keyOf = { it },
            push = { sent += it; Result.success(Unit) },
            sliceSize = 2,
        )
        val empty = pushInSlices(rows = emptyList<String>(), keyOf = { it }, push = { sent += it; Result.success(Unit) })

        assertEquals(Result.success(2), result)
        assertEquals(listOf(listOf("a", "b"), listOf("c")), sent)
        assertEquals(Result.success(0), empty)
    }

    @Test
    fun advancePastPulledRowsStopsAtTheFirstLocalWrite() {
        assertEquals(12L, advancePastPulledRows(10L, listOf(13L, 11L, 12L, 14L), setOf(11L, 12L, 14L)))
        assertEquals(10L, advancePastPulledRows(10L, listOf(11L), emptySet()))
        assertEquals(10L, advancePastPulledRows(10L, emptyList(), setOf(11L)))
        assertEquals(10L, advancePastPulledRows(10L, listOf(5L, 9L), setOf(5L, 9L)))
    }
}
