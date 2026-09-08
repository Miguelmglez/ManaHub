package com.mmg.manahub.feature.decks.domain.engine
// COMMENTS_REVIEWED: 2026-09-08

import kotlin.math.roundToInt

/**
 * Deck Wizard Commander v3 plan (Phase 0 / E5): per-MV-bucket curve targets derived from a
 * [ResolvedArchetypeSkeleton]'s [ResolvedArchetypeSkeleton.curve] band and
 * [ResolvedArchetypeSkeleton.shape] -- read-only, additive, consumed by no scoring path today.
 *
 * IMPORTANT (plan-vs-reality note, recorded 2026-09-08): `AnalysisEngine.evaluateCurve` does NOT
 * compute a per-bucket numeric target anywhere -- it only checks (a) the WHOLE-deck average CMC
 * against [ResolvedArchetypeSkeleton.curve]'s `[min,max]` band, and (b) a coarse relative ordering
 * of three zones (low 0-2 / mid 3-4 / high 5+) against [CurveShape]. There is therefore no existing
 * inline computation to "extract" into this file -- what follows is NEW derived logic, not a
 * refactor. It reuses `evaluateCurve`'s OWN zone boundaries (low/mid/high, and the same `mv:0`..
 * `mv:6`/`mv:7plus` bucket ids `AnalysisEngine`'s curve sections already use) so a future consumer
 * (e.g. a Commander builder's placement scorer) targets buckets the Analysis tab already shows,
 * rather than inventing a fourth vocabulary. The per-shape zone RATIOS below are new, clearly
 * scoped constants used ONLY by this file -- nothing in the existing scoring path calls
 * [forSkeleton], so this is zero behavior change by construction (`AnalysisEngine.evaluate` stays
 * untouched, per the plan's standing constraint).
 */
object CurveTargets {

    /** `mv` is `null` for the `mv:7plus` bucket (mirrors `AnalysisEngine.evaluateCurve`'s own
     * `curveSections` ids/labels exactly, so a caller can reuse them as `CardSection` ids).
     * [fraction] is this bucket's share of the deck's non-land count (all 8 buckets sum to `1.0`).
     * [targetCount] is `fraction * nonLandCount` rounded, present only when [forSkeleton] was given
     * a non-null `nonLandCount`. */
    data class CurveBucketTarget(
        val bucketId: String,
        val mv: Int?,
        val fraction: Double,
        val targetCount: Int? = null,
    )

    private const val LOW_BUCKET_COUNT = 3 // mv 0, 1, 2
    private const val MID_BUCKET_COUNT = 2 // mv 3, 4
    private const val HIGH_BUCKET_COUNT = 3 // mv 5, 6, 7plus

    /** Zone shares per [CurveShape] -- (low, mid, high), summing to 1.0. A deliberate, documented
     * judgment call (not fitted to any fixture): FRONT/BACK skew 45/35/20 one way or the other,
     * BELL centers on mid at 25/50/25 -- consistent with the ORDERING `evaluateCurve` already
     * enforces for each shape (FRONT: low >= mid >= high; BELL: mid >= low, mid >= high; BACK:
     * high >= mid >= low). */
    private val ZONE_SHARES: Map<CurveShape, Triple<Double, Double, Double>> = mapOf(
        CurveShape.FRONT to Triple(0.45, 0.35, 0.20),
        CurveShape.BELL to Triple(0.25, 0.50, 0.25),
        CurveShape.BACK to Triple(0.20, 0.35, 0.45),
    )

    /**
     * @param skeleton the resolved skeleton whose [ResolvedArchetypeSkeleton.shape] picks the zone
     *        distribution above. [ResolvedArchetypeSkeleton.curve] itself is not read numerically
     *        here (see this object's KDoc) -- it stays the whole-curve average-CMC band check
     *        `evaluateCurve` already performs; this function only distributes SHARE across buckets.
     * @param nonLandCount when supplied, each bucket's [CurveBucketTarget.targetCount] is populated
     *        (`fraction * nonLandCount`, rounded). `null` (the default) returns fractions only.
     */
    fun forSkeleton(skeleton: ResolvedArchetypeSkeleton, nonLandCount: Int? = null): List<CurveBucketTarget> {
        val (lowShare, midShare, highShare) = ZONE_SHARES.getValue(skeleton.shape)
        val perLowBucket = lowShare / LOW_BUCKET_COUNT
        val perMidBucket = midShare / MID_BUCKET_COUNT
        val perHighBucket = highShare / HIGH_BUCKET_COUNT

        val fractionsByMv = mapOf(
            0 to perLowBucket, 1 to perLowBucket, 2 to perLowBucket,
            3 to perMidBucket, 4 to perMidBucket,
            5 to perHighBucket, 6 to perHighBucket,
        )

        val buckets = (0..6).map { mv ->
            val fraction = fractionsByMv.getValue(mv)
            CurveBucketTarget(
                bucketId = "mv:$mv",
                mv = mv,
                fraction = fraction,
                targetCount = nonLandCount?.let { (fraction * it).roundToInt() },
            )
        }
        val sevenPlus = CurveBucketTarget(
            bucketId = "mv:7plus",
            mv = null,
            fraction = perHighBucket,
            targetCount = nonLandCount?.let { (perHighBucket * it).roundToInt() },
        )
        return buckets + sevenPlus
    }
}
