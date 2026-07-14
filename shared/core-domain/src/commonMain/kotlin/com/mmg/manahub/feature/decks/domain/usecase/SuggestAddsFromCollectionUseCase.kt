package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.DeckFormat
import com.mmg.manahub.feature.decks.domain.engine.ArchetypeRoleClassifier
import com.mmg.manahub.feature.decks.domain.engine.CardFit
import com.mmg.manahub.feature.decks.domain.engine.DeckEntry
import com.mmg.manahub.feature.decks.domain.engine.DeckProfile
import com.mmg.manahub.feature.decks.domain.engine.DeckScorer
import com.mmg.manahub.feature.decks.domain.engine.ManaBaseAnalyzer
import com.mmg.manahub.feature.decks.domain.engine.ResolvedArchetypeSkeleton
import com.mmg.manahub.feature.decks.domain.engine.RoleKey
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
 *    ranking is driven by [DeckScorer.rankAdds] alone, byte-identical to pre-Phase-2 behavior);
 *  - a PIP-INTENSITY MULTIPLIER (D14) that softly penalizes a candidate whose heaviest single-color
 *    mana-symbol commitment is expensive relative to the deck's color count, reusing
 *    [ManaBaseAnalyzer.maxSinglePipIntensity] rather than re-parsing mana costs.
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
 * mirrors [SuggestAddsWithBudgetUseCase]'s existing D3 logic exactly.
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
     *         already owned, so [BudgetOptimizer] never needs to run against this source).
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
        val colorCount = profile.colorIdentity.size

        val rescored = ranked.map { fit ->
            val bonus = themeRoleBonus(fit.card, resolvedSkeleton, currentRoleCounts)
            val multiplier = pipIntensityMultiplier(fit.card, colorCount)
            fit.copy(score = ((fit.score + bonus).coerceIn(0f, 1f)) * multiplier)
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
     * Additive bonus for a candidate that fills a GAP role in the resolved archetype/theme
     * skeleton (`current < ideal`), scaled by the candidate's own [ArchetypeRoleClassifier]
     * confidence for that role and by how large the gap still is. Anti-roles never earn a bonus
     * (adding to a role the archetype actively avoids is never rewarded here — the redundancy/
     * anti-role warning path in [com.mmg.manahub.feature.decks.domain.usecase.EvaluateDeckUseCase]
     * already discourages it). Returns `0f` when [resolvedSkeleton] is `null` (GENERIC/no themes)
     * or the card matches no gapped role — the base [DeckScorer] score is untouched in that case.
     */
    private fun themeRoleBonus(
        card: Card,
        resolvedSkeleton: ResolvedArchetypeSkeleton?,
        currentRoleCounts: Map<RoleKey, Int>,
    ): Float {
        if (resolvedSkeleton == null) return 0f
        val cardRoles = ArchetypeRoleClassifier.classify(card)
        if (cardRoles.isEmpty()) return 0f
        var best = 0f
        resolvedSkeleton.roleTargets.forEach { (key, target) ->
            if (key in resolvedSkeleton.antiRoles) return@forEach
            val confidence = cardRoles[key] ?: return@forEach
            val current = currentRoleCounts[key] ?: 0
            if (current >= target.ideal) return@forEach
            val idealFloor = target.ideal.coerceAtLeast(1)
            val gapRatio = ((target.ideal - current).toFloat() / idealFloor).coerceIn(0f, 1f)
            val bonus = confidence * gapRatio
            if (bonus > best) best = bonus
        }
        return best * THEME_ROLE_BONUS_WEIGHT
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
    }
}
