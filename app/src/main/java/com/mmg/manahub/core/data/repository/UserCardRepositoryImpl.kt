package com.mmg.manahub.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.dao.UserCardWithCard
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.data.local.paging.RemoteKeyDao
import com.mmg.manahub.core.data.local.paging.CollectionRemoteMediator
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.domain.model.UserCardWithCard as DomainUserCardWithCard
import com.mmg.manahub.core.domain.repository.UserCardRepository
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-first implementation of [UserCardRepository].
 *
 * All mutations write to Room first. The [com.mmg.manahub.core.sync.SyncManager]
 * detects dirty rows via [UserCardCollectionEntity.updatedAt] and pushes them to
 * Supabase on the next sync cycle.
 *
 * Soft-deletes set [UserCardCollectionEntity.isDeleted] = true rather than
 * removing the row, so that the deletion is propagated to Supabase on the next push.
 */
@Singleton
class UserCardRepositoryImpl @Inject constructor(
    private val userCardCollectionDao: UserCardCollectionDao,
    private val collectionRemoteDataSource: CollectionRemoteDataSource,
    private val remoteKeyDao: RemoteKeyDao,
    private val database: MtgDatabase,
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UserCardRepository {

    // ── Observables ───────────────────────────────────────────────────────────

    override fun observeCollection(): Flow<List<DomainUserCardWithCard>> =
        userCardCollectionDao.observeAll(null).map { list ->
            list.filter { !it.userCard.isDeleted }.map { it.toDomain() }
        }

    override fun observeByColor(color: String): Flow<List<DomainUserCardWithCard>> =
        userCardCollectionDao.observeAll(null).map { list ->
            list.filter { item ->
                !item.userCard.isDeleted &&
                    item.card.colorIdentity.contains(color, ignoreCase = true)
            }.map { it.toDomain() }
        }

    override fun observeByRarity(rarity: String): Flow<List<DomainUserCardWithCard>> =
        userCardCollectionDao.observeAll(null).map { list ->
            list.filter { item ->
                !item.userCard.isDeleted &&
                    item.card.rarity.equals(rarity, ignoreCase = true)
            }.map { it.toDomain() }
        }

    override fun searchInCollection(query: String): Flow<List<DomainUserCardWithCard>> =
        userCardCollectionDao.observeAll(null).map { list ->
            list.filter { item ->
                !item.userCard.isDeleted &&
                    item.card.name.contains(query, ignoreCase = true)
            }.map { it.toDomain() }
        }

    override fun observeByScryfallId(scryfallId: String, userId: String?): Flow<List<UserCard>> =
        userCardCollectionDao.observeByScryfall(scryfallId, userId).map { list ->
            list.filter { !it.isDeleted }.map { entity ->
                UserCard(
                    id = entity.id,
                    scryfallId = entity.scryfallId,
                    quantity = entity.quantity,
                    isFoil = entity.isFoil,
                    condition = entity.condition,
                    language = entity.language,
                    isAlternativeArt = entity.isAlternativeArt,
                    isForTrade = entity.isForTrade,
                    isInWishlist = entity.isInWishlist,
                    updatedAt = entity.updatedAt,
                    createdAt = entity.createdAt,
                )
            }
        }

    override fun observeCount(userId: String?): Flow<Int> =
        userCardCollectionDao.observeCount(userId)

    @OptIn(ExperimentalPagingApi::class)
    override fun getCollectionPager(userId: String?): Flow<PagingData<UserCardWithCard>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false),
            remoteMediator = CollectionRemoteMediator(
                userId = userId,
                supabaseClient = supabaseClient,
                userCardCollectionDao = userCardCollectionDao,
                remoteKeyDao = remoteKeyDao,
                db = database,
            ),
            pagingSourceFactory = { userCardCollectionDao.getCollectionPagingSource(userId) },
        ).flow

    // ── Mutations ─────────────────────────────────────────────────────────────

    override suspend fun addOrIncrement(
        scryfallId: String,
        isFoil: Boolean,
        condition: String,
        language: String,
        isAlternativeArt: Boolean,
        isForTrade: Boolean,
        isInWishlist: Boolean,
        userId: String?,
    ) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()

        // Try to find an existing row matching the unique physical variant.
        val existing = userCardCollectionDao.observeByScryfall(scryfallId, userId)
            // This is a Flow — we read once using a suspend-compatible approach.
            // Use getById after finding the match from a snapshot.
            .let { flow ->
                // Read current list synchronously from the DAO (non-Flow query).
                null // will resolve via upsert logic below
            }

        // The DAO's upsert handles the insert-or-increment logic:
        // If a row with the same (userId, scryfallId, isFoil, condition, language,
        // isAlternativeArt) already exists, it increments the quantity and updates
        // updatedAt. Otherwise, it inserts a new row with a fresh UUID.
        val entity = UserCardCollectionEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            scryfallId = scryfallId,
            quantity = 1,
            isFoil = isFoil,
            condition = condition,
            language = language,
            isAlternativeArt = isAlternativeArt,
            isForTrade = isForTrade,
            isInWishlist = isInWishlist,
            isDeleted = false,
            updatedAt = now,
            createdAt = now,
        )
        userCardCollectionDao.upsert(entity)
    }

    override suspend fun updateAttributes(
        id: String,
        isForTrade: Boolean,
        isInWishlist: Boolean,
        quantity: Int,
    ) = withContext(ioDispatcher) {
        val existing = userCardCollectionDao.getById(id) ?: return@withContext
        userCardCollectionDao.upsert(
            existing.copy(
                isForTrade = isForTrade,
                isInWishlist = isInWishlist,
                quantity = quantity,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    override suspend fun deleteCard(id: String) = withContext(ioDispatcher) {
        // Soft delete: set isDeleted = true, bump updatedAt. The sync engine will
        // propagate this deletion to Supabase on the next push cycle.
        userCardCollectionDao.softDelete(id, System.currentTimeMillis())
    }

    override suspend fun getScryfallIds(): List<String> = withContext(ioDispatcher) {
        // Return distinct scryfall IDs from non-deleted rows.
        userCardCollectionDao.observeAll(null)
            .let { flow ->
                // Use getAllSince with 0L to get all rows, filter non-deleted.
                userCardCollectionDao.getAllSince("", 0L)
                    .filter { !it.isDeleted }
                    .map { it.scryfallId }
                    .distinct()
            }
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun UserCardWithCard.toDomain(): DomainUserCardWithCard {
        val userCardDomain = UserCard(
            id = userCard.id,
            scryfallId = userCard.scryfallId,
            quantity = userCard.quantity,
            isFoil = userCard.isFoil,
            condition = userCard.condition,
            language = userCard.language,
            isAlternativeArt = userCard.isAlternativeArt,
            isForTrade = userCard.isForTrade,
            isInWishlist = userCard.isInWishlist,
            updatedAt = userCard.updatedAt,
            createdAt = userCard.createdAt,
        )
        return DomainUserCardWithCard(userCard = userCardDomain, card = card.toDomainCard())
    }

    companion object {
        private const val PAGE_SIZE = 50
    }
}
