package com.mmg.magicfolder.code.core.data.repository

object CachePolicy {
    const val FRESH_MS = 24L * 60 * 60 * 1_000        // 24 h
    const val STALE_MS = 7L  * 24 * 60 * 60 * 1_000   // 7 days  — show warning badge
    const val EVICT_MS = 30L * 24 * 60 * 60 * 1_000   // 30 days — evict uncollected cards

    fun isFresh(cachedAt: Long): Boolean = System.currentTimeMillis() - cachedAt < FRESH_MS
    fun isStale(cachedAt: Long): Boolean = System.currentTimeMillis() - cachedAt > STALE_MS
}