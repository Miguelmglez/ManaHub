package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.data.local.dao.UserCardDao
import com.mmg.manahub.core.data.local.mapper.toDomain
import com.mmg.manahub.core.data.local.mapper.toEntity
import com.mmg.manahub.core.domain.model.UserCard
import com.mmg.manahub.core.domain.model.UserCardWithCard
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.repository.UserCardRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserCardRepositoryImpl @Inject constructor(
    private val userCardDao: UserCardDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : UserCardRepository {

    override fun observeCollection(): Flow<List<UserCardWithCard>> =
        userCardDao.observeCollection().map { it.map { r -> r.toDomain() } }

    override fun observeByColor(color: String): Flow<List<UserCardWithCard>> =
        userCardDao.observeByColor(color).map { it.map { r -> r.toDomain() } }

    override fun observeByRarity(rarity: String): Flow<List<UserCardWithCard>> =
        userCardDao.observeByRarity(rarity).map { it.map { r -> r.toDomain() } }

    override fun searchInCollection(query: String): Flow<List<UserCardWithCard>> =
        userCardDao.searchInCollection(query).map { it.map { r -> r.toDomain() } }

    override fun observeByScryfallId(scryfallId: String): Flow<List<UserCard>> =
        userCardDao.observeByScryfallId(scryfallId).map { it.map { entity -> entity.toDomain() } }

    override suspend fun addOrIncrement(userCard: UserCard) = withContext(ioDispatcher) {
        // Atomicity is enforced inside UserCardDao.insertOrIncrement() via @Transaction.
        userCardDao.insertOrIncrement(userCard.toEntity())
    }

    override suspend fun updateCard(userCard: UserCard) = withContext(ioDispatcher) {
        userCardDao.update(userCard.toEntity())
    }

    override suspend fun deleteCard(id: Long) = withContext(ioDispatcher) {
        userCardDao.deleteById(id)
    }

    override suspend fun incrementQuantity(id: Long) = withContext(ioDispatcher) {
        userCardDao.incrementQuantity(id)
    }

    override suspend fun updateQuantity(id: Long, quantity: Int) = withContext(ioDispatcher) {
        require(quantity >= 0) { "Quantity cannot be negative" }
        if (quantity == 0) userCardDao.deleteById(id)
        else userCardDao.updateQuantity(id, quantity)
    }

    override suspend fun getScryfallIds(): List<String> = withContext(ioDispatcher) {
        userCardDao.getAllScryfallIds()
    }
}