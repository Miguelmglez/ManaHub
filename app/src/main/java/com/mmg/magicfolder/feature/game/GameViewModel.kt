package com.mmg.magicfolder.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.domain.repository.GameSessionRepository
import com.mmg.magicfolder.core.ui.theme.PlayerTheme
import com.mmg.magicfolder.feature.game.model.*
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
    val mode:             GameMode          = GameMode.COMMANDER,
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
    // UI visibility
    val showPhasePanel:              Boolean = false,
    val editingNameForPlayerId:      Int?    = null,
    val showCmdPanelForPlayerId:     Int?    = null,
    val showCounterPanelForPlayerId: Int?    = null,
    val showLayoutEditor:            Boolean = false,
    // Per-player accumulated life deltas (cleared after 1.5s of inactivity)
    val lifeDeltas:   Map<Int, Int>     = emptyMap(),
)

@HiltViewModel
class GameViewModel @Inject constructor(
    savedStateHandle:            SavedStateHandle,
    private val gameSessionRepo: GameSessionRepository,
) : ViewModel() {

    private val initMode: GameMode = runCatching {
        GameMode.valueOf(savedStateHandle.get<String>("mode") ?: GameMode.COMMANDER.name)
    }.getOrDefault(GameMode.COMMANDER)

    private val initPlayerCount: Int =
        savedStateHandle.get<Int>("playerCount")?.coerceIn(2, 6) ?: 4

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
                            .onSuccess { id -> _uiState.update { it.copy(lastSessionId = id) } }
                    }
                }
        }
    }

    // ── Life ──────────────────────────────────────────────────────────────────

    fun changeLife(playerId: Int, delta: Int) {
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

    fun changePoison(playerId: Int, delta: Int) {
        _uiState.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) p.copy(poison = (p.poison + delta).coerceAtLeast(0)) else p
            })
        }
        checkPendingDefeat()
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

    fun addCustomCounter(playerId: Int, name: String) {
        if (name.isBlank()) return
        _uiState.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id != playerId) p else p.copy(
                    customCounters = p.customCounters + CustomCounter(
                        id    = System.currentTimeMillis(),
                        name  = name.trim(),
                        value = 0,
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
            val newTurn   = if (nextIndex == 0) s.turnNumber + 1 else s.turnNumber
            val newActive = if (nextIndex == 0) nextActivePlayer(s) else s.activePlayerId
            s.copy(currentPhase = nextPhase, turnNumber = newTurn, activePlayerId = newActive)
        }
    }

    fun nextTurn() {
        _uiState.update { s ->
            s.copy(
                activePlayerId = nextActivePlayer(s),
                currentPhase   = GamePhase.UNTAP,
                turnNumber     = s.turnNumber + 1,
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

    fun rotatePlayerClockwise(playerId: Int) {
        val s       = _uiState.value
        val slotPos = s.activeLayout.slots.find { it.playerId == playerId }?.position
        val current = s.playerRotations[playerId] ?: slotPos.toDefaultDegrees()
        setPlayerRotation(playerId, (current + 90) % 360)
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
        val alive = s.players.filter { !it.defeated }
        if (alive.size == 1 && s.players.size > 1) {
            val winner = alive.first()
            val duration = System.currentTimeMillis() - s.gameStartTime
            val result = GameResult(
                winner        = winner,
                allPlayers    = s.players,
                gameMode      = s.mode,
                totalTurns    = s.turnNumber,
                durationMs    = duration,
                playerResults = s.players.map { p ->
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
            _uiState.update { it.copy(winner = winner, gameResult = result) }
        }
    }

    private fun nextActivePlayer(s: GameUiState): Int {
        val alive = s.players.filter { !it.defeated }
        if (alive.isEmpty()) return s.activePlayerId
        val idx = alive.indexOfFirst { it.id == s.activePlayerId }
        return alive[(idx + 1) % alive.size].id
    }

    // ── Setup init from PlayerConfig ──────────────────────────────────────────

    fun initFromConfigs(configs: List<PlayerConfig>, selectedLayout: LayoutTemplate? = null) {
        val mode    = _uiState.value.mode
        val players = configs.mapIndexed { i, config ->
            Player(
                id    = i,
                name  = config.name.ifEmpty { "Player ${i + 1}" },
                life  = mode.startingLife,
                theme = config.theme,
            )
        }
        val layout = selectedLayout ?: LayoutTemplates.getDefaultLayout(players.size)
        _uiState.update { it.copy(players = players, activePlayerId = players.first().id, activeLayout = layout) }
    }

    companion object {
        fun buildInitialState(mode: GameMode, playerCount: Int): GameUiState {
            val clampedCount = playerCount.coerceIn(2, 6)
            val players = (0 until clampedCount).map { i ->
                Player(
                    id    = i,
                    name  = "Player ${i + 1}",
                    life  = mode.startingLife,
                    theme = PlayerTheme.ALL[i % PlayerTheme.ALL.size],
                )
            }
            return GameUiState(
                players        = players,
                mode           = mode,
                activePlayerId = players.first().id,
                activeLayout   = LayoutTemplates.getDefaultLayout(clampedCount),
                gameStartTime  = System.currentTimeMillis(),
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
