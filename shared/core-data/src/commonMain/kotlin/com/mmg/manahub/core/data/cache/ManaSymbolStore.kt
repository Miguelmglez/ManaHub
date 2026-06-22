package com.mmg.manahub.core.data.cache

/**
 * Platform-neutral store for mana symbol data.
 *
 * The Android implementation wraps [ManaSymbolDao][com.mmg.manahub.core.data.local.dao.ManaSymbolDao]
 * (Room); the web implementation can use a remote API or skip caching entirely.
 */
interface ManaSymbolStore {

    /** Returns the number of cached mana symbols. */
    suspend fun count(): Int

    /** Upserts all [symbols] into the local store. */
    suspend fun upsertAll(symbols: List<ManaSymbolRecord>)
}

/**
 * Platform-neutral representation of a mana symbol for the [ManaSymbolStore] contract.
 * Maps 1:1 to [ManaSymbolEntity][com.mmg.manahub.core.data.local.entity.ManaSymbolEntity] on Android.
 */
data class ManaSymbolRecord(
    val symbol: String,
    val svgUri: String,
    val description: String,
    val isHybrid: Boolean,
    val isPhyrexian: Boolean,
)
