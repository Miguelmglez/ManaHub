package com.mmg.manahub.feature.draft.presentation.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.di.DefaultDispatcher
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.feature.draft.domain.engine.BotDrafter
import com.mmg.manahub.feature.draft.domain.model.BoosterPack
import com.mmg.manahub.feature.draft.domain.model.DraftCard
import com.mmg.manahub.feature.draft.domain.model.DraftConfig
import com.mmg.manahub.feature.draft.domain.model.DraftError
import com.mmg.manahub.feature.draft.domain.model.DraftState
import com.mmg.manahub.feature.draft.domain.model.DraftStatus
import com.mmg.manahub.feature.draft.domain.repository.DraftSimRepository
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
    private val draftSimRepository: DraftSimRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    /** Set code passed via the [com.mmg.manahub.app.navigation.Screen.DraftSimSetup] route. */
    private val setCode: String = savedStateHandle.get<String>("setCode").orEmpty()

    private val _uiState = MutableStateFlow<DraftSimUiState>(DraftSimUiState.Loading)
    val uiState: StateFlow<DraftSimUiState> = _uiState.asStateFlow()

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
     * Resolves the [com.mmg.manahub.feature.draft.domain.model.DraftableSet] for [code] and
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

    /** Records the human's pick for [scryfallId] in the current pack. */
    fun onPick(scryfallId: String) {
        if (isPickInFlight) return
        val state = currentDraftState ?: return
        if (state.status != DraftStatus.DRAFTING) return
        isPickInFlight = true
        cancelTimer()
        viewModelScope.launch {
            try {
                when (val result = makePick(state, scryfallId)) {
                    is DataResult.Success -> Unit // observeActiveSession() drives the new UI + timer
                    is DataResult.Error ->
                        _uiState.value =
                            DraftSimUiState.Error(DraftError.Unexpected(result.message))
                }
            } finally {
                isPickInFlight = false
            }
        }
    }

    /** Auto-picks the best card from the human's current pack (timeout / convenience action). */
    fun onAutoPick() {
        if (isPickInFlight) return
        val state = currentDraftState ?: return
        if (state.status != DraftStatus.DRAFTING) return
        isPickInFlight = true
        cancelTimer()
        viewModelScope.launch {
            try {
                when (val result = autoPick(state)) {
                    is DataResult.Success -> Unit
                    is DataResult.Error ->
                        _uiState.value =
                            DraftSimUiState.Error(DraftError.Unexpected(result.message))
                }
            } finally {
                isPickInFlight = false
            }
        }
    }

    // ── Result ──────────────────────────────────────────────────────────────────

    /** Builds and persists the final deck, then transitions to [DraftSimUiState.Complete]. */
    fun onCompleteDraft() {
        val state = currentDraftState ?: return
        viewModelScope.launch {
            _uiState.value = DraftSimUiState.Loading
            val setCode = state.config.setCode
            val pickCount = state.seats.firstOrNull { it.isHuman }?.pool?.size?.toLong() ?: 0L
            when (val result = completeDraft(state)) {
                is DataResult.Success -> {
                    val deckId = result.data
                    analytics.logEvent(
                        "draft_completed",
                        mapOf(
                            "set_code" to setCode,
                            "pick_count" to pickCount,
                        ),
                    )
                    analytics.logEvent(
                        "deck_saved_from_draft",
                        mapOf(
                            "deck_id" to deckId,
                            "set_code" to setCode,
                        ),
                    )
                    _uiState.value = DraftSimUiState.Complete(deckId)
                }
                is DataResult.Error ->
                    _uiState.value = DraftSimUiState.Error(DraftError.Unexpected(result.message))
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
                        _uiState.value = DraftSimUiState.Building(state)
                    }
                    DraftStatus.COMPLETE -> {
                        cancelTimer()
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
}
