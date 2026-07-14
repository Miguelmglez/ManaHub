package com.mmg.manahub.feature.decks.domain.engine

import com.mmg.manahub.core.model.Card
import com.mmg.manahub.core.model.CardTag
import com.mmg.manahub.core.model.UserCardWithCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    /**
     * Structured, localizable explanations for the suggestion (plan E5). The engine emits
     * [ScoreReason] values; the presentation layer renders them through `DeckDoctorStrings`
     * (`ScoreReason.label()`). This replaces the old `roles.map { it.name }` raw-enum-name leak,
     * which bypassed the localization pipeline and surfaced `BOARD_WIPE`/`THREAT` strings.
     */
    val reasons: List<ScoreReason>
)

data class MagicDiscovery(
    val label: String,
    val cards: List<MagicCard>,
    val description: String,
    val primaryTag: CardTag
)

/**
 * Unified engine for collection analysis (Discovery) and deck creation (Creator).
 *
 * Platform-agnostic — no `@Inject`/`@Singleton` annotations (stripped for KMP `commonMain`).
 * Provide via Hilt `@Provides` in `DeckDoctorModule` (still-Hilt consumers) or Koin factory
 * (migrated islands). On non-JVM targets `Dispatchers.Default` falls back to `Dispatchers.Default`.
 */
class DeckMagicEngine(
    private val deckScorer: DeckScorer,
) {

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
                .distinctBy { it.card.scryfallId } // Fix: de-dup for UI stability (lazy list keys)
                .take(12)
                .map { MagicCard(it.card, isOwned = true) }

            MagicDiscovery(
                label = tag.displayLabel,
                cards = matchingCards,
                description = "$count cards with this tag in your collection",
                primaryTag = tag
            )
        }
    }

    /**
     * Builds a list of suggestions based on seed tags and current deck state.
     * Delegates scoring to [DeckScorer] (replaces the legacy MagicScorer path).
     */
    suspend fun getSuggestions(
        collection: List<UserCardWithCard>,
        targetTags: List<CardTag>,
        selectedColors: Set<ManaColor>,
        mainboard: List<MagicCard>,
        format: GameFormat
    ): List<MagicSuggestion> = withContext(Dispatchers.Default) {
        val mainboardEntries = mainboard.map {
            DeckEntry(card = it.card, quantity = it.quantity, isOwned = it.isOwned)
        }
        val profile = deckScorer.profile(
            mainboard = mainboardEntries,
            // Phase 4 (D1): 1:1 GameFormat → DeckFormat so the right legality/skeleton is used.
            format = format.toEngineDeckFormat(),
            colorIdentity = selectedColors,
            seedTags = targetTags
        )

        val candidates = collection.map { it.card }.distinctBy { it.scryfallId }
        val ownedIds = candidates.map { it.scryfallId }.toSet()

        val ranked = deckScorer.rankAdds(
            candidates = candidates,
            profile = profile,
            ownedIds = ownedIds,
            limit = 50
        )

        ranked.map { fit ->
            MagicSuggestion(
                magicCard = MagicCard(fit.card, isOwned = fit.isOwned),
                score = fit.score,
                reasons = fit.reasons
            )
        }
    }
}
