package com.mmg.manahub.feature.puzzle.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
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
import com.mmg.manahub.core.util.recordSafeNonFatal

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

    /**
     * Static-call convention (matches `DeckWizardViewModel`/`HomeViewModel`) — this ViewModel is
     * Android-only (Daily Puzzle Phase 0/1 per ADR-006), so no `CrashReporter` DI is needed here.
     * Repo-layer failures (Worker fetch, resume-row decode) are reported via the injected
     * `CrashReporter` in `PuzzleRepositoryImpl` instead — see `crashlytics-ux-auditor`'s
     * `audit_daily_puzzle.md`.
     */
    private val crashlytics = FirebaseCrashlytics.getInstance()

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
                        // Server-authoritative — a corrupt/malformed payload fails the load (caught
                        // by the outer runCatching below) rather than silently starting the attempt
                        // under the wrong budget.
                        val maxGuesses = resolveMaxGuesses(puzzle)

                        if (resumable != null && resumable.guesses.size >= maxGuesses) {
                            // A local row saved under a DIFFERENT (larger) budget than the server
                            // currently publishes is already exhausted — e.g. Batch B3's hardcoded-
                            // 8-vs-server-7 mismatch left rows with 7 guesses and no completedAt.
                            // Route straight to the terminal state instead of re-showing Playing
                            // with an already-spent budget (which would render an enabled submit
                            // button for an attempt that is, in truth, already over). By
                            // construction the last saved guess here can never have been correct —
                            // a correct guess always sets completedAt regardless of budget — so this
                            // row's `solved` is always false.
                            val closedOut = resumable.copy(completedAt = Clock.System.now())
                            // Best-effort persistence of the correction — never blocks reaching the
                            // terminal state, mirrors onGuessResolved's own save-failure tolerance.
                            runCatching { savePuzzleResultUseCase(closedOut) }
                            PuzzleUiState.Solved(closedOut)
                        } else {
                            PuzzleUiState.Playing(
                                puzzle = puzzle,
                                guesses = resumable?.guesses.orEmpty(),
                                resumedFromCache = resumable != null,
                                maxGuesses = maxGuesses,
                            )
                        }
                    }
                    is DataResult.Error -> {
                        crashlytics.log("puzzle_offline_state_shown")
                        PuzzleUiState.Offline
                    }
                }
            }.fold(
                onSuccess = { state -> _uiState.value = state },
                onFailure = { e ->
                    crashlytics.setCustomKey("puzzle_load_failure_source", e::class.simpleName ?: "Unknown")
                    recordSafeNonFatal("puzzle_load_unexpected_failure", e)
                    _uiState.value = PuzzleUiState.Failed(e.message ?: "Unknown error")
                },
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
     *
     * A decoded [GuessCardPayload.maxGuesses] outside [VALID_MAX_GUESSES_RANGE] (the generator's own
     * `schemaValidate.mjs` bound, `tools/puzzle-generator/stages/schemaValidate.mjs`) throws, which
     * the caller's `runCatching` turns into a [PuzzleUiState.Failed] load — a corrupt/out-of-range
     * payload fails to load rather than silently starting an instantly-over or effectively-infinite
     * puzzle, same framing as the malformed-JSON case above.
     */
    private fun resolveMaxGuesses(puzzle: Puzzle): Int = when (puzzle.type) {
        PuzzleType.GUESS_CARD -> {
            val maxGuesses = json.decodeFromString(GuessCardPayload.serializer(), puzzle.payloadJson).maxGuesses
            require(maxGuesses in VALID_MAX_GUESSES_RANGE) {
                "GuessCardPayload.maxGuesses ($maxGuesses) is outside the valid range $VALID_MAX_GUESSES_RANGE"
            }
            maxGuesses
        }
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
                onFailure = { e ->
                    crashlytics.setCustomKey("puzzle_guess_submit_error_type", e::class.simpleName ?: "Unknown")
                    recordSafeNonFatal("puzzle_guess_submit_failed", e)
                    _guessError.value = e.message ?: "Could not find a card with that name"
                },
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
        // failure never blocks local progress — it only surfaces as a soft warning. A solved puzzle
        // that fails to save here is a genuine data-loss surface (lost XP/streak/achievement, since
        // PuzzleRepositoryImpl.savePuzzleResult only emits ProgressionEvent.PuzzleSolved AFTER a
        // successful write) — record it as a non-fatal, not just a UI warning, so it's actually
        // visible in aggregate.
        runCatching { savePuzzleResultUseCase(result) }
            .onFailure { e ->
                crashlytics.setCustomKey("puzzle_date", result.puzzleDate.toString())
                crashlytics.setCustomKey("puzzle_attempts_used", result.attempts)
                crashlytics.setCustomKey("puzzle_is_game_over", isGameOver)
                recordSafeNonFatal("puzzle_progress_save_failed", e)
                _guessError.value = "Guess resolved, but couldn't save progress: ${e.message}"
            }

        _uiState.value = if (isGameOver) {
            crashlytics.log(if (result.solved) "puzzle_solved" else "puzzle_failed")
            crashlytics.setCustomKey("puzzle_attempts_used", result.attempts)
            PuzzleUiState.Solved(result)
        } else {
            current.copy(guesses = updatedGuesses)
        }

        // Clear the input after EVERY resolved guess, not just on game-over — otherwise the
        // just-rejected name stays in the field and a second tap on Submit re-submits an identical
        // guess, burning a budget slot on an accidental duplicate.
        _guessQueryText.value = ""
        _guessSuggestions.value = emptyList()
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

        /**
         * The generator's own valid range for [GuessCardPayload.maxGuesses]
         * (`tools/puzzle-generator/stages/schemaValidate.mjs`: `payload.maxGuesses must be in [1, 20]`).
         * A decoded value outside this range is treated as a corrupt payload — see [resolveMaxGuesses].
         */
        val VALID_MAX_GUESSES_RANGE = 1..20
    }
}
