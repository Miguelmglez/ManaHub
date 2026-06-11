package com.mmg.manahub.feature.draft.data.engine

import com.mmg.manahub.feature.draft.domain.engine.BotDrafter
import com.mmg.manahub.feature.draft.domain.model.BoosterPack
import com.mmg.manahub.feature.draft.domain.model.DraftCard
import com.mmg.manahub.feature.draft.domain.model.DraftSeat
import com.mmg.manahub.feature.draft.domain.model.EngineArchetype
import com.mmg.manahub.feature.draft.domain.model.EngineConfig
import kotlin.math.min

/**
 * Archetype-based bot drafter driven by a per-set [EngineConfig].
 *
 * Unlike [HeuristicBotDrafter] (which commits to a colour *pair* with an off-colour penalty), this
 * drafter commits to a **strategy/archetype**. Each card carries per-archetype membership weights;
 * the seat's commitment is the sum of those weights over its pool. This works natively for 2-colour,
 * 3-colour wedge, and 5-colour decks because archetypes carry a colour *list*, not a pair — there is
 * **no off-colour penalty** at all.
 *
 * Scoring per candidate card:
 * ```
 * score = ratingWeight * rating
 *       + synergyWeight * synergyScaled
 *       + (fixing ? fixingBonus : 0)
 *       + (bomb   ? BOMB_BONUS   : 0)
 *       + curveWeight  * curveNudge
 *       + opennessTerm
 * ```
 * where:
 * - `rating`        = `signals.rating ?: DraftRatingNormalizer.ratingScore(card)` in `[0, 1]`.
 * - `synergy`       = dot(commitment, card.archetypeWeights).
 * - `synergyScaled` = `synergy * synergyRamp`, where the ramp interpolates from ~0 on the opening
 *                     picks (rating-led speculation) up to 1 as both the pick number passes
 *                     `speculationPicks` **and** total commitment approaches `commitmentThreshold`.
 * - `curveNudge`    = small positive for cheap cards, small negative when the curve is already
 *                     top-heavy (mirrors [HeuristicBotDrafter]).
 * - `opennessTerm`  = early-pick bonus toward the most open lanes the card supports, scaled down as
 *                     the seat commits (openness only matters while lanes are still being chosen).
 *
 * For **non-human** seats a tiny deterministic *lane prior* (derived from [DraftSeat.index]) is added
 * as a separate, commitment-faded score term so bots spread across lanes instead of all funnelling
 * into the same archetype. It is NOT folded into the synergy commitment (which stays pool-only and
 * honest); it only nudges the early, uncommitted picks and then fades out. The human seat
 * (`seat.isHuman`) receives **no** prior, so its suggested pick stays neutral.
 *
 * When [EngineConfig] is null the drafter delegates entirely to [fallback]. Pure and stateless.
 *
 * @property fallback Heuristic drafter used for sets without an engine.json.
 */
