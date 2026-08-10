package com.mmg.manahub.feature.communitydecks.data

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.data.cache.CachedDeckEntry
import com.mmg.manahub.core.data.cache.CommunityDeckCache
import com.mmg.manahub.core.data.local.dao.CommunityDeckCacheDao
import com.mmg.manahub.core.data.remote.dto.ArchidektDeckDetailDto
import com.mmg.manahub.feature.communitydecks.data.remote.toCacheEntity
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Android (Room-backed) implementation of [CommunityDeckCache].
 *
 * Delegates to [CommunityDeckCacheDao] for persistence and uses the existing
 * [toCacheEntity] mapper to serialise the DTO into the Room entity. On read,
 * the stored JSON blob is deserialised back into an [ArchidektDeckDetailDto].
 *
 * [crashReporter] is DI'd (not a static `FirebaseCrashlytics.getInstance()` call) since this is a
 * data-layer class, not a ViewModel — mirrors `PuzzleRepositoryImpl`'s corrupt-row guard pattern
 * and keeps [getById]'s corrupt-blob path trivially unit-testable without `mockkStatic`.
 */
class CommunityDeckCacheImpl(
    private val cacheDao: CommunityDeckCacheDao,
    private val crashReporter: CrashReporter,
) : CommunityDeckCache {

    /** Lenient JSON for round-tripping the cached DTO blob. */
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    /**
     * Bug fix (post-review hardening): a corrupted/unparseable [entity.responseJson] blob (schema
     * drift, a truncated write, ...) used to make [json.decodeFromString] throw straight out of
     * this method. [CommunityDecksRepositoryImpl.getDeckById] calls [getById] a SECOND time from
     * its own catch block to attempt a stale-cache fallback — against the SAME corrupt row — which
     * threw again, uncaught, and crashed the caller's `viewModelScope.launch`. A corrupt row is now
     * treated as a cache miss (`null`) instead of a thrown exception, restoring the repository's
     * intended fresh-fetch/stale-fallback resilience.
     */
    override suspend fun getById(archidektId: Int): CachedDeckEntry? {
        val entity = cacheDao.getById(archidektId) ?: return null
        return try {
            val dto = json.decodeFromString(ArchidektDeckDetailDto.serializer(), entity.responseJson)
            CachedDeckEntry(dto = dto, cachedAt = entity.cachedAt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {
            recordCorruptRow(archidektId, e)
            null
        } catch (e: IllegalArgumentException) {
            // kotlinx.serialization also surfaces some malformed-input cases as IllegalArgumentException
            // (e.g. an invalid enum/polymorphic discriminant) rather than SerializationException.
            recordCorruptRow(archidektId, e)
            null
        }
    }

    private fun recordCorruptRow(archidektId: Int, e: Exception) {
        crashReporter.log("community_deck_cache_corrupt")
        crashReporter.setCustomKey("community_deck_archidekt_id", archidektId.toString())
        crashReporter.recordException(e)
    }

    override suspend fun insert(dto: ArchidektDeckDetailDto) {
        cacheDao.insert(dto.toCacheEntity())
    }
}
