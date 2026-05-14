package com.mmg.manahub.feature.tagdictionary.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.TagCategory
import com.mmg.manahub.core.tagging.TagDictionary
import com.mmg.manahub.core.tagging.TagDictionaryRepository
import com.mmg.manahub.core.tagging.TagOverride
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagDictionaryRow(
    val key:        String,
    val category:   TagCategory,
    val labelEn:    String,
    val labelEs:    String,
    val labelDe:    String,
    val patternsEn: List<String>,
    val patternsEs: List<String>,
    val patternsDe: List<String>,
)

data class TagDictionaryUiState(
    val rows:             List<TagDictionaryRow> = emptyList(),
    val autoThreshold:    Float = 0.90f,
    val suggestThreshold: Float = 0.60f,
    val editingKey:       String? = null,
    val query:            String  = "",
)

@HiltViewModel
class TagDictionaryViewModel @Inject constructor(
    private val dictionaryRepo: TagDictionaryRepository,
    private val prefs:          UserPreferencesDataStore,
) : ViewModel() {

    private val _state = MutableStateFlow(TagDictionaryUiState())
    val state: StateFlow<TagDictionaryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { dictionaryRepo.loadAndApply(); refreshRows() }
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
    fun onStartEdit(key: String) = _state.update { it.copy(editingKey = key) }
    fun onDismissEdit()          = _state.update { it.copy(editingKey = null) }

    fun setAutoThreshold(value: Float) {
        viewModelScope.launch {
            // Keep auto > suggest by at least 0.05.
            val safe = value.coerceIn(0.05f, 1f)
            prefs.saveTagAutoThreshold(safe)
            // Read the persisted suggest threshold directly from DataStore rather
            // than from _state, which may lag behind if two rapid calls are in
            // flight and the collector has not yet propagated the first write.
            val persistedSuggest = prefs.tagSuggestThresholdFlow
                .catch { emit(0.60f) }
                .first()
            if (persistedSuggest > safe - 0.05f) {
                prefs.saveTagSuggestThreshold((safe - 0.05f).coerceAtLeast(0f))
            }
        }
    }

    fun setSuggestThreshold(value: Float) {
        viewModelScope.launch {
            val safe = value.coerceIn(0f, _state.value.autoThreshold - 0.05f)
            prefs.saveTagSuggestThreshold(safe)
        }
    }

    fun saveOverride(row: TagDictionaryRow) {
        viewModelScope.launch {
            dictionaryRepo.upsert(
                TagOverride(
                    key      = row.key,
                    category = row.category,
                    labels   = mapOf(
                        "en" to row.labelEn,
                        "es" to row.labelEs,
                        "de" to row.labelDe,
                    ).filterValues { it.isNotBlank() },
                    patterns = mapOf(
                        "en" to row.patternsEn,
                        "es" to row.patternsEs,
                        "de" to row.patternsDe,
                    ).filterValues { it.isNotEmpty() },
                )
            )
            refreshRows()
            _state.update { it.copy(editingKey = null) }
        }
    }

    fun resetEntry(key: String) {
        viewModelScope.launch {
            dictionaryRepo.delete(key)
            refreshRows()
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            dictionaryRepo.resetAll()
            refreshRows()
        }
    }

    private suspend fun refreshRows() {
        // Make sure singleton is fresh before reading.
        dictionaryRepo.loadAndApply()
        val rows = TagDictionary.all()
            .sortedBy { it.key }
            .map { e ->
                TagDictionaryRow(
                    key        = e.key,
                    category   = e.category,
                    labelEn    = e.labels["en"].orEmpty(),
                    labelEs    = e.labels["es"].orEmpty(),
                    labelDe    = e.labels["de"].orEmpty(),
                    patternsEn = e.patterns["en"].orEmpty(),
                    patternsEs = e.patterns["es"].orEmpty(),
                    patternsDe = e.patterns["de"].orEmpty(),
                )
            }
        _state.update { it.copy(rows = rows) }
        // Touch overrides flow once so subsequent edits trigger refresh chain.
        dictionaryRepo.overridesFlow.first()
    }
}
