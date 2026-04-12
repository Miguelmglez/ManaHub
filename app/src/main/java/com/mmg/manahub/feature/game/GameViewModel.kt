package com.mmg.manahub.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.PlayerThemeColors
import com.mmg.manahub.feature.game.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// TODO v2: inject val gameRepository: GameRepository
// TODO v2: fun joinOnlineGame(code: String)
// TODO v2: fun syncToFirebase()

data class GameUiState(
    val players:          List<Player>      = emptyList(),
    val mode:             GameMode          = GameMode.STANDARD,
    val activePlayerId:   Int               = 0,
    val currentPhase:     GamePhase         = GamePhase.UNTAP,
    val turnNumber:       Int               = 1,
    val phaseStops:       List<PhaseStop>   = emptyList(),
    val winner:           Player?           = null,
    val gameResult:       GameResult?       = null,
    val lastSessionId:    Long?             = null,
    val gameStartTime:    Long              = System.currentTimeMillis(),
    // Layout
    val activeLayout:     LayoutTemplate    = LayoutTemplates.getDefaultLayout(4),
    val playerRotations:  Map<Int, Int>     = emptyMap(),  // playerId → rotation degrees override
    // Tournament context (null = standalone game)
    val activeTournamentId:      Long?      = null,
    val activeTournamentMatchId: Long?      = null,
    val tournamentPlayerIds:     List<Long> = emptyList(), // index → tournament player DB id
    // UI visibility
    val showPhasePanel:              Boolean = false,
    val editingNameForPlayerId:      Int?    = null,
    val showCmdPanelForPlayerId:     Int?    = null,
    val showCounterPanelForPlayerId: Int?    = null,
    val showLayoutEditor:            Boolean = false,
    // Per-player accumulated life deltas (cleared after 1.5s of inactivity)
    val lifeDeltas:   Map<Int, Int>     = emptyMap(),
    val isGameRunning: Boolean           = false,
    /** Set of playerIds that have played a land in the current turn. Cleared on nextTurn(). */
    val hasPlayedLand: Set<Int>          = emptySet(),
    /**
     * Maps a slot's playerId (from LayoutTemplate.slots) to the actual player id
     * currently displayed in that slot. An empty map means identity (slot 0 → player 0, etc.).
     */
    val gridAssignment: Map<Int, Int>    = emptyMap(),
) {
    val appUserPlayer: Player? get() = players.firstOrNull { it.isAppUser }
    val appUserWon:    Boolean get() = winner?.isAppUser == true
}

