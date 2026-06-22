package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer.Companion.FULL_BUCKET_FLOOR
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer.Companion.TRIBE_ABS_THRESHOLD
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer.Companion.TRIBE_SHARE_THRESHOLD
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
    private val manaBaseAnalyzer: ManaBaseAnalyzer = ManaBaseAnalyzer(),
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

        // Quantity × confidence accumulation: a cantrip (CARD_ADVANTAGE @ 0.3) adds
        // 0.3 of a draw slot per copy, not a full one. LAND is full-weight.
        val roleCounts = mutableMapOf<DeckRole, Float>()
        mainboard.forEach { entry ->
            roleClassifier.classify(entry.card).forEach { (role, confidence) ->
                roleCounts[role] = (roleCounts[role] ?: 0f) + entry.quantity * confidence
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

        // B1 — per-tribe granularity. Derive `tribe:<subtype>` keys from creature
        // TYPE LINES (structural, language-neutral; no DB/CardTag change) and add the
        // ones that clear the frequency threshold to the raw fingerprint, so the deck
        // distinguishes Elf from Dragon instead of lumping both under the generic
        // `tribal` STRATEGY key. The threshold is quantity-weighted to stay consistent
        // with the rest of the engine: a subtype enters the fingerprint when it appears
        // on ≥[TRIBE_SHARE_THRESHOLD] of creature COPIES or on ≥[TRIBE_ABS_THRESHOLD]
        // copies (whichever is easier to clear), so both a 60-card 4-of tribal deck and
        // a singleton Commander tribal deck identify their tribe.
        val tribeCopies = mutableMapOf<String, Int>()
        var creatureCopies = 0
        nonLand.forEach { entry ->
            if (entry.card.typeLine.contains("Creature", ignoreCase = true)) {
                creatureCopies += entry.quantity
            }
            TribeDeriver.subtypeKeys(entry.card).forEach { key ->
                tribeCopies[key] = (tribeCopies[key] ?: 0) + entry.quantity
            }
        }
        val clearedTribes = tribeCopies.filter { (_, copies) ->
            copies >= TRIBE_ABS_THRESHOLD ||
                (creatureCopies > 0 && copies.toFloat() / creatureCopies >= TRIBE_SHARE_THRESHOLD)
        }
        clearedTribes.forEach { (key, copies) -> raw[key] = (raw[key] ?: 0f) + copies }

        // When the deck has an identifiable tribe, the coarse generic `tribal` STRATEGY
        // key is SUPERSEDED by the specific `tribe:<x>` keys and must be dropped from the
        // fingerprint — otherwise an off-tribe card that merely carries the generic
        // `tribal` tag (e.g. a Dragon in an Elf deck) would still match the deck's
        // strategy. The specific per-tribe keys now carry the entire tribal signal.
        if (clearedTribes.isNotEmpty()) raw.remove(CardTag.TRIBAL.key)

        // B3 — size-independent seed influence: normalize the deck's OWN identity tags
        // first, then FLOOR every seed key to [SEED_FLOOR] post-normalization. The old
        // pre-normalization "+3f" made a seed dominate an empty deck (3/3 = 1.0) but
        // decay to noise in a tagged 99-card deck (3/30 ≈ 0.1). A post-normalization
        // floor keeps a seed's influence constant regardless of deck size.
        if (raw.isEmpty() && seedTags.isEmpty()) return emptyMap()
        val max = raw.values.maxOrNull() ?: 1f // seeds-only deck: nothing to normalize against
        val normalized = raw.mapValues { (it.value / max).coerceIn(0f, 1f) }.toMutableMap()
        seedTags.forEach { seed ->
            // A generic `tribal` seed is also superseded by the per-tribe keys: floor it
            // only when no specific tribe was derived, so the seed never re-opens the
            // cross-tribe match the per-tribe split just closed.
            if (seed.key == CardTag.TRIBAL.key && clearedTribes.isNotEmpty()) return@forEach
            normalized[seed.key] = maxOf(normalized[seed.key] ?: 0f, SEED_FLOOR)
        }
        return normalized
    }

    // ── Fit of a card ─────────────────────────────────────────────────────────────

    fun fit(
        card: Card,
        profile: DeckProfile,
        isOwned: Boolean,
        weights: ScoreWeights = ScoreWeights(),
    ): CardFit {
        val w = weights.normalized()
        val weightedRoles = roleClassifier.classify(card)
        val roles = weightedRoles.keys
        val reasons = mutableListOf<ScoreReason>()

        val synergy = synergyScore(card, profile, reasons)
        val roleNeed = roleNeedScore(weightedRoles, profile, reasons)
        val curve = curveScore(card, profile, reasons)
        val cardPower = power.powerOf(card)
        val powerScore = cardPower.normalized
        val (withinColor, colorSoft) = colorScore(card, profile, reasons)
        val isLegal = isLegal(card, profile.format)
        val redundancy = redundancyScore(weightedRoles, profile, reasons)

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

    /**
     * @param fullMainboard the COMPLETE mainboard (lands included, quantity-aware) used for
     *        construction validation (plan C5): deck size, the format copy limit, the Commander
     *        singleton rule and off-color-identity cards. Null (the default) SKIPS construction
     *        validation entirely — callers that only have the non-land slice (or want pure health
     *        math) pass nothing and the construction warnings never fire. Basic lands are exempt
     *        from copy limits.
     * @param weights the (optionally debug-tuned) [ScoreWeights] threaded through the Deck Doctor
     *        read path (plan F2). The Health evaluation's composite is intentionally
     *        weight-INDEPENDENT (it scores construction/curve/coverage, not per-card fit), so this
     *        param is accepted purely for API parity with the cut/add ranking paths and to keep a
     *        single tuning entry point. The default [ScoreWeights] guarantees byte-identical output
     *        for every existing caller and test.
     */
    fun evaluate(
        profile: DeckProfile,
        nonLand: List<DeckEntry>,
        fullMainboard: List<DeckEntry>? = null,
        @Suppress("UNUSED_PARAMETER") weights: ScoreWeights = ScoreWeights(),
    ): DeckEvaluation {
        // roleCounts are float (quantity × confidence); RoleCoverage.current is Int, so
        // round each role's weighted count for the coverage view (Phase 3 will move the
        // scoring math onto floats — for Phase 1 we keep the Int-based skeleton ideals).
        val coverage = profile.skeleton.slots.map { slot ->
            RoleCoverage(
                role = slot.role,
                current = Math.round(profile.roleCounts[slot.role] ?: 0f),
                ideal = slot.ideal,
            )
        }
        val landCount = Math.round(profile.roleCounts[DeckRole.LAND] ?: 0f)
        // Phase 5 (C3): the land target is DYNAMIC — the skeleton's flat ideal shifted down
        // by cheap ramp, a low avg CMC and draw density, bounded to the skeleton's land band.
        // So 8 cheap ramp pieces lower the "aim for" land count fed into the land warnings.
        val targetLands = manaBaseAnalyzer.dynamicLandIdeal(profile)

        // Quantity-weighted synergy density (A7): aligned copies / total non-land copies,
        // not distinct-entries / distinct-entries (wrong for 60-card decks with 4-ofs).
        val totalNonLandCopies = nonLand.sumOf { it.quantity }
        val alignedCopies = nonLand.sumOf { entry ->
            // Alignment counts a card's persisted identity tags AND its runtime-derived
            // per-tribe keys (B1) against the fingerprint, so a tribeless lord whose
            // tribal identity comes only from its type line / payoff text still reads as
            // on-strategy in its tribal deck.
            val tagKeys = (entry.card.tags + entry.card.userTags).map { it.key }
            val keys = tagKeys + TribeDeriver.tribeKeys(entry.card)
            val aligned = keys.any { (profile.tagFingerprint[it] ?: 0f) >= 0.4f }
            if (aligned) entry.quantity else 0
        }
        val synergyDensity = if (totalNonLandCopies == 0) 0f
        else alignedCopies.toFloat() / totalNonLandCopies

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

            // C5 — construction validation. Only runs when the FULL mainboard is supplied
            // (quantity-aware, lands included). Skipped otherwise so pure health-math callers
            // and tests are unaffected.
            if (fullMainboard != null) addAll(constructionWarnings(profile, fullMainboard))

            // C3 (Phase 5) — mana-base fixing check. Also needs the FULL mainboard (the land
            // base lives there, not in the non-land slice). Compares each demanded colour's
            // producing sources against a Karsten-style threshold and emits
            // ColorSourceShortage / UnfixedSplash. Skipped for the pure health-math path.
            if (fullMainboard != null) {
                addAll(manaBaseAnalyzer.analyze(fullMainboard, profile).shortages)
            }
        }

        return DeckEvaluation(
            roleCoverage = coverage,
            avgCmc = profile.avgCmc,
            curveHistogram = profile.curveHistogram,
            landCount = landCount,
            synergyDensity = synergyDensity,
            healthScore = healthScore(profile.skeleton, coverage, landCount, targetLands, profile.avgCmc, synergyDensity),
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
            // B2 — the denominator must count IDENTITY-category tags only (the same
            // filter the fingerprint uses). The numerator can only ever match an
            // identity-category fingerprint key, so accumulating KEYWORD/ROLE/TYPE tags
            // into [ownWeight] just diluted heavily-tagged staples and made the score
            // track tag VOLUME instead of strategic ALIGNMENT.
            if (tag.category !in IDENTITY_CATEGORIES) return
            val fp = profile.tagFingerprint[tag.key] ?: 0f
            ownWeight += sourceWeight
            if (fp > 0f) { weighted += sourceWeight * fp; matched += tag }
        }
        // Dedup by key, keeping the highest-weight source.
        val seen = mutableSetOf<String>()
        card.userTags.forEach { if (seen.add(it.key)) consider(it, 1.2f) }
        card.tags.forEach { if (seen.add(it.key)) consider(it, 1.0f) }
        card.suggestedTags.forEach { s -> if (seen.add(s.tag.key)) consider(s.tag, 0.8f * s.confidence) }
        // B1 — the card's OWN per-tribe identity (its creature subtypes + the tribes it
        // pays off, derived at runtime from the type line / oracle text — never from a
        // persisted tag). These are TRIBAL-category identity signal, so they flow into
        // BOTH the numerator and the denominator exactly like a real identity tag: an
        // Elf candidate in an Elf deck matches `tribe:elf`; an off-tribe Dragon adds
        // `tribe:dragon` to its denominator with no matching fingerprint key → it is
        // measured as NOT aligned, never riding in on the generic `tribal` key.
        TribeDeriver.tribeKeys(card).forEach { key ->
            if (seen.add(key)) consider(CardTag(key, TagCategory.TRIBAL), 1.0f)
        }

        if (ownWeight == 0f) { reasons += ScoreReason.OffStrategy; return 0f }
        val score = (weighted / sqrt(ownWeight)).coerceIn(0f, 1f) // sqrt = don't reward stacking tags
        if (matched.isNotEmpty()) reasons += ScoreReason.SynergyMatch(matched.distinct())
        else reasons += ScoreReason.OffStrategy
        return score
    }

    /** How much it covers a skeleton gap (max over the card's functional roles). */
    private fun roleNeedScore(roles: Map<DeckRole, Float>, profile: DeckProfile, reasons: MutableList<ScoreReason>): Float {
        var best = 0f
        var bestRole: DeckRole? = null
        roles.keys.filter { it.isFunctional }.forEach { role ->
            val ideal = profile.skeleton.idealFor(role)
            if (ideal <= 0) return@forEach
            val current = profile.roleCounts[role] ?: 0f
            val need = ((ideal - current) / ideal).coerceIn(0f, 1f)
            if (need > best) { best = need; bestRole = role }
        }
        bestRole?.let { if (best >= 0.4f) reasons += ScoreReason.FillsGap(it) }
        return best
    }

    /**
     * Scores a card's CMC bucket against the format's TARGET curve (plan C1/C2).
     *
     * The old version rewarded a bucket by how empty it was *relative to the deck's
     * own busiest bucket* — so an 8-drop in an all-cheap deck scored 1.0, actively
     * nudging decks toward flat curves and big spells. Now we compare the deck's
     * current share of each bucket to the [DeckSkeleton.targetCurve] the format wants:
     *
     *   score = unmet target demand for the bucket / target demand for the bucket
     *
     * so a bucket the curve does NOT want at all (target ≈ 0) scores 0 even when empty
     * (the C1 fix), and a bucket the curve wants but the deck has not filled scores high.
     * An over-filled bucket (current share ≥ target) collapses to [FULL_BUCKET_FLOOR]:
     * a card occupying a legitimately-wanted-but-already-full bucket is a WEAK signal,
     * not equal to off-curve filler — otherwise a premium, role-satisfied staple in a
     * crowded cheap bucket would tie with vanilla filler and surface as a top cut.
     */
    private fun curveScore(card: Card, profile: DeckProfile, reasons: MutableList<ScoreReason>): Float {
        // Tiny decks: the curve isn't meaningful yet — keep cheap cards attractive.
        if (profile.nonLandCount < 10 && card.cmc <= 2.0) { reasons += ScoreReason.OnCurve; return 0.9f }

        val bucket = card.cmc.toInt().coerceAtMost(7)
        val target = profile.skeleton.targetShareFor(bucket)
        if (target <= 0f) return 0f // the format's curve does not want this bucket at all

        val here = profile.curveHistogram[bucket] ?: 0
        val currentShare = if (profile.nonLandCount <= 0) 0f
        else here.toFloat() / profile.nonLandCount
        // Fraction of this bucket's target demand the deck has not yet met, floored so a
        // wanted-but-full bucket stays distinguishable from a curve-unwanted bucket.
        val unmet = ((target - currentShare) / target).coerceIn(0f, 1f)
        val score = maxOf(unmet, FULL_BUCKET_FLOOR)

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

    /**
     * Penalizes adding to a role that is genuinely over-covered — i.e. ALREADY PAST its
     * skeleton `max`, not merely at or above `ideal`.
     *
     * Phase-3 (plan C4/item 5): the healthy band is `[ideal, max]`; a role anywhere in
     * that band is correctly stocked and must NOT be penalised. The old `current >= ideal`
     * trigger punished perfectly-good staples sitting exactly at their ideal slot — and
     * with the weighted (Float) `roleCounts`, a small spurious cross-credit (e.g. a typed-
     * land tutor pattern bleeding 0.85 into TUTOR) tipped a 2-tutor deck "over" ideal and
     * dragged a premium tutor into the cut list. Penalising only past `max` keeps the
     * redundancy signal consistent with the symmetric coverage used in `healthScore`.
     */
    private fun redundancyScore(roles: Map<DeckRole, Float>, profile: DeckProfile, reasons: MutableList<ScoreReason>): Float {
        var worst = 0f
        roles.keys.filter { it.isFunctional }.forEach { role ->
            val ideal = profile.skeleton.idealFor(role)
            if (ideal <= 0) return@forEach
            val max = profile.skeleton.maxFor(role).coerceAtLeast(ideal)
            val current = profile.roleCounts[role] ?: 0f
            if (current > max) {
                val overflow = ((current - max) / max).coerceIn(0f, 1f)
                if (overflow > worst) { worst = overflow; reasons += ScoreReason.OverCovered(role, Math.round(current), ideal) }
            }
        }
        return worst
    }

    /**
     * Quantity-aware deck-construction validation (plan C5). Produces, in a stable order:
     *  · [DeckWarning.DeckTooSmall] when the total mainboard count is below the format minimum
     *    (60 for constructed, 100 incl. the commander for Commander). DRAFT/CASUAL are exempt
     *    (limited decks legitimately run 40; Casual is intentionally permissive).
     *  · [DeckWarning.SingletonViolation] / [DeckWarning.TooManyCopies] for any NON-BASIC card
     *    over the format copy limit (1 for Commander → singleton, 4 for constructed). Basic lands
     *    are exempt (unlimited copies are always legal).
     *  · [DeckWarning.OffColorIdentity] for any Commander card whose color identity is not a subset
     *    of the deck's [DeckProfile.colorIdentity].
     *
     * Cards are grouped by name so multiple printings of the same card (different scryfallIds)
     * still count toward one copy-limit check (the 4-copy rule is by card name, not printing).
     */
    private fun constructionWarnings(
        profile: DeckProfile,
        mainboard: List<DeckEntry>,
    ): List<DeckWarning> = buildList {
        val format = profile.format
        val totalCards = mainboard.sumOf { it.quantity }

        // ── Deck size ────────────────────────────────────────────────────────────
        // CASUAL & DRAFT have no enforced minimum here.
        if (format != DeckFormat.CASUAL && format != DeckFormat.DRAFT && totalCards > 0) {
            val minimum = format.targetDeckSize
            if (totalCards < minimum) add(DeckWarning.DeckTooSmall(totalCards, minimum))
        }

        // ── Copy limit (by card name; basics exempt) ─────────────────────────────
        val maxCopies = format.maxCopies
        if (maxCopies in 1..98) { // 99 (Draft) = effectively unlimited → skip
            mainboard
                .filterNot { BasicLandCalculator.isLand(it.card) && it.card.typeLine.contains("Basic", ignoreCase = true) }
                .groupBy { it.card.name }
                .forEach { (name, entries) ->
                    val copies = entries.sumOf { it.quantity }
                    if (copies > maxCopies) {
                        if (format.uniqueCards) add(DeckWarning.SingletonViolation(name, copies))
                        else add(DeckWarning.TooManyCopies(name, copies, maxCopies))
                    }
                }
        }

        // ── Off-color identity (Commander only) ──────────────────────────────────
        if (format == DeckFormat.COMMANDER && profile.colorIdentity.isNotEmpty()) {
            val allowed = profile.colorIdentity.map { it.symbol }.toSet()
            mainboard
                .map { it.card }
                .distinctBy { it.scryfallId }
                .filter { card -> card.colorIdentity.isNotEmpty() && card.colorIdentity.any { it !in allowed } }
                .forEach { add(DeckWarning.OffColorIdentity(it.name)) }
        }
    }

    private fun isLegal(card: Card, format: DeckFormat): Boolean {
        // "restricted" (Vintage) is still legal (1 copy). CASUAL/DRAFT are permissive.
        fun ok(s: String) = s.equals("legal", true) || s.equals("restricted", true)
        return when (format) {
            /*DeckFormat.STANDARD -> ok(card.legalityStandard)
            DeckFormat.PIONEER -> ok(card.legalityPioneer)
            DeckFormat.MODERN -> ok(card.legalityModern)
            DeckFormat.LEGACY -> ok(card.legalityLegacy)
            DeckFormat.VINTAGE -> ok(card.legalityVintage)
            DeckFormat.PAUPER -> ok(card.legalityPauper)*/
            DeckFormat.COMMANDER -> ok(card.legalityCommander)
            DeckFormat.CASUAL -> true // permissive: no legality restriction
            DeckFormat.DRAFT -> true // limited: any card in the set is playable
        }
    }

    /**
     * Composite 0..100 deck-health score. Phase-3 (plan C4) fixes:
     *  · Over-coverage is now PENALISED. Each functional role contributes a symmetric
     *    ratio: full credit inside the healthy `[ideal, max]` band, falling off as the
     *    count climbs past `max` (`max / current`). So 20 board wipes (ideal 4 / max 6)
     *    no longer read as a "healthy" role — they score `6/20 = 0.3`, not 1.0.
     *  · `curveOk` is CONTINUOUS — the normalized distance of `avgCmc` from the format's
     *    [DeckSkeleton.cmcBand], not the old binary 1.0/0.6.
     *  · The CMC band is per-format (Commander runs naturally higher than Standard/Draft),
     *    carried on the skeleton instead of a hardcoded 1.8..4.0 for every format.
     */
    private fun healthScore(
        skeleton: DeckSkeleton,
        coverage: List<RoleCoverage>,
        landCount: Int,
        targetLands: Int,
        avgCmc: Double,
        synergyDensity: Float,
    ): Int {
        val roleScore = coverage.filter { it.role.isFunctional && it.ideal > 0 }
            .map { cov ->
                val max = skeleton.maxFor(cov.role).coerceAtLeast(cov.ideal)
                roleFitRatio(cov.current, cov.ideal, max)
            }
            .ifEmpty { listOf(1f) }
            .average().toFloat()
        val landScore = if (targetLands <= 0) 1f
        else (1f - kotlin.math.abs(landCount - targetLands).toFloat() / targetLands).coerceIn(0f, 1f)
        val curveOk = curveBandScore(avgCmc, skeleton.cmcBand)
        val composite = 0.45f * roleScore + 0.20f * landScore + 0.15f * curveOk + 0.20f * synergyDensity
        return (composite * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Symmetric coverage fit in [0,1]: ramps up to 1.0 as `current` reaches `ideal`,
     * stays 1.0 across the healthy `[ideal, max]` band, then DROPS as `current` exceeds
     * `max` (over-coverage). Anchors the C4 over-coverage penalty.
     */
    private fun roleFitRatio(current: Int, ideal: Int, max: Int): Float = when {
        ideal <= 0 -> 1f
        current <= ideal -> (current.toFloat() / ideal).coerceIn(0f, 1f)
        current <= max -> 1f
        else -> (max.toFloat() / current).coerceIn(0f, 1f)
    }

    /**
     * Continuous curve-band score in [0,1] (C4): 1.0 inside [band], decaying linearly
     * with the relative distance below/above the band edges.
     */
    private fun curveBandScore(avgCmc: Double, band: ClosedFloatingPointRange<Double>): Float = when {
        avgCmc in band -> 1f
        avgCmc < band.start ->
            (1.0 - (band.start - avgCmc) / band.start).coerceIn(0.0, 1.0).toFloat()
        else ->
            (1.0 - (avgCmc - band.endInclusive) / band.endInclusive).coerceIn(0.0, 1.0).toFloat()
    }

    private companion object {
        val IDENTITY_CATEGORIES = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)

        /**
         * Post-normalization floor applied to every seed key in the fingerprint (B3).
         * A seed always ends at ≥ this weight, so its strategic influence is constant
         * regardless of how many identity tags the rest of the deck carries.
         */
        const val SEED_FLOOR = 0.6f

        /**
         * Floor for the curve score of a card whose CMC bucket the format's curve WANTS
         * (target > 0) but the deck has already filled. Keeps such a card above a card
         * in a bucket the curve does not want at all (which scores 0), so a premium,
         * role-satisfied staple in a crowded cheap bucket is not mistaken for filler.
         * Strictly below 0.15 so the "crowded bucket scores near 0" unit test holds.
         */
        const val FULL_BUCKET_FLOOR = 0.1f

        /**
         * B1 per-tribe fingerprint thresholds. A creature subtype becomes a `tribe:<x>`
         * fingerprint key when it appears on at least [TRIBE_SHARE_THRESHOLD] of the
         * deck's creature COPIES *or* on at least [TRIBE_ABS_THRESHOLD] copies — whichever
         * is easier to clear. The OR keeps both deck shapes covered: a 60-card constructed
         * tribal deck clears the 8-copy absolute bar with two 4-ofs even at a low share,
         * while a singleton Commander deck clears the 15 % share bar with ~5 of 33
         * creatures. The threshold is intentionally permissive — a subtype the deck does
         * NOT lean on (a one-off body) stays out, but anything the deck is built around
         * gets its own key, which is exactly the granularity B1 needs.
         */
        const val TRIBE_SHARE_THRESHOLD = 0.15f
        const val TRIBE_ABS_THRESHOLD = 8
    }
}
