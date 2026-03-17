package com.mmg.magicfolder.code.core.data.repository

import com.mmg.magicfolder.code.core.data.local.dao.CardDao
import com.mmg.magicfolder.code.core.data.local.dao.UserCardDao
import com.mmg.magicfolder.code.core.data.local.mapper.toDomain
import com.mmg.magicfolder.code.core.data.local.mapper.toEntity
import com.mmg.magicfolder.code.core.data.remote.ScryfallRemoteDataSource
import com.mmg.magicfolder.code.core.data.remote.mapper.toEntity
import com.mmg.magicfolder.code.core.domain.model.Card
import com.mmg.magicfolder.code.core.domain.model.DataResult
import com.mmg.magicfolder.code.core.domain.repository.CardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepositoryImpl @Inject constructor(
    private val cardDao:     CardDao,
    private val userCardDao: UserCardDao,
    private val remote:      ScryfallRemoteDataSource,
) : CardRepository {

    override suspend fun searchCardByName(query: String): DataResult {
        val result = remote.searchCardByName(query)
        return if (result.isSuccess) {
            val card = result.getOrThrow()
            cardDao.upsert(card.toEntity())
            DataResult.Success(card)
        } else {
            DataResult.Error(result.exceptionOrNull()?.message ?: "Unknown error")
        }
    }

    override suspend fun searchCards(query: String, page: Int): DataResult> {
        val result = remote.searchCards(query, page)
        return if (result.isSuccess) {
            val cards = result.getOrThrow()
            cardDao.upsertAll(cards.map { it.toEntity() })
            DataResult.Success(cards)
        } else {
            DataResult.Error(result.exceptionOrNull()?.message ?: "Unknown error")
        }
    }

    override suspend fun getCardById(scryfallId: String): DataResult {
        val cached = cardDao.getById(scryfallId)
        if (cached != null && CachePolicy.isFresh(cached.cachedAt))
            return DataResult.Success(cached.toDomain())

        val result = remote.getCardById(scryfallId)
        return when {
            result.isSuccess -> {
                val card = result.getOrThrow()
                cardDao.upsert(card.toEntity())
                cardDao.clearStale(scryfallId)
                DataResult.Success(card)
            }
            cached != null -> {
                if (CachePolicy.isStale(cached.cachedAt))
                    cardDao.markStale(scryfallId, buildStaleReason(result.exceptionOrNull()))
                DataResult.Success(
                    data    = cached.toDomain(),
                    isStale = CachePolicy.isStale(cached.cachedAt),
                )
            }
            else -> DataResult.Error(
                result.exceptionOrNull()?.message ?: "No local data and network unavailable"
            )
        }
    }

    override fun observeCard(scryfallId: String): Flow =
        cardDao.observeById(scryfallId).map { it?.toDomain() }

    override suspend fun refreshCollectionPrices() {
        val allIds = userCardDao.getAllScryfallIds()
        if (allIds.isEmpty()) return
        val staleIds = allIds.filter { id ->
            val c = cardDao.getById(id)
            c == null || !CachePolicy.isFresh(c.cachedAt)
        }
        if (staleIds.isEmpty()) return
        val result = remote.getCardsBatch(staleIds)
        if (result.isSuccess) {
            val cards = result.getOrThrow()
            cardDao.upsertAll(cards.map { it.toEntity() })
            cards.forEach { cardDao.clearStale(it.scryfallId) }
            val refreshed = cards.map { it.scryfallId }.toSet()
            (staleIds - refreshed).forEach { id ->
                val c = cardDao.getById(id) ?: return@forEach
                if (CachePolicy.isStale(c.cachedAt))
                    cardDao.markStale(id, "Not found in Scryfall batch response")
            }
        } else {
            val reason = buildStaleReason(result.exceptionOrNull())
            staleIds.forEach { id ->
                val c = cardDao.getById(id) ?: return@forEach
                if (CachePolicy.isStale(c.cachedAt)) cardDao.markStale(id, reason)
            }
        }
    }

    override suspend fun evictStaleCache() =
        cardDao.evictStaleCache(System.currentTimeMillis() - CachePolicy.EVICT_MS)

    private fun buildStaleReason(e: Throwable?): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return "${e?.message ?: "Network error"} — $date"
    }
}