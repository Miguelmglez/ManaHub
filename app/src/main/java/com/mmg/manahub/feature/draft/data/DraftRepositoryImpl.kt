package com.mmg.manahub.feature.draft.data

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
import com.mmg.manahub.core.data.local.entity.DraftSetEntity
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
import com.mmg.manahub.core.util.recordSafeNonFatal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class DraftRepositoryImpl(
    private val context: Context,
    private val scryfallApi: ScryfallClient,
    private val scryfallQueue: ScryfallRequestQueue,
    private val cloudflareClient: CloudflareContentClient,
    private val draftSetDao: DraftSetDao,
    private val gson: Gson,
    private val draftPrefs: SharedPreferences,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : DraftRepository {

    companion object {
        private const val MANIFEST_CACHE_DURATION_MS = 5 * 60 * 1000L
        private const val FAILURE_COOLDOWN_MS = 5 * 60 * 1000L
        private const val PREF_GUIDE_VERSION = "pref_draft_%s_guide_version"
        private const val PREF_TIER_VERSION = "pref_draft_%s_tier_version"

        private val VALID_SET_CODE = Regex("^[a-z0-9]{2,6}$")
    }

    private val guideMutexes = ConcurrentHashMap<String, Mutex>()
    private val tierMutexes = ConcurrentHashMap<String, Mutex>()
    private val manifestMutex = Mutex()
    @Volatile
    private var manifestRefreshGeneration = 0L
    private var lastManifestRefreshFailure: Exception? = null
    private var lastManifestRefreshFailureAt: Long? = null
    private val artifactRefreshFailures = ConcurrentHashMap<ArtifactFailureKey, TimedFailure>()

    private fun guideMutex(code: String) = guideMutexes.computeIfAbsent(code) { Mutex() }
    private fun tierMutex(code: String) = tierMutexes.computeIfAbsent(code) { Mutex() }

    // Parsed models keyed by set code; only read/written under that set's mutex
    private val parsedGuides = ConcurrentHashMap<String, VersionedModel<SetDraftGuide>>()
    private val parsedTierLists = ConcurrentHashMap<String, VersionedModel<SetTierList>>()

    private data class VersionedModel<T>(val version: String?, val model: T)
    private data class TimedFailure(val error: Exception, val failedAt: Long)
    private data class ArtifactFailureKey(
        val setCode: String,
        val artifactType: ArtifactType,
        val remoteVersion: String,
    )

    private enum class ArtifactType(val telemetryValue: String) {
        GUIDE("guide"),
        TIER_LIST("tier_list"),
    }

    private sealed interface RemoteVersionLookup {
        data class Present(val version: String) : RemoteVersionLookup
        data object MissingSet : RemoteVersionLookup
        data class Failure(val error: Exception) : RemoteVersionLookup
    }

    override suspend fun getDraftableSets(forceRefresh: Boolean): DataResult<List<DraftSet>> {
        return withContext(ioDispatcher) {
            val refreshFailure = ensureManifestFresh(forceRefresh)
            try {
                val cached = draftSetDao.getAllSetsSnapshot()
                if (cached.isNotEmpty()) {
                    DataResult.Success(
                        data = cached.map { it.toDomain() },
                        isStale = refreshFailure != null,
                    )
                } else {
                    DataResult.Error(refreshFailure?.message ?: "No draft sets are available")
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                DataResult.Error(e.message ?: "Failed to load sets")
            }
        }
    }

    override suspend fun getSetGuide(setCode: String): DataResult<SetDraftGuide> {
        return withContext(ioDispatcher) {
            val safeCode = sanitizeSetCode(setCode)
            try {
                val manifestFailure = ensureManifestFresh(forceRefresh = false)
                guideMutex(safeCode).withLock {
                    val localFile = guideFile(safeCode)
                    val remoteVersion = manifestFailure?.let(RemoteVersionLookup::Failure)
                        ?: getRemoteVersion(safeCode) { it.guideVersion }
                    loadVersionedArtifact(
                        safeCode = safeCode,
                        artifactType = ArtifactType.GUIDE,
                        localFile = localFile,
                        preferenceKey = PREF_GUIDE_VERSION.format(safeCode),
                        remoteVersionLookup = remoteVersion,
                        parsedModels = parsedGuides,
                        unavailableMessage = "Guide not available for $safeCode",
                        fetch = { version -> cloudflareClient.getSetGuide(safeCode, version) },
                        parse = { json -> parseAndValidateGuide(safeCode, json) },
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                reportArtifactFailure(
                    safeCode = safeCode,
                    artifactType = ArtifactType.GUIDE,
                    error = e,
                    staleFallback = false,
                    tag = "draft_artifact_local_cache_failed",
                )
                DataResult.Error(e.message ?: "Failed to load guide for $setCode")
            }
        }
    }

    override suspend fun getSetTierList(setCode: String): DataResult<SetTierList> {
        return withContext(ioDispatcher) {
            val safeCode = sanitizeSetCode(setCode)
            try {
                val manifestFailure = ensureManifestFresh(forceRefresh = false)
                tierMutex(safeCode).withLock {
                    val localFile = tierListFile(safeCode)
                    val remoteVersion = manifestFailure?.let(RemoteVersionLookup::Failure)
                        ?: getRemoteVersion(safeCode) { it.tierListVersion }
                    loadVersionedArtifact(
                        safeCode = safeCode,
                        artifactType = ArtifactType.TIER_LIST,
                        localFile = localFile,
                        preferenceKey = PREF_TIER_VERSION.format(safeCode),
                        remoteVersionLookup = remoteVersion,
                        parsedModels = parsedTierLists,
                        unavailableMessage = "Tier list not available for $safeCode",
                        fetch = { version -> cloudflareClient.getSetTierList(safeCode, version) },
                        parse = { json -> parseAndValidateTierList(safeCode, json) },
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                reportArtifactFailure(
                    safeCode = safeCode,
                    artifactType = ArtifactType.TIER_LIST,
                    error = e,
                    staleFallback = false,
                    tag = "draft_artifact_local_cache_failed",
                )
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

    private suspend fun ensureManifestFresh(forceRefresh: Boolean): Exception? {
        val observedRefreshGeneration = manifestRefreshGeneration
        val observedCachedTime = try {
            draftSetDao.getLastCachedTime()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return manifestMutex.withLock {
                if (manifestRefreshGeneration != observedRefreshGeneration) {
                    return@withLock lastManifestRefreshFailure
                }
                if (!forceRefresh && isManifestFailureCoolingDown()) {
                    return@withLock lastManifestRefreshFailure
                }
                registerManifestFailure(e, forceRefresh, staleFallback = false)
            }
        }
        if (!forceRefresh && isManifestFresh(observedCachedTime)) return null
        if (!forceRefresh && isManifestFailureCoolingDown()) return lastManifestRefreshFailure

        return manifestMutex.withLock {
            if (manifestRefreshGeneration != observedRefreshGeneration) {
                return@withLock lastManifestRefreshFailure
            }
            if (!forceRefresh && isManifestFailureCoolingDown()) {
                return@withLock lastManifestRefreshFailure
            }
            val currentCachedTime = try {
                draftSetDao.getLastCachedTime()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withLock registerManifestFailure(
                    error = e,
                    forceRefresh = forceRefresh,
                    staleFallback = observedCachedTime != null,
                )
            }
            val anotherCallerRefreshed = currentCachedTime != observedCachedTime &&
                isManifestFresh(currentCachedTime)
            if ((!forceRefresh && isManifestFresh(currentCachedTime)) || anotherCallerRefreshed) {
                return@withLock null
            }

            val refreshFailure = try {
                val response = cloudflareClient.getSetsIndex()
                require(response.indexVersion.isNotBlank()) { "Draft sets manifest has no version" }
                require(response.sets.isNotEmpty()) { "Draft sets manifest is empty" }
                require(response.sets.map { it.code }.distinct().size == response.sets.size) {
                    "Draft sets manifest contains duplicate set codes"
                }
                val cachedAt = nowMillis()
                val entities = response.sets.map { entry ->
                    require(VALID_SET_CODE.matches(entry.code)) {
                        "Draft sets manifest contains an invalid set code"
                    }
                    require(entry.contentVersions.guide.isNotBlank()) {
                        "Draft sets manifest contains a blank guide version"
                    }
                    require(entry.contentVersions.tierList.isNotBlank()) {
                        "Draft sets manifest contains a blank tier-list version"
                    }
                    entry.toEntity().copy(cachedAt = cachedAt)
                }
                draftSetDao.replaceAll(entities)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }
            if (refreshFailure == null) {
                lastManifestRefreshFailure = null
                lastManifestRefreshFailureAt = null
                manifestRefreshGeneration++
                null
            } else {
                registerManifestFailure(
                    error = refreshFailure,
                    forceRefresh = forceRefresh,
                    staleFallback = currentCachedTime != null,
                )
            }
        }
    }

    private fun registerManifestFailure(
        error: Exception,
        forceRefresh: Boolean,
        staleFallback: Boolean,
    ): Exception {
        lastManifestRefreshFailure = error
        lastManifestRefreshFailureAt = nowMillis()
        manifestRefreshGeneration++
        reportManifestFailure(error, forceRefresh, staleFallback)
        return error
    }

    private fun isManifestFailureCoolingDown(): Boolean {
        val failedAt = lastManifestRefreshFailureAt ?: return false
        val age = nowMillis() - failedAt
        return lastManifestRefreshFailure != null && age in 0 until FAILURE_COOLDOWN_MS
    }

    private fun isManifestFresh(cachedAt: Long?): Boolean {
        if (cachedAt == null) return false
        val age = nowMillis() - cachedAt
        return age in 0 until MANIFEST_CACHE_DURATION_MS
    }

    private suspend fun <T> loadVersionedArtifact(
        safeCode: String,
        artifactType: ArtifactType,
        localFile: File,
        preferenceKey: String,
        remoteVersionLookup: RemoteVersionLookup,
        parsedModels: ConcurrentHashMap<String, VersionedModel<T>>,
        unavailableMessage: String,
        fetch: suspend (String) -> String,
        parse: (String) -> T,
    ): DataResult<T> {
        val storedVersion = draftPrefs.getString(preferenceKey, null)
        val existingModel = parsedModels[safeCode]?.model ?: loadLocalArtifact(
            safeCode = safeCode,
            artifactType = artifactType,
            localFile = localFile,
            parse = parse,
        )?.also { parsedModels[safeCode] = VersionedModel(storedVersion, it) }

        val remoteVersion = when (remoteVersionLookup) {
            RemoteVersionLookup.MissingSet -> {
                clearArtifactCache(
                    safeCode = safeCode,
                    artifactType = artifactType,
                    localFile = localFile,
                    preferenceKey = preferenceKey,
                    parsedModels = parsedModels,
                )
                return DataResult.Error(unavailableMessage)
            }
            is RemoteVersionLookup.Failure -> {
                return existingModel?.let { DataResult.Success(it, isStale = true) }
                    ?: DataResult.Error(remoteVersionLookup.error.message ?: unavailableMessage)
            }
            is RemoteVersionLookup.Present -> remoteVersionLookup.version
        }

        clearArtifactFailuresForOtherVersions(safeCode, artifactType, remoteVersion)
        val needsRefresh = existingModel == null || !localFile.exists() ||
            remoteVersion != storedVersion
        if (!needsRefresh) return DataResult.Success(existingModel)

        val failureKey = ArtifactFailureKey(safeCode, artifactType, remoteVersion)
        if (existingModel != null && isArtifactFailureCoolingDown(failureKey)) {
            return DataResult.Success(existingModel, isStale = true)
        }

        return try {
            val downloadedJson = fetch(remoteVersion)
            val downloadedModel = parse(downloadedJson)
            replaceJsonFile(downloadedJson, localFile)
            draftPrefs.edit().putString(preferenceKey, remoteVersion).apply()
            parsedModels[safeCode] = VersionedModel(remoteVersion, downloadedModel)
            artifactRefreshFailures.remove(failureKey)
            reportArtifactReplacement(safeCode, artifactType)
            DataResult.Success(downloadedModel)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            artifactRefreshFailures[failureKey] = TimedFailure(e, nowMillis())
            reportArtifactFailure(
                safeCode = safeCode,
                artifactType = artifactType,
                error = e,
                staleFallback = existingModel != null,
                tag = "draft_artifact_refresh_failed",
            )
            existingModel?.let { DataResult.Success(it, isStale = true) }
                ?: DataResult.Error(e.message ?: unavailableMessage)
        }
    }

    private fun <T> loadLocalArtifact(
        safeCode: String,
        artifactType: ArtifactType,
        localFile: File,
        parse: (String) -> T,
    ): T? {
        val temporaryFile = File(localFile.parent, "${localFile.name}.tmp")
        val backupFile = File(localFile.parent, "${localFile.name}.bak")
        var mainFailure: Exception? = null

        if (localFile.exists()) {
            try {
                val model = parse(localFile.readText())
                cleanupRecoveryFiles(safeCode, artifactType, temporaryFile, backupFile)
                return model
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mainFailure = e
            }
        }

        if (backupFile.exists() && backupFile.isFile) {
            try {
                val model = parse(backupFile.readText())
                mainFailure?.let {
                    reportArtifactFailure(
                        safeCode,
                        artifactType,
                        it,
                        staleFallback = true,
                        tag = "draft_artifact_local_cache_failed",
                    )
                }
                restoreBackup(
                    safeCode = safeCode,
                    artifactType = artifactType,
                    localFile = localFile,
                    temporaryFile = temporaryFile,
                    backupFile = backupFile,
                    parse = parse,
                )
                return model
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val failure = mainFailure ?: e
                reportArtifactFailure(
                    safeCode,
                    artifactType,
                    failure,
                    staleFallback = false,
                    tag = "draft_artifact_local_cache_failed",
                )
                return null
            }
        }

        mainFailure?.let {
            reportArtifactFailure(
                safeCode,
                artifactType,
                it,
                staleFallback = false,
                tag = "draft_artifact_local_cache_failed",
            )
        }
        return null
    }

    private fun <T> restoreBackup(
        safeCode: String,
        artifactType: ArtifactType,
        localFile: File,
        temporaryFile: File,
        backupFile: File,
        parse: (String) -> T,
    ) {
        try {
            if (temporaryFile.exists() && !temporaryFile.delete()) {
                throw IOException("Failed to clear temporary draft cache file")
            }
            backupFile.copyTo(localFile, overwrite = true)
            parse(localFile.readText())
            if (!backupFile.delete()) throw IOException("Failed to clear restored draft cache backup")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportArtifactFailure(
                safeCode,
                artifactType,
                e,
                staleFallback = true,
                tag = "draft_artifact_local_cache_failed",
            )
        }
    }

    private fun cleanupRecoveryFiles(
        safeCode: String,
        artifactType: ArtifactType,
        temporaryFile: File,
        backupFile: File,
    ) {
        try {
            listOf(temporaryFile, backupFile).forEach { recoveryFile ->
                if (recoveryFile.exists() && !recoveryFile.delete()) {
                    throw IOException("Failed to clear stale draft cache recovery file")
                }
            }
        } catch (e: Exception) {
            reportArtifactFailure(
                safeCode,
                artifactType,
                e,
                staleFallback = true,
                tag = "draft_artifact_local_cache_failed",
            )
        }
    }

    private fun <T> clearArtifactCache(
        safeCode: String,
        artifactType: ArtifactType,
        localFile: File,
        preferenceKey: String,
        parsedModels: ConcurrentHashMap<String, VersionedModel<T>>,
    ) {
        parsedModels.remove(safeCode)
        artifactRefreshFailures.keys.removeAll { it.setCode == safeCode && it.artifactType == artifactType }
        try {
            val files = listOf(
                localFile,
                File(localFile.parent, "${localFile.name}.tmp"),
                File(localFile.parent, "${localFile.name}.bak"),
            )
            files.forEach { file ->
                if (file.exists() && !file.delete()) throw IOException("Failed to clear unpublished draft content")
            }
            draftPrefs.edit().remove(preferenceKey).apply()
        } catch (e: Exception) {
            reportArtifactFailure(
                safeCode,
                artifactType,
                e,
                staleFallback = false,
                tag = "draft_artifact_local_cache_failed",
            )
        }
    }

    private fun isArtifactFailureCoolingDown(key: ArtifactFailureKey): Boolean {
        val failure = artifactRefreshFailures[key] ?: return false
        val age = nowMillis() - failure.failedAt
        if (age in 0 until FAILURE_COOLDOWN_MS) return true
        artifactRefreshFailures.remove(key, failure)
        return false
    }

    private fun clearArtifactFailuresForOtherVersions(
        safeCode: String,
        artifactType: ArtifactType,
        remoteVersion: String,
    ) {
        artifactRefreshFailures.keys.removeAll {
            it.setCode == safeCode && it.artifactType == artifactType && it.remoteVersion != remoteVersion
        }
    }

    private fun replaceJsonFile(jsonString: String, file: File) {
        val tmp = File(file.parent, "${file.name}.tmp")
        val backup = File(file.parent, "${file.name}.bak")
        try {
            if (tmp.exists() && !tmp.delete()) {
                throw IOException("Failed to clear temporary draft cache file")
            }
            tmp.writeText(jsonString)
            if (!file.exists()) {
                if (!tmp.renameTo(file)) throw IOException("Failed to install draft cache file")
                return
            }

            if (backup.exists() && !backup.delete()) {
                throw IOException("Failed to clear draft cache backup")
            }
            if (!file.renameTo(backup)) throw IOException("Failed to back up draft cache file")

            try {
                if (!tmp.renameTo(file)) throw IOException("Failed to install draft cache file")
            } catch (replacementFailure: Exception) {
                try {
                    if (file.exists() && !file.delete()) {
                        throw IOException("Failed to remove incomplete draft cache file")
                    }
                    if (!backup.renameTo(file)) {
                        backup.copyTo(file, overwrite = true)
                    }
                    if (backup.exists()) backup.delete()
                } catch (restoreFailure: Exception) {
                    replacementFailure.addSuppressed(restoreFailure)
                }
                throw replacementFailure
            }
            if (backup.exists()) backup.delete()
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private suspend fun getRemoteVersion(
        safeCode: String,
        selectVersion: (DraftSetEntity) -> String,
    ): RemoteVersionLookup = try {
        val set = draftSetDao.getSetByCode(safeCode) ?: return RemoteVersionLookup.MissingSet
        val version = selectVersion(set)
        if (version.isBlank()) {
            RemoteVersionLookup.Failure(IOException("Draft artifact version is blank"))
        } else {
            RemoteVersionLookup.Present(version)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RemoteVersionLookup.Failure(e)
    }

    private fun reportManifestFailure(
        error: Exception,
        forceRefresh: Boolean,
        staleFallback: Boolean,
    ) {
        if (error is CancellationException) return
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("draft_manifest_force_refresh", forceRefresh)
                setCustomKey("draft_manifest_stale_fallback", staleFallback)
                log("draft_manifest_refresh_failed")
            }
            recordSafeNonFatal("draft_manifest_refresh_failed", error)
        }
    }

    private fun reportArtifactFailure(
        safeCode: String,
        artifactType: ArtifactType,
        error: Exception,
        staleFallback: Boolean,
        tag: String,
    ) {
        if (error is CancellationException) return
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("draft_artifact_set_code", safeCode)
                setCustomKey("draft_artifact_type", artifactType.telemetryValue)
                setCustomKey("draft_artifact_stale_fallback", staleFallback)
                log(tag)
            }
            recordSafeNonFatal(tag, error)
        }
    }

    private fun reportArtifactReplacement(
        safeCode: String,
        artifactType: ArtifactType,
    ) {
        runCatching {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("draft_artifact_set_code", safeCode)
                setCustomKey("draft_artifact_type", artifactType.telemetryValue)
                log("draft_artifact_cache_replaced")
            }
        }
    }

    private fun parseAndValidateGuide(setCode: String, jsonString: String): SetDraftGuide {
        val json = gson.fromJson(jsonString, JsonObject::class.java)
            ?: throw IOException("Guide JSON is empty")
        val guide = parseGuide(setCode, json)
        require(guide.setName.isNotBlank()) { "Guide JSON has no set name" }
        require(json.get("set_overview")?.isJsonObject == true) { "Guide JSON has no set overview" }
        return guide
    }

    private fun parseAndValidateTierList(setCode: String, jsonString: String): SetTierList {
        val json = gson.fromJson(jsonString, JsonObject::class.java)
            ?: throw IOException("Tier-list JSON is empty")
        val tierList = parseTierList(setCode, json)
        require(tierList.setName.isNotBlank()) { "Tier-list JSON has no set name" }
        require(json.get("categories")?.isJsonArray == true) { "Tier-list JSON has no categories" }
        return tierList
    }

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

        val keyUncommonsByColor = json.getAsJsonObject("key_uncommons_by_color")
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
            keyUncommonsByColor = keyUncommonsByColor,
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
