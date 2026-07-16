package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.repository.CommunityDecksRepository
import com.mmg.manahub.core.model.CommunityDeckSearchFilters
import com.mmg.manahub.core.model.CommunityDeckSummary
import com.mmg.manahub.core.model.DataResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One "decks like yours" result — a real, importable Archidekt deck. */
data class SimilarDeckResult(
    val archidektId: Int,
    val name: String,
    val ownerUsername: String,
    val viewCount: Int,
    /** 0f..1f color-identity Jaccard similarity against the user's own deck. See the class KDoc's
     * "Scope note" for why this is the ONLY similarity signal (no mainboard-overlap term). */
    val colorSimilarity: Float,
)

/**
 * Motor B "Decks like yours" (Deck Doctor Community/Archetype plan, Phase 4).
 *
 * ## Scope note — a documented deviation from the plan's original wording
 * The plan describes this as "`FindSimilarDecksUseCase`: Worker `/v1/similar` pre-ranks; client
 * re-ranks by Jaccard similarity over the exact mainboard + color-profile proximity." Verified
 * against the ACTUAL Worker contract (`cloudflare/manahub-community/src/routes/similar.ts`):
 * `/v1/similar` returns EDHREC's `similar` field — a list of SIMILAR COMMANDER NAMES (a
 * classifier-adjacent signal), never a list of actual decks with mainboards. There is no
 * per-deck card-list data anywhere in the Worker's contract for either the Commander (EDHREC,
 * card-aggregate-only) or 60-card (Archidekt search-summary-only, no card list) path — fetching a
 * full mainboard per candidate deck to compute a real Jaccard would mean N+1 Archidekt detail
 * calls per carousel render, exactly the client-side hammering the Worker exists to prevent.
 *
 * This use case is therefore built on the EXISTING, already-live [CommunityDecksRepository]
 * (Archidekt search — same data source `SearchCommunityDecksUseCase`/`CommunityDecksScreen`
 * already use) rather than the new aggregate Worker: it searches Archidekt for decks matching
 * [seedQuery] (the deck's commander name, or its most distinctive card for 60-card), sorted by
 * popularity (`orderBy=-viewCount`, confirmed live in `docs/adr/ADR-004-community-api-contracts.md`
 * §1), then CLIENT-SIDE re-ranks by the ONE similarity signal the returned [CommunityDeckSummary]
 * actually carries: [CommunityDeckSummary.colorIdentity] Jaccard against the user's own deck colors
 * (the plan's "color-profile proximity" term). The mainboard-overlap term is a documented, honest
 * gap — mirrors this codebase's existing precedent for such gaps (see `project_archetype_engine`
 * memory's "D18 land union rule — MDFC 0.62-weighting NOT implemented").
 *
 * Every result carries a real `archidektId` so the Studio carousel can navigate straight to
 * [com.mmg.manahub.app.navigation.Screen.CommunityDeckDetail] — the plan's UI requirement is
 * satisfied even though the ranking signal is narrower than originally worded.
 */
class FindSimilarDecksUseCase(
    private val communityDecksRepository: CommunityDecksRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * @param seedQuery the commander name (Commander format) or the deck's most distinctive
     *        signature card name (60-card format) to search Archidekt for.
     * @param deckFormat the Archidekt numeric format id to filter by, or `null` for no filter.
     * @param userColorIdentity the user's own deck's color identity (WUBRG letters).
     * @param limit maximum number of results.
     */
    suspend operator fun invoke(
        seedQuery: String,
        deckFormat: Int?,
        userColorIdentity: Set<String>,
        limit: Int = 10,
    ): DataResult<List<SimilarDeckResult>> = withContext(ioDispatcher) {
        if (seedQuery.isBlank()) return@withContext DataResult.Success(emptyList())
        when (
            val result = communityDecksRepository.searchDecks(
                CommunityDeckSearchFilters(
                    cardName = seedQuery,
                    deckFormatId = deckFormat,
                    orderBy = "-viewCount",
                    page = 1,
                    pageSize = SEARCH_POOL_SIZE,
                ),
            )
        ) {
            is DataResult.Error -> result
            is DataResult.Success -> {
                val ranked = result.data.decks
                    .map { deck -> deck to colorSimilarity(deck, userColorIdentity) }
                    .sortedWith(
                        compareByDescending<Pair<CommunityDeckSummary, Float>> { it.second }
                            .thenByDescending { it.first.viewCount }
                            .thenBy { it.first.archidektId }
                    )
                    .take(limit)
                    .map { (deck, similarity) ->
                        SimilarDeckResult(
                            archidektId = deck.archidektId,
                            name = deck.name,
                            ownerUsername = deck.owner.username,
                            viewCount = deck.viewCount,
                            colorSimilarity = similarity,
                        )
                    }
                DataResult.Success(ranked)
            }
        }
    }

    /** Jaccard similarity of the two color-identity sets; `1f` when both are colorless. */
    private fun colorSimilarity(deck: CommunityDeckSummary, userColorIdentity: Set<String>): Float {
        val deckColors = deck.colorIdentity.toSet()
        if (deckColors.isEmpty() && userColorIdentity.isEmpty()) return 1f
        val union = deckColors + userColorIdentity
        if (union.isEmpty()) return 1f
        val intersection = deckColors.intersect(userColorIdentity)
        return intersection.size.toFloat() / union.size
    }

    private companion object {
        /** A generous single-page pool to re-rank locally (Archidekt caps a page at 60 anyway). */
        const val SEARCH_POOL_SIZE = 40
    }
}
