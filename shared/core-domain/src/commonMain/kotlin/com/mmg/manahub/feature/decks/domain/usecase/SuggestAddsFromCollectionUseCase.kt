package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeData
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.ColorRoleAffinity
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
import com.mmg.manahub.feature.decks.domain.engine.ScoreReason
import com.mmg.manahub.feature.decks.domain.engine.ScoreWeights
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Motor A of the Deck Doctor Community/Archetype plan (Phase 2,
 * `docs/claude-code-prompt-deck-doctor-community.md`): OFFLINE, ALWAYS-AVAILABLE add suggestions
 * drawn ONLY from the user's owned collection — no network call, no flag gate (unlike the future
 * Cloudflare-Worker-backed Motor B, `SuggestAddsFromCommunityUseCase`, Phase 4).
 *
 * ## Reuse, not reinvention
 * The base fit — legality + color-identity HARD filter, gap-role fill against the format's GENERIC
 * skeleton, tag synergy against the deck fingerprint, curve fit and the power/redundancy terms —
 * is computed by the EXISTING [DeckScorer.rankAdds] engine call, untouched (this use case never
 * reimplements or forks any of [DeckScorer]'s scoring math; see CLAUDE.md's Deck Doctor "golden
 * ordering invariants" rule). On top of that base [CardFit.score] this use case ADDITIVELY layers
 * two archetype-aware Phase-2 terms and re-sorts:
 *  - a THEME-ROLE BONUS when [resolvedSkeleton] is non-null (i.e. the deck resolved to a
 *    specialized archetype/theme, not GENERIC/no-themes — mirrors
 *    [EvaluateDeckUseCase]'s own additive-only-when-specialized branch so the GENERIC path's
 *    ranking is driven by [DeckScorer.rankAdds] alone, byte-identical to pre-Phase-2 behavior).
 *    Deck Wizard & Engine Rework plan (WS9.5): when the gapped role is one of
 *    [ColorRoleAffinity.INTERACTION_ROLES], this bonus is additionally SCALED by how well the
 *    candidate's OWN casting colour(s) fill that role ([ColorRoleAffinity.affinity]) — a Grixis
 *    deck short on board wipes ranks a Red damage-sweep/Black edict (SECONDARY) candidate above a
 *    Blue mass-bounce (SUBSTITUTE) one, all three legal within the same identity;
 *  - a PIP-INTENSITY MULTIPLIER (D14) that softly penalizes a candidate whose heaviest single-color
 *    mana-symbol commitment is expensive relative to the deck's color count, reusing
 *    [ManaBaseAnalyzer.maxSinglePipIntensity] rather than re-parsing mana costs;
 *  - (WS9.5) a MANABASE-SHORTAGE PENALTY: on top of the color-COUNT-only pip multiplier above, a
 *    candidate whose heaviest pip colour the deck's OWN measured sources ([ManaBaseAnalyzer
 *    .analyze]) cannot reliably support is penalized further — "don't suggest a triple-pip card
 *    into a manabase that's already short on that colour";
 *  - (WS9.5) a MANA-FIX PRIORITY BONUS: when the identity carries >= 3 colors AND the manabase
 *    report shows an active shortage, a candidate classified under [ArchetypeData.MANA_FIX_KEY]
 *    (rocks AND fixing lands, via [ArchetypeRoleClassifier]) is boosted so fixing suggestions rank
 *    ABOVE plain spell adds — fixing is the highest-leverage fix for a shaky multicolor manabase.
 *
 * ## D14 — fail-closed on unknown color identity (Commander only)
 * [DeckScorer]'s own `colorScore` treats an EMPTY [Card.colorIdentity] as "within identity" (a
 * genuinely colorless card, e.g. a Sol Ring, is legal in any Commander deck) — that is correct and
 * untouched. D14 is a NARROWER, additional guard for a card whose identity data looks UNRESOLVED:
 * by Magic's own rules `colorIdentity` is always a superset of `colors` (any color in the mana cost
 * is part of the identity), so [hasUnresolvedColorIdentity] flags a card where that invariant does
 * not hold as fail-closed-not-suggestible in Commander. The [Card] model carries no explicit
 * "unknown" sentinel (Phase 0 added `colors`/`colorIdentity`/`producedMana` as plain non-null
 * fields), so this structural inconsistency is the chosen, defensible proxy — see
 * `project_deck_doctor_phase2_motor_a` memory for the full reasoning.
 *
 * ## Copy limits (plan D3 precedent)
 * A candidate is dropped when the deck's mainboard ALREADY holds `maxCopies` (by NAME, not
 * printing — the same card under a different Scryfall printing still counts) of it; basic lands are
 * exempt (unlimited copies are always legal). Multi-copy formats get a playset top-up suggestion
 * (`maxCopies − alreadyOwnedCopies`, clamped to `[1, maxCopies]`); Commander/singleton stays at 1 —
 * mirrors the retired `SuggestAddsWithBudgetUseCase`'s D3 logic exactly.
 *
 * ## Determinism
 * The final ranking is fully re-sorted by an EXPLICIT comparator (score desc, then card name, then
 * scryfallId) rather than relying on [DeckScorer.rankAdds]'s own stable sort over whatever order
 * [collection] arrived in (a Room query with no `ORDER BY` is not guaranteed stable across runs) —
 * two calls with the same card DATA always produce the same suggestion order.
 */
