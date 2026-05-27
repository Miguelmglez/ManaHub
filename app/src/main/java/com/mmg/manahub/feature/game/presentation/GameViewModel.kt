package com.mmg.manahub.feature.game.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.core.domain.repository.GameSessionRepository
import com.mmg.manahub.core.domain.repository.TournamentRepository
import com.mmg.manahub.core.util.AnalyticsHelper
import com.mmg.manahub.core.ui.theme.PlayerTheme
import com.mmg.manahub.core.ui.theme.PlayerThemeColors
import com.mmg.manahub.feature.game.domain.model.CounterType
import com.mmg.manahub.feature.game.domain.model.CustomCounter
import com.mmg.manahub.feature.game.domain.model.EliminationReason
import com.mmg.manahub.feature.game.domain.model.GameMode
import com.mmg.manahub.feature.game.domain.model.GamePhase
import com.mmg.manahub.feature.game.domain.model.GameResult
import com.mmg.manahub.feature.game.domain.model.LayoutTemplate
import com.mmg.manahub.feature.game.domain.model.LayoutTemplates
import com.mmg.manahub.feature.game.domain.model.PhaseStop
import com.mmg.manahub.feature.game.domain.model.Player
import com.mmg.manahub.feature.game.domain.model.PlayerResult
import dagger.hilt.android.lifecycle.HiltViewModel
import com.mmg.manahub.core.nearby.domain.model.NearbyConnectionEvent
import com.mmg.manahub.core.nearby.domain.model.NearbyGameMessage
import com.mmg.manahub.core.nearby.domain.repository.NearbySessionRepository
import com.mmg.manahub.core.online.domain.model.OnlineSessionStatus
import com.mmg.manahub.core.online.domain.model.SessionEvent
import com.mmg.manahub.core.online.domain.usecase.AdvancePhaseUseCase
import com.mmg.manahub.core.online.domain.usecase.ConfirmDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.LeaveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.NextTurnUseCase
import com.mmg.manahub.core.online.domain.usecase.ObserveSessionUseCase
import com.mmg.manahub.core.online.domain.usecase.RevokeDefeatUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateCommanderDamageUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateCounterUseCase
import com.mmg.manahub.core.online.domain.usecase.UpdateLifeUseCase
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val isOnlineSession: Boolean         = false,
    val isOnlineSessionAbandoned: Boolean = false,
    /** Set of playerIds that have played a land in the current turn. Cleared on nextTurn(). */
    val hasPlayedLand: Set<Int>          = emptySet(),
    /**
     * Maps a slot's playerId (from LayoutTemplate.slots) to the actual player id
     * currently displayed in that slot. An empty map means identity (slot 0 → player 0, etc.).
     */
    val gridAssignment: Map<Int, Int>    = emptyMap(),
    val gameSettings:   GameSettings     = GameSettings(),
) {
    val appUserPlayer: Player? get() = players.firstOrNull { it.isAppUser }
    val appUserWon:    Boolean get() = winner?.isAppUser == true
}

