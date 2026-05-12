package com.mmg.manahub.feature.decks.engine

import com.mmg.manahub.R
import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.DeckFormat
import com.mmg.manahub.core.domain.model.DeckSlotEntry
import com.mmg.manahub.core.domain.model.UserCardWithCard
import com.mmg.manahub.core.domain.usecase.decks.BasicLandCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Result of the deck improvement analysis.
 */
data class DeckImprovementReport(
    val strengths: List<AnalysisPoint>,
    val weaknesses: List<AnalysisPoint>,
    val suggestions: List<ImprovementSuggestion>
)

data class AnalysisPoint(
    val labelResId: Int,
    val descriptionResId: Int,
    val severity: AnalysisSeverity = AnalysisSeverity.INFO
)

enum class AnalysisSeverity { INFO, WARNING, ERROR }

data class ImprovementSuggestion(
    val magicCard: MagicCard,
    val reasonResId: Int,
    val actionType: SuggestionActionType,
    val swapFor: MagicCard? = null
)

enum class SuggestionActionType { ADD_FROM_COLLECTION, SWAP_FROM_SIDEBOARD }

/**
 * Representation of a card within the Magic Engine, abstracted to support 
 * both owned and future non-owned cards.
 */
data class MagicCard(
    val card: Card,
    val isOwned: Boolean,
    val quantity: Int = 1
)

data class MagicSuggestion(
    val magicCard: MagicCard,
    val score: Float,
    val reasons: List<String>
)

data class MagicDiscovery(
    val label: String,
    val cards: List<MagicCard>,
    val description: String,
    val primaryTag: CardTag
)

/**
 * Unified engine for collection analysis (Discovery) and deck creation (Creator).
 * This is a singleton-friendly logic class, not a ViewModel.
 */
@Singleton
class DeckMagicEngine @Inject constructor() {

    /**
     * Analyzes the collection to find "Synergy Clusters" based on recurring tags.
     * Replaces the old Regex-based tribal detection.
     */
    suspend fun discoverSynergies(
        collection: List<UserCardWithCard>
    ): List<MagicDiscovery> = withContext(Dispatchers.Default) {
        val allTags = collection.flatMap { it.card.tags + it.card.userTags }
            .groupingBy { it }
            .eachCount()
            .filter { it.value >= 4 } // Minimum cluster size
            .entries.sortedByDescending { it.value }

        allTags.take(6).map { (tag, count) ->
            val matchingCards = collection
                .filter { tag in it.card.tags || tag in it.card.userTags }
                .take(12)
                .map { MagicCard(it.card, isOwned = true) }

            MagicDiscovery(
                label = tag.label,
                cards = matchingCards,
                description = "$count cards with this tag in your collection",
                primaryTag = tag
            )
        }
    }

    /**
     * Builds a list of suggestions based on seed tags and current deck state.
     */
    suspend fun getSuggestions(
        collection: List<UserCardWithCard>,
        targetTags: List<CardTag>,
        selectedColors: Set<ManaColor>,
        mainboard: List<MagicCard>,
        format: GameFormat
    ): List<MagicSuggestion> = withContext(Dispatchers.Default) {
        val alreadyIn = mainboard.map { it.card.scryfallId }.toSet()
        val avgCmc = if (mainboard.isEmpty()) 3.0
        else mainboard.sumOf { it.card.cmc * it.quantity } /
                mainboard.sumOf { it.quantity }.coerceAtLeast(1)

        collection
            .filter { it.card.scryfallId !in alreadyIn }
            .map { uwc ->
                val result = MagicScorer.score(
                    card = uwc.card,
                    targetTags = targetTags,
                    selectedColors = selectedColors,
                    currentAverageCmc = avgCmc,
                    deckSize = mainboard.sumOf { it.quantity }
                )
                MagicSuggestion(
                    magicCard = MagicCard(uwc.card, isOwned = true),
                    score = result.score,
                    reasons = result.reasons
                )
            }
            .filter { it.score > 0.1f }
            .sortedByDescending { it.score }
            .take(50)
    }

