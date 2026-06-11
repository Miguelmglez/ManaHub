package com.mmg.manahub.feature.online.presentation.lobby

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.data.local.UserPreferencesDataStore
import com.mmg.manahub.feature.auth.domain.model.AuthResult
import com.mmg.manahub.feature.auth.domain.model.SessionState
import com.mmg.manahub.feature.auth.domain.repository.AuthRepository
import com.mmg.manahub.core.online.domain.model.ActiveSession
import com.mmg.manahub.core.online.domain.model.OnlineParticipant
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.online.domain.model.ParticipantStatus
import com.mmg.manahub.core.online.domain.model.SessionEvent
import com.mmg.manahub.core.online.domain.repository.OnlineSessionRepository
import com.mmg.manahub.core.online.domain.usecase.AbandonMyActiveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.CreateSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.GetMyActiveSessionsUseCase
import com.mmg.manahub.core.online.domain.usecase.LeaveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.ObserveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.StartSessionUseCase
import com.mmg.manahub.feature.game.domain.model.GameMode
import androidx.lifecycle.SavedStateHandle
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
 * ViewModel for the host lobby screen.
 *
 * Responsibilities:
 * - Drive session creation via [CreateSessionUseCase].
 * - Subscribe to realtime [SessionEvent]s via [ObserveSessionUseCase].
 * - Expose participant list and readiness state to the UI.
 * - Gate the "Start" action behind [UiState.canStart].
 */
