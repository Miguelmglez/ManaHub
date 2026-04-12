package com.mmg.manahub.feature.draft.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.remote.ScryfallApi
import com.mmg.manahub.core.data.remote.mapper.toDomain
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.data.local.DraftSetDao
import com.mmg.manahub.feature.draft.data.remote.YouTubeApi
import com.mmg.manahub.feature.draft.data.remote.toDomain
import com.mmg.manahub.feature.draft.data.remote.toEntity
import com.mmg.manahub.feature.draft.domain.model.ArchetypeGuide
import com.mmg.manahub.feature.draft.domain.model.DraftSet
import com.mmg.manahub.feature.draft.domain.model.DraftVideo
import com.mmg.manahub.feature.draft.domain.model.MechanicGuide
import com.mmg.manahub.feature.draft.domain.model.SetDraftGuide
import com.mmg.manahub.feature.draft.domain.model.SetTierList
import com.mmg.manahub.feature.draft.domain.model.TierCard
import com.mmg.manahub.feature.draft.domain.model.TierGroup
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DraftRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scryfallApi: ScryfallApi,
    private val youTubeApi: YouTubeApi,
    private val draftSetDao: DraftSetDao,
    private val gson: Gson,
) : DraftRepository {

    companion object {
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val VIDEO_CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour
        private val DRAFTABLE_TYPES = setOf("expansion", "draft_innovation", "core", "masters")
        private const val MIN_RELEASE_DATE = "2018-01-01"
    }

    private val videoCache = ConcurrentHashMap<String, Pair<Long, List<DraftVideo>>>()

    override suspend fun getDraftableSets(forceRefresh: Boolean): DataResult<List<DraftSet>> {
        return withContext(Dispatchers.IO) {
            try {
                val cachedTime = draftSetDao.getLastCachedTime()
                val isCacheFresh = cachedTime != null &&
                    (System.currentTimeMillis() - cachedTime) < CACHE_DURATION_MS

                if (!forceRefresh && isCacheFresh) {
                    val cached = draftSetDao.getAllSetsSnapshot()
                    if (cached.isNotEmpty()) {
                        val availableCodes = getAvailableDraftSetCodes()
                        val filtered = cached.filter { it.code.lowercase() in availableCodes }
                        return@withContext DataResult.Success(filtered.map { it.toDomain() })
                    }
                }

                val response = scryfallApi.getSets()
                val availableCodes = getAvailableDraftSetCodes()
                val filtered = response.data
                    .filter { it.setType in DRAFTABLE_TYPES }
                    .filter { (it.releasedAt ?: "") >= MIN_RELEASE_DATE }
                    .filter { !it.name.contains("Commander", ignoreCase = true) }
                    .filter { !it.digital }
                    .filter { it.code.lowercase() in availableCodes }
                    .sortedByDescending { it.releasedAt }

                val entities = filtered.map { it.toEntity() }
                draftSetDao.deleteAll()
                draftSetDao.insertAll(entities)

                DataResult.Success(entities.map { it.toDomain() })
            } catch (e: Exception) {
                val cached = draftSetDao.getAllSetsSnapshot()
                if (cached.isNotEmpty()) {
                    val availableCodes = getAvailableDraftSetCodes()
                    val filtered = cached.filter { it.code.lowercase() in availableCodes }
                    DataResult.Success(filtered.map { it.toDomain() }, isStale = true)
                } else {
                    DataResult.Error(e.message ?: "Failed to load sets")
                }
            }
        }
    }

    override suspend fun getSetGuide(setCode: String): DataResult<SetDraftGuide> {
        return withContext(Dispatchers.IO) {
            try {
                val json = context.assets.open("draft_guides/$setCode"+"_draft_guide_en.json")
                    .bufferedReader().use { it.readText() }
                val map = gson.fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)
                val guide = parseGuide(map)
                DataResult.Success(guide)
            } catch (_: Exception) {
                DataResult.Error("Guide not available")
            }
        }
    }

    override suspend fun getSetTierList(setCode: String): DataResult<SetTierList> {
        return withContext(Dispatchers.IO) {
            try {
                val json = context.assets.open("tier_lists/$setCode"+"_tier_list_en.json")
                    .bufferedReader().use { it.readText() }
                val map = gson.fromJson<Map<String, Any>>(json, object : TypeToken<Map<String, Any>>() {}.type)
                val tierList = parseTierList(map)
                DataResult.Success(tierList)
            } catch (_: Exception) {
                DataResult.Error("Tier list not available")
            }
        }
    }

    override suspend fun getSetCards(setCode: String, page: Int): DataResult<List<Card>> {
        return withContext(Dispatchers.IO) {
            try {
                val result = scryfallApi.searchCards(
                    query = "set:$setCode lang:en",
                    order = "set",
                    unique = "cards",
                    page = page,
                )
                DataResult.Success(result.data.toDomain())
            } catch (e: Exception) {
                DataResult.Error(e.message ?: "Failed to load cards")
            }
        }
    }

    override suspend fun getSetVideos(setCode: String, setName: String): DataResult<List<DraftVideo>> {
        return withContext(Dispatchers.IO) {
            val cacheKey = setCode
            val cached = videoCache[cacheKey]
            if (cached != null && (System.currentTimeMillis() - cached.first) < VIDEO_CACHE_DURATION_MS) {
                return@withContext DataResult.Success(cached.second)
            }

            if (BuildConfig.YOUTUBE_API_KEY.isBlank()) {
                return@withContext DataResult.Error("YouTube API key not configured")
            }

            try {
                val query = "$setName MTG draft guide"
                val enResults = runCatching {
                    youTubeApi.searchVideos(query = query, language = "en")
                }.getOrNull()?.items ?: emptyList()

                val esResults = runCatching {
                    youTubeApi.searchVideos(query = query, language = "es")
                }.getOrNull()?.items ?: emptyList()

                val seenIds = mutableSetOf<String>()
                val combined = mutableListOf<DraftVideo>()
                for (item in enResults + esResults) {
                    if (seenIds.add(item.id.videoId)) {
                        combined.add(item.toDomain())
                    }
                }

                videoCache[cacheKey] = System.currentTimeMillis() to combined
                DataResult.Success(combined)
            } catch (e: Exception) {
                DataResult.Error(e.message ?: "Failed to load videos")
            }
        }
    }

    override suspend fun resolveCardId(cardName: String, setCode: String): DataResult<String> {
        return withContext(Dispatchers.IO) {
            try {
                val card = scryfallApi.getCardByName(name = cardName, set = setCode)
                DataResult.Success(card.id)
            } catch (_: Exception) {
                try {
                    val card = scryfallApi.getCardByName(name = cardName)
                    DataResult.Success(card.id)
                } catch (e: Exception) {
                    DataResult.Error(e.message ?: "Card not found")
                }
            }
        }
    }

    override suspend fun getCardByName(name: String, setCode: String): DataResult<Card> {
        return withContext(Dispatchers.IO) {
            try {
                val card = scryfallApi.getCardByName(name = name, set = setCode)
                DataResult.Success(card.toDomain())
            } catch (_: Exception) {
                try {
                    val card = scryfallApi.getCardByName(name = name)
                    DataResult.Success(card.toDomain())
                } catch (e: Exception) {
                    DataResult.Error(e.message ?: "Card not found")
                }
            }
        }
    }

    private fun getAvailableDraftSetCodes(): Set<String> {
        return try {
            (context.assets.list("draft_guides") ?: emptyArray())
                .map { it.substringBefore("_draft_guide").lowercase() }
                .toSet()
        } catch (_: Exception) { emptySet() }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseGuide(map: Map<String, Any>): SetDraftGuide {
        val mechanicsList = (map["mechanics"] as? List<Map<String, Any>>) ?: emptyList()
        val archetypesList = (map["archetypes"] as? List<Map<String, Any>>) ?: emptyList()
        val topCommons = (map["top_commons"] as? Map<String, List<String>>) ?: emptyMap()
        val topUncommons = (map["top_uncommons"] as? Map<String, List<String>>) ?: emptyMap()
        val generalTips = (map["general_tips"] as? List<String>) ?: emptyList()

        return SetDraftGuide(
            setCode = map["set_code"] as? String ?: "",
            setName = map["set_name"] as? String ?: "",
            lastUpdated = map["last_updated"] as? String ?: "",
            overview = map["overview"] as? String ?: "",
            mechanics = mechanicsList.map { m ->
                MechanicGuide(
                    name = m["name"] as? String ?: "",
                    description = m["description"] as? String ?: "",
                    draftTip = m["draft_tip"] as? String ?: "",
                )
            },
            archetypes = archetypesList.map { a ->
                ArchetypeGuide(
                    colors = a["colors"] as? String ?: "",
                    name = a["name"] as? String ?: "",
                    tier = a["tier"] as? String ?: "",
                    description = a["description"] as? String ?: "",
                    keyCommons = (a["key_commons"] as? List<String>) ?: emptyList(),
                    keyUncommons = (a["key_uncommons"] as? List<String>) ?: emptyList(),
                    strategy = a["strategy"] as? String ?: "",
                )
            },
            topCommons = topCommons,
            topUncommons = topUncommons,
            generalTips = generalTips,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseTierList(map: Map<String, Any>): SetTierList {
        val tiersList = (map["tiers"] as? List<Map<String, Any>>) ?: emptyList()

        return SetTierList(
            setCode = map["set_code"] as? String ?: "",
            setName = map["set_name"] as? String ?: "",
            lastUpdated = map["last_updated"] as? String ?: "",
            tiers = tiersList.map { t ->
                val cardsList = (t["cards"] as? List<Map<String, Any>>) ?: emptyList()
                TierGroup(
                    tier = t["tier"] as? String ?: "",
                    label = t["label"] as? String ?: "",
                    description = t["description"] as? String ?: "",
                    cards = cardsList.map { c ->
                        TierCard(
                            name = c["name"] as? String ?: "",
                            color = c["color"] as? String ?: "",
                            rarity = c["rarity"] as? String ?: "",
                            reason = c["reason"] as? String ?: "",
                        )
                    },
                )
            },
        )
    }
}
