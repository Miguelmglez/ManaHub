package com.mmg.manahub.feature.tagdictionary.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.tagging.TagDictionary
import com.mmg.manahub.core.tagging.TagDictionaryRepository
import com.mmg.manahub.core.tagging.TagDictionaryRepository.Companion.CUSTOM_KEY_PREFIX
import com.mmg.manahub.core.tagging.TagOverride
import com.mmg.manahub.core.util.recordNonFatal
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A single row in the Tag Dictionary editor.
 *
 * D12: [isSystem] rows (any key that does not start with the [CUSTOM_KEY_PREFIX] namespace) are
 * READ-ONLY in the editor — label + rules are shown, but there is no edit/delete control. Only
 * rows the user created via [TagDictionaryViewModel.createCustomTag] are editable/deletable.
 */
data class TagDictionaryRow(
    val key:      String,
    val category: TagCategory,
    val labelEn:  String,
    /** Detection rules rendered in the user override line syntax (see [TagDictionary.parseRuleLine]). */
    val rules:    List<String>,
) {
    val isSystem: Boolean get() = !key.startsWith(CUSTOM_KEY_PREFIX)
}

data class TagDictionaryUiState(
    val rows:                 List<TagDictionaryRow> = emptyList(),
    val autoThreshold:        Float = 0.90f,
    val suggestThreshold:     Float = 0.60f,
    val editingKey:           String? = null,
    val query:                String  = "",
    val isCreatingCustomTag:  Boolean = false,
    val isConfirmingResetAll: Boolean = false,
)

/** One-shot events consumed by the Screen (buffered Channel, never a nullable StateFlow). */
sealed class TagDictionaryEvent {
    /** F2: all custom tags were deleted — the Screen shows a [com.mmg.manahub.core.ui.components.MagicToast] success message. */
    data object CustomTagsCleared : TagDictionaryEvent()
}

/**
 * ViewModel for the Tag Dictionary editor screen.
 *
 * KMP migration — Phase 1 Hilt→Koin cutover: this ViewModel is now a plain class resolved by Koin
 * (`koinViewModel()`) via [com.mmg.manahub.feature.tagdictionary.di.tagDictionaryKoinModule], not Hilt.
 *
 * F4: [state] is kept in sync with [TagDictionaryRepository.overridesFlow] via a single reactive
 * subscription — there is no manual "refresh after every mutation" chain. Any repository write
 * (upsert/delete/resetAll/loadAndApply's migration) causes a new DataStore emission, which this
 * collector turns into a fresh [TagDictionary.applyOverrides] + row rebuild automatically.
 */
