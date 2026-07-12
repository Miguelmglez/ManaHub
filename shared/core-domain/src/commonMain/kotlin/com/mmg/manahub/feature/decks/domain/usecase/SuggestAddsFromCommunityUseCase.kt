package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.repository.CardRepository
import com.mmg.manahub.core.model.AggregateCardEntry
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DataResult
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One community (Motor B) add suggestion — a card ranked by the aggregate's own
 * staple-dampened [synergy] (EDHREC-native for Commander, Worker-computed for 60-card; see
 * `docs/adr/ADR-004-community-api-contracts.md`), never by [com.mmg.manahub.feature.decks.domain.engine.DeckScorer].
 *
 * Deliberately a NEW, lightweight type rather than reusing [AddSuggestion]/[com.mmg.manahub.feature.decks.domain.engine.CardFit]:
 * community data carries no [com.mmg.manahub.feature.decks.domain.engine.ScoreComponents] (curve/
 * power/redundancy are DeckScorer-only concepts) — forcing it into that shape would mean inventing
 * fake component values. [com.mmg.manahub.feature.decks.presentation.components.CommunityAddSuggestionRow]
 * (a new `AddSuggestionRow` sibling, not a fork) renders this shape directly.
 */
data class CommunityAddSuggestion(
    val card: Card,
    /** 0f..1f fraction of sampled community decks including this card. */
    val inclusionPct: Float,
    /** Staple-dampened synergy (already computed upstream — never re-derived here). */
    val synergy: Float,
    val ownedInCollection: Boolean,
    /** Archetype/theme role keys this card would help fill (empty when the deck is GENERIC/no themes). */
    val fillsGapRoles: Set<RoleKey>,
)

/**
 * Motor B of the Deck Doctor Community/Archetype plan (Phase 4,
 * `docs/claude-code-prompt-deck-doctor-community.md`): ranks a fetched [CommunityAggregate][
 * com.mmg.manahub.core.model.CommunityAggregate] card list into add suggestions.
 *
 * ## Ownership split (mirrors [SuggestAddsFromCollectionUseCase] / [DeckDoctorOrchestrator])
 * This use case is PURE ranking over an ALREADY-FETCHED [aggregateCards] list — it never talks to
 * [com.mmg.manahub.core.domain.repository.CommunityAggregateRepository] itself (fetching + the
 * Room/Worker/fallback layering + the `communityEngineEnabledFlow` gate all stay the orchestrator's
 * job, exactly like Motor A never owned Scryfall access). This keeps it easily unit-testable with
 * hand-built fixtures, no fake HTTP layer needed.
 *
 * ## Card resolution (D14, snapshot-enrichment-first per the plan)
 * An [AggregateCardEntry] carries only a name + an OPTIONAL [AggregateCardEntry.scryfallUid] — never
 * a full [Card]. Resolution order: an exact id lookup via [CardRepository.getCardById] when
 * [AggregateCardEntry.scryfallUid] is present (cheap, exact), else a by-name search via
 * [CardRepository.searchCardByName] (which itself routes through `ScryfallRequestQueue` and the
 * safe INSERT-OR-IGNORE + `@Update` upsert — see [CardRepository]'s own contract). A card that fails
 * to resolve is SKIPPED, never aborts the batch (mirrors [com.mmg.manahub.feature.communitydecks
 * .domain.usecase.ImportCommunityDeckUseCase]'s "never abort the whole import" precedent).
 *
 * ## D14 — fail-closed on unknown color identity (Commander only)
 * Same proxy as [SuggestAddsFromCollectionUseCase.hasUnresolvedColorIdentity]: `colorIdentity` must
 * be a superset of `colors`; a violation means stale/unresolved data and the card is dropped in
 * Commander. Legality mirrors [com.mmg.manahub.feature.decks.domain.engine.DeckScorer]'s OWN
 * `isLegal` (Commander-only check via `legalityCommander`; every other currently-active
 * [DeckFormat] is permissive) — this use case never invents a stricter legality gate than the rest
 * of the engine.
 *
 * ## Theme-role gap chip (no scoring — purely informational)
 * When [resolvedSkeleton] is non-null, [CommunityAddSuggestion.fillsGapRoles] is the intersection of
 * the candidate's own [ArchetypeRoleClassifier] roles and the skeleton's roles still below their
 * `ideal` (mirrors [SuggestAddsFromCollectionUseCase]'s theme-role gap check, but surfaced as a set
 * for the UI chip rather than folded into a score — Motor B's ranking is the aggregate's `synergy`
 * alone, D13/plan Phase 4: "ranked by synergy [...] filtered by color identity + legality").
 *
 * ## Determinism
 * Sorted by `synergy` desc, then `inclusionPct` desc, then card name, then scryfallId — never relies
 * on [aggregateCards]' own (Worker-supplied) ordering being stable across fetches.
 */
