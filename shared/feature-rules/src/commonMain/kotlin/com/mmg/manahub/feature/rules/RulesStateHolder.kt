package com.mmg.manahub.feature.rules

import com.mmg.manahub.core.common.CrashReporter
import com.mmg.manahub.core.domain.rules.*
import com.mmg.manahub.core.model.rules.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.koin.dsl.module

data class RulesUiState(
    val edition: RulesEdition? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val query: String = "",
    val searching: Boolean = false,
    val results: RulesSearchPage = RulesSearchPage(emptyList(), 0, 0),
    val reference: String? = null,
    val updating: Boolean = false,
    val updateResult: RulesUpdateResult? = null,
    val searchFailed: Boolean = false,
)

class RulesStateHolder(
    private val load: LoadRulesUseCase,
    private val search: SearchRulesUseCase,
    private val update: UpdateRulesUseCase,
    private val reporter: CrashReporter,
    private val scope: CoroutineScope,
    private val entrySource: String = "navigation",
) {
    private val mutableState = MutableStateFlow(RulesUiState())
    val state: StateFlow<RulesUiState> = mutableState.asStateFlow()
    private var searchJob: Job? = null
    private var updateJob: Job? = null
    private var generation = 0L
    private var openGeneration = 0L
    private var openJob: Job? = null
    private var opened = false
    private fun reportFailure(phase: String, message: String) {
        reporter.setCustomKey("rules_failure_category", "unexpected")
        reporter.setCustomKey("rules_phase", phase)
        reporter.recordException(IllegalStateException(message))
    }

    fun open(edition: String?, reference: String?, query: String, offset: Int = 0) {
        val token = ++openGeneration
        openJob?.cancel()
        searchJob?.cancel()
        val queryGeneration = generation
        mutableState.value = mutableState.value.copy(loading = true, failed = false, query = query.take(200))
        if (!opened) {
            reporter.setCustomKey("rules_entry_source", entrySource.takeIf { it in setOf("profile", "home_shortcut", "home_tip", "navigation") } ?: "navigation")
            reporter.setCustomKey("rules_view", if (reference == null) "index" else "reader")
            reporter.log("rules_open")
            opened = true
        }
        openJob = scope.launch {
            try {
                val loaded = load(edition)
                if (token != openGeneration) return@launch
                mutableState.value = mutableState.value.copy(edition = loaded, loading = false, failed = false, reference = reference)
                val references = reference?.let { if (it.startsWith("related:")) it.removePrefix("related:").split(',') else listOf(it) }.orEmpty()
                if (references.any { ref -> loaded.nodes.none { it.id == ref } }) reporter.log("rules_reference_missing")
                changeQuery(if (generation == queryGeneration) query else state.value.query, if (generation == queryGeneration) offset else 0)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (token == openGeneration) { mutableState.value = mutableState.value.copy(loading = false, failed = true); reportFailure("load", "rules_baseline_invalid") } }
        }
    }

    fun changeQuery(query: String, offset: Int = 0) {
        val capturedQuery = query.take(200)
        val token = ++generation
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(query = capturedQuery, searchFailed = false, searching = capturedQuery.isNotBlank(), results = RulesSearchPage(emptyList(), 0, 0))
        val edition = state.value.edition ?: return
        if (capturedQuery.isBlank()) return
        searchJob = scope.launch {
            delay(250)
            try {
                val result = search(edition, capturedQuery, offset)
                if (generation == token && state.value.edition?.id == edition.id && state.value.query == capturedQuery) {
                    mutableState.value = mutableState.value.copy(searching = false, results = result)
                    reporter.setCustomKey("rules_query_length_bucket", when (capturedQuery.length) { in 1..10 -> "1_10"; in 11..50 -> "11_50"; in 51..100 -> "51_100"; else -> "101_200" })
                    reporter.setCustomKey("rules_result_count_bucket", when (result.total) { 0 -> "0"; in 1..10 -> "1_10"; in 11..50 -> "11_50"; else -> "over_50" })
                    reporter.log("rules_search_completed")
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == token) { mutableState.value = mutableState.value.copy(searching = false, searchFailed = true); reportFailure("page", "rules_unexpected_failure") } }
        }
    }

    fun page(offset: Int) {
        val edition = state.value.edition ?: return
        val query = state.value.query
        val token = ++generation
        searchJob?.cancel()
        mutableState.value = mutableState.value.copy(searching = true, searchFailed = false)
        searchJob = scope.launch {
            try {
                val page = search(edition, query, offset)
                if (generation == token && state.value.edition?.id == edition.id && state.value.query == query) mutableState.value = mutableState.value.copy(results = page, searching = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (generation == token) { mutableState.value = mutableState.value.copy(searching = false, searchFailed = true); reporter.recordException(IllegalStateException("rules_unexpected_failure")) } }
        }
    }

    fun checkForUpdates() {
        if (updateJob?.isActive == true) return
        updateJob = scope.launch {
            mutableState.value = mutableState.value.copy(updating = true, updateResult = null)
            reporter.setCustomKey("rules_failure_category", "none")
            reporter.log("rules_update_started")
            try {
                val result = update()
                mutableState.value = mutableState.value.copy(updating = false, updateResult = result)
                reporter.setCustomKey("rules_failure_category", when ((result as? RulesUpdateResult.Failed)?.category) { RulesFailure.NETWORK -> "network"; RulesFailure.STORAGE -> "storage"; RulesFailure.INVALID_SOURCE -> "candidate_invalid"; RulesFailure.BASELINE -> "unexpected"; null -> "none" })
                reporter.setCustomKey("rules_update_result", when (result) { is RulesUpdateResult.Installed -> "updated"; RulesUpdateResult.Unchanged -> "up_to_date"; is RulesUpdateResult.Failed -> "failed" })
                reporter.log("rules_update_result")
            } catch (cancelled: CancellationException) { mutableState.value = mutableState.value.copy(updating = false); throw cancelled }
        }
    }

    fun reload() {
        val next = (state.value.updateResult as? RulesUpdateResult.Installed)?.edition ?: return
        mutableState.value = mutableState.value.copy(edition = next, updateResult = null, reference = null)
        changeQuery(state.value.query)
    }
}

val rulesFeatureModule = module {
    factory { LoadRulesUseCase(get()) }
    factory { SearchRulesUseCase(get()) }
    factory { UpdateRulesUseCase(get()) }
}