@HiltViewModel
class GameViewModel @Inject constructor(
    savedStateHandle:             SavedStateHandle,
    private val gameSessionRepo:  GameSessionRepository,
    private val tournamentRepo:   TournamentRepository,
) : ViewModel() {

    private val initMode: GameMode = runCatching {
        GameMode.valueOf(savedStateHandle.get<String>("mode") ?: GameMode.STANDARD.name)
    }.getOrDefault(GameMode.STANDARD)

    private val initPlayerCount: Int =
        savedStateHandle.get<Int>("playerCount")?.coerceIn(2, 6) ?: 2

    private val _uiState = MutableStateFlow(buildInitialState(initMode, initPlayerCount))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _toolsState = MutableStateFlow(GlobalToolsState())
    val toolsState: StateFlow<GlobalToolsState> = _toolsState.asStateFlow()

    private val deltaJobs = mutableMapOf<Int, Job>()

    init {
        // Persist each unique game result to Room as soon as it appears
        viewModelScope.launch {
            uiState
                .mapNotNull { it.gameResult }
                .distinctUntilChanged()
                .collect { result ->
                    launch(Dispatchers.IO) {
                        runCatching { gameSessionRepo.saveGameSession(result) }
                            .onSuccess { id ->
                                _uiState.update { it.copy(lastSessionId = id, isGameRunning = false) }
                                recordTournamentResultIfNeeded(id, result)
                            }
                    }
                }
        }
    }

    // ── Life ──────────────────────────────────────────────────────────────────

    fun changeLife(playerId: Int, delta: Int) {
        // Ignore life changes once a winner has been determined — the game is over.
        if (_uiState.value.winner != null) return
        _uiState.update { s ->
            s.copy(
                players    = s.players.map { p ->
                    if (p.id == playerId) p.copy(life = p.life + delta) else p
                },
                lifeDeltas = s.lifeDeltas + (playerId to ((s.lifeDeltas[playerId] ?: 0) + delta)),
            )
        }
        checkPendingDefeat()
        scheduleDeltaClear(playerId)
    }



    // ── Counters ──────────────────────────────────────────────────────────────

    fun changeCounter(playerId: Int, type: CounterType, delta: Int) {
        _uiState.update { s ->
            s.copy(
                players = s.players.map { p ->
                    if (p.id != playerId) p else when (type) {
                        CounterType.POISON     -> p.copy(poison     = (p.poison     + delta).coerceAtLeast(0))
                        CounterType.EXPERIENCE -> p.copy(experience = (p.experience + delta).coerceAtLeast(0))
                        CounterType.ENERGY     -> p.copy(energy     = (p.energy     + delta).coerceAtLeast(0))
                    }
                },
            )
        }
        checkPendingDefeat()
    }

    fun addCustomCounter(playerId: Int, name: String, iconKey: String = "") {
        if (name.isBlank()) return
        _uiState.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id != playerId) p else p.copy(
                    customCounters = p.customCounters + CustomCounter(
                        id      = System.currentTimeMillis(),
                        name    = name.trim(),
                        value   = 0,
                        iconKey = iconKey,
                    )
                )
            })
        }
    }

    fun changeCustomCounter(playerId: Int, counterId: Long, delta: Int) {
        _uiState.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id != playerId) p else p.copy(
                    customCounters = p.customCounters.map { c ->
                        if (c.id == counterId) c.copy(value = c.value + delta) else c
                    }
                )
            })
        }
    }

    fun removeCustomCounter(playerId: Int, counterId: Long) {
        _uiState.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id != playerId) p else p.copy(
                    customCounters = p.customCounters.filter { it.id != counterId }
                )
            })
        }
    }

    // ── Commander damage ──────────────────────────────────────────────────────

    fun changeCommanderDamage(targetId: Int, sourceId: Int, delta: Int) {
        _uiState.update { s ->
            s.copy(
                players = s.players.map { p ->
                    if (p.id != targetId) p else {
                        val prev   = p.commanderDamage[sourceId] ?: 0
                        val newDmg = (prev + delta).coerceAtLeast(0)
                        p.copy(commanderDamage = p.commanderDamage + (sourceId to newDmg))
                    }
                },
            )
        }
        checkPendingDefeat()
    }

    // ── Phase tracker ─────────────────────────────────────────────────────────

    fun advancePhase() {
        _uiState.update { s ->
            val phases    = GamePhase.entries
            val nextIndex = (phases.indexOf(s.currentPhase) + 1) % phases.size
            val nextPhase = phases[nextIndex]
            if (nextIndex == 0) {
                // Phase wrapped — player's turn ends, advance to next player
                val nextId      = nextActivePlayer(s)
                val isNewRound  = nextId == s.players.filter { !it.defeated }.firstOrNull()?.id
                s.copy(
                    currentPhase   = nextPhase,
                    turnNumber     = if (isNewRound) s.turnNumber + 1 else s.turnNumber,
                    activePlayerId = nextId,
                )
            } else {
                s.copy(currentPhase = nextPhase)
            }
        }
    }

    fun nextTurn() {
        _uiState.update { s ->
            val nextId     = nextActivePlayer(s)
            val alive      = s.players.filter { !it.defeated }
            // Turn number only increments when a full round completes (back to first alive player)
            val isNewRound = nextId == alive.firstOrNull()?.id
            s.copy(
                activePlayerId = nextId,
                currentPhase   = GamePhase.UNTAP,
                turnNumber     = if (isNewRound) s.turnNumber + 1 else s.turnNumber,
                hasPlayedLand  = emptySet(),
            )
        }
    }

    /** Toggles whether the given player has played a land this turn. */
    fun toggleLandPlayed(playerId: Int) {
        _uiState.update { s ->
            s.copy(
                hasPlayedLand = if (playerId in s.hasPlayedLand)
                    s.hasPlayedLand - playerId
                else
                    s.hasPlayedLand + playerId
            )
        }
    }

    fun setPhaseStop(playerId: Int, phase: GamePhase, forTurnOf: Int) {
        _uiState.update { s ->
            val filtered = s.phaseStops.filter {
                !(it.playerId == playerId && it.phase == phase && it.forTurnOf == forTurnOf)
            }
            s.copy(phaseStops = filtered + PhaseStop(playerId, phase, forTurnOf))
        }
    }

    fun removePhaseStop(playerId: Int, phase: GamePhase) {
        _uiState.update { s ->
            s.copy(phaseStops = s.phaseStops.filter {
                !(it.playerId == playerId && it.phase == phase)
            })
        }
    }

    // ── Player management ─────────────────────────────────────────────────────

    fun renamePlayer(playerId: Int, name: String) {
        if (name.isBlank()) return
        _uiState.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) p.copy(name = name.trim()) else p
            })
        }
    }

    /** Marks players meeting elimination conditions as pendingDefeat (not yet defeated). */
    fun checkPendingDefeat() {
        _uiState.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.defeated || p.pendingDefeat) p
                else if (shouldEliminate(p, s.mode)) p.copy(pendingDefeat = true)
                else p
            })
        }
        // NO automatic winner — only confirmDefeat can trigger that
    }

    /** Player (or host) confirms the defeat. */
    fun confirmDefeat(playerId: Int) {
        _uiState.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) p.copy(defeated = true, pendingDefeat = false) else p
            })
        }
        checkWinner()
    }

    /** Player disputes and continues playing with negative life. */
    fun revokeDefeat(playerId: Int) {
        _uiState.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) p.copy(pendingDefeat = false, defeated = false) else p
            })
        }
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    fun selectLayout(template: LayoutTemplate) {
        _uiState.update { it.copy(activeLayout = template) }
    }

    fun setPlayerRotation(playerId: Int, degrees: Int) {
        _uiState.update { s ->
            s.copy(playerRotations = s.playerRotations + (playerId to degrees % 360))
        }
    }

    /**
     * Updates the color theme for a single player.
     *
     * @param playerId Target player id.
     * @param theme    New [PlayerThemeColors] to apply.
     */
    fun updatePlayerTheme(playerId: Int, theme: PlayerThemeColors) {
        _uiState.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) p.copy(theme = theme) else p
            })
        }
    }

    /**
     * Swaps two grid slot assignments so the players rendered in those slots switch positions.
     *
     * @param slotIdA First slot id (playerId field from LayoutTemplate.slots).
     * @param slotIdB Second slot id.
     */
    fun swapGridSlots(slotIdA: Int, slotIdB: Int) {
        _uiState.update { s ->
            val assign = s.gridAssignment.toMutableMap()
            val playerA = assign.getOrDefault(slotIdA, slotIdA)
            val playerB = assign.getOrDefault(slotIdB, slotIdB)
            assign[slotIdA] = playerB
            assign[slotIdB] = playerA
            s.copy(gridAssignment = assign)
        }
    }

    /**
     * Reorders the players list to match the given ordered player id list,
     * effectively changing the turn order.
     * If no full round has been completed yet (turnNumber == 1), also sets the
     * active player to the first in the new order.
     *
     * @param orderedPlayerIds Player ids in the desired turn order.
     */
    fun reorderTurnOrder(orderedPlayerIds: List<Int>) {
        _uiState.update { s ->
            val playerMap = s.players.associateBy { it.id }
            val reordered = orderedPlayerIds.mapNotNull { playerMap[it] }
            val newActiveId = if (s.turnNumber == 1)
                reordered.firstOrNull()?.id ?: s.activePlayerId
            else
                s.activePlayerId
            s.copy(players = reordered, activePlayerId = newActiveId)
        }
    }

    // ── Global tools (dice / coin) ────────────────────────────────────────────

    fun toggleTools() {
        _toolsState.update { it.copy(isExpanded = !it.isExpanded) }
    }

    fun rollDice() {
        viewModelScope.launch {
            _toolsState.update { it.copy(isRollingDice = true) }
            delay(800L)
            _toolsState.update { it.copy(
                isRollingDice   = false,
                lastDiceResult  = (1..20).random(),
                lastCoinResult  = null,   // clear coin
            )}
        }
    }

    fun flipCoin() {
        viewModelScope.launch {
            _toolsState.update { it.copy(isFlippingCoin = true) }
            delay(1_000L)
            _toolsState.update { it.copy(
                isFlippingCoin  = false,
                lastCoinResult  = (0..1).random() == 1,
                lastDiceResult  = null,   // clear dice
            )}
        }
    }

    // ── Layout editor ─────────────────────────────────────────────────────────

    fun swapPlayerPositions(indexA: Int, indexB: Int) {
        val players = _uiState.value.players.toMutableList()
        if (indexA in players.indices && indexB in players.indices) {
            val temp = players[indexA]
            players[indexA] = players[indexB]
            players[indexB] = temp
            _uiState.update { it.copy(players = players) }
        }
    }

    // ── UI state toggles ──────────────────────────────────────────────────────

    fun showPhasePanel(show: Boolean)    = _uiState.update { it.copy(showPhasePanel = show) }
    fun showCmdPanel(playerId: Int?)     = _uiState.update { it.copy(showCmdPanelForPlayerId = playerId) }
    fun showCounterPanel(playerId: Int?) = _uiState.update { it.copy(showCounterPanelForPlayerId = playerId) }
    fun showEditName(playerId: Int?)     = _uiState.update { it.copy(editingNameForPlayerId = playerId) }
    fun showLayoutEditor(show: Boolean)  = _uiState.update { it.copy(showLayoutEditor = show) }

    fun resetGame() {
        deltaJobs.values.forEach { it.cancel() }
        deltaJobs.clear()
        _uiState.value = buildInitialState(initMode, initPlayerCount)
        _toolsState.value = GlobalToolsState()
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun scheduleDeltaClear(playerId: Int) {
        deltaJobs[playerId]?.cancel()
        deltaJobs[playerId] = viewModelScope.launch {
            delay(1_500L)
            _uiState.update { it.copy(lifeDeltas = it.lifeDeltas - playerId) }
        }
    }

    private fun checkWinner() {
        val s     = _uiState.value
        if (s.winner != null) return   // already resolved, don't re-fire
        val alive = s.players.filter { !it.defeated }

        // Normal case: exactly one player standing.
        // Edge case: all remaining players eliminated simultaneously (e.g. both players
        // die from infect in the same trigger). Declare the last non-defeated player
        // before this batch of confirmations as winner. If truly everyone is dead,
        // pick the player with the highest life total as a tiebreak so the game
        // always resolves and its result is persisted.
        val winner: Player = when {
            alive.size == 1 && s.players.size > 1 -> alive.first()
            alive.isEmpty() && s.players.size > 1 ->
                s.players.maxByOrNull { it.life } ?: return  // tiebreak: highest life; can't be null (list non-empty)
            else -> return
        }

        val duration = System.currentTimeMillis() - s.gameStartTime
        val appUser = s.players.firstOrNull { it.isAppUser }
        val result = GameResult(
            winner           = winner,
            allPlayers       = s.players,
            gameMode         = s.mode,
            totalTurns       = s.turnNumber,
            durationMs       = duration,
            appUserWon       = winner.isAppUser,
            appUserFinalLife = appUser?.life ?: 0,
            appUserName      = appUser?.name ?: "",
            playerResults    = s.players.map { p ->
                PlayerResult(
                    player                       = p,
                    finalLife                    = p.life,
                    finalPoison                  = p.poison,
                    totalCommanderDamageDealt    = p.commanderDamage.values.sum(),
                    totalCommanderDamageReceived = s.players
                        .filter { it.id != p.id }
                        .sumOf { it.commanderDamage[p.id] ?: 0 },
                    eliminationReason = when {
                        p.life <= 0    -> EliminationReason.LIFE
                        p.poison >= 10 -> EliminationReason.POISON
                        p.commanderDamage.values.any { it >= 21 }
                                       -> EliminationReason.COMMANDER_DAMAGE
                        else           -> null
                    }
                )
            }
        )
        _uiState.update { it.copy(winner = winner, gameResult = result, isGameRunning = false) }
    }

    private fun nextActivePlayer(s: GameUiState): Int {
        val alive = s.players.filter { !it.defeated }
        if (alive.isEmpty()) return s.activePlayerId
        val idx = alive.indexOfFirst { it.id == s.activePlayerId }
        return alive[(idx + 1) % alive.size].id
    }

    // ── Tournament ────────────────────────────────────────────────────────────

    /**
     * Initialise a fresh game for a tournament match with pre-built player configs.
     * Stores tournament context so result is auto-recorded when the game ends.
     */
    fun initFromTournamentMatch(
        matchId:              Long,
        tournamentId:         Long,
        tournamentPlayerIds:  List<Long>,
        configs:              List<PlayerConfig>,
        mode:                 GameMode,
        layout:               LayoutTemplate? = null,
    ) {
        deltaJobs.values.forEach { it.cancel() }
        deltaJobs.clear()
        val players = configs.mapIndexed { i, cfg ->
            Player(
                id        = i,
                name      = cfg.name.ifEmpty { "Player ${i + 1}" },
                life      = mode.startingLife,
                theme     = cfg.theme,
                isAppUser = cfg.isAppUser,
            )
        }
        val actualLayout = layout ?: LayoutTemplates.getDefaultLayout(players.size)
        _uiState.value = GameUiState(
            players              = players,
            mode                 = mode,
            activePlayerId       = players.first().id,
            activeLayout         = actualLayout,
            gameStartTime        = System.currentTimeMillis(),
            activeTournamentId   = tournamentId,
            activeTournamentMatchId = matchId,
            tournamentPlayerIds  = tournamentPlayerIds,
            isGameRunning        = true
        )
        _toolsState.value = GlobalToolsState()
    }

    private fun recordTournamentResultIfNeeded(sessionId: Long, result: GameResult) {
        val s = _uiState.value
        val matchId = s.activeTournamentMatchId ?: return
        val tournamentId = s.activeTournamentId ?: return

        // Guard: if tournament player id list does not cover every game player,
        // recording the result would silently map some players to wrong tournament
        // entries or drop them from life totals — abort instead of persisting garbage.
        if (s.tournamentPlayerIds.size != s.players.size) {
            android.util.Log.e(
                "GameViewModel",
                "recordTournamentResultIfNeeded: tournamentPlayerIds.size (${s.tournamentPlayerIds.size}) " +
                "!= players.size (${s.players.size}) — match result not recorded for match $matchId"
            )
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val winnerIndex = s.players.indexOfFirst { it.id == result.winner.id }
            val winnerTournamentId = s.tournamentPlayerIds.getOrNull(winnerIndex) ?: return@launch
            val lifeTotals = s.players.mapIndexedNotNull { i, p ->
                val tid = s.tournamentPlayerIds.getOrNull(i) ?: return@mapIndexedNotNull null
                tid to p.life
            }.toMap()
            runCatching {
                tournamentRepo.finishMatch(matchId, winnerTournamentId, sessionId, lifeTotals)
            }
        }
    }

    // ── Setup init from PlayerConfig ──────────────────────────────────────────

    fun initFromConfigs(configs: List<PlayerConfig>, selectedLayout: LayoutTemplate? = null) {
        val mode    = _uiState.value.mode
        val players = configs.mapIndexed { i, config ->
            Player(
                id        = i,
                name      = config.name.ifEmpty { "Player ${i + 1}" },
                life      = mode.startingLife,
                theme     = config.theme,
                isAppUser = config.isAppUser,
            )
        }
        val layout = selectedLayout ?: LayoutTemplates.getDefaultLayout(players.size)
        _uiState.update { it.copy(
            players = players,
            activePlayerId = players.first().id,
            activeLayout = layout,
            isGameRunning = true
        ) }
    }

    companion object {
        fun buildInitialState(mode: GameMode, playerCount: Int): GameUiState {
            val clampedCount = playerCount.coerceIn(2, 6)
            val players = (0 until clampedCount).map { i ->
                Player(
                    id        = i,
                    name      = "Player ${i + 1}",
                    life      = mode.startingLife,
                    theme     = PlayerTheme.ALL[i % PlayerTheme.ALL.size],
                    isAppUser = i == 0,
                )
            }
            return GameUiState(
                players        = players,
                mode           = mode,
                activePlayerId = players.first().id,
                activeLayout   = LayoutTemplates.getDefaultLayout(clampedCount),
                gameStartTime  = System.currentTimeMillis(),
                isGameRunning = false
            )
        }

        fun shouldEliminate(p: Player, mode: GameMode): Boolean {
            if (p.life <= 0) return true
            if (p.poison >= 10) return true
            if (mode == GameMode.COMMANDER && p.commanderDamage.values.any { it >= 21 }) return true
            return false
        }
    }
}
