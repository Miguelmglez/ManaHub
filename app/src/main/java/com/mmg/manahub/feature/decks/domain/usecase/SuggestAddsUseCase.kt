package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.feature.decks.presentation.engine.CardFit
import com.mmg.manahub.feature.decks.presentation.engine.DeckProfile
import com.mmg.manahub.feature.decks.presentation.engine.DeckScorer
import com.mmg.manahub.feature.decks.presentation.engine.ScoreWeights
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Where an add suggestion came from. Phase 5 only ever produces [COLLECTION] candidates;
 * [WISHLIST] and [NEW] are reserved for Phase 6 (external Scryfall + budget filters) so the
 * UI and result shape do not have to change when those sources are wired in.
 */
enum class AddOrigin { COLLECTION, WISHLIST, NEW }

/**
 * A ranked add suggestion: the engine's [fit] plus its [origin].
 *
 * The origin is decoupled from [CardFit] on purpose — [CardFit.isOwned] only says whether the
 * card is in the collection, while origin distinguishes collection vs wishlist vs a brand-new
 * Scryfall recommendation. Phase 6 can add wishlist/new candidates without reshaping this type.
 */
data class AddSuggestion(
    val fit: CardFit,
    val origin: AddOrigin,
)

/**
 * Ranks add candidates drawn ONLY from the user's collection (Phase 5).
 *
 * The candidate pool is the user's owned cards minus those already in the deck mainboard. The
 * engine ([DeckScorer.rankAdds]) then applies the HARD filter (legality + color identity) plus a
 * power floor, so out-of-color / illegal cards never surface. Because every candidate here is
 * owned, [CardFit.isOwned] is always true and the UI can label them "In collection" / price 0.
 *
 * Phase 6 will extend this with wishlist + external (Scryfall) candidates by appending more
 * [AddSuggestion]s with the corresponding [AddOrigin]; the engine call and ranking stay the same.
 */
class SuggestAddsUseCase @Inject constructor(
    private val deckScorer: DeckScorer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param collection the user's owned cards (one [Card] per distinct printing/scryfallId).
     * @param mainboardIds Scryfall ids already present in the deck mainboard (excluded from the pool).
     * @param profile the deck profile (reuse the one from [EvaluateDeckUseCase]).
     * @param weights scoring weights (defaults to the engine's tuned defaults).
     * @param limit maximum number of suggestions to return.
     * @return add suggestions sorted descending by fit, all with [AddOrigin.COLLECTION].
     */
    suspend operator fun invoke(
        collection: List<Card>,
        mainboardIds: Set<String>,
        profile: DeckProfile,
        weights: ScoreWeights = ScoreWeights(),
        limit: Int = 50,
    ): List<AddSuggestion> = withContext(ioDispatcher) {
        // Candidate pool: owned cards that are NOT already in the deck. De-duplicate by
        // scryfallId so the same printing held in multiple conditions is scored only once.
        val candidates = collection
            .filterNot { it.scryfallId in mainboardIds }
            .distinctBy { it.scryfallId }

        // Everything in the pool is owned, so ownedIds = the whole pool's ids.
        val ownedIds = candidates.mapTo(mutableSetOf()) { it.scryfallId }

        deckScorer.rankAdds(
            candidates = candidates,
            profile = profile,
            ownedIds = ownedIds,
            weights = weights,
            limit = limit,
        ).map { fit -> AddSuggestion(fit = fit, origin = AddOrigin.COLLECTION) }
    }
}