    /**
     * Analyzes the deck to find strengths, weaknesses and improvement suggestions.
     */
    suspend fun analyzeDeck(
        cards: List<DeckSlotEntry>,
        collection: List<UserCardWithCard>,
        format: DeckFormat?
    ): DeckImprovementReport = withContext(Dispatchers.Default) {
        val strengths = mutableListOf<AnalysisPoint>()
        val weaknesses = mutableListOf<AnalysisPoint>()
        val suggestions = mutableListOf<ImprovementSuggestion>()

        val mainboard = cards.filter { !it.isSideboard && it.card != null }
        val sideboard = cards.filter { it.isSideboard && it.card != null }
        val totalCards = mainboard.sumOf { it.quantity }
        val lands = mainboard.filter { BasicLandCalculator.isLand(it.card!!) }
        val landCount = lands.sumOf { it.quantity }
        val nonLands = mainboard.filter { !BasicLandCalculator.isLand(it.card!!) }

        val targetDeckSize = format?.targetDeckSize ?: 60
        val targetLandCount = format?.targetLandCount ?: (targetDeckSize * 0.4).toInt()

        // ── 1. Land Count Analysis ──────────────────────────────────────────
        val landDiff = landCount - targetLandCount
        when {
            abs(landDiff) <= 2 -> strengths.add(AnalysisPoint(R.string.deck_improve_lands_good, R.string.deck_improve_lands_good, AnalysisSeverity.INFO))
            landDiff < -2 -> weaknesses.add(AnalysisPoint(R.string.deck_improve_lands_few, R.string.deck_improve_lands_few, AnalysisSeverity.ERROR))
            landDiff > 2 -> weaknesses.add(AnalysisPoint(R.string.deck_improve_lands_many, R.string.deck_improve_lands_many, AnalysisSeverity.WARNING))
        }

        // ── 2. Mana Curve Analysis ──────────────────────────────────────────
        val avgCmc = if (nonLands.isEmpty()) 0.0 else nonLands.sumOf { it.card!!.cmc * it.quantity } / nonLands.sumOf { it.quantity }
        when {
            avgCmc in 2.0..3.5 -> strengths.add(AnalysisPoint(R.string.deck_improve_mana_curve_good, R.string.deck_improve_mana_curve_good, AnalysisSeverity.INFO))
            avgCmc > 3.5 -> weaknesses.add(AnalysisPoint(R.string.deck_improve_mana_curve_high, R.string.deck_improve_mana_curve_high, AnalysisSeverity.WARNING))
            avgCmc < 2.0 && nonLands.isNotEmpty() -> weaknesses.add(AnalysisPoint(R.string.deck_improve_mana_curve_low, R.string.deck_improve_mana_curve_low, AnalysisSeverity.WARNING))
        }

        // ── 3. Interaction Analysis ─────────────────────────────────────────
        val interactionTags = setOf("removal", "board_wipe", "counterspell")
        val interactionCount = nonLands.sumOf { entry ->
            if (entry.card!!.tags.any { it.key in interactionTags } || entry.card.userTags.any { it.key in interactionTags }) entry.quantity else 0
        }
        val targetInteraction = (targetDeckSize * 0.15).toInt() // Roughly 15%
        if (interactionCount >= targetInteraction) {
            strengths.add(AnalysisPoint(R.string.deck_improve_interaction_good, R.string.deck_improve_interaction_good, AnalysisSeverity.INFO))
        } else {
            weaknesses.add(AnalysisPoint(R.string.deck_improve_interaction_low, R.string.deck_improve_interaction_low, AnalysisSeverity.WARNING))

            // Suggest interaction from sideboard or collection
            val sideboardInteraction = sideboard.filter { entry ->
                entry.card!!.tags.any { it.key in interactionTags } || entry.card.userTags.any { it.key in interactionTags }
            }.take(3)

            sideboardInteraction.forEach { entry ->
                suggestions.add(ImprovementSuggestion(
                    magicCard = MagicCard(entry.card!!, isOwned = true),
                    reasonResId = R.string.deck_improve_interaction_low,
                    actionType = SuggestionActionType.SWAP_FROM_SIDEBOARD
                ))
            }
        }

        // ── 4. Synergy Analysis ─────────────────────────────────────────────
        val allTags = nonLands.flatMap { entry -> (entry.card!!.tags + entry.card.userTags).map { it to entry.quantity } }
            .groupBy { it.first }
            .mapValues { it.value.sumOf { p -> p.second } }

        val topSynergy = allTags.entries.filter { it.key.category == com.mmg.manahub.core.domain.model.TagCategory.STRATEGY || it.key.category == com.mmg.manahub.core.domain.model.TagCategory.TRIBAL }
            .maxByOrNull { it.value }

        if (topSynergy != null && topSynergy.value >= (targetDeckSize * 0.2).toInt()) {
            strengths.add(AnalysisPoint(R.string.deck_improve_synergy_good, R.string.deck_improve_synergy_good, AnalysisSeverity.INFO))
        } else {
            weaknesses.add(AnalysisPoint(R.string.deck_improve_synergy_low, R.string.deck_improve_synergy_low, AnalysisSeverity.INFO))
        }

        DeckImprovementReport(strengths, weaknesses, suggestions.distinctBy { it.magicCard.card.scryfallId })
    }
}























