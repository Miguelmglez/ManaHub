package com.mmg.manahub.core.domain.usecase.card

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsRepository
import com.mmg.manahub.core.domain.repository.CardStrategyTagsResult
import com.mmg.manahub.core.model.CardTag

/**
 * Best-effort, non-blocking enrichment of a VIEWED card's auto-generated tags from the offline
 * pipeline's precomputed [CardStrategyTagsRepository] (Deck Engine Unification plan, D8, §5
 * Phase 5c — "card detail view" read point).
 *
 * This use case is intentionally ADDITIVE-ONLY: it never removes a tag the on-device
 * [com.mmg.manahub.core.data.tagging.StrategyAnalyzer] already confirmed (which ran when the card
 * was first cached — see [com.mmg.manahub.core.domain.usecase.card.ComputeCardTagsUseCase] and its
 * background sibling in `:app`'s `CardRepositoryImpl`); it only ever ADDS precomputed tags on top,
 * via [CardRepository.updateCardTags]. This closes the coverage gap for cards that were cached
 * BEFORE this table existed, or before the offline pipeline had processed them — every card the
 * user opens in Card Detail gets one chance to catch up, regardless of when it was first cached.
 *
 * Never throws: a repository failure (offline, table miss, decode error) is swallowed and simply
 * means "nothing to add this time" — the caller (a ViewModel) should invoke this fire-and-forget,
 * exactly like the existing `oracleId.isBlank()` background-refresh precedent in
 * `CardDetailViewModel.loadCard()`.
 *
 * RUN 7b (BUG 2 fix): the actual merge now happens via [CardRepository.unionCardTags] — an
 * atomic read-union-write inside a single Room transaction — instead of computing the union HERE
 * from [currentTags] and plain-overwriting via `updateCardTags`. [currentTags] is a point-in-time
 * snapshot (captured by the caller before this suspend function even runs) that can go stale if a
 * concurrent background job (`CardRepositoryImpl.scheduleTagResolution`, fired on the SAME
 * cache-miss card view) writes to the same `tags` column in between — a use-case-level merge off
 * that stale snapshot would silently discard whatever the concurrent job just wrote. [currentTags]
 * is kept only as a cheap, SAFE fast-path: it can only ever cause this use case to skip a call that
 * `unionCardTags` would have no-op'd anyway, never cause a lost update.
 */
class RefreshCardStrategyTagsUseCase(
    private val cardRepository: CardRepository,
    private val cardStrategyTagsRepository: CardStrategyTagsRepository,
) {
    suspend operator fun invoke(scryfallId: String, oracleId: String, currentTags: List<CardTag>) {
        if (oracleId.isBlank()) return
        val result = runCatching { cardStrategyTagsRepository.getStrategyTags(oracleId) }.getOrNull()
        val remoteTags = (result as? CardStrategyTagsResult.Found)?.tags.orEmpty()
        if (remoteTags.isEmpty()) return
        // Fast-path only (see class KDoc): skip the call when this snapshot already covers every
        // remote tag. A stale snapshot just means an extra, harmless no-op union call — never data
        // loss, since the real merge is atomic inside unionCardTags.
        val currentTagSet = currentTags.toSet()
        if (currentTagSet.containsAll(remoteTags)) return
        runCatching { cardRepository.unionCardTags(scryfallId, remoteTags) }
    }
}