class ArchetypeAwareBotDrafter(
    private val fallback: BotDrafter = HeuristicBotDrafter(),
) : BotDrafter {

    override fun pick(
        seat: DraftSeat,
        pack: BoosterPack,
        round: Int,
        pickNumber: Int,
        engine: EngineConfig?,
    ): DraftCard {
        // A pick can never be made from an empty pack — fail fast with a clear message rather than
        // crashing later with a confusing NoSuchElementException / `error("Empty pack")`. Callers
        // (e.g. DefaultDraftEngine.autoPick) must guard for empty packs before reaching here.
        require(pack.cards.isNotEmpty()) { "ArchetypeAwareBotDrafter.pick called with an empty pack" }
        if (engine == null) {
            return fallback.pick(seat, pack, round, pickNumber, engine)
        }

        val commitment = buildCommitment(seat, engine)
        val totalCommitment = commitment.values.sum()
        val globalPickIndex = seat.pool.size
        // Bot seats get a deterministic lane prior; the human seat gets none (neutral suggestion).
        val lanePrior = if (seat.isHuman) emptyMap() else seatLanePrior(seat.index, engine.archetypes)

        // Deterministic tie-break: when two cards score equal (e.g. early speculation with identical
        // ratings), the one with the lexicographically larger scryfallId wins so picks are stable.
        return pack.cards
            .maxWithOrNull(
                compareBy<DraftCard> { card ->
                    score(card, engine, commitment, totalCommitment, globalPickIndex, lanePrior)
                }.thenByDescending { it.card.scryfallId },
            )
            ?: pack.cards.first()
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    private fun score(
        card: DraftCard,
        engine: EngineConfig,
        commitment: Map<String, Float>,
        totalCommitment: Float,
        globalPickIndex: Int,
        lanePrior: Map<String, Float>,
    ): Float {
        val params = engine.params
        val signals = engine.cards[card.card.scryfallId]
        val weights = signals?.archetypeWeights.orEmpty()

        val rating = signals?.rating ?: DraftRatingNormalizer.ratingScore(card)

        // Raw synergy = commitment · card-archetype-weights.
        val synergy = weights.entries.sumOf { (archId, w) ->
            ((commitment[archId] ?: 0f) * w).toDouble()
        }.toFloat()

        // Ramp synergy in as the seat both gets past the speculation picks AND builds real
        // commitment. Early picks (low index, low commitment) are rating-led; late picks are
        // synergy-led. Both factors are clamped to [0, 1] and combined multiplicatively so a card
        // is only strongly synergy-driven once the seat is genuinely committed.
        val pickRamp = if (params.speculationPicks <= 0) {
            1f
        } else {
            (globalPickIndex.toFloat() / params.speculationPicks).coerceIn(0f, 1f)
        }
        val commitmentRamp = if (params.commitmentThreshold <= 0f) {
            1f
        } else {
            (totalCommitment / params.commitmentThreshold).coerceIn(0f, 1f)
        }
        val synergyRamp = min(pickRamp, commitmentRamp)
        val synergyScaled = synergy * synergyRamp

        val fixingTerm = if (signals?.fixing == true) params.fixingBonus else 0f
        val bombTerm = if (signals?.bomb == true) BOMB_BONUS else 0f
        val curveNudge = curveNudge(card)
        val opennessTerm = opennessTerm(weights, engine.archetypes, params.opennessWeight, commitmentRamp)
        // The lane prior fades out as the seat commits, just like openness — it only steers the
        // early, uncommitted picks toward this bot's assigned lanes.
        val priorTerm = lanePriorTerm(weights, lanePrior) * (1f - commitmentRamp)

        return params.ratingWeight * rating +
            params.synergyWeight * synergyScaled +
            fixingTerm +
            bombTerm +
            params.curveWeight * curveNudge +
            opennessTerm +
            priorTerm
    }

    /**
     * Early-pick bias toward the most open lanes the card supports. Stronger lanes (higher
     * [EngineArchetype.opennessBase]) are slightly favoured while the seat is still uncommitted, then
     * faded out as commitment ramps up (lane choice no longer matters once committed).
     */
    private fun opennessTerm(
        weights: Map<String, Float>,
        archetypes: List<EngineArchetype>,
        opennessWeight: Float,
        commitmentRamp: Float,
    ): Float {
        if (weights.isEmpty() || opennessWeight <= 0f) return 0f
        val openness = archetypes
            .filter { (weights[it.id] ?: 0f) > 0f }
            .maxOfOrNull { it.opennessBase * (weights[it.id] ?: 0f) }
            ?: 0f
        // Fade openness out as the seat commits.
        return opennessWeight * openness * (1f - commitmentRamp)
    }

    /** Dot product of the card's archetype weights with this seat's lane prior. */
    private fun lanePriorTerm(weights: Map<String, Float>, lanePrior: Map<String, Float>): Float {
        if (weights.isEmpty() || lanePrior.isEmpty()) return 0f
        return weights.entries.sumOf { (archId, w) ->
            ((lanePrior[archId] ?: 0f) * w).toDouble()
        }.toFloat()
    }

    /** Small curve nudge mirroring [HeuristicBotDrafter]: favour cheap cards, lightly. */
    private fun curveNudge(card: DraftCard): Float = when {
        card.card.cmc <= 2.0 -> 1.0f
        card.card.cmc >= 5.0 -> -0.5f
        else -> 0.0f
    }

    /**
     * Builds the seat's archetype commitment by summing per-card archetype weights over the pool.
     * Pool-only and honest — the per-bot lane prior is applied separately in scoring, never here.
     */
    private fun buildCommitment(seat: DraftSeat, engine: EngineConfig): Map<String, Float> {
        val commitment = mutableMapOf<String, Float>()
        for (card in seat.pool) {
            val weights = engine.cards[card.card.scryfallId]?.archetypeWeights ?: continue
            for ((archId, w) in weights) {
                commitment[archId] = (commitment[archId] ?: 0f) + w
            }
        }
        return commitment
    }

    /**
     * Deterministic per-bot lane preference. Each seat is biased toward two archetypes selected by
     * `index modulo archetypeCount`, so eight bots fan out across the available lanes rather than all
     * chasing the single best archetype. The prior is small ([SEAT_PRIOR]) and commitment-faded, so a
     * real signal from the pool always overrides it.
     */
    private fun seatLanePrior(seatIndex: Int, archetypes: List<EngineArchetype>): Map<String, Float> {
        if (archetypes.isEmpty()) return emptyMap()
        val count = archetypes.size
        val prior = mutableMapOf<String, Float>()
        val primary = archetypes[seatIndex % count]
        prior[primary.id] = SEAT_PRIOR
        if (count > 1) {
            val secondary = archetypes[(seatIndex + 1) % count]
            prior[secondary.id] = (prior[secondary.id] ?: 0f) + SEAT_PRIOR * SECONDARY_FACTOR
        }
        return prior
    }

    private companion object {
        /** Flat additive boost for engine-flagged bombs. */
        const val BOMB_BONUS = 0.4f

        /** Strength of the deterministic per-bot lane prior. Small so real signal dominates. */
        const val SEAT_PRIOR = 0.6f

        /** The bot's secondary lane gets a fraction of the primary prior. */
        const val SECONDARY_FACTOR = 0.5f
    }
}
