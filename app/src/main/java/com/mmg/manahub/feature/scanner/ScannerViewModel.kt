package com.mmg.manahub.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DataResult
import com.mmg.manahub.core.domain.usecase.collection.AddCardToCollectionUseCase
import com.mmg.manahub.core.util.AnalyticsHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the scanner screen.
 *
 * Receives [RecognitionResult] from [CardRecognizer] and applies:
 * - **Stability buffer**: the same scryfallId must appear [STABILITY_FRAMES] consecutive
 *   times before the card is treated as confirmed.
 * - **Anti-duplicate guard**: once a card is added to the session, the same card is
 *   ignored for [ANTI_DUPLICATE_MS] milliseconds to avoid re-adding when the user
 *   hasn't moved the camera.
 * - **Set lock filter**: when [ScannerUiState.lockedSetCode] is non-null, only cards
 *   whose [Card.setCode] matches are processed; others are silently ignored.
 * - **Language mismatch**: when [ScannerUiState.selectedLanguage] is not "en" and the
 *   scanned card's [Card.lang] differs, the card is displayed but not auto-added in
 *   Quick Mode, and a [ScannerUiState.languageMismatch] indicator is shown.
 * - **Ambiguity selector**: when the recognition result is ambiguous and the scanner is
 *   in normal mode (not Quick, not Lookup Only), a [DropdownMenu] is shown to let the
 *   user confirm or skip without interrupting the camera.
 *
 * Modes (controlled by the settings sheet):
 * - **Quick Mode ON**:  auto-adds the confirmed card to the session and collection.
 * - **Lookup Only ON**: only shows the card in the bottom bar, never adds it.
 * - **Neither**:        shows the card in the bottom bar; user taps "+" to add.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val addToCollection: AddCardToCollectionUseCase,
    private val analyticsHelper: AnalyticsHelper,
    private val soundManager: SoundManager,
    val hashDatabase: HashDatabase,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val workManager: WorkManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    // ── Stability buffer ─────────────────────────────────────────────────────
    private val recentMatches = ArrayDeque<String>(STABILITY_FRAMES)

    // ── Anti-duplicate guard ─────────────────────────────────────────────────
    private var lastAddedId: String? = null
    private var lastAddedTime: Long = 0L

    companion object {
        /** Number of consecutive identical matches required before a card is confirmed. */
        private const val STABILITY_FRAMES = 3

        /** Minimum time in ms before the same card can be added again. */
        private const val ANTI_DUPLICATE_MS = 800L

        /** Default language code — used to determine when the language filter is active. */
        private const val DEFAULT_LANGUAGE = "en"
    }

    init {
        // Reflect the persisted hash-DB version in the UI state.
        viewModelScope.launch {
            userPreferencesDataStore.hashDbVersionFlow.collect { version ->
                _uiState.update { it.copy(hashDbVersion = version) }
            }
        }

        // Mirror the WorkManager running state so the settings sheet shows an updating indicator.
        viewModelScope.launch {
            workManager
                .getWorkInfosForUniqueWorkLiveData(HashDatabaseUpdateWorker.WORK_NAME)
                .asFlow()
                .collect { infos ->
                    val isRunning = infos.any { it.state == WorkInfo.State.RUNNING }
                    _uiState.update { it.copy(isHashDbUpdating = isRunning) }
                }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  pHash recognition entry point — called by CardRecognizer
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Processes a [RecognitionResult] from [CardRecognizer].
     *
     * - [RecognitionResult.NoCard]     → clears the overlay and search indicator.
     * - [RecognitionResult.Detected]   → shows the outline but keeps searching.
     * - [RecognitionResult.Identified] → applies set lock, language mismatch,
     *   stability + anti-duplicate logic, then routes to the appropriate mode.
     */
    fun onRecognitionResult(result: RecognitionResult) {
        // onRecognitionResult is called from CardRecognizer's Dispatchers.Default coroutine.
        // recentMatches, lastAddedId, and lastAddedTime are not thread-safe, so we dispatch
        // the entire handler onto the main thread where those fields are always accessed.
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            processRecognitionResult(result)
        }
    }

    private fun processRecognitionResult(result: RecognitionResult) {
        when (result) {
            RecognitionResult.NoCard -> {
                recentMatches.clear()
                _uiState.update {
                    it.copy(
                        detectedCorners = null,
                        isSearching = false,
                        languageMismatch = false,
                    )
                }
            }

            is RecognitionResult.Detected -> {
                recentMatches.clear()
                _uiState.update {
                    it.copy(
                        detectedCorners = result.corners,
                        isSearching = true,
                        languageMismatch = false,
                    )
                }
            }

            is RecognitionResult.Identified -> {
                _uiState.update { it.copy(detectedCorners = result.corners) }

                val state = _uiState.value

                // ── Set lock filter (applied before stability) ───────────────
                if (state.lockedSetCode != null &&
                    result.card.setCode.lowercase() != state.lockedSetCode.lowercase()
                ) {
                    recentMatches.clear()
                    return
                }

                val id = result.card.scryfallId

                // ── Anti-duplicate guard ─────────────────────────────────────
                val now = System.currentTimeMillis()
                if (id == lastAddedId && now - lastAddedTime < ANTI_DUPLICATE_MS) {
                    recentMatches.clear()
                    return
                }

                // ── Stability buffer ─────────────────────────────────────────
                if (recentMatches.size >= STABILITY_FRAMES) recentMatches.removeFirst()
                recentMatches.addLast(id)
                if (recentMatches.size < STABILITY_FRAMES || recentMatches.any { it != id }) return

                // ── Card confirmed — reset buffer ────────────────────────────
                recentMatches.clear()
                _uiState.update { it.copy(isSearching = false, lastDetectedCard = result.card) }

                val confirmedState = _uiState.value

                when {
                    confirmedState.isLookupOnly -> {
                        // Show card in the bottom bar; language mismatch is irrelevant in this mode
                        _uiState.update { it.copy(languageMismatch = false) }
                    }

                    confirmedState.isQuickMode -> {
                        // ── Language mismatch check (Quick Mode) ─────────────
                        if (confirmedState.selectedLanguage != DEFAULT_LANGUAGE &&
                            result.card.lang != confirmedState.selectedLanguage
                        ) {
                            _uiState.update { it.copy(languageMismatch = true) }
                            return
                        }
                        _uiState.update { it.copy(languageMismatch = false) }
                        quickAddCard(result.card)
                    }

                    result.ambiguous -> {
                        // ── Ambiguous match — show inline selector (normal mode) ──
                        _uiState.update {
                            it.copy(
                                showAmbiguitySelector = true,
                                languageMismatch = false,
                            )
                        }
                    }

                    else -> {
                        // Normal mode, unambiguous: card shown in bar for manual confirmation
                        _uiState.update { it.copy(languageMismatch = false) }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Quick add — auto-adds a confirmed card (Quick Mode)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adds [card] to the scan session and persists it to the collection.
     * Updates [lastAddedId] and [lastAddedTime] on success to activate the anti-duplicate guard.
     * Used both by Quick Mode (automatic) and [onManualAddCurrentCard] (manual confirmation).
     */
    private fun quickAddCard(card: Card) {
        val state = _uiState.value
        viewModelScope.launch {
            // Optimistic update: add to session immediately
            addToSession(card)

            val result = addToCollection(
                scryfallId = card.scryfallId,
                isFoil = state.selectedIsFoil,
                condition = state.selectedCondition,
                language = state.selectedLanguage,
            )

            when (result) {
                is DataResult.Success -> {
                    analyticsHelper.logEvent(
                        "scanner_quick_add",
                        mapOf("card_id" to card.scryfallId, "mode" to "quick"),
                    )
                    // Activate anti-duplicate guard
                    lastAddedId = card.scryfallId
                    lastAddedTime = System.currentTimeMillis()

                    _uiState.update { it.copy(toastMessage = card.name) }

                    // Play price-based sound if enabled
                    if (_uiState.value.isSoundEnabled) {
                        soundManager.playForPrice(
                            priceEur = card.priceEur,
                            priceUsdFallback = card.priceUsd,
                        )
                    }

                    // Brief pause to prevent immediate re-scan of the same card
                    delay(1_500)
                }

                is DataResult.Error -> {
                    analyticsHelper.logEvent(
                        "scanner_quick_add_error",
                        mapOf("card_id" to card.scryfallId),
                    )
                    _uiState.update { it.copy(error = result.message) }
                    delay(2_000)
                    _uiState.update { it.copy(error = null) }
                }
            }
        }
    }

    /**
     * Merges [card] into the current [ScanSession].
     * Increments quantity if an entry with the same key (scryfallId + isFoil + language + condition)
     * already exists; otherwise appends a new [ScannedCard].
     */
    private fun addToSession(card: Card) {
        _uiState.update { state ->
            val existingIndex = state.scanSession.cards.indexOfFirst { entry ->
                entry.card.scryfallId == card.scryfallId &&
                    entry.isFoil == state.selectedIsFoil &&
                    entry.language == state.selectedLanguage &&
                    entry.condition == state.selectedCondition
            }
            val updatedCards = if (existingIndex >= 0) {
                state.scanSession.cards.toMutableList().also {
                    it[existingIndex] = it[existingIndex].copy(
                        quantity = it[existingIndex].quantity + state.selectedQuantity,
                    )
                }
            } else {
                state.scanSession.cards + ScannedCard(
                    card = card,
                    quantity = state.selectedQuantity,
                    isFoil = state.selectedIsFoil,
                    language = state.selectedLanguage,
                    condition = state.selectedCondition,
                    setCode = card.setCode,
                    timestamp = System.currentTimeMillis(),
                )
            }
            state.copy(scanSession = state.scanSession.copy(cards = updatedCards))
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Manual add (non-quick mode): user taps the confirm button in the bottom bar
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called when the user explicitly confirms adding the currently displayed card
     * from the bottom bar (non-quick mode). Reuses [quickAddCard] logic.
     */
    fun onManualAddCurrentCard() {
        val card = _uiState.value.lastDetectedCard ?: return
        quickAddCard(card)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Ambiguity selector
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Dismisses the inline ambiguity [DropdownMenu] without adding the card.
     * Clears [ScannerUiState.showAmbiguitySelector] and [ScannerUiState.lastDetectedCard].
     */
    fun onDismissAmbiguitySelector() {
        _uiState.update {
            it.copy(
                showAmbiguitySelector = false,
                lastDetectedCard = null,
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Price detail sheet (Lookup Only mode)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens the price detail [ModalBottomSheet] for the currently detected card.
     * Only meaningful when [ScannerUiState.isLookupOnly] is true.
     */
    fun onOpenPriceDetail() {
        _uiState.update { it.copy(showPriceDetailSheet = true) }
    }

    /**
     * Closes the price detail [ModalBottomSheet].
     */
    fun onClosePriceDetail() {
        _uiState.update { it.copy(showPriceDetailSheet = false) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Sound toggle
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Toggles sound effects on or off.
     */
    fun onToggleSound() {
        _uiState.update { it.copy(isSoundEnabled = !it.isSoundEnabled) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Mode bar actions
    // ─────────────────────────────────────────────────────────────────────────

    /** Toggles the foil chip in the mode bar. */
    fun onToggleFoil() {
        _uiState.update { it.copy(selectedIsFoil = !it.selectedIsFoil) }
    }

    /** Updates the selected language code. */
    fun onLanguageSelected(language: String) {
        _uiState.update { it.copy(selectedLanguage = language, languageMismatch = false) }
    }

    /** Updates the selected condition code. */
    fun onConditionSelected(condition: String) {
        _uiState.update { it.copy(selectedCondition = condition) }
    }

    /** Updates the selected quantity. */
    fun onQuantitySelected(quantity: Int) {
        _uiState.update { it.copy(selectedQuantity = quantity) }
    }

    /** Sets or clears the locked set filter. */
    fun onSetLockSelected(setCode: String?) {
        _uiState.update { it.copy(lockedSetCode = setCode) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Flash
    // ─────────────────────────────────────────────────────────────────────────

    /** Toggles the camera torch. */
    fun onToggleFlash() {
        _uiState.update { it.copy(isFlashOn = !it.isFlashOn) }
    }

    /**
     * Updates [ScannerUiState.hasFlash] after the camera binds and hardware availability
     * is confirmed via [androidx.camera.core.CameraInfo.hasFlashUnit].
     *
     * @param available True if the bound camera has a flash unit, false otherwise.
     */
    fun onFlashAvailabilityChanged(available: Boolean) {
        _uiState.update { it.copy(hasFlash = available) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Settings bottom sheet
    // ─────────────────────────────────────────────────────────────────────────

    /** Opens the settings bottom sheet. */
    fun onOpenSettings() {
        _uiState.update { it.copy(showSettingsSheet = true) }
    }

    /** Closes the settings bottom sheet. */
    fun onCloseSettings() {
        _uiState.update { it.copy(showSettingsSheet = false) }
    }

    /** Toggles Quick Mode (auto-add on detect). */
    fun onToggleQuickMode() {
        _uiState.update { it.copy(isQuickMode = !it.isQuickMode, languageMismatch = false) }
    }

    /** Toggles Lookup Only mode (show price, never add). */
    fun onToggleLookupOnly() {
        _uiState.update { it.copy(isLookupOnly = !it.isLookupOnly) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Queue bottom sheet
    // ─────────────────────────────────────────────────────────────────────────

    /** Opens the scan-queue bottom sheet. */
    fun onOpenQueue() {
        _uiState.update { it.copy(showQueueSheet = true, multiSelectedIds = emptySet()) }
    }

    /** Closes the scan-queue bottom sheet. */
    fun onCloseQueue() {
        _uiState.update { it.copy(showQueueSheet = false, multiSelectedIds = emptySet()) }
    }

    /** Removes a single [ScannedCard] from the session. */
    fun onRemoveSessionCard(entry: ScannedCard) {
        _uiState.update { state ->
            state.copy(
                scanSession = state.scanSession.copy(
                    cards = state.scanSession.cards.filter { it !== entry },
                ),
                multiSelectedIds = state.multiSelectedIds - entry.card.scryfallId,
            )
        }
    }

    /** Clears the entire scan session and resets the anti-duplicate guard. */
    fun onClearSession() {
        _uiState.update {
            it.copy(
                scanSession = ScanSession(),
                multiSelectedIds = emptySet(),
                showQueueSheet = false,
            )
        }
        lastAddedId = null
        lastAddedTime = 0L
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Multi-select in the queue sheet
    // ─────────────────────────────────────────────────────────────────────────

    /** Toggles the long-press selection state of a session entry. */
    fun onToggleMultiSelect(entry: ScannedCard) {
        _uiState.update { state ->
            val id = entry.card.scryfallId
            val updated = if (id in state.multiSelectedIds) {
                state.multiSelectedIds - id
            } else {
                state.multiSelectedIds + id
            }
            state.copy(multiSelectedIds = updated)
        }
    }

    /** Deletes all currently selected entries from the session. */
    fun onDeleteSelected() {
        _uiState.update { state ->
            state.copy(
                scanSession = state.scanSession.copy(
                    cards = state.scanSession.cards.filter {
                        it.card.scryfallId !in state.multiSelectedIds
                    },
                ),
                multiSelectedIds = emptySet(),
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Add all from queue to collection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Persists every [ScannedCard] in the session to the collection,
     * then closes the queue sheet.
     */
    fun onAddAllToCollection() {
        val cards = _uiState.value.scanSession.cards
        if (cards.isEmpty()) return

        viewModelScope.launch {
            cards.forEach { entry ->
                repeat(entry.quantity) {
                    addToCollection(
                        scryfallId = entry.card.scryfallId,
                        isFoil = entry.isFoil,
                        condition = entry.condition,
                        language = entry.language,
                    )
                }
            }
            analyticsHelper.logEvent(
                "scanner_add_all",
                mapOf("count" to cards.size.toString()),
            )
            onClearSession()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Toast dismissal
    // ─────────────────────────────────────────────────────────────────────────

    /** Clears the one-shot toast message after it has been displayed. */
    fun onToastDismissed() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Cleanup
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCleared() {
        super.onCleared()
        soundManager.release()
    }
}
