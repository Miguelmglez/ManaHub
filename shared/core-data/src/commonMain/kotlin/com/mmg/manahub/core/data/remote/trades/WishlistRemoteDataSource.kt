package com.mmg.manahub.core.data.remote.trades

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.remote.dto.WishlistEntryDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.withContext
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
    suspend fun getWishlist(userId: String): Result<List<WishlistEntryDto>> =
        withContext(dispatcherProvider.io) {
            runCatching {
                supabaseClient.postgrest["wishlists"]
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<WishlistEntryDto>()
            }
        }

    suspend fun addWishlistEntry(dto: WishlistEntryDto): Result<Unit> =
        withContext(dispatcherProvider.io) {
            runCatching {
                supabaseClient.postgrest["wishlists"].insert(dto)
                Unit
            }
        }

    suspend fun removeWishlistEntry(id: String): Result<Unit> =
        withContext(dispatcherProvider.io) {
            runCatching {
                supabaseClient.postgrest["wishlists"]
                    .delete { filter { eq("id", id) } }
                Unit
            }
        }

    /**
     * Partial update of a synced wishlist row's quantity only (no full [WishlistEntryDto]
     * needed — the row already exists server-side and `user_id` never changes). Used to
     * propagate a local quantity decrement/edit to an already-synced entry instead of letting
     * the next [getWishlist] resurrect the stale server value (trades audit §2.3, 2026-07-10).
     */
    suspend fun updateWishlistQuantity(id: String, quantity: Int): Result<Unit> =
        withContext(dispatcherProvider.io) {
            runCatching {
                supabaseClient.postgrest["wishlists"].update(
                    buildJsonObject { put("quantity", quantity) }
                ) { filter { eq("id", id) } }
                Unit
            }
        }

    suspend fun batchAddWishlistEntries(dtos: List<WishlistEntryDto>): Result<Unit> =
        withContext(dispatcherProvider.io) {
            runCatching {
                if (dtos.isEmpty()) return@runCatching
                // upsert handles re-sync of entries whose local synced flag was reset,
                // avoiding a duplicate-key error that would leave the banner stuck forever.
                supabaseClient.postgrest["wishlists"].upsert(dtos)
                Unit
            }
        }
}
