package com.mmg.manahub.core.tagging

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.TagCategory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists user edits to the tag dictionary as a JSON blob in DataStore and
 * keeps the global [TagDictionary] singleton in sync.
 *
 * The Settings → Tag Dictionary screen reads/writes through this class.
 * [MagicFolderApp] calls [loadAndApply] once at startup so analyzers running
 * inside [com.mmg.manahub.core.data.repository.CardRepositoryImpl] see
 * the user's overrides immediately.
 */
@Singleton
class TagDictionaryRepository @Inject constructor(
    private val prefs: UserPreferencesDataStore,
) {

    private val gson = Gson()
    private val listType = object : TypeToken<List<OverrideRecord>>() {}.type

    // Serializes all read-modify-write operations so concurrent upsert/delete
    // calls don't silently clobber each other's changes.
    private val writeMutex = Mutex()

    val overridesFlow: Flow<List<TagOverride>> = prefs.tagDictionaryOverridesFlow.map { json ->
        decode(json)
    }

    /**
     * Reads the persisted overrides and applies them to the singleton.
     * Acquires [writeMutex] so this never races with an in-progress upsert/delete.
     */
    suspend fun loadAndApply() = writeMutex.withLock {
        val list = decode(prefs.tagDictionaryOverridesFlow.first())
        TagDictionary.applyOverrides(list)
    }

    suspend fun upsert(override: TagOverride) = writeMutex.withLock {
        val current = decode(prefs.tagDictionaryOverridesFlow.first())
            .filterNot { it.key == override.key } + override
        prefs.saveTagDictionaryOverrides(encode(current))
        TagDictionary.applyOverrides(current)
    }

    suspend fun delete(key: String) = writeMutex.withLock {
        val current = decode(prefs.tagDictionaryOverridesFlow.first())
            .filterNot { it.key == key }
        prefs.saveTagDictionaryOverrides(encode(current))
        TagDictionary.applyOverrides(current)
    }

    suspend fun resetAll() = writeMutex.withLock {
        prefs.saveTagDictionaryOverrides("[]")
        TagDictionary.applyOverrides(emptyList())
    }

    // ── JSON ↔ domain ────────────────────────────────────────────────────────

    private data class OverrideRecord(
        val key: String,
        val category: String?,
        val labels: Map<String, String>,
        val patterns: Map<String, List<String>>,
    )

    private fun decode(json: String): List<TagOverride> = runCatching {
        val records: List<OverrideRecord> = gson.fromJson(json, listType) ?: emptyList()
        records.map { r ->
            TagOverride(
                key      = r.key,
                category = r.category?.let { runCatching { TagCategory.valueOf(it) }.getOrNull() },
                labels   = r.labels,
                patterns = r.patterns,
            )
        }
    }.getOrDefault(emptyList())

    private fun encode(overrides: List<TagOverride>): String = gson.toJson(
        overrides.map {
            OverrideRecord(
                key      = it.key,
                category = it.category?.name,
                labels   = it.labels,
                patterns = it.patterns,
            )
        }
    )
}
