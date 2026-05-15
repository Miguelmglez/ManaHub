package com.mmg.manahub.feature.draft.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mmg.manahub.BuildConfig
import com.mmg.manahub.core.data.remote.ScryfallApi
import com.mmg.manahub.core.data.remote.mapper.toDomain
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.feature.draft.data.local.DraftSetDao
import com.mmg.manahub.feature.draft.data.remote.CloudflareContentApi
import com.mmg.manahub.feature.draft.data.remote.YouTubeApi
import com.mmg.manahub.feature.draft.data.remote.toDomain
import com.mmg.manahub.feature.draft.data.remote.toEntity
import com.mmg.manahub.feature.draft.domain.model.ArchetypeGuide
import com.mmg.manahub.feature.draft.domain.model.ArchetypeKeyCard
import com.mmg.manahub.feature.draft.domain.model.DraftSet
import com.mmg.manahub.feature.draft.domain.model.DraftVideo
import com.mmg.manahub.feature.draft.domain.model.MechanicExamples
import com.mmg.manahub.feature.draft.domain.model.MechanicGuide
import com.mmg.manahub.feature.draft.domain.model.SetDraftGuide
import com.mmg.manahub.feature.draft.domain.model.SetTierList
import com.mmg.manahub.feature.draft.domain.model.TierCard
import com.mmg.manahub.feature.draft.domain.model.TierGroup
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Implementation of [DraftRepository] that fetches draft content from the Cloudflare Worker
 * and caches it locally in [filesDir] (JSON files) and Room (set metadata).
 *
 * Cache strategy:
 * - **Set list**: Room cache with 24h TTL. Falls back to stale Room data on network error.
 * - **Guide / Tier-list**: Per-set JSON files in `filesDir/draft/{setCode}/`.
 *   Invalidated when the content version stored in SharedPreferences differs from the
 *   version in the sets-index. No automatic TTL — content only refreshes when the
 *   Worker publishes a new version.
 *
 * No assets/ reads. If Cloudflare is unreachable and no local file exists, an error is returned.
 */
