package com.mmg.manahub.feature.online.presentation.lobby

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.core.online.domain.model.OnlineParticipant
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.online.domain.model.ParticipantStatus
import com.mmg.manahub.core.online.domain.model.SessionEvent
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.core.online.domain.usecase.JoinSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.LeaveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.ObserveSessionUseCase
import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
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
 * - Validate and submit a 6-character session code via [JoinSessionUseCase].
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
    private val authRepository: AuthRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    // ── UI state ──────────────────────────────────────────────────────────────

    /**
     * Full UI state for the join lobby.
     *
     * @property isLoading True while a network call is in flight.
     * @property codeInput Raw text entered by the user (auto-uppercased, max 6 chars).
     * @property displayName Player name shown to other participants.
     * @property selectedThemeKey Key for the chosen [PlayerThemeColors].
     * @property sessionId Assigned after a successful join.
     * @property slotIndex Seat index assigned by the backend.
     * @property participants Live list of all active participants.
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
     * Does nothing if the session has already been joined.
     */
    fun prefillCode(code: String) {
        if (_uiState.value.sessionId != null) return
        _uiState.update { it.copy(codeInput = code.uppercase().take(6)) }
    }

    /**
     * Attempts to join the session identified by the current [UiState.codeInput].
     *
     * On success: stores the [sessionId] and [slotIndex], connects to Realtime.
     * On failure: maps backend errors to friendly messages.
     *
     * @param onGameStart Invoked on the main thread when the session becomes ACTIVE.
     */
    fun joinSession(onGameStart: (sessionId: String, slotIndex: Int, mode: String, playerCount: Int) -> Unit) {
        val state = _uiState.value
        if (state.sessionId != null) return // already joined

        if (!numericCodeRegex.matches(state.codeInput)) {
            _uiState.update {
                it.copy(error = appContext.getString(R.string.lobby_error_invalid_code))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Ensure a Supabase session exists; sign in anonymously for guests.
            if (authRepository.sessionState.value is SessionState.Unauthenticated) {
                val anonResult = authRepository.signInAnonymously()
                if (anonResult is AuthResult.Error) {
                    val msg = anonResult.error.toString()
                    crashlytics.log("online_session_anon_signin_failed: $msg")
                    _uiState.update { it.copy(isLoading = false, error = mapBackendError(msg)) }
                    return@launch
                }
            }

            crashlytics.log("online_session_join_started: code_length=${state.codeInput.length}")

            joinSessionUseCase(
                code = state.codeInput,
                displayName = state.displayName.ifBlank { appContext.getString(R.string.lobby_player_default_name) },
                themeKey = state.selectedThemeKey,
            ).fold(
                onSuccess = { (sessionId, slotIndex) ->
                    crashlytics.log("online_session_join_success: slot_index=$slotIndex")
                    crashlytics.setCustomKey("online_session_id_hash", sessionId.take(8))
                    crashlytics.setCustomKey("online_session_slot_index", slotIndex)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sessionId = sessionId,
                            slotIndex = slotIndex,
                        )
                    }
                    // Fetch session metadata and existing participants for the waiting room
                    observeSessionUseCase.getSnapshot(sessionId).onSuccess { snapshot ->
                        crashlytics.setCustomKey("online_session_game_mode", snapshot.session.gameMode)
                        crashlytics.setCustomKey("online_session_player_count", snapshot.session.playerCount)
                        val ps = snapshot.participants.filter { it.status != ParticipantStatus.LEFT }
                        _uiState.update {
                            it.copy(
                                sessionMode        = snapshot.session.gameMode,
                                sessionPlayerCount = snapshot.session.playerCount,
                                participants       = ps,
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
                    val errorToken = classifyJoinError(throwable.message)
                    crashlytics.log("online_session_join_failed: $errorToken")
                    crashlytics.setCustomKey("online_session_join_error", errorToken)
                    crashlytics.setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    crashlytics.recordException(throwable)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = mapBackendError(throwable.message),
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
            repository.setReady(sessionId, ready).onFailure { throwable ->
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
                        error = mapBackendError(throwable.message),
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
                leaveSessionUseCase(sessionId)
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
        onGameStart: (sessionId: String, slotIndex: Int, mode: String, playerCount: Int) -> Unit,
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
     * Polls the session snapshot every 3 s, syncs the participant list, and checks whether
     * the host has started the game. This is the primary mechanism for game-start detection
     * because Realtime CDC may not be available in all environments.
     */
    private fun startLobbyPolling(
        sessionId: String,
        onGameStart: (sessionId: String, slotIndex: Int, mode: String, playerCount: Int) -> Unit,
    ) {
        viewModelScope.launch {
            while (_uiState.value.sessionId != null) {
                kotlinx.coroutines.delay(3_000L)
                if (_uiState.value.sessionId == null) break
                observeSessionUseCase.getSnapshot(sessionId).onSuccess { snapshot ->
                    val ps = snapshot.participants.filter { it.status != ParticipantStatus.LEFT }
                    _uiState.update { state ->
                        state.copy(participants = if (ps.isNotEmpty()) ps.sortedBy { it.slotIndex } else state.participants)
                    }
                    if (snapshot.session.status == OnlineSessionStatus.ACTIVE &&
                        _uiState.value.sessionStatus != OnlineSessionStatus.ACTIVE
                    ) {
                        val s = _uiState.value
                        gameLaunched = true
                        _uiState.update { it.copy(sessionStatus = OnlineSessionStatus.ACTIVE) }
                        crashlytics.log("online_session_game_started_via_poll: slot=${s.slotIndex}")
                        onGameStart(sessionId, s.slotIndex, s.sessionMode, s.sessionPlayerCount)
                    }
                }
            }
        }
    }

    private fun handleEvent(
        event: SessionEvent,
        sessionId: String,
        onGameStart: (String, Int, String, Int) -> Unit,
    ) {
        when (event) {
            is SessionEvent.ParticipantUpdated -> {
                _uiState.update { state ->
                    val updated = state.participants
                        .filterNot { it.id == event.participant.id }
                        .plus(event.participant)
                        .filter { it.status != ParticipantStatus.LEFT }
                        .sortedBy { it.slotIndex }
                    state.copy(participants = updated)
                }
            }

            is SessionEvent.SessionStatusChanged -> {
                crashlytics.log("online_session_status_changed: ${event.status.name}")
                _uiState.update { it.copy(sessionStatus = event.status) }
                when (event.status) {
                    OnlineSessionStatus.ACTIVE -> {
                        val s = _uiState.value
                        gameLaunched = true
                        crashlytics.log("online_session_game_started: slot_index=${s.slotIndex} mode=${s.sessionMode}")
                        onGameStart(sessionId, s.slotIndex, s.sessionMode, s.sessionPlayerCount)
                    }
                    OnlineSessionStatus.ABANDONED -> {
                        crashlytics.log("online_session_abandoned: joiner_view slot_index=${_uiState.value.slotIndex}")
                    }
                    else -> Unit
                }
            }

            is SessionEvent.Error -> {
                crashlytics.log("online_session_event_error: joiner ${event.message}")
                _uiState.update { it.copy(error = mapBackendError(event.message)) }
            }

            else -> Unit // Other events handled in GameViewModel (Phase 3)
        }
    }

    /**
     * Returns a short, non-PII token identifying the join failure category.
     * Used as a Crashlytics custom key value to segment join errors in dashboards.
     */
    private fun classifyJoinError(message: String?): String = when {
        message == null -> "unknown"
        "Too many failed join attempts" in message -> "rate_limited"
        "Invalid session code format" in message -> "invalid_code_format"
        "Session limit reached" in message -> "session_limit_reached"
        "Session is full" in message -> "session_full"
        "not in LOBBY" in message -> "session_not_found_or_started"
        else -> "unexpected"
    }

    /**
     * Maps raw backend error strings to user-friendly messages in Spanish.
     * Unrecognised errors are replaced with a generic message to avoid leaking
     * internal server details to the user. The raw throwable is recorded to
     * Crashlytics separately by each call site before invoking this function.
     */
    private fun mapBackendError(message: String?): String = when {
        message == null -> appContext.getString(R.string.lobby_error_generic)
        "Too many failed join attempts" in message ->
            appContext.getString(R.string.lobby_error_too_many_attempts)
        "Invalid session code format" in message ->
            appContext.getString(R.string.lobby_error_invalid_code)
        "Session limit reached" in message ->
            appContext.getString(R.string.lobby_error_active_room)
        "Session is full" in message ->
            appContext.getString(R.string.lobby_error_full)
        "not in LOBBY" in message ->
            appContext.getString(R.string.lobby_error_not_found)
        else -> appContext.getString(R.string.lobby_error_generic)
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
                observeSessionUseCase.disconnect(sessionId)
                cleanupScope.cancel()
            }
        } else {
            cleanupScope.cancel()
        }
    }
}
