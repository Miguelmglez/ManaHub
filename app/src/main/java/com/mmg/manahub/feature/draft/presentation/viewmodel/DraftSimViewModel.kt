package com.mmg.manahub.feature.draft.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.di.DefaultDispatcher
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.BasicLandSlot
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckCard
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.DraftDeck
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.domain.engine.BotDrafter
import com.mmg.manahub.core.domain.engine.DraftDeckBuilder
import com.mmg.manahub.core.model.BoosterPack
import com.mmg.manahub.core.model.DraftCard
import com.mmg.manahub.core.model.DraftConfig
import com.mmg.manahub.core.model.DraftCuration
import com.mmg.manahub.core.model.DraftError
import com.mmg.manahub.core.model.DraftState
import com.mmg.manahub.core.model.DraftStatus
import com.mmg.manahub.core.domain.repository.DraftSimRepository
import com.mmg.manahub.feature.draft.domain.usecase.AutoPickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.CompleteDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.GetDraftableSimSetUseCase
import com.mmg.manahub.feature.draft.domain.usecase.MakePickUseCase
import com.mmg.manahub.feature.draft.domain.usecase.ObserveDraftUseCase
import com.mmg.manahub.feature.draft.domain.usecase.StartDraftUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the Draft Simulator flow (setup → drafting → result).
 *
 * Each screen in the flow gets its own [DraftSimViewModel] instance because Hilt scopes
 * view models to the `NavBackStackEntry`. To keep all three screens in sync, the ViewModel
 * does not rely on shared in-memory state across instances: instead it collects
 * [ObserveDraftUseCase], which emits the single active [DraftState] persisted by the use cases.
 * Drafting and Result screens therefore reconstruct their UI purely from the observed session.
 *
 * KMP migration — Phase 1: resolved by Koin (`koinViewModel()`), not Hilt. The [SavedStateHandle] is
 * Koin-injected and carries the current `NavBackStackEntry` arguments, so the `setCode`/`sessionId`
 * routing behaviour is identical to the previous Hilt-scoped instance.
 *
 * Draft Simulator Phase D: this ViewModel also owns the Deck tab's mainboard/sideboard curation
 * state ([inactivePoolIndices], [basicLandCounts], [targetLandCount]). It lives here — not as
 * transient Composable `remember` state — because [onCompleteDraft] needs to read it at save time;
 * the Deck tab composables are stateless and read/write through these StateFlows + methods.
 *
 * Phase E: [deckBuilder] is retained as a constructor param (still natively Koin-bound in
 * [com.mmg.manahub.feature.draft.di.DraftKoinModule] and still exercised directly by
 * `DraftSimIntegrationTest`) even though this class no longer calls it — E.6 removed the D.4
 * "seed a starting mainboard/sideboard split" behavior (every drafted card now starts active by
 * default) and E.4 replaced [applyLandSuggestionAutofill]'s land math with [BasicLandCalculator],
 * the same engine `DeckStudioViewModel` uses. Removing the parameter outright would ripple into the
 * Koin module and every test constructing this ViewModel for no behavioral gain — see the Phase E
 * memory note for the full rationale.
 */
