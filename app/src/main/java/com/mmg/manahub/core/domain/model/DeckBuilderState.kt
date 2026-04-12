package com.mmg.manahub.core.domain.model

// Carta dentro del mazo builder (con cantidad)
data class DeckCard(
    val card: Card,
    val quantity: Int = 1,
    val isOwned: Boolean = false,
)

// Distribución de tierras básicas calculada
data class BasicLandDistribution(
    val plains: Int = 0,
    val islands: Int = 0,
    val swamps: Int = 0,
    val mountains: Int = 0,
    val forests: Int = 0,
) {
    val total: Int get() = plains + islands + swamps + mountains + forests

    fun toMap(): Map<String, Int> = mapOf(
        "W" to plains,
        "U" to islands,
        "B" to swamps,
        "R" to mountains,
        "G" to forests,
    ).filter { it.value > 0 }
}

data class DeckBuilderState(
    val deckName: String = "",
    val format: DeckFormat = DeckFormat.STANDARD,
    val commander: Card? = null,
    val commanderColorIdentity: Set<String> = emptySet(),
    val mainboard: List<DeckCard> = emptyList(),
    val sideboard: List<DeckCard> = emptyList(),
    val nonBasicLands: List<DeckCard> = emptyList(),
    val basicLands: BasicLandDistribution = BasicLandDistribution(),
    val step: BuilderStep = BuilderStep.SETUP,
    val activeTab: BuilderTab = BuilderTab.MY_COLLECTION,
    val collectionCards: List<DeckCard> = emptyList(),
    val suggestions: List<DeckCard> = emptyList(),
    val filterColors: Set<String> = emptySet(),
    val filterType: String = "",
    val filterMaxCmc: Int? = null,
    val filterMaxPrice: Double? = null,
    val isLoadingCollection: Boolean = false,
    val isLoadingSuggestions: Boolean = false,
    val error: String? = null,
    val reviewGroupBy: ReviewGroupBy = ReviewGroupBy.TYPE,
    val acknowledgedOverLimitCards: Set<String> = emptySet(),
) {
    val overLimitCards: Set<String>
        get() {
            if (format.uniqueCards || format.maxCopies >= 99) return emptySet()
            return mainboard
                .filter { it.quantity > format.maxCopies }
                .map { it.card.scryfallId }
                .toSet()
        }

    val unacknowledgedOverLimitCards: Set<String>
        get() = overLimitCards - acknowledgedOverLimitCards

    val nonLandCardCount: Int
        get() = mainboard.sumOf { it.quantity }

    val totalLandCount: Int
        get() = basicLands.total + nonBasicLands.sumOf { it.quantity }

    val totalCardCount: Int
        get() = nonLandCardCount + totalLandCount + (if (commander != null) 1 else 0)

    val remainingNonLandSlots: Int
        get() = (format.nonLandSlots - nonLandCardCount).coerceAtLeast(0)

    val completionPercent: Float
        get() = (totalCardCount.toFloat() / format.targetDeckSize).coerceIn(0f, 1f)

    val isComplete: Boolean
        get() = totalCardCount >= format.targetDeckSize
}

enum class BuilderStep { SETUP, BUILDING, REVIEW }
enum class BuilderTab { MY_COLLECTION, SCRYFALL_SUGGESTIONS }
enum class ReviewGroupBy { TYPE, COLOR, CMC, RARITY }
