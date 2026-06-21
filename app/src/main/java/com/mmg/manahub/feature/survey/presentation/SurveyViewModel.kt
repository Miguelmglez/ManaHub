package com.mmg.manahub.feature.survey.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.data.local.dao.SurveyCardImpactDao
import com.mmg.manahub.core.data.local.entity.SurveyAnswerEntity
import com.mmg.manahub.core.data.local.entity.SurveyCardImpactEntity
import com.mmg.manahub.core.data.local.entity.SurveyStatus
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.model.Deck
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import com.mmg.manahub.feature.survey.domain.usecase.CompleteSurveyUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Survey mode ───────────────────────────────────────────────────────────────

/**
 * Determines how the survey screen behaves:
 * - [COMPLETE] first-time flow shown immediately after a game.
 * - [REVIEW]   re-opening an existing PARTIAL or COMPLETED survey from history.
 */
enum class SurveyMode { COMPLETE, REVIEW }

// ── Card-impact rating ────────────────────────────────────────────────────────

/** The three possible ratings a player can assign to a card in the legacy CARD_IMPACT panel. */
enum class CardImpactRating { KEY_CARD, AVERAGE, WEAK }

// ── Game recap ────────────────────────────────────────────────────────────────

/**
 * Immutable snapshot of the finished game shown in the read-only context strip.
 *
 * @param opponents Ordered (playerId → name) pairs for every non-local seat. Used to
 *   render one archetype block per opponent and to write the archetype back to the
 *   correct seat via [SurveyViewModel.setOpponentArchetype].
 */
data class GameRecap(
    val sessionId: Long,
    val won: Boolean,
    val mode: String,
    val totalTurns: Int,
    val durationMs: Long,
    val playedAt: Long,
    val winnerName: String,
    val playerCount: Int,
    val opponentNames: List<String>,
    val opponents: List<Pair<Int, String>>,
    val commanderDamageWin: Boolean,
)

// ── UiState ───────────────────────────────────────────────────────────────────

/**
 * Full UI state for the single-form survey screen (Phase 3).
 *
 * The legacy panel-based fields ([panels], [currentPanel], [cardImpactSelections],
 * [suggestedImpactCards], [extraImpactCards]) are retained so backward-compatible
 * persistence keeps working, but the screen no longer drives a step flow.
 */
