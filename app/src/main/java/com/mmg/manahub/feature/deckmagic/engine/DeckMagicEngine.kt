package com.mmg.manahub.feature.deckmagic.engine

import com.mmg.manahub.core.domain.model.Card
import com.mmg.manahub.core.domain.model.CardTag
import com.mmg.manahub.core.domain.model.UserCardWithCard
import com.mmg.manahub.feature.decks.engine.GameFormat
import com.mmg.manahub.feature.decks.engine.ManaColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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
}
