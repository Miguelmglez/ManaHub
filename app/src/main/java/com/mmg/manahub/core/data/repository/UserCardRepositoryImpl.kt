package com.mmg.manahub.core.data.repository

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.mmg.manahub.core.data.local.MtgDatabase
import com.mmg.manahub.core.data.local.dao.UserCardCollectionDao
import com.mmg.manahub.core.data.local.dao.UserCardWithCard
import com.mmg.manahub.core.data.local.entity.UserCardCollectionEntity
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.data.local.paging.CollectionRemoteMediator
import com.mmg.manahub.core.data.local.paging.RemoteKeyDao
import com.mmg.manahub.core.data.remote.collection.CollectionRemoteDataSource
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.mmg.manahub.core.domain.model.UserCardWithCard as DomainUserCardWithCard

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
    private val authRepository: AuthRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UserCardRepository {

    // Emits null for unauthenticated/loading, userId for authenticated.
    // Used by all observe methods so they re-subscribe when the user changes.
    @Suppress("OPT_IN_USAGE")
    private val currentUserIdFlow = authRepository.sessionState.map { state ->
        (state as? SessionState.Authenticated)?.user?.id
    }

    // ── Observables ───────────────────────────────────────────────────────────

    @Suppress("OPT_IN_USAGE")
    override fun observeCollection(): Flow<List<DomainUserCardWithCard>> =
        currentUserIdFlow.flatMapLatest { userId ->
            // When logged out (userId == null) show ALL local non-deleted cards so the
            // collection stays visible after logout instead of going blank. Logged-in
            // cards have a real userId and would be invisible to observeAll(null).
            val source = if (userId != null)
                userCardCollectionDao.observeAll(userId)
            else
                userCardCollectionDao.observeAllLocal()
            source.map { list -> list.filter { !it.userCard.isDeleted }.mapNotNull { it.toDomain() } }
        }

    @Suppress("OPT_IN_USAGE")
    override fun observeByColor(color: String): Flow<List<DomainUserCardWithCard>> =
        currentUserIdFlow.flatMapLatest { userId ->
            userCardCollectionDao.observeAll(userId).map { list ->
                list.filter { item ->
                    !item.userCard.isDeleted &&
                        item.card?.colorIdentity?.contains(color, ignoreCase = true) == true
                }.mapNotNull { it.toDomain() }
            }
        }

    @Suppress("OPT_IN_USAGE")
    override fun observeByRarity(rarity: String): Flow<List<DomainUserCardWithCard>> =
        currentUserIdFlow.flatMapLatest { userId ->
            userCardCollectionDao.observeAll(userId).map { list ->
                list.filter { item ->
                    !item.userCard.isDeleted &&
                        item.card?.rarity?.equals(rarity, ignoreCase = true) == true
                }.mapNotNull { it.toDomain() }
            }
        }

    @Suppress("OPT_IN_USAGE")
    override fun searchInCollection(query: String): Flow<List<DomainUserCardWithCard>> =
        currentUserIdFlow.flatMapLatest { userId ->
            userCardCollectionDao.observeAll(userId).map { list ->
                list.filter { item ->
                    !item.userCard.isDeleted &&
                        item.card?.name?.contains(query, ignoreCase = true) == true
                }.mapNotNull { it.toDomain() }
            }
        }

    @Suppress("OPT_IN_USAGE")
    override fun observeByScryfallId(scryfallId: String, userId: String?): Flow<List<UserCard>> {
        // If caller passes an explicit userId, use it directly.
        // If null, follow the current session so logged-in users see their cards.
        return if (userId != null) {
            userCardCollectionDao.observeByScryfall(scryfallId, userId).map { it.toUserCards() }
        } else {
            currentUserIdFlow.flatMapLatest { resolvedId ->
                userCardCollectionDao.observeByScryfall(scryfallId, resolvedId).map { it.toUserCards() }
            }
        }
    }

    private fun List<UserCardCollectionEntity>.toUserCards() =
        filter { !it.isDeleted }.map { entity ->
            UserCard(
                id = entity.id,
                scryfallId = entity.scryfallId,
                quantity = entity.quantity,
                isFoil = entity.isFoil,
                condition = entity.condition,
                language = entity.language,
                isAlternativeArt = entity.isAlternativeArt,
                isForTrade = entity.isForTrade,
                updatedAt = entity.updatedAt,
                createdAt = entity.createdAt,
            )
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
        userId: String?,
        quantity: Int,
    ) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        val resolvedUserId = userId ?: authRepository.getCurrentUser()?.id
        val normalizedCondition = condition.uppercase().trim()
        val normalizedLanguage = language.lowercase().trim()

        // Look up existing row by composite unique key (synchronous query, not Flow).
        val existing = if (!resolvedUserId.isNullOrBlank()) {
            userCardCollectionDao.getByCompositeKey(
                resolvedUserId, scryfallId, isFoil,
                normalizedCondition, normalizedLanguage, isAlternativeArt,
            )
        } else {
            userCardCollectionDao.getByCompositeKeyGuest(
                scryfallId, isFoil, normalizedCondition,
                normalizedLanguage, isAlternativeArt,
            )
        }

        if (existing != null && !existing.isDeleted) {
            // INCREMENT existing row's quantity
            userCardCollectionDao.upsert(
                existing.copy(
                    quantity  = existing.quantity + quantity,
                    isForTrade = isForTrade || existing.isForTrade,
                    updatedAt = now,
                )
            )
        } else {
            // INSERT new row
            userCardCollectionDao.upsert(
                UserCardCollectionEntity(
                    id               = UUID.randomUUID().toString(),
                    userId           = resolvedUserId,
                    scryfallId       = scryfallId,
                    quantity         = quantity,
                    isFoil           = isFoil,
                    condition        = normalizedCondition,
                    language         = normalizedLanguage,
                    isAlternativeArt = isAlternativeArt,
                    isForTrade       = isForTrade,
                    isDeleted        = false,
                    updatedAt        = now,
                    createdAt        = now,
                )
            )
        }
        Unit
    }

    override suspend fun updateAttributes(
        id: String,
        isForTrade: Boolean,
        quantity: Int,
    ) = withContext(ioDispatcher) {
        val existing = userCardCollectionDao.getById(id) ?: return@withContext
        userCardCollectionDao.upsert(
            existing.copy(
                isForTrade = isForTrade,
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
        val userId = authRepository.getCurrentUser()?.id ?: ""
        userCardCollectionDao.getAllSince(userId, 0L)
            .filter { !it.isDeleted }
            .map { it.scryfallId }
            .distinct()
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun UserCardWithCard.toDomain(): DomainUserCardWithCard? {
        val cardEntity = card ?: return null
        val userCardDomain = UserCard(
            id = userCard.id,
            scryfallId = userCard.scryfallId,
            quantity = userCard.quantity,
            isFoil = userCard.isFoil,
            condition = userCard.condition,
            language = userCard.language,
            isAlternativeArt = userCard.isAlternativeArt,
            isForTrade = userCard.isForTrade,
            updatedAt = userCard.updatedAt,
            createdAt = userCard.createdAt,
        )
        return DomainUserCardWithCard(userCard = userCardDomain, card = cardEntity.toDomainCard())
    }

    companion object {
        private const val PAGE_SIZE = 50
    }
}
