package com.mmg.manahub.core.data.remote.trades

import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.remote.dto.WishlistEntryDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.withContext

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
