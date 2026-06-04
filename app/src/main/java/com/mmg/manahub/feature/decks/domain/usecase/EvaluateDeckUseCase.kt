package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.feature.decks.presentation.engine.DeckEntry
import com.mmg.manahub.feature.decks.presentation.engine.DeckEvaluation
import com.mmg.manahub.feature.decks.presentation.engine.DeckProfile
import com.mmg.manahub.feature.decks.presentation.engine.DeckScorer
import com.mmg.manahub.feature.decks.presentation.engine.ManaColor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Computes the read-only "Health" evaluation of a deck's mainboard using the new
 * [DeckScorer] engine.
 *
 * The caller (the ViewModel) resolves each deck slot to a full [Card] and passes the
 * mainboard in as a list of [DeckEntry]; this use case stays pure and easily testable
 * (no repositories, no Card resolution). It derives the deck's color identity from the
 * mainboard plus an optional commander identity, builds the [DeckProfile] and returns the
 * resulting [DeckEvaluation] together with the profile (the Health view needs the profile's
 * role skeleton / ideals).
 *
 * Seed tags are intentionally empty for Phase 4 — strategy seed inference lands in Phase 7.
 */
class EvaluateDeckUseCase @Inject constructor(
    private val deckScorer: DeckScorer,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param mainboard the non-sideboard deck entries, each with a resolved [Card].
     * @param format the deck's [DeckFormat].
     * @param commanderIdentity color symbols of the deck's commander (empty for non-commander
     *        decks or when the commander card could not be resolved). Each symbol is a single
     *        letter from `W/U/B/R/G`; anything else is ignored.
     * @return a [DeckHealth] bundling the [DeckEvaluation] and the [DeckProfile] used to build it.
     */
    suspend operator fun invoke(
        mainboard: List<DeckEntry>,
        format: DeckFormat,
        commanderIdentity: Set<String> = emptySet(),
    ): DeckHealth = withContext(ioDispatcher) {
        val colorIdentity = deriveColorIdentity(mainboard, commanderIdentity)

        val profile = deckScorer.profile(
            mainboard = mainboard,
            format = format,
            colorIdentity = colorIdentity,
            seedTags = emptyList(), // seed inference is Phase 7
        )

        val nonLand = mainboard.filterNot { BasicLandCalculator.isLand(it.card) }
        val evaluation = deckScorer.evaluate(profile, nonLand)

        DeckHealth(evaluation = evaluation, profile = profile)
    }

    /**
     * Color identity = union of every mainboard card's [Card.colorIdentity] symbols plus the
     * commander's identity, mapped to [ManaColor]. Only the five WUBRG letters map to a color;
     * "C" (colorless) and any unknown symbol are dropped so an empty result correctly means
     * "no color restriction" for the scorer.
     */
    private fun deriveColorIdentity(
        mainboard: List<DeckEntry>,
        commanderIdentity: Set<String>,
    ): Set<ManaColor> {
        val symbols = buildSet {
            mainboard.forEach { entry -> addAll(entry.card.colorIdentity) }
            addAll(commanderIdentity)
        }
        return symbols.mapNotNull(::symbolToColor).toSet()
    }

    private fun symbolToColor(symbol: String): ManaColor? = when (symbol.uppercase()) {
        ManaColor.W.symbol -> ManaColor.W
        ManaColor.U.symbol -> ManaColor.U
        ManaColor.B.symbol -> ManaColor.B
        ManaColor.R.symbol -> ManaColor.R
        ManaColor.G.symbol -> ManaColor.G
        else -> null // "C" and unknown symbols are not a color-identity restriction
    }
}

/**
 * Result of [EvaluateDeckUseCase]: the deck [evaluation] plus the [profile] it was derived from
 * (exposed because the Health UI renders role ideals / skeleton, which live on the profile).
 */
data class DeckHealth(
    val evaluation: DeckEvaluation,
    val profile: DeckProfile,
)