@HiltViewModel
class GameViewModel @Inject constructor(
    savedStateHandle:                  SavedStateHandle,
    private val gameSessionRepo:       GameSessionRepository,
    private val tournamentRepo:        TournamentRepository,
    private val analyticsHelper:       AnalyticsHelper,
    private val observeSessionUseCase:        ObserveSessionUseCase,
    private val updateLifeUseCase:            UpdateLifeUseCase,
    private val advancePhaseUseCase:          AdvancePhaseUseCase,
    private val nextTurnUseCase:              NextTurnUseCase,
    private val updateCounterUseCase:         UpdateCounterUseCase,
    private val updateCommanderDamageUseCase: UpdateCommanderDamageUseCase,
    private val confirmDefeatUseCase:         ConfirmDefeatUseCase,
    private val revokeDefeatUseCase:          RevokeDefeatUseCase,
    private val leaveSessionUseCase:          LeaveSessionUseCase,
    private val nearbyRepo:                   NearbySessionRepository,
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

    private var onlineSessionId: String? = null
    private var mySlotIndex: Int = -1
    private var isNearbySession: Boolean = false
    private var isNearbyHost: Boolean = false
    private var onlineObserveJob: Job? = null
    private var nearbyObserveJob: Job? = null
    private val persistLifeJobs  = mutableMapOf<Int, Job>()
    private var persistPhaseJob: Job? = null
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
                            .onFailure { e ->
                                FirebaseCrashlytics.getInstance().apply {
                                    log("game_session_save_failed: mode=${result.gameMode.name} turns=${result.totalTurns}")
                                    setCustomKey("game_mode", result.gameMode.name)
                                    setCustomKey("game_turn_count", result.totalTurns)
                                    recordException(e)
                                }
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

        val sessionId = onlineSessionId
        if (isNearbySession && playerId == mySlotIndex) {
            val newLife = _uiState.value.players.find { it.id == playerId }?.life ?: return
            nearbyRepo.sendMessage(NearbyGameMessage.LifeChanged(slot = playerId, life = newLife))
        } else if (sessionId != null && playerId == mySlotIndex) {
            val newLife = _uiState.value.players.find { it.id == playerId }?.life ?: return
            viewModelScope.launch { updateLifeUseCase.broadcast(sessionId, playerId, newLife) }
            schedulePersistLife(sessionId, playerId, newLife)
        }
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
        val sessionId = onlineSessionId
        val player = _uiState.value.players.find { it.id == playerId } ?: return
        if (isNearbySession && playerId == mySlotIndex) {
            when (type) {
                CounterType.POISON     -> nearbyRepo.sendMessage(NearbyGameMessage.PoisonChanged(playerId, player.poison))
                CounterType.EXPERIENCE -> nearbyRepo.sendMessage(NearbyGameMessage.ExperienceChanged(playerId, player.experience))
                CounterType.ENERGY     -> nearbyRepo.sendMessage(NearbyGameMessage.EnergyChanged(playerId, player.energy))
            }
        } else if (sessionId != null && playerId == mySlotIndex) {
            val newValue = when (type) {
                CounterType.POISON     -> player.poison
                CounterType.EXPERIENCE -> player.experience
                CounterType.ENERGY     -> player.energy
            }
            viewModelScope.launch {
                updateCounterUseCase.broadcast(sessionId, playerId, type.name, newValue)
                runCatching { updateCounterUseCase(sessionId, playerId, type.name, delta) }
            }
        }
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
        val sessionId = onlineSessionId
        if (sessionId != null && sourceId == mySlotIndex) {
            val newDmg = _uiState.value.players.find { it.id == targetId }
                ?.commanderDamage?.get(sourceId) ?: 0
            viewModelScope.launch {
                updateCommanderDamageUseCase.broadcast(sessionId, targetId, sourceId, newDmg)
                runCatching { updateCommanderDamageUseCase(sessionId, targetId, sourceId, delta) }
            }
        }
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
        val s = _uiState.value
        val sessionId = onlineSessionId
        if (isNearbySession) {
            nearbyRepo.sendMessage(NearbyGameMessage.PhaseChanged(s.currentPhase.name))
            if (s.currentPhase == GamePhase.UNTAP) {
                nearbyRepo.sendMessage(NearbyGameMessage.TurnChanged(s.turnNumber, s.activePlayerId))
            }
        } else if (sessionId != null) {
            viewModelScope.launch {
                advancePhaseUseCase.broadcast(sessionId, s.currentPhase.name, s.activePlayerId, s.turnNumber)
            }
            schedulePhasePersist(sessionId)
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
        val s = _uiState.value
        val sessionId = onlineSessionId
        if (isNearbySession) {
            nearbyRepo.sendMessage(NearbyGameMessage.TurnChanged(s.turnNumber, s.activePlayerId))
            nearbyRepo.sendMessage(NearbyGameMessage.PhaseChanged(s.currentPhase.name))
        } else if (sessionId != null) {
            viewModelScope.launch {
                advancePhaseUseCase.broadcast(sessionId, s.currentPhase.name, s.activePlayerId, s.turnNumber)
                runCatching { nextTurnUseCase(sessionId) }
            }
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
                val meetsCondition = shouldEliminate(p, s.mode)
                when {
                    p.defeated -> p
                    // If already pending, clear it if they are no longer meeting the condition (e.g. gained life)
                    p.pendingDefeat -> if (!meetsCondition) p.copy(pendingDefeat = false) else p
                    // If surviving, clear the flag if they are healthy again
                    p.isSurviving -> if (!meetsCondition) p.copy(isSurviving = false) else p
                    // If healthy, mark as pending if they hit a defeat condition
                    meetsCondition -> p.copy(pendingDefeat = true)
                    else -> p
                }
            })
        }
        // NO automatic winner — only confirmDefeat can trigger that
    }

    /** Player (or host) confirms the defeat. */
    fun confirmDefeat(playerId: Int) {
        _uiState.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) p.copy(defeated = true, pendingDefeat = false, isSurviving = false) else p
            })
        }
        checkWinner()
        val sessionId = onlineSessionId
        if (isNearbySession && playerId == mySlotIndex) {
            nearbyRepo.sendMessage(NearbyGameMessage.DefeatConfirmed(playerId))
        } else if (sessionId != null && playerId == mySlotIndex) {
            viewModelScope.launch { runCatching { confirmDefeatUseCase(sessionId, playerId) } }
        }
    }

    /** Player disputes and continues playing with negative life. */
    fun revokeDefeat(playerId: Int) {
        _uiState.update { s ->
            s.copy(players = s.players.map { p ->
                if (p.id == playerId) p.copy(pendingDefeat = false, isSurviving = true, defeated = false) else p
            })
        }
        val sessionId = onlineSessionId
        if (isNearbySession && playerId == mySlotIndex) {
            nearbyRepo.sendMessage(NearbyGameMessage.DefeatRevoked(playerId))
        } else if (sessionId != null && playerId == mySlotIndex) {
            viewModelScope.launch { runCatching { revokeDefeatUseCase(sessionId, playerId) } }
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
        _uiState.update { s ->
            val resetPlayers = s.players.map { p ->
                p.copy(
                    life            = s.mode.startingLife,
                    poison          = 0,
                    experience      = 0,
                    energy          = 0,
                    commanderDamage = emptyMap(),
                    customCounters  = emptyList(),
                    pendingDefeat   = false,
                    defeated        = false
                )
            }
            s.copy(
                players        = resetPlayers,
                activePlayerId = resetPlayers.firstOrNull()?.id ?: 0,
                currentPhase   = GamePhase.UNTAP,
                turnNumber     = 1,
                winner         = null,
                gameResult     = null,
                gameStartTime  = System.currentTimeMillis(),
                isGameRunning  = true,
                hasPlayedLand  = emptySet(),
                lifeDeltas     = emptyMap(),
                phaseStops     = emptyList()
            )
        }
        _toolsState.value = GlobalToolsState()
    }

    fun finishGame() {
        deltaJobs.values.forEach { it.cancel() }
        deltaJobs.clear()
        _uiState.update { it.copy(
            isGameRunning = false,
            winner        = null,
            gameResult    = null
        ) }
        _toolsState.value = GlobalToolsState()
    }

    // ── Online session ────────────────────────────────────────────────────────

    fun initFromOnlineSession(
        sessionId:   String,
        mySlotIndex: Int,
        configs:     List<PlayerConfig>,
        mode:        GameMode,
        layout:      LayoutTemplate? = null,
    ) {
        deltaJobs.values.forEach { it.cancel() }
        deltaJobs.clear()
        persistLifeJobs.values.forEach { it.cancel() }
        persistLifeJobs.clear()
        persistPhaseJob?.cancel()
        onlineObserveJob?.cancel()

        this.onlineSessionId = sessionId
        this.mySlotIndex     = mySlotIndex

        val players = configs.mapIndexed { i, cfg ->
            Player(
                id        = i,
                name      = cfg.name.ifEmpty { "Wizard ${i + 1}" },
                life      = mode.startingLife,
                theme     = cfg.theme,
                isAppUser = i == mySlotIndex,
            )
        }
        val actualLayout = layout ?: LayoutTemplates.getDefaultLayout(players.size)
        _uiState.value = GameUiState(
            players         = players,
            mode            = mode,
            activePlayerId  = players.first().id,
            activeLayout    = actualLayout,
            gameStartTime   = System.currentTimeMillis(),
            isGameRunning   = true,
            isOnlineSession = true,
        )
        _toolsState.value = GlobalToolsState()

        FirebaseCrashlytics.getInstance().apply {
            log("game_started: mode=${mode.name} players=${players.size} online=true sessionId=$sessionId")
            setCustomKey("game_mode", mode.name)
            setCustomKey("game_player_count", players.size)
            setCustomKey("online_session_id", sessionId)
        }
        connectAndObserveOnlineSession(sessionId)
    }

    private fun connectAndObserveOnlineSession(sessionId: String) {
        onlineObserveJob = viewModelScope.launch {
            observeSessionUseCase.getSnapshot(sessionId).onSuccess { snapshot ->
                val participantsBySlot = snapshot.participants.associateBy { it.slotIndex }
                val playerStatesBySlot = snapshot.playerStates.associateBy { it.slotIndex }
                _uiState.update { s ->
                    s.copy(
                        players = s.players.map { p ->
                            val participant = participantsBySlot[p.id]
                            val ps = playerStatesBySlot[p.id]
                            p.copy(
                                name = participant?.displayName?.takeIf { it.isNotBlank() } ?: p.name,
                                theme = participant?.themeKey
                                    ?.let { key -> PlayerTheme.ALL.firstOrNull { it.name == key } }
                                    ?: p.theme,
                                life       = ps?.life       ?: p.life,
                                poison     = ps?.poison     ?: p.poison,
                                experience = ps?.experience ?: p.experience,
                                energy     = ps?.energy     ?: p.energy,
                                defeated   = ps?.defeated   ?: p.defeated,
                            )
                        },
                        currentPhase   = runCatching { GamePhase.valueOf(snapshot.sessionState.currentPhase) }.getOrDefault(GamePhase.UNTAP),
                        activePlayerId = snapshot.sessionState.activePlayerSlot,
                        turnNumber     = snapshot.sessionState.turnNumber,
                    )
                }
            }
            runCatching { observeSessionUseCase.connect(sessionId) }
                .onFailure { throwable ->
                    if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                    FirebaseCrashlytics.getInstance().apply {
                        log("online_game_realtime_connect_failed: ${throwable::class.simpleName}")
                        recordException(throwable)
                    }
                    _uiState.update { it.copy(isOnlineSessionAbandoned = true, isGameRunning = false) }
                    return@launch
                }
            observeSessionUseCase(sessionId).collect { event -> handleOnlineEvent(event) }
        }
    }

    private fun handleOnlineEvent(event: SessionEvent) {
        when (event) {
            is SessionEvent.LifeDeltaReceived -> {
                if (event.slotIndex != mySlotIndex) {
                    _uiState.update { s ->
                        s.copy(players = s.players.map { p ->
                            if (p.id == event.slotIndex) p.copy(life = event.newLife) else p
                        })
                    }
                    checkPendingDefeat()
                }
            }
            is SessionEvent.PhaseChangedReceived -> {
                _uiState.update { s ->
                    val phase = runCatching { GamePhase.valueOf(event.newPhase) }.getOrDefault(s.currentPhase)
                    s.copy(
                        currentPhase   = phase,
                        activePlayerId = event.activePlayerSlot,
                        turnNumber     = event.turnNumber,
                    )
                }
            }
            is SessionEvent.CounterUpdatedReceived -> {
                if (event.slotIndex != mySlotIndex) {
                    _uiState.update { s ->
                        s.copy(players = s.players.map { p ->
                            if (p.id != event.slotIndex) p else when (event.counterType) {
                                CounterType.POISON.name     -> p.copy(poison     = event.newValue)
                                CounterType.EXPERIENCE.name -> p.copy(experience = event.newValue)
                                CounterType.ENERGY.name     -> p.copy(energy     = event.newValue)
                                else -> p
                            }
                        })
                    }
                    checkPendingDefeat()
                }
            }
            is SessionEvent.CommanderDamageReceived -> {
                if (event.sourceSlot != mySlotIndex) {
                    _uiState.update { s ->
                        s.copy(players = s.players.map { p ->
                            if (p.id != event.targetSlot) p
                            else p.copy(commanderDamage = p.commanderDamage + (event.sourceSlot to event.newDamage))
                        })
                    }
                    checkPendingDefeat()
                }
            }
            is SessionEvent.StateUpdated -> {
                _uiState.update { s ->
                    s.copy(
                        currentPhase   = runCatching { GamePhase.valueOf(event.state.currentPhase) }.getOrDefault(s.currentPhase),
                        activePlayerId = event.state.activePlayerSlot,
                        turnNumber     = event.state.turnNumber,
                    )
                }
            }
            is SessionEvent.PlayerStateUpdated -> {
                val ps = event.playerState
                if (ps.slotIndex != mySlotIndex) {
                    _uiState.update { s ->
                        s.copy(players = s.players.map { p ->
                            if (p.id == ps.slotIndex) p.copy(
                                life       = ps.life,
                                poison     = ps.poison,
                                experience = ps.experience,
                                energy     = ps.energy,
                                defeated   = ps.defeated,
                            ) else p
                        })
                    }
                    checkPendingDefeat()
                }
            }
            is SessionEvent.SessionStatusChanged -> {
                if (event.status == OnlineSessionStatus.ABANDONED ||
                    event.status == OnlineSessionStatus.FINISHED) {
                    _uiState.update { it.copy(isOnlineSessionAbandoned = true, isGameRunning = false) }
                }
            }
            is SessionEvent.ParticipantUpdated -> { /* presence handled in lobby */ }
            is SessionEvent.Error -> {
                FirebaseCrashlytics.getInstance().apply {
                    log("online_session_event_error: ${event.message}")
                    setCustomKey("online_session_id", onlineSessionId ?: "")
                }
            }
        }
    }

    fun initFromNearbySession(
        sessionId:   String,
        isHost:      Boolean,
        slotIndex:   Int,
        configs:     List<PlayerConfig>,
        mode:        GameMode,
        layout:      LayoutTemplate? = null,
    ) {
        deltaJobs.values.forEach { it.cancel() }
        deltaJobs.clear()
        persistLifeJobs.values.forEach { it.cancel() }
        persistLifeJobs.clear()
        persistPhaseJob?.cancel()
        onlineObserveJob?.cancel()
        nearbyObserveJob?.cancel()

        this.onlineSessionId = sessionId
        this.mySlotIndex     = slotIndex
        this.isNearbySession = true
        this.isNearbyHost    = isHost

        val players = configs.mapIndexed { i, cfg ->
            Player(
                id        = i,
                name      = cfg.name.ifEmpty { "Wizard ${i + 1}" },
                life      = mode.startingLife,
                theme     = cfg.theme,
                isAppUser = i == slotIndex,
            )
        }
        val actualLayout = layout ?: LayoutTemplates.getDefaultLayout(players.size)
        _uiState.value = GameUiState(
            players         = players,
            mode            = mode,
            activePlayerId  = players.first().id,
            activeLayout    = actualLayout,
            gameStartTime   = System.currentTimeMillis(),
            isGameRunning   = true,
            isOnlineSession = true, // Reuse online flag for UI elements that assume networked game
        )
        _toolsState.value = GlobalToolsState()

        FirebaseCrashlytics.getInstance().apply {
            log("game_started: mode=${mode.name} players=${players.size} nearby=true isHost=$isHost sessionId=$sessionId")
            setCustomKey("game_mode", mode.name)
            setCustomKey("game_player_count", players.size)
            setCustomKey("nearby_session_id", sessionId)
        }

        nearbyRepo.fullStateSyncProvider = {
            val s = _uiState.value
            NearbyGameMessage.FullStateSync(
                players = s.players.map { p ->
                    NearbyGameMessage.PlayerSnapshot(
                        slot = p.id,
                        life = p.life,
                        poison = p.poison,
                        experience = p.experience,
                        energy = p.energy,
                        defeated = p.defeated,
                        commanderDamage = p.commanderDamage,
                    )
                },
                phase = s.currentPhase.name,
                turnNumber = s.turnNumber,
                activeSlot = s.activePlayerId,
            )
        }

        nearbyObserveJob = viewModelScope.launch {
            launch {
                nearbyRepo.observeMessages().collect { msg ->
                    handleNearbyMessage(msg)
                }
            }
            launch {
                nearbyRepo.observeConnectionEvents().collect { event ->
                    handleNearbyConnectionEvent(event)
                }
            }
        }
    }

    private fun handleNearbyMessage(message: NearbyGameMessage) {
        when (message) {
            is NearbyGameMessage.LifeChanged -> {
                if (message.slot != mySlotIndex) {
                    _uiState.update { s ->
                        s.copy(players = s.players.map { p ->
                            if (p.id == message.slot) p.copy(life = message.life) else p
                        })
                    }
                    checkPendingDefeat()
                }
            }
            is NearbyGameMessage.PoisonChanged -> {
                if (message.slot != mySlotIndex) {
                    _uiState.update { s ->
                        s.copy(players = s.players.map { p ->
                            if (p.id == message.slot) p.copy(poison = message.poison) else p
                        })
                    }
                    checkPendingDefeat()
                }
            }
            is NearbyGameMessage.ExperienceChanged -> {
                if (message.slot != mySlotIndex) {
                    _uiState.update { s ->
                        s.copy(players = s.players.map { p ->
                            if (p.id == message.slot) p.copy(experience = message.experience) else p
                        })
                    }
                }
            }
            is NearbyGameMessage.EnergyChanged -> {
                if (message.slot != mySlotIndex) {
                    _uiState.update { s ->
                        s.copy(players = s.players.map { p ->
                            if (p.id == message.slot) p.copy(energy = message.energy) else p
                        })
                    }
                }
            }
            is NearbyGameMessage.CommanderDamageChanged -> {
                if (message.toSlot != mySlotIndex) {
                    _uiState.update { s ->
                        s.copy(players = s.players.map { p ->
                            if (p.id == message.toSlot) p.copy(
                                commanderDamage = p.commanderDamage + (message.fromSlot to message.damage)
                            ) else p
                        })
                    }
                    checkPendingDefeat()
                }
            }
            is NearbyGameMessage.PhaseChanged -> {
                _uiState.update { s ->
                    val phase = runCatching { GamePhase.valueOf(message.phase) }.getOrDefault(s.currentPhase)
                    s.copy(currentPhase = phase)
                }
            }
            is NearbyGameMessage.TurnChanged -> {
                _uiState.update { s ->
                    s.copy(
                        turnNumber = message.turnNumber,
                        activePlayerId = message.activeSlot,
                    )
                }
            }
            is NearbyGameMessage.DefeatConfirmed -> {
                if (message.slot != mySlotIndex) {
                    _uiState.update { s ->
                        s.copy(players = s.players.map { p ->
                            if (p.id == message.slot) p.copy(defeated = true, pendingDefeat = false, isSurviving = false) else p
                        })
                    }
                    checkWinner()
                }
            }
            is NearbyGameMessage.DefeatRevoked -> {
                if (message.slot != mySlotIndex) {
                    _uiState.update { s ->
                        s.copy(players = s.players.map { p ->
                            if (p.id == message.slot) p.copy(pendingDefeat = false, isSurviving = true, defeated = false) else p
                        })
                    }
                }
            }
            is NearbyGameMessage.GameFinished -> {
                // Resolved locally via checkWinner, but just in case:
            }
            is NearbyGameMessage.FullStateSync -> {
                if (!isNearbyHost) {
                    _uiState.update { s ->
                        s.copy(
                            currentPhase = runCatching { GamePhase.valueOf(message.phase) }.getOrDefault(s.currentPhase),
                            turnNumber = message.turnNumber,
                            activePlayerId = message.activeSlot,
                            players = s.players.map { p ->
                                val snap = message.players.find { it.slot == p.id }
                                if (snap != null) p.copy(
                                    life = snap.life,
                                    poison = snap.poison,
                                    experience = snap.experience,
                                    energy = snap.energy,
                                    defeated = snap.defeated,
                                    commanderDamage = snap.commanderDamage,
                                ) else p
                            }
                        )
                    }
                    checkPendingDefeat()
                    checkWinner()
                }
            }
        }
    }

    private fun handleNearbyConnectionEvent(event: NearbyConnectionEvent) {
        when (event) {
            is NearbyConnectionEvent.EndpointConnected,
            is NearbyConnectionEvent.EndpointDisconnected,
            is NearbyConnectionEvent.ConnectionFailed -> {
                // Handled implicitly by repo keeping peers list, could update UI later if needed.
            }
        }
    }

    private fun schedulePersistLife(sessionId: String, slotIndex: Int, newLife: Int) {
        persistLifeJobs[slotIndex]?.cancel()
        persistLifeJobs[slotIndex] = viewModelScope.launch {
            delay(500L)
            runCatching { updateLifeUseCase.persist(sessionId, slotIndex, newLife) }
        }
    }

    private fun schedulePhasePersist(sessionId: String) {
        persistPhaseJob?.cancel()
        persistPhaseJob = viewModelScope.launch {
            delay(500L)
            runCatching { advancePhaseUseCase.persist(sessionId) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        onlineObserveJob?.cancel()
        nearbyObserveJob?.cancel()
        persistLifeJobs.values.forEach { it.cancel() }
        persistPhaseJob?.cancel()
        val sessionId = onlineSessionId ?: return
        if (isNearbySession) {
            nearbyRepo.disconnect()
        }
        cleanupScope.launch {
            if (!isNearbySession) {
                observeSessionUseCase.disconnect(sessionId)
            }
            runCatching { leaveSessionUseCase(sessionId) }
            cleanupScope.cancel()
        }
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

        if (isNearbySession && isNearbyHost) {
            nearbyRepo.sendMessage(NearbyGameMessage.GameFinished(winner.id))
        }

        FirebaseCrashlytics.getInstance().log(
            "game_ended: mode=${result.gameMode.name} turns=${result.totalTurns} appUserWon=${result.appUserWon}"
        )
        analyticsHelper.logEvent("game_ended", mapOf(
            "game_mode"        to result.gameMode.name,
            "total_turns"      to result.totalTurns,
            "app_user_won"     to result.appUserWon,
            "duration_seconds" to (result.durationMs / 1000).toInt(),
            "player_count"     to result.allPlayers.size,
        ))
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
        settings:             GameSettings = GameSettings(),
    ) {
        deltaJobs.values.forEach { it.cancel() }
        deltaJobs.clear()
        val players = configs.mapIndexed { i, cfg ->
            Player(
                id        = i,
                name      = cfg.name.ifEmpty { "Wizard ${i + 1}" },
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
            isGameRunning        = true,
            gameSettings         = settings,
        )
        _toolsState.value = GlobalToolsState()

        FirebaseCrashlytics.getInstance().apply {
            log("game_started: mode=${mode.name} players=${players.size} tournament=true matchId=$matchId")
            setCustomKey("game_mode", mode.name)
            setCustomKey("game_player_count", players.size)
        }
        analyticsHelper.logEvent("game_started", mapOf(
            "game_mode"       to mode.name,
            "player_count"    to players.size,
            "is_tournament"   to true,
        ))
    }

    private fun recordTournamentResultIfNeeded(sessionId: Long, result: GameResult) {
        val s = _uiState.value
        val matchId = s.activeTournamentMatchId ?: return
        val tournamentId = s.activeTournamentId ?: return

        // Guard: if tournament player id list does not cover every game player,
        // recording the result would silently map some players to wrong tournament
        // entries or drop them from life totals — abort instead of persisting garbage.
        if (s.tournamentPlayerIds.size != s.players.size) {
            FirebaseCrashlytics.getInstance().apply {
                log("tournament_result_player_id_mismatch: matchId=$matchId")
                setCustomKey("game_player_count", s.players.size)
                setCustomKey("game_turn_count", s.turnNumber)
                recordException(IllegalStateException(
                    "[GameViewModel] tournamentPlayerIds.size (${s.tournamentPlayerIds.size}) " +
                    "!= players.size (${s.players.size}) for match $matchId"
                ))
            }
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

    fun initFromConfigs(
        configs: List<PlayerConfig>,
        mode: GameMode,
        selectedLayout: LayoutTemplate? = null,
        settings: GameSettings = GameSettings(),
    ) {
        val players = configs.mapIndexed { i, config ->
            Player(
                id        = i,
                name      = config.name.ifEmpty { "Wizard ${i + 1}" },
                life      = mode.startingLife,
                theme     = config.theme,
                isAppUser = config.isAppUser,
            )
        }
        val layout = selectedLayout ?: LayoutTemplates.getDefaultLayout(players.size)
        _uiState.update { it.copy(
            mode = mode,
            players = players,
            activePlayerId = players.first().id,
            activeLayout = layout,
            isGameRunning = true,
            gameSettings = settings,
        ) }
        _toolsState.value = GlobalToolsState()

        FirebaseCrashlytics.getInstance().apply {
            log("game_started: mode=${mode.name} players=${players.size}")
            setCustomKey("game_mode", mode.name)
            setCustomKey("game_player_count", players.size)
        }
        analyticsHelper.logEvent("game_started", mapOf(
            "game_mode"    to mode.name,
            "player_count" to players.size,
        ))
    }

    companion object {
        fun buildInitialState(mode: GameMode, playerCount: Int): GameUiState {
            val clampedCount = playerCount.coerceIn(2, 6)
            val players = (0 until clampedCount).map { i ->
                Player(
                    id        = i,
                    name      = "Wizard ${i + 1}",
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
