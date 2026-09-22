package com.mmg.manahub.feature.draft.data
// COMMENTS_REVIEWED: 2026-09-22

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mmg.manahub.core.data.remote.ScryfallClient
import com.mmg.manahub.core.data.remote.mapper.toDomain
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DraftSet
import com.mmg.manahub.core.data.network.ScryfallRequestQueue
import com.mmg.manahub.feature.draft.data.DraftRepositoryImpl.Companion.VALID_SET_CODE
import com.mmg.manahub.core.data.local.dao.DraftSetDao
import com.mmg.manahub.core.data.remote.CloudflareContentClient
import com.mmg.manahub.feature.draft.data.remote.toDomain
import com.mmg.manahub.feature.draft.data.remote.toEntity
import com.mmg.manahub.core.model.ArchetypeGuide
import com.mmg.manahub.core.model.ArchetypeKeyCard
import com.mmg.manahub.core.model.DraftCardStats
import com.mmg.manahub.core.model.MechanicExamples
import com.mmg.manahub.core.model.MechanicGuide
import com.mmg.manahub.core.model.MechanicKeyCard
import com.mmg.manahub.core.model.SetDraftGuide
import com.mmg.manahub.core.model.SetTierList
import com.mmg.manahub.core.model.TierCard
import com.mmg.manahub.core.model.TierGroup
import com.mmg.manahub.core.domain.repository.DraftRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

