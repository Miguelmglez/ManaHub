package com.mmg.manahub.feature.online.presentation.lobby

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.online.domain.model.OnlineParticipant
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.online.domain.model.ParticipantStatus
import com.mmg.manahub.core.online.domain.model.SessionEvent
import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import com.mmg.manahub.core.online.domain.usecase.JoinSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.LeaveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.ObserveSessionUseCase
import com.mmg.manahub.core.online.presentation.classifyOnlineJoinError
import com.mmg.manahub.core.online.presentation.mapOnlineBackendError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the join lobby screen.
 *
 * Responsibilities:
 * - Validate and submit a 6-digit numeric session code via [JoinSessionUseCase].
 * - Subscribe to Realtime [SessionEvent]s via [ObserveSessionUseCase].
 * - Toggle the player's ready state via [OnlineSessionRepository].
 * - Trigger [onGameStart] automatically when [SessionEvent.SessionStatusChanged] → ACTIVE.
 */
@HiltViewModel
class LobbyJoinViewModel @Inject constructor(
    private val joinSessionUseCase: JoinSessionUseCase,
    private val observeSessionUseCase: ObserveSessionUseCase,
    private val leaveSessionUseCase: LeaveSessionUseCase,
    private val repository: OnlineSessionRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    // ── UI state ──────────────────────────────────────────────────────────────

    /**
     * Full UI state for the join lobby.
     *
     * @property isLoading True while a network call is in flight.
     * @property codeInput Raw text entered by the user (digits only, max 6 chars).
     * @property displayName Player name shown to other participants.
     * @property selectedThemeKey Key for the chosen [PlayerThemeColors].
     * @property sessionId Assigned after a successful join.
     * @property slotIndex Seat index assigned by the backend.
     * @property participants Live list of all active participants.
     * @property allReady True when every active participant has toggled ready.
     * @property isReady Whether this player has marked themselves ready.
     * @property sessionStatus Current lifecycle status of the session.
     * @property error User-visible error message, cleared after display.
     */
    data class UiState(
        val isLoading: Boolean = false,
        val codeInput: String = "",
        val displayName: String = "",
        val selectedThemeKey: String = "Crimson",
        val sessionId: String? = null,
        val slotIndex: Int = -1,
        val sessionMode: String = "STANDARD",
        val sessionPlayerCount: Int = 2,
        val participants: List<OnlineParticipant> = emptyList(),
        val allReady: Boolean = false,
        val isReady: Boolean = false,
        val sessionStatus: OnlineSessionStatus = OnlineSessionStatus.LOBBY,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val crashlytics = FirebaseCrashlytics.getInstance()

    /**
     * A separate coroutine scope used exclusively for cleanup work in [onCleared].
     * [viewModelScope] is cancelled *before* [onCleared] is invoked, so any coroutine
     * launched on it inside [onCleared] is immediately cancelled and never runs.
     * This scope is cancelled manually at the end of [onCleared] after the work completes.
     */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var gameLaunched = false

    /**
     * Opaque identity token captured from [JoinSessionUseCase]'s response when this VM's caller
     * has no Supabase Auth session at all (a guest — see the 2026-08 anonymous-sign-in removal).
     * Null for a real signed-in account, in which case every RPC below resolves identity via
     * `auth.uid()` exactly as before. Threaded into every subsequent call this VM makes for the
     * lobby session (`setReady`, snapshot polling, `leaveSession`).
     */
    private var guestToken: String? = null

    /** Per-participant-id consecutive-miss counter, used by the two-strike ghost rule. */
    private var participantMissStreak: Map<String, Int> = emptyMap()

    private val numericCodeRegex = Regex("^[0-9]{6}$")

    init {
        viewModelScope.launch {
            userPreferencesDataStore.playerNameFlow
                .first()
                .takeIf { it.isNotBlank() }
                ?.let { name -> _uiState.update { it.copy(displayName = name) } }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /**
     * Updates the code input field.
     * Only digits are accepted; capped at 6 characters.
     */
    fun onCodeChanged(code: String) {
        _uiState.update {
            it.copy(codeInput = code.filter { c -> c.isDigit() }.take(6))
        }
    }

    /**
     * Updates the display name field, capped at 32 characters.
     */
    fun onDisplayNameChanged(name: String) {
        _uiState.update { it.copy(displayName = name.take(32)) }
    }

    /** Updates the selected player theme key. */
    fun onThemeChanged(themeKey: String) {
        _uiState.update { it.copy(selectedThemeKey = themeKey) }
    }

    /**
     * Pre-fills the code input (e.g., when arriving via deep link).
     * Does nothing if the session has already been joined. Digit-filtered to match
     * [onCodeChanged] — codes are strictly 6-digit numeric.
     */
    fun prefillCode(code: String) {
        if (_uiState.value.sessionId != null) return
        _uiState.update { it.copy(codeInput = code.filter { c -> c.isDigit() }.take(6)) }
    }

    /**
     * Attempts to join the session identified by the current [UiState.codeInput].
     *
     * On success: stores the [sessionId] and [slotIndex], connects to Realtime.
     * On failure: maps backend errors to friendly messages.
     *
     * @param onGameStart Invoked on the main thread when the session becomes ACTIVE. Also carries
     *   the guest identity token (null for a real signed-in account) so [GameViewModel] can keep
     *   authenticating in-game RPCs the same way.
     */
    fun joinSession(onGameStart: (sessionId: String, slotIndex: Int, mode: String, playerCount: Int, guestToken: String?) -> Unit) {
        val state = _uiState.value
        if (state.sessionId != null || state.isLoading) return // already joined or in flight

        if (!numericCodeRegex.matches(state.codeInput)) {
            _uiState.update {
                it.copy(error = appContext.getString(R.string.lobby_error_invalid_code))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            crashlytics.log("online_session_join_started: code_length=${state.codeInput.length}")

            // No auth gate: the RPC mints/validates identity server-side via an optional
            // guest_token when the caller has no Supabase Auth session at all (2026-08 — guests
            // never create a Supabase Auth user of any kind). Real signed-in accounts keep
            // resolving identity via auth.uid() exactly as before.
            joinSessionUseCase(
                code = state.codeInput,
                displayName = state.displayName.ifBlank { appContext.getString(R.string.lobby_player_default_name) },
                themeKey = state.selectedThemeKey,
            ).fold(
                onSuccess = { (sessionId, slotIndex, token) ->
                    crashlytics.log("online_session_join_success: slot_index=$slotIndex")
                    crashlytics.setCustomKey("online_session_id_hash", sessionId.take(8))
                    crashlytics.setCustomKey("online_session_slot_index", slotIndex)
                    this@LobbyJoinViewModel.guestToken = token
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sessionId = sessionId,
                            slotIndex = slotIndex,
                        )
                    }
                    // Fetch session metadata and existing participants for the waiting room.
                    // Failure here is self-healing: startLobbyPolling refreshes sessionMode/
                    // sessionPlayerCount from every subsequent snapshot (audit finding #5).
                    observeSessionUseCase.getSnapshot(sessionId, guestToken).onSuccess { snapshot ->
                        crashlytics.setCustomKey("online_session_game_mode", snapshot.session.gameMode)
                        crashlytics.setCustomKey("online_session_player_count", snapshot.session.playerCount)
                        val ps = snapshot.participants.filter { it.status != ParticipantStatus.LEFT }
                        _uiState.update {
                            it.copy(
                                sessionMode        = snapshot.session.gameMode,
                                sessionPlayerCount = snapshot.session.playerCount,
                                participants       = ps,
                                allReady           = ps.isNotEmpty() && ps.all { p -> p.isReady },
                            )
                        }
                    }
                    connectAndObserve(sessionId, onGameStart)
                    setReady(true)
                    // Reliable 3s polling — primary mechanism for participant sync and
                    // game-start detection regardless of Realtime availability.
                    startLobbyPolling(sessionId, onGameStart)
                },
                onFailure = { throwable ->
                    val errorToken = classifyOnlineJoinError(throwable.message)
                    crashlytics.log("online_session_join_failed: $errorToken")
                    crashlytics.setCustomKey("online_session_join_error", errorToken)
                    crashlytics.setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    crashlytics.recordException(throwable)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = mapOnlineBackendError(appContext, throwable.message),
                        )
                    }
                },
            )
        }
    }

    /**
     * Toggles this player's ready state on the backend.
     * No-op if the session is not yet joined.
     */
    fun setReady(ready: Boolean) {
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            crashlytics.log("online_session_ready_toggled: ready=$ready")
            _uiState.update { it.copy(isReady = ready) }
            repository.setReady(sessionId, ready, guestToken).onFailure { throwable ->
                // Revert optimistic update on failure.
                // The raw throwable.message is intentionally kept out of the log line to
                // avoid leaking backend internals into Crashlytics breadcrumbs visible to
                // all dashboard users; the full detail is captured by recordException().
                crashlytics.log("online_session_ready_toggle_failed: ready=$ready type=${throwable::class.simpleName}")
                crashlytics.setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                crashlytics.recordException(throwable)
                _uiState.update {
                    it.copy(
                        isReady = !ready,
                        error = mapOnlineBackendError(appContext, throwable.message),
                    )
                }
            }
        }
    }

    /**
     * Leaves the current session and disconnects from Realtime.
     *
     * @param onNavigateBack Called once the operation completes.
     */
    fun leaveSession(onNavigateBack: () -> Unit) {
        val sessionId = _uiState.value.sessionId
        viewModelScope.launch {
            if (sessionId != null) {
                crashlytics.log("online_session_joiner_left: slot_index=${_uiState.value.slotIndex}")
                leaveSessionUseCase(sessionId, guestToken)
            }
            onNavigateBack()
        }
    }

    /** Clears the current error so the UI can dismiss the toast. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Connects to the Realtime channel for [sessionId] and starts collecting events.
     * This is an optional fast-path on top of [startLobbyPolling] — failure is silent
     * because polling guarantees lobby correctness regardless of Realtime availability.
     */
    private fun connectAndObserve(
        sessionId: String,
        onGameStart: (sessionId: String, slotIndex: Int, mode: String, playerCount: Int, guestToken: String?) -> Unit,
    ) {
        viewModelScope.launch {
            crashlytics.log("online_realtime_connect_started: joiner")
            runCatching { observeSessionUseCase.connect(sessionId) }
                .onFailure { throwable ->
                    if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                    crashlytics.log("online_realtime_connect_failed: joiner type=${throwable::class.simpleName}")
                    return@launch
                }
            observeSessionUseCase(sessionId)
                .onEach { event -> handleEvent(event, sessionId, onGameStart) }
                .catch { throwable ->
                    crashlytics.log("online_realtime_stream_error: joiner type=${throwable::class.simpleName}")
                }
                .collect()
        }
    }

    /**
     * Polls the session snapshot every 3 s, syncs the participant list, refreshes session
     * metadata, and checks whether the host has started the game. This is the primary
     * mechanism for game-start detection because Realtime CDC may not be available in all
     * environments.
     *
     * Stops once the session ends, once [gameLaunched] (audit finding #9), or once the
     * backend reports a terminal status (audit finding #8).
     */
    private fun startLobbyPolling(
        sessionId: String,
        onGameStart: (sessionId: String, slotIndex: Int, mode: String, playerCount: Int, guestToken: String?) -> Unit,
    ) {
        viewModelScope.launch {
            while (_uiState.value.sessionId != null && !gameLaunched) {
                kotlinx.coroutines.delay(3_000L)
                if (_uiState.value.sessionId == null || gameLaunched) break
                observeSessionUseCase.getSnapshot(sessionId, guestToken).onSuccess { snapshot ->
                    if (snapshot.session.status.isTerminal()) {
                        handleTerminalStatus(snapshot.session.status)
                        return@launch
                    }

                    // Merge by id (never a raw replace — audit finding #4) and refresh
                    // sessionMode/sessionPlayerCount from every snapshot so a failed initial
                    // getSnapshot call after join self-heals within one poll cycle instead of
                    // launching the game with the "STANDARD"/2 defaults (audit finding #5).
                    val (merged, nextStreak) = mergeParticipantsById(
                        current = _uiState.value.participants,
                        snapshot = snapshot.participants,
                        missingStreak = participantMissStreak,
                    )
                    participantMissStreak = nextStreak
                    _uiState.update { state ->
                        state.copy(
                            participants = merged,
                            allReady = merged.isNotEmpty() && merged.all { it.isReady },
                            sessionMode = snapshot.session.gameMode,
                            sessionPlayerCount = snapshot.session.playerCount,
                        )
                    }

                    if (snapshot.session.status == OnlineSessionStatus.ACTIVE &&
                        _uiState.value.sessionStatus != OnlineSessionStatus.ACTIVE
                    ) {
                        val s = _uiState.value
                        gameLaunched = true
                        _uiState.update { it.copy(sessionStatus = OnlineSessionStatus.ACTIVE) }
                        crashlytics.log("online_session_game_started_via_poll: slot=${s.slotIndex}")
                        onGameStart(sessionId, s.slotIndex, s.sessionMode, s.sessionPlayerCount, guestToken)
                    }
                }
            }
        }
    }

    /**
     * Resets the join lobby to its pre-join state after the session transitions to a
     * terminal status (FINISHED or ABANDONED). Called from both the poll loop and the
     * Realtime handler so either mechanism reacts identically (audit findings #8/#9/#13) —
     * previously the joiner waited indefinitely with no user-visible message. User-entered
     * preferences (display name, theme) are preserved.
     */
    private fun handleTerminalStatus(status: OnlineSessionStatus) {
        crashlytics.log("online_session_terminal: joiner_view status=${status.name} slot_index=${_uiState.value.slotIndex}")
        participantMissStreak = emptyMap()
        guestToken = null
        val message = if (status == OnlineSessionStatus.FINISHED) {
            appContext.getString(R.string.lobby_session_finished_msg)
        } else {
            appContext.getString(R.string.lobby_session_abandoned_msg)
        }
        _uiState.update {
            UiState(
                displayName = it.displayName,
                selectedThemeKey = it.selectedThemeKey,
                error = message,
            )
        }
    }

    private fun handleEvent(
        event: SessionEvent,
        sessionId: String,
        onGameStart: (String, Int, String, Int, String?) -> Unit,
    ) {
        when (event) {
            is SessionEvent.ParticipantUpdated -> {
                _uiState.update { state ->
                    val updated = state.participants
                        .filterNot { it.id == event.participant.id }
                        .plus(event.participant)
                        .filter { it.status != ParticipantStatus.LEFT }
                        .sortedBy { it.slotIndex }
                    state.copy(
                        participants = updated,
                        allReady = updated.isNotEmpty() && updated.all { it.isReady },
                    )
                }
            }

            is SessionEvent.SessionStatusChanged -> {
                crashlytics.log("online_session_status_changed: joiner ${event.status.name}")
                // Capture the PREVIOUS status before updating state, and gate the ACTIVE branch
                // on it — otherwise a poll-detected ACTIVE (which already fired onGameStart) can
                // be immediately re-fired by a lagging Realtime CDC event for the same
                // transition, double-navigating into the game screen (audit finding #3).
                val previousStatus = _uiState.value.sessionStatus
                _uiState.update { it.copy(sessionStatus = event.status) }
                when {
                    event.status == OnlineSessionStatus.ACTIVE && previousStatus != OnlineSessionStatus.ACTIVE -> {
                        val s = _uiState.value
                        gameLaunched = true
                        crashlytics.log("online_session_game_started: slot_index=${s.slotIndex} mode=${s.sessionMode}")
                        onGameStart(sessionId, s.slotIndex, s.sessionMode, s.sessionPlayerCount, guestToken)
                    }
                    event.status.isTerminal() -> handleTerminalStatus(event.status)
                    else -> Unit
                }
            }

            is SessionEvent.Error -> {
                crashlytics.log("online_session_event_error: joiner type=${event::class.simpleName}")
                _uiState.update { it.copy(error = mapOnlineBackendError(appContext, event.message)) }
            }

            else -> Unit // Other events handled in GameViewModel (Phase 3)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // viewModelScope is already cancelled when onCleared() is called, so coroutines
        // launched on it here would be silently dropped. cleanupScope is a separate
        // scope that lives only for this teardown work and is cancelled once done.
        val sessionId = _uiState.value.sessionId ?: run {
            cleanupScope.cancel()
            return
        }
        if (!gameLaunched) {
            cleanupScope.launch {
                try {
                    observeSessionUseCase.disconnect(sessionId)
                } finally {
                    cleanupScope.cancel()
                }
            }
        } else {
            cleanupScope.cancel()
        }
    }
}
