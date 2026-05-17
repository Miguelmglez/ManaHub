package com.mmg.manahub.feature.survey.presentation

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.data.local.dao.CardDao
import com.mmg.manahub.core.data.local.dao.GameSessionDao
import com.mmg.manahub.core.data.local.dao.SurveyAnswerDao
import com.mmg.manahub.core.data.local.entity.SurveyAnswerEntity
import com.mmg.manahub.core.data.local.entity.SurveyStatus
import com.mmg.manahub.core.data.local.mapper.toDomainCard
import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.Deck
import com.mmg.manahub.core.domain.repository.DeckRepository
import com.mmg.manahub.core.domain.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ── Survey mode ───────────────────────────────────────────────────────────────

/**
 * Determines how the survey screen behaves:
 * - [COMPLETE] first-time flow shown immediately after a game.
 * - [REVIEW]   re-opening an existing PARTIAL or COMPLETED survey from history.
 */
enum class SurveyMode { COMPLETE, REVIEW }

// ── Card-impact rating ────────────────────────────────────────────────────────

/** The three possible ratings a player can assign to a card in the CARD_IMPACT panel. */
enum class CardImpactRating { KEY_CARD, AVERAGE, WEAK }

// ── Game recap ────────────────────────────────────────────────────────────────

/**
 * Immutable snapshot of the finished game shown at the top of the survey and in
 * the SUMMARY panel.
 */
data class GameRecap(
    val sessionId: Long,
    val won: Boolean,
    val mode: String,
    val totalTurns: Int,
    val durationMs: Long,
    val playedAt: Long,
    val winnerName: String,
    val opponentNames: List<String>,
    val commanderDamageWin: Boolean,
)

// ── UiState ───────────────────────────────────────────────────────────────────

/**
 * Full UI state for the survey screen.
 */
