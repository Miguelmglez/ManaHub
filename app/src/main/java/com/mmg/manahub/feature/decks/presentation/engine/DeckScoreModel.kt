package com.mmg.manahub.feature.decks.presentation.engine

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.DeckFormat

// ═══════════════════════════════════════════════════════════════════════════════
//  DeckScoreModel
//
//  Models for the new scoring engine (DeckScorer). Replaces the scattered logic of
//  MagicScorer + SynergyScorer with a single, gap-aware, explainable model.
//
//  Design decisions versus the old scorers:
//   · The power signal is injected (PowerResolver) instead of being ignored.
//   · Color is a HARD filter (withinColorIdentity) + a soft bonus, not a 0.0.
//   · Reasons are structured objects (ScoreReason) -> presentation localizes them
//     to strings, instead of hardcoded English strings in the engine.
//   · Everything is compared by CardTag.key (locale-safe), not by object equality.
// ═══════════════════════════════════════════════════════════════════════════════

/** Functional role of a card inside a deck. Foundation of the whole evaluation. */
enum class DeckRole {
    RAMP,            // mana acceleration (mana_rock, mana_dork, ramp)
    CARD_ADVANTAGE,  // draw / card advantage
    SPOT_REMOVAL,    // targeted removal
    BOARD_WIPE,      // mass removal
    INTERACTION,     // counterspells, protection, stax
    TUTOR,           // searchers
    PAYOFF,          // win conditions / pieces that execute the strategy
    SYNERGY,         // strategy gears (do not win on their own)
    THREAT,          // generic threats (creatures with a relevant body)
    LAND,            // mana base
    FILLER;          // no clear role -> natural cut candidate

    /** Roles that cover a skeleton "need" (LAND and FILLER are handled separately). */
    val isFunctional: Boolean get() = this != LAND && this != FILLER
}

// ─────────────────────────────────────────────────────────────────────────────
//  Power signal (decoupled so we don't have to touch the Card model yet)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Card quality/playability, independent of synergy.
 * [normalized] in [0,1] (1 = top staple). [isGameChanger] marks cards on the
 * official Commander Brackets list (already available in Card.gameChanger).
 */
data class CardPower(val normalized: Float, val isGameChanger: Boolean)

/** Injectable power strategy. Lets the source evolve without touching the engine. */
fun interface PowerResolver {
    fun powerOf(card: Card): CardPower
}

/**
 * TEMPORARY default while edhrec_rank is not persisted. Neutral (0.5) with a nudge
 * for Game Changers. It does not distinguish staples from jank: replace it with
 * [EdhrecPowerResolver] as soon as the field is added (see prerequisite note in the
 * design document).
 */
object NeutralPowerResolver : PowerResolver {
    override fun powerOf(card: Card): CardPower =
        CardPower(normalized = if (card.gameChanger) 0.85f else 0.5f, isGameChanger = card.gameChanger)
}

/**
 * Recommended resolver. Converts edhrec_rank (lower = more played) into [0,1] on a
 * logarithmic scale. [rankOf] is passed as a lambda so it compiles TODAY (before the
 * field is added to Card); afterwards just use `EdhrecPowerResolver { it.edhrecRank }`.
 */
