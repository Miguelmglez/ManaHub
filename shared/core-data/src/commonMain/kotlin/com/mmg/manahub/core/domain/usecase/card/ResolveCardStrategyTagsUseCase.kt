package com.mmg.manahub.core.domain.usecase.card

import com.mmg.manahub.core.data.remote.edhrec.CARD_TAG_KEY_TO_THEME_ID
import com.mmg.manahub.core.data.remote.edhrec.EdhrecCardTagEnrichmentSourceContract
import com.mmg.manahub.core.data.usecase.card.SuggestTagsUseCase
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsResult
import com.mmg.manahub.core.domain.repository.CardStrategyTagsSubmission
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.feature.decks.domain.engine.ThemeId

/**
 * The BACKGROUND-ONLY tag resolution path used by `:app`'s `CardRepositoryImpl` when it first
 * caches a card (Deck Engine Unification plan, D8, §5 Phase 5c — "card add to collection" read
 * point). Deliberately distinct from [com.mmg.manahub.core.domain.usecase.card
 * .RefreshCardStrategyTagsUseCase] (the CardDetail-facing, `CardRepository`-persisting sibling):
 * this one is consumed by `CardRepositoryImpl` itself, which cannot depend on the public
 * `CardRepository` interface it implements (that would be circular) — so it returns a full
 * [ComputeCardTagsUseCase.Result] for the caller to persist directly via its own DAO, instead of
 * writing anything itself.
 *
 * **STRICT FALLBACK (plan §8a addendum, 2026-07-21) — superseded the original RUN 6 UNION
 * strategy.** With the production `card_strategy_tags` table populated (38k+ real rows), Supabase
 * is now the PRIMARY source and on-device analysis is a true fallback, not a permanent duplicate
 * computation:
 * 1. **Precomputed row found** → use it EXCLUSIVELY. The on-device [computeCardTags]
 *    ([com.mmg.manahub.core.data.tagging.StrategyAnalyzer]/`SuggestTagsUseCase`) is skipped
 *    entirely — this is also the fix for "adding many cards freezes the app," since most adds now
 *    resolve as a cheap cache/table hit with zero local CPU work. User-confirmed tags already on
 *    the card ([existingTagsJson]) are still preserved (a cheap JSON-parse union, no analyzer run)
 *    — never silently dropped just because the analyzer itself was skipped. No suggested tags are
 *    produced on a hit ([CardStrategyTagsResult.Found] only ever carries auto-CONFIRMED tags).
 * 2. **Genuine miss** ([CardStrategyTagsResult.NotFound]/[CardStrategyTagsResult.Error]/blank
 *    `oracleId`) → run the on-device rule engine as before, PLUS a lightweight EDHREC shortlist
 *    check ([edhrecEnrichment]) that can promote a low-confidence SUGGESTED tag to CONFIRMED when
 *    a matching EDHREC theme page independently lists this exact card — see
 *    [com.mmg.manahub.core.data.remote.edhrec.CARD_TAG_KEY_TO_THEME_ID]'s KDoc for why this is a
 *    shortlist verification rather than a true per-card EDHREC lookup (no such endpoint exists).
 *    The device then pushes its computed result BACK to `card_strategy_tags`
 *    ([CardStrategyTagsRepository.submitStrategyTags]) so the table converges toward completeness
 *    from real usage — never blocking on that write's outcome (the repository itself never
 *    throws).
 */
class ResolveCardStrategyTagsUseCase(
    private val computeCardTags: ComputeCardTagsUseCase,
    private val cardStrategyTagsRepository: CardStrategyTagsRepository,
    private val edhrecEnrichment: EdhrecCardTagEnrichmentSourceContract,
) {
    suspend operator fun invoke(
        card: Card,
        existingTagsJson: String?,
        autoThreshold: Float = SuggestTagsUseCase.DEFAULT_AUTO_THRESHOLD,
        suggestThreshold: Float = SuggestTagsUseCase.DEFAULT_SUGGEST_THRESHOLD,
    ): ComputeCardTagsUseCase.Result {
        if (card.oracleId.isNotBlank()) {
            val remote = runCatching { cardStrategyTagsRepository.getStrategyTags(card.oracleId) }.getOrNull()
            if (remote is CardStrategyTagsResult.Found) {
                return remote.toResolvedResult(existingTagsJson)
            }
        }

        // Genuine miss (or blank oracleId, which can never have a precomputed row): fall back to
        // the on-device rule engine, always.
        val onDevice = computeCardTags(card, existingTagsJson, autoThreshold, suggestThreshold)
        if (card.oracleId.isBlank()) return onDevice

        val edhrecConfirmed = runCatching {
            val candidates = onDevice.suggestedTags
                .sortedByDescending { it.confidence }
                .mapNotNull { CARD_TAG_KEY_TO_THEME_ID[it.tag.key] }
                .distinct()
            edhrecEnrichment.confirmThemes(card.name, candidates)
        }.getOrDefault(emptyMap())

        val finalResult = onDevice.promoteEdhrecConfirmed(edhrecConfirmed)

        runCatching {
            cardStrategyTagsRepository.submitStrategyTags(
                oracleId = card.oracleId,
                submission = CardStrategyTagsSubmission(
                    tags = finalResult.confirmedTags.map { it.key },
                    themes = edhrecConfirmed.mapKeys { (themeId, _) -> themeId.name },
                ),
            )
        }

        return finalResult
    }

    /** Precomputed-hit path: union the remote's confirmed tags with whatever the user had already
     *  confirmed on this card — never re-run the analyzer, never produce suggestions. */
    private fun CardStrategyTagsResult.Found.toResolvedResult(existingTagsJson: String?): ComputeCardTagsUseCase.Result {
        val existingConfirmed = existingTagsJson
            ?.takeIf { it.isNotBlank() && it != "[]" }
            ?.toTagList()
            .orEmpty()
        return ComputeCardTagsUseCase.Result(
            confirmedTags = (existingConfirmed + tags).distinct(),
            suggestedTags = emptyList(),
        )
    }

    /** Miss path: promotes any on-device SUGGESTED tag that [edhrecConfirmed] independently
     *  verified into the CONFIRMED set, removing it from the remaining suggestions. */
    private fun ComputeCardTagsUseCase.Result.promoteEdhrecConfirmed(
        edhrecConfirmed: Map<ThemeId, Float>,
    ): ComputeCardTagsUseCase.Result {
        if (edhrecConfirmed.isEmpty()) return this
        val promotedKeys = CARD_TAG_KEY_TO_THEME_ID.filterValues { it in edhrecConfirmed.keys }.keys
        val promoted = suggestedTags.filter { it.tag.key in promotedKeys }
        if (promoted.isEmpty()) return this
        return copy(
            confirmedTags = (confirmedTags + promoted.map { it.tag }).distinct(),
            suggestedTags = suggestedTags.filterNot { it.tag.key in promotedKeys },
        )
    }
}
