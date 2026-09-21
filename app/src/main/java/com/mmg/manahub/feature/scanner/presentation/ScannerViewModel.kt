package com.mmg.manahub.feature.scanner.presentation
// COMMENTS_REVIEWED: 2026-09-16

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.R
import com.mmg.manahub.core.data.queue.InMemoryCardQueueStore
import com.mmg.manahub.core.data.queue.PersistentCardQueueRepository
import com.mmg.manahub.core.data.queue.SharedPreferencesCardQueueStore
import com.mmg.manahub.core.domain.repository.CardQueueRepository
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.UserCardRepository
import com.mmg.manahub.core.domain.usecase.queue.AddAllToCollectionResult
import com.mmg.manahub.core.domain.usecase.queue.CardQueueActions
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.QueuedCard
import com.mmg.manahub.core.di.ApplicationScope
import com.mmg.manahub.core.ui.components.MagicToastType
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.util.recordSafeNonFatal
import com.mmg.manahub.feature.decks.domain.usecase.AddScannedCardsToDeckUseCase
import com.mmg.manahub.feature.decks.domain.usecase.DeckBoard
import com.mmg.manahub.feature.decks.domain.usecase.ScannedDeckCardInput
import com.mmg.manahub.feature.scanner.domain.model.RecognitionResult
import com.mmg.manahub.feature.scanner.presentation.ScannerViewModel.Companion.ANTI_DUPLICATE_MS
import com.mmg.manahub.feature.scanner.presentation.ScannerViewModel.Companion.HIGH_CONFIDENCE_FRAMES
import com.mmg.manahub.feature.scanner.presentation.ScannerViewModel.Companion.STABILITY_FRAMES
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Clears the transient per-frame detection overlay ([ScannerUiState.detectedCorners],
 * [ScannerUiState.isSearching]) before a covering sheet/overlay opens (W3,
 * `scanner-reliability-plan.md`, 2026-08-24). See [ScannerViewModel]'s class KDoc, "Camera stop
 * vs. recognition pause", for why this lives on every overlay-opening action rather than being
 * driven from the camera-binding side (this project has no Compose UI test infrastructure to
 * exercise that cross-layer wiring directly — see `CardRecognizerTest`/`ScannerViewModelTest`).
 */
