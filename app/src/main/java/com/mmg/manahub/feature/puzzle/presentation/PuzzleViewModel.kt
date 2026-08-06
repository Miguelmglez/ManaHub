package com.mmg.manahub.feature.puzzle.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.manahub.core.domain.usecase.card.SearchCardsUseCase
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.puzzle.GuessCardPayload
import com.mmg.manahub.core.model.puzzle.Puzzle
import com.mmg.manahub.core.model.puzzle.PuzzleGuessResult
import com.mmg.manahub.core.model.puzzle.PuzzleResult
import com.mmg.manahub.core.model.puzzle.PuzzleType
import com.mmg.manahub.feature.puzzle.domain.usecase.GetPuzzleResultUseCase
import com.mmg.manahub.feature.puzzle.domain.usecase.GetTodayPuzzleUseCase
import com.mmg.manahub.feature.puzzle.domain.usecase.SavePuzzleResultUseCase
import com.mmg.manahub.feature.puzzle.domain.usecase.SubmitPuzzleGuessUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

/**
 * Drives the Daily Puzzle screen: resolves today's puzzle (resuming an in-progress local attempt
 * when one exists), accepts guesses through a debounced card-name search box, and persists
 * progress after every guess so a process death mid-puzzle can resume correctly.
 *
 * ## Deviations from the original Batch B2 design note (documented, not silent)
 * - [GetPuzzleResultUseCase] did not exist in Batch B1 (only [GetTodayPuzzleUseCase],
 *   [SubmitPuzzleGuessUseCase] and [SavePuzzleResultUseCase] were wrapped) — it was added alongside
 *   this ViewModel as a fourth, thin, single-purpose use case (see its own KDoc).
 * - [PuzzleUiState.Offline] fires on ANY [GetTodayPuzzleUseCase] failure, not only "no network and
 *   nothing resumable locally" — see [PuzzleUiState.Offline]'s KDoc for why.
 * - No one-shot event channel: [PuzzleUiState.Solved] already carries the outcome reactively
 *   ([PuzzleResult.solved]), and nothing else in this batch needs a fire-once side effect, so one
 *   was not added (per the task's own "don't add unused plumbing" guidance).
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PuzzleViewModel(
    private val getTodayPuzzleUseCase: GetTodayPuzzleUseCase,
    private val getPuzzleResultUseCase: GetPuzzleResultUseCase,
    private val submitPuzzleGuessUseCase: SubmitPuzzleGuessUseCase,
    private val savePuzzleResultUseCase: SavePuzzleResultUseCase,
    private val searchCardsUseCase: SearchCardsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<PuzzleUiState>(PuzzleUiState.Loading)
    val uiState: StateFlow<PuzzleUiState> = _uiState.asStateFlow()

    /** Live text in the guess input field. */
    private val _guessQueryText = MutableStateFlow("")
    val guessQueryText: StateFlow<String> = _guessQueryText.asStateFlow()

    /** Debounced card-name autocomplete results for [guessQueryText]. */
    private val _guessSuggestions = MutableStateFlow<List<Card>>(emptyList())
    val guessSuggestions: StateFlow<List<Card>> = _guessSuggestions.asStateFlow()

    private val _isSearchingSuggestions = MutableStateFlow(false)
    val isSearchingSuggestions: StateFlow<Boolean> = _isSearchingSuggestions.asStateFlow()

    /** True while a submitted guess is being resolved/saved — drives the submit button's spinner. */
    private val _isSubmittingGuess = MutableStateFlow(false)
    val isSubmittingGuess: StateFlow<Boolean> = _isSubmittingGuess.asStateFlow()

    /**
     * Transient guess-submission error (e.g. the typed name did not resolve to a real card, or a
     * partial-progress save failed) — separate from [PuzzleUiState.Failed], which is reserved for a
     * failure to load the puzzle itself. Cleared on the next query edit or guess submission.
     */
    private val _guessError = MutableStateFlow<String?>(null)
    val guessError: StateFlow<String?> = _guessError.asStateFlow()

    /** Debounce trigger for [guessSuggestions] — separate from [_guessQueryText] so the text field
     * updates immediately while the search itself waits out the debounce window. */
    private val guessQueryTrigger = MutableStateFlow("")

    /**
     * Wall-clock start of the current attempt. Set in [loadPuzzle]: the ORIGINAL persisted value
     * when resuming (never reset), or now for a fresh attempt. Used to compute
     * [PuzzleResult.elapsedMs].
     */
    private var sessionStartedAt: Instant = Clock.System.now()

    private val json = Json { ignoreUnknownKeys = true }

    init {
        loadPuzzle()
        viewModelScope.launch {
            guessQueryTrigger
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { query -> runGuessSearch(query) }
        }
    }

    /** Re-runs [loadPuzzle] — the retry action for [PuzzleUiState.Offline]/[PuzzleUiState.Failed]. */
    fun retryLoad() = loadPuzzle()

    private fun loadPuzzle() {
        viewModelScope.launch {
            _uiState.value = PuzzleUiState.Loading
            runCatching {
                // Mirrors the server's UTC rollover boundary (ADR-006 Decision 2) so the resume-check
                // targets the right local row BEFORE the network round-trip confirms the real date.
                val guessedToday = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
                val localInProgress = getPuzzleResultUseCase(guessedToday)?.takeIf { it.completedAt == null }

                when (val result = getTodayPuzzleUseCase()) {
                    is DataResult.Success -> {
                        val puzzle = result.data
                        // Only resume a local row that is genuinely for THIS server-confirmed puzzle
                        // date — a stale/guessed-date row (clock skew, or yesterday's leftover) must
                        // never be silently merged into a different day's puzzle.
                        val resumable = localInProgress?.takeIf { it.puzzleDate == puzzle.date }
                        sessionStartedAt = resumable?.startedAt ?: Clock.System.now()
                        PuzzleUiState.Playing(
                            puzzle = puzzle,
                            guesses = resumable?.guesses.orEmpty(),
                            resumedFromCache = resumable != null,
                            // Server-authoritative — a corrupt/malformed payload fails the load
                            // (caught by the outer runCatching below) rather than silently starting
                            // the attempt under the wrong budget.
                            maxGuesses = resolveMaxGuesses(puzzle),
                        )
                    }
                    is DataResult.Error -> PuzzleUiState.Offline
                }
            }.fold(
                onSuccess = { state -> _uiState.value = state },
                onFailure = { e -> _uiState.value = PuzzleUiState.Failed(e.message ?: "Unknown error") },
            )
        }
    }

    /**
     * Resolves the server-authoritative guess budget from [puzzle]'s type-specific payload — never
     * a client-side constant (the real bug this replaced: the client silently enforced a hardcoded
     * 8-guess budget while the generator publishes `maxGuesses = 7`, so every real puzzle ran with a
     * mismatched budget). Only [PuzzleType.GUESS_CARD] has a payload shape known to this build;
     * any other/unknown type (not yet renderable — see `PuzzleScreen`'s `PuzzleType.UNKNOWN`
     * branch) falls back to [DEFAULT_MAX_GUESSES], which mirrors the generator's own
     * `DEFAULT_MAX_GUESSES` (`tools/puzzle-generator/stages/emit.mjs`) so the fallback stays
     * consistent with the real server default rather than reintroducing an arbitrary client value.
     */
    private fun resolveMaxGuesses(puzzle: Puzzle): Int = when (puzzle.type) {
        PuzzleType.GUESS_CARD ->
            json.decodeFromString(GuessCardPayload.serializer(), puzzle.payloadJson).maxGuesses
        else -> DEFAULT_MAX_GUESSES
    }

    fun onGuessQueryChange(query: String) {
        _guessQueryText.value = query
        _guessError.value = null
        guessQueryTrigger.value = query
    }

    /** Fills the guess input with an exact suggestion name (submission requires an exact match). */
    fun onSuggestionSelected(card: Card) {
        _guessQueryText.value = card.name
        _guessSuggestions.value = emptyList()
    }

    fun dismissGuessError() {
        _guessError.value = null
    }

    private suspend fun runGuessSearch(query: String) {
        if (query.length < MIN_SUGGESTION_QUERY_LENGTH) {
            _guessSuggestions.value = emptyList()
            _isSearchingSuggestions.value = false
            return
        }
        _isSearchingSuggestions.value = true
        when (val result = searchCardsUseCase(query)) {
            is DataResult.Success -> _guessSuggestions.value = result.data.cards.take(MAX_SUGGESTIONS)
            is DataResult.Error -> _guessSuggestions.value = emptyList()
        }
        _isSearchingSuggestions.value = false
    }

    /** Submits the current [guessQueryText] as a guess against the active [PuzzleUiState.Playing] puzzle. */
    fun submitGuess() {
        val current = _uiState.value as? PuzzleUiState.Playing ?: return
        val guessName = _guessQueryText.value.trim()
        if (guessName.isBlank() || _isSubmittingGuess.value) return

        viewModelScope.launch {
            _isSubmittingGuess.value = true
            _guessError.value = null
            submitPuzzleGuessUseCase(current.puzzle, guessName).fold(
                onSuccess = { guessResult -> onGuessResolved(current, guessResult) },
                onFailure = { e -> _guessError.value = e.message ?: "Could not find a card with that name" },
            )
            _isSubmittingGuess.value = false
        }
    }

    private suspend fun onGuessResolved(current: PuzzleUiState.Playing, guessResult: PuzzleGuessResult) {
        val updatedGuesses = current.guesses + guessResult
        val isGameOver = guessResult.isCorrect || updatedGuesses.size >= current.maxGuesses
        val now = Clock.System.now()

        val result = PuzzleResult(
            puzzleDate = current.puzzle.date,
            type = current.puzzle.type,
            attempts = updatedGuesses.size,
            solved = guessResult.isCorrect,
            perfect = guessResult.isCorrect && updatedGuesses.size == 1,
            elapsedMs = (now - sessionStartedAt).inWholeMilliseconds,
            startedAt = sessionStartedAt,
            guesses = updatedGuesses,
            completedAt = if (isGameOver) now else null,
        )

        // Persisted after EVERY guess (not just on solve) so a process death mid-puzzle resumes
        // correctly. The guess itself is already resolved client-side by this point, so a save
        // failure never blocks local progress — it only surfaces as a soft warning.
        runCatching { savePuzzleResultUseCase(result) }
            .onFailure { e -> _guessError.value = "Guess resolved, but couldn't save progress: ${e.message}" }

        _uiState.value = if (isGameOver) {
            PuzzleUiState.Solved(result)
        } else {
            current.copy(guesses = updatedGuesses)
        }

        if (isGameOver) {
            _guessQueryText.value = ""
            _guessSuggestions.value = emptyList()
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 400L
        const val MIN_SUGGESTION_QUERY_LENGTH = 2
        const val MAX_SUGGESTIONS = 8

        /**
         * Fallback guess budget for a puzzle type with no known payload shape to read
         * `maxGuesses` from (see [resolveMaxGuesses]) — mirrors the generator's own
         * `DEFAULT_MAX_GUESSES` (`tools/puzzle-generator/stages/emit.mjs`), never used for
         * [PuzzleType.GUESS_CARD], which always reads the real server-published value.
         */
        const val DEFAULT_MAX_GUESSES = 7
    }
}
