package com.mmg.manahub.feature.communitydecks.data

import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.repository.CachePolicy
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.util.recordNonFatal
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.core.data.local.dao.CommunityDeckCacheDao
import com.mmg.manahub.core.data.remote.ArchidektClient
import com.mmg.manahub.feature.communitydecks.data.remote.ArchidektRequestQueue
import com.mmg.manahub.feature.communitydecks.data.remote.toCacheEntity
import com.mmg.manahub.feature.communitydecks.data.remote.toDomain
import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeck
import com.mmg.manahub.feature.communitydecks.domain.model.CommunityDeckSearchResult
import com.mmg.manahub.feature.communitydecks.domain.repository.CommunityDecksRepository
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Cache-first implementation of [CommunityDecksRepository].
 *
 * Strategy for [getDeckById]:
 * 1. Return a FRESH cache hit immediately (no network call).
 * 2. Otherwise fetch from Archidekt (rate-limited via [ArchidektRequestQueue]), cache it, return it.
 * 3. On network failure (HTTP or otherwise), fall back to a STALE cache hit flagged
 *    `isStale = true`; only when no cache exists at all is a [DataResult.Error] surfaced.
 *
 * All work runs on the injected [IoDispatcher].
 */
class CommunityDecksRepositoryImpl(
    private val api: ArchidektClient,
    private val requestQueue: ArchidektRequestQueue,
    private val cacheDao: CommunityDeckCacheDao,
    private val ioDispatcher: CoroutineDispatcher,
) : CommunityDecksRepository {

    override suspend fun getDeckById(archidektId: Int): DataResult<CommunityDeck> =
        withContext(ioDispatcher) {
            try {
                // 1. Fresh cache hit → serve directly.
                val cached = cacheDao.getById(archidektId)
                if (cached != null && CachePolicy.isFresh(cached.cachedAt)) {
                    return@withContext DataResult.Success(cached.toDomain())
                }

                // 2. Fetch from network (rate-limited + retried by the queue).
                val dto = requestQueue.execute { api.getDeckById(archidektId) }

                // Cache the response for offline re-render / stale fallback.
                cacheDao.insert(dto.toCacheEntity())

                DataResult.Success(dto.toDomain())
            } catch (e: ResponseException) {
                // HTTP error → try stale cache before failing.
                val statusCode = e.response.status.value
                val stale = cacheDao.getById(archidektId)
                recordSafeNonFatal("community_deck_fetch", e)
                FirebaseCrashlytics.getInstance().setCustomKey("community_deck_archidekt_id", archidektId)
                FirebaseCrashlytics.getInstance().setCustomKey("community_deck_http_code", statusCode)
                FirebaseCrashlytics.getInstance().setCustomKey("community_deck_stale_fallback", stale != null)
                if (stale != null) {
                    DataResult.Success(stale.toDomain(), isStale = true)
                } else {
                    val message = when (statusCode) {
                        404 -> "Deck not found on Archidekt"
                        429 -> "Too many requests. Please try again later."
                        else -> "Network error: $statusCode"
                    }
                    DataResult.Error(message)
                }
            } catch (e: Exception) {
                // Any other failure (IO, parse, …) → try stale cache before failing.
                val stale = cacheDao.getById(archidektId)
                recordSafeNonFatal("community_deck_fetch", e)
                FirebaseCrashlytics.getInstance().setCustomKey("community_deck_archidekt_id", archidektId)
                if (stale != null) {
                    DataResult.Success(stale.toDomain(), isStale = true)
                } else {
                    DataResult.Error(e.message ?: "Unknown error")
                }
            }
        }

    override suspend fun searchDecks(
        cardName: String?,
        deckFormat: Int?,
        orderBy: String?,
        page: Int,
        pageSize: Int,
    ): DataResult<CommunityDeckSearchResult> = withContext(ioDispatcher) {
        try {
            val dto = requestQueue.execute {
                api.searchDecks(cardName, deckFormat, orderBy, page, pageSize)
            }

            // Archidekt signals a server-side statement timeout with count = -1 and an
            // empty result set (commonly on a popular cardName + deckFormat combination).
            if (dto.count < 0) {
                recordNonFatal(
                    "community_deck_search_server_timeout",
                    IllegalStateException("Archidekt search timeout: count=${dto.count}"),
                )
                FirebaseCrashlytics.getInstance()
                    .setCustomKey("community_search_timeout_format", deckFormat?.toString() ?: "none")
                FirebaseCrashlytics.getInstance()
                    .setCustomKey("community_search_timeout_query_len", cardName?.length ?: 0)
                return@withContext DataResult.Error(
                    "Search timed out. Try a more specific query or remove the format filter.",
                )
            }

            DataResult.Success(dto.toDomain())
        } catch (e: ResponseException) {
            val statusCode = e.response.status.value
            recordSafeNonFatal("community_deck_search", e)
            FirebaseCrashlytics.getInstance().setCustomKey("community_deck_search_http_code", statusCode)
            val message = when (statusCode) {
                429 -> "Too many requests. Please try again later."
                else -> "Search failed: $statusCode"
            }
            DataResult.Error(message)
        } catch (e: Exception) {
            recordSafeNonFatal("community_deck_search", e)
            FirebaseCrashlytics.getInstance()
                .setCustomKey("community_deck_search_error_type", e.javaClass.simpleName)
            FirebaseCrashlytics.getInstance()
                .setCustomKey("community_deck_search_query_len", cardName?.length ?: 0)
            DataResult.Error(e.message ?: "Search failed")
        }
    }
}