@Singleton
class DraftRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scryfallApi: ScryfallApi,
    private val youTubeApi: YouTubeApi,
    private val cloudflareApi: CloudflareContentApi,
    private val draftSetDao: DraftSetDao,
    private val gson: Gson,
    @Named("draft_prefs") private val draftPrefs: SharedPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DraftRepository {

    companion object {
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val VIDEO_CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour
        private const val PREF_GUIDE_VERSION = "pref_draft_%s_guide_version"
        private const val PREF_TIER_VERSION = "pref_draft_%s_tier_version"
    }

    private val videoCache = ConcurrentHashMap<String, Pair<Long, List<DraftVideo>>>()

    // -------------------------------------------------------------------------
    // getDraftableSets — Cloudflare sets-index.json with Room cache
    // -------------------------------------------------------------------------

    override suspend fun getDraftableSets(forceRefresh: Boolean): DataResult<List<DraftSet>> {
        return withContext(ioDispatcher) {
            try {
                val cachedTime = draftSetDao.getLastCachedTime()
                val isCacheFresh = cachedTime != null &&
                    (System.currentTimeMillis() - cachedTime) < CACHE_DURATION_MS

                if (!forceRefresh && isCacheFresh) {
                    val cached = draftSetDao.getAllSetsSnapshot()
                    if (cached.isNotEmpty()) {
                        return@withContext DataResult.Success(cached.map { it.toDomain() })
                    }
                }

                val response = cloudflareApi.getSetsIndex()
                val entities = response.sets.map { it.toEntity() }
                draftSetDao.deleteAll()
                draftSetDao.insertAll(entities)

                DataResult.Success(entities.map { it.toDomain() })
            } catch (e: Exception) {
                val cached = draftSetDao.getAllSetsSnapshot()
                if (cached.isNotEmpty()) {
                    DataResult.Success(cached.map { it.toDomain() }, isStale = true)
                } else {
                    DataResult.Error(e.message ?: "Failed to load sets")
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // getSetGuide — Cloudflare guide.json with versioned local file cache
    // -------------------------------------------------------------------------

    override suspend fun getSetGuide(setCode: String): DataResult<SetDraftGuide> {
        return withContext(ioDispatcher) {
            try {
                val localFile = guideFile(setCode)
                val storedVersion = draftPrefs.getString(PREF_GUIDE_VERSION.format(setCode), null)
                val remoteVersion = getRemoteGuideVersion(setCode)

                val needsRefresh = !localFile.exists() ||
                    (remoteVersion != null && remoteVersion != storedVersion)

                if (needsRefresh) {
                    val json = cloudflareApi.getSetGuide(setCode.lowercase())
                    saveJsonToFile(json, localFile)
                    if (remoteVersion != null) {
                        draftPrefs.edit()
                            .putString(PREF_GUIDE_VERSION.format(setCode), remoteVersion)
                            .apply()
                    }
                }

                if (!localFile.exists()) {
                    return@withContext DataResult.Error("Guide not available for $setCode")
                }

                val jsonObject = gson.fromJson(localFile.readText(), JsonObject::class.java)
                DataResult.Success(parseGuide(setCode, jsonObject))
            } catch (e: Exception) {
                DataResult.Error(e.message ?: "Failed to load guide for $setCode")
            }
        }
    }

    // -------------------------------------------------------------------------
    // getSetTierList — Cloudflare tier-list.json with versioned local file cache
    // -------------------------------------------------------------------------

    override suspend fun getSetTierList(setCode: String): DataResult<SetTierList> {
        return withContext(ioDispatcher) {
            try {
                val localFile = tierListFile(setCode)
                val storedVersion = draftPrefs.getString(PREF_TIER_VERSION.format(setCode), null)
                val remoteVersion = getRemoteTierVersion(setCode)

                val needsRefresh = !localFile.exists() ||
                    (remoteVersion != null && remoteVersion != storedVersion)

                if (needsRefresh) {
                    val json = cloudflareApi.getSetTierList(setCode.lowercase())
                    saveJsonToFile(json, localFile)
                    if (remoteVersion != null) {
                        draftPrefs.edit()
                            .putString(PREF_TIER_VERSION.format(setCode), remoteVersion)
                            .apply()
                    }
                }

                if (!localFile.exists()) {
                    return@withContext DataResult.Error("Tier list not available for $setCode")
                }

                val jsonObject = gson.fromJson(localFile.readText(), JsonObject::class.java)
                DataResult.Success(parseTierList(setCode, jsonObject))
            } catch (e: Exception) {
                DataResult.Error(e.message ?: "Failed to load tier list for $setCode")
            }
        }
    }

    // -------------------------------------------------------------------------
    // getSetCards — Scryfall (unchanged)
    // -------------------------------------------------------------------------

    override suspend fun getSetCards(setCode: String, page: Int): DataResult<List<Card>> {
        return withContext(ioDispatcher) {
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

    // -------------------------------------------------------------------------
    // getSetVideos — YouTube API with in-memory cache (unchanged)
    // -------------------------------------------------------------------------

    override suspend fun getSetVideos(setCode: String, setName: String): DataResult<List<DraftVideo>> {
        return withContext(ioDispatcher) {
            val cached = videoCache[setCode]
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

                videoCache[setCode] = System.currentTimeMillis() to combined
                DataResult.Success(combined)
            } catch (e: Exception) {
                DataResult.Error(e.message ?: "Failed to load videos")
            }
        }
    }

    // -------------------------------------------------------------------------
    // resolveCardId / getCardByName — Scryfall (unchanged)
    // -------------------------------------------------------------------------

    override suspend fun resolveCardId(cardName: String, setCode: String): DataResult<String> {
        return withContext(ioDispatcher) {
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
        return withContext(ioDispatcher) {
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

    // -------------------------------------------------------------------------
    // Private helpers — file paths
    // -------------------------------------------------------------------------

    private fun draftDir(setCode: String): File {
        return File(context.filesDir, "draft/${setCode.lowercase()}").also { it.mkdirs() }
    }

    private fun guideFile(setCode: String): File =
        File(draftDir(setCode), "guide.json")

    private fun tierListFile(setCode: String): File =
        File(draftDir(setCode), "tier-list.json")

    private fun saveJsonToFile(json: JsonObject, file: File) {
        file.writeText(gson.toJson(json))
    }

    // -------------------------------------------------------------------------
    // Private helpers — version lookup from Room cache
    // -------------------------------------------------------------------------

    /**
     * Returns the guide version stored in Room for [setCode], or null if not cached.
     * This avoids a network call just to check if the version changed.
     */
    private suspend fun getRemoteGuideVersion(setCode: String): String? {
        return draftSetDao.getSetByCode(setCode.lowercase())?.guideVersion
    }

    private suspend fun getRemoteTierVersion(setCode: String): String? {
        return draftSetDao.getSetByCode(setCode.lowercase())?.tierListVersion
    }

    // -------------------------------------------------------------------------
    // Private helpers — JSON parsing (guide)
    // -------------------------------------------------------------------------

    /**
     * Parses the new guide.json format from Cloudflare into a [SetDraftGuide] domain model.
     *
     * JSON structure:
     * ```
     * {
     *   "metadata": { "set_name", "set_code", "last_updated" },
     *   "set_overview": { "summary", "color_ranking", "color_notes", "key_gameplay_notes" },
     *   "mechanics": [ { "name", "summary", "performance", "key_examples": { "overperformers", "underperformers" } } ],
     *   "archetype_tier_list": { "tier_1": [...], "tier_2": [...], ... }
     * }
     * ```
     */
    private fun parseGuide(setCode: String, json: JsonObject): SetDraftGuide {
        val metadata = json.getAsJsonObject("metadata")
        val setName = metadata?.get("set_name")?.asString ?: ""
        val lastUpdated = metadata?.get("last_updated")?.asString ?: ""

        val overview = json.getAsJsonObject("set_overview")
        val summary = overview?.get("summary")?.asString ?: ""

        val colorRanking = overview?.getAsJsonArray("color_ranking")
            ?.map { it.asString } ?: emptyList()

        val colorNotes = overview?.getAsJsonObject("color_notes")
            ?.entrySet()
            ?.associate { (k, v) -> k to v.asString } ?: emptyMap()

        val keyGameplayNotes = overview?.getAsJsonArray("key_gameplay_notes")
            ?.map { it.asString } ?: emptyList()

        val mechanics = json.getAsJsonArray("mechanics")
            ?.map { parseMechanic(it.asJsonObject) } ?: emptyList()

        val archetypes = parseArchetypeTierList(json.getAsJsonObject("archetype_tier_list"))

        return SetDraftGuide(
            setCode = setCode.uppercase(),
            setName = setName,
            lastUpdated = lastUpdated,
            summary = summary,
            colorRanking = colorRanking,
            colorNotes = colorNotes,
            keyGameplayNotes = keyGameplayNotes,
            mechanics = mechanics,
            archetypes = archetypes,
        )
    }

    private fun parseMechanic(obj: JsonObject): MechanicGuide {
        val keyExamplesObj = obj.getAsJsonObject("key_examples")
        val examples = if (keyExamplesObj != null) {
            MechanicExamples(
                overperformers = keyExamplesObj.getAsJsonArray("overperformers")
                    ?.map { it.asString } ?: emptyList(),
                underperformers = keyExamplesObj.getAsJsonArray("underperformers")
                    ?.map { it.asString } ?: emptyList(),
            )
        } else null

        return MechanicGuide(
            name = obj.get("name")?.asString ?: "",
            summary = obj.get("summary")?.asString ?: "",
            performance = obj.get("performance")?.asString ?: "",
            keyExamples = examples,
        )
    }

    /**
     * Flattens archetype_tier_list (keyed tier_1..tier_5) into a single ordered list.
     * Tier key order: tier_1 → tier_5.
     */
    private fun parseArchetypeTierList(obj: JsonObject?): List<ArchetypeGuide> {
        if (obj == null) return emptyList()
        val result = mutableListOf<ArchetypeGuide>()
        val tierKeys = listOf("tier_1", "tier_2", "tier_3", "tier_4", "tier_5")
        for (key in tierKeys) {
            obj.getAsJsonArray(key)?.forEach { element ->
                result.add(parseArchetype(element.asJsonObject))
            }
        }
        return result
    }

    private fun parseArchetype(obj: JsonObject): ArchetypeGuide {
        val keyCards = obj.getAsJsonArray("key_cards")
            ?.map { parseArchetypeKeyCard(it.asJsonObject) } ?: emptyList()

        return ArchetypeGuide(
            colors = obj.get("colors")?.asString ?: "",
            name = obj.get("name")?.asString ?: "",
            tier = obj.get("tier")?.asString ?: "",
            strategy = obj.get("strategy")?.asString ?: "",
            difficulty = obj.get("difficulty")?.asString ?: "",
            keyCards = keyCards,
        )
    }

    private fun parseArchetypeKeyCard(obj: JsonObject): ArchetypeKeyCard {
        val imageUris = obj.getAsJsonObject("image_uris")
        val colors = obj.getAsJsonArray("colors")
            ?.map { it.asString } ?: emptyList()
        return ArchetypeKeyCard(
            name = obj.get("name")?.asString ?: "",
            scryfallId = obj.get("id")?.asString ?: "",
            colors = colors,
            typeLine = obj.get("type_line")?.asString ?: "",
            artCropUri = imageUris?.get("art_crop")?.asString ?: "",
            imageNormalUri = imageUris?.get("normal")?.asString ?: "",
            rarity = obj.get("rarity")?.asString ?: "",
        )
    }

    // -------------------------------------------------------------------------
    // Private helpers — JSON parsing (tier list)
    // -------------------------------------------------------------------------

    /**
     * Parses the new tier-list.json format from Cloudflare into a [SetTierList] domain model.
     *
     * JSON structure:
     * ```
     * {
     *   "metadata": { "set_name", "set_code", "last_updated", "tier_key": { "S": "...", ... } },
     *   "categories": [
     *     { "priority", "description", "tier_label", "cards": [ { card fields } ] }
     *   ]
     * }
     * ```
     */
    private fun parseTierList(setCode: String, json: JsonObject): SetTierList {
        val metadata = json.getAsJsonObject("metadata")
        val setName = metadata?.get("set_name")?.asString ?: ""
        val lastUpdated = metadata?.get("last_updated")?.asString ?: ""
        val tierKey = metadata?.getAsJsonObject("tier_key")
            ?.entrySet()
            ?.associate { (k, v) -> k to v.asString } ?: emptyMap()

        val tiers = json.getAsJsonArray("categories")
            ?.map { parseTierGroup(it.asJsonObject) } ?: emptyList()

        return SetTierList(
            setCode = setCode.uppercase(),
            setName = setName,
            lastUpdated = lastUpdated,
            tierKey = tierKey,
            tiers = tiers,
        )
    }

    private fun parseTierGroup(obj: JsonObject): TierGroup {
        val tier = obj.get("tier_label")?.asString ?: ""
        val label = obj.get("priority")?.asString ?: ""
        val description = obj.get("description")?.asString ?: ""
        val cards = obj.getAsJsonArray("cards")
            ?.map { parseTierCard(it.asJsonObject) } ?: emptyList()

        return TierGroup(
            tier = tier,
            label = label,
            description = description,
            cards = cards,
        )
    }

    private fun parseTierCard(obj: JsonObject): TierCard {
        val imageUris = obj.getAsJsonObject("image_uris")
        val colors = obj.getAsJsonArray("colors")
            ?.map { it.asString } ?: emptyList()
        return TierCard(
            name = obj.get("name")?.asString ?: "",
            scryfallId = obj.get("id")?.asString ?: "",
            color = obj.get("color")?.asString ?: colors.joinToString(""),
            colors = colors,
            rarity = obj.get("rarity")?.asString ?: "",
            pickOrderRank = obj.get("pick_order_rank")?.asInt ?: 0,
            tierRating = obj.get("tier_rating")?.asString ?: "",
            note = obj.get("note")?.asString ?: "",
            artCropUri = imageUris?.get("art_crop")?.asString ?: "",
            imageNormalUri = imageUris?.get("normal")?.asString ?: "",
            typeLine = obj.get("type_line")?.asString ?: "",
        )
    }
}
