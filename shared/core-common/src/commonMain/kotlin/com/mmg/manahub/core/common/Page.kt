package com.mmg.manahub.core.common

/**
 * Platform-neutral pagination model — the future replacement for `androidx.paging.PagingData`,
 * which has no Kotlin/Wasm target and therefore cannot live in `commonMain`.
 *
 * This is the TYPE ONLY: no repository or call site is refactored onto it yet. A later phase will
 * migrate paged sources (e.g. `UserCardRepository`) to emit [Page]s behind a shared interface, with
 * the Android side adapting to/from `PagingData` and the web side driving its own paged fetch.
 *
 * @param T the item type in this page.
 * @property items the items loaded for this page.
 * @property nextKey opaque cursor/key to fetch the next page, or null when this is the last page.
 * @property hasMore whether more pages exist after this one.
 * @property total the total item count across all pages when known, or null if unknown.
 */
data class Page<out T>(
    val items: List<T>,
    val nextKey: String? = null,
    val hasMore: Boolean = nextKey != null,
    val total: Int? = null,
) {
    companion object {
        /** An empty terminal page (no items, no further pages). */
        fun <T> empty(): Page<T> = Page(items = emptyList(), nextKey = null, hasMore = false, total = 0)
    }
}