data class SurveyUiState(
    val sessionId: Long = 0L,
    val surveyMode: SurveyMode = SurveyMode.COMPLETE,
    val recap: GameRecap? = null,

    // Generic answer map for star ratings / free-form question ids (e.g. "game_rating").
    val answers: Map<String, String> = emptyMap(),

    // ── Phase 3 structured answers ───────────────────────────────────────────
    val resultAnswer: String? = null,           // "WIN" | "LOSE" | "DRAW"
    val eliminationCause: String? = null,       // null until the user picks
    val handQualityRating: Int? = null,         // 1-4 (Unkeepable/Risky/Solid/Perfect)
    val mulligansCount: Int = 0,                // 0..N London mulligan stepper
    val manaFlowAnswer: String? = null,         // "SCREWED"|"TIGHT"|"SMOOTH"|"FLOODED"
    val deckPickedForSeat: Boolean = false,     // true once the local seat has a deckId
    val commanderCarriedAnswer: String? = null, // Commander mode only
    val sideboardSwingAnswer: String? = null,   // Bo2/Bo3 or deck has a sideboard
    val opponentArchetypes: Map<Int, String> = emptyMap(), // playerId → archetype

    // Per-card impact ratings keyed by "playerSessionId:cardId" → "MVP" | "DEAD".
    val cardImpactRatings: Map<String, String> = emptyMap(),

    val freeNotes: String = "",

    // Deck association.
    val deckCards: List<Card> = emptyList(),
    val selectedDeckId: String? = null,
    val availableDecks: List<Deck> = emptyList(),
    val hasSideboard: Boolean = false,
    val localPlayerSessionId: Long = 0L,

    val completionFraction: Float = 0f,         // 0f..1f answered optional sections
    val isComplete: Boolean = false,
    val isLoading: Boolean = false,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * ViewModel for the single-form post-game survey screen.
 *
 * Reads session/player data from [GameSessionDao], persists answers through
 * [SurveyAnswerDao] and per-seat card impacts through [SurveyCardImpactDao], and
 * coordinates deck association via [DeckRepository]. Every setter mutates in-memory
 * state immediately and schedules a debounced DRAFT autosave; the result write-back
 * additionally corrects the recorded game outcome. All heavy IO runs on the injected
 * [ioDispatcher]; state updates are posted via [MutableStateFlow.update].
 *
 * KMP migration — Phase 1: resolved by Koin (`koinViewModel()`), not Hilt. The plain constructor lets
 * `surveyKoinModule` supply the application [Context] via `androidContext()`, the IO dispatcher as
 * `Dispatchers.IO` directly (the same singleton the old `@IoDispatcher` binding returned), and the
 * nav-arg-carrying [SavedStateHandle] via `get()`.
 */
class SurveyViewModel(
    private val surveyAnswerDao: SurveyAnswerDao,
    private val surveyCardImpactDao: SurveyCardImpactDao,
    private val gameSessionDao: GameSessionDao,
    private val deckRepository: DeckRepository,
    private val cardDao: CardDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val completeSurvey: CompleteSurveyUseCase,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L
    private val modeArg: String = savedStateHandle.get<String>("mode") ?: "COMPLETE"

    private val _uiState = MutableStateFlow(SurveyUiState(sessionId = sessionId))
    val uiState: StateFlow<SurveyUiState> = _uiState.asStateFlow()

    /** Total number of optional sections counted toward [SurveyUiState.completionFraction]. */
    private val optionalSectionCount = 7

    private var autoSaveJob: Job? = null

    init {
        loadSession()
    }

    // ── Session loading ───────────────────────────────────────────────────────

    /** Loads all session data and hydrates the full UI state. */
    private fun loadSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val surveyMode = runCatching { SurveyMode.valueOf(modeArg) }.getOrDefault(SurveyMode.COMPLETE)

            val sessionWithPlayers = withContext(ioDispatcher) {
                gameSessionDao.getSessionById(sessionId)
            } ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val session = sessionWithPlayers.session
            val players = sessionWithPlayers.players

            // Determine the app user's seat and win status from the persisted `isLocal`
            // flag (see ADR-001). Inferring from `isWinner` always reports a win because
            // every finished game has a winner.
            val localPlayer = players.firstOrNull { it.isLocal }
            val appUserWon = localPlayer?.isWinner == true
            val winnerName = players.firstOrNull { it.isWinner }?.playerName ?: session.winnerName
            val opponents = players
                .filter { !it.isLocal }
                .map { it.playerId to it.playerName }
            val opponentNames = opponents.map { it.second }
            val restoredArchetypes = players
                .filter { !it.isLocal && it.archetype != null }
                .associate { it.playerId to it.archetype!! }

            val commanderDamageWin = players.any { it.eliminationReason == "COMMANDER_DAMAGE" }

            val recap = GameRecap(
                sessionId = session.id,
                won = appUserWon,
                mode = session.mode,
                totalTurns = session.totalTurns,
                durationMs = session.durationMs,
                playedAt = session.playedAt,
                winnerName = winnerName,
                playerCount = session.playerCount,
                opponentNames = opponentNames,
                opponents = opponents,
                commanderDamageWin = commanderDamageWin,
            )

            val availableDecks = withContext(ioDispatcher) {
                deckRepository.observeAllDecks().first()
            }

            // Restore previously saved answers (REVIEW mode or DRAFT session).
            val existingAnswers = withContext(ioDispatcher) {
                surveyAnswerDao.getAnswersForSession(sessionId)
            }
            val existingImpacts = withContext(ioDispatcher) {
                surveyCardImpactDao.observeForSession(sessionId).first()
            }

            val restoredAnswers = mutableMapOf<String, String>()
            var restoredNotes = ""
            var restoredResult: String? = null
            var restoredElimination: String? = null
            var restoredHandQuality: Int? = null
            var restoredMulligans = 0
            var restoredManaFlow: String? = null
            var restoredCommanderCarried: String? = null
            var restoredSideboardSwing: String? = null
            var restoredDeckId = session.deckId

            existingAnswers.forEach { entity ->
                when (entity.questionType) {
                    "RESULT" -> restoredResult = entity.answer
                    "ELIMINATION" -> restoredElimination = entity.answer
                    "HAND" -> restoredHandQuality = entity.answer.toIntOrNull()
                    "MULLIGAN" -> restoredMulligans = entity.answer.toIntOrNull() ?: 0
                    "MANA" -> restoredManaFlow = entity.answer
                    "COMMANDER_CARRIED" -> restoredCommanderCarried = entity.answer
                    "SIDEBOARD_SWING" -> restoredSideboardSwing = entity.answer
                    "FREE_TEXT" -> restoredNotes = entity.answer
                    else -> restoredAnswers[entity.questionId] = entity.answer
                }
            }

            // Pre-fill the result from the authoritative win flag if not already saved.
            if (restoredResult == null) {
                restoredResult = when {
                    appUserWon -> "WIN"
                    isDrawSession(session.winnerId, winnerName) -> "DRAW"
                    else -> "LOSE"
                }
            }

            // Restore per-card impacts keyed by "playerSessionId:cardId".
            val restoredImpacts = existingImpacts.associate {
                "${it.playerSessionId}:${it.cardId}" to it.impact
            }

            // Load deck cards; treat a deleted deck as no deck.
            var deckCards = if (restoredDeckId != null) loadDeckCards(restoredDeckId!!) else emptyList()
            var hasSideboard = false
            if (restoredDeckId != null) {
                val deckWithCards = withContext(ioDispatcher) {
                    deckRepository.observeDeckWithCards(restoredDeckId!!).first()
                }
                if (deckWithCards == null) {
                    restoredDeckId = null
                    deckCards = emptyList()
                } else {
                    hasSideboard = deckWithCards.sideboard.isNotEmpty()
                }
            }
            val deckPicked = restoredDeckId != null

            _uiState.update {
                it.copy(
                    surveyMode = surveyMode,
                    recap = recap,
                    answers = restoredAnswers,
                    resultAnswer = restoredResult,
                    eliminationCause = restoredElimination,
                    handQualityRating = restoredHandQuality,
                    mulligansCount = restoredMulligans,
                    manaFlowAnswer = restoredManaFlow,
                    commanderCarriedAnswer = restoredCommanderCarried,
                    sideboardSwingAnswer = restoredSideboardSwing,
                    opponentArchetypes = restoredArchetypes,
                    cardImpactRatings = restoredImpacts,
                    freeNotes = restoredNotes,
                    selectedDeckId = restoredDeckId,
                    deckPickedForSeat = deckPicked,
                    availableDecks = availableDecks,
                    deckCards = deckCards,
                    hasSideboard = hasSideboard,
                    localPlayerSessionId = localPlayer?.id ?: 0L,
                    isLoading = false,
                )
            }
            recomputeCompletion()
        }
    }

    /**
     * Heuristic draw detection for a finished game: the persisted session carries a
     * draw sentinel (winnerId = -1) or an explicit "Draw" winner name.
     */
    private fun isDrawSession(winnerId: Int, winnerName: String): Boolean =
        winnerId == -1 || winnerName.equals("Draw", ignoreCase = true)

    // ── Setters (Phase 3) ───────────────────────────────────────────────────────

    /**
     * Records the user-corrected game result and writes it back to the persisted
     * session/seat rows. "WIN" sets the local seat as winner and the session winner to
     * the local player; "DRAW" writes the draw sentinel; "LOSE" only clears the local
     * seat's winner flag (the opponent's recorded winner is left untouched).
     */
    fun setResult(result: String) {
        _uiState.update { it.copy(resultAnswer = result) }
        viewModelScope.launch(ioDispatcher) {
            val session = gameSessionDao.getSessionById(sessionId)
            val localPlayer = session?.players?.firstOrNull { it.isLocal }
            when (result) {
                "WIN" -> {
                    if (localPlayer != null) {
                        gameSessionDao.updateSessionResult(
                            sessionId = sessionId,
                            winnerId = localPlayer.playerId,
                            winnerName = localPlayer.playerName,
                        )
                    }
                    gameSessionDao.updateLocalSeatWinner(sessionId, true)
                }
                "LOSE" -> {
                    gameSessionDao.updateLocalSeatWinner(sessionId, false)
                }
                "DRAW" -> {
                    gameSessionDao.updateSessionResultDraw(sessionId)
                    gameSessionDao.updateLocalSeatWinner(sessionId, false)
                }
            }
        }
        recomputeCompletion()
        scheduleAutoSave()
    }

    /** Records how the game ended (win cause or loss cause). */
    fun setEliminationCause(cause: String) {
        _uiState.update { it.copy(eliminationCause = cause) }
        recomputeCompletion()
        scheduleAutoSave()
    }

    /** Records the opening-hand quality rating (1-4). */
    fun setHandQuality(rating: Int) {
        _uiState.update { it.copy(handQualityRating = rating) }
        recomputeCompletion()
        scheduleAutoSave()
    }

    /** Records the number of mulligans taken (clamped to 0..6). */
    fun setMulligan(count: Int) {
        _uiState.update { it.copy(mulligansCount = count.coerceIn(0, 6)) }
        recomputeCompletion()
        scheduleAutoSave()
    }

    /** Records the mana-flow answer. */
    fun setManaFlow(answer: String) {
        _uiState.update { it.copy(manaFlowAnswer = answer) }
        recomputeCompletion()
        scheduleAutoSave()
    }

    /** Records the Commander-carried answer (Commander mode only). */
    fun setCommanderCarried(answer: String) {
        _uiState.update { it.copy(commanderCarriedAnswer = answer) }
        recomputeCompletion()
        scheduleAutoSave()
    }

    /** Records the sideboard-swing answer. */
    fun setSideboardSwing(answer: String) {
        _uiState.update { it.copy(sideboardSwingAnswer = answer) }
        recomputeCompletion()
        scheduleAutoSave()
    }

    /**
     * Records an opponent seat's archetype and writes it straight to that seat's row
     * so cross-game matchup stats see the classification immediately.
     */
    fun setOpponentArchetype(playerId: Int, archetype: String) {
        _uiState.update { it.copy(opponentArchetypes = it.opponentArchetypes + (playerId to archetype)) }
        viewModelScope.launch(ioDispatcher) {
            gameSessionDao.updateSeatArchetype(sessionId, playerId, archetype)
        }
        recomputeCompletion()
        scheduleAutoSave()
    }

    /**
     * Toggles the per-card impact rating for the local seat. The map is keyed by
     * "playerSessionId:cardId" so multiple copies of the same card on different seats
     * never collide.
     */
    fun setCardImpact(playerSessionId: Long, cardId: String, impact: String) {
        val key = "$playerSessionId:$cardId"
        _uiState.update { s ->
            val current = s.cardImpactRatings[key]
            // Tapping the same impact again clears it (neutral state).
            val updated = if (current == impact) s.cardImpactRatings - key
            else s.cardImpactRatings + (key to impact)
            s.copy(cardImpactRatings = updated)
        }
        recomputeCompletion()
        scheduleAutoSave()
    }

    /** Records a generic answer (e.g. star "game_rating"). */
    fun setAnswer(questionId: String, answer: String) {
        _uiState.update { it.copy(answers = it.answers + (questionId to answer)) }
        recomputeCompletion()
        scheduleAutoSave()
    }

    /** Updates free-form notes. */
    fun setFreeNotes(text: String) {
        _uiState.update { it.copy(freeNotes = text) }
        recomputeCompletion()
        scheduleAutoSave()
    }

    // ── Deck association ──────────────────────────────────────────────────────

    /**
     * Associates [deckId] with this session, loads its cards, flags
     * [SurveyUiState.deckPickedForSeat], and detects whether the deck has a sideboard.
     */
    fun setDeckForSession(deckId: String?) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                gameSessionDao.updateSessionDeck(sessionId, deckId)
            }
            val deckCards = if (deckId != null) loadDeckCards(deckId) else emptyList()
            val hasSideboard = if (deckId != null) {
                withContext(ioDispatcher) {
                    deckRepository.observeDeckWithCards(deckId).first()?.sideboard?.isNotEmpty() ?: false
                }
            } else false

            _uiState.update {
                it.copy(
                    selectedDeckId = deckId,
                    deckCards = deckCards,
                    deckPickedForSeat = deckId != null,
                    hasSideboard = hasSideboard,
                )
            }
            recomputeCompletion()
            scheduleAutoSave()
        }
    }

    // ── Completion actions ────────────────────────────────────────────────────

    /** Saves the current progress as a DRAFT and signals the screen to pop ("Save & exit"). */
    fun postpone() {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            persistAnswers("DRAFT")
            _uiState.update { it.copy(isComplete = true) }
        }
    }

    /** Saves all answers as COMPLETED and signals the screen to pop ("Submit review"). */
    fun complete() {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            persistAnswers("COMPLETED")
            // Raise the progression event only after the COMPLETED write succeeds. The
            // event is idempotent (key survey:{sessionId}), so re-completing is a no-op.
            completeSurvey(sessionId)
            _uiState.update { it.copy(isComplete = true) }
        }
    }

    /**
     * Closes the survey without persisting any status change (REVIEW mode close).
     */
    fun dismissWithoutChanges() {
        _uiState.update { it.copy(isComplete = true) }
    }

    /**
     * Marks the survey as [SurveyStatus.SKIPPED] (only if currently PENDING) and pops.
     */
    fun skipAll() {
        viewModelScope.launch {
            val currentSession = withContext(ioDispatcher) {
                gameSessionDao.getSessionById(sessionId)
            }
            val currentStatus = currentSession?.session?.surveyStatus?.let {
                runCatching { SurveyStatus.valueOf(it) }.getOrNull()
            } ?: SurveyStatus.PENDING

            if (currentStatus == SurveyStatus.PENDING) {
                withContext(ioDispatcher) {
                    gameSessionDao.updateSurveyStatus(
                        sessionId = sessionId,
                        status = SurveyStatus.SKIPPED.name,
                        completedAt = null,
                    )
                }
            }
            _uiState.update { it.copy(isComplete = true) }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Debounced (800 ms) DRAFT persistence — cancels any pending save. */
    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(800)
            persistAnswers("DRAFT")
        }
    }

    /** Recomputes the optional-section completion fraction (result is excluded — always pre-filled). */
    private fun recomputeCompletion() {
        _uiState.update { s ->
            var answered = 0
            if (s.eliminationCause != null) answered++
            if (s.handQualityRating != null) answered++
            if (s.manaFlowAnswer != null) answered++
            if (s.cardImpactRatings.isNotEmpty()) answered++
            if (s.opponentArchetypes.isNotEmpty()) answered++
            if (!s.answers["game_rating"].isNullOrBlank()) answered++
            if (s.freeNotes.isNotBlank()) answered++
            s.copy(completionFraction = answered.toFloat() / optionalSectionCount.toFloat())
        }
    }

    /**
     * Builds [SurveyAnswerEntity] rows from the current state and writes them atomically,
     * then persists per-seat card impacts to [SurveyCardImpactDao]. Updates the session's
     * [SurveyStatus] (DRAFT → PARTIAL, COMPLETED → COMPLETED).
     *
     * @param status "DRAFT" while auto-saving, "COMPLETED" on submit.
     */
    private suspend fun persistAnswers(status: String) {
        val state = _uiState.value
        val now = System.currentTimeMillis()
        val deckId = state.selectedDeckId

        val entities = mutableListOf<SurveyAnswerEntity>()

        fun add(questionId: String, type: String, answer: String?) {
            if (answer.isNullOrBlank()) return
            entities += SurveyAnswerEntity(
                sessionId = sessionId,
                questionId = questionId,
                questionType = type,
                answer = answer,
                cardReference = null,
                deckId = deckId,
                updatedAt = now,
                status = status,
            )
        }

        add("result", "RESULT", state.resultAnswer)
        add("elimination_cause", "ELIMINATION", state.eliminationCause)
        add("hand_quality", "HAND", state.handQualityRating?.toString())
        add("mulligans", "MULLIGAN", state.mulligansCount.toString())
        add("mana_flow", "MANA", state.manaFlowAnswer)
        add("commander_carried", "COMMANDER_CARRIED", state.commanderCarriedAnswer)
        add("sideboard_swing", "SIDEBOARD_SWING", state.sideboardSwingAnswer)
        add("free_notes", "FREE_TEXT", state.freeNotes)

        // Generic answers (e.g. game_rating).
        state.answers.forEach { (questionId, answer) ->
            add(questionId, "GENERIC", answer)
        }

        // Build per-seat card impacts from the "playerSessionId:cardId" keyed map.
        val impacts = state.cardImpactRatings.mapNotNull { (key, impact) ->
            val sep = key.indexOf(':')
            if (sep <= 0) return@mapNotNull null
            val playerSessionId = key.substring(0, sep).toLongOrNull() ?: return@mapNotNull null
            val cardId = key.substring(sep + 1)
            if (cardId.isBlank()) return@mapNotNull null
            SurveyCardImpactEntity(
                sessionId = sessionId,
                playerSessionId = playerSessionId,
                cardId = cardId,
                impact = impact,
            )
        }

        val sessionStatus = if (status == "COMPLETED") SurveyStatus.COMPLETED else SurveyStatus.PARTIAL
        val completedAt = if (status == "COMPLETED") now else null

        withContext(ioDispatcher) {
            surveyAnswerDao.replaceAnswersForSession(sessionId, entities)
            surveyCardImpactDao.deleteForSession(sessionId)
            if (impacts.isNotEmpty()) surveyCardImpactDao.insertAll(impacts)
            gameSessionDao.updateSurveyStatus(
                sessionId = sessionId,
                status = sessionStatus.name,
                completedAt = completedAt,
            )
        }
    }

    /**
     * Loads all cards from [deckId] via [DeckRepository.observeDeckWithCards].
     * Returns empty list if deck is not found.
     */
    private suspend fun loadDeckCards(deckId: String): List<Card> = withContext(ioDispatcher) {
        val deckWithCards = deckRepository.observeDeckWithCards(deckId).first()
            ?: return@withContext emptyList()
        val scryfallIds = (deckWithCards.mainboard + deckWithCards.sideboard)
            .map { it.scryfallId }
            .distinct()
        if (scryfallIds.isEmpty()) emptyList()
        else cardDao.getByIds(scryfallIds).map { it.toDomainCard() }
    }
}
