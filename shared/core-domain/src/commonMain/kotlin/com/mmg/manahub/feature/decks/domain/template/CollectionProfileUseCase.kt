package com.mmg.manahub.feature.decks.domain.template

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.TagCategory
import com.mmg.manahub.feature.decks.domain.engine.CommanderEligibility
import com.mmg.manahub.feature.decks.domain.engine.ManaColor
import com.mmg.manahub.feature.decks.domain.engine.TribeDeriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.ln

/** One color's share of the collection's colored mana-cost pips. [share] is the normalized
 * fraction (0f..1f, sums to 1f across the returned list) consumed by ranking logic
 * ([com.mmg.manahub.feature.decks.presentation.wizard.DeckWizardViewModel.recomputeColorComboSuggestions]);
 * [pipCount] is the RAW, un-normalized pip count -- callers feeding a UI element that displays an
 * actual total (e.g. [com.mmg.manahub.core.ui.components.CircularDistribution]'s centered sum) must
 * use [pipCount], never a share rescaled to an arbitrary int (see
 * `feedback_circulardistribution_wizard_pipcount_not_scaled_share.md`). */
data class CollectionColorShare(val color: ManaColor, val share: Float, val pipCount: Int)

/** A STRATEGY tag the collection leans into, with how many distinct owned cards carry it. */
data class CollectionStrategySignal(val tag: CardTag, val copies: Int)

/** A creature tribe the collection leans into (derived, never persisted -- mirrors
 * [TribeDeriver]'s own runtime-only contract). */
data class CollectionTribeSignal(val tribeKey: String, val displayLabel: String, val copies: Int)

/** An owned legendary creature ranked as a viable Commander pick. */
data class OwnedCommanderCandidate(val card: Card, val supportScore: Float)

/** Wizard Step 2 input (plan §3.1 row 4, §3.4 Step 2 "Your collection leans ..."). */
data class CollectionProfile(
    val colorShares: List<CollectionColorShare>,
    val dominantStrategies: List<CollectionStrategySignal>,
    val dominantTribes: List<CollectionTribeSignal>,
    val commanderCandidates: List<OwnedCommanderCandidate>,
)

/**
 * Deck Builder v2 (plan §3.1 row 4): aggregates the user's OWN collection into the signals the
 * wizard's "Direction" step surfaces as tappable suggestions ("you have a lot of white / a lot of
 * aggro / 23 Vampires") plus a ranked list of owned Commander-eligible legendaries.
 *
 * ## Ownership split (mirrors Motor A/B precedent)
 * Takes an ALREADY-SNAPSHOTTED [Card] list rather than injecting
 * [com.mmg.manahub.core.domain.repository.UserCardRepository] and observing internally -- same
 * split [com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCollectionUseCase] and
 * [com.mmg.manahub.feature.decks.domain.usecase.SuggestAddsFromCommunityUseCase] use: the
 * repository Flow subscription is the CALLER's job (a wizard ViewModel, Phase 3, out of scope
 * here), this use case is a pure, easily-testable computation over the data.
 *
 * Determinism: every returned list is EXPLICITLY sorted (never left in collection-query order).
 */
