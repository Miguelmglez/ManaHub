package com.mmg.manahub.core.tagging

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
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
    private val listType = object : TypeToken<List<JsonElement>>() {}.type

    // Serializes all read-modify-write operations so concurrent upsert/delete
    // calls don't silently clobber each other's changes.
    private val writeMutex = Mutex()

    val overridesFlow: Flow<List<TagOverride>> = prefs.tagDictionaryOverridesFlow.map { json ->
        decode(json)
    }

    /**
     * Reads the persisted overrides and applies them to the singleton.
     * Acquires [writeMutex] so this never races with an in-progress upsert/delete.
     *
     * Performs a one-time silent migration: if the persisted JSON is in the legacy
     * shape (map-shaped `patterns`, es/de labels), it is re-encoded in the new
     * English-only list shape and saved back.
     */
    suspend fun loadAndApply() = writeMutex.withLock {
        val raw = prefs.tagDictionaryOverridesFlow.first()
        val list = decode(raw)
        // Silent migration: if decode produced records but re-encoding differs from
        // what is stored (legacy shape detected), persist the canonical new shape.
        val canonical = encode(list)
        if (list.isNotEmpty() && isLegacyShape(raw)) {
            prefs.saveTagDictionaryOverrides(canonical)
        }
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

    /** Canonical on-disk record (new shape): English-only labels + rule-line list. */
    private data class OverrideRecord(
        val key: String,
        val category: String?,
        val labels: Map<String, String>,
        val patterns: List<String>,
    )

    /**
     * Decodes the persisted JSON into [TagOverride]s, accepting BOTH shapes:
     *  - NEW: `patterns` is a JSON array of rule-line strings; labels are en-only.
     *  - LEGACY: `patterns` is a JSON object `{lang:[...]}`; labels may carry es/de.
     *    Only the "en" pattern list and "en" label are kept; es/de are dropped.
     */
    private fun decode(json: String): List<TagOverride> = runCatching {
        val elements: List<JsonElement> = gson.fromJson(json, listType) ?: emptyList()
        elements.mapNotNull { element ->
            if (!element.isJsonObject) return@mapNotNull null
            val obj = element.asJsonObject

            val key = obj.get("key")?.takeIf { it.isJsonPrimitive }?.asString
                ?: return@mapNotNull null

            val category = obj.get("category")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.let { runCatching { TagCategory.valueOf(it) }.getOrNull() }

            // Labels: keep only the "en" key (drop es/de from legacy data).
            val labels = obj.getAsJsonObject("labels")?.let { labelsObj ->
                labelsObj.get("en")?.takeIf { it.isJsonPrimitive }?.asString
                    ?.let { mapOf("en" to it) }
            } ?: emptyMap()

            // Patterns: array of rule lines (new) OR map's "en" list (legacy).
            val patternsElement = obj.get("patterns")
            val patterns: List<String> = when {
                patternsElement == null || patternsElement.isJsonNull -> emptyList()
                patternsElement.isJsonArray ->
                    (patternsElement as JsonArray).mapNotNull { p ->
                        p.takeIf { it.isJsonPrimitive }?.asString
                    }
                patternsElement.isJsonObject ->
                    patternsElement.asJsonObject.getAsJsonArray("en")?.mapNotNull { p ->
                        p.takeIf { it.isJsonPrimitive }?.asString
                    } ?: emptyList()
                else -> emptyList()
            }

            TagOverride(
                key      = key,
                category = category,
                labels   = labels,
                patterns = patterns,
            )
        }
    }.getOrDefault(emptyList())

    /** True when the raw JSON uses the legacy shape (map-shaped patterns or es/de labels). */
    private fun isLegacyShape(json: String): Boolean = runCatching {
        val elements: List<JsonElement> = gson.fromJson(json, listType) ?: return false
        elements.any { element ->
            if (!element.isJsonObject) return@any false
            val obj = element.asJsonObject
            val patternsIsObject = obj.get("patterns")?.isJsonObject == true
            val labelsHasOther = obj.getAsJsonObject("labels")?.entrySet()
                ?.any { it.key != "en" } == true
            patternsIsObject || labelsHasOther
        }
    }.getOrDefault(false)

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
