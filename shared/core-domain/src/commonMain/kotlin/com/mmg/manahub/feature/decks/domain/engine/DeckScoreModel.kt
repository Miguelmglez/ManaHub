package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat

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
            (1f - (kotlin.math.ln(r.toDouble()) / kotlin.math.ln(maxRank.toDouble())).toFloat()).coerceIn(0f, 1f)
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

/**
 * Per-format deck template.
 *
 * Beyond the role [slots] it now carries the two distributions the Phase-3 scoring
 * math needs (plan C1/C2/C4):
 *  · [targetCurve] — the *desired* mana-curve shape, as a normalized fraction per CMC
 *    bucket (0..7, where 7 means "7+"). `curveScore` rewards a card by how much the
 *    target curve still wants its bucket, so an 8-drop in a curve that wants none of
 *    that bucket scores LOW even when the bucket is empty (fixes the old "empty bucket
 *    relative to the deck's own max" bug). The values per bucket sum to ≈1.0.
 *  · [cmcBand] — the healthy average-CMC band for the format, used by `healthScore`'s
 *    continuous `curveOk` term (Commander runs naturally higher than Standard/Draft).
 *
 * The curve is a single representative shape per format for now; archetype-specific
 * curves (aggro/midrange/control) plug in here later via a `targetCurveFor(format,
 * archetype)` selector without touching the scoring math.
 */
data class DeckSkeleton(
    val format: DeckFormat,
    val slots: List<RoleSlot>,
    val targetCurve: Map<Int, Float>,
    val cmcBand: ClosedFloatingPointRange<Double>,
) {
    fun idealFor(role: DeckRole): Int = slots.firstOrNull { it.role == role }?.ideal ?: 0
    fun maxFor(role: DeckRole): Int = slots.firstOrNull { it.role == role }?.max ?: Int.MAX_VALUE

    /** Desired normalized fraction of non-lands in the given CMC [bucket] (0..7). */
    fun targetShareFor(bucket: Int): Float = targetCurve[bucket] ?: 0f
}

/**
 * Per-format templates. Rough numbers (Commander ~ 11 ramp / 11 draw / 8 spot /
 * 4 wipes / 37 lands; 60-card decks with a stricter curve). The rest of the deck is
 * filled with PAYOFF/SYNERGY/THREAT. Parameterizable later by archetype and Commander Bracket.
 */
object DeckSkeletons {
    // ── Target mana-curve shapes (normalized fraction per CMC bucket 0..7) ───────
    //
    //  A single representative shape per format (extension point for archetype curves).
    //  Each map sums to ≈1.0. Bucket 7 means "7+". These encode WHAT a healthy curve
    //  for the format looks like; `DeckScorer.curveScore` scores a card by how much of
    //  the target the deck has NOT yet met in its bucket, so a high bucket the curve
    //  does not want scores low even when empty.

    /** Commander: midrange, singleton, ramp-supported — peaks at 2-3, real top end. */
    private val COMMANDER_CURVE = mapOf(
        0 to 0.02f, 1 to 0.10f, 2 to 0.22f, 3 to 0.24f,
        4 to 0.18f, 5 to 0.12f, 6 to 0.07f, 7 to 0.05f,
    )

    /** Standard 60-card: tighter, lower — peaks hard at 2, little top end. */
    private val STANDARD_CURVE = mapOf(
        0 to 0.03f, 1 to 0.16f, 2 to 0.28f, 3 to 0.24f,
        4 to 0.16f, 5 to 0.08f, 6 to 0.03f, 7 to 0.02f,
    )

    /** Limited/Draft: creature-dense bell around 2-4. */
    private val DRAFT_CURVE = mapOf(
        0 to 0.02f, 1 to 0.12f, 2 to 0.24f, 3 to 0.24f,
        4 to 0.20f, 5 to 0.10f, 6 to 0.05f, 7 to 0.03f,
    )