data class SurveyUiState(
    val sessionId: Long = 0L,
    val surveyMode: SurveyMode = SurveyMode.COMPLETE,
    val recap: GameRecap? = null,
    val panels: List<SurveyPanel> = emptyList(),
    val currentPanel: Int = 0,
    val answers: Map<String, String> = emptyMap(),
    val cardImpactSelections: Map<String, CardImpactRating> = emptyMap(),
    /** Up to 3 highest-CMC non-land cards from the deck, suggested for rating. */
    val suggestedImpactCards: List<Card> = emptyList(),
    /** Additional cards the user has manually added to the impact list. */
    val extraImpactCards: List<Card> = emptyList(),
    /** Full deck card list used for the card picker. */
    val deckCards: List<Card> = emptyList(),
    val selectedDeckId: String? = null,
    val availableDecks: List<Deck> = emptyList(),
    val freeNotes: String = "",
    val isComplete: Boolean = false,
    val isLoading: Boolean = false,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * ViewModel for the post-game survey screen.
 *
 * Reads session/player data from [GameSessionDao], persists answers through
 * [SurveyAnswerDao], and coordinates deck association via [DeckRepository].
 * All heavy IO runs on [Dispatchers.IO]; state updates are posted back to the
 * main thread via [MutableStateFlow.update].
 */
@HiltViewModel
class SurveyViewModel @Inject constructor(
    private val surveyAnswerDao: SurveyAnswerDao,
    private val gameSessionDao: GameSessionDao,
    private val deckRepository: DeckRepository,
    private val cardDao: CardDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val sessionId: Long = savedStateHandle.get<Long>("sessionId") ?: 0L
    private val modeArg: String = savedStateHandle.get<String>("mode") ?: "COMPLETE"

    private val _uiState = MutableStateFlow(SurveyUiState(sessionId = sessionId))
    val uiState: StateFlow<SurveyUiState> = _uiState.asStateFlow()

    init {
        loadSession()
    }

    // ── Session loading ───────────────────────────────────────────────────────

    /** Loads all session data and hydrates the full UI state. */
    private fun loadSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val surveyMode = runCatching { SurveyMode.valueOf(modeArg) }.getOrDefault(SurveyMode.COMPLETE)

            val prefs = userPreferencesRepository.preferencesFlow.first()
            val langCode = prefs.appLanguage.code

            // Load session + players from Room on IO dispatcher
            val sessionWithPlayers = withContext(ioDispatcher) {
                gameSessionDao.getSessionById(sessionId)
            } ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val session = sessionWithPlayers.session
            val players = sessionWithPlayers.players

            // Determine app-user player and win status
            val appUserName = prefs.appLanguage.let { _ ->
                // Prefer the winner name lookup: if app user is the winner we know their name;
                // otherwise fall back to the first non-winner player for the recap.
                session.winnerName
            }
            val appUserPlayer = players.firstOrNull { it.isWinner }
            val appUserWon = appUserPlayer != null
            val opponentNames = players
                .filter { !it.isWinner }
                .map { it.playerName }

            val commanderDamageWin = players.any {
                it.eliminationReason == "COMMANDER_DAMAGE"
            }

            val recap = GameRecap(
                sessionId = session.id,
                won = appUserWon,
                mode = session.mode,
                totalTurns = session.totalTurns,
                durationMs = session.durationMs,
                playedAt = session.playedAt,
                winnerName = session.winnerName,
                opponentNames = opponentNames,
                commanderDamageWin = commanderDamageWin,
            )

            // Load available decks
            val availableDecks = withContext(ioDispatcher) {
                deckRepository.observeAllDecks().first()
            }

            // Restore previously saved answers if REVIEW mode or PARTIAL session
            val existingAnswers = withContext(ioDispatcher) {
                surveyAnswerDao.getAnswersForSession(sessionId)
            }

            val restoredAnswers = mutableMapOf<String, String>()
            val restoredCardImpact = mutableMapOf<String, CardImpactRating>()
            var restoredNotes = ""
            var restoredDeckId = session.deckId

            existingAnswers.forEach { entity ->
                when (entity.questionType) {
                    "CARD_IMPACT" -> {
                        val rating = runCatching { CardImpactRating.valueOf(entity.answer) }
                            .getOrNull()
                        if (entity.cardReference != null && rating != null) {
                            restoredCardImpact[entity.cardReference] = rating
                        }
                    }
                    "FREE_TEXT" -> restoredNotes = entity.answer
                    else -> restoredAnswers[entity.questionId] = entity.answer
                }
            }

            // Load deck cards for the selected deck; treat a deleted deck as no deck
            val deckCards = if (restoredDeckId != null) loadDeckCards(restoredDeckId!!) else emptyList()
            if (restoredDeckId != null && deckCards.isEmpty()) {
                val exists = withContext(ioDispatcher) {
                    deckRepository.observeDeckWithCards(restoredDeckId!!).first() != null
                }
                if (!exists) restoredDeckId = null
            }
            val hasDeck = restoredDeckId != null
            val suggestedCards = computeSuggestedCards(deckCards)

            // Build panels
            val panels = SurveyQuestionEngine.buildPanels(
                won = appUserWon,
                context = context,
                langCode = langCode,
                hasDeck = hasDeck,
            )

            _uiState.update {
                it.copy(
                    surveyMode = surveyMode,
                    recap = recap,
                    panels = panels,
                    answers = restoredAnswers,
                    cardImpactSelections = restoredCardImpact,
                    freeNotes = restoredNotes,
                    selectedDeckId = restoredDeckId,
                    availableDecks = availableDecks,
                    deckCards = deckCards,
                    suggestedImpactCards = suggestedCards,
                    isLoading = false,
                )
            }
        }
    }

    // ── Panel navigation ──────────────────────────────────────────────────────

    /** Jumps to the panel at [index]. */
    fun setCurrentPanel(index: Int) {
        _uiState.update { it.copy(currentPanel = index.coerceIn(0, it.panels.lastIndex)) }
    }

    // ── Answer mutations ──────────────────────────────────────────────────────

    /**
     * Records a standard answer for [questionId] and triggers an async persistence
     * of the current state.
     */
    fun setAnswer(questionId: String, answer: String) {
        _uiState.update { it.copy(answers = it.answers + (questionId to answer)) }
        autoSave()
    }

    /** Records a card-impact rating for [scryfallId]. */
    fun setCardImpact(scryfallId: String, rating: CardImpactRating) {
        _uiState.update {
            it.copy(cardImpactSelections = it.cardImpactSelections + (scryfallId to rating))
        }
        autoSave()
    }

    /** Adds [card] to the manually selected impact cards list (deduplicates by scryfallId). */
    fun addExtraImpactCard(card: Card) {
        _uiState.update { state ->
            if (state.extraImpactCards.any { it.scryfallId == card.scryfallId }) state
            else state.copy(extraImpactCards = state.extraImpactCards + card)
        }
    }

    /** Removes the card with [scryfallId] from both the selections map and the extra-cards list. */
    fun removeImpactCard(scryfallId: String) {
        _uiState.update { state ->
            state.copy(
                cardImpactSelections = state.cardImpactSelections - scryfallId,
                extraImpactCards = state.extraImpactCards.filter { it.scryfallId != scryfallId },
            )
        }
    }

    /** Updates free-form notes and triggers auto-save. */
    fun setFreeNotes(text: String) {
        _uiState.update { it.copy(freeNotes = text) }
        autoSave()
    }

    // ── Deck association ──────────────────────────────────────────────────────

    /**
     * Associates [deckId] with this session. Updates the Room row, loads the deck cards,
     * recomputes card suggestions, and rebuilds panels (adding CARD_IMPACT if first association).
     */
    fun selectDeck(deckId: String?) {
        viewModelScope.launch {
            withContext(ioDispatcher) {
                gameSessionDao.updateSessionDeck(sessionId, deckId)
            }

            val deckCards = if (deckId != null) loadDeckCards(deckId) else emptyList()
            val suggestedCards = computeSuggestedCards(deckCards)

            val prefs = userPreferencesRepository.preferencesFlow.first()
            val langCode = prefs.appLanguage.code
            val won = _uiState.value.recap?.won ?: false
            val hasDeck = deckId != null

            val previousId = _uiState.value.panels.getOrNull(_uiState.value.currentPanel)?.id

            val newPanels = SurveyQuestionEngine.buildPanels(
                won = won,
                context = context,
                langCode = langCode,
                hasDeck = hasDeck,
            )

            val newCurrentPanel = when {
                previousId == SurveyPanelId.CARD_IMPACT && newPanels.none { it.id == SurveyPanelId.CARD_IMPACT } ->
                    newPanels.indexOfFirst { it.id == SurveyPanelId.FUNDAMENTALS }.coerceAtLeast(0)
                previousId != null && newPanels.any { it.id == previousId } ->
                    newPanels.indexOfFirst { it.id == previousId }
                else ->
                    _uiState.value.currentPanel.coerceAtMost(newPanels.lastIndex)
            }

            _uiState.update {
                it.copy(
                    selectedDeckId = deckId,
                    deckCards = deckCards,
                    suggestedImpactCards = suggestedCards,
                    panels = newPanels,
                    currentPanel = newCurrentPanel,
                )
            }
        }
    }

    // ── Completion actions ────────────────────────────────────────────────────

    /**
     * Saves the current progress as [SurveyStatus.PARTIAL] and signals the screen to pop.
     * Use when the user taps "Later".
     */
    fun postpone() {
        viewModelScope.launch {
            persistAnswers(SurveyStatus.PARTIAL)
            _uiState.update { it.copy(isComplete = true) }
        }
    }

    /**
     * Saves all answers as [SurveyStatus.COMPLETED] and signals the screen to pop.
     * Use when the user taps "Save & finish".
     */
    fun complete() {
        viewModelScope.launch {
            persistAnswers(SurveyStatus.COMPLETED)
            _uiState.update { it.copy(isComplete = true) }
        }
    }

    /**
     * Closes the survey without persisting any status change.
     * Used when closing from [SurveyMode.REVIEW] — the session is already COMPLETED or
     * PARTIAL, so no downgrade should occur.
     */
    fun dismissWithoutChanges() {
        _uiState.update { it.copy(isComplete = true) }
    }

    /**
     * Marks the survey as [SurveyStatus.SKIPPED] (only if currently PENDING) and signals
     * the screen to pop. If the survey is already PARTIAL or COMPLETED, the status is left
     * unchanged.
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

    /** Triggers a non-debounced persistence of the current answers as PARTIAL. */
    private fun autoSave() {
        viewModelScope.launch {
            persistAnswers(SurveyStatus.PARTIAL)
        }
    }

    /**
     * Builds [SurveyAnswerEntity] rows from the current state and writes them atomically.
     *
     * - Standard answers: one row per (questionId, answer) pair.
     * - Card-impact answers: one row per rated card, [questionType] = "CARD_IMPACT",
     *   [SurveyAnswerEntity.cardReference] = scryfallId.
     * - Free-text notes: one row with [questionType] = "FREE_TEXT" (only if non-blank).
     *
     * Uses [SurveyAnswerDao.replaceAnswersForSession] (DELETE + INSERT transaction) so
     * repeated calls are idempotent and never accumulate stale rows.
     */
    private suspend fun persistAnswers(status: SurveyStatus) {
        val state = _uiState.value
        val now = System.currentTimeMillis()
        val deckId = state.selectedDeckId

        val entities = mutableListOf<SurveyAnswerEntity>()

        // Standard question answers
        state.answers.forEach { (questionId, answer) ->
            val panel = state.panels.firstNotNullOfOrNull { p ->
                p.questions.find { it.id == questionId }
            }
            entities += SurveyAnswerEntity(
                sessionId = sessionId,
                questionId = questionId,
                questionType = panel?.type ?: "UNKNOWN",
                answer = answer,
                cardReference = null,
                deckId = deckId,
                updatedAt = now,
            )
        }

        // Card-impact selections (one row per card)
        state.cardImpactSelections.forEach { (scryfallId, rating) ->
            entities += SurveyAnswerEntity(
                sessionId = sessionId,
                questionId = "card_impact",
                questionType = "CARD_IMPACT",
                answer = rating.name,
                cardReference = scryfallId,
                deckId = deckId,
                updatedAt = now,
            )
        }

        // Free-text notes
        if (state.freeNotes.isNotBlank()) {
            entities += SurveyAnswerEntity(
                sessionId = sessionId,
                questionId = "free_notes",
                questionType = "FREE_TEXT",
                answer = state.freeNotes,
                cardReference = null,
                deckId = deckId,
                updatedAt = now,
            )
        }

        val completedAt = if (status == SurveyStatus.COMPLETED) now else null

        withContext(ioDispatcher) {
            surveyAnswerDao.replaceAnswersForSession(sessionId, entities)
            gameSessionDao.updateSurveyStatus(
                sessionId = sessionId,
                status = status.name,
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

    /**
     * Returns up to 3 cards sorted by descending CMC, excluding lands.
     * Used as the pre-seeded suggestions in the CARD_IMPACT panel.
     */
    private fun computeSuggestedCards(cards: List<Card>): List<Card> =
        cards
            .filter { !it.typeLine.contains("Land", ignoreCase = true) }
            .sortedByDescending { it.cmc }
            .take(3)
}
