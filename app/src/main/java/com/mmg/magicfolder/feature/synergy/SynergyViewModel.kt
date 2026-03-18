package com.mmg.magicfolder.feature.synergy

import com.mmg.magicfolder.core.domain.usecase.collection.GetCollectionUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mmg.magicfolder.core.domain.model.UserCardWithCard
import com.mmg.magicfolder.core.domain.model.MtgColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SynergyViewModel @Inject constructor(
    private val getCollection: GetCollectionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SynergyUiState())
    val uiState: StateFlow<SynergyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getCollection()
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { cards ->
                    _uiState.update { it.copy(collectionCards = cards) }
                    analyzeCollection(cards)
                }
        }
    }

    fun onFormatChange(format: DeckFormat) {
        _uiState.update { it.copy(selectedFormat = format) }
        analyzeCollection(_uiState.value.collectionCards)
    }

    private fun analyzeCollection(cards: List<UserCardWithCard>) {
        if (cards.isEmpty()) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            val format = _uiState.value.selectedFormat
            val (synergies, decks) = withContext(Dispatchers.Default) {
                val groups  = detectSynergyGroups(cards)
                val suggestions = buildDeckSuggestions(cards, groups, format)
                groups to suggestions
            }
            _uiState.update {
                it.copy(synergyGroups = synergies, suggestedDecks = decks, isLoading = false)
            }
        }
    }

    // ── Synergy engine ────────────────────────────────────────────────────

    private fun detectSynergyGroups(cards: List<UserCardWithCard>): List<SynergyGroup> {
        val groups = mutableListOf<SynergyGroup>()

        // Keyword synergies
        val keywordGroups = mapOf(
            "Flying"      to "Flying tribal",
            "Haste"       to "Haste package",
            "Deathtouch"  to "Deathtouch synergy",
            "Lifelink"    to "Lifegain package",
            "Trample"     to "Trample package",
            "Vigilance"   to "Vigilance package",
        )
        for ((keyword, label) in keywordGroups) {
            val matching = cards.filter { keyword in it.card.keywords }
            if (matching.size >= 3) {
                groups.add(SynergyGroup(label, matching.take(12), "Cards with $keyword"))
            }
        }

        // Tribal synergies — detect repeated subtypes
        val typePattern = Regex("""— (.+)$""")
        val subtypeCounts = mutableMapOf<String, MutableList<UserCardWithCard>>()
        cards.forEach { item ->
            val match = typePattern.find(item.card.typeLine)
            match?.groupValues?.get(1)?.split(" ")?.forEach { subtype ->
                if (subtype.length > 2) {
                    subtypeCounts.getOrPut(subtype) { mutableListOf() }.add(item)
                }
            }
        }
        subtypeCounts.entries
            .filter { it.value.size >= 4 }
            .sortedByDescending { it.value.size }
            .take(3)
            .forEach { (subtype, tribal) ->
                groups.add(SynergyGroup("$subtype tribal", tribal, "${tribal.size} $subtype cards in collection"))
            }

        // Draw engine — oracle text patterns
        val drawCards = cards.filter { "draw a card" in (it.card.oracleText ?: "") }
        if (drawCards.size >= 3) {
            groups.add(SynergyGroup("Draw engine", drawCards.take(10), "${drawCards.size} cards that draw"))
        }

        // Graveyard synergy
        val graveyardCards = cards.filter {
            val text = it.card.oracleText?.lowercase() ?: ""
            "graveyard" in text || "dies" in text || "flashback" in text
        }
        if (graveyardCards.size >= 4) {
            groups.add(SynergyGroup("Graveyard package", graveyardCards.take(12), "Graveyard synergy"))
        }

        return groups
    }

    private fun buildDeckSuggestions(
        cards:    List<UserCardWithCard>,
        groups:   List<SynergyGroup>,
        format:   DeckFormat,
    ): List<DeckSuggestion> {
        val suggestions = mutableListOf<DeckSuggestion>()

        // For each significant synergy group, suggest a deck built around it
        groups.take(5).forEach { group ->
            val coreCards = group.cards
            val coreColors = coreCards
                .flatMap { it.card.colorIdentity }
                .map { code -> 
                    MtgColor.entries.find { it.name == code } ?: MtgColor.COLORLESS
                }
                .groupingBy { it }
                .eachCount()
                .entries.sortedByDescending { it.value }
                .take(2)
                .map { it.key }

            // Fill to 60 cards with on-color cards from the collection
            val filler = cards
                .filter { item ->
                    item !in coreCards &&
                            item.card.colorIdentity.any { code -> coreColors.any { it.name == code } } &&
                            !item.card.typeLine.contains("Land")
                }
                .sortedByDescending { rarityWeight(it.card.rarity) }
                .take(60 - coreCards.size)

            val deckCards = (coreCards + filler).take(60)
            val score = minOf(100, group.cards.size * 10)

            suggestions.add(
                DeckSuggestion(
                    name        = group.label,
                    format      = format,
                    colors      = coreColors,
                    cards       = deckCards,
                    coverCardId = coreCards.firstOrNull()?.card?.scryfallId,
                    synergyScore = score,
                )
            )
        }

        return suggestions.sortedByDescending { it.synergyScore }
    }

    private fun rarityWeight(rarity: String) = when (rarity.lowercase()) {
        "mythic" -> 4; "rare" -> 3; "uncommon" -> 2; else -> 1
    }

    fun onErrorDismissed() = _uiState.update { it.copy(error = null) }
}