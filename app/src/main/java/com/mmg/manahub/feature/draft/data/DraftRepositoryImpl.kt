package com.mmg.manahub.feature.draft.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonElement
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
import com.mmg.manahub.feature.draft.domain.model.MechanicKeyCard
import com.mmg.manahub.feature.draft.domain.model.SetDraftGuide
import com.mmg.manahub.feature.draft.domain.model.SetTierList
import com.mmg.manahub.feature.draft.domain.model.TierCard
import com.mmg.manahub.feature.draft.domain.model.TierGroup
import com.mmg.manahub.feature.draft.domain.repository.DraftRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
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

        /** Allowlist: set codes must be 2–6 lowercase ASCII letters only. */
        private val VALID_SET_CODE = Regex("^[a-z]{2,6}$")
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
                draftSetDao.replaceAll(entities)

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
                val safeCode = sanitizeSetCode(setCode)
                val localFile = guideFile(safeCode)
                val storedVersion = draftPrefs.getString(PREF_GUIDE_VERSION.format(safeCode), null)
                val remoteVersion = getRemoteGuideVersion(safeCode)

                val needsRefresh = !localFile.exists() ||
                    (remoteVersion != null && remoteVersion != storedVersion)

                if (needsRefresh) {
                    val json = cloudflareApi.getSetGuide(safeCode)
                    saveJsonToFile(json, localFile)
                    if (remoteVersion != null) {
                        draftPrefs.edit()
                            .putString(PREF_GUIDE_VERSION.format(safeCode), remoteVersion)
                            .apply()
                    }
                }

                if (!localFile.exists()) {
                    return@withContext DataResult.Error("Guide not available for $safeCode")
                }

                val jsonObject = gson.fromJson(localFile.readText(), JsonObject::class.java)
                DataResult.Success(parseGuide(safeCode, jsonObject))
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
                val safeCode = sanitizeSetCode(setCode)
                val localFile = tierListFile(safeCode)
                val storedVersion = draftPrefs.getString(PREF_TIER_VERSION.format(safeCode), null)
                val remoteVersion = getRemoteTierVersion(safeCode)

                val needsRefresh = !localFile.exists() ||
                    (remoteVersion != null && remoteVersion != storedVersion)

                if (needsRefresh) {
                    val json = cloudflareApi.getSetTierList(safeCode)
                    saveJsonToFile(json, localFile)
                    if (remoteVersion != null) {
                        draftPrefs.edit()
                            .putString(PREF_TIER_VERSION.format(safeCode), remoteVersion)
                            .apply()
                    }
                }

                if (!localFile.exists()) {
                    return@withContext DataResult.Error("Tier list not available for $safeCode")
                }

                val jsonObject = gson.fromJson(localFile.readText(), JsonObject::class.java)
                DataResult.Success(parseTierList(safeCode, jsonObject))
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

    /**
     * Sanitizes [setCode] to a safe, lowercase string matching [VALID_SET_CODE].
     * Throws [IllegalArgumentException] for any code that does not match, preventing
     * path-traversal attacks when the value is used as a directory component.
     */
    private fun sanitizeSetCode(setCode: String): String {
        val normalized = setCode.lowercase().trim()
        require(VALID_SET_CODE.matches(normalized)) { "Invalid set code: '$normalized'" }
        return normalized
    }

    /**
     * Returns (and creates if necessary) the per-set cache directory.
     * Throws [IOException] if the directory cannot be created — callers must not
     * swallow this, as it indicates the device is out of storage or has a permissions issue.
     *
     * @param setCode Already-sanitized (lowercase) set code.
     */
    private fun draftDir(setCode: String): File {
        val dir = File(context.filesDir, "draft/$setCode")
        if (!dir.exists() && !dir.mkdirs()) {
            throw IOException("Failed to create draft cache directory: ${dir.absolutePath}")
        }
        return dir
    }

    private fun guideFile(setCode: String): File =
        File(draftDir(setCode), "guide.json")

    private fun tierListFile(setCode: String): File =
        File(draftDir(setCode), "tier-list.json")

    /**
     * Writes [json] to [file] atomically: first writes to a sibling `.tmp` file,
     * then renames it into place. This prevents a partially-written file from being
     * read as valid JSON if the process is killed mid-write.
     */
    private fun saveJsonToFile(json: JsonObject, file: File) {
        val tmp = File(file.parent, "${file.name}.tmp")
        tmp.writeText(gson.toJson(json))
        if (!tmp.renameTo(file)) {
            // renameTo can fail across filesystems; fall back to a plain copy + delete.
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers — version lookup from Room cache
    // -------------------------------------------------------------------------

    /**
     * Returns the guide version stored in Room for [safeCode] (already lowercase), or null if not cached.
     * This avoids a network call just to check if the version changed.
     */
    private suspend fun getRemoteGuideVersion(safeCode: String): String? =
        draftSetDao.getSetByCode(safeCode)?.guideVersion

    private suspend fun getRemoteTierVersion(safeCode: String): String? =
        draftSetDao.getSetByCode(safeCode)?.tierListVersion

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
        val setName = metadata?.get("set_name").safeAsString()
        val lastUpdated = metadata?.get("last_updated").safeAsString()

        val overview = json.getAsJsonObject("set_overview")
        val summary = overview?.get("summary").safeAsString()

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

    /**
     * Parses a single mechanic object from the JSON array.
     *
     * The `key_examples` field has two legal shapes:
     * - **JsonObject** with optional `overperformers`/`underperformers` arrays of card objects.
     * - **JsonArray** of card objects (flat list; placed in [MechanicExamples.overperformers]).
     * - Absent or JsonNull → [MechanicGuide.keyExamples] is null.
     */
    private fun parseMechanic(obj: JsonObject): MechanicGuide {
        val keyExamplesElement = obj.get("key_examples")
        val examples: MechanicExamples? = when {
            keyExamplesElement == null || keyExamplesElement.isJsonNull -> null
            keyExamplesElement.isJsonObject -> {
                val ex = keyExamplesElement.asJsonObject
                MechanicExamples(
                    overperformers = ex.getAsJsonArray("overperformers")
                        ?.map { parseMechanicKeyCard(it.asJsonObject) } ?: emptyList(),
                    underperformers = ex.getAsJsonArray("underperformers")
                        ?.map { parseMechanicKeyCard(it.asJsonObject) } ?: emptyList(),
                )
            }
            keyExamplesElement.isJsonArray -> {
                MechanicExamples(
                    overperformers = keyExamplesElement.asJsonArray
                        .map { parseMechanicKeyCard(it.asJsonObject) },
                    underperformers = emptyList(),
                )
            }
            else -> null
        }

        return MechanicGuide(
            name = obj.get("name").safeAsString(),
            summary = obj.get("summary").safeAsString(),
            performance = obj.get("performance").safeAsString(),
            keyExamples = examples,
        )
    }

    /**
     * Parses a single card object inside `key_examples`.
     * Image fields default to empty string when the card omits `image_uris`.
     */
    private fun parseMechanicKeyCard(obj: JsonObject): MechanicKeyCard {
        val imageUris = obj.getAsJsonObject("image_uris")
        val colors = obj.getAsJsonArray("colors")?.map { it.asString } ?: emptyList()
        return MechanicKeyCard(
            name = obj.get("name").safeAsString(),
            scryfallId = obj.get("id").safeAsString(),
            artCropUri = imageUris?.get("art_crop").safeAsString(),
            imageNormalUri = imageUris?.get("normal").safeAsString(),
            note = obj.get("note").safeAsString(),
            tierRating = obj.get("tier_rating").safeAsString(),
            pickOrderRank = obj.get("pick_order_rank").safeAsInt(),
            color = obj.get("color").safeAsString(),
            rarity = obj.get("rarity").safeAsString(),
            colors = colors,
            typeLine = obj.get("type_line").safeAsString(),
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
            colors = obj.get("colors").safeAsString(),
            name = obj.get("name").safeAsString(),
            tier = obj.get("tier").safeAsString(),
            strategy = obj.get("strategy").safeAsString(),
            difficulty = obj.get("difficulty").safeAsString(),
            keyCards = keyCards,
        )
    }

    private fun parseArchetypeKeyCard(obj: JsonObject): ArchetypeKeyCard {
        val imageUris = obj.getAsJsonObject("image_uris")
        val colors = obj.getAsJsonArray("colors")
            ?.map { it.asString } ?: emptyList()
        return ArchetypeKeyCard(
            name = obj.get("name").safeAsString(),
            scryfallId = obj.get("id").safeAsString(),
            colors = colors,
            typeLine = obj.get("type_line").safeAsString(),
            artCropUri = imageUris?.get("art_crop").safeAsString(),
            imageNormalUri = imageUris?.get("normal").safeAsString(),
            rarity = obj.get("rarity").safeAsString(),
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
        val setName = metadata?.get("set_name").safeAsString()
        val lastUpdated = metadata?.get("last_updated").safeAsString()
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
        val tier = obj.get("tier_label").safeAsString()
        val label = obj.get("priority").safeAsString()
        val description = obj.get("description").safeAsString()
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
            name = obj.get("name").safeAsString(),
            scryfallId = obj.get("id").safeAsString(),
            color = obj.get("color").safeAsString(colors.joinToString("")),
            colors = colors,
            rarity = obj.get("rarity").safeAsString(),
            pickOrderRank = obj.get("pick_order_rank").safeAsInt(),
            tierRating = obj.get("tier_rating").safeAsString(),
            note = obj.get("note").safeAsString(),
            artCropUri = imageUris?.get("art_crop").safeAsString(),
            imageNormalUri = imageUris?.get("normal").safeAsString(),
            typeLine = obj.get("type_line").safeAsString(),
        )
    }

    // -------------------------------------------------------------------------
    // Null-safe JsonElement extension functions — guard against JsonNull values
    // -------------------------------------------------------------------------

    /**
     * Returns [JsonElement.asString] or [default] if the element is null or [com.google.gson.JsonNull].
     * Using `?.asString` alone does NOT protect against JsonNull — Gson throws
     * [UnsupportedOperationException] when `asString` is called on a JsonNull element.
     */
    private fun JsonElement?.safeAsString(default: String = ""): String =
        if (this == null || isJsonNull) default else asString

    /**
     * Returns [JsonElement.asInt] or [default] if the element is null or [com.google.gson.JsonNull].
     */
    private fun JsonElement?.safeAsInt(default: Int = 0): Int =
        if (this == null || isJsonNull) default else asInt
}
