package com.mmg.manahub.feature.playtest.presentation.hand

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.model.BattlefieldState
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.HandSnapshot
import com.mmg.manahub.core.model.PlayCard
import com.mmg.manahub.core.model.PlayZone
import com.mmg.manahub.core.model.PlaytestPhase
import com.mmg.manahub.core.model.PlaytestSetup
import com.mmg.manahub.core.model.PlaytestSurveyAnswers
import com.mmg.manahub.core.model.computeProtectedIndices
import com.mmg.manahub.core.model.computeRequiredBottomCount
import com.mmg.manahub.feature.playtest.domain.usecase.BuildLibraryUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.LondonMulliganUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.SavePlaytestSurveyUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.SavePlaytestUseCase
import com.mmg.manahub.feature.playtest.domain.usecase.drawWithForced
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlaytestHandUiState(
    val isLoading: Boolean = true,
    val setup: PlaytestSetup? = null,
    val snapshot: HandSnapshot? = null,
    /** Current phase: MULLIGAN (hand decision) or PLAY (simulated battlefield). */
    val phase: PlaytestPhase = PlaytestPhase.MULLIGAN,
    /** Ephemeral battlefield state — non-null only while [phase] == PLAY. */
    val battlefield: BattlefieldState? = null,
    /** Whether the "End Test" confirmation dialog is visible (PLAY phase only). */
    val showEndTestConfirm: Boolean = false,
    /** Indices (into snapshot.hand) selected for bottom-N. */
    val selectedBottomIndices: Set<Int> = emptySet(),
    val showBottomNSelector: Boolean = false,
    /**
     * "Custom your hand" forced-card selection: scryfallId → forced copy count. Survives every
     * redraw/mulligan for the rest of the session (only cleared by building a fresh
     * [PlaytestHandViewModel] instance, i.e. leaving the screen).
     */
    val customHandSelection: Map<String, Int> = emptyMap(),
    val showCustomHandSheet: Boolean = false,
    /**
     * The deck's own mainboard, grouped by card with its in-deck quantity (commander excluded) —
     * the source list for the "Custom your hand" sheet. No network calls; already-loaded data.
     */
    val availableCards: List<Pair<Card, Int>> = emptyList(),
    // DORMANT: showSaveSheet / showSurveySheet / savedSessionId / isSaving back the
    // save + survey flow, which is currently unreachable. Kept intact for when
    // playtest stats tracking returns.
    val showSaveSheet: Boolean = false,
    val showSurveySheet: Boolean = false,
    val savedSessionId: Long? = null,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

sealed class PlaytestHandEvent {
    object SaveSuccess : PlaytestHandEvent()
    object NavigateBack : PlaytestHandEvent()
    data class ShowError(val message: String) : PlaytestHandEvent()

    /**
     * Informational toast carrying a string-resource name (resolved on the screen so
     * the ViewModel stays free of Android resource references).
     */
    data class ShowInfo(val stringResName: String) : PlaytestHandEvent()
}

class PlaytestHandViewModel(
    private val deckRepository: DeckRepository,
    private val cardDao: CardDao,
    private val buildLibraryUseCase: BuildLibraryUseCase,
    private val londonMulliganUseCase: LondonMulliganUseCase,
    private val savePlaytestUseCase: SavePlaytestUseCase,
    private val savePlaytestSurveyUseCase: SavePlaytestSurveyUseCase,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaytestHandUiState())
    val uiState: StateFlow<PlaytestHandUiState> = _uiState.asStateFlow()

    /**
     * One-shot events delivered via a buffered [Channel] (NOT a StateFlow).
     *
     * A StateFlow would equality-collapse repeated events (two consecutive
     * [PlaytestHandEvent.NavigateBack] = no-op, so "End Test" could fail to navigate on
     * a second tap) and could be missed entirely if the lifecycle stops before the value
     * is collected. A buffered Channel guarantees every emission is delivered exactly once
     * and survives a paused collector. Collect it on the screen with a plain
     * `LaunchedEffect(Unit) { viewModel.events.collect { ... } }`.
     */
    private val _events = Channel<PlaytestHandEvent>(Channel.BUFFERED)
    val events: Flow<PlaytestHandEvent> = _events.receiveAsFlow()

    /** Snapshot id counter — incremented on every draw/reshuffle. */
    private var snapshotIdCounter = 0

    /**
     * Monotonic counter minting a unique [PlayCard.instanceId] for every physical
     * card instance on the battlefield. NEVER reset within a session so ids stay
     * globally unique — this is what battlefield LazyRow `key`s use to avoid the
     * duplicate-key crash with repeated copies of the same card.
     */
    private var instanceIdCounter = 0L

    /**
     * Library size before the first draw (used for save mapping). Set exactly once per
     * session. `null` is the "not yet registered" sentinel — a legitimately empty library
     * (0 cards) is a valid recorded value and must NOT be re-registered, so 0 cannot double
     * as "uninitialized".
     */
    private var originalLibrarySize: Int? = null

    /**
     * Epoch-millis when the session was first initialized (first draw).
     * Set exactly once; never updated on subsequent redraws or mulligans so that
     * the saved session always reflects when the user actually started the test.
     */
    private var sessionStartedAt: Long = 0L

    /**
     * The deck's own mainboard, grouped by card with its in-deck quantity (commander excluded) —
     * source list for the "Custom your hand" sheet. Recomputed on every [buildAndDraw] call
     * (cheap: derived from data already fetched in that same IO block, no extra network/DB
     * round-trip) and mirrored into [PlaytestHandUiState.availableCards].
     */
    private var mainboardCardCounts: List<Pair<Card, Int>> = emptyList()

    // ── Initialise from setup ─────────────────────────────────────────────────

    /**
     * Called from the screen after navigation with the resolved [PlaytestSetup].
     * Only initialises once (if snapshot is already set, this is a no-op).
     */
    fun initWithSetup(setup: PlaytestSetup) {
        if (_uiState.value.snapshot != null) return
        sessionStartedAt = System.currentTimeMillis()
        FirebaseCrashlytics.getInstance().apply {
            log("playtest_session_started: deckId=${setup.deckId} format=${setup.deckFormat} drawCount=${setup.drawCount} onThePlay=${setup.isOnThePlay}")
            setCustomKey("playtest_deck_id", setup.deckId)
            setCustomKey("playtest_format", setup.deckFormat)
            setCustomKey("playtest_draw_count", setup.drawCount)
            setCustomKey("playtest_on_the_play", setup.isOnThePlay)
        }
        _uiState.update { it.copy(setup = setup, isLoading = true) }
        buildAndDraw(setup, previousBottomed = emptyList())
    }

    // ── Ephemeral actions (no DB writes) ──────────────────────────────────────

    /**
     * Ephemeral "Nueva mano": reshuffles the entire deck + commander exclusion,
     * redraws the configured count, and resets all counters.
     * Does NOT increment the mulligan counter.
     */
    fun onRedraw() {
        val state = _uiState.value
        val setup = state.setup ?: return
        FirebaseCrashlytics.getInstance().log("playtest_redraw_triggered: deckId=${setup.deckId}")
        // Reset mulligan counter and bottomed list for a fresh start.
        // sessionStartedAt is NOT reset here — it captures when the session began.
        buildAndDraw(setup, previousBottomed = emptyList())
    }

    /**
     * London Mulligan: reshuffles current hand + library and redraws the same count.
     * Increments the mulligan counter by 1.
     */
    fun onMulligan() {
        val state = _uiState.value
        val snapshot = state.snapshot ?: return
        val setup = state.setup ?: return
        // Prevent reaching a 0-card kept hand: the minimum keepable hand is 1 card. This is
        // now guaranteed by computeRequiredBottomCount()'s own coerceIn(0, drawCount - 1) clamp
        // (it can never return >= drawCount), so this check can no longer actually trip in
        // practice — it is kept as defense-in-depth documenting the invariant, the same style as
        // moveCard's toZone==LIBRARY guard, in case the formula ever changes.
        val nextRequired = computeRequiredBottomCount(setup.drawCount, setup.startCount, snapshot.mulligansUsed + 1)
        if (nextRequired >= setup.drawCount) return
        if (snapshot.hand.size <= 1) return // Safety guard for small hands.

        val (newHand, newLibrary) = londonMulliganUseCase(
            currentHand    = snapshot.hand,
            currentLibrary = snapshot.library,
            drawCount      = setup.drawCount,
            forced         = state.customHandSelection,
        )

        val newMulligansUsed = snapshot.mulligansUsed + 1
        FirebaseCrashlytics.getInstance().apply {
            log("playtest_mulligan_taken: mulligansNow=$newMulligansUsed handSize=${newHand.size}")
            setCustomKey("playtest_mulligans_used", newMulligansUsed)
        }
        val newSnapshot = HandSnapshot(
            id                  = ++snapshotIdCounter,
            hand                = newHand,
            library             = newLibrary,
            mulligansUsed       = newMulligansUsed,
            bottomedScryfallIds = snapshot.bottomedScryfallIds,
            startedAt           = snapshot.startedAt,
        )
        _uiState.update { it.copy(snapshot = newSnapshot, selectedBottomIndices = emptySet()) }
    }

    /**
     * "Keep": triggered when the user decides to keep the current hand.
     * If the required bottom-N count (drawCount vs. startCount gap, or mulligansUsed — see
     * [computeRequiredBottomCount]) is > 0, shows the bottom-N selector first (which ends by
     * calling [enterPlayPhase]). Otherwise enters the PLAY phase directly.
     *
     * Note: this no longer opens the (now dormant) save sheet — keeping a hand starts
     * the simulated game.
     */
    fun onKeep() {
        val state = _uiState.value
        val snapshot = state.snapshot ?: return
        val setup = state.setup ?: return
        val required = computeRequiredBottomCount(setup.drawCount, setup.startCount, snapshot.mulligansUsed)
        if (required > 0) {
            _uiState.update { it.copy(showBottomNSelector = true, selectedBottomIndices = emptySet()) }
        } else {
            enterPlayPhase()
        }
    }

    // ── Bottom-N selector ─────────────────────────────────────────────────────

    /**
     * The number of cards the player must actually select in the bottom-N step, right now.
     *
     * Normally this is just [computeRequiredBottomCount]. It is additionally clamped to the
     * number of UNPROTECTED hand cards available (`hand.size - protected count`) — a rare edge
     * case where many "Custom your hand" forced cards plus many mulligans leave fewer selectable
     * cards than the formula asks for. The forced-card guarantee wins in that case: the kept hand
     * may end up slightly larger than `startCount`, which is an acceptable, documented trade-off
     * rather than a dead-end where Confirm can never be enabled.
     */
    private fun effectiveRequiredBottomCount(
        snapshot: HandSnapshot,
        setup: PlaytestSetup,
        forced: Map<String, Int>,
    ): Int {
        val required = computeRequiredBottomCount(setup.drawCount, setup.startCount, snapshot.mulligansUsed)
        val protectedCount = computeProtectedIndices(snapshot.hand, forced).size
        val selectable = (snapshot.hand.size - protectedCount).coerceAtLeast(0)
        return required.coerceAtMost(selectable)
    }

    fun toggleBottomSelection(index: Int) {
        val state = _uiState.value
        val snapshot = state.snapshot ?: return
        val setup = state.setup ?: return
        // A "Custom your hand" forced card can never be bottomed — it must stay in the kept hand.
        if (index in computeProtectedIndices(snapshot.hand, state.customHandSelection)) return
        val required = effectiveRequiredBottomCount(snapshot, setup, state.customHandSelection)
        val current = state.selectedBottomIndices.toMutableSet()
        if (index in current) {
            current.remove(index)
        } else if (current.size < required) {
            current.add(index)
        }
        _uiState.update { it.copy(selectedBottomIndices = current) }
    }

    fun onConfirmBottomN() {
        val state = _uiState.value
        val snapshot = state.snapshot ?: return
        val setup = state.setup ?: return
        val required = effectiveRequiredBottomCount(snapshot, setup, state.customHandSelection)
        val indices = state.selectedBottomIndices.toList()
        if (indices.size != required) return

        val (finalHand, finalLibrary) = londonMulliganUseCase.applyBottomN(
            hand          = snapshot.hand,
            library       = snapshot.library,
            cardsToBottom = indices,
        )

        val bottomedScryfallIds = snapshot.bottomedScryfallIds +
            indices.map { snapshot.hand[it].scryfallId }

        FirebaseCrashlytics.getInstance().log("playtest_bottom_n_confirmed: n=${indices.size} finalHandSize=${finalHand.size}")
        val finalSnapshot = snapshot.copy(
            id                  = ++snapshotIdCounter,
            hand                = finalHand,
            library             = finalLibrary,
            bottomedScryfallIds = bottomedScryfallIds,
        )
        _uiState.update {
            it.copy(
                snapshot              = finalSnapshot,
                showBottomNSelector   = false,
                selectedBottomIndices = emptySet(),
            )
        }
        // Bottoming complete → start the simulated game with the final kept hand.
        enterPlayPhase()
    }

    fun onDismissBottomN() {
        _uiState.update { it.copy(showBottomNSelector = false, selectedBottomIndices = emptySet()) }
    }

    // ── Custom your hand (MULLIGAN phase only) ────────────────────────────────
    // Lets the user force specific deck cards into the opening hand, bounded by deck copy count
    // AND by drawCount total. The selection survives every redraw/mulligan for the rest of the
    // session (buildAndDraw/onMulligan both thread customHandSelection through drawWithForced).
    // Applying a selection is INSTANT — it rebuilds the currently-displayed hand right away,
    // preserving mulligansUsed/bottomedScryfallIds (this is a re-deal, not a mulligan).

    fun onOpenCustomHandSheet() {
        _uiState.update { it.copy(showCustomHandSheet = true) }
    }

    /**
     * Dismissing the sheet (tap outside, system back, drag-to-close) has no separate discard
     * path — every +/- tap is already committed to [PlaytestHandUiState.customHandSelection]
     * immediately (same pattern as the Setup screen's steppers), so dismissing just closes the
     * sheet and triggers the same instant re-apply as an explicit confirm.
     */
    fun onDismissCustomHandSheet() {
        onConfirmCustomHandSheet()
    }

    fun onConfirmCustomHandSheet() {
        _uiState.update { it.copy(showCustomHandSheet = false) }
        // Custom Hand only affects the DRAWN hand during the mulligan loop — it has no meaning
        // once the battlefield (PLAY phase) exists, so this is a no-op there.
        if (_uiState.value.phase == PlaytestPhase.MULLIGAN) {
            applyCustomHandSelection()
        }
    }

    /**
     * Sets the forced copy count for [scryfallId], clamped twice: first to how many copies of
     * this card actually exist in the deck ([quantityInDeck] as reported by
     * [PlaytestHandUiState.availableCards]), then to whatever headroom remains under the overall
     * `drawCount` cap once every OTHER forced card's count is accounted for (mirrors the
     * DeckStudio add-card qty-stepper capping pattern, against `drawCount` instead of a deck
     * legality limit).
     */
    fun onSetCustomHandCount(scryfallId: String, count: Int) {
        val state = _uiState.value
        val setup = state.setup ?: return
        val quantityInDeck = mainboardCardCounts.firstOrNull { (card, _) -> card.scryfallId == scryfallId }?.second ?: 0
        val clampedToDeck = count.coerceIn(0, quantityInDeck)
        val othersSum = state.customHandSelection.filterKeys { it != scryfallId }.values.sum()
        val remainingCap = (setup.drawCount - othersSum).coerceAtLeast(0)
        val finalCount = clampedToDeck.coerceAtMost(remainingCap)
        val newSelection = if (finalCount <= 0) {
            state.customHandSelection - scryfallId
        } else {
            state.customHandSelection + (scryfallId to finalCount)
        }
        _uiState.update { it.copy(customHandSelection = newSelection) }
    }

    /**
     * Instantly rebuilds the current hand honoring [PlaytestHandUiState.customHandSelection]:
     * combines the current hand + library, reshuffles, and redraws via [drawWithForced]. Preserves
     * `mulligansUsed`/`bottomedScryfallIds`/`startedAt` from the current snapshot — this is a
     * re-deal, not a mulligan or a redraw, and must never touch those fields (confirmed with
     * product: applying Custom Hand does not count as a mulligan).
     */
    private fun applyCustomHandSelection() {
        val state = _uiState.value
        val snapshot = state.snapshot ?: return
        val setup = state.setup ?: return

        val combined = (snapshot.hand + snapshot.library).toMutableList()
        combined.shuffle()
        val (newHand, newLibrary) = drawWithForced(combined, setup.drawCount, state.customHandSelection)

        FirebaseCrashlytics.getInstance().log(
            "playtest_custom_hand_applied: forcedCards=${state.customHandSelection.values.sum()} handSize=${newHand.size}"
        )
        val newSnapshot = snapshot.copy(
            id      = ++snapshotIdCounter,
            hand    = newHand,
            library = newLibrary,
        )
        _uiState.update { it.copy(snapshot = newSnapshot) }
    }

    // ── Drag-and-drop reorder ─────────────────────────────────────────────────

    fun onReorderHand(fromIndex: Int, toIndex: Int) {
        val ui = _uiState.value
        if (ui.phase == PlaytestPhase.PLAY) {
            val bf = ui.battlefield ?: return
            if (fromIndex == toIndex) return
            // Guard against out-of-range drag indices delivered by gesture callbacks.
            if (fromIndex !in bf.hand.indices || toIndex !in 0..bf.hand.lastIndex) return
            val newHand = bf.hand.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
            _uiState.update { it.copy(battlefield = bf.copy(hand = newHand)) }
            return
        }

        val snapshot = ui.snapshot ?: return
        if (fromIndex == toIndex) return
        // Guard against out-of-range drag indices delivered by gesture callbacks.
        if (fromIndex !in snapshot.hand.indices || toIndex !in 0..snapshot.hand.lastIndex) return
        val newHand = snapshot.hand.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        _uiState.update { it.copy(snapshot = snapshot.copy(hand = newHand)) }
    }

    // ── Save sheet actions ────────────────────────────────────────────────────
    // DORMANT: the entire save + survey flow below is currently unreachable.
    // "Keep" now enters the PLAY phase instead of opening the save sheet, and
    // "End Test" navigates back without persisting. Kept intact (not deleted) so it
    // can be re-wired when playtest stats tracking returns.

    fun onDismissSaveSheet() {
        _uiState.update { it.copy(showSaveSheet = false) }
    }

    /** Save without survey. */
    fun onSaveWithoutSurvey() {
        save(openSurveyAfter = false)
    }

    /** Save and then open the survey sheet. */
    fun onSaveAndOpenSurvey() {
        save(openSurveyAfter = true)
    }

    /** Discard — no persistence. */
    fun onDiscard() {
        _uiState.update { it.copy(showSaveSheet = false) }
        _events.trySend(PlaytestHandEvent.NavigateBack)
    }

    private fun save(openSurveyAfter: Boolean) {
        val state = _uiState.value
        val setup = state.setup ?: return
        val snapshot = state.snapshot ?: return
        if (state.isSaving) return

        _uiState.update { it.copy(isSaving = true) }

        // `originalLibrarySize` is registered before the first draw; by the time a save is
        // reachable it is always non-null. Fall back to 0 defensively if a save somehow runs
        // before registration.
        val librarySizeForSave = originalLibrarySize ?: 0

        viewModelScope.launch {
            FirebaseCrashlytics.getInstance().apply {
                log("playtest_save_attempted: deckId=${setup.deckId} mulligansUsed=${snapshot.mulligansUsed} librarySize=$librarySizeForSave openSurveyAfter=$openSurveyAfter")
                setCustomKey("playtest_mulligans_used", snapshot.mulligansUsed)
                setCustomKey("playtest_library_size", librarySizeForSave)
            }
            val sessionId = runCatching {
                savePlaytestUseCase(
                    setup       = setup,
                    snapshot    = snapshot,
                    librarySize = librarySizeForSave,
                )
            }.onFailure { e ->
                Log.e("PlaytestHand", "Save failed", e)
                FirebaseCrashlytics.getInstance().apply {
                    log("playtest_save_failed: deckId=${setup.deckId}")
                    setCustomKey("playtest_deck_id", setup.deckId)
                    setCustomKey("playtest_format", setup.deckFormat)
                    setCustomKey("playtest_draw_count", setup.drawCount)
                    setCustomKey("playtest_mulligans_used", snapshot.mulligansUsed)
                    recordException(RuntimeException("[PlaytestHand] Save failed", e))
                }
            }
             .getOrNull()

            if (sessionId == null) {
                _uiState.update { it.copy(isSaving = false) }
                _events.trySend(PlaytestHandEvent.ShowError("Failed to save test"))
                return@launch
            }

            FirebaseCrashlytics.getInstance().log("playtest_save_succeeded: sessionId=$sessionId openSurveyAfter=$openSurveyAfter")
            _uiState.update {
                it.copy(
                    isSaving       = false,
                    savedSessionId = sessionId,
                    showSaveSheet  = false,
                    showSurveySheet = openSurveyAfter,
                )
            }
            if (!openSurveyAfter) {
                _events.trySend(PlaytestHandEvent.SaveSuccess)
            }
        }
    }

    // ── Survey sheet ──────────────────────────────────────────────────────────

    fun onDismissSurveySheet() {
        _uiState.update { it.copy(showSurveySheet = false) }
        _events.trySend(PlaytestHandEvent.SaveSuccess)
    }

    fun onSurveyFinished(
        answers: PlaytestSurveyAnswers,
        questionTypes: Map<String, String>,
        cardReferences: Map<String, String?>,
    ) {
        val state = _uiState.value
        val sessionId = state.savedSessionId ?: return
        val deckId = state.setup?.deckId ?: return

        viewModelScope.launch {
            FirebaseCrashlytics.getInstance().log("playtest_survey_submitted: sessionId=$sessionId answerCount=${answers.size}")
            runCatching {
                savePlaytestSurveyUseCase(
                    playtestSessionId = sessionId,
                    deckId            = deckId,
                    answers           = answers,
                    questionTypes     = questionTypes,
                    cardReferences    = cardReferences,
                )
            }.onFailure { e ->
                FirebaseCrashlytics.getInstance().apply {
                    log("playtest_survey_save_failed: sessionId=$sessionId")
                    setCustomKey("playtest_deck_id", deckId)
                    recordException(RuntimeException("[PlaytestHand] Survey save failed", e))
                }
            }
            _uiState.update { it.copy(showSurveySheet = false) }
            _events.trySend(PlaytestHandEvent.SaveSuccess)
        }
    }

    // ── PLAY phase (simulated battlefield — fully ephemeral, no DB writes) ─────

    /**
     * Transitions from MULLIGAN to PLAY. Builds the initial [BattlefieldState] from
     * the kept hand snapshot: the hand maps to [PlayCard]s with fresh instanceIds,
     * the library is carried over verbatim, and all field zones start empty.
     *
     * Idempotent: if already in the PLAY phase OR a battlefield already exists this is a
     * no-op. The `battlefield != null` guard is deliberately redundant with the phase check
     * — it prevents re-minting fresh instanceIds (and rebuilding the battlefield from the
     * snapshot, discarding any in-progress field state) if a future refactor ever desyncs
     * `phase` from `battlefield`.
     */
    fun enterPlayPhase() {
        val state = _uiState.value
        if (state.phase == PlaytestPhase.PLAY || state.battlefield != null) return
        val snapshot = state.snapshot ?: return

        val handCards = snapshot.hand.map { card ->
            PlayCard(instanceId = ++instanceIdCounter, card = card)
        }
        FirebaseCrashlytics.getInstance().log(
            "playtest_play_phase_entered: handSize=${handCards.size} libraryRemaining=${snapshot.library.size}"
        )
        _uiState.update {
            it.copy(
                phase       = PlaytestPhase.PLAY,
                battlefield = BattlefieldState(
                    hand       = handCards,
                    lands      = emptyList(),
                    permanents = emptyList(),
                    graveyard  = emptyList(),
                    library    = snapshot.library,
                ),
            )
        }
    }

    /**
     * Draws the top library card into the hand. Each draw mints a new
     * [PlayCard.instanceId]. No-op (and emits an informative toast) when the library
     * is empty. Guards against a null battlefield.
     */
    fun drawCard() {
        // Read AND write within the same atomic _uiState.update snapshot. Capturing the
        // battlefield outside the lambda is a stale-capture bug: two rapid taps would each
        // mint a card but both drop(1) from the SAME base library, producing deck+1 cards
        // (a phantom duplicate that breaks card conservation).
        var libraryWasEmpty = false
        _uiState.update { state ->
            val bf = state.battlefield ?: return@update state
            if (bf.library.isEmpty()) {
                libraryWasEmpty = true
                return@update state
            }
            val newCard = PlayCard(instanceId = ++instanceIdCounter, card = bf.library.first())
            val newHand = bf.hand.toMutableList().apply {
                add(size / 2, newCard)
            }
            state.copy(
                battlefield = bf.copy(
                    hand    = newHand,
                    library = bf.library.drop(1),
                ),
            )
        }
        // Emit the toast outside the update lambda (update may run more than once).
        if (libraryWasEmpty) {
            _events.trySend(PlaytestHandEvent.ShowInfo("playtest_battle_library_empty"))
        }
    }

    /**
     * Moves the card identified by [instanceId] to [toZone], removing it from its
     * current zone. No game rules are enforced (any card may go to any zone).
     * Idempotent if the card already sits in [toZone]; a no-op if [instanceId] is
     * not found in any zone.
     */
    fun moveCard(instanceId: Long, toZone: PlayZone) {
        Log.d("PlaytestViewModel", "moveCard: instanceId=$instanceId toZone=$toZone")
        // LIBRARY is a draw-only source (see PlayZone.LIBRARY KDoc) — it must never be a move
        // DESTINATION. The `when (toZone)` block below has no branch that re-adds a card to
        // library, so acting on toZone=LIBRARY here would remove the card from its origin zone
        // and never put it anywhere: a silent delete that breaks the
        // hand+lands+permanents+graveyard+library conservation invariant. The primary guard
        // against this is the drop-target hit-test in BattlefieldContent (Library is excluded
        // from the registered drop targets there), but this early return is defense-in-depth —
        // rejecting the move (no-op) rather than ever executing it — in case a future caller or
        // gesture-handling bug invokes moveCard with toZone=LIBRARY directly.
        if (toZone == PlayZone.LIBRARY) return
        // Read AND write the same atomic snapshot to avoid stale-capture races (two rapid
        // moves operating on the same base state would lose one of the mutations).
        _uiState.update { state ->
            val battlefield = state.battlefield ?: return@update state

            // Locate the card and its current zone.
            val (card, fromZone) = findCard(battlefield, instanceId) ?: return@update state
            if (fromZone == toZone) return@update state

            // Remove from origin zone.
            val withoutCard = when (fromZone) {
                PlayZone.HAND       -> battlefield.copy(hand = battlefield.hand.filterNot { it.instanceId == instanceId })
                PlayZone.LANDS      -> battlefield.copy(lands = battlefield.lands.filterNot { it.instanceId == instanceId })
                PlayZone.PERMANENTS -> battlefield.copy(permanents = battlefield.permanents.filterNot { it.instanceId == instanceId })
                PlayZone.GRAVEYARD  -> battlefield.copy(graveyard = battlefield.graveyard.filterNot { it.instanceId == instanceId })
                PlayZone.EXILE      -> battlefield.copy(exile = battlefield.exile.filterNot { it.instanceId == instanceId })
                PlayZone.LIBRARY    -> battlefield
            }

            // Returning a card to hand untaps it. Moving onto the field (LANDS/PERMANENTS)
            // resets the free-form xOffset/yOffset to 0f so FreeFormFieldZone's auto-cascade
            // placement (which only cascades when xOffset==yOffset==0f) re-triggers. moveCard is
            // only ever called on an inter-zone transition (the fromZone==toZone case already
            // returned above) — same-zone drag repositioning goes through updateCardOffset
            // instead — so a card entering LANDS/PERMANENTS here is always NEWLY arriving on the
            // field, never being repositioned within it. Without this reset a card that detours
            // through GRAVEYARD/EXILE/HAND and back onto the field would render at its stale
            // pre-detour coordinates, potentially overlapping another card.
            val moved = when {
                toZone == PlayZone.HAND -> card.copy(isTapped = false)
                toZone == PlayZone.LANDS || toZone == PlayZone.PERMANENTS -> card.copy(xOffset = 0f, yOffset = 0f)
                else -> card
            }

            // Add to destination zone.
            val updated = when (toZone) {
                PlayZone.HAND       -> withoutCard.copy(hand = withoutCard.hand + moved)
                PlayZone.LANDS      -> withoutCard.copy(lands = withoutCard.lands + moved)
                PlayZone.PERMANENTS -> withoutCard.copy(permanents = withoutCard.permanents + moved)
                PlayZone.GRAVEYARD  -> withoutCard.copy(graveyard = withoutCard.graveyard + moved)
                PlayZone.EXILE      -> withoutCard.copy(exile = withoutCard.exile + moved)
                PlayZone.LIBRARY    -> withoutCard
            }
            state.copy(battlefield = updated)
        }
    }

    /**
     * Toggles the tapped (rotated 90°) state of a battlefield card. Only cards on the
     * field (lands / permanents) can be tapped; hand and graveyard cards are ignored.
     */
    fun toggleTap(instanceId: Long) {
        // Read AND write the same atomic snapshot (stale-capture safety, as in moveCard).
        _uiState.update { state ->
            val battlefield = state.battlefield ?: return@update state
            val (_, zone) = findCard(battlefield, instanceId) ?: return@update state
            if (zone != PlayZone.LANDS && zone != PlayZone.PERMANENTS) return@update state

            val updated = when (zone) {
                PlayZone.LANDS -> battlefield.copy(
                    lands = battlefield.lands.map {
                        if (it.instanceId == instanceId) it.copy(isTapped = !it.isTapped) else it
                    },
                )
                else -> battlefield.copy(
                    permanents = battlefield.permanents.map {
                        if (it.instanceId == instanceId) it.copy(isTapped = !it.isTapped) else it
                    },
                )
            }
            state.copy(battlefield = updated)
        }
    }

    /**
     * Updates the free-form drop coordinates of a card.
     */
    fun updateCardOffset(instanceId: Long, x: Float, y: Float) {
        _uiState.update { state ->
            val battlefield = state.battlefield ?: return@update state
            val (_, zone) = findCard(battlefield, instanceId) ?: return@update state
            if (zone != PlayZone.LANDS && zone != PlayZone.PERMANENTS) return@update state

            val updated = when (zone) {
                PlayZone.LANDS -> battlefield.copy(
                    lands = battlefield.lands.map {
                        if (it.instanceId == instanceId) it.copy(xOffset = x, yOffset = y) else it
                    },
                )
                else -> battlefield.copy(
                    permanents = battlefield.permanents.map {
                        if (it.instanceId == instanceId) it.copy(xOffset = x, yOffset = y) else it
                    },
                )
            }
            state.copy(battlefield = updated)
        }
    }

    /** Locates a [PlayCard] across all zones, returning it with its current zone. */
    private fun findCard(
        battlefield: BattlefieldState,
        instanceId: Long,
    ): Pair<PlayCard, PlayZone>? {
        battlefield.hand.find { it.instanceId == instanceId }?.let { return it to PlayZone.HAND }
        battlefield.lands.find { it.instanceId == instanceId }?.let { return it to PlayZone.LANDS }
        battlefield.permanents.find { it.instanceId == instanceId }?.let { return it to PlayZone.PERMANENTS }
        battlefield.graveyard.find { it.instanceId == instanceId }?.let { return it to PlayZone.GRAVEYARD }
        battlefield.exile.find { it.instanceId == instanceId }?.let { return it to PlayZone.EXILE }
        return null
    }

    // ── End Test confirmation (PLAY phase) ────────────────────────────────────

    /** Requests ending the test — shows the confirmation dialog. */
    fun requestEndTest() {
        _uiState.update { it.copy(showEndTestConfirm = true) }
    }

    /** Dismisses the End Test confirmation without leaving. */
    fun dismissEndTest() {
        _uiState.update { it.copy(showEndTestConfirm = false) }
    }

    /**
     * Confirms ending the test: navigates back WITHOUT persisting anything (the PLAY
     * phase is purely ephemeral — there is nothing to save).
     */
    fun confirmEndTest() {
        FirebaseCrashlytics.getInstance().log("playtest_test_ended_without_save")
        _uiState.update { it.copy(showEndTestConfirm = false) }
        _events.trySend(PlaytestHandEvent.NavigateBack)
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Builds the library from the deck's mainboard, shuffles, and draws.
     *
     * Runs IO work off the main thread. [sessionStartedAt] is fixed at session init and
     * must NOT be changed here — every snapshot within a session shares the same start time.
     *
     * @param setup The playtest configuration.
     * @param previousBottomed Accumulated bottomed scryfallIds from prior mulligan steps.
     *   Pass [emptyList] for a fresh draw (new hand / redraw).
     */
    private fun buildAndDraw(
        setup: PlaytestSetup,
        previousBottomed: List<String>,
    ) {
        viewModelScope.launch {
            val result = withContext(ioDispatcher) {
                // Collect the first emission from the deck flow to get the current state.
                val deckWithCards = deckRepository.observeDeckWithCards(setup.deckId)
                    .first() ?: return@withContext null

                // Hydrate all cards in one batch query.
                val scryfallIds = deckWithCards.mainboard.map { it.scryfallId }
                val cardEntities = cardDao.getByIds(scryfallIds)
                // Non-fatal: fewer cards returned than requested signals cache eviction or stale data.
                if (cardEntities.size < scryfallIds.distinct().size) {
                    FirebaseCrashlytics.getInstance().apply {
                        log("playtest_card_cache_underfetch: requested=${scryfallIds.distinct().size} returned=${cardEntities.size}")
                        setCustomKey("playtest_deck_id", setup.deckId)
                        setCustomKey("playtest_format", setup.deckFormat)
                        recordException(
                            IllegalStateException(
                                "[PlaytestHand] CardDao.getByIds returned ${cardEntities.size} of ${scryfallIds.distinct().size} requested cards"
                            )
                        )
                    }
                }
                val cardLookup = cardEntities.associate { it.scryfallId to it.toDomainCard() }

                // Build and shuffle library (excluding commander).
                val commanderId = setup.commanderCard?.scryfallId
                val library = buildLibraryUseCase(
                    mainboardSlots      = deckWithCards.mainboard,
                    cardLookup          = cardLookup,
                    commanderScryfallId = commanderId,
                )

                // Non-fatal: library size must match expected size (mainboard count minus commander).
                val expectedSize = deckWithCards.mainboard
                    .filter { it.scryfallId != commanderId }
                    .sumOf { it.quantity }
                if (library.size != expectedSize) {
                    FirebaseCrashlytics.getInstance().apply {
                        log("playtest_library_size_mismatch: expected=$expectedSize actual=${library.size}")
                        setCustomKey("playtest_deck_id", setup.deckId)
                        setCustomKey("playtest_format", setup.deckFormat)
                        setCustomKey("playtest_library_size", library.size)
                        recordException(
                            IllegalStateException(
                                "[PlaytestHand] Library size mismatch: expected=$expectedSize actual=${library.size}"
                            )
                        )
                    }
                }

                // Grouped mainboard (card + in-deck quantity, commander excluded) for the
                // "Custom your hand" sheet — derived from data already fetched above, no extra
                // network/DB round-trip.
                val mainboardCounts = deckWithCards.mainboard
                    .filter { it.scryfallId != commanderId }
                    .mapNotNull { slot -> cardLookup[slot.scryfallId]?.let { it to slot.quantity } }

                library to mainboardCounts
            } ?: run {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Failed to load deck cards") }
                return@launch
            }
            val (library, mainboardCounts) = result
            mainboardCardCounts = mainboardCounts

            // Record library size exactly once per session (before the first draw).
            // `null` (not 0) is the "not yet registered" sentinel so a legitimately empty
            // library is recorded as 0 rather than triggering a re-registration each draw.
            if (originalLibrarySize == null) {
                originalLibrarySize = library.size
                FirebaseCrashlytics.getInstance().setCustomKey("playtest_library_size", library.size)
            }

            // Forced-aware draw: guarantees any "Custom your hand" selection survives redraws too
            // (onRedraw calls buildAndDraw without clearing customHandSelection).
            val (hand, remainingLibrary) = drawWithForced(library, setup.drawCount, _uiState.value.customHandSelection)

            FirebaseCrashlytics.getInstance().log("playtest_hand_drawn: handSize=${hand.size} mulligansUsed=0 libraryRemaining=${remainingLibrary.size}")
            val snapshot = HandSnapshot(
                id                  = ++snapshotIdCounter,
                hand                = hand,
                library             = remainingLibrary,
                mulligansUsed       = 0,
                bottomedScryfallIds = previousBottomed,
                // sessionStartedAt is fixed once in initWithSetup; never changes within a session.
                startedAt           = sessionStartedAt,
            )

            _uiState.update {
                it.copy(
                    isLoading             = false,
                    snapshot              = snapshot,
                    selectedBottomIndices = emptySet(),
                    showBottomNSelector   = false,
                    showSaveSheet         = false,
                    availableCards        = mainboardCounts,
                )
            }
        }
    }
}

