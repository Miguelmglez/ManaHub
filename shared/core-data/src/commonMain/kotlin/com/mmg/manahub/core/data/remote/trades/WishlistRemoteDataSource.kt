package com.mmg.manahub.core.data.remote.trades

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.remote.dto.WishlistEntryDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Remote data source for the user wishlist.
 *
 * All calls delegate to [SupabaseClient] PostgREST and run on [DispatcherProvider.io]
 * (KMP-safe replacement for `Dispatchers.IO`).
 *
 * @param supabaseClient      The Supabase client for PostgREST calls.
 * @param dispatcherProvider   Platform dispatcher abstraction.
 */
class WishlistRemoteDataSource(
    private val supabaseClient: SupabaseClient,
    private val dispatcherProvider: DispatcherProvider = DispatcherProvider(),
) {
    /** Every row of [userId]'s list; fails unless the whole list was fetched. */
    suspend fun getWishlist(userId: String): Result<List<WishlistEntryDto>> = drainWishlist(userId).toResult()

    /**
     * Every row of [userId]'s list, drained page by page in `(created_at, id)` order. A table
     * query past `db-max-rows` is silently truncated, so only a complete drain may drive eviction.
     */
    suspend fun drainWishlist(userId: String): KeysetDrain<WishlistEntryDto> =
        drainByCreatedAt(
            id = { it.id },
            createdAt = { it.createdAt },
        ) { after, limit -> getWishlistPage(userId, after, limit) }

    /** One `(created_at, id)` keyset page of [getWishlist]. */
    suspend fun getWishlistPage(userId: String, after: CreatedAtCursor?, limit: Int): Result<List<WishlistEntryDto>> =
        dispatcherProvider.remoteResult {
            supabaseClient.postgrest["wishlists"]
                .select {
                    filter {
                        eq("user_id", userId)
                        if (after != null) afterCreatedAt(after)
                    }
                    createdAtPage(limit)
                }
                .decodeList<WishlistEntryDto>()
        }

    suspend fun addWishlistEntry(dto: WishlistEntryDto): Result<Unit> =
        dispatcherProvider.remoteResult {
            supabaseClient.postgrest["wishlists"].insert(dto)
            Unit
        }

    suspend fun removeWishlistEntry(id: String): Result<Unit> =
        dispatcherProvider.remoteResult {
            supabaseClient.postgrest["wishlists"]
                .delete { filter { eq("id", id) } }
            Unit
        }

    /**
     * Partial update of a synced wishlist row's quantity only (no full [WishlistEntryDto]
     * needed — the row already exists server-side and `user_id` never changes). Used to
     * propagate a local quantity decrement/edit to an already-synced entry instead of letting
     * the next [getWishlist] resurrect the stale server value (trades audit §2.3, 2026-07-10).
     */
    suspend fun updateWishlistQuantity(id: String, quantity: Int): Result<Unit> =
        dispatcherProvider.remoteResult {
            supabaseClient.postgrest["wishlists"].update(
                buildJsonObject { put("quantity", quantity) }
            ) { filter { eq("id", id) } }
            Unit
        }

    suspend fun batchAddWishlistEntries(dtos: List<WishlistEntryDto>): Result<Unit> =
        dispatcherProvider.remoteResult {
            if (dtos.isEmpty()) return@remoteResult Unit
            // upsert handles re-sync of entries whose local synced flag was reset,
            // avoiding a duplicate-key error that would leave the banner stuck forever.
            supabaseClient.postgrest["wishlists"].upsert(dtos)
            Unit
        }
}
