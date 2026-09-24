package com.mmg.manahub.core.data.remote.trades

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.sync.KeysetPageRow
import com.mmg.manahub.core.data.sync.SERVER_PAGE_CAP
import com.mmg.manahub.core.data.sync.drainPages
import io.github.jan.supabase.postgrest.query.PostgrestRequestBuilder
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/** Keyset position right after the last row of a page ordered by `(created_at, id)`. */
data class CreatedAtCursor(val createdAt: String, val id: String)

/**
 * Every row gathered by [drainByCreatedAt].
 *
 * @property isComplete true only when every page was fetched. Local rows missing from [rows] may be
 *   evicted only then: a partial drain says nothing about the rows it never reached.
 * @property failure why the drain stopped early; null when [isComplete].
 */
data class KeysetDrain<T>(
    val rows: List<T>,
    val isComplete: Boolean,
    val failure: Throwable? = null,
) {
    /** The failure to report for an incomplete drain (never null when [isComplete] is false). */
    fun incompleteFailure(): Throwable? =
        if (isComplete) null else failure ?: IncompleteDrainException()

    /** Every row when the drain completed; otherwise a failure, so callers never mistake a partial list for the whole one. */
    fun toResult(): Result<List<T>> = incompleteFailure()?.let { Result.failure(it) } ?: Result.success(rows)

    companion object {
        /** A drain that fetched every page and found exactly [rows]. */
        fun <T> complete(rows: List<T>): KeysetDrain<T> = KeysetDrain(rows, isComplete = true)
    }
}

/** A keyset drain stopped at its page cap before the server ran out of rows. */
class IncompleteDrainException : IllegalStateException("keyset_drain_page_cap")

/** Hard cap on pages per drain: 40 x 500 rows is far beyond any real trade list. */
const val TRADES_DRAIN_MAX_PAGES = 40

private class DrainRow<T>(val value: T, override val id: String) : KeysetPageRow {
    // The (created_at, id) cursor is carried separately; this drain only needs the completeness verdict.
    override val updatedAt: Long = 0L
}

/**
 * Drains a `(created_at, id)` keyset-paginated table query through [drainPages], so the page-size
 * clamp and the short-page-means-done rule are the same ones every other paged sync relies on.
 * The cursor keeps the server's raw `created_at` string: rounding it to milliseconds would re-fetch
 * or skip rows that share a millisecond.
 */
suspend fun <T> drainByCreatedAt(
    id: (T) -> String,
    createdAt: (T) -> String,
    pageSize: Int = SERVER_PAGE_CAP,
    maxPages: Int = TRADES_DRAIN_MAX_PAGES,
    fetchPage: suspend (after: CreatedAtCursor?, limit: Int) -> Result<List<T>>,
): KeysetDrain<T> {
    val rows = LinkedHashMap<String, T>()
    var failure: Throwable? = null
    var lastCreatedAt: String? = null
    val result = drainPages<DrainRow<T>>(
        since = 0L,
        limit = pageSize,
        maxPages = maxPages,
        fetchPage = { _, afterId, limit ->
            val cursor = lastCreatedAt?.let { created -> afterId?.let { CreatedAtCursor(created, it) } }
            fetchPage(cursor, limit)
                .onFailure { if (it !is CancellationException) failure = it }
                .map { page -> page.map { DrainRow(it, id(it)) } }
        },
        onPage = { page ->
            page.forEach { rows[it.id] = it.value }
            lastCreatedAt = createdAt(page.last().value)
            true
        },
    )
    val complete = result.minUnappliedUpdatedAt == Long.MAX_VALUE
    return KeysetDrain(rows.values.toList(), complete, if (complete) null else failure)
}

/** Restricts a query to rows strictly after [cursor] in `(created_at, id)` order. */
internal fun PostgrestFilterBuilder.afterCreatedAt(cursor: CreatedAtCursor) {
    or {
        gt("created_at", cursor.createdAt)
        and {
            eq("created_at", cursor.createdAt)
            gt("id", cursor.id)
        }
    }
}

/** Explicit `(created_at, id)` order plus the page limit: PostgREST has no stable default order. */
internal fun PostgrestRequestBuilder.createdAtPage(limit: Int) {
    order("created_at", Order.ASCENDING)
    order("id", Order.ASCENDING)
    limit(limit.toLong())
}

/**
 * Runs a remote call on the IO dispatcher. Cancellation always propagates; any other failure,
 * including the `kotlin.Error` a wasm fetch failure surfaces as, becomes a [Result.failure].
 */
internal suspend fun <T> DispatcherProvider.remoteResult(block: suspend () -> T): Result<T> =
    withContext(io) {
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
