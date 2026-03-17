package com.mmg.magicfolder.code.core.data.repository

import com.mmg.magicfolder.code.core.data.local.dao.UserCardDao
import com.mmg.magicfolder.code.core.data.local.
import com.mmg.magicfolder.code.core.data.local.mapper.toEntity
import com.mmg.magicfolder.code.core.domain.model.UserCard
import com.mmg.magicfolder.code.core.domain.repository.UserCardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserCardRepositoryImpl @Inject constructor(
    private val userCardDao: UserCardDao,
) : UserCardRepository {

    override fun observeCollection(): Flow> =
    userCardDao.observeCollection().map { it.map { r -> r.toDomain() } }

    override fun observeByColor(color: String): Flow> =
    userCardDao.observeByColor(color).map { it.map { r -> r.toDomain() } }

    override fun observeByRarity(rarity: String): Flow> =
    userCardDao.observeByRarity(rarity).map { it.map { r -> r.toDomain() } }

    override fun searchInCollection(query: String): Flow> =
    userCardDao.searchInCollection(query).map { it.map { r -> r.toDomain() } }

    override suspend fun addOrIncrement(userCard: UserCard) {
        val inserted = userCardDao.insert(userCard.toEntity())
        if (inserted == -1L) {
            userCardDao.incrementQuantityByUniqueKey(
                scryfallId = userCard.scryfallId,
                isFoil     = userCard.isFoil,
                condition  = userCard.condition,
                language   = userCard.language,
            )
        }
    }

    override suspend fun updateCard(userCard: UserCard) = userCardDao.update(userCard.toEntity())
    override suspend fun deleteCard(id: Long)           = userCardDao.deleteById(id)
    override suspend fun incrementQuantity(id: Long)    = userCardDao.incrementQuantity(id)

    override suspend fun updateQuantity(id: Long, quantity: Int) {
        require(quantity >= 0) { "Quantity cannot be negative" }
        if (quantity == 0) userCardDao.deleteById(id)
        else userCardDao.updateQuantity(id, quantity)
    }
}