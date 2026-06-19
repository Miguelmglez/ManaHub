package com.mmg.manahub.feature.tournament.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.data.local.entity.TournamentEntity
import com.mmg.manahub.core.data.local.entity.TournamentMatchEntity
import com.mmg.manahub.core.data.local.entity.TournamentPlayerEntity
import com.mmg.manahub.core.data.local.entity.projection.TournamentStanding
import com.mmg.manahub.core.domain.repository.MatchResultOutcome
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.presentation.PlayerConfig
import com.mmg.manahub.feature.tournament.domain.usecase.CalculateStandingsUseCase
import com.mmg.manahub.feature.tournament.domain.usecase.RecordMatchResultUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TournamentViewModel @Inject constructor(
    private val repository:             TournamentRepository,
    private val calculateStandings:     CalculateStandingsUseCase,
    private val recordMatchResultUseCase: RecordMatchResultUseCase,
    savedStateHandle:                   SavedStateHandle,
) : ViewModel() {

    private val tournamentId: Long = checkNotNull(
        savedStateHandle.get<Long>("tournamentId")?.takeIf { it > 0L }
    ) { "TournamentViewModel requires a positive tournamentId" }

    data class UiState(
        val tournament:      TournamentEntity?            = null,
        val standings:       List<TournamentStanding>     = emptyList(),
        val matches:         List<TournamentMatchEntity>   = emptyList(),
        val players:         List<TournamentPlayerEntity>  = emptyList(),
        val nextMatch:       TournamentMatchEntity?        = null,
        val activeMatch:     TournamentMatchEntity?        = null,
        val isFinished:      Boolean                      = false,
        val isPaused:        Boolean                      = false,
        val isLoading:       Boolean                      = true,
        /**
         * Single re-entrancy guard covering ALL game-launch entry points — startNextMatch / startMatch
         * / resumeMatch (audit M6). Prevents launching two matches if the user taps a second match
         * before navigation completes. Cleared via [onGameNavigationConsumed] (on the screen returning),
         * NOT synchronously inside the success callback (which let resumeMatch bypass the old guard).
         */
        val isNavigatingToGame: Boolean                   = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadTournament()
    }

    private fun loadTournament() {
        viewModelScope.launch {
            // One-time auto-resume on screen open — MUST NOT be inside the combine transformer.
            // Auto-resume ONLY from "SETUP" (first open after creation). A tournament the user
            // explicitly PAUSED must STAY paused across a screen reopen (audit H4) — resuming a
            // PAUSED tournament is now an explicit user action (resumeTournament()), never a silent
            // side effect of opening the screen.
            val current = repository.observeTournament(tournamentId).first()
            if (current?.status == "SETUP") {
                runCatching { repository.startTournament(tournamentId) }
            }

            combine(
                repository.observeTournament(tournamentId),
                repository.observeMatches(tournamentId),
                repository.observePlayers(tournamentId),
            ) { tournament, matches, players ->
                val standings   = calculateStandings(tournamentId)
                val nextMatch   = matches.firstOrNull { it.status == "PENDING" }
                val activeMatch = matches.firstOrNull { it.status == "ACTIVE" }
                val isFinished  = matches.isNotEmpty() && matches.all { it.status == "FINISHED" }
                val isPaused    = tournament?.status == "PAUSED"
                _uiState.update { state ->
                    state.copy(
                        tournament  = tournament,
                        matches     = matches,
                        players     = players,
                        standings   = standings,
                        nextMatch   = nextMatch,
                        activeMatch = activeMatch,
                        isFinished  = isFinished,
                        isPaused    = isPaused,
                        isLoading   = false,
                    )
                }
            }.distinctUntilChanged().collect {}
        }
    }

    fun pause() {
        viewModelScope.launch {
            runCatching { repository.pauseTournament(tournamentId) }
                .onSuccess { _uiState.update { it.copy(isPaused = true) } }
        }
    }

    /**
     * Explicitly resumes a PAUSED tournament (audit H4). This is the ONLY way a paused tournament
     * returns to ACTIVE — opening the screen no longer auto-resumes a paused tournament.
     */
    fun resumeTournament() {
        viewModelScope.launch {
            runCatching { repository.startTournament(tournamentId) }
                .onSuccess { _uiState.update { it.copy(isPaused = false) } }
        }
    }

    fun startNextMatch(onNavigateToGame: (matchId: Long) -> Unit) {
        if (_uiState.value.isNavigatingToGame) return
        _uiState.update { it.copy(isNavigatingToGame = true) }
        viewModelScope.launch {
            val match = _uiState.value.nextMatch ?: run {
                _uiState.update { it.copy(isNavigatingToGame = false) }
                return@launch
            }
            FirebaseCrashlytics.getInstance().log("tournament_match_started: matchId=${match.id}")
            runCatching { repository.startMatch(match.id) }
                .onSuccess {
                    // Guard stays SET across navigation; cleared by onGameNavigationConsumed() when the
                    // screen returns (audit M6) so a second match can't be launched mid-navigation.
                    onNavigateToGame(match.id)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isNavigatingToGame = false) }
                    FirebaseCrashlytics.getInstance().apply {
                        log("tournament_match_start_failed: matchId=${match.id}")
                        setCustomKey("tournament_id", tournamentId)
                        recordException(e)
                    }
                }
        }
    }

    fun startMatch(matchId: Long, onNavigateToGame: (matchId: Long) -> Unit) {
        if (_uiState.value.isNavigatingToGame) return
        _uiState.update { it.copy(isNavigatingToGame = true) }
        viewModelScope.launch {
            runCatching { repository.startMatch(matchId) }
                .onSuccess { onNavigateToGame(matchId) }
                .onFailure { e ->
                    _uiState.update { it.copy(isNavigatingToGame = false) }
                    FirebaseCrashlytics.getInstance().apply {
                        log("tournament_match_start_failed: matchId=$matchId")
                        setCustomKey("tournament_id", tournamentId)
                        recordException(e)
                    }
                }
        }
    }

    /**
     * Resumes an ACTIVE match — navigates directly to the game screen without calling startMatch again.
     * Now also covered by the single nav guard (audit M6) so it cannot launch a second match in parallel
     * with startNextMatch/startMatch.
     */
    fun resumeMatch(matchId: Long, onNavigateToGame: (matchId: Long) -> Unit) {
        if (_uiState.value.isNavigatingToGame) return
        _uiState.update { it.copy(isNavigatingToGame = true) }
        onNavigateToGame(matchId)
    }

    /**
     * Clears the [UiState.isNavigatingToGame] guard. The screen calls this when it returns from the
     * game (lifecycle resume), NOT synchronously after navigating, so the guard genuinely spans the
     * round-trip and blocks a double launch (audit M6).
     */
    fun onGameNavigationConsumed() {
        if (_uiState.value.isNavigatingToGame) {
            _uiState.update { it.copy(isNavigatingToGame = false) }
        }
    }

    /** Resets an ACTIVE match back to PENDING so it can be replayed or have its result recorded manually. */
    fun resetMatch(matchId: Long) {
        viewModelScope.launch {
            runCatching { repository.resetMatch(matchId) }
                .onFailure { e ->
                    FirebaseCrashlytics.getInstance().apply {
                        log("tournament_reset_match_failed: matchId=$matchId")
                        setCustomKey("tournament_id", tournamentId)
                        recordException(e)
                    }
                }
        }
    }

    /**
     * Returns (tournamentPlayerIds, playerConfigs) for a given match, ready to pass to GameViewModel.
     */
    fun buildPlayerConfigsForMatch(matchId: Long): Pair<List<Long>, List<PlayerConfig>> {
        val state = _uiState.value
        val match = state.matches.find { it.id == matchId } ?: return Pair(emptyList(), emptyList())
        val ids = com.mmg.manahub.feature.tournament.domain.engine.TournamentIdCodec.decodeIds(match.playerIds)
        val configs = ids.mapIndexed { index, playerId ->
            val player = state.players.find { it.id == playerId }
            PlayerConfig(
                id        = index,
                name      = player?.playerName ?: "Wizard ${index + 1}",
                theme     = PlayerTheme.ALL[(player?.seed ?: index) % PlayerTheme.ALL.size],
                isAppUser = index == 0,
            )
        }
        return Pair(ids, configs)
    }

    fun getGameMode(): GameMode {
        // NOTE (audit L4): only COMMANDER maps to its own GameMode; DRAFT (and any other format)
        // intentionally plays under STANDARD life rules — there is no draft-pod life simulation. This
        // is by design, not a missing case.
        return when (_uiState.value.tournament?.format?.uppercase()) {
            "COMMANDER" -> GameMode.COMMANDER
            else        -> GameMode.STANDARD
        }
    }

    fun recordMatchResultManual(matchId: Long, winnerId: Long) {
        recordMatchResult(matchId, winnerId, null, emptyMap())
    }

    fun recordDrawManual(matchId: Long) {
        viewModelScope.launch {
            runCatching {
                val outcome = recordMatchResultUseCase.recordDraw(matchId, null, emptyMap())
                refreshAfterResult(outcome)
            }.onFailure { e ->
                FirebaseCrashlytics.getInstance().apply {
                    log("tournament_draw_save_failed: matchId=$matchId")
                    setCustomKey("tournament_id", tournamentId)
                    recordException(e)
                }
            }
        }
    }

    fun recordMatchResult(
        matchId:    Long,
        winnerId:   Long,
        sessionId:  Long?,
        lifeTotals: Map<Long, Int>,
    ) {
        viewModelScope.launch {
            runCatching {
                val outcome = recordMatchResultUseCase.recordWin(matchId, winnerId, sessionId, lifeTotals)
                refreshAfterResult(outcome)
            }.onFailure { e ->
                FirebaseCrashlytics.getInstance().apply {
                    log("tournament_match_result_save_failed: matchId=$matchId winnerId=$winnerId")
                    setCustomKey("tournament_id", tournamentId)
                    recordException(e)
                }
            }
        }
    }

    /**
     * Recomputes standings for the UI and reflects the finished flag. Round advancement and the
     * tournament-finish (XP-emitting) commit ALREADY happened atomically inside the repository's
     * single write path (audit C1/C2) — this method MUST NOT generate rounds or finish the tournament
     * itself. The observe* DB flows will re-emit the new round / FINISHED status into the combine.
     */
    private suspend fun refreshAfterResult(outcome: MatchResultOutcome) {
        val standings = calculateStandings(tournamentId)
        _uiState.update { state ->
            state.copy(
                standings  = standings,
                isFinished = outcome == MatchResultOutcome.TournamentFinished || state.isFinished,
            )
        }
    }
}
