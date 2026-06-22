package com.mmg.manahub.core.data.repository

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.common.DispatcherProvider
import com.mmg.manahub.core.data.cache.CommunityDeckCache
import com.mmg.manahub.core.data.network.ArchidektRequestQueue
import com.mmg.manahub.core.data.remote.ArchidektClient
import com.mmg.manahub.core.data.remote.mapper.toDomain
import com.mmg.manahub.core.domain.repository.CommunityDecksRepository
import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.core.model.CommunityDeckSearchResult
import com.mmg.manahub.core.model.DataResult
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.withContext

/**
 * Cache-first implementation of [CommunityDecksRepository].
 *
 * Strategy for [getDeckById]:
 * 1. Return a FRESH cache hit immediately (no network call).
 * 2. Otherwise fetch from Archidekt (rate-limited via [requestQueue]), cache it, return it.
 * 3. On network failure (HTTP or otherwise), fall back to a STALE cache hit flagged
 *    `isStale = true`; only when no cache exists at all is a [DataResult.Error] surfaced.
 *
 * All work runs on [DispatcherProvider.io].
 *
 * @param api           the Archidekt HTTP client (Ktor-based, already in commonMain).
 * @param requestQueue  rate-limiter / retry queue for Archidekt calls.
 * @param cache         platform-neutral cache abstraction (Room on Android, IndexedDB/no-op on web).
 * @param crashReporter platform-neutral crash/log reporter (Crashlytics on Android, no-op on web).
 * @param dispatcherProvider platform dispatchers (IO on Android, Default on web).
 */
class CommunityDecksRepositoryImpl(
    private val api: ArchidektClient,
    private val requestQueue: ArchidektRequestQueue,
    private val cache: CommunityDeckCache,
    private val crashReporter: CrashReporter,
    private val dispatcherProvider: DispatcherProvider,
) : CommunityDecksRepository {

    override suspend fun getDeckById(archidektId: Int): DataResult<CommunityDeck> =
        withContext(dispatcherProvider.io) {
            try {
                // 1. Fresh cache hit → serve directly.
                val cached = cache.getById(archidektId)
                if (cached != null && CachePolicy.isFresh(cached.cachedAt)) {
                    return@withContext DataResult.Success(cached.dto.toDomain())
                }

                // 2. Fetch from network (rate-limited + retried by the queue).
                val dto = requestQueue.execute { api.getDeckById(archidektId) }

                // Cache the response for offline re-render / stale fallback.
                cache.insert(dto)

                DataResult.Success(dto.toDomain())
            } catch (e: ResponseException) {
                // HTTP error → try stale cache before failing.
                val statusCode = e.response.status.value
                val stale = cache.getById(archidektId)
                crashReporter.log("community_deck_fetch")
                crashReporter.recordException(e)
                crashReporter.setCustomKey("community_deck_archidekt_id", archidektId.toString())
                crashReporter.setCustomKey("community_deck_http_code", statusCode.toString())
                crashReporter.setCustomKey("community_deck_stale_fallback", (stale != null).toString())
                if (stale != null) {
                    DataResult.Success(stale.dto.toDomain(), isStale = true)
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
                val stale = cache.getById(archidektId)
                crashReporter.log("community_deck_fetch")
                crashReporter.recordException(e)
                crashReporter.setCustomKey("community_deck_archidekt_id", archidektId.toString())
                if (stale != null) {
                    DataResult.Success(stale.dto.toDomain(), isStale = true)
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
    ): DataResult<CommunityDeckSearchResult> = withContext(dispatcherProvider.io) {
        try {
            val dto = requestQueue.execute {
                api.searchDecks(cardName, deckFormat, orderBy, page, pageSize)
            }

            // Archidekt signals a server-side statement timeout with count = -1 and an
            // empty result set (commonly on a popular cardName + deckFormat combination).
            if (dto.count < 0) {
                crashReporter.log("community_deck_search_server_timeout")
                crashReporter.recordException(
                    IllegalStateException("Archidekt search timeout: count=${dto.count}"),
                )
                crashReporter.setCustomKey(
                    "community_search_timeout_format",
                    deckFormat?.toString() ?: "none",
                )
                crashReporter.setCustomKey(
                    "community_search_timeout_query_len",
                    (cardName?.length ?: 0).toString(),
                )
                return@withContext DataResult.Error(
                    "Search timed out. Try a more specific query or remove the format filter.",
                )
            }

            DataResult.Success(dto.toDomain())
        } catch (e: ResponseException) {
            val statusCode = e.response.status.value
            crashReporter.log("community_deck_search")
            crashReporter.recordException(e)
            crashReporter.setCustomKey("community_deck_search_http_code", statusCode.toString())
            val message = when (statusCode) {
                429 -> "Too many requests. Please try again later."
                else -> "Search failed: $statusCode"
            }
            DataResult.Error(message)
        } catch (e: Exception) {
            crashReporter.log("community_deck_search")
            crashReporter.recordException(e)
            crashReporter.setCustomKey(
                "community_deck_search_error_type",
                e::class.simpleName ?: "Unknown",
            )
            crashReporter.setCustomKey(
                "community_deck_search_query_len",
                (cardName?.length ?: 0).toString(),
            )
            DataResult.Error(e.message ?: "Search failed")
        }
    }
}
