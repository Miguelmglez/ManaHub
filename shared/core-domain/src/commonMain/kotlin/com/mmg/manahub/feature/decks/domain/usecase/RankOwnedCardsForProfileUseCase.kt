package com.mmg.manahub.feature.decks.domain.usecase

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.DeckIdentitySeedTags
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.StrategyProfile
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver

/**
 * Deck Engine Unification plan (`docs/plans/deck-engine-unification-plan.md` §5 Phase 3.3/3.4, Flow
 * B colors-first / Flow C strategy-first): once the user has picked a [StrategyProfile] (before any
 * card has been placed), ranks the user's OWNED collection by identity-tag overlap with that profile
 * and returns the top matches as a check/uncheck "suggested seeds" list (RC5 — the user always
 * controls which specific cards seed the build; this only proposes a starting selection, it never
 * auto-includes anything).
 *
 * Same lightweight tag-overlap heuristic as [SuggestStrategiesForSeedsUseCase] (the inverse
 * direction: there {seeds -> profile}, here {profile -> owned cards}) — deliberately NOT
 * [com.mmg.manahub.feature.decks.domain.engine.DeckScorer.fit], which needs a materialized
 * [com.mmg.manahub.feature.decks.domain.engine.DeckProfile] this early UX step does not have yet.
 *
 * Pure, dependency-free, and side-effect-free — easily unit-testable.
 */
class RankOwnedCardsForProfileUseCase {

    operator fun invoke(profile: StrategyProfile, collection: List<Card>, limit: Int = DEFAULT_LIMIT): List<Card> {
        val profileKeys = DeckIdentitySeedTags.forProfile(profile).map { it.key }.toSet()
        if (profileKeys.isEmpty()) return emptyList()
        return collection
            .distinctBy { it.name }
            // Bug fix (Deck Wizard entry-flow audit): a picked StrategyProfile.colors was captured
            // but never actually consulted -- every owned card matching the strategy/theme tag
            // overlap surfaced as a "suggested seed" regardless of its own color identity, so an
            // off-color card could be suggested for a deck whose colors the user had ALREADY picked
            // in this same flow (Flow B colors-first / Flow C's post-combo re-rank). Empty
            // profile.colors means "no color pick yet" (unchanged behavior -- every existing caller
            // that hasn't resolved colors keeps ranking on strategy fit alone); once colors ARE set,
            // only cards whose color identity is a subset of them (the same identity-inclusion rule
            // used everywhere else in the engine, e.g. DeckWarning.OffColorIdentity) are eligible.
            .filter { card -> profile.colors.isEmpty() || profile.colors.containsAll(card.colorIdentity.toManaColorSet()) }
            .map { card -> card to overlap(card, profileKeys) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Card, Int>> { it.second }.thenBy { it.first.name })
            .take(limit)
            .map { it.first }
    }

    private fun overlap(card: Card, profileKeys: Set<String>): Int {
        val cardKeys = (card.tags + card.userTags)
            .filter { it.category in IDENTITY_CATEGORIES }
            .mapTo(mutableSetOf()) { it.key } + TribeDeriver.subtypeKeys(card)
        return cardKeys.count { it in profileKeys }
    }

    private companion object {
        val IDENTITY_CATEGORIES = setOf(TagCategory.STRATEGY, TagCategory.ARCHETYPE, TagCategory.TRIBAL)
        const val DEFAULT_LIMIT = 12
    }
}

/** Same convention as [com.mmg.manahub.feature.decks.domain.template.DeckTemplateResolver]'s private
 * file-local extension of the same name -- resolves raw Scryfall color-identity symbols onto
 * [ManaColor], dropping anything unrecognized. */
private fun List<String>.toManaColorSet(): Set<ManaColor> =
    mapNotNull { symbol -> ManaColor.entries.firstOrNull { it.symbol == symbol } }.toSet()
