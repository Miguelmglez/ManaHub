package com.mmg.manahub.core.data.sync

import kotlin.coroutines.cancellation.CancellationException

/**
 * Position of the last APPLIED row in a server change feed ordered by `(position, key)`.
 *
 * For a keyset table [position] is the server-assigned `changed_at` and [key] the primary key; for
 * an append-only feed (the XP ledger's `server_seq`) [position] alone is the cursor and [key] is
 * informational.
 */
data class SyncCursor(val position: Long, val key: String)

/**
 * Outcome of [drainFromCursor].
 *
 * @property fullyDrained True only when a short (or empty) page proved the feed exhausted and every
 *   page before it applied.
 * @property pagesDrained Pages fetched successfully (diagnostic only).
 * @property cursor The cursor of the last applied row, or the start cursor when nothing applied.
 * @property failure The page-fetch error that stopped the drain, when that is what stopped it.
 */
data class CursorDrainResult(
    val fullyDrained: Boolean,
    val pagesDrained: Int,
    val cursor: SyncCursor?,
    val failure: Throwable?,
)

private class CursorRow<T>(val row: T, val cursor: SyncCursor) : KeysetPageRow {
    override val id: String get() = cursor.key
    override val updatedAt: Long get() = cursor.position
}

/**
 * Drains a server change feed from a persisted cursor, persisting the cursor after each page is
 * applied (ADR-008: a cursor never moves past a row that was fetched but not applied).
 *
 * Built on [drainPages], so the page size is clamped to [SERVER_PAGE_CAP] and a short page ends the
 * loop. The first request resumes from [start]; later requests use the last row of the previous
 * page. A page that fails to apply leaves the cursor on the previous page's tail, so the next run
 * re-fetches it; re-applying is safe because every consumer merges monotonically.
 *
 * @param start The persisted cursor, or null for a full pull from the beginning of the feed.
 * @param cursorOf Extracts a row's `(position, key)` exactly as the server orders it.
 * @param fetchPage The RPC call: rows strictly after `after` (null = from the start), at most `limit`.
 * @param applyPage Applies one page locally; returns false when any row failed to apply.
 * @param saveCursor Persists the cursor of a fully applied page before the next page is fetched.
 */
suspend fun <T> drainFromCursor(
    start: SyncCursor?,
    cursorOf: (T) -> SyncCursor,
    fetchPage: suspend (after: SyncCursor?, limit: Int) -> Result<List<T>>,
    applyPage: suspend (List<T>) -> Boolean,
    saveCursor: suspend (SyncCursor) -> Unit,
    limit: Int = SERVER_PAGE_CAP,
    maxPages: Int = 200,
): CursorDrainResult {
    var failure: Throwable? = null
    var cursor = start
    val result = drainPages<CursorRow<T>>(
        since = start?.position ?: 0L,
        limit = limit,
        maxPages = maxPages,
        fetchPage = { afterPosition, afterKey, pageLimit ->
            val after = if (afterPosition != null && afterKey != null) {
                SyncCursor(afterPosition, afterKey)
            } else {
                start
            }
            fetchPage(after, pageLimit)
                .onFailure { error ->
                    if (error is CancellationException) throw error
                    failure = error
                }
                .map { rows -> rows.map { CursorRow(it, cursorOf(it)) } }
        },
        onPage = { page ->
            val applied = applyPage(page.map { it.row })
            if (applied) {
                val last = page.last().cursor
                saveCursor(last)
                cursor = last
            }
            applied
        },
    )
    return CursorDrainResult(
        fullyDrained = result.minUnappliedUpdatedAt == Long.MAX_VALUE,
        pagesDrained = result.pagesDrained,
        cursor = cursor,
        failure = failure,
    )
}

/**
 * Pushes [rows] in slices of at most [sliceSize] (clamped to [SERVER_PAGE_CAP]), calling
 * [onSliceConfirmed] after each slice the server accepted so a push watermark can advance per slice.
 *
 * Rows are de-duplicated by [keyOf] first (keeping the first occurrence and the input order): a
 * server `ON CONFLICT DO UPDATE` cannot touch the same primary key twice in one statement.
 *
 * @return the number of confirmed slices, or the first push failure (later slices are not sent).
 */
suspend fun <T> pushInSlices(
    rows: List<T>,
    keyOf: (T) -> Any,
    push: suspend (List<T>) -> Result<Unit>,
    onSliceConfirmed: suspend (List<T>) -> Unit = {},
    sliceSize: Int = SERVER_PAGE_CAP,
): Result<Int> {
    val effectiveSize = sliceSize.coerceIn(1, SERVER_PAGE_CAP)
    var confirmed = 0
    for (slice in rows.distinctBy(keyOf).chunked(effectiveSize)) {
        val outcome = push(slice)
        outcome.exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
            return Result.failure(error)
        }
        onSliceConfirmed(slice)
        confirmed++
    }
    return Result.success(confirmed)
}

/**
 * Advances an id-based push watermark over local rows that were inserted by a pull and are therefore
 * already on the server, stopping at the first id that was not pulled (a local write that still needs
 * pushing). Never moves backwards.
 *
 * @param watermark Highest local id already confirmed pushed.
 * @param idsAbove Local ids above [watermark] (any order).
 * @param pulledIds Local ids of rows this cycle inserted from the server.
 */
fun advancePastPulledRows(watermark: Long, idsAbove: List<Long>, pulledIds: Set<Long>): Long {
    var advanced = watermark
    for (id in idsAbove.sorted()) {
        if (id <= advanced) continue
        if (id !in pulledIds) break
        advanced = id
    }
    return advanced
}
