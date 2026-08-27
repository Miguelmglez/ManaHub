package com.mmg.manahub.feature.communitydecks.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.util.recordNonFatal
import com.mmg.manahub.feature.communitydecks.domain.CommunityDeckImportCoordinator
import com.mmg.manahub.feature.communitydecks.domain.ImportJobState
import com.mmg.manahub.feature.communitydecks.domain.usecase.GetCommunityDeckUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Community Deck detail screen.
 *
 * Loads a single Archidekt deck (cache-first via the repository) and drives a
 * resilient import into a new local ManaHub deck. One-shot navigation / toast
 * effects are delivered through a buffered [Channel].
 *
 * ## Import survives navigation (bug fix, 2026-07-22)
 * The actual import runs on [CommunityDeckImportCoordinator], an app-scoped singleton — NOT on
 * `viewModelScope`, which is cancelled the instant this ViewModel is cleared (i.e. the moment the
 * user navigates away from this screen). [observeImportState] merely REFLECTS the coordinator's
 * live/terminal state into [uiState] and, via [CommunityDeckImportCoordinator.markDeliveredIfFirst],
 * delivers the one-shot toast/navigation events exactly once per import — so navigating away
 * mid-import and returning later (a fresh ViewModel instance for the same `archidektId`) picks up
 * exactly where the background import currently is, including an already-finished result, without
 * ever replaying a stale toast.
 */
class CommunityDeckDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val getCommunityDeck: GetCommunityDeckUseCase,
    private val importCoordinator: CommunityDeckImportCoordinator,
    private val userCardRepository: UserCardRepository,
) : ViewModel() {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    private val archidektId: Int = savedStateHandle["archidektId"] ?: 0

    private val _uiState = MutableStateFlow<CommunityDeckDetailUiState>(CommunityDeckDetailUiState.Loading)
    val uiState: StateFlow<CommunityDeckDetailUiState> = _uiState.asStateFlow()

    private val _events = Channel<CommunityDeckDetailEvent>(Channel.BUFFERED)
    val events: Flow<CommunityDeckDetailEvent> = _events.receiveAsFlow()

    /**
     * Independent of [_uiState] on purpose: a [loadDeck] retry blanks [_uiState] to [Loading] and
     * rebuilds [CommunityDeckDetailUiState.Content] from scratch, but [observeOwnedCardIdentityKeys]
     * is a long-lived collector started once in [init] — it may not re-emit between two [loadDeck]
     * calls, so the fresh [Content] must be able to read the LATEST known snapshot directly rather
     * than default back to empty.
     */
    private val _ownedCardIdentityKeys = MutableStateFlow<Set<String>>(emptySet())

    init {
        crashlytics.setCustomKey("community_deck_archidekt_id", archidektId)
        if (archidektId <= 0) {
            recordNonFatal("community_deck_invalid_id", IllegalArgumentException("archidektId=$archidektId"))
        }
        loadDeck()
        observeOwnedCardIdentityKeys()
        observeImportState()
    }

    /** (Re)loads the deck. Safe to call from a retry button. */
    fun loadDeck() {
        viewModelScope.launch {
            _uiState.value = CommunityDeckDetailUiState.Loading
            when (val result = getCommunityDeck(archidektId)) {
                is DataResult.Success -> {
                    crashlytics.setCustomKey("community_deck_card_count", result.data.cards.size)
                    crashlytics.log("community_deck_load_success")
                    // Re-sync with the coordinator's CURRENT import state right now (not just future
                    // emissions) — a background import can already be Running/terminal if this is a
                    // fresh ViewModel instance re-observing after navigation (see class KDoc).
                    val currentImportState = importCoordinator.importState(archidektId).value
                    _uiState.value = CommunityDeckDetailUiState.Content(
                        deck = result.data,
                        isStale = result.isStale,
                        // Sideboard is collapsed by default to keep the focus on the mainboard.
                        sideboardExpanded = false,
                        isImporting = currentImportState is ImportJobState.Running,
                        importProgress = (currentImportState as? ImportJobState.Running)
                            ?.let { it.processed to it.total },
                        ownedCardIdentityKeys = _ownedCardIdentityKeys.value,
                    )
                }
                is DataResult.Error -> {
                    crashlytics.log("community_deck_load_error")
                    _uiState.value = CommunityDeckDetailUiState.Error(result.message)
                }
            }
        }
    }

    fun toggleCommander() {
        _uiState.update { current ->
            if (current is CommunityDeckDetailUiState.Content) {
                current.copy(commanderExpanded = !current.commanderExpanded)
            } else current
        }
    }

    fun toggleMainboard() {
        _uiState.update { current ->
            if (current is CommunityDeckDetailUiState.Content) {
                current.copy(mainboardExpanded = !current.mainboardExpanded)
            } else current
        }
    }

    fun toggleSideboard() {
        _uiState.update { current ->
            if (current is CommunityDeckDetailUiState.Content) {
                current.copy(sideboardExpanded = !current.sideboardExpanded)
            } else current
        }
    }

    /** Toggles the collapsed state of a sub-section (category) within a board. */
    fun toggleSection(sectionLabel: String) {
        _uiState.update { current ->
            if (current is CommunityDeckDetailUiState.Content) {
                val newCollapsed = if (sectionLabel in current.collapsedSections) {
                    current.collapsedSections - sectionLabel
                } else {
                    current.collapsedSections + sectionLabel
                }
                current.copy(collapsedSections = newCollapsed)
            } else current
        }
    }

    /**
     * Continuously tracks the set of "already owned" card identity keys — the same convention
     * used across Card Versions & Languages: [com.mmg.manahub.core.model.Card.oracleId] falling
     * back to the exact English [com.mmg.manahub.core.model.Card.name] when [oracleId] is blank
     * (mirrors `ScannerViewModel.observeOwnedCardIdentityKeys()`). This is a LIVE collector (not a
     * one-shot fetch) so the "already owned" badges update immediately if the user adds a card to
     * their collection while this screen is open.
     */
    private fun observeOwnedCardIdentityKeys() {
        viewModelScope.launch {
            userCardRepository.observeCollection().collect { rows ->
                val keys = rows.mapTo(mutableSetOf()) { it.card.oracleId.ifBlank { it.card.name } }
                _ownedCardIdentityKeys.value = keys
                _uiState.update { current ->
                    if (current is CommunityDeckDetailUiState.Content) {
                        current.copy(ownedCardIdentityKeys = keys)
                    } else {
                        current
                    }
                }
            }
        }
    }

    /**
     * Reflects [importCoordinator]'s live/terminal state for this screen's `archidektId` into
     * [uiState], and delivers the one-shot toast/navigation events exactly once per import via
     * [CommunityDeckImportCoordinator.markDeliveredIfFirst] — see the class KDoc.
     */
    private fun observeImportState() {
        viewModelScope.launch {
            importCoordinator.importState(archidektId).collect { jobState ->
                when (jobState) {
                    null -> Unit
                    is ImportJobState.Running -> _uiState.update { current ->
                        if (current is CommunityDeckDetailUiState.Content) {
                            current.copy(isImporting = true, importProgress = jobState.processed to jobState.total)
                        } else {
                            current
                        }
                    }
                    is ImportJobState.Success -> {
                        clearImportingFlag()
                        if (importCoordinator.markDeliveredIfFirst(archidektId)) {
                            val total = jobState.resolvedCount + jobState.failedCount
                            crashlytics.setCustomKey("community_deck_import_resolved", jobState.resolvedCount)
                            crashlytics.setCustomKey("community_deck_import_failed", jobState.failedCount)
                            crashlytics.log("community_deck_import_success")
                            _events.send(
                                CommunityDeckDetailEvent.ShowImportResult(
                                    isError = false,
                                    resolvedCount = jobState.resolvedCount,
                                    totalCount = total,
                                ),
                            )
                            _events.send(CommunityDeckDetailEvent.NavigateToDeck(jobState.deckId))
                        }
                    }
                    is ImportJobState.Error -> {
                        clearImportingFlag()
                        if (importCoordinator.markDeliveredIfFirst(archidektId)) {
                            crashlytics.log("community_deck_import_error")
                            _events.send(CommunityDeckDetailEvent.ShowImportResult(isError = true))
                        }
                    }
                }
            }
        }
    }

    private fun clearImportingFlag() {
        _uiState.update { current ->
            if (current is CommunityDeckDetailUiState.Content) {
                current.copy(isImporting = false, importProgress = null)
            } else {
                current
            }
        }
    }

    /**
     * Starts importing the loaded deck into a new local deck via [importCoordinator]. No-ops unless
     * we are in [CommunityDeckDetailUiState.Content] and not already importing — the AUTHORITATIVE
     * re-entry guard lives in [CommunityDeckImportCoordinator.startImport] (this is a fast local
     * check only).
     */
    fun importDeck() {
        val state = _uiState.value
        if (state !is CommunityDeckDetailUiState.Content || state.isImporting) return

        crashlytics.log("community_deck_import_started")
        importCoordinator.startImport(state.deck)
    }
}
