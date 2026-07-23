package com.mmg.manahub.feature.communitydecks.domain

import com.mmg.manahub.core.model.CommunityDeck
import com.mmg.manahub.feature.communitydecks.domain.usecase.ImportCommunityDeckUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State of a single Community Deck import job, keyed by `archidektId` (see
 * [CommunityDeckImportCoordinator]).
 */
sealed interface ImportJobState {
    /** Card-resolution is in progress. [processed] / [total] are PHYSICAL card counts. */
    data class Running(val processed: Int, val total: Int) : ImportJobState

    /** The import completed. [resolvedCount] cards were added to [deckId]; [failedCount] were
     * skipped (never aborts the batch — see [ImportCommunityDeckUseCase]). */
    data class Success(val deckId: String, val resolvedCount: Int, val failedCount: Int) : ImportJobState

    /** The import failed before completing (deck creation or a repository write threw). */
    data class Error(val message: String) : ImportJobState
}

/**
 * Process-lifetime coordinator for Community Deck imports (bug fix, 2026-07-22).
 *
 * ## Why this exists
 * [CommunityDeckDetailViewModel] previously launched the import on `viewModelScope`, which is
 * cancelled the instant the ViewModel is cleared — which happens as soon as the user navigates away
 * from the deck detail screen. [ImportDeckCardsUseCase] resolves cards over the network (the slow,
 * cancellable part) and only then performs its single atomic deck write, so a mid-resolution
 * cancellation used to be silent — the user would return to a Deck List with nothing imported and
 * no error, or (before the Resolve-then-write fix) a half-built deck.
 *
 * This class owns the import coroutine on an app-scoped [CoroutineScope] that outlives any single
 * screen/ViewModel, so navigating away no longer cancels an in-flight import. It does NOT survive an
 * actual process death — nothing is written to Room until [ImportDeckCardsUseCase]'s single atomic
 * write at the very end of resolution, so a process kill mid-resolution simply loses the in-flight
 * attempt cleanly (no orphan/partial data), which is an intentional scope boundary, not an
 * oversight. Reaching for WorkManager-level durability here would be over-engineering unless a
 * concrete case shows the app is killed in the background mid-import often enough to matter.
 *
 * ## Contract
 * - [startImport] is idempotent per `archidektId`: a second call while a job is already running for
 *   the same deck is a no-op (re-entry guard — this REPLACES the `state.isImporting` guard that used
 *   to live in [CommunityDeckDetailViewModel]).
 * - [importState] returns a hot [StateFlow] a ViewModel can collect from `init` — a FRESH ViewModel
 *   instance for the same `archidektId` (e.g. the user re-opens the screen after navigating away
 *   mid-import) immediately observes wherever the job currently is, including a terminal result that
 *   already landed while the screen was closed.
 * - [markDeliveredIfFirst] gates one-shot toast/navigation delivery: it returns `true` exactly once
 *   per terminal state per [startImport] call, so a ViewModel re-collecting an already-delivered
 *   terminal state (e.g. the user reopens the screen after the import already finished) does not
 *   replay a stale toast or a stale navigation event.
 */
class CommunityDeckImportCoordinator(
    private val importCommunityDeck: ImportCommunityDeckUseCase,
    private val appScope: CoroutineScope,
) {

    private val stateFlows = mutableMapOf<Int, MutableStateFlow<ImportJobState?>>()
    private val jobs = mutableMapOf<Int, Job>()
    /** archidektIds whose CURRENT terminal state has already been delivered (toast/nav shown). */
    private val delivered = mutableSetOf<Int>()

    /** The live state of the import job for [archidektId], or a flow of `null` if none ever ran. */
    fun importState(archidektId: Int): StateFlow<ImportJobState?> = stateFor(archidektId).asStateFlow()

    /**
     * Starts importing [deck] on the app-scoped coroutine. No-ops if a job for
     * [CommunityDeck.archidektId] is already running (re-entry guard).
     */
    fun startImport(deck: CommunityDeck) {
        val archidektId = deck.archidektId
        synchronized(jobs) {
            if (jobs[archidektId]?.isActive == true) return

            // A fresh import cycle re-arms the one-shot delivery gate for this archidektId.
            synchronized(delivered) { delivered.remove(archidektId) }

            val stateFlow = stateFor(archidektId)
            val totalPhysicalCards = deck.cards.sumOf { it.quantity }
            stateFlow.value = ImportJobState.Running(processed = 0, total = totalPhysicalCards)

            jobs[archidektId] = appScope.launch {
                val result = importCommunityDeck(
                    deck = deck,
                    onProgress = { processed, total -> stateFlow.value = ImportJobState.Running(processed, total) },
                )
                stateFlow.value = when (result) {
                    is ImportCommunityDeckUseCase.ImportResult.Success ->
                        ImportJobState.Success(result.deckId, result.resolvedCount, result.failedCount)
                    is ImportCommunityDeckUseCase.ImportResult.Error ->
                        ImportJobState.Error(result.message)
                }
            }
        }
    }

    /**
     * Returns `true` the FIRST time this is called for the CURRENT terminal state of
     * [archidektId] (since the most recent [startImport] call) — `false` on every subsequent call,
     * including from a fresh ViewModel instance re-observing after navigation. Callers must gate
     * one-shot toast/navigation delivery on this return value.
     */
    fun markDeliveredIfFirst(archidektId: Int): Boolean = synchronized(delivered) {
        delivered.add(archidektId)
    }

    private fun stateFor(archidektId: Int): MutableStateFlow<ImportJobState?> = synchronized(stateFlows) {
        stateFlows.getOrPut(archidektId) { MutableStateFlow(null) }
    }
}