class SuggestAddsFromCollectionUseCase(
    private val deckScorer: DeckScorer,
    private val manaBaseAnalyzer: ManaBaseAnalyzer = ManaBaseAnalyzer(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    /**
     * @param collection the user's owned cards (one [Card] per distinct scryfallId).
     * @param mainboard the deck's current resolved mainboard entries (lands included — used to
     *        derive the copy-limit-by-name map and, when [resolvedSkeleton] is set, the archetype
     *        role-count snapshot the theme-role bonus gaps against).
     * @param profile the deck profile (color identity, format, fingerprint, GENERIC skeleton) —
     *        reuse the one built by [EvaluateDeckUseCase].
     * @param resolvedSkeleton the archetype/theme-resolved skeleton
     *        ([com.mmg.manahub.feature.decks.domain.engine.ArchetypeSkeletonResolver.resolveWithColor]),
     *        or `null` when the deck is GENERIC with no themes (Draft, or a deck with no archetype
     *        signal) — `null` disables the theme-role bonus entirely, so a GENERIC deck's ranking is
     *        driven by [DeckScorer.rankAdds] plus only the pip multiplier.
     * @param weights scoring weights (defaults to the engine's tuned defaults).
     * @param limit maximum number of suggestions to return.
     * @return suggestions sorted best-fit-first, all [AddOrigin.COLLECTION] (every candidate is
     *         already owned, so the (now-retired) `BudgetOptimizer` never needed to run against
     *         this source).
     */
    suspend operator fun invoke(
        collection: List<Card>,
        mainboard: List<DeckEntry>,
        profile: DeckProfile,
        resolvedSkeleton: ResolvedArchetypeSkeleton? = null,
        weights: ScoreWeights = ScoreWeights(),
        limit: Int = 50,
    ): List<AddSuggestion> = withContext(ioDispatcher) {
        val mainboardIds = mainboard.mapTo(HashSet()) { it.card.scryfallId }
        val mainboardCopiesByName = mainboard
            .groupBy { it.card.name }
            .mapValues { (_, entries) -> entries.sumOf { it.quantity } }
        val maxCopies = profile.format.maxCopies

        // ── Candidate pool: owned, not already in the deck, under the copy limit, and (Commander
        //    only) not fail-closed on an unresolved color identity (D14). ─────────────────────
        val candidates = collection
            .filterNot { it.scryfallId in mainboardIds }
            .distinctBy { it.scryfallId }
            .filterNot { card -> isOverCopyLimit(card, mainboardCopiesByName, maxCopies) }
            .filterNot { card -> profile.format == DeckFormat.COMMANDER && card.hasUnresolvedColorIdentity() }

        // Every candidate here comes from the collection, so ownedIds == the whole pool.
        val ownedIds = candidates.mapTo(HashSet()) { it.scryfallId }

        // Base fit: DeckScorer's own HARD legality + color filter, power floor, gap-role/synergy/
        // curve scoring against the profile's GENERIC skeleton — untouched, no scoring-path change.
        // No `limit` truncation here: the theme-role bonus / pip multiplier below can still reorder
        // the tail, so every legal candidate is re-scored before the final cut.
        val ranked = deckScorer.rankAdds(
            candidates = candidates,
            profile = profile,
            ownedIds = ownedIds,
            weights = weights,
            limit = candidates.size.coerceAtLeast(1),
        )

        val currentRoleCounts: Map<RoleKey, Int> =
            if (resolvedSkeleton != null) ArchetypeRoleClassifier.deckRoleCounts(mainboard) else emptyMap()
        val colorCount = profile.colorIdentity.count { it != ManaColor.C }
        // WS9.5: computed ONCE per call (not per-candidate) -- the deck's OWN measured mana-base
        // reality, reused by both the shortage penalty and the fixing-priority bonus below.
        val manaBaseReport = manaBaseAnalyzer.analyze(mainboard, profile)
        val totalLands = mainboard.filter { BasicLandCalculator.isLand(it.card) }.sumOf { it.quantity }

        val rescored = ranked.map { fit ->
            val themeBonus = themeRoleBonus(fit.card, resolvedSkeleton, currentRoleCounts)
            val multiplier = pipIntensityMultiplier(fit.card, colorCount)
            val shortagePenalty = manaBaseShortagePenalty(fit.card, colorCount, manaBaseReport, totalLands)
            val fixBonus = manaFixPriorityBonus(fit.card, colorCount, manaBaseReport)
            // WS8.2: name the band this candidate fills whenever the theme-role bonus actually
            // fired -- the bonus and the reason always travel together, never one without the
            // other (resolvedSkeleton is guaranteed non-null here since themeBonus.roleKey is only
            // ever set inside that non-null branch of themeRoleBonus).
            val reasons = if (themeBonus.roleKey != null) {
                fit.reasons + ScoreReason.FillsArchetypeGap(
                    roleKey = themeBonus.roleKey,
                    current = themeBonus.current,
                    ideal = themeBonus.ideal,
                    planLabel = resolvedSkeleton?.planLabel().orEmpty(),
                )
            } else {
                fit.reasons
            }
            fit.copy(
                score = ((fit.score + themeBonus.bonus + fixBonus).coerceIn(0f, 1f)) * multiplier * shortagePenalty,
                reasons = reasons,
            )
        }

        val sorted = rescored.sortedWith(
            compareByDescending<CardFit> { it.score }
                .thenBy { it.card.name }
                .thenBy { it.card.scryfallId }
        ).take(limit)

        val multiCopyFormat = profile.format.isSixtyCardConstructed
        sorted.map { fit ->
            val copies = if (multiCopyFormat) {
                val already = mainboardCopiesByName[fit.card.name] ?: 0
                (maxCopies - already).coerceIn(1, maxCopies)
            } else {
                1
            }
            AddSuggestion(fit = fit, origin = AddOrigin.COLLECTION, suggestedCopies = copies)
        }
    }

    /** True for a NON-basic card whose deck NAME count already reaches the format's copy limit. */
    private fun isOverCopyLimit(card: Card, copiesByName: Map<String, Int>, maxCopies: Int): Boolean {
        if (maxCopies !in 1..98) return false // Draft (99) is effectively unlimited.
        if (BasicLandCalculator.isLand(card) && card.typeLine.contains("Basic", ignoreCase = true)) return false
        return (copiesByName[card.name] ?: 0) >= maxCopies
    }

    /**
     * D14 fail-closed proxy: `colorIdentity` must always be a superset of `colors` (any colored
     * mana-cost symbol is, by Magic's own rules, part of the card's color identity). A card that
     * violates this invariant carries UNRESOLVED/stale identity data — never suggest it in
     * Commander, where an incorrect identity could break the deck's fundamental legality.
     */
    private fun Card.hasUnresolvedColorIdentity(): Boolean = colors.any { it !in colorIdentity }

    /**
     * WS8.2 -- [themeRoleBonus]'s result, carrying not just the additive [bonus] but WHICH role it
     * came from (so a [ScoreReason.FillsArchetypeGap] can name the exact band, e.g. "Aggro wants
     * 24+ threats -- you have 19"). [roleKey] is `null` whenever [bonus] is `0f` (no gapped role
     * matched, or [ResolvedArchetypeSkeleton] itself was `null`) -- the bonus and a nameable reason
     * always travel together.
     */
    private data class ThemeRoleBonus(val bonus: Float, val roleKey: RoleKey?, val current: Int, val ideal: Int)

    /**
     * Additive bonus for a candidate that fills a GAP role in the resolved archetype/theme
     * skeleton (`current < ideal`), scaled by the candidate's own [ArchetypeRoleClassifier]
     * confidence for that role and by how large the gap still is. Anti-roles never earn a bonus
     * (adding to a role the archetype actively avoids is never rewarded here — the redundancy/
     * anti-role warning path in [com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase]
     * already discourages it). Returns a zero [ThemeRoleBonus] when [resolvedSkeleton] is `null`
     * (GENERIC/no themes) or the card matches no gapped role — the base [DeckScorer] score is
     * untouched in that case.
     */
    private fun themeRoleBonus(
        card: Card,
        resolvedSkeleton: ResolvedArchetypeSkeleton?,
        currentRoleCounts: Map<RoleKey, Int>,
    ): ThemeRoleBonus {
        if (resolvedSkeleton == null) return ThemeRoleBonus(0f, null, 0, 0)
        val cardRoles = ArchetypeRoleClassifier.classify(card)
        if (cardRoles.isEmpty()) return ThemeRoleBonus(0f, null, 0, 0)
        var best = 0f
        var bestKey: RoleKey? = null
        var bestCurrent = 0
        var bestIdeal = 0
        resolvedSkeleton.roleTargets.forEach { (key, target) ->
            if (key in resolvedSkeleton.antiRoles) return@forEach
            val confidence = cardRoles[key] ?: return@forEach
            val current = currentRoleCounts[key] ?: 0
            if (current >= target.ideal) return@forEach
            val idealFloor = target.ideal.coerceAtLeast(1)
            val gapRatio = ((target.ideal - current).toFloat() / idealFloor).coerceIn(0f, 1f)
            // WS9.5: scale by the candidate's OWN colour-pie fit for this gap role when it's one
            // of the researched interaction roles (see colorAffinityScale's KDoc).
            val colorScale = colorAffinityScale(card, key)
            val bonus = confidence * gapRatio * colorScale
            if (bonus > best) {
                best = bonus
                bestKey = key
                bestCurrent = current
                bestIdeal = target.ideal
            }
        }
        return ThemeRoleBonus(best * THEME_ROLE_BONUS_WEIGHT, bestKey, bestCurrent, bestIdeal)
    }

    /**
     * Deck Wizard & Engine Rework plan, Workstream 9.5: scales a gap-role bonus by how well
     * [card]'s OWN casting colour(s) fill [role], for [role]s the plan explicitly calls out
     * ([ColorRoleAffinity.INTERACTION_ROLES] — removal_spot/removal_mass/counterspell/protection/
     * recursion). A multicolor card takes its BEST colour's affinity (the most favourable reading
     * — a gold card that includes a PRIMARY colour for the role is not penalized for also
     * including a weaker one). Returns `1f` (neutral, no scaling) for every role OUTSIDE that set,
     * an untabled role, or a colourless card — this must never penalize a role/card pair WS9.1 has
     * no basis to judge, and must never touch the GENERIC-path scoring (this function is only ever
     * reached from inside [themeRoleBonus], itself gated on a non-null resolved skeleton).
     */
    private fun colorAffinityScale(card: Card, role: RoleKey): Float {
        if (role !in ColorRoleAffinity.INTERACTION_ROLES) return 1f
        val cardColors = card.colors.mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }
        if (cardColors.isEmpty()) return 1f
        val bestLevel = cardColors.map { ColorRoleAffinity.affinity(it, role) }.minByOrNull { it.ordinal } ?: return 1f
        return when (bestLevel) {
            ColorRoleAffinity.ColorAffinityLevel.PRIMARY -> 1f
            ColorRoleAffinity.ColorAffinityLevel.SECONDARY -> INTERACTION_COLOR_SCALE_SECONDARY
            ColorRoleAffinity.ColorAffinityLevel.SUBSTITUTE -> INTERACTION_COLOR_SCALE_SUBSTITUTE
            ColorRoleAffinity.ColorAffinityLevel.ABSENT -> INTERACTION_COLOR_SCALE_ABSENT
        }
    }

    /**
     * Penalizes a candidate whose heaviest single-color pip commitment is expensive relative to
     * the deck's color COUNT (D14: "penalizes pip intensity incompatible with the deck's color
     * profile"). Reuses [ManaBaseAnalyzer.maxSinglePipIntensity] (never re-parses mana symbols) by
     * wrapping the single [card] in its own one-copy [DeckEntry] and reading the heaviest color's
     * intensity back out.
     *
     * A mono-color deck (`colorCount <= 1`) never penalizes — there is no "off-color-leaning" risk
     * when the deck only wants one color to begin with. A HYBRID cost (`manaCost` contains `/`) is
     * treated as more flexible (lower per-pip penalty): [ManaBaseAnalyzer.maxSinglePipIntensity]
     * counts a hybrid symbol toward BOTH of its colors (a conservative reading for the mana-base
     * FIXING check it was built for), which is the opposite of what a SUGGESTION penalty wants — a
     * hybrid pip is easier to cast than a hard-colored one, so it earns a lighter multiplier here.
     * No new mana-symbol parsing: the hybrid flag is a coarse, honest substring check.
     */
    private fun pipIntensityMultiplier(card: Card, colorCount: Int): Float {
        if (colorCount <= 1) return 1f
        val ownEntry = listOf(DeckEntry(card = card, quantity = 1, isOwned = true, isSideboard = false))
        val maxPip = manaBaseAnalyzer.maxSinglePipIntensity(ownEntry).values.maxOrNull() ?: 0
        if (maxPip <= 1) return 1f
        val isHybridFlexible = card.manaCost?.contains('/') == true
        val perPipPenalty = if (isHybridFlexible) HYBRID_PIP_PENALTY else MONO_PIP_PENALTY
        val penalty = (maxPip - 1) * perPipPenalty * (colorCount - 1)
        return (1f - penalty).coerceIn(PIP_MULTIPLIER_FLOOR, 1f)
    }

    /**
     * Workstream 9.5 -- a SOFT penalty distinct from [pipIntensityMultiplier]: that term penalizes
     * purely by colour COUNT (never looks at the manabase itself), this one penalizes a candidate
     * whose heaviest pip colour the deck's OWN [manaBaseReport] shows is ALREADY short on sources
     * ("don't suggest a triple-pip card into a manabase that's already short on that colour" — the
     * Karsten data is already computed by [ManaBaseAnalyzer.analyze], never re-simulated here).
     * Neutral (`1f`) when the candidate has no colour pips, when its OWN pip-intensity tier is
     * already adequately sourced, or on a mono/no-color deck ([colorCount] `< 2`, same gate as
     * [pipIntensityMultiplier]). Deliberately uses [ManaBaseAnalyzer.requiredSources] keyed on THIS
     * candidate's OWN pip intensity (not [ManaBaseAnalyzer.ManaBaseReport.requiredByColor], which
     * is keyed on the deck-wide heaviest card for that colour) — a cheap single-pip candidate must
     * not be penalized just because some OTHER card already in the deck is triple-pip in that
     * colour; only a candidate whose OWN cost the measured sources can't support is penalized.
     */
    private fun manaBaseShortagePenalty(
        card: Card,
        colorCount: Int,
        manaBaseReport: ManaBaseAnalyzer.ManaBaseReport,
        totalLands: Int,
    ): Float {
        if (colorCount < 2) return 1f
        val ownEntry = listOf(DeckEntry(card = card, quantity = 1, isOwned = true, isSideboard = false))
        val (color, intensity) = manaBaseAnalyzer.maxSinglePipIntensity(ownEntry).maxByOrNull { it.value } ?: return 1f
        if (intensity <= 0) return 1f
        // Only penalize when the deck ALREADY demands this colour elsewhere -- an empty/near-empty
        // deck (nothing to compare against yet) must never penalize every multi-pip candidate just
        // because no lands have been placed for that colour yet.
        if (color !in manaBaseReport.requiredByColor) return 1f
        val have = manaBaseReport.sourcesByColor[color] ?: 0
        val need = manaBaseAnalyzer.requiredSources(intensity, totalLands)
        if (have >= need) return 1f
        val shortageRatio = (have.toFloat() / need).coerceIn(0f, 1f)
        return (SHORTAGE_PENALTY_FLOOR + (1f - SHORTAGE_PENALTY_FLOOR) * shortageRatio).coerceIn(SHORTAGE_PENALTY_FLOOR, 1f)
    }

    /**
     * Workstream 9.5 -- when the identity carries >= 3 colours AND [manaBaseReport] shows an
     * ACTIVE shortage, boosts a candidate classified under [ArchetypeData.MANA_FIX_KEY] (rocks AND
     * fixing lands — [ArchetypeRoleClassifier]'s `manaFixMatcher` already covers both) so fixing
     * suggestions rank ABOVE plain spell adds: "fixing is the highest-leverage fix for a shaky
     * multicolor manabase" (plan). Neutral (`0f`) below 3 colours, with no active shortage, or for
     * a non-fixing candidate.
     */
    private fun manaFixPriorityBonus(card: Card, colorCount: Int, manaBaseReport: ManaBaseAnalyzer.ManaBaseReport): Float {
        if (colorCount < 3 || manaBaseReport.shortages.isEmpty()) return 0f
        val fixConfidence = ArchetypeRoleClassifier.classify(card)[ArchetypeData.MANA_FIX_KEY] ?: 0f
        if (fixConfidence <= 0f) return 0f
        return MANA_FIX_PRIORITY_BONUS * fixConfidence
    }

    private companion object {
        /** Additive weight applied to the best theme-role gap bonus (kept small vs. the engine's
         * own ~0.2-0.34 term weights — this is a nudge on top of the base fit, not a new pillar). */
        const val THEME_ROLE_BONUS_WEIGHT = 0.15f

        /** Per-extra-pip, per-extra-color penalty for a HARD-colored pip beyond the first. */
        const val MONO_PIP_PENALTY = 0.06f

        /** Per-extra-pip, per-extra-color penalty for a card carrying a hybrid symbol — lighter,
         * since a hybrid pip is payable in more than one color. */
        const val HYBRID_PIP_PENALTY = 0.03f

        /** The pip multiplier never drops a candidate more than half — this is a soft nudge, not a
         * hard filter (a card can still be the best gap-fill available despite a heavy cost). */
        const val PIP_MULTIPLIER_FLOOR = 0.5f

        /** WS9.5 -- affinity scale for a role-gap bonus when the candidate's best colour is
         * SECONDARY for that (interaction) role. */
        const val INTERACTION_COLOR_SCALE_SECONDARY = 0.85f

        /** WS9.5 -- affinity scale when the candidate's best colour is only SUBSTITUTE (real but
         * reduced access, per [ColorRoleAffinity]) for the role. */
        const val INTERACTION_COLOR_SCALE_SUBSTITUTE = 0.55f

        /** WS9.5 -- affinity scale when the candidate's best colour is ABSENT for the role (should
         * be rare in practice -- WS9.2 already relaxes/redistributes fully-infeasible role bands at
         * the skeleton level, so a candidate reaching this branch is an edge case, not the norm). */
        const val INTERACTION_COLOR_SCALE_ABSENT = 0.2f

        /** WS9.5 -- the manabase-shortage penalty never drops a candidate more than half, same
         * "soft nudge" discipline as [PIP_MULTIPLIER_FLOOR]. */
        const val SHORTAGE_PENALTY_FLOOR = 0.6f

        /** WS9.5 -- additive bonus for a mana-fixing candidate (rock or fixing land) when the
         * manabase is genuinely short at 3+ colours; large enough to push a typical fixing
         * candidate's score above a typical spell add's, without an outright score=1 override that
         * would erase ranking among multiple fixing options themselves. */
        const val MANA_FIX_PRIORITY_BONUS = 0.5f
    }
}
