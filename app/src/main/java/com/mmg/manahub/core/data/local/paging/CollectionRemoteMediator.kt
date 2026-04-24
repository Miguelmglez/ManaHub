package com.mmg.manahub.core.data.local.paging

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import androidx.room.withTransaction
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.dao.UserCardWithCard
import com.mmg.manahub.core.data.remote.collection.UserCardCollectionDto
import com.mmg.manahub.core.data.remote.collection.toEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order

/**
 * Paging 3 [RemoteMediator] for the collection list.
 *
 * Loads pages from the Supabase `user_card_collection` table using offset-based
 * pagination (LIMIT/OFFSET via Postgrest). Results are cached in Room so the UI
 * can display them immediately on subsequent launches without waiting for the network.
 *
 * The mediator is configured with [InitializeAction.SKIP_INITIAL_REFRESH] because
 * the [com.mmg.manahub.core.sync.SyncManager] already keeps the local Room cache
 * up to date. The pager supplements that by loading older pages on demand.
 */
@OptIn(ExperimentalPagingApi::class)
class CollectionRemoteMediator(
    private val userId: String?,
    private val supabaseClient: SupabaseClient,
    private val userCardCollectionDao: UserCardCollectionDao,
    private val remoteKeyDao: RemoteKeyDao,
    private val db: MtgDatabase,
) : RemoteMediator<Int, UserCardWithCard>() {

    companion object {
        /** Key used to store the pagination cursor in [RemoteKeyDao]. */
        private const val LABEL = "collection_paging"

        /** Number of rows to fetch per page from Supabase. */
        private const val PAGE_SIZE = 50

        const val TABLE = "user_card_collection"
    }

    /**
     * Skip initial refresh because [com.mmg.manahub.core.sync.SyncManager] keeps
     * the Room cache current. The mediator only loads additional pages on APPEND.
     */
    override suspend fun initialize(): InitializeAction = InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun load(
        loadType: LoadType,
        state: PagingState<Int, UserCardWithCard>,
    ): MediatorResult {
        // PREPEND is not supported in offset-based pagination.
        if (loadType == LoadType.PREPEND) {
            return MediatorResult.Success(endOfPaginationReached = true)
        }

        // Guest users have no Supabase rows — signal end of pagination immediately.
        val safeUserId = userId ?: return MediatorResult.Success(endOfPaginationReached = true)

        val currentOffset = when (loadType) {
            LoadType.REFRESH -> 0
            LoadType.APPEND -> {
                val key = remoteKeyDao.getByLabel(LABEL)
                    ?: return MediatorResult.Success(endOfPaginationReached = true)
                key.nextOffset ?: return MediatorResult.Success(endOfPaginationReached = true)
            }
            LoadType.PREPEND -> return MediatorResult.Success(endOfPaginationReached = true)
        }

        return try {
            val remoteRows = supabaseClient.from(TABLE)
                .select {
                    filter {
                        eq("user_id", safeUserId)
                        eq("is_deleted", false)
                    }
                    order("updated_at", Order.DESCENDING)
                    range(currentOffset.toLong(), (currentOffset + PAGE_SIZE - 1).toLong())
                }
                .decodeList<UserCardCollectionDto>()

            val endOfPaginationReached = remoteRows.size < PAGE_SIZE

            db.withTransaction {
                if (loadType == LoadType.REFRESH) {
                    remoteKeyDao.delete(LABEL)
                }

                // Cache fetched rows into Room without overwriting newer local edits
                // (the DAO's upsert handles conflict resolution by updatedAt).
                userCardCollectionDao.upsertAll(remoteRows.map { it.toEntity() })

                if (!endOfPaginationReached) {
                    remoteKeyDao.upsert(RemoteKeyEntity(LABEL, currentOffset + remoteRows.size))
                }
            }

            MediatorResult.Success(endOfPaginationReached = endOfPaginationReached)
        } catch (e: Exception) {
            MediatorResult.Error(e)
        }
    }

}