class TagDictionaryViewModel(
    private val dictionaryRepo: TagDictionaryRepository,
    private val prefs:          UserPreferencesDataStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TagDictionaryUiState())
    val state: StateFlow<TagDictionaryUiState> = _state.asStateFlow()

    private val _events = Channel<TagDictionaryEvent>(Channel.BUFFERED)
    val events: Flow<TagDictionaryEvent> = _events.receiveAsFlow()

    // F5: serializes both threshold writes onto a single path so a rapid drag on one slider
    // can't interleave with the other slider's read-modify-write and lose an adjustment.
    private val thresholdMutex = Mutex()

    init {
        // One-time prime + migration (legacy-shape re-encode, D12 system-key override retirement).
        viewModelScope.launch { dictionaryRepo.loadAndApply() }

        // F4: the single reactive source of truth for `rows` — replaces the old imperative
        // refreshRows()-after-every-mutation chain and the dead overridesFlow.first() touch.
        viewModelScope.launch {
            dictionaryRepo.overridesFlow.collect { overrides ->
                TagDictionary.applyOverrides(overrides)
                _state.update { it.copy(rows = buildRows()) }
            }
        }
        viewModelScope.launch {
            prefs.tagAutoThresholdFlow.collect { v ->
                _state.update { it.copy(autoThreshold = v) }
            }
        }
        viewModelScope.launch {
            prefs.tagSuggestThresholdFlow.collect { v ->
                _state.update { it.copy(suggestThreshold = v) }
            }
        }
    }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }

    /** D12: no-op for system rows — the Screen must only call this for editable (custom) rows. */
    fun onStartEdit(key: String) = _state.update { it.copy(editingKey = key) }
    fun onDismissEdit()          = _state.update { it.copy(editingKey = null) }

    fun onStartCreateCustomTag()   = _state.update { it.copy(isCreatingCustomTag = true) }
    fun onDismissCreateCustomTag() = _state.update { it.copy(isCreatingCustomTag = false) }

    fun onRequestResetAll()  = _state.update { it.copy(isConfirmingResetAll = true) }
    fun onDismissResetAll()  = _state.update { it.copy(isConfirmingResetAll = false) }

    fun setAutoThreshold(value: Float) {
        viewModelScope.launch {
            thresholdMutex.withLock {
                // Keep auto > suggest by at least 0.05.
                val safe = value.coerceIn(0.05f, 1f)
                prefs.saveTagAutoThreshold(safe)
                // Read the persisted suggest threshold directly from DataStore rather
                // than from _state, which may lag behind if two rapid calls are in
                // flight and the collector has not yet propagated the first write.
                val persistedSuggest = prefs.tagSuggestThresholdFlow
                    .catch { e ->
                        recordNonFatal("tagdict_suggest_threshold_flow_failed", e)
                        emit(0.60f)
                    }
                    .first()
                if (persistedSuggest > safe - 0.05f) {
                    prefs.saveTagSuggestThreshold((safe - 0.05f).coerceAtLeast(0f))
                }
                logBreadcrumb("tagdict_threshold_changed_auto")
            }
        }
    }

    fun setSuggestThreshold(value: Float) {
        viewModelScope.launch {
            thresholdMutex.withLock {
                val safe = value.coerceIn(0f, _state.value.autoThreshold - 0.05f)
                prefs.saveTagSuggestThreshold(safe)
                logBreadcrumb("tagdict_threshold_changed_suggest")
            }
        }
    }

    /** Editing an existing `custom_` row — D12 guarantees the repository rejects any other key. */
    fun saveOverride(row: TagDictionaryRow) {
        viewModelScope.launch {
            dictionaryRepo.upsert(
                TagOverride(
                    key      = row.key,
                    category = row.category,
                    labels   = mapOf("en" to row.labelEn).filterValues { it.isNotBlank() },
                    patterns = row.rules.filter { it.isNotBlank() },
                )
            )
            logBreadcrumb("tagdict_custom_tag_saved")
            _state.update { it.copy(editingKey = null) }
        }
    }

    /**
     * D12: creates a brand-new user tag under the `custom_` namespace. [label] is slugified into
     * a stable key (collision-avoided against every currently registered key, system or custom)
     * so it can never shadow a system entry.
     */
    fun createCustomTag(label: String, category: TagCategory = TagCategory.CUSTOM, rules: List<String>) {
        val trimmedLabel = label.trim()
        if (trimmedLabel.isBlank()) return
        viewModelScope.launch {
            val existingKeys = TagDictionary.all().map { it.key }.toSet()
            val key = uniqueCustomKey(trimmedLabel, existingKeys)
            dictionaryRepo.upsert(
                TagOverride(
                    key      = key,
                    category = category,
                    labels   = mapOf("en" to trimmedLabel),
                    patterns = rules.filter { it.isNotBlank() },
                )
            )
            logBreadcrumb("tagdict_custom_tag_saved")
            _state.update { it.copy(isCreatingCustomTag = false) }
        }
    }

    /** D12: only meaningful for `custom_` rows — the repository would reject a system-key delete anyway (no-op). */
    fun resetEntry(key: String) {
        viewModelScope.launch {
            dictionaryRepo.delete(key)
        }
    }

    /** F2: "delete all custom tags" — confirmed via [onRequestResetAll] in the Screen before this runs. */
    fun resetAll() {
        viewModelScope.launch {
            val clearedCount = _state.value.rows.count { it.isSystem.not() }
            dictionaryRepo.resetAll()
            logBreadcrumb("tagdict_custom_tags_reset: count=$clearedCount")
            _state.update { it.copy(isConfirmingResetAll = false) }
            _events.send(TagDictionaryEvent.CustomTagsCleared)
        }
    }

    private fun buildRows(): List<TagDictionaryRow> =
        TagDictionary.all()
            .sortedBy { it.key }
            .map { e ->
                TagDictionaryRow(
                    key      = e.key,
                    category = e.category,
                    labelEn  = e.labels["en"].orEmpty(),
                    rules    = e.rules.map { rule -> TagDictionary.renderRuleLine(rule) }
                        .filter { it.isNotBlank() },
                )
            }

    private fun uniqueCustomKey(label: String, existingKeys: Set<String>): String {
        val base = "$CUSTOM_KEY_PREFIX${slugify(label)}"
        if (base !in existingKeys) return base
        var suffix = 2
        while ("${base}_$suffix" in existingKeys) suffix++
        return "${base}_$suffix"
    }

    private fun slugify(label: String): String {
        val slug = label.trim().lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
        return slug.ifBlank { "tag" }
    }

    private fun logBreadcrumb(message: String) {
        FirebaseCrashlytics.getInstance().log(message)
    }
}
