package com.mmg.manahub.feature.puzzle.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mmg.manahub.R
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.puzzle.GuessCardPayload
import com.mmg.manahub.core.model.puzzle.PuzzleAttemptFeedback.Comparison
import com.mmg.manahub.core.model.puzzle.PuzzleAttemptFeedback.MatchLevel
import com.mmg.manahub.core.model.puzzle.PuzzleGuessResult
import com.mmg.manahub.core.ui.components.CardName
import com.mmg.manahub.core.ui.components.MagicLoadingSpinner
import com.mmg.manahub.core.ui.components.InlineErrorState
import com.mmg.manahub.core.ui.components.MagicCtaButton
import com.mmg.manahub.core.ui.components.MagicCtaColor
import com.mmg.manahub.core.ui.theme.CardShape
import com.mmg.manahub.core.ui.theme.ChipShape
import com.mmg.manahub.core.ui.theme.magicColors
import com.mmg.manahub.core.ui.theme.magicTypography
import com.mmg.manahub.core.ui.theme.spacing

/**
 * Renderer for a [com.mmg.manahub.core.model.puzzle.PuzzleType.GUESS_CARD] puzzle: a debounced
 * card-name search box (no reusable search-field component exists in this codebase — this mirrors
 * AddCard's own inline `OutlinedTextField` pattern), a submit action, and the guess history so far.
 *
 * Note: [GuessCardPayload] is not read directly here — this composable only needs the guess
 * history + input state; attribute comparison already happened in
 * [com.mmg.manahub.feature.puzzle.domain.usecase.SubmitPuzzleGuessUseCase].
 */
@Composable
fun GuessCardWidgetContent(
    state: PuzzleUiState.Playing,
    guessQueryText: String,
    guessSuggestions: List<Card>,
    isSearchingSuggestions: Boolean,
    isSubmittingGuess: Boolean,
    guessError: String?,
    onQueryChange: (String) -> Unit,
    onSuggestionSelected: (Card) -> Unit,
    onSubmitGuess: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (state.resumedFromCache) {
            Text(
                text = stringResource(R.string.daily_puzzle_resumed_badge),
                style = ty.labelMedium,
                color = mc.secondaryAccent,
            )
        }

        Text(
            text = stringResource(R.string.daily_puzzle_attempts_label, state.guesses.size),
            style = ty.bodySmall,
            color = mc.textSecondary,
        )

        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            OutlinedTextField(
                value = guessQueryText,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.daily_puzzle_guess_hint)) },
                singleLine = true,
                trailingIcon = {
                    if (isSearchingSuggestions) {
                        MagicLoadingSpinner(modifier = Modifier.size(20.dp))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = mc.primaryAccent,
                    unfocusedBorderColor = mc.textDisabled,
                    focusedTextColor = mc.textPrimary,
                    unfocusedTextColor = mc.textPrimary,
                    cursorColor = mc.primaryAccent,
                ),
            )

            if (guessSuggestions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(mc.surface),
                ) {
                    guessSuggestions.forEach { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClickLabel = suggestion.name) { onSuggestionSelected(suggestion) }
                                .padding(horizontal = spacing.md, vertical = spacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CardName(name = suggestion.name, style = ty.bodyMedium, color = mc.textPrimary)
                        }
                    }
                }
            }
        }

        if (guessError != null) {
            InlineErrorState(
                message = guessError,
                retryLabel = stringResource(R.string.action_dismiss),
                onRetry = onDismissError,
            )
        }

        MagicCtaButton(
            onClick = onSubmitGuess,
            text = stringResource(R.string.daily_puzzle_guess_submit),
            enabled = guessQueryText.isNotBlank() && !isSubmittingGuess,
            isLoading = isSubmittingGuess,
            color = MagicCtaColor.Primary,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.guesses.isNotEmpty()) {
            Text(
                text = stringResource(R.string.daily_puzzle_guess_history_title),
                style = ty.titleMedium,
                color = mc.textPrimary,
            )
            GuessHistoryList(guesses = state.guesses)
        }
    }
}

