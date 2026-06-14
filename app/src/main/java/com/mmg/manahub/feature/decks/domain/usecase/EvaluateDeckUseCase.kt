package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.di.IoDispatcher
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.gamification.domain.ProgressionEventBus
import com.mmg.manahub.core.gamification.domain.event.ProgressionEvent
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckEvaluation
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ScoreWeights
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
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
 * Strategy seed inference (Phase 7 / plan B4) is the caller's responsibility: the Deck Doctor
 * ViewModel runs [InferDeckIdentityUseCase] over the commander + the deck's highest-weight identity
 * cards and passes the resulting `seedTags` here, so the fingerprint is no longer purely
 * self-referential. Callers that have no seeds (e.g. an empty-strategy preview) simply pass the
 * default empty list.
 */
class EvaluateDeckUseCase @Inject constructor(
    private val deckScorer: DeckScorer,
    private val progressionEventBus: ProgressionEventBus,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Stable, code-side slug identifying the Deck Doctor feature for exploration quests. */
    private companion object {
        const val FEATURE_DECK_DOCTOR = "deck_doctor"
    }

    /**
     * @param mainboard the non-sideboard deck entries, each with a resolved [Card].
     * @param format the deck's [DeckFormat].
     * @param commanderIdentity color symbols of the deck's commander (empty for non-commander
     *        decks or when the commander card could not be resolved). Each symbol is a single
     *        letter from `W/U/B/R/G`; anything else is ignored.
     * @param seedTags inferred strategy seed tags (plan B4) — the commander's + the deck's
     *        highest-weight identity cards' tags, produced by [InferDeckIdentityUseCase]. Floored
     *        into the fingerprint by the scorer (size-independent, Phase-3 `SEED_FLOOR`). Empty when
     *        the deck carries no recognizable strategy signal.
     * @param weights the (optionally debug-tuned) [ScoreWeights] threaded through the Deck Doctor
     *        read path (plan F2), forwarded to [DeckScorer.evaluate]. The default keeps every other
     *        caller (and all existing tests) byte-identical.
     * @return a [DeckHealth] bundling the [DeckEvaluation] and the [DeckProfile] used to build it.
     */
    suspend operator fun invoke(
        mainboard: List<DeckEntry>,
        format: DeckFormat,
        commanderIdentity: Set<String> = emptySet(),
        seedTags: List<CardTag> = emptyList(),
        weights: ScoreWeights = ScoreWeights(),
    ): DeckHealth = withContext(ioDispatcher) {
        val colorIdentity = deriveColorIdentity(mainboard, commanderIdentity)

        val profile = deckScorer.profile(
            mainboard = mainboard,
            format = format,
            colorIdentity = colorIdentity,
            seedTags = seedTags, // B4: inferred strategy seed from the caller (Phase 7)
        )

        val nonLand = mainboard.filterNot { BasicLandCalculator.isLand(it.card) }
        // Pass the FULL mainboard so the scorer can run construction validation (C5):
        // deck size, copy limits, Commander singleton + off-color-identity checks.
        val evaluation = deckScorer.evaluate(profile, nonLand, fullMainboard = mainboard, weights = weights)

        // Canonical Deck Doctor "analysis produced" choke point: a successful evaluation is the single
        // domain event for the EXPLORATION quest (`daily_explore_deck_doctor`). Emitted here (not the
        // ViewModel) per ADR-002 §1; the per-day idempotency key (FeatureExplored) means re-running on
        // a cut/add the same day advances the quest at most once. The emit grants 0 XP (no ledger row);
        // it is fire-and-forget on the bus and never affects the returned analysis.
        progressionEventBus.emit(
            ProgressionEvent.FeatureExplored(
                featureKey = FEATURE_DECK_DOCTOR,
                occurredAt = Instant.now(),
            )
        )

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
