package com.mmg.manahub.core.data.remote.trades

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreatedAtKeysetTest {

    private data class Row(val id: String, val createdAt: String)

    private fun rows(from: Int, count: Int) =
        (from until from + count).map { Row("id-$it", "2026-09-23T10:00:00.${it.toString().padStart(6, '0')}+00:00") }

    @Test
    fun drainsEveryPageAndPassesTheRawCreatedAtCursor() = runTest {
        val all = rows(0, 5)
        val cursors = mutableListOf<CreatedAtCursor?>()

        val drain = drainByCreatedAt(Row::id, Row::createdAt, pageSize = 2) { after, limit ->
            cursors += after
            val start = after?.let { cursor -> all.indexOfFirst { it.id == cursor.id } + 1 } ?: 0
            Result.success(all.drop(start).take(limit))
        }

        assertTrue(drain.isComplete)
        assertEquals(all, drain.rows)
        assertNull(cursors.first())
        assertEquals(CreatedAtCursor(all[1].createdAt, all[1].id), cursors[1])
        assertEquals(CreatedAtCursor(all[3].createdAt, all[3].id), cursors[2])
    }

    @Test
    fun aFailedLaterPageKeepsEarlierRowsButIsNeverComplete() = runTest {
        val error = RuntimeException("page 2")
        var calls = 0

        val drain = drainByCreatedAt(Row::id, Row::createdAt, pageSize = 2) { _, _ ->
            if (calls++ == 0) Result.success(rows(0, 2)) else Result.failure(error)
        }

        assertFalse(drain.isComplete)
        assertEquals(2, drain.rows.size)
        assertEquals(error, drain.incompleteFailure())
        assertTrue(drain.toResult().isFailure)
    }

    @Test
    fun hittingThePageCapIsIncompleteEvenWithoutAnError() = runTest {
        var next = 0
        val drain = drainByCreatedAt(Row::id, Row::createdAt, pageSize = 2, maxPages = 3) { _, _ ->
            Result.success(rows(next, 2).also { next += 2 })
        }

        assertFalse(drain.isComplete)
        assertEquals(6, drain.rows.size)
        assertIs<IncompleteDrainException>(drain.incompleteFailure())
    }

    @Test
    fun aShortFirstPageIsComplete() = runTest {
        val drain = drainByCreatedAt(Row::id, Row::createdAt, pageSize = 5) { _, _ -> Result.success(rows(0, 3)) }

        assertTrue(drain.isComplete)
        assertEquals(Result.success(rows(0, 3)), drain.toResult())
    }
}
