package core.domain.repository

import core.domain.model.UserCard
import core.domain.model.UserCardWithCard
import kotlinx.coroutines.flow.Flow

interface UserCardRepository {
    fun observeCollection(): Flow<List<UserCardWithCard>>
    fun observeByColor(color: String): Flow<List<UserCardWithCard>>
    fun observeByRarity(rarity: String): Flow<List<UserCardWithCard>>
    fun searchInCollection(query: String): Flow<List<UserCardWithCard>>
    suspend fun addOrIncrement(userCard: UserCard)
    suspend fun updateCard(userCard: UserCard)
    suspend fun deleteCard(id: Long)
    suspend fun incrementQuantity(id: Long)
    suspend fun updateQuantity(id: Long, quantity: Int)
}