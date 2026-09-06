package com.mmg.manahub.core.data.sync

import kotlin.coroutines.cancellation.CancellationException

/**
 * Row shape shared by every keyset-paginated Supabase `*_changes_page` RPC drain
 * ([UserCardCollectionDto][com.mmg.manahub.core.data.remote.collection.UserCardCollectionDto],
 * [DeckSyncDto][com.mmg.manahub.core.data.remote.decks.DeckSyncDto]).
 *
 * `id`/`updatedAt` are exactly the two columns the server's keyset cursor is built from:
 * `WHERE (updated_at, id) > (p_after_updated_at, p_after_id) ORDER BY updated_at, id`.
 */
interface KeysetPageRow {
    val id: String
    val updatedAt: Long
}

/**
 * Outcome of draining every page of a keyset-paginated `*_changes_page` RPC.
 *
 * @property minUnappliedUpdatedAt The `updatedAt` boundary a caller's safe-watermark formula must
 *   respect. [Long.MAX_VALUE] means "nothing is unapplied" — every page drained AND every row in
 *   every page was applied, so the caller may advance its watermark all the way to its own
 *   `syncStartTime` snapshot via `min(syncStartTime, minUnappliedUpdatedAt - 1)`. Any other value
 *   is the `updatedAt` of the LAST successfully-consumed row before the loop stopped (a page-fetch
 *   failure, or a page whose rows didn't all apply) — see [drainPages]'s KDoc for why this is the
 *   row's OWN timestamp and not one past it.
 * @property pagesDrained Number of pages successfully fetched (diagnostic/telemetry only).
 */
data class PageDrainResult(
    val minUnappliedUpdatedAt: Long,
    val pagesDrained: Int,
)

/**
 * Drains every page of a keyset-paginated Supabase `*_changes_page` RPC, feeding rows to [onPage]
 * as they arrive and tracking the safe ceiling a caller's watermark formula must respect.
 *
 * KMP-safe (no Android/browser import): lives in `:shared:core-data` commonMain so Android's
 * `SyncManager` (Room-backed) and any future web pull path share ONE implementation of the
 * pagination/cursor-advance/failure-bookkeeping logic — the collection-sync data-loss fix
 * (`linear-moseying-yeti` plan) explicitly calls for this to be reusable by the gamification
 * `*_changes_since` RPCs later.
 *
 * ## Cursor rule
 * The NEXT page's cursor is derived from the LAST ROW OF THE CURRENT PAGE (never a computed max
 * across the page), matching the server's keyset contract `(updated_at, id) > (p_after_updated_at,
 * p_after_id)`. The first page passes `afterUpdatedAt = null, afterId = null`. The loop terminates
 * when a page returns FEWER rows than [limit] (the server itself caps at `LEAST(p_limit, 500)`, so
 * a full-sized page always means "there may be more").
 *
 * ## Safe-watermark ceiling on failure
 * On a page-fetch failure OR a page whose rows did not all apply, [PageDrainResult
 * .minUnappliedUpdatedAt] is set to the `updatedAt` of the LAST successfully-consumed row (or
 * `since` itself if no page ever succeeded) — deliberately NOT that row's successor. The row's own
 * millisecond may be shared with sibling rows this cycle never fetched (keyset ties are broken by
 * `id`, not `updatedAt` alone), so a caller computing `min(syncStartTime, minUnappliedUpdatedAt -
 * 1)` gets a watermark strictly BELOW the last consumed row — the next cycle re-fetches it too.
 * Re-fetching an already-applied row is free (LWW + `@Upsert`), silent data loss is not.
 *
 * @param T Row type; must expose [KeysetPageRow.id]/[KeysetPageRow.updatedAt] for cursor advance.
 * @param since The caller's current watermark — the RPC's own `p_since` window filter. Also the
 *   fallback ceiling when the very first page fails (nothing was ever consumed this cycle).
 * @param limit Page size requested from the server (server itself caps at 500).
 * @param maxPages Hard cap on iterations so a server bug (e.g. a cursor that never advances)
 *   cannot spin this loop forever. 200 pages x 500 rows = 100k rows, far beyond any real user.
 * @param fetchPage `(afterUpdatedAt, afterId, limit) -> Result<List<T>>` — the RPC call.
 * @param onPage Called once per successfully-fetched, non-empty page with its rows; returns
 *   `true` if every row in the page was applied, `false` if at least one failed to apply (e.g. a
 *   genuine Room write exception — NOT a card-metadata gap, which the placeholder path always
 *   resolves as a successful apply).
 */
suspend fun <T : KeysetPageRow> drainPages(
    since: Long,
    limit: Int,
    maxPages: Int = 200,
    fetchPage: suspend (afterUpdatedAt: Long?, afterId: String?, limit: Int) -> Result<List<T>>,
    onPage: suspend (List<T>) -> Boolean,
): PageDrainResult {
    var afterUpdatedAt: Long? = null
    var afterId: String? = null
    var lastConsumedUpdatedAt = since
    var pages = 0

    while (pages < maxPages) {
        val pageResult = fetchPage(afterUpdatedAt, afterId, limit)
        val page = pageResult.getOrElse { error ->
            if (error is CancellationException) throw error
            return PageDrainResult(lastConsumedUpdatedAt, pages)
        }
        pages++

        if (page.isEmpty()) {
            return PageDrainResult(Long.MAX_VALUE, pages)
        }

        val appliedCleanly = onPage(page)
        if (!appliedCleanly) {
            // Something in this page failed to apply -- stop advancing past what came before it.
            // The page's own rows are NOT counted as consumed since we can't tell which of them
            // (possibly all) failed; lastConsumedUpdatedAt still points at the prior page's tail.
            return PageDrainResult(lastConsumedUpdatedAt, pages)
        }

        val last = page.last()
        lastConsumedUpdatedAt = last.updatedAt
        afterUpdatedAt = last.updatedAt
        afterId = last.id

        if (page.size < limit) {
            return PageDrainResult(Long.MAX_VALUE, pages)
        }
    }
    // Hit maxPages without draining -- defensive only (should not happen for any real user); treat
    // as incomplete so the watermark stays capped at the last known-safe point.
    return PageDrainResult(lastConsumedUpdatedAt, pages)
}
