package com.mmg.manahub.feature.decks.presentation.engine

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.core.domain.model.TagCategory
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

// ═══════════════════════════════════════════════════════════════════════════════
//  DeckScorer
//
//  Single, injectable, configurable engine that replaces MagicScorer + SynergyScorer.
//  It brings what those lacked: role/gap awareness, a power signal, a HARD
//  color/legality filter, a redundancy penalty and localizable reasons.
//
//  API:
//   · profile()    — builds the current deck's "fingerprint".
//   · fit()        — scores a card in the context of a profile.
//   · rankAdds()   — ranks add candidates (hard filter + power floor).
//   · rankCuts()   — ranks deck cards by lowest fit (cut candidates).
//   · evaluate()   — deck health report (coverage, curve, warnings).
// ═══════════════════════════════════════════════════════════════════════════════

@Singleton
class DeckScorer @Inject constructor(
    private val roleClassifier: RoleClassifier,
    private val power: PowerResolver = NeutralPowerResolver,
) {

    // ── Profile ─────────────────────────────────────────────────────────────────

    fun profile(
        mainboard: List<DeckEntry>,
        format: DeckFormat,
        colorIdentity: Set<ManaColor>,
        seedTags: List<CardTag>,
    ): DeckProfile {
        val nonLand = mainboard.filterNot { BasicLandCalculator.isLand(it.card) }
        val nonLandCount = nonLand.sumOf { it.quantity }

        val avgCmc = if (nonLandCount == 0) 0.0
        else nonLand.sumOf { it.card.cmc * it.quantity } / nonLandCount

        val histogram = nonLand
            .groupBy { it.card.cmc.toInt().coerceAtMost(7) }
            .mapValues { (_, e) -> e.sumOf { it.quantity } }

        val roleCounts = mutableMapOf<DeckRole, Int>()
        mainboard.forEach { entry ->
            roleClassifier.classify(entry.card).forEach { role ->
                roleCounts[role] = (roleCounts[role] ?: 0) + entry.quantity
            }
        }

        return DeckProfile(
            format = format,
            colorIdentity = colorIdentity,
            seedTags = seedTags,
            tagFingerprint = fingerprint(nonLand, seedTags),
            roleCounts = roleCounts,
            skeleton = DeckSkeletons.forFormat(format),
            avgCmc = avgCmc,
            curveHistogram = histogram,
            nonLandCount = nonLandCount,
        )
    }

    /** Strategy fingerprint: normalized per-key weight of identity tags + seed. */
    private fun fingerprint(nonLand: List<DeckEntry>, seedTags: List<CardTag>): Map<String, Float> {
        val raw = mutableMapOf<String, Float>()
        nonLand.forEach { entry ->
            (entry.card.tags + entry.card.userTags)
                .filter { it.category in IDENTITY_CATEGORIES }
                .forEach { raw[it.key] = (raw[it.key] ?: 0f) + entry.quantity }
        }
        // Explicit seeds weigh as if they appeared several times.
        seedTags.forEach { raw[it.key] = (raw[it.key] ?: 0f) + 3f }
        val max = raw.values.maxOrNull() ?: return emptyMap()
        return raw.mapValues { (it.value / max).coerceIn(0f, 1f) }
    }

    // ── Fit of a card ─────────────────────────────────────────────────────────────

    fun fit(
        card: Card,
        profile: DeckProfile,
        isOwned: Boolean,
        weights: ScoreWeights = ScoreWeights(),
    ): CardFit {
        val w = weights.normalized()
        val roles = roleClassifier.classify(card)
        val reasons = mutableListOf<ScoreReason>()

        val synergy = synergyScore(card, profile, reasons)
        val roleNeed = roleNeedScore(roles, profile, reasons)
        val curve = curveScore(card, profile, reasons)
        val cardPower = power.powerOf(card)
        val powerScore = cardPower.normalized
        val (withinColor, colorSoft) = colorScore(card, profile, reasons)
        val isLegal = isLegal(card, profile.format)
        val redundancy = redundancyScore(roles, profile, reasons)

        when {
            cardPower.isGameChanger -> reasons += ScoreReason.GameChanger
            powerScore >= 0.75f -> reasons += ScoreReason.HighPower
            powerScore < weights.powerFloor -> reasons += ScoreReason.BelowPowerFloor
        }
        if (isOwned) reasons += ScoreReason.InCollection

        val base = w.synergy * synergy + w.roleNeed * roleNeed + w.curve * curve +
            w.power * powerScore + w.color * colorSoft
        val score = (base - weights.redundancyPenalty * redundancy).coerceIn(0f, 1f)

        return CardFit(
            card = card,
            score = score,
            components = ScoreComponents(synergy, roleNeed, curve, powerScore, colorSoft, redundancy),
            roles = roles,
            reasons = reasons,
            isOwned = isOwned,
            isLegal = isLegal,
            withinColorIdentity = withinColor,
        )
    }

    // ── Add ranking ─────────────────────────────────────────────────────────────

    /**
     * Ranks add candidates. Applies the HARD filter (legality + color identity) and a
     * power floor: below the threshold a card is dropped unless its synergy is
     * exceptional. The collection/budget preference is decided by the use-case layer
     * using [CardFit.isOwned] — the score stays independent of ownership.
     */
    fun rankAdds(
        candidates: List<Card>,
        profile: DeckProfile,
        ownedIds: Set<String>,
        weights: ScoreWeights = ScoreWeights(),
        limit: Int = 50,
    ): List<CardFit> = candidates
        .map { fit(it, profile, isOwned = it.scryfallId in ownedIds, weights = weights) }
        .filter { it.isAddCandidate }
        .filter { it.components.power >= weights.powerFloor || it.components.synergy >= 0.85f }
        .sortedByDescending { it.score }
        .take(limit)

    // ── Cut ranking ───────────────────────────────────────────────────────────────

    /**
     * Ranks the deck's cards by lowest fit (the first ones are the best cut candidates).
     * Protects commander/locks ([protectedIds]) and combo cores automatically.
     */
    fun rankCuts(
        mainboard: List<DeckEntry>,
        profile: DeckProfile,
        protectedIds: Set<String> = emptySet(),
        weights: ScoreWeights = ScoreWeights(),
    ): List<CardFit> = mainboard
        .asSequence()
        .filterNot { BasicLandCalculator.isLand(it.card) }
        .filterNot { it.card.scryfallId in protectedIds }
        .filterNot { it.card.isComboCore() }
        .map { fit(it.card, profile, isOwned = true, weights = weights) }
        .sortedBy { it.score }
        .toList()

    private fun Card.isComboCore(): Boolean =
        (tags + userTags).any { it.key == CardTag.INFINITE.key || it.key == CardTag.COMBO.key }

    // ── Health evaluation ─────────────────────────────────────────────────────────

    fun evaluate(profile: DeckProfile, nonLand: List<DeckEntry>): DeckEvaluation {
        val coverage = profile.skeleton.slots.map { slot ->
            RoleCoverage(slot.role, current = profile.roleCounts[slot.role] ?: 0, ideal = slot.ideal)
        }
        val landCount = profile.roleCounts[DeckRole.LAND] ?: 0
        val targetLands = profile.skeleton.idealFor(DeckRole.LAND)

        val aligned = nonLand.count { entry ->
            (entry.card.tags + entry.card.userTags).any { (profile.tagFingerprint[it.key] ?: 0f) >= 0.4f }
        }
        val synergyDensity = if (nonLand.isEmpty()) 0f else aligned.toFloat() / nonLand.size

        val warnings = buildList {
            if (targetLands > 0 && landCount < profile.skeleton.slots.first { it.role == DeckRole.LAND }.min)
                add(DeckWarning.TooFewLands(landCount, targetLands))
            if (targetLands > 0 && landCount > profile.skeleton.slots.first { it.role == DeckRole.LAND }.max)
                add(DeckWarning.TooManyLands(landCount, targetLands))
            coverage.filter { it.role.isFunctional && it.current == 0 && it.ideal >= 2 }
                .forEach { add(DeckWarning.MissingRole(it.role, it.ideal)) }
            if (profile.avgCmc > 4.0) add(DeckWarning.CurveTooHigh(profile.avgCmc))
            if (profile.nonLandCount > 10 && profile.avgCmc in 0.1..1.8) add(DeckWarning.CurveTooLow(profile.avgCmc))
            if (profile.nonLandCount > 10 && synergyDensity < 0.35f) add(DeckWarning.LowSynergyDensity(synergyDensity))
        }

        return DeckEvaluation(
            roleCoverage = coverage,
            avgCmc = profile.avgCmc,
            curveHistogram = profile.curveHistogram,
            landCount = landCount,
            synergyDensity = synergyDensity,
            healthScore = healthScore(coverage, landCount, targetLands, profile.avgCmc, synergyDensity),
            warnings = warnings,
        )
    }

    // ── Components ──────────────────────────────────────────────────────────────────

    /** Source-weighted tag overlap (user > auto > suggested), smoothed against tag-spam. */
    private fun synergyScore(card: Card, profile: DeckProfile, reasons: MutableList<ScoreReason>): Float {
        if (profile.tagFingerprint.isEmpty() && profile.seedTags.isEmpty()) return 0.5f

        val matched = mutableListOf<CardTag>()
        var weighted = 0f
        var ownWeight = 0f

        fun consider(tag: CardTag, sourceWeight: Float) {
            val fp = profile.tagFingerprint[tag.key] ?: 0f
            ownWeight += sourceWeight
            if (fp > 0f) { weighted += sourceWeight * fp; matched += tag }
        }
        // Dedup by key, keeping the highest-weight source.
        val seen = mutableSetOf<String>()
        card.userTags.forEach { if (seen.add(it.key)) consider(it, 1.2f) }
        card.tags.forEach { if (seen.add(it.key)) consider(it, 1.0f) }
        card.suggestedTags.forEach { s -> if (seen.add(s.tag.key)) consider(s.tag, 0.8f * s.confidence) }

        if (ownWeight == 0f) { reasons += ScoreReason.OffStrategy; return 0f }
        val score = (weighted / sqrt(ownWeight)).coerceIn(0f, 1f) // sqrt = don't reward stacking tags
        if (matched.isNotEmpty()) reasons += ScoreReason.SynergyMatch(matched.distinct())
        else reasons += ScoreReason.OffStrategy
        return score
    }

    /** How much it covers a skeleton gap (max over the card's functional roles). */
    private fun roleNeedScore(roles: Set<DeckRole>, profile: DeckProfile, reasons: MutableList<ScoreReason>): Float {
        var best = 0f
        var bestRole: DeckRole? = null
        roles.filter { it.isFunctional }.forEach { role ->
            val ideal = profile.skeleton.idealFor(role)
            if (ideal <= 0) return@forEach
            val current = profile.roleCounts[role] ?: 0
            val need = ((ideal - current).toFloat() / ideal).coerceIn(0f, 1f)
            if (need > best) { best = need; bestRole = role }
        }
        bestRole?.let { if (best >= 0.4f) reasons += ScoreReason.FillsGap(it) }
        return best
    }

    /** Rewards the CMC buckets under-represented in the current curve; fixes the land-inclusion bug. */
    private fun curveScore(card: Card, profile: DeckProfile, reasons: MutableList<ScoreReason>): Float {
        if (profile.nonLandCount < 10 && card.cmc <= 2.0) { reasons += ScoreReason.OnCurve; return 0.9f }
        val bucket = card.cmc.toInt().coerceAtMost(7)
        val maxBucket = profile.curveHistogram.values.maxOrNull() ?: 0
        val here = profile.curveHistogram[bucket] ?: 0
        val score = (1f - here.toFloat() / (maxBucket + 1)).coerceIn(0f, 1f)
        if (score >= 0.7f) reasons += ScoreReason.CurveGap(bucket) else if (score >= 0.45f) reasons += ScoreReason.OnCurve
        return score
    }

    /** HARD color-identity filter + soft bonus (fixes the soft-zero of the old scorers). */
    private fun colorScore(card: Card, profile: DeckProfile, reasons: MutableList<ScoreReason>): Pair<Boolean, Float> {
        if (profile.colorIdentity.isEmpty()) return true to 1.0f
        if (card.colorIdentity.isEmpty()) { reasons += ScoreReason.Colorless; return true to 0.95f }
        val allowed = profile.colorIdentity.map { it.symbol }.toSet()
        val within = card.colorIdentity.all { it in allowed }
        if (!within) reasons += ScoreReason.OutOfColorIdentity
        return within to if (within) 1.0f else 0.0f
    }

    /** Penalizes adding another role that is already over-covered. */
    private fun redundancyScore(roles: Set<DeckRole>, profile: DeckProfile, reasons: MutableList<ScoreReason>): Float {
        var worst = 0f
        roles.filter { it.isFunctional }.forEach { role ->
            val ideal = profile.skeleton.idealFor(role)
            if (ideal <= 0) return@forEach
            val current = profile.roleCounts[role] ?: 0
            if (current >= ideal) {
                val overflow = ((current - ideal).toFloat() / ideal).coerceIn(0f, 1f)
                if (overflow > worst) { worst = overflow; reasons += ScoreReason.OverCovered(role, current, ideal) }
            }
        }
        return worst
    }

    private fun isLegal(card: Card, format: DeckFormat): Boolean {
        fun ok(s: String) = s.equals("legal", true) || s.equals("restricted", true)
        return when (format) {
            DeckFormat.STANDARD -> ok(card.legalityStandard)
            DeckFormat.COMMANDER -> ok(card.legalityCommander)
            DeckFormat.DRAFT -> true // limited: any card in the set is playable
        }
    }

    private fun healthScore(
        coverage: List<RoleCoverage>,
        landCount: Int,
        targetLands: Int,
        avgCmc: Double,
        synergyDensity: Float,
    ): Int {
        val roleScore = coverage.filter { it.role.isFunctional && it.ideal > 0 }
            .map { (it.current.toFloat() / it.ideal).coerceIn(0f, 1f) }
            .ifEmpty { listOf(1f) }
            .average().toFloat()
        val landScore = if (targetLands <= 0) 1f
        else (1f - kotlin.math.abs(landCount - targetLands).toFloat() / targetLands).coerceIn(0f, 1f)
        val curveOk = if (avgCmc in 1.8..4.0) 1f else 0.6f
        val composite = 0.45f * roleScore + 0.20f * landScore + 0.15f * curveOk + 0.20f * synergyDensity
        return (composite * 100).toInt().coerceIn(0, 100)
    }

    private companion object {
        val IDENTITY_CATEGORIES = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)
    }
}
