package com.mmg.manahub.feature.trades.data.remote

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.trades.data.remote.dto.WishlistEntryDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WishlistRemoteDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun getWishlist(userId: String): Result<List<WishlistEntryDto>> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest["wishlists"]
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<WishlistEntryDto>()
            }
        }

    suspend fun addWishlistEntry(dto: WishlistEntryDto): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest["wishlists"].insert(dto)
                Unit
            }
        }

    suspend fun removeWishlistEntry(id: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                supabaseClient.postgrest["wishlists"]
                    .delete { filter { eq("id", id) } }
                Unit
            }
        }

    suspend fun batchAddWishlistEntries(dtos: List<WishlistEntryDto>): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                if (dtos.isEmpty()) return@runCatching
                // upsert handles re-sync of entries whose local synced flag was reset,
                // avoiding a duplicate-key error that would leave the banner stuck forever.
                supabaseClient.postgrest["wishlists"].upsert(dtos)
                Unit
            }
        }
}