class DraftSimViewModel(
    savedStateHandle: SavedStateHandle,
    private val startDraft: StartDraftUseCase,
    private val makePick: MakePickUseCase,
    private val autoPick: AutoPickUseCase,
    private val observeDraft: ObserveDraftUseCase,
    private val completeDraft: CompleteDraftUseCase,
    private val getDraftableSimSet: GetDraftableSimSetUseCase,
    private val analytics: AnalyticsHelper,
    private val botDrafter: BotDrafter,
    private val deckBuilder: DraftDeckBuilder,
    private val draftSimRepository: DraftSimRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** Set code passed via the [com.mmg.manahub.app.navigation.Screen.DraftSimSetup] route. */
    private val setCode: String = savedStateHandle.get<String>("setCode").orEmpty()

    private val _uiState = MutableStateFlow<DraftSimUiState>(DraftSimUiState.Loading)
    val uiState: StateFlow<DraftSimUiState> = _uiState.asStateFlow()

    // ── Deck tab curation (Phase D, reworked Phase E) ───────────────────────────

    /**
     * Positions (indices into the human seat's [com.mmg.manahub.core.model.DraftSeat.pool]) the
     * user toggled OFF (sideboard); everything else in the pool is mainboard.
     *
     * Phase E (E.2 bug fix): keyed by POOL POSITION, not `scryfallId` — a duplicated card (drafted
     * more than once) previously shared one Set<String> key across every copy, so toggling one copy
     * toggled all of them. The pool is APPEND-ONLY during a draft (`DefaultDraftEngine.applyHumanPick`
     * always does `pool = seat.pool + pickedCard`, never reorders/removes), so a card's index is a
     * stable, correct-per-copy identifier for the whole session.
     */
    private val _inactivePoolIndices = MutableStateFlow<Set<Int>>(emptySet())
    val inactivePoolIndices: StateFlow<Set<Int>> = _inactivePoolIndices.asStateFlow()

    /**
     * Basic-land name ("Plains"/"Island"/…) → count. Pre-save curation; mirrored into the active
     * session's [DraftState.curation] on every edit (Phase G, G.4 — see [persistCuration]) so a
     * process death mid-BUILDING doesn't silently lose it.
     */
    private val _basicLandCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val basicLandCounts: StateFlow<Map<String, Int>> = _basicLandCounts.asStateFlow()

    /** User-adjustable total basic-land target for the "Magic Land Suggestions" autofill. */
    private val _targetLandCount = MutableStateFlow(DeckFormat.DRAFT.targetLandCount)
    val targetLandCount: StateFlow<Int> = _targetLandCount.asStateFlow()

    /**
     * Cards the human has tapped so far this turn in Pick 2+ mode (Phase G, G.5/G.6). Lifted here
     * from what used to be `DraftingContent`'s local `remember` state: [onAutoPick] (timer expiry)
     * needs to see the CURRENT in-progress selection so it can complete it instead of discarding it
     * (G.5), and a Composable's `remember` state is invisible to a ViewModel-driven timer. Reset to
     * empty whenever a new turn starts (see [observeActiveSession]'s `lastDraftingTurnKey` tracking)
     * and, per G.6, only cleared on a CONFIRMED pick — never blindly on a no-op/rejected confirm, so
     * a re-entrancy-guarded no-op never silently drops the user's selection.
     */
    private val _selectedCardIds = MutableStateFlow<List<String>>(emptyList())
    val selectedCardIds: StateFlow<List<String>> = _selectedCardIds.asStateFlow()

    /**
     * True while [onCompleteDraft] is persisting the curated deck (Phase G, G.2) — lets the Deck
     * tab's Save button disable/show a loading spinner instead of silently ignoring a double-tap.
     */
    private val _isCompletingDraft = MutableStateFlow(false)
    val isCompletingDraft: StateFlow<Boolean> = _isCompletingDraft.asStateFlow()

    /** Latest known draft state — required because the use cases take [DraftState] as input. */
    private var currentDraftState: DraftState? = null

    /** Config of the active draft, used to restart the pick timer after each pick. */
    private var activeConfig: DraftConfig? = null

    /** Job that collects the persisted session Flow once a draft is started/observed. */
    private var observeJob: Job? = null

    /** Job for the per-pick countdown timer. */
    private var timerJob: Job? = null

    /** True while the app is backgrounded; pauses the pick timer. */
    private var isPaused: Boolean = false

    /**
     * Guards against concurrent picks (rapid double-tap, or timer expiry racing a manual tap).
     * Both [onPick] and [onAutoPick] read the same [currentDraftState]; without this guard the
     * second pick would apply against a stale state and overwrite the first one in Room.
     */
    private var isPickInFlight = false

    /** Guards against a double-tap on "Start Draft" launching two concurrent sessions. */
    private var isDraftStarting = false

    /** Guards against a double-tap on the cancel-draft confirmation double-firing (Phase G, G.8). */
    private var isCancellingDraft = false

    /** (round, pickNumber) last seen while DRAFTING — resets [_selectedCardIds] on a new turn. */
    private var lastDraftingTurnKey: Pair<Int, Int>? = null

    /**
     * True once this ViewModel instance has attempted to re-seed [inactivePoolIndices] /
     * [basicLandCounts] / [targetLandCount] from a persisted [DraftState.curation] (Phase G, G.4).
     * Guards [seedCurationIfNeeded] so it runs exactly once per instance — after that, this
     * ViewModel's own local StateFlows are the source of truth, and re-seeding on every subsequent
     * emission would fight the edits that triggered them (this instance's own [persistCuration]
     * calls round-trip back through [observeDraft]).
     */
    private var curationSeeded = false

    init {
        if (setCode.isNotBlank()) {
            loadSet(setCode)
        } else {
            // No set code (e.g. the Drafting/Result screens): attach to the active session.
            observeActiveSession()
        }
    }

    // ── Setup ───────────────────────────────────────────────────────────────────

    /** Re-attempts loading the set passed via the route (used for error-state retry). */
    fun retryLoadSet() {
        if (setCode.isNotBlank()) loadSet(setCode)
    }

    /**
     * Resolves the [com.mmg.manahub.core.model.DraftableSet] for [code] and
     * transitions to [DraftSimUiState.SetupReady] on success.
     */
    fun loadSet(code: String) {
        viewModelScope.launch {
            _uiState.value = DraftSimUiState.Loading
            when (val result = getDraftableSimSet(code)) {
                is DataResult.Success -> {
                    val set = result.data
                    _uiState.value = DraftSimUiState.SetupReady(
                        setCode = set.set.code,
                        setName = set.set.name,
                        boosterVersion = set.booster.schemaVersion.toString(),
                    )
                }
                is DataResult.Error -> {
                    _uiState.value = DraftSimUiState.Error(parseDraftError(result.message))
                }
            }
        }
    }

    /**
     * Starts a new draft with [config] and begins collecting the persisted session.
     */
    fun startDraft(config: DraftConfig) {
        if (isDraftStarting) return
        isDraftStarting = true
        viewModelScope.launch {
            try {
                _uiState.value = DraftSimUiState.Loading
                // Tag the crash session so any failure during this draft carries its parameters.
                FirebaseCrashlytics.getInstance().apply {
                    log("draft_sim: startDraft set=${config.setCode} mode=${config.mode.name}")
                    setCustomKey("draft_set_code", config.setCode)
                    setCustomKey("draft_mode", config.mode.name)
                    setCustomKey("draft_pack_count", config.packCount)
                    setCustomKey("draft_seat_count", config.seatCount)
                }
                when (val result = startDraft(config.setCode, config)) {
                    is DataResult.Success -> {
                        activeConfig = config
                        analytics.logEvent(
                            "draft_started",
                            mapOf(
                                "set_code" to config.setCode,
                                "mode" to config.mode.name,
                            ),
                        )
                        observeActiveSession()
                    }
                    is DataResult.Error -> {
                        _uiState.value =
                            DraftSimUiState.Error(DraftError.Unexpected(result.message))
                    }
                }
            } finally {
                isDraftStarting = false
            }
        }
    }

    // ── Drafting ──────────────────────────────────────────────────────────────────

    /**
     * Records the human's picks for the current turn — `1..DraftConfig.picksPerTurn` ids from the
     * current pack, in selection order (Pick 1 mode always calls this with a single-element list).
     *
     * G.6: [_selectedCardIds] is only cleared on a CONFIRMED (`DataResult.Success`) pick, never
     * blindly — if this call is rejected by the [isPickInFlight] guard (a near-simultaneous
     * auto-pick already in flight) or the engine call errors, the user's selection is preserved so
     * they never lose it to a silent no-op.
     */
    fun onConfirmPicks(scryfallIds: List<String>) {
        if (isPickInFlight) return
        val state = currentDraftState ?: return
        if (state.status != DraftStatus.DRAFTING) return
        if (scryfallIds.isEmpty()) return
        isPickInFlight = true
        cancelTimer()
        viewModelScope.launch {
            try {
                when (val result = makePick(state, scryfallIds)) {
                    is DataResult.Success -> {
                        // observeActiveSession() drives the new UI + timer; clear the in-progress
                        // Pick 2+ selection now that it was actually committed (G.6).
                        _selectedCardIds.value = emptyList()
                    }
                    is DataResult.Error ->
                        _uiState.value =
                            DraftSimUiState.Error(DraftError.Unexpected(result.message))
                }
            } finally {
                isPickInFlight = false
            }
        }
    }

    /**
     * Toggles [scryfallId] in the human's in-progress Pick 2+ selection for the current turn
     * (Phase G, G.5/G.6 — see [_selectedCardIds]'s KDoc for why this state lives here instead of in
     * `DraftingContent`'s Composable `remember`). No-op in Pick-1 mode
     * ([DraftConfig.picksPerTurn] <= 1), which uses `CardZoomSheet`'s direct tap-to-pick flow
     * instead and never touches this selection.
     */
    fun toggleCardSelection(scryfallId: String) {
        val state = currentDraftState ?: return
        val picksPerTurn = state.config.picksPerTurn
        if (picksPerTurn <= 1) return
        val confirmTarget = minOf(picksPerTurn, humanPack(state).size)
        _selectedCardIds.value = _selectedCardIds.value.let { current ->
            when {
                scryfallId in current -> current - scryfallId
                current.size < confirmTarget -> current + scryfallId
                else -> current
            }
        }
    }

    /**
     * Auto-picks for the human's current turn — fired by the pick timer expiring OR a manual
     * "Auto-pick" tap.
     *
     * G.5 fix: if the human already has an in-progress Pick 2+ selection ([_selectedCardIds]) when
     * this fires, that selection is PRESERVED — it is committed via [makePick] first (the engine's
     * `applyHumanPick` already supports a partial turn: submitting fewer than `picksPerTurn` ids
     * just advances `picksTakenInTurn` without rotating packs), then [autoPick] fills only the
     * REMAINING slots (its `pickCount` is computed from `picksPerTurn - picksTakenInTurn`, which by
     * then already reflects the just-committed partial pick). The user's manual taps are never
     * silently discarded, and the auto-filled remainder is guaranteed not to duplicate them.
     */
    fun onAutoPick() {
        if (isPickInFlight) return
        val state = currentDraftState ?: return
        if (state.status != DraftStatus.DRAFTING) return
        val partialSelection = _selectedCardIds.value
        isPickInFlight = true
        cancelTimer()
        viewModelScope.launch {
            try {
                if (partialSelection.isEmpty()) {
                    when (val result = autoPick(state)) {
                        is DataResult.Success -> Unit
                        is DataResult.Error ->
                            _uiState.value =
                                DraftSimUiState.Error(DraftError.Unexpected(result.message))
                    }
                } else {
                    when (val partialResult = makePick(state, partialSelection)) {
                        is DataResult.Success -> {
                            _selectedCardIds.value = emptyList()
                            when (val autoResult = autoPick(partialResult.data)) {
                                is DataResult.Success -> Unit
                                is DataResult.Error ->
                                    _uiState.value = DraftSimUiState.Error(
                                        DraftError.Unexpected(autoResult.message),
                                    )
                            }
                        }
                        is DataResult.Error ->
                            _uiState.value =
                                DraftSimUiState.Error(DraftError.Unexpected(partialResult.message))
                    }
                }
            } finally {
                isPickInFlight = false
            }
        }
    }

    // ── Result ──────────────────────────────────────────────────────────────────

    /**
     * Toggles the pool card at [poolIndex] between mainboard (active) and sideboard (inactive) —
     * the Deck tab's per-card hide/show icon (D.4). Instant, no confirmation dialog.
     *
     * E.2: keyed by pool position (see [inactivePoolIndices]'s KDoc), not `scryfallId`, so
     * duplicated copies of the same card toggle independently.
     */
    fun toggleCardActive(poolIndex: Int) {
        _inactivePoolIndices.value = _inactivePoolIndices.value.let { current ->
            if (poolIndex in current) current - poolIndex else current + poolIndex
        }
        persistCuration()
    }

    /** Sets the "Magic Land Suggestions" autofill target (D.7). Soft UX guard, not a hard rule. */
    fun setTargetLandCount(count: Int) {
        _targetLandCount.value = count.coerceIn(0, MAX_TARGET_LAND_COUNT)
        persistCuration()
    }

    /**
     * Sets [name]'s ("Plains"/"Island"/"Swamp"/"Mountain"/"Forest") basic-land count (D.7 stepper).
     *
     * G.7: clamped to the same `[0, MAX_TARGET_LAND_COUNT]` range [setTargetLandCount] already
     * enforces — previously only `coerceAtLeast(0)`, so repeatedly tapping "+" on one basic had no
     * upper bound. A single basic-land name theoretically COULD equal the whole deck's land target,
     * so this reuses that bound rather than a stricter per-land ceiling.
     */
    fun setBasicLandCount(name: String, count: Int) {
        val clamped = count.coerceIn(0, MAX_TARGET_LAND_COUNT)
        _basicLandCounts.value = if (clamped <= 0) {
            _basicLandCounts.value - name
        } else {
            _basicLandCounts.value + (name to clamped)
        }
        persistCuration()
    }

    /**
     * "Magic Land Suggestions" autofill (E.4 rework): overwrites [basicLandCounts] with
     * [BasicLandCalculator]'s per-color-pip-weighted distribution — the SAME engine
     * `DeckStudioViewModel.calculateLandDeltas` uses — instead of the old
     * [com.mmg.manahub.feature.draft.data.engine.ScoringDraftDeckBuilder.buildBasicLands]'s
     * simplistic [com.mmg.manahub.core.model.DraftSeat.colorCommitment]-proportion split (which
     * degenerated to "everything into Plains" whenever `colorCommitment` was empty — the fallback
     * path `buildBasicLands` takes for an entirely-colorless pool, but was also reachable any time
     * the human seat had no `colorCommitment` recorded, e.g. a mostly-artifact pool). Computed from
     * the CURRENT mainboard (active, non-basic-land pool cards — E.5 excludes drafted basics from
     * this screen's land accounting entirely) so the suggestion reflects the user's own curation,
     * not the seat's raw draft-time signal.
     */
    fun applyLandSuggestionAutofill() {
        val seat = currentDraftState?.seats?.firstOrNull { it.isHuman } ?: return
        val inactive = _inactivePoolIndices.value
        val activeCards = seat.pool
            .filterIndexed { index, _ -> index !in inactive }
            .filterNot { BasicLandCalculator.isBasicLand(it.card) }
        val mainboardNonLands = activeCards
            .filterNot { BasicLandCalculator.isLand(it.card) }
            .map { DeckCard(card = it.card, quantity = 1, isOwned = true) }
        val nonBasicLands = activeCards
            .filter { BasicLandCalculator.isLand(it.card) }
            .map { DeckCard(card = it.card, quantity = 1, isOwned = true) }
        val distribution = BasicLandCalculator.calculate(
            mainboard = mainboardNonLands,
            nonBasicLands = nonBasicLands,
            totalLandTarget = _targetLandCount.value,
            commanderIdentity = null,
        )
        _basicLandCounts.value = distribution.toMap()
            .mapNotNull { (symbol, count) -> BasicLandCalculator.LAND_FOR_COLOR[symbol]?.let { it to count } }
            .toMap()
        persistCuration()
    }

    /**
     * Cancels the active draft session (Phase E exit-confirmation flows, E.10b/E.10c): deletes the
     * persisted [DraftState] so it is never offered as "resume draft" again. Best-effort in the
     * sense that a delete FAILURE never blocks the user's exit (recorded, then swallowed) — but the
     * delete attempt itself IS awaited by the caller (F.6 fix): this is a `suspend fun`, not a
     * fire-and-forget `viewModelScope.launch`, specifically so `DraftSimulatorScreen`'s exit-dialog
     * confirm handler can `viewModel.onCancelDraft()` THEN `onBack()` in the same coroutine, instead
     * of racing the delete against the nav call that follows it (the old fire-and-forget shape let
     * `onBack()` fire before the delete had even started, so a screen reachable by backing further
     * could observe the stale, not-yet-cancelled session).
     */
    suspend fun onCancelDraft() {
        // G.8: guards a rapid double-tap on the exit-confirmation dialog's confirm button from
        // double-firing the delete + the "draft_cancelled" analytics breadcrumb. Low severity
        // (telemetry noise, not a functional bug — the caller's onBack() still fires unconditionally
        // after this returns, guarded or not) — same lightweight re-entrancy pattern as
        // [isPickInFlight]/[isDraftStarting]/[isCompletingDraft], nothing more elaborate.
        if (isCancellingDraft) return
        isCancellingDraft = true
        try {
            val state = currentDraftState ?: return
            runCatching { draftSimRepository.cancelSession(state) }
                .onFailure { FirebaseCrashlytics.getInstance().recordException(it) }
            analytics.logEvent(
                "draft_cancelled",
                mapOf("set_code" to state.config.setCode, "status" to state.status.name),
            )
        } finally {
            isCancellingDraft = false
        }
    }

    /**
     * Persists the Deck tab's CURATED mainboard/sideboard/basics, then transitions to
     * [DraftSimUiState.Complete]. Replaces the old blind `DraftDeckBuilder.build(seat)` re-derivation
     * (Phase D, D.8) — the final [DraftDeck] is assembled here from the human seat's full pool split
     * by [inactivePoolIndices] plus [basicLandCounts], so whatever the user curated on the Deck tab
     * is exactly what gets saved.
     */
    fun onCompleteDraft() {
        // G.2: guards a rapid double-tap on "Save Deck" from firing two concurrent
        // CompleteDraftUseCase calls (each independently building + persisting a deck), same
        // pattern as isPickInFlight/isDraftStarting above. [_isCompletingDraft] is also exposed to
        // the UI so the Save button can disable/show a loading spinner instead of the tap just
        // silently doing nothing.
        if (_isCompletingDraft.value) return
        val state = currentDraftState ?: return
        _isCompletingDraft.value = true
        viewModelScope.launch {
            try {
                _uiState.value = DraftSimUiState.Loading
                val setCode = state.config.setCode
                val pool = state.seats.firstOrNull { it.isHuman }?.pool.orEmpty()
                val inactiveIndices = _inactivePoolIndices.value
                val mainboard = pool.filterIndexed { index, _ -> index !in inactiveIndices }
                val sideboard = pool.filterIndexed { index, _ -> index in inactiveIndices }
                val basics = _basicLandCounts.value
                    .filterValues { it > 0 }
                    .map { (name, count) -> BasicLandSlot(scryfallId = "", name = name, count = count) }
                val curatedDeck = DraftDeck(mainboard = mainboard, basics = basics, sideboard = sideboard)

                when (val result = completeDraft(state, curatedDeck)) {
                    is DataResult.Success -> {
                        val deckId = result.data
                        analytics.logEvent(
                            "draft_completed",
                            mapOf(
                                "set_code" to setCode,
                                "pick_count" to pool.size.toLong(),
                            ),
                        )
                        analytics.logEvent(
                            "deck_saved_from_draft",
                            mapOf(
                                "deck_id" to deckId,
                                "set_code" to setCode,
                                // Phase D: the saved deck now reflects user curation, not a blind
                                // top-23 pick — track the split so adoption/behavior is observable.
                                "mainboard_count" to mainboard.size.toLong(),
                                "sideboard_count" to sideboard.size.toLong(),
                            ),
                        )
                        _uiState.value = DraftSimUiState.Complete(deckId)
                    }
                    is DataResult.Error ->
                        _uiState.value = DraftSimUiState.Error(DraftError.Unexpected(result.message))
                }
            } finally {
                _isCompletingDraft.value = false
            }
        }
    }

    // ── Lifecycle hooks for the timer ───────────────────────────────────────────

    /** Called from the Drafting screen when it enters the background — pauses the timer. */
    fun onScreenPaused() {
        isPaused = true
        cancelTimer()
    }

    /** Called from the Drafting screen when it returns to the foreground — resumes the timer. */
    fun onScreenResumed() {
        isPaused = false
        val state = currentDraftState ?: return
        if (state.status == DraftStatus.DRAFTING) {
            restartTimerIfConfigured()
        }
    }

    // ── Internal ────────────────────────────────────────────────────────────────

    private fun observeActiveSession() {
        if (observeJob != null) return
        observeJob = viewModelScope.launch {
            observeDraft().collect { state ->
                if (state == null) {
                    currentDraftState = null
                    return@collect
                }
                currentDraftState = state
                activeConfig = state.config
                when (state.status) {
                    DraftStatus.SETUP -> _uiState.value = DraftSimUiState.Loading
                    DraftStatus.DRAFTING -> {
                        // G.5/G.6: a new turn (round/pickNumber changed) always clears any leftover
                        // in-progress Pick 2+ selection — a safety net alongside onConfirmPicks'/
                        // onAutoPick's own clears, covering e.g. a resumed session or any path that
                        // advances the turn without going through either of those methods.
                        val turnKey = state.round to state.pickNumber
                        if (turnKey != lastDraftingTurnKey) {
                            _selectedCardIds.value = emptyList()
                            lastDraftingTurnKey = turnKey
                        }
                        val pack = humanPack(state)
                        val humanSeat = state.seats.firstOrNull { it.isHuman }
                        val suggestedId = if (humanSeat != null && pack.isNotEmpty()) {
                            // Suggested pick MUST use the same engine/drafter as the bots so the hint
                            // matches how a bot would actually draft. Best-effort load: null → fallback.
                            // The human seat carries no diversity prior, so the suggestion is neutral.
                            // The engine load (network/cache) and the scoring are CPU/IO-bound, so run
                            // them off the Main collector thread to keep the UI responsive.
                            withContext(defaultDispatcher) {
                                val engineConfig =
                                    draftSimRepository.getEngineConfig(state.config.setCode)
                                botDrafter.pick(
                                    humanSeat,
                                    BoosterPack("temp", pack),
                                    state.round,
                                    state.pickNumber,
                                    engineConfig,
                                ).card.scryfallId
                            }
                        } else null

                        _uiState.value = DraftSimUiState.Drafting(
                            state = state,
                            currentPack = pack,
                            poolSize = humanPoolSize(state),
                            timerSecondsLeft = state.config.pickTimerSeconds,
                            suggestedPickId = suggestedId,
                        )
                        restartTimerIfConfigured()
                    }
                    DraftStatus.BUILDING -> {
                        cancelTimer()
                        seedCurationIfNeeded(state) // G.4: recover curation after a process death.
                        _uiState.value = DraftSimUiState.Building(state)
                    }
                    DraftStatus.COMPLETE -> {
                        cancelTimer()
                        seedCurationIfNeeded(state)
                        // Deck UUID is delivered via onCompleteDraft(); keep Building UI otherwise.
                        if (_uiState.value !is DraftSimUiState.Complete) {
                            _uiState.value = DraftSimUiState.Building(state)
                        }
                    }
                }
            }
        }
    }

    /** Cards in the pack currently in front of the human seat (empty if none in flight). */
    private fun humanPack(state: DraftState): List<DraftCard> {
        val human = state.seats.firstOrNull { it.isHuman } ?: return emptyList()
        return state.packsInFlight[human.index]?.cards ?: emptyList()
    }

    /** Number of cards the human has drafted so far. */
    private fun humanPoolSize(state: DraftState): Int =
        state.seats.firstOrNull { it.isHuman }?.pool?.size ?: 0

    /**
     * G.4: re-seeds [inactivePoolIndices]/[basicLandCounts]/[targetLandCount] from a persisted
     * [DraftState.curation] — recovers the user's mainboard/sideboard/basic-land choices after the
     * app process is killed mid-BUILDING (a real Android low-memory scenario) instead of silently
     * resetting to "everything active, no lands set". Guarded by [curationSeeded] so it runs exactly
     * once per ViewModel instance — see that flag's KDoc for why re-seeding on every emission would
     * be wrong.
     */
    private fun seedCurationIfNeeded(state: DraftState) {
        if (curationSeeded) return
        curationSeeded = true
        val curation = state.curation ?: return
        _inactivePoolIndices.value = curation.inactivePoolIndices
        _basicLandCounts.value = curation.basicLandCounts
        _targetLandCount.value = curation.targetLandCount
    }

    /**
     * G.4: persists the Deck tab's current curation into the active session so it survives a
     * process death while `status == BUILDING` (see [DraftCuration] and [DraftState.curation]).
     * Called after every curation edit ([toggleCardActive]/[setBasicLandCount]/
     * [setTargetLandCount]/[applyLandSuggestionAutofill]); a no-op once the draft is no longer
     * BUILDING (nothing to persist while DRAFTING, and a COMPLETE session's curation no longer
     * matters). Best-effort: a save failure is recorded but never surfaced to the user — curation
     * stays correct in memory for the rest of this instance's lifetime either way, only a
     * SUBSEQUENT process-death recovery would miss this particular edit.
     */
    private fun persistCuration() {
        val state = currentDraftState ?: return
        if (state.status != DraftStatus.BUILDING) return
        val updated = state.copy(
            curation = DraftCuration(
                inactivePoolIndices = _inactivePoolIndices.value,
                basicLandCounts = _basicLandCounts.value,
                targetLandCount = _targetLandCount.value,
            ),
        )
        currentDraftState = updated
        viewModelScope.launch {
            runCatching { draftSimRepository.saveSession(updated) }
                .onFailure { FirebaseCrashlytics.getInstance().recordException(it) }
        }
    }

    /**
     * Restarts the per-pick countdown. When it reaches zero an auto-pick is triggered.
     * No-op when no timer is configured or the screen is paused.
     */
    private fun restartTimerIfConfigured() {
        cancelTimer()
        val seconds = activeConfig?.pickTimerSeconds ?: return
        if (seconds <= 0 || isPaused) return

        timerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining >= 0) {
                val current = _uiState.value
                if (current is DraftSimUiState.Drafting) {
                    _uiState.value = current.copy(timerSecondsLeft = remaining)
                }
                if (remaining == 0) {
                    onAutoPick()
                    return@launch
                }
                delay(1_000L)
                remaining--
            }
        }
    }

    private fun cancelTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Maps a [DataResult.Error] message string back to a [DraftError] for display.
     * The repository serializes [DraftError] via `.toString()` — this reverses that mapping
     * so the UI shows a relevant message instead of always "No connection and no cached data".
     */
    private fun parseDraftError(message: String): DraftError = when {
        message.contains("SetNotDraftable", ignoreCase = true)  -> DraftError.SetNotDraftable
        message.contains("RatingsMissing",  ignoreCase = true)  -> DraftError.RatingsMissing
        message.contains("SetNotDownloaded",ignoreCase = true)  -> DraftError.SetNotDownloaded
        message.contains("OfflineNoCache",  ignoreCase = true)  -> DraftError.OfflineNoCache
        else -> DraftError.Unexpected(message)
    }

    override fun onCleared() {
        super.onCleared()
        cancelTimer()
    }

    private companion object {
        /** Soft UX guard on the "Magic Land Suggestions" target stepper (D.7) — not a hard rule. */
        const val MAX_TARGET_LAND_COUNT = 30
    }
}
