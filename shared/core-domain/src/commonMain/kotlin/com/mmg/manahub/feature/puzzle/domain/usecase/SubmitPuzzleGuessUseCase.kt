package com.mmg.manahub.feature.puzzle.domain.usecase

import com.mmg.manahub.core.common.sha256Hex
import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.puzzle.GuessCardPayload
import com.mmg.manahub.core.model.puzzle.Puzzle
import com.mmg.manahub.core.model.puzzle.PuzzleAttemptFeedback
import com.mmg.manahub.core.model.puzzle.PuzzleAttemptFeedback.Comparison
import com.mmg.manahub.core.model.puzzle.PuzzleAttemptFeedback.MatchLevel
import com.mmg.manahub.core.model.puzzle.PuzzleCardAttributes
import com.mmg.manahub.core.model.puzzle.PuzzleGuessResult
import com.mmg.manahub.core.model.puzzle.PuzzleNameNormalizer
import com.mmg.manahub.core.model.puzzle.PuzzleType
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/**
 * Resolves a submitted card-name guess against a [PuzzleType.GUESS_CARD] puzzle: fetches the
 * guessed card (by exact name, via [CardRepository.getCardByExactName] — matching by name rather
 * than a cached oracle id, since the answer card may never have been searched/cached by this
 * device before), determines correctness by comparing a salted hash (never by comparing the
 * plaintext answer name client-side), and computes per-attribute feedback for the guess UI.
 */
class SubmitPuzzleGuessUseCase(
    private val cardRepository: CardRepository,
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param puzzle today's puzzle, as returned by [GetTodayPuzzleUseCase]. Must have
     *   [Puzzle.type] == [PuzzleType.GUESS_CARD] — any other type fails with
     *   [IllegalArgumentException] since this use case only knows how to interpret a
     *   [GuessCardPayload].
     * @param guessName the raw, user-entered card name.
     * @return [Result.success] with the resolved [PuzzleGuessResult] (feedback is always computed,
     *   regardless of correctness, so the UI has attribute-level chips to render either way), or
     *   [Result.failure] when [puzzle] is not a [PuzzleType.GUESS_CARD] puzzle, [guessName]
     *   resolves to no card, or the payload fails to parse.
     */
    suspend operator fun invoke(puzzle: Puzzle, guessName: String): Result<PuzzleGuessResult> {
        if (puzzle.type != PuzzleType.GUESS_CARD) {
            return Result.failure(
                IllegalArgumentException("SubmitPuzzleGuessUseCase only supports GUESS_CARD puzzles, got ${puzzle.type}")
            )
        }

        val payload = runCatching { json.decodeFromString(GuessCardPayload.serializer(), puzzle.payloadJson) }
            .getOrElse { e -> return Result.failure(e) }

        val guessedCard = cardRepository.getCardByExactName(guessName)
            .getOrElse { e -> return Result.failure(e) }

        val normalizedGuess = PuzzleNameNormalizer.normalize(guessedCard.name)
        val guessHash = sha256Hex(normalizedGuess + payload.dailySalt)
        val isCorrect = guessHash == payload.answerNameHash

        val feedback = buildFeedback(guessedCard, payload.attributes)

        return Result.success(
            PuzzleGuessResult(
                guessedName = guessedCard.name,
                guessedScryfallId = guessedCard.scryfallId,
                feedback = feedback,
                isCorrect = isCorrect,
                guessedAt = Clock.System.now(),
            )
        )
    }

    private fun buildFeedback(guessed: Card, answer: PuzzleCardAttributes): PuzzleAttemptFeedback =
        PuzzleAttemptFeedback(
            cmc = compareNumeric(guessed.cmc, answer.cmc),
            colorIdentity = compareColorIdentity(guessed.colorIdentity, answer.colorIdentity),
            rarity = compareExact(guessed.rarity, answer.rarity),
            typeLine = compareTypeLine(guessed.typeLine, answer.typeLine),
            setCode = compareExact(guessed.setCode, answer.setCode),
            releasedYear = compareNumeric(releasedYear(guessed.releasedAt), answer.releasedYear),
            power = comparePowerToughness(guessed.power, answer.power),
            toughness = comparePowerToughness(guessed.toughness, answer.toughness),
        )

    private fun compareNumeric(guessedValue: Double, answerValue: Double): Comparison = when {
        guessedValue > answerValue -> Comparison.HIGHER
        guessedValue < answerValue -> Comparison.LOWER
        else -> Comparison.MATCH
    }

    private fun compareNumeric(guessedValue: Int, answerValue: Int): Comparison = when {
        guessedValue > answerValue -> Comparison.HIGHER
        guessedValue < answerValue -> Comparison.LOWER
        else -> Comparison.MATCH
    }

    /** Extracts the 4-digit year prefix from a Scryfall `released_at` date (`yyyy-MM-dd`). */
    private fun releasedYear(releasedAt: String): Int =
        releasedAt.take(4).toIntOrNull() ?: 0

    private fun compareColorIdentity(guessed: List<String>, answer: List<String>): MatchLevel {
        val guessedSet = guessed.toSet()
        val answerSet = answer.toSet()
        return when {
            guessedSet == answerSet -> MatchLevel.MATCH
            guessedSet.intersect(answerSet).isNotEmpty() -> MatchLevel.PARTIAL
            else -> MatchLevel.NONE
        }
    }

    private fun compareExact(guessed: String, answer: String): MatchLevel =
        if (guessed.equals(answer, ignoreCase = true)) MatchLevel.MATCH else MatchLevel.NONE

    /**
     * MATCH on an exact (case-insensitive) type line; PARTIAL when the two type lines share at
     * least one type/subtype token (split on whitespace and the em-dash separator); NONE otherwise.
     */
    private fun compareTypeLine(guessed: String, answer: String): MatchLevel {
        if (guessed.equals(answer, ignoreCase = true)) return MatchLevel.MATCH
        val guessedTokens = tokenizeTypeLine(guessed)
        val answerTokens = tokenizeTypeLine(answer)
        return if (guessedTokens.intersect(answerTokens).isNotEmpty()) MatchLevel.PARTIAL else MatchLevel.NONE
    }

    private fun tokenizeTypeLine(typeLine: String): Set<String> =
        typeLine.lowercase()
            .split(Regex("[\\s—-]+"))
            .filter { it.isNotBlank() }
            .toSet()

    /**
     * Numeric power/toughness comparison. Returns null when either side is non-numeric (`*`, `X`,
     * or absent — e.g. a non-creature card) — the UI shows no comparison chip in that case rather
     * than a misleading result.
     */
    private fun comparePowerToughness(guessed: String?, answer: String?): Comparison? {
        val guessedValue = guessed?.toDoubleOrNull() ?: return null
        val answerValue = answer?.toDoubleOrNull() ?: return null
        return compareNumeric(guessedValue, answerValue)
    }
}
