package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ScoreWeights
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Ranks the deck's mainboard cards as cut candidates (worst fit first).
 *
 * Pure delegation to [DeckScorer.rankCuts]: the engine already excludes lands, the
 * [protectedIds] (typically the commander) and combo cores, and returns the list sorted
 * ascending by fit so the first entries are the strongest cut candidates.
 *
 * The use case stays free of repositories and Card resolution — the ViewModel resolves the
 * mainboard slots to a [DeckEntry] list and reuses the [DeckProfile] already built by
 * [EvaluateDeckUseCase] for the Health view. This keeps it trivially testable.
 */
class SuggestCutsUseCase @Inject constructor(
    private val deckScorer: DeckScorer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param mainboard the non-sideboard deck entries, each with a resolved Card.
     * @param profile the deck profile (reuse the one from [EvaluateDeckUseCase]).
     * @param protectedIds Scryfall ids that must never be suggested for a cut (e.g. the commander).
     * @param weights scoring weights (defaults to the engine's tuned defaults).
     * @return cut candidates sorted ascending by fit (worst first).
     */
    suspend operator fun invoke(
        mainboard: List<DeckEntry>,
        profile: DeckProfile,
        protectedIds: Set<String> = emptySet(),
        weights: ScoreWeights = ScoreWeights(),
    ): List<CardFit> = withContext(ioDispatcher) {
        deckScorer.rankCuts(
            mainboard = mainboard,
            profile = profile,
            protectedIds = protectedIds,
            weights = weights,
        )
    }
}