// Guide/tier-list JSON is file-cached per set and refreshed only when the sets-index content version changes (no TTL)
class DraftRepositoryImpl(
    private val context: Context,
    private val scryfallApi: ScryfallClient,
    private val scryfallQueue: ScryfallRequestQueue,
    private val cloudflareClient: CloudflareContentClient,
    private val draftSetDao: DraftSetDao,
    private val gson: Gson,
    private val draftPrefs: SharedPreferences,
    private val ioDispatcher: CoroutineDispatcher,
) : DraftRepository {

    companion object {
        private const val CACHE_DURATION_MS = 24 * 60 * 60 * 1000L
        private const val PREF_GUIDE_VERSION = "pref_draft_%s_guide_version"
        private const val PREF_TIER_VERSION = "pref_draft_%s_tier_version"

        private val VALID_SET_CODE = Regex("^[a-z0-9]{2,6}$")
    }

    private val guideMutexes = ConcurrentHashMap<String, Mutex>()
    private val tierMutexes = ConcurrentHashMap<String, Mutex>()

    private fun guideMutex(code: String) = guideMutexes.computeIfAbsent(code) { Mutex() }
    private fun tierMutex(code: String) = tierMutexes.computeIfAbsent(code) { Mutex() }

    // Parsed models keyed by set code; only read/written under that set's mutex
    private val parsedGuides = ConcurrentHashMap<String, VersionedModel<SetDraftGuide>>()
    private val parsedTierLists = ConcurrentHashMap<String, VersionedModel<SetTierList>>()

    private data class VersionedModel<T>(val version: String?, val model: T)

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

                val response = cloudflareClient.getSetsIndex()
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

    override suspend fun getSetGuide(setCode: String): DataResult<SetDraftGuide> {
        return withContext(ioDispatcher) {
            try {
                val safeCode = sanitizeSetCode(setCode)
                guideMutex(safeCode).withLock {
                    val localFile = guideFile(safeCode)
                    val storedVersion = draftPrefs.getString(PREF_GUIDE_VERSION.format(safeCode), null)
                    val remoteVersion = getRemoteGuideVersion(safeCode)

                    val needsRefresh = !localFile.exists() ||
                        (remoteVersion != null && remoteVersion != storedVersion)

                    if (needsRefresh) {
                        parsedGuides.remove(safeCode)
                        val jsonString = cloudflareClient.getSetGuide(safeCode)
                        saveJsonToFile(jsonString, localFile)
                        if (remoteVersion != null) {
                            draftPrefs.edit()
                                .putString(PREF_GUIDE_VERSION.format(safeCode), remoteVersion)
                                .apply()
                        }
                    }

                    if (!localFile.exists()) {
                        DataResult.Error("Guide not available for $safeCode")
                    } else {
                        val currentVersion = if (needsRefresh) remoteVersion ?: storedVersion else storedVersion
                        val cached = parsedGuides[safeCode]?.takeIf { it.version == currentVersion }
                        val model = cached?.model ?: parseGuide(
                            safeCode,
                            gson.fromJson(localFile.readText(), JsonObject::class.java),
                        ).also { parsedGuides[safeCode] = VersionedModel(currentVersion, it) }
                        DataResult.Success(model)
                    }
                }
            } catch (e: Exception) {
                DataResult.Error(e.message ?: "Failed to load guide for $setCode")
            }
        }
    }

    override suspend fun getSetTierList(setCode: String): DataResult<SetTierList> {
        return withContext(ioDispatcher) {
            try {
                val safeCode = sanitizeSetCode(setCode)
                tierMutex(safeCode).withLock {
                    val localFile = tierListFile(safeCode)
                    val storedVersion = draftPrefs.getString(PREF_TIER_VERSION.format(safeCode), null)
                    val remoteVersion = getRemoteTierVersion(safeCode)

                    val needsRefresh = !localFile.exists() ||
                        (remoteVersion != null && remoteVersion != storedVersion)

                    if (needsRefresh) {
                        parsedTierLists.remove(safeCode)
                        val jsonString = cloudflareClient.getSetTierList(safeCode)
                        saveJsonToFile(jsonString, localFile)
                        if (remoteVersion != null) {
                            draftPrefs.edit()
                                .putString(PREF_TIER_VERSION.format(safeCode), remoteVersion)
                                .apply()
                        }
                    }

                    if (!localFile.exists()) {
                        DataResult.Error("Tier list not available for $safeCode")
                    } else {
                        val currentVersion = if (needsRefresh) remoteVersion ?: storedVersion else storedVersion
                        val cached = parsedTierLists[safeCode]?.takeIf { it.version == currentVersion }
                        val model = cached?.model ?: parseTierList(
                            safeCode,
                            gson.fromJson(localFile.readText(), JsonObject::class.java),
                        ).also { parsedTierLists[safeCode] = VersionedModel(currentVersion, it) }
                        DataResult.Success(model)
                    }
                }
            } catch (e: Exception) {
                DataResult.Error(e.message ?: "Failed to load tier list for $setCode")
            }
        }
    }

    override suspend fun getSetCards(setCode: String, page: Int): DataResult<List<Card>> {
        return withContext(ioDispatcher) {
            try {
                val result = scryfallQueue.execute {
                    scryfallApi.searchCards(
                        query = "set:$setCode lang:en",
                        order = "set",
                        unique = "cards",
                        page = page,
                    )
                }
                DataResult.Success(result.data.toDomain())
            } catch (e: Exception) {
                DataResult.Error(e.message ?: "Failed to load cards")
            }
        }
    }

    override suspend fun getSetCardsPage(
        setCode: String,
        page: Int,
        extraPoolSets: List<String>,
    ): DataResult<Pair<List<Card>, Boolean>> {
        return withContext(ioDispatcher) {
            try {
                val poolQuery = buildPoolQuery(setCode, extraPoolSets)
                val result = scryfallQueue.execute {
                    scryfallApi.searchCards(
                        query = poolQuery,
                        order = "set",
                        unique = "cards",
                        page = page,
                    )
                }
                DataResult.Success(result.data.toDomain() to result.hasMore)
            } catch (first: Exception) {
                // A bare 304 arrives when the disk-cached body was evicted; retry once uncached
                try {
                    val poolQuery = buildPoolQuery(setCode, extraPoolSets)
                    val result = scryfallQueue.execute {
                        scryfallApi.searchCardsNoCache(
                            query = poolQuery,
                            order = "set",
                            unique = "cards",
                            page = page,
                        )
                    }
                    DataResult.Success(result.data.toDomain() to result.hasMore)
                } catch (e: Exception) {
                    DataResult.Error(e.message ?: "Failed to load cards for $setCode page $page")
                }
            }
        }
    }

    // Extra set codes are re-sanitized (defense in depth): they are interpolated straight into a Scryfall query
    private fun buildPoolQuery(setCode: String, extraPoolSets: List<String>): String {
        val safeSetCode = sanitizeSetCode(setCode)
        val safeExtras = extraPoolSets.mapNotNull { code ->
            runCatching { sanitizeSetCode(code) }.getOrNull()
        }
        if (safeExtras.isEmpty()) {
            return "set:$safeSetCode lang:en"
        }
        val setClause = (listOf(safeSetCode) + safeExtras).joinToString(" or ") { "set:$it" }
        return "($setClause) lang:en"
    }

    override suspend fun resolveCardId(cardName: String, setCode: String): DataResult<String> {
        return withContext(ioDispatcher) {
            try {
                val card = scryfallQueue.execute { scryfallApi.getCardByName(name = cardName, set = setCode) }
                DataResult.Success(card.id)
            } catch (_: Exception) {
                try {
                    val card = scryfallQueue.execute { scryfallApi.getCardByName(name = cardName) }
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
                val card = scryfallQueue.execute { scryfallApi.getCardByName(name = name, set = setCode) }
                DataResult.Success(card.toDomain())
            } catch (_: Exception) {
                try {
                    val card = scryfallQueue.execute { scryfallApi.getCardByName(name = name) }
                    DataResult.Success(card.toDomain())
                } catch (e: Exception) {
                    DataResult.Error(e.message ?: "Card not found")
                }
            }
        }
    }

    // Allowlist guards path traversal: the code becomes a cache directory name
    private fun sanitizeSetCode(setCode: String): String {
        val normalized = setCode.lowercase().trim()
        require(VALID_SET_CODE.matches(normalized)) { "Invalid set code: '$normalized'" }
        return normalized
    }

    // Throws instead of returning a missing dir: out-of-storage must surface, not be swallowed
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

    // Write-then-rename so a process kill mid-write never leaves truncated JSON behind
    private fun saveJsonToFile(jsonString: String, file: File) {
        val tmp = File(file.parent, "${file.name}.tmp")
        try {
            tmp.writeText(jsonString)
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    // Versions come from the Room-cached sets index, avoiding a network round-trip per open
    private suspend fun getRemoteGuideVersion(safeCode: String): String? =
        draftSetDao.getSetByCode(safeCode)?.guideVersion

    private suspend fun getRemoteTierVersion(safeCode: String): String? =
        draftSetDao.getSetByCode(safeCode)?.tierListVersion

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

        val formatSpeed = overview?.get("format_speed").safeAsString()

        val mechanics = json.getAsJsonArray("mechanics")
            ?.map { parseMechanic(it.asJsonObject) } ?: emptyList()

        val archetypes = parseArchetypeTierList(json.getAsJsonObject("archetype_tier_list"))

        val keyCommonsByColor = json.getAsJsonObject("key_commons_by_color")
            ?.entrySet()
            ?.associate { (colorLabel, cardsElement) ->
                val cards = cardsElement.takeIf { it.isJsonArray }?.asJsonArray
                    ?.map { parseArchetypeKeyCard(it.asJsonObject) } ?: emptyList()
                colorLabel to cards
            } ?: emptyMap()

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
            keyCommonsByColor = keyCommonsByColor,
            formatSpeed = formatSpeed,
        )
    }

    // key_examples is either {overperformers, underperformers} or a flat array (treated as overperformers)
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

    private fun parseMechanicKeyCard(obj: JsonObject): MechanicKeyCard {
        val imageUris = obj.getAsJsonObject("image_uris")
        val colors = obj.getAsJsonArray("colors")?.map { it.asString } ?: emptyList()
        val colorIdentity = obj.getAsJsonArray("color_identity")?.map { it.asString } ?: emptyList()
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
            manaCost = obj.get("mana_cost").safeAsString(),
            cmc = obj.get("cmc").safeAsDoubleOrNull(),
            colorIdentity = colorIdentity,
            sourceSet = obj.get("source_set").safeAsString(),
            stats = parseCardStats(obj.getAsJsonObject("stats")),
        )
    }

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
        val signpostCards = obj.getAsJsonArray("signpost_cards")
            ?.map { parseArchetypeKeyCard(it.asJsonObject) } ?: emptyList()
        val cardsToAvoid = obj.getAsJsonArray("cards_to_avoid")
            ?.map { parseArchetypeKeyCard(it.asJsonObject) } ?: emptyList()
        val colorLetters = obj.getAsJsonArray("color_letters")?.map { it.asString } ?: emptyList()

        return ArchetypeGuide(
            colors = obj.get("colors").safeAsString(),
            name = obj.get("name").safeAsString(),
            tier = obj.get("tier").safeAsString(),
            strategy = obj.get("strategy").safeAsString(),
            difficulty = obj.get("difficulty").safeAsString(),
            keyCards = keyCards,
            colorLetters = colorLetters,
            signpostCards = signpostCards,
            cardsToAvoid = cardsToAvoid,
            archetypeWinRate = obj.get("archetype_win_rate").safeAsDoubleOrNull(),
            archetypeGames = obj.get("archetype_games").safeAsIntOrNull(),
            notes = obj.get("notes").safeAsString(),
        )
    }

    private fun parseArchetypeKeyCard(obj: JsonObject): ArchetypeKeyCard {
        val imageUris = obj.getAsJsonObject("image_uris")
        val colors = obj.getAsJsonArray("colors")
            ?.map { it.asString } ?: emptyList()
        val colorIdentity = obj.getAsJsonArray("color_identity")?.map { it.asString } ?: emptyList()
        return ArchetypeKeyCard(
            name = obj.get("name").safeAsString(),
            scryfallId = obj.get("id").safeAsString(),
            colors = colors,
            typeLine = obj.get("type_line").safeAsString(),
            artCropUri = imageUris?.get("art_crop").safeAsString(),
            imageNormalUri = imageUris?.get("normal").safeAsString(),
            rarity = obj.get("rarity").safeAsString(),
            manaCost = obj.get("mana_cost").safeAsString(),
            cmc = obj.get("cmc").safeAsDoubleOrNull(),
            colorIdentity = colorIdentity,
            sourceSet = obj.get("source_set").safeAsString(),
            tierRating = obj.get("tier_rating").safeAsString(),
            pickOrderRank = obj.get("pick_order_rank").safeAsInt(),
            stats = parseCardStats(obj.getAsJsonObject("stats")),
        )
    }

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
        val colorIdentity = obj.getAsJsonArray("color_identity")?.map { it.asString } ?: emptyList()
        val ratingSources = obj.getAsJsonArray("rating_sources")?.map { it.asString } ?: emptyList()
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
            manaCost = obj.get("mana_cost").safeAsString(),
            cmc = obj.get("cmc").safeAsDoubleOrNull(),
            oracleText = obj.get("oracle_text").safeAsString(),
            colorIdentity = colorIdentity,
            sourceSet = obj.get("source_set").safeAsString(),
            ratingConfidence = obj.get("rating_confidence").safeAsString(),
            ratingSources = ratingSources,
            stats = parseCardStats(obj.getAsJsonObject("stats")),
            inBoosters = obj.get("in_boosters").safeAsBooleanOrNull(),
        )
    }

    private fun parseCardStats(obj: JsonObject?): DraftCardStats? {
        if (obj == null) return null
        return DraftCardStats(
            gihWinRate = obj.get("gih_wr").safeAsDoubleOrNull(),
            gihGames = obj.get("gih_games").safeAsIntOrNull(),
            iwd = obj.get("iwd").safeAsDoubleOrNull(),
        )
    }

    // `?.asString` alone throws on JsonNull, hence the explicit isJsonNull checks below
    private fun JsonElement?.safeAsString(default: String = ""): String =
        if (this == null || isJsonNull) default else asString

    private fun JsonElement?.safeAsInt(default: Int = 0): Int =
        if (this == null || isJsonNull) default else asInt

    // Null, not 0.0, for absent numbers: 0.0 is a legitimate cmc
    private fun JsonElement?.safeAsDoubleOrNull(): Double? =
        if (this == null || isJsonNull) null else runCatching { asDouble }.getOrNull()

    private fun JsonElement?.safeAsIntOrNull(): Int? =
        if (this == null || isJsonNull) null else runCatching { asInt }.getOrNull()

    private fun JsonElement?.safeAsBooleanOrNull(): Boolean? =
        if (this == null || isJsonNull) null else runCatching { asBoolean }.getOrNull()
}