/**
 * Scrollable history of submitted guesses, newest first — reused by both the live
 * [GuessCardWidgetContent] and the post-game [PuzzleSolvedContent] summary. Keyed by index (not
 * [PuzzleGuessResult.guessedScryfallId]): a player could in principle guess-search the same card
 * twice before the puzzle ends, so the id alone is not guaranteed unique.
 */
@Composable
fun GuessHistoryList(guesses: List<PuzzleGuessResult>, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    val reversed = remember(guesses) { guesses.asReversed() }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        itemsIndexed(reversed, key = { index, _ -> guesses.size - 1 - index }) { _, guess ->
            GuessResultCard(guess)
        }
    }
}

/**
 * A single guess row: card name + [FlowRow] of per-attribute feedback chips. Public so
 * [PuzzleSolvedContent] can render the same rich row inside ITS OWN `LazyColumn` post-game — never
 * by nesting [GuessHistoryList] (itself a `LazyColumn`) inside another `LazyColumn`.
 */
@Composable
fun GuessResultCard(guess: PuzzleGuessResult, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (guess.isCorrect) mc.lifePositive.copy(alpha = 0.12f) else mc.surface)
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        CardName(
            name = guess.guessedName,
            style = ty.bodyLarge,
            color = mc.textPrimary,
            fontWeight = if (guess.isCorrect) FontWeight.Bold else null,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            ComparisonChip(stringResource(R.string.daily_puzzle_attr_cmc), guess.feedback.cmc)
            MatchLevelChip(stringResource(R.string.daily_puzzle_attr_color), guess.feedback.colorIdentity)
            MatchLevelChip(stringResource(R.string.daily_puzzle_attr_rarity), guess.feedback.rarity)
            MatchLevelChip(stringResource(R.string.daily_puzzle_attr_type), guess.feedback.typeLine)
            MatchLevelChip(stringResource(R.string.daily_puzzle_attr_set), guess.feedback.setCode)
            ComparisonChip(stringResource(R.string.daily_puzzle_attr_year), guess.feedback.releasedYear)
            guess.feedback.power?.let { ComparisonChip(stringResource(R.string.daily_puzzle_attr_power), it) }
            guess.feedback.toughness?.let { ComparisonChip(stringResource(R.string.daily_puzzle_attr_toughness), it) }
        }
    }
}

@Composable
private fun ComparisonChip(label: String, comparison: Comparison, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val (icon, tint) = when (comparison) {
        Comparison.HIGHER -> Icons.Default.ArrowUpward to mc.textSecondary
        Comparison.LOWER -> Icons.Default.ArrowDownward to mc.textSecondary
        Comparison.MATCH -> Icons.Default.Check to mc.lifePositive
    }
    FeedbackAttributeChip(label = label, icon = icon, tint = tint, modifier = modifier)
}

@Composable
private fun MatchLevelChip(label: String, matchLevel: MatchLevel, modifier: Modifier = Modifier) {
    val mc = MaterialTheme.magicColors
    val (icon, tint) = when (matchLevel) {
        MatchLevel.MATCH -> Icons.Default.Check to mc.lifePositive
        MatchLevel.PARTIAL -> Icons.Default.Remove to mc.goldMtg
        MatchLevel.NONE -> Icons.Default.Close to mc.lifeNegative
    }
    FeedbackAttributeChip(label = label, icon = icon, tint = tint, modifier = modifier)
}

@Composable
private fun FeedbackAttributeChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val mc = MaterialTheme.magicColors
    val ty = MaterialTheme.magicTypography
    val spacing = MaterialTheme.spacing

    Row(
        modifier = modifier
            .clip(ChipShape)
            .background(mc.surfaceVariant)
            .padding(horizontal = spacing.sm, vertical = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(text = label, style = ty.labelSmall, color = mc.textSecondary)
    }
}
