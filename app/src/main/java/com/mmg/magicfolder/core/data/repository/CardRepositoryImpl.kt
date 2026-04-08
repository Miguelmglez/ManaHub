package com.mmg.magicfolder.core.data.repository

import com.mmg.magicfolder.core.data.local.UserPreferencesDataStore
import com.mmg.magicfolder.core.data.local.dao.CardDao
import com.mmg.magicfolder.core.data.local.dao.UserCardDao
import com.mmg.magicfolder.core.data.local.mapper.toSuggestedTagList
import com.mmg.magicfolder.core.data.local.mapper.toSuggestedTagsJson
import com.mmg.magicfolder.core.data.local.mapper.toTagList
import com.mmg.magicfolder.core.data.local.mapper.toTagsJson
import com.mmg.magicfolder.core.data.remote.ScryfallRemoteDataSource
import com.mmg.magicfolder.core.data.remote.mapper.toDomain
import com.mmg.magicfolder.core.data.remote.mapper.toEntity
import com.mmg.magicfolder.core.domain.model.Card
import com.mmg.magicfolder.core.domain.model.CardTag
import com.mmg.magicfolder.core.domain.model.DataResult
import com.mmg.magicfolder.core.domain.model.SuggestedTag
import com.mmg.magicfolder.core.domain.repository.CardRepository
import com.mmg.magicfolder.core.domain.usecase.card.SuggestTagsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardRepositoryImpl @Inject constructor(
    private val cardDao:        CardDao,
    private val userCardDao:    UserCardDao,
    private val remote:         ScryfallRemoteDataSource,
    private val suggestTags:    SuggestTagsUseCase,
    private val userPrefs:      UserPreferencesDataStore,
) : CardRepository {

    override suspend fun searchCardByName(query: String): DataResult<Card> {
        val result = remote.searchCardByName(query)
        return if (result.isSuccess) {
            val card = result.getOrThrow()
            cardDao.upsert(entityWithComputedTags(card))
            DataResult.Success(card)
        } else {
            DataResult.Error(result.exceptionOrNull()?.message ?: "Unknown error")
        }
    }

    override suspend fun searchCards(query: String, page: Int): DataResult<List<Card>> {
        val result = remote.searchCards(query, page)
        return if (result.isSuccess) {
            val cards = result.getOrThrow()
            cardDao.upsertAll(cards.map { entityWithComputedTags(it) })
            DataResult.Success(cards)
        } else {
            DataResult.Error(result.exceptionOrNull()?.message ?: "Unknown error")
        }
    }

    private suspend fun entityWithComputedTags(card: Card) = run {
        val existing = cardDao.getById(card.scryfallId)
        val (tagsJson, suggestedJson) = computeTagsForCache(card, existing?.tags)
        card.toEntity().copy(
            tags = tagsJson,
            userTags = existing?.userTags ?: "[]",
            suggestedTags = suggestedJson,
        )
    }

    override suspend fun getCardById(scryfallId: String): DataResult<Card> {
        val cached = cardDao.getById(scryfallId)
        if (cached != null && CachePolicy.isFresh(cached.cachedAt))
            return DataResult.Success(cached.toDomain())

        val result = remote.getCardById(scryfallId)
        return when {
            result.isSuccess -> {
                val card = result.getOrThrow()

                // Preserve any user edits to confirmed tags; otherwise auto-tag.
                val (tagsJson, suggestedJson) = computeTagsForCache(card, cached?.tags)

                cardDao.upsert(
                    card.toEntity().copy(
                        tags = tagsJson,
                        userTags = cached?.userTags ?: "[]",
                        suggestedTags = suggestedJson,
                    )
                )
                cardDao.clearStale(scryfallId)
                DataResult.Success(cardDao.getById(scryfallId)!!.toDomain())
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

    override fun observeCard(scryfallId: String): Flow<Card?> =
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
            cards.forEach { card ->
                val existing = cardDao.getById(card.scryfallId)
                val (tagsJson, suggestedJson) = computeTagsForCache(card, existing?.tags)
                cardDao.upsert(
                    card.toEntity().copy(
                        tags = tagsJson,
                        userTags = existing?.userTags ?: "[]",
                        suggestedTags = suggestedJson,
                    )
                )
            }
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

    override suspend fun updatePrices(
        scryfallId:   String,
        priceUsd:     Double?,
        priceUsdFoil: Double?,
        priceEur:     Double?,
        priceEurFoil: Double?,
    ) {
        cardDao.updatePrices(
            scryfallId   = scryfallId,
            priceUsd     = priceUsd,
            priceUsdFoil = priceUsdFoil,
            priceEur     = priceEur,
            priceEurFoil = priceEurFoil,
        )
    }

    override suspend fun evictStaleCache() =
        cardDao.evictStaleCache(System.currentTimeMillis() - CachePolicy.EVICT_MS)

    override suspend fun updateCardTags(scryfallId: String, tags: List<CardTag>) {
        cardDao.updateTags(scryfallId, tags.distinct().toTagsJson())
    }

    override suspend fun updateUserTags(scryfallId: String, userTags: List<CardTag>) {
        cardDao.updateUserTags(scryfallId, userTags.distinct().toTagsJson())
    }

    override suspend fun updateSuggestedTags(
        scryfallId: String, suggestions: List<SuggestedTag>,
    ) {
        cardDao.updateSuggestedTags(scryfallId, suggestions.toSuggestedTagsJson())
    }

    override suspend fun confirmSuggestedTag(scryfallId: String, tag: CardTag) {
        val cached = cardDao.getById(scryfallId) ?: return
        val confirmed   = (cached.tags.toTagList() + tag).distinct()
        val suggestions = cached.suggestedTags.toSuggestedTagList()
            .filterNot { it.tag.key == tag.key }
        cardDao.updateTagsAndSuggestions(
            scryfallId    = scryfallId,
            tagsJson      = confirmed.toTagsJson(),
            suggestedJson = suggestions.toSuggestedTagsJson(),
        )
    }

    override suspend fun dismissSuggestedTag(scryfallId: String, tag: CardTag) {
        val cached = cardDao.getById(scryfallId) ?: return
        val suggestions = cached.suggestedTags.toSuggestedTagList()
            .filterNot { it.tag.key == tag.key }
        cardDao.updateSuggestedTags(scryfallId, suggestions.toSuggestedTagsJson())
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Compute the JSON to persist for `tags` and `suggested_tags`.
     *
     * If the cached entity already has user-confirmed tags, we keep them and
     * only re-run the engine to refresh suggestions. Otherwise we run the
     * engine and use its split for both columns.
     */
    private suspend fun computeTagsForCache(
        card: Card,
        existingTagsJson: String?,
    ): Pair<String, String> {
        val auto    = userPrefs.tagAutoThresholdFlow.first()
        val suggest = userPrefs.tagSuggestThresholdFlow.first()
        val result  = suggestTags(card, autoThreshold = auto, suggestThreshold = suggest)

        val keepExisting = !existingTagsJson.isNullOrBlank() && existingTagsJson != "[]"
        val confirmed = if (keepExisting) {
            // Merge: keep user choices but add any newly-confirmed engine tags
            // that aren't already there (e.g. dictionary just learned them).
            (existingTagsJson!!.toTagList() + result.confirmed).distinct()
        } else {
            result.confirmed
        }
        return confirmed.toTagsJson() to result.suggested.toSuggestedTagsJson()
    }

    private fun buildStaleReason(e: Throwable?): String {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        return "${e?.message ?: "Network error"} — $date"
    }
}