    /**
     * Eternal/non-rotating 60-card (Modern/Pioneer/Legacy/Vintage): even tighter than Standard —
     * the power level is higher and decks lean on cheap, efficient spells, so the curve peaks
     * harder at 1-2 with almost no real top end.
     */
    private val ETERNAL_CURVE = mapOf(
        0 to 0.04f, 1 to 0.20f, 2 to 0.30f, 3 to 0.22f,
        4 to 0.14f, 5 to 0.06f, 6 to 0.02f, 7 to 0.02f,
    )

    /** Pauper: commons-only — cheapest curves of all, peaks at 1-2. */
    private val PAUPER_CURVE = mapOf(
        0 to 0.04f, 1 to 0.22f, 2 to 0.32f, 3 to 0.22f,
        4 to 0.12f, 5 to 0.05f, 6 to 0.02f, 7 to 0.01f,
    )

    /**
     * Shared 60-card constructed skeleton (Standard shape) parameterised by the target curve, land
     * band and CMC band. Reused for Standard, the eternal formats, Pauper and Casual so the role
     * slot layout stays in one place (D1).
     */
    private fun sixtyCardSkeleton(
        format: DeckFormat,
        targetCurve: Map<Int, Float>,
        landMin: Int,
        landIdeal: Int,
        landMax: Int,
        cmcBand: ClosedFloatingPointRange<Double>,
    ): DeckSkeleton = DeckSkeleton(
        format = format,
        slots = listOf(
            RoleSlot(DeckRole.RAMP,           min = 0,  ideal = 2,  max = 8),
            RoleSlot(DeckRole.CARD_ADVANTAGE, min = 2,  ideal = 4,  max = 8),
            RoleSlot(DeckRole.SPOT_REMOVAL,   min = 4,  ideal = 8,  max = 12),
            RoleSlot(DeckRole.BOARD_WIPE,     min = 0,  ideal = 2,  max = 5),
            RoleSlot(DeckRole.INTERACTION,    min = 0,  ideal = 3,  max = 8),
            RoleSlot(DeckRole.LAND,           min = landMin, ideal = landIdeal, max = landMax),
        ),
        targetCurve = targetCurve,
        cmcBand = cmcBand,
    )