private fun ScannerUiState.clearedForOverlay(): ScannerUiState =
    copy(detectedCorners = null, isSearching = false)

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
 * - **Language fallback (informational only, W2.11)**: [CardRecognizer] resolves the LOCALIZED
 *   printing whenever one exists, so [Card.lang] normally matches [ScannerUiState.selectedLanguage]
 *   already. When no printing exists in the selected language, [CardRecognizer] falls back to the
 *   English printing and flags [RecognitionResult.Identified.languageFallback] — the card is
 *   still added (never silently refused), with [ScannerUiState.languageMismatch] surfacing a
 *   non-blocking "no <lang> printing found — added as EN" badge.
 * - **Rate-limit cooldown (W2.10)**: [RecognitionResult.RateLimited] sets
 *   [ScannerUiState.rateLimitedUntilMs] — the recognizer suspends all further lookups until it
 *   elapses, and the UI shows a countdown badge instead of a card.
 * - **Ambiguity selector**: when the recognition result is ambiguous and the scanner is
 *   in normal mode (not Quick, not Lookup Only), a dialog is shown to let the user confirm.
 * - **Camera stop vs. recognition pause (W3, `scanner-reliability-plan.md`, 2026-08-24)**: every
 *   action that opens a covering sheet/overlay ([onOpenQueue], [onEditScannedCard],
 *   [onOpenVariantSelector], [onExpandVariantImage], [onOpenCardDetail], [onOpenPriceDetail])
 *   clears [ScannerUiState.detectedCorners]/[ScannerUiState.isSearching] via
 *   [ScannerUiState.clearedForOverlay] — `ScannerScreen`'s `CameraPreview` fully unbinds the
 *   camera for these same conditions, so no new [RecognitionResult] will arrive to refresh them
 *   while the overlay is open, and a resumed session must not show a stale outline/spinner left
 *   over from before it opened. This is independent of [onToggleRecognitionPaused] (the top-bar
 *   toggle), which only stops the analyzer while keeping the preview live.
 *
 * **Queue**: for the collection target, cards live in the app-wide [CardQueueRepository] (shared
 * with AddCard "Select multiple", persisted across process death); [ScannerUiState.scanSession] is a
 * synchronous snapshot of it, refreshed after every mutation made here and on every external
 * change. Commit actions (add all / per entry, collection / wishlist) go through the shared
 * [CardQueueActions] and run in the app scope, so leaving the Scanner mid-batch never cancels a
 * partially written commit.
 *
 * **Target** ([ScannerTarget], from the `deckId` nav argument): a [ScannerTarget.Deck] scanner keeps
 * its own persisted per-deck queue (never the shared collection queue) and only offers the deck
 * actions ([onAddEntryToDeck]/[onAddAllToDeck], through [AddScannedCardsToDeckUseCase]); the
 * collection/wishlist actions are refused for it, and vice versa.
 *
 * Modes (controlled by the settings sheet):
 * - **Quick Mode ON**:  auto-adds the confirmed card to the session.
 * - **Lookup Only ON**: only shows the card in the bottom bar, never adds it.
 * - **Neither**:        shows the card in the bottom bar; user taps "+" to add.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cardRepository: CardRepository,
    private val userCardRepository: UserCardRepository,
    sharedQueueRepository: CardQueueRepository,
    private val queueActions: CardQueueActions,
    private val addScannedCardsToDeck: AddScannedCardsToDeckUseCase,
    private val analyticsHelper: AnalyticsHelper,
    private val soundManager: SoundManager,
    @ApplicationContext private val context: Context,
    @ApplicationScope appScope: CoroutineScope,
) : ViewModel() {

    private val target: ScannerTarget = ScannerTarget.from(savedStateHandle)
    private val isCollectionTarget: Boolean = target is ScannerTarget.Collection

    // A deck scan must never land in (or clear) the shared collection queue.
    private val queueRepository: CardQueueRepository = when (target) {
        ScannerTarget.Collection -> sharedQueueRepository
        is ScannerTarget.Deck -> PersistentCardQueueRepository(
            SharedPreferencesCardQueueStore(context, SharedPreferencesCardQueueStore.deckQueueKey(target.deckId))
        )
        ScannerTarget.Invalid -> PersistentCardQueueRepository(InMemoryCardQueueStore())
    }

    private val isCommittingToDeck = MutableStateFlow(false)

    private val _uiState = MutableStateFlow(
        ScannerUiState(
            target = target,
            error = if (target == ScannerTarget.Invalid) context.getString(R.string.scanner_invalid_link) else null,
            scanSession = ScanSession(queueRepository.queue.value),
        ).withWriteGuards()
    )
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    // The repository is not synchronized: commit-side mutations stay on the main thread while
    // outliving this ViewModel.
    private val commitScope = CoroutineScope(appScope.coroutineContext + Dispatchers.Main.immediate)

    // ── Stability buffer ─────────────────────────────────────────────────────
    private val recentMatches = ArrayDeque<String>(STABILITY_FRAMES)

    // ── Rolling FPS counter (DEBUG only) ─────────────────────────────────────
    private val frameTimestamps = ArrayDeque<Long>(11)

    // ── Anti-duplicate guard ─────────────────────────────────────────────────
    private var lastAddedId: String? = null
    private var lastAddedTime: Long = 0L

    // ── Variant load job — cancelled if the user closes the sheet before load completes ──
    private var variantLoadJob: kotlinx.coroutines.Job? = null

    companion object {
        /**
         * Number of consecutive identical matches required before a card is confirmed.
         */
        private const val STABILITY_FRAMES = 3

        /**
         * Reduced stability requirement for high-confidence matches.
         * Since OCR + exact-name lookup always returns similarity = 1.0f,
         * [HIGH_CONFIDENCE_FRAMES] = 1 is always used.
         */
        private const val HIGH_CONFIDENCE_FRAMES = 1

        /** Minimum time in ms before the same card can be added again. */
        private const val ANTI_DUPLICATE_MS = 800L
    }

    init {
        observeSharedQueue()
        if (isCollectionTarget) observeOwnedCardIdentityKeys()
    }

    /** Keeps [ScannerUiState.scanSession] / [ScannerUiState.isCommittingQueue] in sync with changes made from other screens. */
    private fun observeSharedQueue() {
        viewModelScope.launch {
            queueRepository.queue.collect { cards -> _uiState.update { it.withQueue(cards) } }
        }
        if (!isCollectionTarget) {
            viewModelScope.launch {
                isCommittingToDeck.collect { committing -> _uiState.update { it.copy(isCommittingQueue = committing) } }
            }
            return
        }
        viewModelScope.launch {
            queueActions.isCommitting.collect { committing ->
                _uiState.update { it.copy(isCommittingQueue = committing) }
            }
        }
        viewModelScope.launch {
            queueActions.isAddingAllToWishlist.collect { adding ->
                _uiState.update { it.copy(isAddingAllToWishlist = adding) }
            }
        }
        viewModelScope.launch {
            queueActions.inFlightIds.collect { ids -> _uiState.update { it.copy(inFlightQueueIds = ids) } }
        }
    }

    // A mutation that empties the collection queue also closes its sheet (nothing is left to act
    // on); the deck queue sheet has its own empty state and stays open.
    private fun ScannerUiState.withQueue(cards: List<QueuedCard>) = copy(
        scanSession = ScanSession(cards),
        showQueueSheet = showQueueSheet && (cards.isNotEmpty() || !isCollectionTarget),
    )

    private fun ScannerUiState.withWriteGuards(): ScannerUiState =
        if (isCollectionTarget) {
            copy(
                isCommittingQueue = queueActions.isCommitting.value,
                isAddingAllToWishlist = queueActions.isAddingAllToWishlist.value,
                inFlightQueueIds = queueActions.inFlightIds.value,
            )
        } else {
            copy(isCommittingQueue = isCommittingToDeck.value, isAddingAllToWishlist = false, inFlightQueueIds = emptySet())
        }

    // The repository mutates synchronously; mirroring immediately keeps reads right after a
    // mutation consistent instead of waiting for the observer coroutine to be dispatched.
    private fun syncQueueSnapshot() {
        _uiState.update { it.withQueue(queueRepository.queue.value).withWriteGuards() }
    }

    /**
     * Continuously tracks the set of "already owned" card identity keys — the same convention
     * used across Card Versions & Languages: every owned [com.mmg.manahub.core.model.Card.oracleId]
     * plus every owned exact [com.mmg.manahub.core.model.Card.name] (some cached rows predate the
     * oracleId backfill, so a card matches on either key). Feeds the "already in collection" badge
     * in `CardQueueSheet`. This is a LIVE collector (not a one-shot fetch) so the badge appears
     * immediately after the user adds a card from the queue while the sheet is still open.
     */
    private fun observeOwnedCardIdentityKeys() {
        viewModelScope.launch {
            userCardRepository.observeCollection().collect { rows ->
                val keys = HashSet<String>(rows.size * 2)
                rows.forEach { row ->
                    row.card.oracleId.takeIf { it.isNotBlank() }?.let(keys::add)
                    keys.add(row.card.name)
                }
                _uiState.update { it.copy(ownedCardIdentityKeys = keys) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ML recognition entry point — called by CardRecognizer
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Processes a [RecognitionResult] from [CardRecognizer].
     *
     * - [RecognitionResult.NoCard]     → clears the overlay and search indicator.
     * - [RecognitionResult.Detected]   → shows searching indicator but keeps going.
     * - [RecognitionResult.Identified] → applies set lock, language mismatch,
     *   stability + anti-duplicate logic, then routes to the appropriate mode.
     */
    fun onRecognitionResult(result: RecognitionResult) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            if (com.mmg.manahub.BuildConfig.DEBUG) {
                val now = System.currentTimeMillis()
                frameTimestamps.addLast(now)
                if (frameTimestamps.size > 10) frameTimestamps.removeAt(0)
                val fps = if (frameTimestamps.size >= 2) {
                    val span = frameTimestamps.last() - frameTimestamps.first()
                    if (span > 0) ((frameTimestamps.size - 1) * 1000L / span).toInt() else 0
                } else 0
                android.util.Log.d("ScannerFPS", "fps=$fps")
            }
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
                        detectedCorners = result.corners.ifEmpty { null },
                        isSearching = true,
                        languageMismatch = false,
                    )
                }
            }

            is RecognitionResult.RateLimited -> {
                // W2.10: the shared Scryfall rate limiter exhausted its retries. OCR keeps
                // running (CardRecognizer only suspends the NETWORK half of the pipeline), so
                // the detected-card overlay/searching indicator just steps back to idle while
                // the countdown badge (rememberRateLimitCountdownSeconds, fed by
                // rateLimitedUntilMs) takes over.
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        rateLimitedUntilMs = System.currentTimeMillis() + result.retryAfterMs,
                    )
                }
            }

            is RecognitionResult.Identified -> {
                _uiState.update {
                    it.copy(detectedCorners = result.corners.ifEmpty { null })
                }

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

                // ── Adaptive stability buffer ────────────────────────────────
                val requiredFrames = HIGH_CONFIDENCE_FRAMES

                if (recentMatches.size >= requiredFrames) recentMatches.removeAt(0)
                recentMatches.addLast(id)

                if (recentMatches.size < requiredFrames || recentMatches.any { it != id }) {
                    _uiState.update { it.copy(isSearching = true) }
                    return
                }

                // ── Card confirmed — reset buffer ────────────────────────────
                recentMatches.clear()
                _uiState.update { it.copy(isSearching = false, lastDetectedCard = result.card) }

                val confirmedState = _uiState.value

                // Skip adding if already in session with same attributes. Language identity
                // tracks the RESOLVED printing's language (result.card.lang), not the mode-bar
                // filter (confirmedState.selectedLanguage) — see addToSession's KDoc (W2.11):
                // QueuedCard.language must be truthful data, so its identity key must match.
                val isInSession = confirmedState.scanSession.cards.any { entry ->
                    entry.card.scryfallId == result.card.scryfallId &&
                            entry.isFoil == confirmedState.selectedIsFoil &&
                            entry.language == result.card.lang &&
                            entry.condition == confirmedState.selectedCondition
                }

                if (isInSession) {
                    _uiState.update { it.copy(languageMismatch = false) }
                    return
                }

                // W2.11 (2026-08-24): with the localized resolution ladder (CardRecognizer), a
                // non-English selection normally resolves the LOCALIZED printing directly, so
                // result.card.lang == confirmedState.selectedLanguage in the common case and
                // languageMismatch never fires. result.languageFallback is set ONLY when no
                // printing exists in the selected language and CardRecognizer fell back to the
                // English printing — that case is now purely informational (a badge), never a
                // reason to refuse the add.
                _uiState.update { it.copy(languageMismatch = result.languageFallback) }

                if (result.ambiguous) {
                    _uiState.update {
                        it.copy(
                            showAmbiguitySelector = true,
                        )
                    }
                } else {
                    analyticsHelper.logEvent(
                        "scanner_card_added_auto",
                        mapOf("set_code" to result.card.setCode)
                    )
                    quickAddCard(result.card)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Quick add — auto-adds a confirmed card (Quick Mode)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adds [card] to the scan session (queue only — no automatic collection write).
     * Updates [lastAddedId] and [lastAddedTime] to activate the anti-duplicate guard.
     * Used both by Quick Mode (automatic) and [onManualAddCurrentCard] (manual confirmation).
     */
    private fun quickAddCard(card: Card) {
        addToSession(card)
        lastAddedId = card.scryfallId
        lastAddedTime = System.currentTimeMillis()

        _uiState.update { it.copy(toastMessage = card.name, toastType = MagicToastType.SUCCESS) }

        if (_uiState.value.isSoundEnabled) {
            soundManager.playForPrice(
                priceEur = card.priceEur,
                priceUsdFallback = card.priceUsd,
            )
        }
    }

    /**
     * Merges [card] into the current [ScanSession] and persists the updated queue.
     * Increments quantity if an entry with the same key (scryfallId + isFoil + language + condition)
     * already exists; otherwise appends a new [QueuedCard].
     *
     * [QueuedCard.language] stores [Card.lang] (the RESOLVED printing's real language), NOT
     * [ScannerUiState.selectedLanguage] (the mode-bar filter) — W2.11 (scanner-reliability-plan.md,
     * 2026-08-24). The two coincide in the normal case (the resolution ladder resolves the
     * localized printing when one exists), but on an English-fallback add
     * ([RecognitionResult.Identified.languageFallback] = true) `card.lang` is `"en"` while
     * [ScannerUiState.selectedLanguage] might still be e.g. `"es"` — the user's collection must
     * reflect the actual printing they now own, not the filter they had selected when scanning.
     */
    private fun addToSession(card: Card) {
        val state = _uiState.value
        queueRepository.addOrMerge(
            QueuedCard(
                card = card,
                quantity = state.selectedQuantity,
                isFoil = state.selectedIsFoil,
                language = card.lang,
                condition = state.selectedCondition,
                setCode = card.setCode,
                timestamp = System.currentTimeMillis(),
            )
        )
        syncQueueSnapshot()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Manual add (non-quick mode): user taps the confirm button in the bottom bar
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called when the user explicitly confirms adding the currently displayed card.
     * Reuses [quickAddCard] logic.
     */
    fun onManualAddCurrentCard() {
        val card = _uiState.value.lastDetectedCard ?: return
        analyticsHelper.logEvent("scanner_card_added_manual", mapOf(
            "set_code" to card.setCode,
        ))
        quickAddCard(card)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Individual Actions (Collection & Wishlist)
    // ─────────────────────────────────────────────────────────────────────────

    /** Adds a single queue entry to the user's collection; a repeat tap while it is in flight is a no-op. */
    fun onAddEntryToCollection(entry: QueuedCard) {
        if (!isCollectionTarget) {
            recordWrongTargetAction("add_entry_to_collection")
            return
        }
        val removeOnSuccess = _uiState.value.isAutoDeleteOnAddEnabled
        // Committed through the scan path (CardScanned XP), never double-counted as a manual add.
        val launched = queueActions.addEntryToCollection(commitScope, entry, removeOnSuccess) { succeeded ->
            analyticsHelper.logEvent(
                "scanner_entry_to_collection",
                mapOf("result" to if (succeeded) "success" else "error"),
            )
            syncQueueSnapshot()
            // A failed write must not report success or drop the entry from the queue.
            if (succeeded) {
                _uiState.update {
                    it.copy(
                        toastMessage = context.getString(R.string.scanner_toast_added_to_collection, entry.card.name),
                        toastType = MagicToastType.SUCCESS,
                    )
                }
                if (removeOnSuccess) clearRemovedEntryState(entry)
            } else {
                _uiState.update {
                    it.copy(
                        toastMessage = context.getString(R.string.scanner_toast_add_failed, entry.card.name),
                        toastType = MagicToastType.ERROR,
                    )
                }
            }
        }
        if (launched) syncQueueSnapshot()
    }

    /**
     * Adds a single queue entry (with its quantity) to the wishlist.
     * No authentication required — guest wishlist entries are stored locally via Room.
     */
    fun onAddEntryToWishlist(entry: QueuedCard) {
        if (!isCollectionTarget) {
            recordWrongTargetAction("add_entry_to_wishlist")
            return
        }
        val removeOnSuccess = _uiState.value.isAutoDeleteOnAddEnabled
        val launched = queueActions.addEntryToWishlist(commitScope, entry, removeOnSuccess) { result ->
            result.exceptionOrNull()?.let { error -> recordSafeNonFatal("scanner_entry_to_wishlist_failed", error) }
            analyticsHelper.logEvent(
                "scanner_entry_to_wishlist",
                mapOf("result" to if (result.isSuccess) "success" else "error"),
            )
            syncQueueSnapshot()
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(
                        toastMessage = context.getString(R.string.scanner_toast_added_to_wishlist, entry.card.name),
                        toastType = MagicToastType.SUCCESS,
                    )
                } else {
                    it.copy(
                        toastMessage = context.getString(R.string.scanner_toast_add_failed, entry.card.name),
                        toastType = MagicToastType.ERROR,
                    )
                }
            }
            if (result.isSuccess && removeOnSuccess) clearRemovedEntryState(entry)
        }
        if (launched) syncQueueSnapshot()
    }

    private fun clearRemovedEntryState(entry: QueuedCard) {
        if (queueRepository.queue.value.any { it.id == entry.id }) return
        _uiState.update { state ->
            state.copy(
                multiSelectedIds = state.multiSelectedIds - entry.card.scryfallId,
                lastDetectedCard = if (state.lastDetectedCard?.scryfallId == entry.card.scryfallId) null else state.lastDetectedCard,
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Bulk Actions
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adds all queue entries (with their quantities) to the wishlist, keeping them in the queue. A
     * repeat tap while the batch is in flight is a no-op.
     * No authentication required — guest wishlist entries are stored locally via Room.
     */
    fun onAddAllToWishlist() {
        if (!isCollectionTarget) {
            recordWrongTargetAction("add_all_to_wishlist")
            return
        }
        var failedCount = 0
        val launched = queueActions.addAllToWishlist(
            scope = commitScope,
            onEntryAdded = { entry, result ->
                if (result.isSuccess) {
                    _uiState.update {
                        it.copy(
                            toastMessage = context.getString(R.string.scanner_toast_added_to_wishlist, entry.card.name),
                            toastType = MagicToastType.SUCCESS,
                        )
                    }
                } else {
                    failedCount++
                    result.exceptionOrNull()?.let { error -> recordSafeNonFatal("scanner_bulk_wishlist_entry_failed", error) }
                }
                kotlinx.coroutines.delay(100)
            },
        ) { count ->
            analyticsHelper.logEvent(
                "scanner_add_all_wishlist",
                mapOf(
                    "count" to (count - failedCount).toString(),
                    "failed_count" to failedCount.toString(),
                    "result" to when {
                        failedCount == 0 -> "success"
                        failedCount == count -> "error"
                        else -> "partial"
                    },
                ),
            )
            _uiState.update {
                it.copy(
                    toastMessage = if (failedCount == 0) {
                        context.getString(R.string.scanner_toast_added_all_to_wishlist, count)
                    } else {
                        context.getString(R.string.scanner_toast_add_all_partial_failure, failedCount, count)
                    },
                    toastType = if (failedCount == 0) MagicToastType.SUCCESS else MagicToastType.WARNING,
                )
            }
        }
        if (launched) syncQueueSnapshot()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Common ViewModel Actions
    // ─────────────────────────────────────────────────────────────────────────

    /** Dismisses the ambiguity resolution dialog without adding the card. */
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

    /** Opens the price detail [ModalBottomSheet] for the currently detected card. */
    fun onOpenPriceDetail() {
        _uiState.update { it.clearedForOverlay().copy(showPriceDetailSheet = true) }
    }

    /** Closes the price detail [ModalBottomSheet]. */
    fun onClosePriceDetail() {
        _uiState.update { it.copy(showPriceDetailSheet = false) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Sound toggle
    // ─────────────────────────────────────────────────────────────────────────

    /** Toggles sound effects on or off. */
    fun onToggleSound() {
        _uiState.update { it.copy(isSoundEnabled = !it.isSoundEnabled) }
    }

    /**
     * Toggles whether a per-entry "Add to collection" / "Add to wishlist" action in
     * `CardQueueSheet` also removes that entry from the queue once the add succeeds. Sticky for
     * the session (survives sheet close/reopen); does NOT affect the bulk "Add all" actions.
     */
    fun onToggleAutoDeleteOnAdd() {
        _uiState.update { it.copy(isAutoDeleteOnAddEnabled = !it.isAutoDeleteOnAddEnabled) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Mode bar actions
    // ─────────────────────────────────────────────────────────────────────────

    /** Toggles the foil chip in the mode bar. */
    fun onToggleFoil() {
        _uiState.update { it.copy(selectedIsFoil = !it.selectedIsFoil) }
    }

    /**
     * Updates the selected scan language.
     *
     * Also clears [ScannerUiState.rateLimitedUntilMs] (W2.11, scanner-reliability-plan.md,
     * 2026-08-24) — switching language is a strong signal the user is starting a fresh scanning
     * intent, so the badge shouldn't keep counting down against the OLD language's failed
     * attempt; the shared `com.mmg.manahub.core.data.network.RateLimitedQueue` still enforces its
     * own cooldown server-side regardless, so this only affects how eagerly the UI lets a new
     * attempt be tried, never bypasses the shared limiter.
     *
     * The CardRecognizer-side half of the reset (negative cache, resolution generation,
     * pre-resolution stability buffer, local 3s memo — see `CardRecognizer`'s KDoc) fires via
     * `CardRecognizer.selectedLanguage`'s custom setter when `ScannerScreen`'s
     * `LaunchedEffect(selectedLanguage)` forwards this new value on the next recomposition.
     * `CardRecognizer` is a Composable-scoped dependency (excluded from KMP, Hilt-entry-point
     * constructed per screen entry), not a ViewModel-owned one, so that half of the reset is unit
     * tested in `CardRecognizerTest`, not here — this project has no Compose UI test
     * infrastructure to exercise the cross-layer wiring directly.
     */
    fun onLanguageSelected(language: String) {
        _uiState.update {
            it.copy(selectedLanguage = language, languageMismatch = false, rateLimitedUntilMs = null)
        }
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
    //  Editing scanned cards
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens the edit sheet for a specific scanned card.
     * Fetches all available prints (sets) for that card to populate the set picker.
     */
    fun onEditScannedCard(entry: QueuedCard) {
        _uiState.update {
            it.clearedForOverlay().copy(
                editingCard = entry,
                showEditSheet = true,
                availablePrints = emptyList(),
                isLoadingPrints = true,
            )
        }

        viewModelScope.launch {
            val result = cardRepository.getCardPrints(entry.card.name)
            if (result is DataResult.Success) {
                _uiState.update {
                    it.copy(
                        availablePrints = result.data,
                        isLoadingPrints = false,
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingPrints = false) }
            }
        }
    }

    /** Replaces the entry being edited with [updatedEntry] in the shared queue and closes the edit sheet. */
    fun onUpdateScannedCard(updatedEntry: QueuedCard) {
        val original = _uiState.value.editingCard ?: return
        queueRepository.update(updatedEntry.copy(id = original.id))
        syncQueueSnapshot()
        _uiState.update { it.copy(showEditSheet = false, editingCard = null) }
    }

    /** Closes the edit sheet without saving. */
    fun onCloseEditSheet() {
        _uiState.update { it.copy(showEditSheet = false, editingCard = null) }
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
     */
    fun onFlashAvailabilityChanged(available: Boolean) {
        _uiState.update { it.copy(hasFlash = available) }
    }

    /** Reports a failed camera `bindToLifecycle` and shows [ScannerUiState.cameraBindError]. */
    fun onCameraBindFailed(error: Throwable) {
        recordSafeNonFatal("scanner_camera_bind_failed", error)
        _uiState.update {
            it.copy(cameraBindError = context.getString(R.string.scanner_camera_bind_failed))
        }
    }

    /** Clears [ScannerUiState.cameraBindError] after the next successful `bindToLifecycle`. */
    fun onCameraBound() {
        _uiState.update { it.copy(cameraBindError = null) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Recognition pause toggle (top bar) — independent of sheet-driven pauses
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Toggles recognition pause via the top-bar play/pause control. This is independent of the
     * sheet-driven pauses (queue/settings/edit/variant-selector/expanded-image) — both conditions
     * are OR'd together at the [CameraPreview] call site.
     */
    fun onToggleRecognitionPaused() {
        _uiState.update { it.copy(isRecognitionPausedByUser = !it.isRecognitionPausedByUser) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Queue bottom sheet
    // ─────────────────────────────────────────────────────────────────────────

    /** Opens the scan-queue bottom sheet. */
    fun onOpenQueue() {
        _uiState.update { it.clearedForOverlay().copy(showQueueSheet = true, multiSelectedIds = emptySet()) }
    }

    /** Closes the scan-queue bottom sheet. */
    fun onCloseQueue() {
        _uiState.update { it.copy(showQueueSheet = false, multiSelectedIds = emptySet()) }
    }

    /** Removes a single entry from the shared queue. */
    fun onRemoveSessionCard(entry: QueuedCard) {
        queueRepository.remove(entry.id)
        syncQueueSnapshot()
        _uiState.update { state ->
            state.copy(
                multiSelectedIds = state.multiSelectedIds - entry.card.scryfallId,
                // W2026-09-06: clearing the overlay when the card is removed from queue
                lastDetectedCard = if (state.lastDetectedCard?.scryfallId == entry.card.scryfallId) null else state.lastDetectedCard
            )
        }
    }

    /** Clears the entire shared queue and resets the anti-duplicate guard. */
    fun onClearSession() {
        queueRepository.clear()
        syncQueueSnapshot()
        resetAfterQueueEmptied()
    }

    private fun resetAfterQueueEmptied() {
        _uiState.update {
            it.copy(multiSelectedIds = emptySet(), showQueueSheet = if (isCollectionTarget) false else it.showQueueSheet)
        }
        lastAddedId = null
        lastAddedTime = 0L
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Multi-select in the queue sheet
    // ─────────────────────────────────────────────────────────────────────────

    /** Toggles the long-press selection state of a session entry. */
    fun onToggleMultiSelect(entry: QueuedCard) {
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

    /** Deletes all currently selected entries from the shared queue. */
    fun onDeleteSelected() {
        queueRepository.removeByScryfallIds(_uiState.value.multiSelectedIds)
        syncQueueSnapshot()
        _uiState.update { it.copy(multiSelectedIds = emptySet()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Add all from queue to collection
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Commits the whole shared queue to the collection in ONE batched call via
     * [CardQueueActions.addAllToCollection]. A full success empties the queue and closes the sheet;
     * a partial failure keeps only the failed entries (by stable id) and surfaces a
     * [MagicToastType.WARNING] naming the shortfall. [ScannerUiState.isCommittingQueue] flips
     * synchronously so a second tap before the first commit resolves is a no-op.
     */
    fun onAddAllToCollection() {
        if (!isCollectionTarget) {
            recordWrongTargetAction("add_all_to_collection")
            return
        }
        if (_uiState.value.isCommittingQueue) return
        val launched = queueActions.addAllToCollection(commitScope) { result ->
            syncQueueSnapshot()
            when (result) {
                is AddAllToCollectionResult.Success -> {
                    analyticsHelper.logEvent(
                        "scanner_add_all",
                        mapOf("count" to result.committedEntries.toString(), "failed" to "0"),
                    )
                    _uiState.update {
                        it.copy(
                            toastMessage = context.getString(R.string.scanner_toast_added_all_to_collection, result.committedEntries),
                            toastType = MagicToastType.SUCCESS,
                        )
                    }
                    resetAfterQueueEmptied()
                }
                is AddAllToCollectionResult.PartialFailure -> {
                    analyticsHelper.logEvent(
                        "scanner_add_all",
                        mapOf("count" to result.totalEntries.toString(), "failed" to result.failedEntries.toString()),
                    )
                    _uiState.update {
                        it.copy(
                            toastMessage = context.getString(
                                R.string.scanner_toast_add_all_partial_failure,
                                result.failedEntries,
                                result.totalEntries,
                            ),
                            toastType = MagicToastType.WARNING,
                        )
                    }
                }
            }
        }
        if (launched) syncQueueSnapshot()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Deck target: add queued cards to the scanned deck
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adds one queued entry to [board] of the target deck. Runs in the app scope; the entry is
     * removed (via [CardQueueRepository.removeCommitted], so copies added meanwhile stay queued) only
     * when it committed and auto-delete is on.
     */
    fun onAddEntryToDeck(entry: QueuedCard, board: DeckBoard) {
        val deckTarget = target as? ScannerTarget.Deck ?: run {
            recordWrongTargetAction("add_entry_to_deck")
            return
        }
        if (isCommittingToDeck.value) return
        val snapshot = queueRepository.queue.value.firstOrNull { it.id == entry.id } ?: return
        val removeOnSuccess = _uiState.value.isAutoDeleteOnAddEnabled
        isCommittingToDeck.value = true
        syncQueueSnapshot()
        analyticsHelper.logEvent(
            "scanner_deck_entry_add_started",
            mapOf("board" to board.name.lowercase()),
        )
        commitScope.launch {
            try {
                val result = addScannedCardsToDeck(
                    deckId = deckTarget.deckId,
                    entries = listOf(snapshot.toDeckInput()),
                    board = board,
                )
                val committed = snapshot.id in result.committedEntryIds
                if (committed && removeOnSuccess) queueRepository.removeCommitted(listOf(snapshot))
                val isBlocked = snapshot.id in result.blockedCommanderEntryIds
                analyticsHelper.logEvent(
                    "scanner_deck_entry_add_result",
                    mapOf(
                        "board" to board.name.lowercase(),
                        "result" to when {
                            isBlocked -> "blocked_commander"
                            committed -> "success"
                            else -> "no_op"
                        },
                    ),
                )
                _uiState.update {
                    it.copy(
                        toastMessage = when {
                            isBlocked -> context.getString(R.string.scanner_deck_add_blocked_commander)
                            committed -> context.getString(
                                R.string.scanner_deck_add_success,
                                snapshot.quantity,
                                boardLabel(board),
                            )
                            else -> context.getString(R.string.scanner_deck_add_no_cards)
                        },
                        toastType = if (isBlocked) MagicToastType.WARNING else MagicToastType.SUCCESS,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordSafeNonFatal("scanner_deck_entry_add_failed", e)
                analyticsHelper.logEvent(
                    "scanner_deck_entry_add_result",
                    mapOf("board" to board.name.lowercase(), "result" to "error"),
                )
                _uiState.update {
                    it.copy(
                        toastMessage = context.getString(R.string.scanner_deck_add_failed),
                        toastType = MagicToastType.ERROR,
                    )
                }
            } finally {
                isCommittingToDeck.value = false
                syncQueueSnapshot()
            }
        }
    }

    /**
     * Adds every queued entry (a snapshot) to [board] of the target deck in one atomic merge.
     * Committed entries are removed only when auto-delete is on; commander-blocked ones stay queued.
     */
    fun onAddAllToDeck(board: DeckBoard) {
        val deckTarget = target as? ScannerTarget.Deck ?: run {
            recordWrongTargetAction("add_all_to_deck")
            return
        }
        val snapshot = queueRepository.queue.value
        if (isCommittingToDeck.value || snapshot.isEmpty()) return
        val removeOnSuccess = _uiState.value.isAutoDeleteOnAddEnabled
        isCommittingToDeck.value = true
        syncQueueSnapshot()
        analyticsHelper.logEvent(
            "scanner_deck_bulk_add_started",
            mapOf("board" to board.name.lowercase(), "entry_count" to snapshot.size.toString()),
        )
        commitScope.launch {
            try {
                val result = addScannedCardsToDeck(
                    deckId = deckTarget.deckId,
                    entries = snapshot.map { it.toDeckInput() },
                    board = board,
                )
                val committed = snapshot.filter { it.id in result.committedEntryIds }
                if (removeOnSuccess) queueRepository.removeCommitted(committed)
                val blockedCount = snapshot.count { it.id in result.blockedCommanderEntryIds }
                analyticsHelper.logEvent(
                    "scanner_deck_bulk_add_result",
                    mapOf(
                        "board" to board.name.lowercase(),
                        "entry_count" to snapshot.size.toString(),
                        "result" to when {
                            blockedCount > 0 && committed.isNotEmpty() -> "partial_commander_block"
                            blockedCount > 0 -> "blocked_commander"
                            committed.isNotEmpty() -> "success"
                            else -> "no_op"
                        },
                    ),
                )
                _uiState.update {
                    it.copy(
                        toastMessage = when {
                            blockedCount > 0 && committed.isNotEmpty() -> context.resources.getQuantityString(
                                R.plurals.scanner_deck_add_partial_success,
                                blockedCount,
                                result.committedCopies,
                                blockedCount,
                            )
                            blockedCount > 0 -> context.getString(R.string.scanner_deck_add_blocked_commander)
                            committed.isNotEmpty() -> context.getString(
                                R.string.scanner_deck_add_success,
                                result.committedCopies,
                                boardLabel(board),
                            )
                            else -> context.getString(R.string.scanner_deck_add_no_cards)
                        },
                        toastType = if (blockedCount > 0) MagicToastType.WARNING else MagicToastType.SUCCESS,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                recordSafeNonFatal("scanner_deck_bulk_add_failed", e)
                analyticsHelper.logEvent(
                    "scanner_deck_bulk_add_result",
                    mapOf(
                        "board" to board.name.lowercase(),
                        "entry_count" to snapshot.size.toString(),
                        "result" to "error",
                    ),
                )
                _uiState.update {
                    it.copy(
                        toastMessage = context.getString(R.string.scanner_deck_add_failed),
                        toastType = MagicToastType.ERROR,
                    )
                }
            } finally {
                isCommittingToDeck.value = false
                syncQueueSnapshot()
            }
        }
    }

    private fun QueuedCard.toDeckInput(): ScannedDeckCardInput = ScannedDeckCardInput(
        entryId = id,
        scryfallId = card.scryfallId,
        quantity = quantity,
        oracleId = card.oracleId,
    )

    private fun boardLabel(board: DeckBoard): String = when (board) {
        DeckBoard.MAINBOARD -> context.getString(R.string.scanner_deck_action_mainboard)
        DeckBoard.SIDEBOARD -> context.getString(R.string.scanner_deck_action_sideboard)
    }

    private fun recordWrongTargetAction(action: String) {
        analyticsHelper.logEvent(
            "scanner_action_blocked_wrong_target",
            mapOf("action" to action),
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Toast dismissal
    // ─────────────────────────────────────────────────────────────────────────

    /** Clears the one-shot toast message after it has been displayed. */
    fun onToastDismissed() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Card Detail overlay
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Opens the detail overlay for [id].
     * @param fromQueue If true, closes the queue sheet first and flags it for restoration on close.
     */
    fun onOpenCardDetail(id: String, fromQueue: Boolean = false) {
        if (!isCollectionTarget) {
            recordWrongTargetAction("open_card_detail")
            return
        }
        _uiState.update {
            it.clearedForOverlay().copy(
                selectedCardDetailId = id,
                showQueueSheet = if (fromQueue) false else it.showQueueSheet,
                returnToQueueOnDetailClose = fromQueue
            )
        }
    }

    /**
     * Closes the detail overlay. If it was opened from the queue, re-opens the queue sheet.
     */
    fun onCloseCardDetail() {
        _uiState.update {
            it.copy(
                selectedCardDetailId = null,
                showQueueSheet = if (it.returnToQueueOnDetailClose) true else it.showQueueSheet,
                returnToQueueOnDetailClose = false
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Variant selector
    // ─────────────────────────────────────────────────────────────────────────

    fun onOpenVariantSelector(entry: QueuedCard) {
        variantLoadJob?.cancel()
        _uiState.update {
            it.clearedForOverlay().copy(
                showVariantSelector = true,
                variantSelectorEntry = entry,
                cardVariants = emptyList(),
                isLoadingVariants = true,
            )
        }
        variantLoadJob = viewModelScope.launch {
            val result = cardRepository.getCardArtVariants(entry.card.name)
            _uiState.update { state ->
                if (!state.showVariantSelector) return@update state
                if (result is DataResult.Success)
                    state.copy(cardVariants = result.data, isLoadingVariants = false)
                else
                    state.copy(isLoadingVariants = false)
            }
        }
    }

    fun onCloseVariantSelector() {
        variantLoadJob?.cancel()
        _uiState.update {
            it.copy(
                showVariantSelector = false,
                variantSelectorEntry = null,
                isLoadingVariants = false,
                cardVariants = emptyList(),
            )
        }
    }

    fun onSelectVariant(variant: Card) {
        val original = _uiState.value.variantSelectorEntry ?: return
        queueRepository.queue.value.firstOrNull { it.id == original.id }?.let { current ->
            queueRepository.update(current.copy(card = variant, setCode = variant.setCode, language = variant.lang))
        }
        syncQueueSnapshot()
        _uiState.update { state ->
            state.copy(
                showVariantSelector = false,
                variantSelectorEntry = null,
                editingCard = if (state.editingCard?.id == original.id) {
                    state.editingCard.copy(card = variant, setCode = variant.setCode, language = variant.lang)
                } else state.editingCard
            )
        }
    }

    fun onExpandVariantImage(imageUrl: String) {
        if (imageUrl.isBlank()) return
        _uiState.update { it.clearedForOverlay().copy(expandedVariantImageUrl = imageUrl) }
    }

    fun onCloseExpandedImage() {
        _uiState.update { it.copy(expandedVariantImageUrl = null) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Duplicate scanned card
    // ─────────────────────────────────────────────────────────────────────────

    fun onIncrementSessionCardQuantity(entry: QueuedCard) {
        queueRepository.incrementQuantity(entry.id)
        syncQueueSnapshot()
    }

    fun onDecrementSessionCardQuantity(entry: QueuedCard) {
        if (entry.quantity <= 1) {
            onRemoveSessionCard(entry)
            return
        }
        queueRepository.decrementQuantity(entry.id)
        syncQueueSnapshot()
    }

    fun onRemoveLastDetectedCard() {
        val lastCard = _uiState.value.lastDetectedCard ?: return
        val entryToRemove = _uiState.value.scanSession.cards.lastOrNull {
            it.card.scryfallId == lastCard.scryfallId
        }
        if (entryToRemove != null) {
            onRemoveSessionCard(entryToRemove)
        } else {
            _uiState.update { it.copy(lastDetectedCard = null) }
        }
    }

    /**
     * Duplicates [original] and inserts the copy immediately after it in the scan queue (in
     * place, NOT appended at the end), so quickly stamping several physical copies of the same
     * card keeps them visually grouped. Backs the "Duplicate" action in `CardQueueSheet` (replaced
     * the old per-copy "Variants" button — item 6 of the 2026-07-17 scanner UX pass). Unlike the
     * old `onAddDuplicateScannedCard` this never touches the edit sheet's visibility, since it is
     * no longer reachable from inside `EditQueuedCardSheet`.
     */
    fun onDuplicateSessionCard(original: QueuedCard) {
        queueRepository.duplicate(original)
        syncQueueSnapshot()
    }

    // Note (WS5, `scanner-reliability-plan.md`, 2026-08-25): this ViewModel deliberately does
    // NOT override onCleared() to release [soundManager]. [SoundManager] is a Hilt @Singleton
    // alive for the whole app process, while this ViewModel is scoped to the scanner screen and
    // is cleared every time the user navigates away — releasing a process-wide singleton from a
    // screen-scoped lifecycle would permanently kill scan sounds on the next visit, the same
    // class of bug fixed for `CardOcrAnalyzer` in WS1. See [SoundManager]'s class KDoc.
}
