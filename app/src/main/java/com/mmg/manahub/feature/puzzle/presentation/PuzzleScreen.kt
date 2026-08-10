package com.mmg.manahub.feature.puzzle.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mmg.manahub.R
import com.mmg.manahub.core.model.puzzle.PuzzleResult
import com.mmg.manahub.core.model.puzzle.PuzzleType
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.FullErrorState
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing
import com.mmg.manahub.core.util.recordNonFatal
import org.koin.androidx.compose.koinViewModel

/**
 * Daily Puzzle screen — the sole navigation destination for
 * [com.mmg.manahub.app.navigation.Screen.DailyPuzzle]. Dispatches on [PuzzleUiState], and within
 * [PuzzleUiState.Playing] on [com.mmg.manahub.core.model.puzzle.Puzzle.type]: only
 * [PuzzleType.GUESS_CARD] has a real renderer today — any other type (including
 * [PuzzleType.UNKNOWN], the forward-compat fallback for a puzzle type this client build doesn't
 * recognize) degrades gracefully to a "not available in this version yet" message instead of
 * crashing or blank-screening.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PuzzleViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val guessQueryText by viewModel.guessQueryText.collectAsStateWithLifecycle()
    val guessSuggestions by viewModel.guessSuggestions.collectAsStateWithLifecycle()
    val isSearchingSuggestions by viewModel.isSearchingSuggestions.collectAsStateWithLifecycle()
    val isSubmittingGuess by viewModel.isSubmittingGuess.collectAsStateWithLifecycle()
    val guessError by viewModel.guessError.collectAsStateWithLifecycle()

    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography

    // Screen-entry breadcrumb (no PII) — matches CardDetailScreen/HomeScreen/DeckStudioScreen's
    // convention. Fires once per entry (keyed on Unit).
    LaunchedEffect(Unit) {
        FirebaseCrashlytics.getInstance().log("screen_viewed: daily_puzzle")
    }

    Scaffold(
        containerColor = mc.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.daily_puzzle_title),
                        style = ty.titleLarge,
                        color = mc.textPrimary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            tint = mc.textSecondary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = mc.backgroundSecondary),
            )
        },
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val state = uiState) {
                PuzzleUiState.Loading -> PuzzleLoadingContent()

                PuzzleUiState.Offline -> FullErrorState(
                    message = stringResource(R.string.daily_puzzle_offline_message),
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = viewModel::retryLoad,
                )

                is PuzzleUiState.Failed -> FullErrorState(
                    message = state.message,
                    retryLabel = stringResource(R.string.action_retry),
                    onRetry = viewModel::retryLoad,
                )

                is PuzzleUiState.Playing -> when (state.puzzle.type) {
                    PuzzleType.GUESS_CARD -> GuessCardWidgetContent(
                        state = state,
                        guessQueryText = guessQueryText,
                        guessSuggestions = guessSuggestions,
                        isSearchingSuggestions = isSearchingSuggestions,
                        isSubmittingGuess = isSubmittingGuess,
                        guessError = guessError,
                        onQueryChange = viewModel::onGuessQueryChange,
                        onSuggestionSelected = viewModel::onSuggestionSelected,
                        onSubmitGuess = viewModel::submitGuess,
                        onDismissError = viewModel::dismissGuessError,
                    )
                    // Forward-compat: a puzzle type this client build doesn't know how to render yet
                    // (see PuzzleType.fromWire's KDoc) degrades gracefully instead of crashing. Should
                    // be ~0 occurrences in Phase 0/1 (only GUESS_CARD ships) -- reaching this branch
                    // signals a generator/client type mismatch, so it backs a non-fatal.
                    PuzzleType.UNKNOWN -> {
                        LaunchedEffect(state.puzzle.date) {
                            FirebaseCrashlytics.getInstance().setCustomKey(
                                "puzzle_date",
                                state.puzzle.date.toString(),
                            )
                            recordNonFatal("puzzle_unsupported_type_reached")
                        }
                        FullErrorState(
                            message = stringResource(R.string.daily_puzzle_type_unsupported),
                        )
                    }
                }

                is PuzzleUiState.Solved -> PuzzleSolvedContent(result = state.result)
            }
        }
    }
}

@Composable
private fun PuzzleLoadingContent(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        MagicLoadingSpinner()
    }
}

/**
 * Post-attempt summary: outcome banner + full guess history. Covers BOTH outcomes
 * ([PuzzleResult.solved] true or false) — a loss never reveals the answer's name/image (the
 * puzzle's payload, which carries [com.mmg.manahub.core.model.puzzle.GuessCardPayload
 * .canonicalScryfallId], is not retained past [PuzzleUiState.Playing], and [PuzzleResult] itself
 * never stores the plaintext answer per ADR-006 Decision 4) — only the guess history is shown. A
 * WIN can safely name the answer from the winning guess itself
 * ([com.mmg.manahub.core.model.puzzle.PuzzleGuessResult.guessedName] on the correct guess, which by
 * definition matches the answer).
 */
@Composable
private fun PuzzleSolvedContent(result: PuzzleResult, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing
    val winningGuessName = result.guesses.lastOrNull { it.isCorrect }?.guessedName

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item(key = "summary") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = if (result.solved) Icons.Default.EmojiEvents else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (result.solved) mc.lifePositive else mc.textSecondary,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    text = stringResource(
                        if (result.solved) R.string.daily_puzzle_solved_title
                        else R.string.daily_puzzle_not_solved_title,
                    ),
                    style = ty.titleLarge,
                    color = mc.textPrimary,
                )
                Text(
                    text = stringResource(R.string.daily_puzzle_attempts_summary, result.attempts),
                    style = ty.bodyMedium,
                    color = mc.textSecondary,
                )
                if (winningGuessName != null) {
                    CardName(
                        name = winningGuessName,
                        style = ty.titleMedium,
                        color = mc.primaryAccent,
                    )
                }
            }
        }

        if (result.guesses.isNotEmpty()) {
            item(key = "history_title") {
                Text(
                    text = stringResource(R.string.daily_puzzle_guess_history_title),
                    style = ty.titleMedium,
                    color = mc.textPrimary,
                )
            }
            itemsIndexed(result.guesses.asReversed(), key = { index, _ -> index }) { _, guess ->
                GuessResultCard(guess)
            }
        }
    }
}