    fun forFormat(format: DeckFormat): DeckSkeleton = when (format) {
        DeckFormat.COMMANDER -> DeckSkeleton(
            format = format,
            slots = listOf(
                RoleSlot(DeckRole.RAMP,           min = 8,  ideal = 11, max = 14),
                RoleSlot(DeckRole.CARD_ADVANTAGE, min = 8,  ideal = 11, max = 14),
                RoleSlot(DeckRole.SPOT_REMOVAL,   min = 5,  ideal = 8,  max = 12),
                RoleSlot(DeckRole.BOARD_WIPE,     min = 2,  ideal = 4,  max = 6),
                RoleSlot(DeckRole.INTERACTION,    min = 2,  ideal = 5,  max = 10),
                RoleSlot(DeckRole.TUTOR,          min = 0,  ideal = 2,  max = 8),
                RoleSlot(DeckRole.LAND,           min = 35, ideal = 37, max = 39),
            ),
            targetCurve = COMMANDER_CURVE,
            cmcBand = 2.5..4.0,
        )
       /* DeckFormat.STANDARD -> sixtyCardSkeleton(
            format = format, targetCurve = STANDARD_CURVE,
            landMin = 22, landIdeal = 24, landMax = 26, cmcBand = 1.8..3.2,
        )
        // Eternal/non-rotating 60-card: Standard shape, tighter curve & lower CMC band.
        DeckFormat.MODERN,
        DeckFormat.PIONEER,
        DeckFormat.LEGACY,
        DeckFormat.VINTAGE -> sixtyCardSkeleton(
            format = format, targetCurve = ETERNAL_CURVE,
            landMin = 22, landIdeal = 24, landMax = 26, cmcBand = 1.6..3.0,
        )
        // Pauper: commons-only, leanest curves, one extra land slot baked into the band.
        DeckFormat.PAUPER -> sixtyCardSkeleton(
            format = format, targetCurve = PAUPER_CURVE,
            landMin = 21, landIdeal = 23, landMax = 25, cmcBand = 1.6..2.8,
        )*/
        // Casual: permissive — Standard shape & curve, used as a forgiving default.
        DeckFormat.CASUAL -> sixtyCardSkeleton(
            format = format, targetCurve = STANDARD_CURVE,
            landMin = 22, landIdeal = 24, landMax = 26, cmcBand = 1.8..3.5,
        )
        DeckFormat.DRAFT -> DeckSkeleton(
            format = format,
            slots = listOf(
                RoleSlot(DeckRole.SPOT_REMOVAL,   min = 2,  ideal = 4,  max = 7),
                RoleSlot(DeckRole.CARD_ADVANTAGE, min = 1,  ideal = 3,  max = 6),
                RoleSlot(DeckRole.THREAT,         min = 12, ideal = 15, max = 18),
                RoleSlot(DeckRole.LAND,           min = 16, ideal = 17, max = 18),
            ),
            targetCurve = DRAFT_CURVE,
            cmcBand = 2.0..3.5,
        )
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
    val roleCounts: Map<DeckRole, Float>,     // current coverage per role (quantity × confidence)
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

    /**
     * One or more mainboard slots referenced a Scryfall id that could not be resolved to a full
     * [Card] (missing from the local card cache / offline). The deck was evaluated on the
     * [count] cards that DID resolve; this warning makes the partial evaluation visible instead
     * of silently shrinking the deck (plan E6). Surfaced by the Deck Doctor ViewModel, never by
     * the engine (the engine only ever sees resolved entries).
     */
    data class UnresolvedCards(val count: Int) : DeckWarning

    // ── Construction validation (plan C5, Phase 4) ───────────────────────────────
    // Quantity-aware checks surfaced by `evaluate()` when the FULL mainboard (incl. lands)
    // is supplied. They flag a deck that breaks the format's deck-construction rules
    // regardless of how well-tuned its spell mix is (a 30-card "Commander deck" must not
    // be allowed to score 90+).

    /** The mainboard has fewer cards than the format's minimum ([current] < [minimum]). */
    data class DeckTooSmall(val current: Int, val minimum: Int) : DeckWarning

    /**
     * A non-basic card appears more times than the format allows. [cardName] is the offending
     * card, [copies] the quantity in the deck, [maxCopies] the format limit (e.g. 4, or 1 for
     * Commander singleton).
     */
    data class TooManyCopies(val cardName: String, val copies: Int, val maxCopies: Int) : DeckWarning

    /**
     * Commander singleton rule violated: [cardName] (a non-basic) appears [copies] times in a
     * singleton format. Distinct from [TooManyCopies] so the UI can phrase it as a singleton
     * violation rather than a generic copy-limit breach.
     */
    data class SingletonViolation(val cardName: String, val copies: Int) : DeckWarning

    /**
     * A card sits outside the Commander deck's color identity ([cardName] carries a color the
     * commander's identity does not allow). Only meaningful for Commander.
     */
    data class OffColorIdentity(val cardName: String) : DeckWarning

    // ── Mana-base analysis (plan C3, Phase 5) ────────────────────────────────────
    // Surfaced by `evaluate()` from [ManaBaseAnalyzer]: a colour the deck wants to cast
    // has fewer producing land sources than a Frank-Karsten-style threshold needs.

    /**
     * A colour the deck demands ([color]) is supported by [sources] land sources, below the
     * [needed] sources required to cast its pip demand reliably (a soft fixing shortage).
     */
    data class ColorSourceShortage(val color: ManaColor, val sources: Int, val needed: Int) : DeckWarning

    /**
     * A colour in the deck's demand ([color]) has so few sources (near-zero) that it is an
     * un-fixed splash — the deck essentially cannot cast its [color] spells. Louder than
     * [ColorSourceShortage].
     */
    data class UnfixedSplash(val color: ManaColor) : DeckWarning
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