class SuggestAddsFromCommunityUseCase(
    private val cardRepository: CardRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * @param aggregateCards the fetched community aggregate's per-card entries (Commander or
     *        60-card — both share [AggregateCardEntry]'s shape).
     * @param mainboard the deck's current resolved mainboard (used to exclude already-owned slots).
     * @param profile the deck profile (format + color identity), reused from [EvaluateDeckUseCase].
     * @param collection the user's owned cards, to compute [CommunityAddSuggestion.ownedInCollection].
     * @param resolvedSkeleton the archetype/theme-resolved skeleton, or `null` on the GENERIC/no-themes
     *        path — disables the gap-role chip entirely in that case.
     * @param limit maximum number of suggestions to return.
     */
    suspend operator fun invoke(
        aggregateCards: List<AggregateCardEntry>,
        mainboard: List<DeckEntry>,
        profile: DeckProfile,
        collection: List<Card>,
        resolvedSkeleton: ResolvedArchetypeSkeleton? = null,
        limit: Int = 25,
    ): List<CommunityAddSuggestion> = withContext(ioDispatcher) {
        val mainboardIds = mainboard.mapTo(HashSet()) { it.card.scryfallId }
        val mainboardNames = mainboard.mapTo(HashSet()) { it.card.name }
        val collectionById = collection.associateBy { it.scryfallId }

        val resolved = aggregateCards
            .filterNot { it.name in mainboardNames }
            .distinctBy { it.name }
            .mapNotNull { entry -> resolveEntry(entry) }
            .filterNot { (card, _) -> card.scryfallId in mainboardIds }
            .filterNot { (card, _) -> profile.format == DeckFormat.COMMANDER && card.hasUnresolvedColorIdentity() }
            .filter { (card, _) -> isLegal(card, profile.format) }
            .filter { (card, _) -> withinColorIdentity(card, profile) }

        val currentRoleCounts: Map<RoleKey, Int> =
            if (resolvedSkeleton != null) ArchetypeRoleClassifier.deckRoleCounts(mainboard) else emptyMap()

        val suggestions = resolved.map { (card, entry) ->
            CommunityAddSuggestion(
                card = card,
                inclusionPct = entry.inclusionPct,
                synergy = entry.synergy,
                ownedInCollection = card.scryfallId in collectionById,
                fillsGapRoles = gapRoles(card, resolvedSkeleton, currentRoleCounts),
            )
        }

        suggestions.sortedWith(
            compareByDescending<CommunityAddSuggestion> { it.synergy }
                .thenByDescending { it.inclusionPct }
                .thenBy { it.card.name }
                .thenBy { it.card.scryfallId }
        ).take(limit)
    }

    private suspend fun resolveEntry(entry: AggregateCardEntry): Pair<Card, AggregateCardEntry>? {
        val byId = entry.scryfallUid?.let { id ->
            (cardRepository.getCardById(id) as? DataResult.Success)?.data
        }
        val card = byId ?: (cardRepository.searchCardByName(entry.name) as? DataResult.Success)?.data
        return card?.let { it to entry }
    }

    private fun Card.hasUnresolvedColorIdentity(): Boolean = colors.any { it !in colorIdentity }

    /** Mirrors [com.mmg.manahub.feature.decks.domain.engine.DeckScorer]'s own (private) `isLegal`. */
    private fun isLegal(card: Card, format: DeckFormat): Boolean {
        fun ok(s: String) = s.equals("legal", true) || s.equals("restricted", true)
        return when (format) {
            DeckFormat.COMMANDER -> ok(card.legalityCommander)
            DeckFormat.CASUAL -> true
            DeckFormat.DRAFT -> true
        }
    }

    private fun withinColorIdentity(card: Card, profile: DeckProfile): Boolean {
        if (profile.colorIdentity.isEmpty()) return true
        val allowed = profile.colorIdentity.map { it.symbol }.toSet()
        return card.colorIdentity.isEmpty() || card.colorIdentity.all { it in allowed }
    }

    private fun gapRoles(
        card: Card,
        resolvedSkeleton: ResolvedArchetypeSkeleton?,
        currentRoleCounts: Map<RoleKey, Int>,
    ): Set<RoleKey> {
        if (resolvedSkeleton == null) return emptySet()
        val cardRoles = ArchetypeRoleClassifier.classify(card)
        if (cardRoles.isEmpty()) return emptySet()
        return resolvedSkeleton.roleTargets
            .filterKeys { key ->
                key !in resolvedSkeleton.antiRoles &&
                    key in cardRoles &&
                    (currentRoleCounts[key] ?: 0) < resolvedSkeleton.roleTargets.getValue(key).ideal
            }
            .keys
    }
}