class EdhrecPowerResolver(
    private val maxRank: Int = 30_000,
    private val rankOf: (Card) -> Int?,
) : PowerResolver {
    override fun powerOf(card: Card): CardPower {
        val rank = rankOf(card)
        val base = if (rank == null || rank <= 0) 0.35f // unknown/invalid rank = slightly below average
        else {
            val r = rank.coerceIn(1, maxRank)
            (1f - (Math.log(r.toDouble()) / Math.log(maxRank.toDouble())).toFloat()).coerceIn(0f, 1f)
        }
        // A Game Changer never scores below "very good".
        val withFloor = if (card.gameChanger) maxOf(base, 0.85f) else base
        return CardPower(withFloor, card.gameChanger)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Configurable weights (replace the hardcoded `const`s of the old scorers)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fit weights. The positive ones are normalized to sum 1; [redundancyPenalty] is a
 * separate subtractive coefficient. Persistable in DataStore to tune without recompiling.
 */
data class ScoreWeights(
    val synergy: Float = 0.34f,
    val roleNeed: Float = 0.22f,
    val curve: Float = 0.14f,
    val power: Float = 0.20f,
    val color: Float = 0.10f,
    val redundancyPenalty: Float = 0.25f,
    /** Below this power, a card is dropped from suggestions unless synergy is exceptional. */
    val powerFloor: Float = 0.18f,
) {
    private val positiveSum get() = synergy + roleNeed + curve + power + color
    fun normalized(): ScoreWeights {
        val s = positiveSum.takeIf { it > 0f } ?: return this
        return copy(synergy = synergy / s, roleNeed = roleNeed / s, curve = curve / s, power = power / s, color = color / s)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Structured (localizable) reasons — replace the hardcoded English strings
// ─────────────────────────────────────────────────────────────────────────────

sealed interface ScoreReason {
    data class SynergyMatch(val tags: List<CardTag>) : ScoreReason
    data class FillsGap(val role: DeckRole) : ScoreReason
    data class OverCovered(val role: DeckRole, val current: Int, val ideal: Int) : ScoreReason
    data object OnCurve : ScoreReason
    data class CurveGap(val cmcBucket: Int) : ScoreReason
    data object HighPower : ScoreReason
    data object GameChanger : ScoreReason
    data object BelowPowerFloor : ScoreReason
    data object OffStrategy : ScoreReason
    data object OutOfColorIdentity : ScoreReason
    data object Colorless : ScoreReason
    data object InCollection : ScoreReason
}

// ─────────────────────────────────────────────────────────────────────────────
//  Result of scoring a card
// ─────────────────────────────────────────────────────────────────────────────

data class ScoreComponents(
    val synergy: Float,
    val roleNeed: Float,
    val curve: Float,
    val power: Float,
    val color: Float,
    val redundancy: Float,
)

data class CardFit(
    val card: Card,
    val score: Float,                    // aggregated [0,1]
    val components: ScoreComponents,
    val roles: Set<DeckRole>,
    val reasons: List<ScoreReason>,
    val isOwned: Boolean,
    val isLegal: Boolean,                // legal in the profile's format
    val withinColorIdentity: Boolean,    // within the deck's color identity
) {
    /** Eligible to be suggested as an addition (hard legality + color filter). */
    val isAddCandidate: Boolean get() = isLegal && withinColorIdentity
}

// ─────────────────────────────────────────────────────────────────────────────
//  Role skeleton (how many cards of each role the deck "should" have)
// ─────────────────────────────────────────────────────────────────────────────

data class RoleSlot(val role: DeckRole, val min: Int, val ideal: Int, val max: Int)

data class DeckSkeleton(val format: DeckFormat, val slots: List<RoleSlot>) {
    fun idealFor(role: DeckRole): Int = slots.firstOrNull { it.role == role }?.ideal ?: 0
    fun maxFor(role: DeckRole): Int = slots.firstOrNull { it.role == role }?.max ?: Int.MAX_VALUE
}

/**
 * Per-format templates. Rough numbers (Commander ~ 11 ramp / 11 draw / 8 spot /
 * 4 wipes / 37 lands; 60-card decks with a stricter curve). The rest of the deck is
 * filled with PAYOFF/SYNERGY/THREAT. Parameterizable later by archetype and Commander Bracket.
 */
object DeckSkeletons {
    fun forFormat(format: DeckFormat): DeckSkeleton = when (format) {
        DeckFormat.COMMANDER -> DeckSkeleton(format, listOf(
            RoleSlot(DeckRole.RAMP,           min = 8,  ideal = 11, max = 14),
            RoleSlot(DeckRole.CARD_ADVANTAGE, min = 8,  ideal = 11, max = 14),
            RoleSlot(DeckRole.SPOT_REMOVAL,   min = 5,  ideal = 8,  max = 12),
            RoleSlot(DeckRole.BOARD_WIPE,     min = 2,  ideal = 4,  max = 6),
            RoleSlot(DeckRole.INTERACTION,    min = 2,  ideal = 5,  max = 10),
            RoleSlot(DeckRole.TUTOR,          min = 0,  ideal = 2,  max = 8),
            RoleSlot(DeckRole.LAND,           min = 35, ideal = 37, max = 39),
        ))
        DeckFormat.STANDARD -> DeckSkeleton(format, listOf(
            RoleSlot(DeckRole.RAMP,           min = 0,  ideal = 2,  max = 8),
            RoleSlot(DeckRole.CARD_ADVANTAGE, min = 2,  ideal = 4,  max = 8),
            RoleSlot(DeckRole.SPOT_REMOVAL,   min = 4,  ideal = 8,  max = 12),
            RoleSlot(DeckRole.BOARD_WIPE,     min = 0,  ideal = 2,  max = 5),
            RoleSlot(DeckRole.INTERACTION,    min = 0,  ideal = 3,  max = 8),
            RoleSlot(DeckRole.LAND,           min = 22, ideal = 24, max = 26),
        ))
        DeckFormat.DRAFT -> DeckSkeleton(format, listOf(
            RoleSlot(DeckRole.SPOT_REMOVAL,   min = 2,  ideal = 4,  max = 7),
            RoleSlot(DeckRole.CARD_ADVANTAGE, min = 1,  ideal = 3,  max = 6),
            RoleSlot(DeckRole.THREAT,         min = 12, ideal = 15, max = 18),
            RoleSlot(DeckRole.LAND,           min = 16, ideal = 17, max = 18),
        ))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Deck profile (current state vs ideal) and health evaluation
// ─────────────────────────────────────────────────────────────────────────────

/** Deck "fingerprint": what the scorer needs to score candidates in context. */
data class DeckProfile(
    val format: DeckFormat,
    val colorIdentity: Set<ManaColor>,        // hard restriction
    val seedTags: List<CardTag>,              // strategy seed (SeedStrategy or inferred)
    val tagFingerprint: Map<String, Float>,   // key -> normalized weight (dominant strategy)
    val roleCounts: Map<DeckRole, Int>,       // current coverage per role
    val skeleton: DeckSkeleton,
    val avgCmc: Double,                        // non-lands ONLY (bug fixed)
    val curveHistogram: Map<Int, Int>,        // CMC bucket -> number of non-lands
    val nonLandCount: Int,
)

data class RoleCoverage(val role: DeckRole, val current: Int, val ideal: Int) {
    val gap: Int get() = (ideal - current).coerceAtLeast(0)
    val ratio: Float get() = if (ideal == 0) 1f else (current.toFloat() / ideal).coerceIn(0f, 2f)
}

sealed interface DeckWarning {
    data class TooFewLands(val current: Int, val target: Int) : DeckWarning
    data class TooManyLands(val current: Int, val target: Int) : DeckWarning
    data class MissingRole(val role: DeckRole, val ideal: Int) : DeckWarning
    data class CurveTooHigh(val avgCmc: Double) : DeckWarning
    data class CurveTooLow(val avgCmc: Double) : DeckWarning
    data class LowSynergyDensity(val density: Float) : DeckWarning
}

data class DeckEvaluation(
    val roleCoverage: List<RoleCoverage>,
    val avgCmc: Double,
    val curveHistogram: Map<Int, Int>,
    val landCount: Int,
    val synergyDensity: Float,      // % of non-lands aligned with the dominant strategy
    val healthScore: Int,           // 0..100
    val warnings: List<DeckWarning>,
)
