package com.mmg.magicfolder.feature.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.domain.repository.GameSessionRepository
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
    val gameStartTime:    Long              = System.currentTimeMillis(),
    // UI visibility
    val showPhasePanel:              Boolean         = false,
    val editingNameForPlayerId:      Int?            = null,
    val showCmdPanelForPlayerId:     Int?            = null,
    val showCounterPanelForPlayerId: Int?            = null,
    // Per-player accumulated life deltas (cleared after 1.5s of inactivity)
    val lifeDeltas:   Map<Int, Int>     = emptyMap(),
    // Dice / coin results
    val diceResults:  Map<Int, Int>     = emptyMap(),  // playerId → last d20 roll
    val coinResults:  Map<Int, Boolean> = emptyMap(),  // playerId → last flip (true=heads)
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
        savedStateHandle.get<Int>("playerCount")?.coerceIn(2, 10) ?: 4

    private val _uiState = MutableStateFlow(buildInitialState(initMode, initPlayerCount))
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

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
            ).checkEliminations()
        }
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
            ).checkEliminations()
        }
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
            ).checkEliminations()
        }
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

    fun eliminatePlayer(playerId: Int) {
        _uiState.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) p.copy(eliminated = true) else p
            }).checkEliminations()
        }
    }

    // ── Dice / coin ───────────────────────────────────────────────────────────

    fun rollDice(playerId: Int) {
        _uiState.update { it.copy(diceResults = it.diceResults + (playerId to (1..20).random())) }
    }

    fun flipCoin(playerId: Int) {
        _uiState.update { it.copy(coinResults = it.coinResults + (playerId to listOf(true, false).random())) }
    }

    // ── UI state toggles ──────────────────────────────────────────────────────

    fun showPhasePanel(show: Boolean)    = _uiState.update { it.copy(showPhasePanel = show) }
    fun showCmdPanel(playerId: Int?)     = _uiState.update { it.copy(showCmdPanelForPlayerId = playerId) }
    fun showCounterPanel(playerId: Int?) = _uiState.update { it.copy(showCounterPanelForPlayerId = playerId) }
    fun showEditName(playerId: Int?)     = _uiState.update { it.copy(editingNameForPlayerId = playerId) }

    fun resetGame() {
        deltaJobs.values.forEach { it.cancel() }
        deltaJobs.clear()
        _uiState.value = buildInitialState(initMode, initPlayerCount)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun scheduleDeltaClear(playerId: Int) {
        deltaJobs[playerId]?.cancel()
        deltaJobs[playerId] = viewModelScope.launch {
            delay(1_500L)
            _uiState.update { it.copy(lifeDeltas = it.lifeDeltas - playerId) }
        }
    }

    private fun GameUiState.checkEliminations(): GameUiState {
        val withEliminations = copy(
            players = players.map { p ->
                if (!p.eliminated && shouldEliminate(p, mode)) p.copy(eliminated = true) else p
            }
        )
        val alive = withEliminations.players.filter { !it.eliminated }
        val newWinner = if (alive.size == 1 && players.size > 1) alive.first()
                        else withEliminations.winner
        val newGameResult = if (newWinner != null && winner == null) {
            val duration = System.currentTimeMillis() - withEliminations.gameStartTime
            GameResult(
                winner        = newWinner,
                allPlayers    = withEliminations.players,
                gameMode      = withEliminations.mode,
                totalTurns    = withEliminations.turnNumber,
                durationMs    = duration,
                playerResults = withEliminations.players.map { p ->
                    PlayerResult(
                        player                       = p,
                        finalLife                    = p.life,
                        finalPoison                  = p.poison,
                        totalCommanderDamageDealt    = p.commanderDamage.values.sum(),
                        totalCommanderDamageReceived = withEliminations.players
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
        } else withEliminations.gameResult
        return withEliminations.copy(winner = newWinner, gameResult = newGameResult)
    }

    private fun nextActivePlayer(s: GameUiState): Int {
        val alive = s.players.filter { !it.eliminated }
        if (alive.isEmpty()) return s.activePlayerId
        val idx = alive.indexOfFirst { it.id == s.activePlayerId }
        return alive[(idx + 1) % alive.size].id
    }

    companion object {
        fun buildInitialState(mode: GameMode, playerCount: Int): GameUiState {
            val players = (0 until playerCount).map { i ->
                Player(id = i, name = "Player ${i + 1}", life = mode.startingLife, themeIndex = i)
            }
            return GameUiState(
                players        = players,
                mode           = mode,
                activePlayerId = players.first().id,
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