@HiltViewModel
class LobbyHostViewModel @Inject constructor(
    private val createSessionUseCase: CreateSessionUseCase,
    private val startSessionUseCase: StartSessionUseCase,
    private val observeSessionUseCase: ObserveSessionUseCase,
    private val leaveSessionUseCase: LeaveSessionUseCase,
    private val getMyActiveSessionsUseCase: GetMyActiveSessionsUseCase,
    private val abandonMyActiveSessionUseCase: AbandonMyActiveSessionUseCase,
    private val repository: OnlineSessionRepository,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    // ── UI state ──────────────────────────────────────────────────────────────

    /**
     * Full UI state for the host lobby.
     *
     * @property isLoading True while a network call is in flight.
     * @property sessionId Assigned after successful session creation.
     * @property sessionCode 6-character invite code shown to other players.
     * @property participants Live list of all joined participants.
     * @property allReady True when every active participant has toggled ready.
     * @property canStart True when the host may start: all ready, ≥2 players.
     * @property gameMode Currently selected [GameMode].
     * @property playerCount Maximum players for the session (2–6).
     * @property error User-visible error message, cleared after display.
     * @property existingSessions Active sessions owned by the user, shown before the create form.
     */
    data class UiState(
        val isLoading: Boolean = false,
        val sessionId: String? = null,
        val sessionCode: String? = null,
        val participants: List<OnlineParticipant> = emptyList(),
        val allReady: Boolean = false,
        val canStart: Boolean = false,
        val gameMode: GameMode = GameMode.COMMANDER,
        val playerCount: Int = 4,
        val displayName: String = "",
        val selectedThemeKey: String = "Crimson",
        val isHostReady: Boolean = false,
        val error: String? = null,
        val existingSessions: List<ActiveSession> = emptyList(),
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

    init {
        // Pre-fill mode and playerCount if navigated from GameSetupScreen
        savedStateHandle.get<String>("mode")
            ?.let { modeName -> GameMode.entries.firstOrNull { it.name == modeName } }
            ?.let { mode -> _uiState.update { it.copy(gameMode = mode) } }
        savedStateHandle.get<Int>("playerCount")
            ?.takeIf { it in 2..6 }
            ?.let { count -> _uiState.update { it.copy(playerCount = count) } }

        checkForExistingSession()
        viewModelScope.launch {
            userPreferencesDataStore.playerNameFlow
                .first()
                .takeIf { it.isNotBlank() }
                ?.let { name -> _uiState.update { it.copy(displayName = name) } }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    /** Sets the game mode before the session is created. */
    fun setGameMode(mode: GameMode) {
        _uiState.update { it.copy(gameMode = mode) }
    }

    /**
     * Sets the maximum player count (clamped to 2–6).
     * Only effective before the session is created.
     */
    fun setPlayerCount(count: Int) {
        _uiState.update { it.copy(playerCount = count.coerceIn(2, 6)) }
    }

    /** Updates the host's color theme key. Only effective before the session is created. */
    fun onThemeChanged(themeKey: String) {
        _uiState.update { it.copy(selectedThemeKey = themeKey) }
    }

    /**
     * Toggles the host's ready state on the backend.
     * No-op if no session has been created yet.
     */
    fun setReady(ready: Boolean) {
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            crashlytics.log("online_session_host_ready_toggled: ready=$ready")
            _uiState.update { it.copy(isHostReady = ready) }
            repository.setReady(sessionId, ready).onFailure { throwable ->
                crashlytics.log("online_session_host_ready_failed: type=${throwable::class.simpleName}")
                crashlytics.recordException(throwable)
                _uiState.update { it.copy(isHostReady = !ready, error = mapBackendError(throwable.message)) }
            }
        }
    }

    fun checkForExistingSession() {
        viewModelScope.launch {
            getMyActiveSessionsUseCase().onSuccess { sessions ->
                _uiState.update { it.copy(existingSessions = sessions) }
            }
        }
    }

    /**
     * Rejoins an existing session by loading its snapshot, restoring UI state,
     * and reconnecting to the Realtime channel.
     */
    fun resumeSession(session: ActiveSession) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            crashlytics.log("online_session_rejoin_started: session_id_hash=${session.sessionId.take(8)}")
            observeSessionUseCase.getSnapshot(session.sessionId).fold(
                onSuccess = { snapshot ->
                    crashlytics.log("online_session_rejoin_snapshot_loaded: participant_count=${snapshot.participants.size}")
                    val ps = snapshot.participants.filter { it.status != ParticipantStatus.LEFT }
                    val allReady = ps.isNotEmpty() && ps.all { it.isReady }
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            sessionId = session.sessionId,
                            sessionCode = session.code,
                            gameMode = GameMode.entries.firstOrNull { it.name == session.gameMode } ?: state.gameMode,
                            playerCount = session.playerCount,
                            participants = ps,
                            allReady = allReady,
                            canStart = allReady && ps.size >= 2,
                            existingSessions = emptyList(),
                        )
                    }
                    connectAndObserve(session.sessionId)
                    startLobbyPolling(session.sessionId)
                },
                onFailure = { throwable ->
                    crashlytics.log("online_session_rejoin_failed: ${throwable.message}")
                    crashlytics.setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    crashlytics.recordException(throwable)
                    _uiState.update { it.copy(isLoading = false, error = mapBackendError(throwable.message)) }
                },
            )
        }
    }

    /**
     * Abandons a specific session from the active sessions list and removes it from UI state.
     */
    fun abandonSession(sessionId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            crashlytics.log("online_session_abandon_started: session_id_hash=${sessionId.take(8)}")
            abandonMyActiveSessionUseCase(sessionId).fold(
                onSuccess = {
                    crashlytics.log("online_session_abandon_success")
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            existingSessions = state.existingSessions.filterNot { it.sessionId == sessionId },
                        )
                    }
                },
                onFailure = { throwable ->
                    crashlytics.log("online_session_abandon_failed: ${throwable.message}")
                    crashlytics.recordException(throwable)
                    _uiState.update { it.copy(isLoading = false, error = mapBackendError(throwable.message)) }
                },
            )
        }
    }

    /**
     * Creates a new online session on the backend and starts observing it.
     *
     * On success: stores [sessionId] and [sessionCode], connects to Realtime.
     * On failure: maps backend error strings to friendly user-facing messages.
     */
    fun createSession() {
        val state = _uiState.value
        if (state.sessionId != null) return // already created

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

            crashlytics.log("online_session_create_started: mode=${state.gameMode.name} player_count=${state.playerCount}")
            crashlytics.setCustomKey("online_session_game_mode", state.gameMode.name)
            crashlytics.setCustomKey("online_session_player_count", state.playerCount)

            createSessionUseCase(
                mode = state.gameMode.name,
                playerCount = state.playerCount,
                layoutKey = null,
                displayName = state.displayName.ifBlank { appContext.getString(R.string.lobby_host_default_name) },
                themeKey = state.selectedThemeKey,
            ).fold(
                onSuccess = { (sessionId, code) ->
                    crashlytics.log("online_session_create_success: session_id_hash=${sessionId.take(8)}")
                    crashlytics.setCustomKey("online_session_id_hash", sessionId.take(8))
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sessionId = sessionId,
                            sessionCode = code,
                        )
                    }
                    // Immediate snapshot so the host appears in their own participant list.
                    refreshParticipants()
                    // Reliable 3s polling — primary mechanism, works regardless of Realtime.
                    startLobbyPolling(sessionId)
                    // Realtime subscription as optional fast-path.
                    connectAndObserve(sessionId)
                    repository.setReady(sessionId, true).onSuccess {
                        _uiState.update { it.copy(isHostReady = true) }
                    }
                },
                onFailure = { throwable ->
                    crashlytics.log("online_session_create_failed: ${throwable.message}")
                    crashlytics.setCustomKey("online_session_error_type", throwable::class.simpleName ?: "Unknown")
                    crashlytics.recordException(throwable)
                    val isLimitReached = throwable.message?.contains("Session limit reached") == true
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = mapBackendError(throwable.message),
                        )
                    }
                    if (isLimitReached) checkForExistingSession()
                },
            )
        }
    }

    /**
     * Starts the game session.
     * Only callable when [UiState.canStart] is true.
     *
     * @param onGameStart Invoked on the main thread once the backend confirms the session
     *   has transitioned to ACTIVE. Provides the session ID, selected mode, and player count.
     */
    fun startSession(onGameStart: (sessionId: String, mode: GameMode, playerCount: Int) -> Unit) {
        val state = _uiState.value
        val sessionId = state.sessionId ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            crashlytics.log("online_session_start_requested: participant_count=${state.participants.size}")
            crashlytics.setCustomKey("online_session_participant_count", state.participants.size)

            startSessionUseCase(sessionId).fold(
                onSuccess = {
                    gameLaunched = true
                    crashlytics.log("online_session_start_success: mode=${state.gameMode.name}")
                    _uiState.update { it.copy(isLoading = false) }
                    onGameStart(sessionId, state.gameMode, state.playerCount)
                },
                onFailure = { throwable ->
                    crashlytics.log("online_session_start_failed: ${throwable.message}")
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
     * Leaves the session and disconnects from Realtime.
     *
     * @param onNavigateBack Called once the leave operation completes (success or failure).
     */
    fun leaveSession(onNavigateBack: () -> Unit) {
        val sessionId = _uiState.value.sessionId
        viewModelScope.launch {
            if (sessionId != null) {
                crashlytics.log("online_session_host_left: participant_count=${_uiState.value.participants.size}")
                leaveSessionUseCase(sessionId)
            }
            onNavigateBack()
        }
    }

    /** Clears the current error so the UI can dismiss the toast. */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /** Manually fetches the latest participant list and merges it into state. */
    fun refreshParticipants() {
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            observeSessionUseCase.getSnapshot(sessionId).onSuccess { snapshot ->
                _uiState.update { state ->
                    mergeSnapshotParticipants(state, snapshot.participants)
                }
            }.onFailure { throwable ->
                crashlytics.log("online_session_refresh_failed: ${throwable.message}")
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Polls the session snapshot every 3 s and merges participant updates into state.
     * This is the primary reliable mechanism — it runs unconditionally regardless of
     * whether Realtime CDC is configured in the Supabase dashboard.
     */
    private fun startLobbyPolling(sessionId: String) {
        viewModelScope.launch {
            while (_uiState.value.sessionId != null) {
                kotlinx.coroutines.delay(3_000L)
                if (_uiState.value.sessionId == null) break
                observeSessionUseCase.getSnapshot(sessionId).onSuccess { snapshot ->
                    _uiState.update { state ->
                        mergeSnapshotParticipants(state, snapshot.participants)
                    }
                }
            }
        }
    }

    /**
     * Merges snapshot participants into current state.
     * Snapshot wins for participants already known (updates ready-state, theme, etc.).
     * Participants present only in current state (arrived via Realtime after snapshot was taken)
     * are preserved to avoid a poll cycle erasing a fast Realtime update.
     */
    private fun mergeSnapshotParticipants(
        state: UiState,
        snapshotParticipants: List<OnlineParticipant>,
    ): UiState {
        val ps = snapshotParticipants.filter { it.status != ParticipantStatus.LEFT }
        val snapshotIds = ps.map { it.id }.toSet()
        val realtimeOnly = state.participants.filter { it.id !in snapshotIds }
        val merged = (ps + realtimeOnly).sortedBy { it.slotIndex }
        val allReady = merged.isNotEmpty() && merged.all { it.isReady }
        return state.copy(participants = merged, allReady = allReady, canStart = allReady && merged.size >= 2)
    }

    /**
     * Connects to the Realtime channel and starts collecting [SessionEvent]s.
     * This is an optional fast-path on top of [startLobbyPolling] — failure is silent
     * because polling guarantees lobby correctness regardless of Realtime availability.
     */
    private fun connectAndObserve(sessionId: String) {
        viewModelScope.launch {
            crashlytics.log("online_realtime_connect_started: host")
            runCatching { observeSessionUseCase.connect(sessionId) }
                .onFailure { throwable ->
                    if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                    crashlytics.log("online_realtime_connect_failed: host type=${throwable::class.simpleName}")
                    return@launch
                }
            launch {
                observeSessionUseCase(sessionId)
                    .onEach { event -> handleEvent(event) }
                    .catch { throwable ->
                        crashlytics.log("online_realtime_stream_error: host type=${throwable::class.simpleName}")
                    }
                    .collect()
            }
        }
    }

    /** Routes incoming [SessionEvent]s to the appropriate state mutation. */
    private fun handleEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.ParticipantUpdated -> {
                _uiState.update { state ->
                    val updated = state.participants
                        .filterNot { it.id == event.participant.id }
                        .plus(event.participant)
                        .filter { it.status != ParticipantStatus.LEFT }
                        .sortedBy { it.slotIndex }
                    val allReady = updated.isNotEmpty() && updated.all { it.isReady }
                    state.copy(
                        participants = updated,
                        allReady = allReady,
                        canStart = allReady && updated.size >= 2,
                    )
                }
            }

            is SessionEvent.SessionStatusChanged -> {
                crashlytics.log("online_session_status_changed: ${event.status.name}")
                if (event.status == OnlineSessionStatus.ABANDONED) {
                    crashlytics.log("online_session_abandoned: host_view participant_count=${_uiState.value.participants.size}")
                }
                if (event.status == OnlineSessionStatus.FINISHED ||
                    event.status == OnlineSessionStatus.ABANDONED
                ) {
                    _uiState.update { it.copy(participants = emptyList()) }
                }
            }

            is SessionEvent.Error -> {
                crashlytics.log("online_session_event_error: host ${event.message}")
                _uiState.update { it.copy(error = mapBackendError(event.message)) }
            }

            else -> Unit // StateUpdated, PlayerStateUpdated, etc. are handled in Phase 3
        }
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
