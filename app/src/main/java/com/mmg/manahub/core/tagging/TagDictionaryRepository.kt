package com.mmg.manahub.core.tagging

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.util.recordNonFatal
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
 *
 * **D12 — system tags are read-only.** Only overrides whose [TagOverride.key] starts
 * with [CUSTOM_KEY_PREFIX] may be created/edited/deleted through [upsert]/[delete].
 * There is no legitimate path (from the UI or otherwise) that writes an override
 * targeting a system (non-`custom_`) key going forward — [upsert] rejects such
 * calls defensively and records a Non-Fatal instead of silently corrupting the
 * store. Any override on a system key found in *existing* persisted data (written
 * before D12) is dropped by the one-time [loadAndApply] migration.
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

    /**
     * F1 — last known-good state, in memory only. Kept in sync on every successful
     * [decode] AND on every successful write ([upsert]/[delete]/[resetAll]/
     * [loadAndApply]'s migration), so it always reflects the true current state —
     * not just whatever the most recent raw-JSON parse happened to produce. When
     * [decode] hits unparsable JSON (corrupt blob) it returns this value instead of
     * silently treating the corruption as "no overrides", and every read-modify-write
     * is based on it — so a single corrupt read can never wipe out the user's other
     * overrides (see `given corrupt stored blob when upsert runs...` in
     * `TagDictionaryRepositoryTest`).
     */
    @Volatile
    private var lastKnownGood: List<TagOverride> = emptyList()

    val overridesFlow: Flow<List<TagOverride>> = prefs.tagDictionaryOverridesFlow.map { json ->
        decode(json)
    }

    /**
     * Reads the persisted overrides and applies them to the singleton.
     * Acquires [writeMutex] so this never races with an in-progress upsert/delete.
     *
     * Performs two one-time silent migrations:
     *  - Legacy shape (map-shaped `patterns`, es/de labels) → re-encoded in the new
     *    English-only list shape.
     *  - D12: any override targeting a system (non-`custom_`) key is retired (it is
     *    no longer creatable/editable from the UI, so persisted copies from before
     *    D12 must be dropped). A count-only Non-Fatal breadcrumb
     *    (`tagdict_system_override_retired`) is recorded — no key names/PII.
     */
    suspend fun loadAndApply() = writeMutex.withLock {
        val raw = prefs.tagDictionaryOverridesFlow.first()
        val decoded = decode(raw)

        val (customOnly, systemKeyOverrides) = decoded.partition { it.key.startsWith(CUSTOM_KEY_PREFIX) }

        val needsRewrite = systemKeyOverrides.isNotEmpty() || (decoded.isNotEmpty() && isLegacyShape(raw))
        if (systemKeyOverrides.isNotEmpty()) {
            recordNonFatal("tagdict_system_override_retired: count=${systemKeyOverrides.size}")
        }
        if (needsRewrite) {
            prefs.saveTagDictionaryOverrides(encode(customOnly))
        }
        lastKnownGood = customOnly
        TagDictionary.applyOverrides(customOnly)
    }

    /**
     * D12: rejects any override whose key does not start with [CUSTOM_KEY_PREFIX] — system
     * dictionary entries are read-only for users. A rejected call is a no-op (defensive; the
     * UI never constructs a non-`custom_` override after D12) and records a Non-Fatal so a
     * future regression is visible instead of silently corrupting the store.
     */
    suspend fun upsert(override: TagOverride) = writeMutex.withLock {
        if (!override.key.startsWith(CUSTOM_KEY_PREFIX)) {
            recordNonFatal("tagdict_upsert_rejected_non_custom_key")
            return@withLock
        }
        val current = decode(prefs.tagDictionaryOverridesFlow.first())
            .filterNot { it.key == override.key } + override
        prefs.saveTagDictionaryOverrides(encode(current))
        lastKnownGood = current
        TagDictionary.applyOverrides(current)
    }

    suspend fun delete(key: String) = writeMutex.withLock {
        val current = decode(prefs.tagDictionaryOverridesFlow.first())
            .filterNot { it.key == key }
        prefs.saveTagDictionaryOverrides(encode(current))
        lastKnownGood = current
        TagDictionary.applyOverrides(current)
    }

    /**
     * F2 — deletes all user-created custom tags (all persisted overrides are `custom_`-keyed
     * after D12, so clearing the whole store is equivalent to "delete all custom tags").
     */
    suspend fun resetAll() = writeMutex.withLock {
        prefs.saveTagDictionaryOverrides("[]")
        lastKnownGood = emptyList()
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
     *
     * F1: a **blank** blob (`""`) is a legitimate empty state and decodes to an empty
     * list without touching [lastKnownGood]. A **corrupt** blob (non-empty but
     * unparsable — a `JsonSyntaxException` or similar) is reported via [recordNonFatal]
     * and [lastKnownGood] is returned instead, so a transient corruption never looks
     * like (and never gets persisted as) "the user has no overrides".
     */
    private fun decode(json: String): List<TagOverride> {
        if (json.isBlank()) return emptyList()
        val decoded = decodeOrNull(json)
        if (decoded == null) {
            recordNonFatal("tagdict_decode_failed", IllegalStateException("Corrupt tag dictionary override JSON"))
            return lastKnownGood
        }
        lastKnownGood = decoded
        return decoded
    }

    /** Returns null (never an empty-as-fallback list) when [json] fails to parse as a whole. */
    private fun decodeOrNull(json: String): List<TagOverride>? = try {
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
    } catch (e: Exception) {
        null
    }

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

    companion object {
        /** D12: prefix that namespaces user-created tags away from system dictionary keys. */
        const val CUSTOM_KEY_PREFIX = "custom_"
    }
}
