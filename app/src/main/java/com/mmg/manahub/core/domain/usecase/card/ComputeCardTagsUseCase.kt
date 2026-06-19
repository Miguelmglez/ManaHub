package com.mmg.manahub.core.domain.usecase.card

import com.mmg.manahub.core.data.local.mapper.toTagList
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.SuggestedTag
import javax.inject.Inject

/**
 * Computes the final (tagsJson, suggestedTagsJson) pair to persist for a card.
 *
 * Rules:
 * - Runs [SuggestTagsUseCase] on the incoming [Card] using the supplied thresholds.
 * - If [existingTagsJson] is non-empty (user has already confirmed tags), those are
 *   merged with the engine's new confirmed tags — user choices are never discarded.
 * - Returns a [Result] with the merged confirmed tags and fresh suggestions.
 *
 * Pure logic — no I/O, no Android imports. Safe to call on [kotlinx.coroutines.Dispatchers.Default].
 */
class ComputeCardTagsUseCase @Inject constructor(
    private val suggestTags: SuggestTagsUseCase,
) {

    data class Result(
        val confirmedTags: List<CardTag>,
        val suggestedTags: List<SuggestedTag>,
    )

    /**
     * @param card              The freshly-fetched card from Scryfall.
     * @param existingTagsJson  The current `tags` column value for this card in Room, or null/blank
     *                          when the card is new. Non-empty values are treated as user-confirmed
     *                          tags and merged with the engine output.
     * @param autoThreshold     Confidence threshold above which a tag is auto-confirmed.
     * @param suggestThreshold  Confidence threshold above which a tag is surfaced as a suggestion.
     */
    operator fun invoke(
        card: Card,
        existingTagsJson: String?,
        autoThreshold: Float = SuggestTagsUseCase.DEFAULT_AUTO_THRESHOLD,
        suggestThreshold: Float = SuggestTagsUseCase.DEFAULT_SUGGEST_THRESHOLD,
    ): Result {
        val engineResult = suggestTags(card, autoThreshold, suggestThreshold)

        val keepExisting = !existingTagsJson.isNullOrBlank() && existingTagsJson != "[]"
        val mergedConfirmed = if (keepExisting) {
            // Merge: preserve user choices and add any new engine-confirmed tags not already present.
            (existingTagsJson!!.toTagList() + engineResult.confirmed).distinct()
        } else {
            engineResult.confirmed
        }

        return Result(
            confirmedTags = mergedConfirmed,
            suggestedTags = engineResult.suggested,
        )
    }
}