class CollectionProfileUseCase(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    suspend operator fun invoke(collection: List<Card>, limit: Int = DEFAULT_LIMIT): CollectionProfile =
        withContext(ioDispatcher) {
            // Dedupe by NAME, not scryfallId: two different printings of the same card (a regular
            // + a foil/promo copy, see project_card_versions_languages memory) are ONE real card to
            // the user, not two. Deduping by scryfallId double-counted that single card in every
            // derived signal below (colorShares, dominantStrategies/Tribes, commanderCandidates'
            // supportFraction). Attributes read here (colors, manaCost, tags, colorIdentity,
            // edhrecRank, typeLine) are identical across a card's printings, so picking any one
            // arbitrary printing per name is safe.
            val distinct = collection.distinctBy { it.name }
            CollectionProfile(
                colorShares = colorShares(distinct),
                dominantStrategies = dominantStrategies(distinct, limit),
                dominantTribes = dominantTribes(distinct, limit),
                commanderCandidates = commanderCandidates(distinct, limit),
            )
        }

    /** Colored mana-cost pip share per color (mirrors [com.mmg.manahub.core.domain.usecase.decks
     * .BasicLandCalculator]'s own colored-pip counting, at the collection level rather than a
     * single deck's mainboard). */
    private fun colorShares(cards: List<Card>): List<CollectionColorShare> {
        val counts = mutableMapOf<ManaColor, Int>()
        cards.forEach { card ->
            val cost = card.manaCost.orEmpty()
            ManaColor.entries.forEach { color ->
                val symbol = color.symbol.firstOrNull() ?: return@forEach
                val count = cost.count { it == symbol }
                if (count > 0) counts[color] = (counts[color] ?: 0) + count
            }
        }
        val total = counts.values.sum()
        if (total <= 0) return emptyList()
        return counts.entries
            .map { (color, count) -> CollectionColorShare(color, count.toFloat() / total, count) }
            .sortedWith(compareByDescending<CollectionColorShare> { it.share }.thenBy { it.color.name })
    }

    private fun dominantStrategies(cards: List<Card>, limit: Int): List<CollectionStrategySignal> {
        val counts = mutableMapOf<CardTag, Int>()
        cards.forEach { card ->
            (card.tags + card.userTags)
                .filter { it.category == TagCategory.STRATEGY }
                .distinct()
                .forEach { tag -> counts[tag] = (counts[tag] ?: 0) + 1 }
        }
        return counts.entries
            .map { (tag, count) -> CollectionStrategySignal(tag, count) }
            .sortedWith(compareByDescending<CollectionStrategySignal> { it.copies }.thenBy { it.tag.key })
            .take(limit)
    }

    private fun dominantTribes(cards: List<Card>, limit: Int): List<CollectionTribeSignal> {
        val counts = mutableMapOf<String, Int>()
        cards.forEach { card ->
            TribeDeriver.subtypeKeys(card).forEach { key -> counts[key] = (counts[key] ?: 0) + 1 }
        }
        return counts.entries
            .map { (key, count) ->
                val word = key.removePrefix(TribeDeriver.TRIBE_PREFIX)
                CollectionTribeSignal(key, pluralize(word).replaceFirstChar { it.uppercase() }, count)
            }
            .sortedWith(compareByDescending<CollectionTribeSignal> { it.copies }.thenBy { it.tribeKey })
            .take(limit)
    }

    private fun commanderCandidates(cards: List<Card>, limit: Int): List<OwnedCommanderCandidate> {
        val candidates = cards.filter(CommanderEligibility::isCommanderEligible)
        if (candidates.isEmpty()) return emptyList()
        return candidates
            .map { commander ->
                val identity = commander.colorIdentity.toSet()
                val supportCount = cards.count { identity.containsAll(it.colorIdentity) }
                val supportFraction = if (cards.isEmpty()) 0f else supportCount.toFloat() / cards.size
                val popularity = edhrecPopularityScore(commander.edhrecRank)
                val score = (popularity * POPULARITY_WEIGHT + supportFraction * SUPPORT_WEIGHT).coerceIn(0f, 1f)
                OwnedCommanderCandidate(commander, score)
            }
            .sortedWith(compareByDescending<OwnedCommanderCandidate> { it.supportScore }.thenBy { it.card.name })
            .take(limit)
    }

    /** Lightweight logarithmic rank->popularity falloff (rank 1 -> ~1f, decaying toward 0 for
     * obscure ranks). A standalone heuristic, NOT a reuse of
     * [com.mmg.manahub.feature.decks.domain.engine.EdhrecPowerResolver] -- that class scores a
     * generic card's raw POWER level, not commander-pick viability, a different signal entirely. */
    private fun edhrecPopularityScore(rank: Int?): Float {
        if (rank == null || rank <= 0) return 0f
        return (1.0 - (ln(rank.toDouble()) / ln(RANK_FALLOFF_CEILING))).coerceIn(0.0, 1.0).toFloat()
    }

    /** Best-effort English pluralization -- mirrors [SuggestionCategoryResolver]'s own tribe-word
     * pluralizer. Kept as a small local duplicate rather than a shared public helper: display-only,
     * two call sites, not worth a new cross-file API surface. */
    private fun pluralize(word: String): String = when {
        word.endsWith("y") && word.length > 1 && word[word.length - 2] !in "aeiou" ->
            word.dropLast(1) + "ies"
        word.endsWith("fe") -> word.dropLast(2) + "ves"
        word.endsWith("f") -> word.dropLast(1) + "ves"
        word.endsWith("s") || word.endsWith("x") || word.endsWith("z") ||
            word.endsWith("ch") || word.endsWith("sh") -> word + "es"
        else -> word + "s"
    }

    private companion object {
        const val DEFAULT_LIMIT = 5
        const val POPULARITY_WEIGHT = 0.5f
        const val SUPPORT_WEIGHT = 0.5f
        const val RANK_FALLOFF_CEILING = 20000.0
    }
}
