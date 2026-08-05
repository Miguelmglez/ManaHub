package com.mmg.manahub.core.data.cache

/**
 * Web actual for [CardStrategyTagsCache] (Card Detail tag DISPLAY slice, web roadmap W4b
 * follow-up — completing the CardDetailScreen built minimal/read-only in W4b). Room has no wasmJs
 * target, so this is a plain session-scoped in-memory map, mirroring
 * [com.mmg.manahub.core.data.repository.WebCardRepository]'s own `sessionCardCache` pattern:
 * cleared on page reload, never persisted to `localStorage`/IndexedDB. Precomputed strategy tags
 * are cheap to re-fetch on the next page load (a single Supabase row read), so this narrow,
 * ephemeral cache only exists to avoid re-fetching the SAME card's tags twice within one page
 * session (e.g. reopening the same Card Detail after navigating away and back) -- it is not a
 * durability guarantee.
 *
 * wasmJs is single-threaded (cooperative coroutines only), so a plain [MutableMap] needs no
 * synchronization here -- same reasoning already documented on [WebCardRepository]'s
 * `sessionCardCache`.
 */
class WebCardStrategyTagsCache : CardStrategyTagsCache {

    private val store = mutableMapOf<String, CachedCardStrategyTagsEntry>()

    override suspend fun get(oracleId: String): CachedCardStrategyTagsEntry? = store[oracleId]

    override suspend fun insert(entry: CachedCardStrategyTagsEntry) {
        store[entry.oracleId] = entry
    }
}
